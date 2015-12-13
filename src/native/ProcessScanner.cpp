/*
 * This file is part of the Guardian Project: https://github.com/spazedog/guardian
 *
 * Copyright (c) 2015 Daniel Bergløv
 *
 * Guardian is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Guardian is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Guardian. If not, see <http://www.gnu.org/licenses/>
 */

/*
 * This native library is used to collect the stat's of all the currently running processes on a device.
 * This task has been assigned to C++ because it is much faster at file operations compared to Java.
 * C++ actually does a well enough job in this area that it is able to do this work 87% faster than
 * Java, which was the result after some extensive testing in both languages.
 *
 * Java is fine to use for file operations. Just not when you need to extract data from
 * 250-500 files at once.
 */

#include <android/log.h>
#include <jni.h>
#include <dirent.h>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>
#include <utility>
#include <cctype>
#include <cstdlib>
#include <inttypes.h>
#include <string>

using namespace std;

#define DEBUG_TAG "NDK_GuardianScanner"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, DEBUG_TAG, "%s", __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, DEBUG_TAG, "%s", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, DEBUG_TAG, "%s", __VA_ARGS__)
#define CATCH_THROW_JVM_EXCEPTION                                       \
    catch (const std::bad_alloc &e) {                                   \
        jclass jc = env->FindClass("java/lang/OutOfMemoryError");       \
        if(jc) env->ThrowNew (jc, e.what());                            \
    } catch (const std::ios_base::failure &e) {                         \
        jclass jc = env->FindClass("java/io/IOException");              \
        if(jc) env->ThrowNew (jc, e.what());                            \
    } catch (const std::exception &e) {                                 \
        jclass jc = env->FindClass("java/lang/Error");                  \
        if(jc) env->ThrowNew (jc, e.what());                            \
    } catch(...) {                                                      \
        jclass jc = env->FindClass("java/lang/Error");                  \
        if(jc) env->ThrowNew (jc, "unidentified exception");            \
    }


/**
 * =====================================================================
 * ---------------------------------------------------------------------
 * The currently used stlport_static does not support all c++11 features.
 * But because of the below issue, among others, with c++_static, which is still not fixed,
 * we will stick to the more stable, yet limited chain. But in case Google should
 * make good on their word and fix the other, let's keep the code compatible by adding
 * the stlport_static missing functions. That way we can simply remove them when/if we switch chain.
 *
 *      Issue: https://code.google.com/p/android/issues/detail?can=2&start=0&num=100&q=&colspec=ID%20Status%20Priority%20Owner%20Summary%20Stars%20Reporter%20Opened&groupby=&sort=&id=68779
 */
static string to_string(int val) {
    stringstream stream;
    stream << val;

    return stream.str();
}

static string to_string(long val) {
    stringstream stream;
    stream << val;

    return stream.str();
}

/*
 * Pre-declare our class
 */
namespace spazedog {
    typedef pair<string, string> PListValue;
    typedef pair<string, PListValue> PListWrapper;
    typedef vector<PListWrapper> PListArray;

    class ProcessScanner {
        stringstream mLogStream;

        /*
         * Avoid to much realloc for each process by keeping a shared set of vars
         */
        struct {
            ifstream stream;
            string line;
            string file;
            string word;
            bool groupChk;
            bool spaceChk;
            bool cpuChk;
            int curPos;
            int maxPos;
            int curWord;
            int arrPos;
            int arrCount;
        } DataVars;

        struct {
            string word;
            size_t posBegin;
            size_t posEnd;
            size_t posSl;
            size_t posCn;
        } SyntaxVars;

        string fixNameSyntax(string &name);
        void addData(JNIEnv *env, jobject &wrapper, string &data);
        bool isIntegral(string &data);
        string prntString(string &data);
        pair<string, string> cpuInfo(string &data);
        void flushLog();

    public:

        jobject scan(JNIEnv *env, jintArray processList, jint scanFlags);

    } scanner;
}

extern "C" {

    const int FLAG_ALL = 0x00000001;
    const int FLAG_SORT = 0x00000002;

    /*
     * Pre-declare our jni functions
     */
    void jniInit(JNIEnv *env, jobject envObj, jboolean debug);
    jobject jniScan(JNIEnv *env, jobject envObj, jintArray processList, jint flags);

    static jclass JSTRING_CLASS;
    static jclass JLIST_CLASS;

    static jmethodID JLIST_INIT_METHOD_ID;
    static jmethodID JLIST_ADD_METHOD_ID;

    static bool DEBUG = false;
    static const char *JCLASS_PATH = "com/spazedog/guardian/scanner/ProcessScanner";
    static const JNINativeMethod JMETHOD_TABLE[] = {
            {"jniInit", "(Z)V", (void*) jniInit},
            {"jniScan", "([II)Ljava/util/List;", (void*) jniScan}
    };

    /**
     * =====================================================================
     * ---------------------------------------------------------------------
     */
    typedef union {
        JNIEnv* read;
        void* write;
    } UnionJNIEnv;

    jint JNI_OnLoad(JavaVM *vm, void* reserved) {
        LOGI("Loading Library");

        UnionJNIEnv env;

        if (vm->GetEnv(&env.write, JNI_VERSION_1_6) != JNI_OK) {
            LOGE("Error getting JNIEnv"); return -1;
        }

        /*
         * Register the methods that can be called from the Java class
         */
        env.read->RegisterNatives(env.read->FindClass(JCLASS_PATH), JMETHOD_TABLE, 2);

        /*
         * Avoid looking for this class on every call.
         * Just cache it to reuse it.
         */
        JSTRING_CLASS = reinterpret_cast<jclass>(env.read->NewGlobalRef(env.read->FindClass("java/lang/String")));

        /*
         * Locate class and method for use with a List object
         */
        JLIST_CLASS = reinterpret_cast<jclass>(env.read->NewGlobalRef(env.read->FindClass("com/spazedog/lib/utilsLib/SparseList")));
        JLIST_INIT_METHOD_ID = env.read->GetMethodID(JLIST_CLASS, "<init>", "()V");
        JLIST_ADD_METHOD_ID = env.read->GetMethodID(JLIST_CLASS, "add", "(Ljava/lang/Object;)Z");

        return JNI_VERSION_1_6;
    }

    /**
     * =====================================================================
     * ---------------------------------------------------------------------
     */
    void jniInit(JNIEnv *env, jobject envObj, jboolean debug) {
        /*
         * We do not want to handle debug settings for both native and jvm.
         * Simply parse from jvm.
         */
        DEBUG = debug == JNI_TRUE;
    }

    /**
     * =====================================================================
     * ---------------------------------------------------------------------
     */
    jobject jniScan(JNIEnv *env, jobject envObj, jintArray processList, jint scanFlags) {
        return spazedog::scanner.scan(env, processList, scanFlags);
    }
}


/**
 * =====================================================================
 * ---------------------------------------------------------------------
 */
string spazedog::ProcessScanner::prntString(string &data) {
    string ret = "";

    for (char &c : data) {
        if (c >= 0 && c < 128) {
            ret += c;
        }
    }

    return ret;
}

/**
 * =====================================================================
 * ---------------------------------------------------------------------
 */
void spazedog::ProcessScanner::flushLog() {
    string line;

    /*
     * Logcat has a max line policy before starting to truncate.
     * So we write the log line by line.
     */
    while (getline(mLogStream, line)) {
        LOGD(line.c_str());
    }

    mLogStream.str("");
}

/**
 * =====================================================================
 * ---------------------------------------------------------------------
 */
pair<string, string> spazedog::ProcessScanner::cpuInfo(string &data) {
    pair<string, string> ret;

    string dataString = "";
    long dataUptime = 0;

    int maxPos = data.length()-1;
    int curPos = 0;
    int curWord = 0;
    bool preSpace = false;

    for (char &c : data) {
        if (!isspace(c)) {
            preSpace = false;

            if (curWord > 0) {
                dataString += c;
            }
        }

        if (isspace(c) || curPos == maxPos) {
            if (curWord > 0 && !dataString.empty()) {
                dataUptime += atol(dataString.c_str());

                if (curWord == 4) {
                    /*
                     * Idle
                     */
                    ret.first = dataString;
                }

                dataString = "";
            }

            if (!preSpace) {
                curWord++;
            }

            preSpace = true;
        }

        curPos++;
    }

    ret.second = to_string(dataUptime);

    return ret;
}

/**
 * =====================================================================
 * ---------------------------------------------------------------------
 */
bool spazedog::ProcessScanner::isIntegral(string &data) {
    for(char &c : data) {
        if (!isdigit(c)) {
            return false;
        }
    }

    return data.length() > 0;
}

/**
 * =====================================================================
 * ---------------------------------------------------------------------
 */
string spazedog::ProcessScanner::fixNameSyntax(string &name) {
    if (name.length() > 0) {
        SyntaxVars.posSl = name.find('/');
        SyntaxVars.posCn = name.find('-');

        if (SyntaxVars.posSl != string::npos) {
            if (SyntaxVars.posSl > 0) {
                SyntaxVars.word = name.substr(0, SyntaxVars.posSl);

            } else {
                /*
                 * Some cmdline files contains something like "/system/bin/binary--command-args"
                 * We only want the name, not the path or the args.
                 */
                SyntaxVars.posBegin = (SyntaxVars.posCn != string::npos) ? name.rfind('/', SyntaxVars.posCn) + 1 : name.rfind('/') + 1;
                SyntaxVars.posEnd = (SyntaxVars.posCn != string::npos) ? name.find('-', SyntaxVars.posBegin) : name.size();

                SyntaxVars.word = name.substr(SyntaxVars.posBegin, SyntaxVars.posEnd);
            }

        } else if (SyntaxVars.posCn != string::npos) {
            SyntaxVars.word = name.substr(0, SyntaxVars.posCn);

        } else {
            return name;
        }
    }

    return SyntaxVars.word;
}

/**
 * =====================================================================
 * ---------------------------------------------------------------------
 */
void spazedog::ProcessScanner::addData(JNIEnv *env, jobject &wrapper, string &data) {
    if (data.length() > 0) {
        /*
         * Reset variables
         */
        DataVars.word = "";
        DataVars.spaceChk = false;
        DataVars.groupChk = false;
        DataVars.cpuChk = data.at(0) == 'c';
        DataVars.maxPos = data.length()-1;
        DataVars.curPos = 0;
        DataVars.curWord = 0;
        DataVars.arrCount = 0;

        jobjectArray lines = env->NewObjectArray((DataVars.cpuChk ? 3 : 11), JSTRING_CLASS, NULL);

        if (DEBUG) {
            mLogStream << "\n\t\tData Size = ";
            mLogStream << (DataVars.cpuChk ? 3 : 11);
        }

        /*
         * We have two types of files (/proc/stat) and (/proc/<pid>/stat) which differs a little.
         *
         *  - /proc/stat = cpu 7650947 104625 1567588 52176062 744598 139 40697 0 0 0 (Rebuild to: cpu <idle> <uptime>)
         *  - /proc/<pid>/stat = 21 (migration/2) S 2 0 0 0 -1 69247040 0 0 0 0 0 44 0 0 -100 0 1 0 7 0 .........
         *
         *  The below code can handle both these types of files.
         *
         *  	Custom array for CPU Stat (/proc/stat)
         *
         *  		- [0] = Type (cpu)
         *  		- [1] = Total idle time
         *  		- [2] = Total uptime (Including idle)
         *
         *
         *  	Custom array for Process Stat (/proc/<pid>/stat)
         *
         *  		- [0] = Process Type (0 for Linux processes or Android importance level for Android processes (1 if located by FLAG_SORT))
         *  		- [1] = Process UID
         *  		- [2] = Process PID
         *  		- [3] = Process Name
         *  		- [4] = Process UTime
         *  		- [5] = Process STime
         *  		- [6] = Process CUTime
         *  		- [7] = Process CSTime
         *  		- [8] = Process uptime (The cpu total uptime at process launch)
         *  		- [9] = CPU total idle time
         *  		- [10] = CPU total uptime (Including cpu idle)
         */
        for (char &c : data) {
            if (!isspace(c) || DataVars.groupChk) {
                DataVars.spaceChk = false;

                if (!DataVars.cpuChk && (c == '(' || c == ')')) {
                    /*
                     * Ignorer space while inside (...)
                     */
                    DataVars.groupChk = c == '(';

                } else if (DataVars.cpuChk || DataVars.curWord <= 5 || (DataVars.curWord >= 17 && DataVars.curWord <= 20) || DataVars.curWord == 25) { // Contains 5 custom values + /proc/<pid>/stat
                    DataVars.word += c;
                }
            }

            if ((isspace(c) && !DataVars.groupChk) || DataVars.curPos == DataVars.maxPos) {
                if (!DataVars.spaceChk && !DataVars.word.empty()) {
                    if (!DataVars.cpuChk && DataVars.curWord == 5) {
                        DataVars.stream.open(DataVars.file.c_str());

                        if (DataVars.stream.good()) {
                            getline(DataVars.stream, DataVars.line);

                            if (!DataVars.line.empty()) {
                                DataVars.word = fixNameSyntax(DataVars.line);
                            }
                        }

                        DataVars.stream.close();
                        DataVars.stream.clear();

                    } else if (!DataVars.cpuChk && DataVars.curWord == 4) {
                        DataVars.file = "/proc/";
                        DataVars.file += DataVars.word;
                        DataVars.file += "/cmdline";
                    }

                    /*
                     * We want index 2 and 3 to be placed at the last two locations.
                     * This way is much faster than having to do more string operations in scan(),
                     * while also having to do a word count of the entire stat output in order to locate
                     * them if placed at the end of that string. The stat output does not have the same length
                     * on all kernels.
                     */
                    if (!DataVars.cpuChk && DataVars.curWord == 2 || DataVars.curWord == 3) {
                        DataVars.arrPos = (DataVars.curWord + 7);

                    } else if (!DataVars.cpuChk && DataVars.curWord > 3) {
                        DataVars.arrPos = (DataVars.arrCount - 2);

                    } else {
                        DataVars.arrPos = DataVars.arrCount;
                    }

                    if (DEBUG) {
                        mLogStream << "\n\t\t[";
                        mLogStream << DataVars.arrPos;
                        mLogStream << "] = ";
                        mLogStream << prntString(DataVars.word);
                    }

                    try {
                        jstring stringObject = env->NewStringUTF(DataVars.word.c_str());
                        env->SetObjectArrayElement(lines, DataVars.arrPos, stringObject);
                        env->DeleteLocalRef(stringObject);

                    } CATCH_THROW_JVM_EXCEPTION

                    DataVars.arrCount++;    // Count for next array position
                }

                if (!DataVars.spaceChk) {
                    DataVars.curWord++;     // Count for next word in the line (divided by space, except for process names)
                }

                DataVars.spaceChk = true;
                DataVars.word = "";
            }

            DataVars.curPos++;
        }

        /*
         * Add this line to the wrapper List
         */
        try {
            env->CallBooleanMethod(wrapper, JLIST_ADD_METHOD_ID, lines);

        } CATCH_THROW_JVM_EXCEPTION
    }
}

/**
 * =====================================================================
 * ---------------------------------------------------------------------
 */
jobject spazedog::ProcessScanner::scan(JNIEnv *env, jintArray processList, jint scanFlags) {
    int32_t flags = (int32_t) scanFlags;
    PListArray processes;

    /*
     * Create a Java List object for the return data
     */
    jobject ret = env->NewObject(JLIST_CLASS, JLIST_INIT_METHOD_ID);

    /*
     * Collect information about parsed processes.
     * This is a very primitive int array for performance reasons.
     */
    if (processList != NULL) {
        int size = env->GetArrayLength(processList);
        jint *elements = env->GetIntArrayElements(processList, 0);
        string ppid, puid, ptype;

        for (int i=0; i+2 < size; i++) {
            ppid = to_string((int) elements[i]);
            puid = to_string((int) elements[++i]);
            ptype = to_string((int) elements[++i]);

            processes.push_back( PListWrapper(ppid, PListValue(puid, ptype)) );
        }
    }

    if (DEBUG) {
        mLogStream << "=============================================";
        mLogStream << "\n---------------------------------------------";
        mLogStream << "\nStarting process scan";
        mLogStream << "\n\t\tProcess Size = ";
        mLogStream << processes.size();
        mLogStream << "\n\t\tFlag SORT = ";
        mLogStream << ((flags & FLAG_SORT) != 0 ? "TRUE" : "FALSE");
        mLogStream << "\n\t\tFlag ALL = ";
        mLogStream << ((flags & FLAG_ALL) != 0 ? "TRUE" : "FALSE");
    }


    /*
     * Collect new process information
     */

    DIR *procDir = opendir("/proc");
    ifstream procStream;
    struct dirent *procEntry = NULL;
    string procData;

    string entFile;
    string entBuffer;
    string entUid;
    string entPid;
    string entType;
    bool entIsListed = false;

    int listCount = 0;
    int scanCount = 0;

    size_t sortBegin;
    size_t sortEnd;

    pair<string, string> cpuStat;

    do {
        if (procEntry == NULL) {
            /*
             * The first entry should be the CPU info
             */
            procStream.open("/proc/stat");

            if (procStream.good()) {
                getline(procStream, procData);

                cpuStat = cpuInfo(procData);

                if (DEBUG) {
                    mLogStream << "\nCollecting CPU information";
                    mLogStream << "\n\t\tStat Line = ";
                    mLogStream << prntString(procData);
                    mLogStream << "\n\t\tIdle = ";
                    mLogStream << prntString(cpuStat.first);
                    mLogStream << "\n\t\tUptime = ";
                    mLogStream << prntString(cpuStat.second);
                }

                /*
                 * Since we have to collect this for all of the processes anyway,
                 * we might as well make use of it here to.
                 */
                procData = "cpu ";
                procData += cpuStat.first;
                procData += " ";
                procData += cpuStat.second;

                addData(env, ret, procData);
            }

            procStream.close();
            procStream.clear();

        } else {
            /*
             * Entry 1 to size-1 should contain all processes
             */
            entPid = procEntry->d_name;
            entUid = "0";
            entType = "0";
            entIsListed = false;

            /*
             * /proc contains more than just processes.
             * We only want numeric directories (Process Directories)
             */
            if (isIntegral(entPid)) {
                /*
                 * First lets check if this process was defined
                 * in the parsed process array arg
                 */
                for (PListArray::iterator it = processes.begin(); it != processes.end(); ++it) {
                    if (it->first == entPid) {
                        PListValue val = it->second;

                        entUid = val.first;
                        entType = val.second;
                        entIsListed = true;

                        break;
                    }
                }

                /*
                 * Otherwise sort Android from Linux process
                 * by checking the /proc/<pid>/cgroup file,
                 * if FLAG_SORT has been defined.
                 *
                 * This is mostly used as failsafe on Lollipop and above
                 * where we no longer has access to running applications listing,
                 * unless we are added as priv-app on /system.
                 */
                if (!entIsListed && (flags & FLAG_SORT) != 0) {
                    entFile = "/proc/";
                    entFile += entPid;
                    entFile += "/cgroup";

                    procStream.open(entFile.c_str());

                    if (procStream.good()) {
                        sortBegin = string::npos;

                        /*
                         * In prev Android version /proc/<pid>/cgroup contained the following:
                         *
                         *      2:cpu:<data>
                         *      1:cpuacct:<data>
                         *
                         * However, Android 6.0 added a new line at the beginning of this file,
                         * changing the one we want from the second line to the third line.
                         * This means that we can no longer just skip the first line, we now have to
                         * check each line to make sure that we get the correct one.
                         *
                         *      4:cpu:<data>
                         *      2:memory:<data>
                         *      1:cpuacct:<data>
                         */
                        if (DEBUG) {
                            mLogStream << "\nSorting Process";
                            mLogStream << "\n\t\tPID = ";
                            mLogStream << entPid;
                        }

                        while (getline(procStream, procData)) {
                            sortBegin = procData.find("uid");

                            if (sortBegin != string::npos) {
                                sortBegin += 4;

                                break;
                            }
                        }

                        if (sortBegin != string::npos) {
                            /*
                             * Before multi-user support, the line looked like
                             *
                             *      1:cpuacct:/uid/xxxxx
                             *
                             * Then multi-user came and the line was changed to
                             *
                             *      1:cpuacct:/uid_xxxxx/pid_yyyyy
                             *
                             * We want xxxxx
                             */
                            size_t sortEnd = procData.find_first_of("/", sortBegin);

                            if (sortEnd != string::npos) {
                                sortEnd -= sortBegin;
                            }

                            entUid = procData.substr(sortBegin, sortEnd);
                            entType = "1";
                            entIsListed = true;

                        } else if (DEBUG) {
                            procData = "Empty";
                        }

                        if (DEBUG) {
                            mLogStream << "\n\t\tCGroup Line = ";
                            mLogStream << procData;
                            mLogStream << "\n\t\tIs Android = ";
                            mLogStream << (entIsListed ? "TRUE" : "FALSE");
                        }
                    }

                    procStream.close();
                    procStream.clear();
                }

                /*
                 * Now we collect the info for the process
                 */
                if (entIsListed || (flags & FLAG_ALL) != 0) {
                    entFile = "/proc/";
                    entFile += entPid;
                    entFile += "/stat";

                    procStream.open(entFile.c_str());

                    if (procStream.good()) {
                        getline(procStream, procData);

                        entBuffer = "";
                        entBuffer += entType;
                        entBuffer += " ";
                        entBuffer += entUid;
                        entBuffer += " ";
                        entBuffer += cpuStat.first;
                        entBuffer += " ";
                        entBuffer += cpuStat.second;
                        entBuffer += " ";
                        entBuffer += procData;

                        if (DEBUG) {
                            mLogStream << "\nAdding Process";
                            mLogStream << "\n\t\tPID = ";
                            mLogStream << entPid;
                            mLogStream << "\n\t\tStat Line = ";
                            mLogStream << (entBuffer.length() > 100 ? entBuffer.substr(0, 100) + " ..." : entBuffer);
                        }

                        addData(env, ret, entBuffer);

                        listCount++;
                    }

                    procStream.close();
                    procStream.clear();
                }
            }

            scanCount++;
        }

    } while(((flags & FLAG_ALL) != 0 || (flags & FLAG_SORT) != 0 || processes.size() > 0) && procDir != NULL && (procEntry = readdir(procDir)) != NULL);

    /*
     * Close proc dir
     */
    closedir(procDir);


    /*
     * Return collected data
     */

    if (DEBUG) {
        mLogStream << "\nProcess scan Ended";
        mLogStream << "\n\t\tProcesses collected = ";
        mLogStream << listCount;
        mLogStream << "\n\t\tProcesses scanned = ";
        mLogStream << scanCount;
        mLogStream << "\n---------------------------------------------";
        mLogStream << "\n=============================================";

        flushLog();
    }

    return ret;
}