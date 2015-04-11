#include <jni.h>
#include <dirent.h>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>
#include <set>
#include <utility>
#include <cctype>

#define DEBUG_TAG "NDK_GuardianScanner"

extern "C" {

	/**
	 * =====================================================================
	 * ---------------------------------------------------------------------
	 */
	static std::string fixProcessNameSyntax(std::string name) {
		size_t posSl = name.find('/');
		size_t posCn = name.find('-');
		std::string ret;

		if (posSl != std::string::npos) {
			if (posSl > 0) {
				ret = name.substr(0, posSl);

			} else {
				/*
				 * Some cmdline files contains something like "/system/bin/binary--command-args"
				 * We only want the name, not the path or the args.
				 */
				size_t startPos = (posCn != std::string::npos) ? name.rfind('/', posCn)+1 : name.rfind('/')+1;
				size_t endPos = (posCn != std::string::npos) ? name.find('-', startPos) : name.size();

				ret = name.substr(startPos, endPos);
			}

		} else if (posCn != std::string::npos) {
			ret = name.substr(0, posCn);

		} else {
			return name;
		}

		return ret;
	}

	/**
	 * =====================================================================
	 * ---------------------------------------------------------------------
	 */
	static jobjectArray createProcessData(JNIEnv* env, std::string& str) {
		/*
		 * Temp container to store the converted data until we create the Java String array
		 */
		std::vector<std::string> lines;

		/*
		 * We have two types of files (/proc/stat) and (/proc/<pid>/stat) which differs a little.
		 *
		 *  - /proc/stat = cpu 7650947 104625 1567588 52176062 744598 139 40697 0 0 0
		 *  - /proc/<pid>/stat = 21 (migration/2) S 2 0 0 0 -1 69247040 0 0 0 0 0 44 0 0 -100 0 1 0 7 0 .........
		 *
		 *  The below code can handle both these types of files.
		 *  It will split each section into an array list. The old c++ compilers in Android's NDK
		 *  does not have proper RegExp support which makes this much easier. And it is a lot faster as well
		 *  since the structure of these files are not very complex.
		 */
		bool isGrouped = false;
		std::string current = "";

		for(char& c : str) {
			if (std::isspace(c) && !isGrouped) {
				if (!current.empty()) {
					lines.push_back(current);
					current = "";
				}

			} else if (c == '(' || c == ')') {
				isGrouped = c == '(';

			} else {
				current += c;
			}
		}

		/*
		 * We will also need to collect the /proc/<pid>/cmdline as this file
		 * will contain the full process name whenever the one in /proc/<pid>/stat
		 * has been truncated.
		 */
		if (lines.size() > 0 && lines[0] != "cpu") {
			std::ifstream in( (std::string("/proc/") + lines[0] + "/cmdline").c_str() );

			if (in && in.good()) {
				std::string line = "";
				std::getline(in, line);

				if (!line.empty()) {
					lines[1] = fixProcessNameSyntax(line);
				}
			}

			if (in) {
				in.close();
			}
		}

		/*
		 * Now we just need to add all of the data to a Java String array
		 */
		jobjectArray ret = env->NewObjectArray(lines.size(), env->FindClass("java/lang/String"), NULL);

		for (int i = 0; i < lines.size(); i++) {
			jstring stringObject = env->NewStringUTF( lines[i].c_str() );
			env->SetObjectArrayElement(ret, i, stringObject);
			env->DeleteLocalRef(stringObject);
		}

		return ret;
	}

	/**
	 * =====================================================================
	 * ---------------------------------------------------------------------
	 */
	static bool isIntegerString(std::string str) {
		for (size_t n = 0; n < str.length(); n++) {
			if (!isdigit( str[ n ] )) {
				return false;
			}
		}

		return str.length() > 0;
	}

	/**
	 * =====================================================================
	 * ---------------------------------------------------------------------
	 */
	JNIEXPORT jobjectArray JNICALL Java_com_spazedog_guardian_scanner_ProcessScanner_getProcessList(JNIEnv *env, jobject thisObj, jobjectArray processList, jboolean collectFromList) {
		/*
		 * Whether or not to collect all processes or only the once defined in the processList
		 */
		bool listCollection = collectFromList == JNI_TRUE;

		/*
		 * Unpack the predefined process list from JVM
		 */
		std::set<std::string> pidList;
		std::set< std::pair<std::string, std::string> > typeList;

		if (processList != NULL) {
			int size = env->GetArrayLength(processList);

			for (int i=0, x=1; x < size; i++, x++) {
				jstring pidString = (jstring) env->GetObjectArrayElement(processList, i);
				const char* pidChars = env->GetStringUTFChars(pidString, 0);

				jstring typeString = (jstring) env->GetObjectArrayElement(processList, x);
				const char* typeChars = env->GetStringUTFChars(typeString, 0);

				env->ReleaseStringUTFChars(pidString, pidChars);
				env->ReleaseStringUTFChars(typeString, typeChars);

				env->DeleteLocalRef(pidString);
				env->DeleteLocalRef(typeString);

				/*
				 * This needs to be searched multiple time, so
				 * we use a set to store it. All we need from it, is to know
				 * whether or not a specific pid has been defined by the caller.
				 */
				pidList.insert( std::string(pidChars) );

				/*
				 * This will only be used if a pid exists in the list above.
				 * It contains the type of process (Android Importance level or 0 for normal Linux process).
				 */
				typeList.insert( std::pair<std::string, std::string>(std::string(pidChars), std::string(typeChars)) );
			}
		}

		/*
		 * Start collecting data from /proc
		 */
		DIR* procDirectory = opendir("/proc");

		if (procDirectory != NULL) {
			std::vector<std::string> lines;
			std::ifstream in("/proc/stat");
			struct dirent* procEntity;

			if (in && in.good()) {
				std::string line = "";
				std::getline(in, line);

				lines.push_back(line);
			}

			if (in) {
				in.close();
			}

			while ((procEntity = readdir(procDirectory)) != NULL) {
				std::string entityName = procEntity->d_name;
				bool isProcess = isIntegerString(entityName);

				/*
				 * If isProcess is false, there is no need to search the 'Set'
				 */
				bool isListedProcess = isProcess && pidList.find(entityName) != pidList.end();

				if (isProcess && (!collectFromList || isListedProcess)) {
					in.open( (std::string("/proc/") + procEntity->d_name + "/stat").c_str() );

					if (in && in.good()) {
						std::string line = "";
						std::getline(in, line);

						if (!line.empty()) {
							std::string type = "0";

							if (isListedProcess) {
								for (std::set< std::pair<std::string, std::string> >::iterator it = typeList.begin(); it != typeList.end(); ++it) {
									if (it->first == entityName) {
										type = std::string( it->second ); break;
									}
								}
							}

							lines.push_back( (type + " " + line) );
						}
					}

					if (in) {
						in.close();
					}
				}
			}

			closedir(procDirectory);

			if (lines.size() > 0) {
				jobjectArray cpuList = createProcessData(env, lines[0]);
				jobjectArray ret = env->NewObjectArray(lines.size(), env->GetObjectClass(cpuList), 0);

				env->SetObjectArrayElement(ret, 0, cpuList);
				env->DeleteLocalRef(cpuList);

				for (int i=1; i < lines.size(); i++) {
					jobjectArray list = createProcessData(env, lines[i]);
					env->SetObjectArrayElement(ret, i, list);
					env->DeleteLocalRef(list);
				}

				return ret;
			}
		}

		return NULL;
	}
};
