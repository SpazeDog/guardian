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

package com.spazedog.guardian.scanner;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.util.Log;

import com.spazedog.guardian.Common;
import com.spazedog.guardian.Constants;
import com.spazedog.guardian.application.Controller;
import com.spazedog.guardian.backend.xposed.WakeLockManager;
import com.spazedog.guardian.backend.xposed.WakeLockService.ProcessLockInfo;
import com.spazedog.guardian.scanner.containers.ProcEntity;
import com.spazedog.guardian.scanner.containers.ProcList;

import java.util.List;

public class ProcessScanner {
	
	public static enum ScanMode { 
		COLLECT_CPU, 				// Only get the CPU stat content
		COLLECT_PROCESSES, 			// Get the stat content for all currently running processes
		COLLECT_APPLICATIONS, 		// Only get the stat content for currently running Android processes
		EVALUATE_COLLECTION 		// Only get the stat content for the defined processes
	}

    /*
     * These are also defined in ProcessScanner.cpp
     */
    private static final int FLAG_ALL = 0x00000001;
    private static final int FLAG_SORT = 0x00000002;

    private static boolean oCheckServiceManager = true;
	
	/*
	 * ============================================================
	 * JNI ProcessScanner Library
	 */
	
		private static boolean oIsLoaded = true;
		
		public static boolean hasLibrary() {
			return oIsLoaded;
		}
	
		static {
			try {
				System.loadLibrary("processScanner");

                jniInit(Constants.ENABLE_DEBUG);
				
			} catch (Throwable e) {
                Log.e("Java_GuardianScanner", e.getMessage(), e);
                oIsLoaded = false;
			}
		}

        private static native void jniInit(boolean debug);

		/*
		 * pidList:
		 * 					pidList[i] = Process ID
		 * 				    pidList[i+1] = Process UID
		 * 					pidList[i+2] = Process Type (1 or Importance for Android and 0 for Linux)
		 * 					...
		 */
		private static synchronized native List<String[]> jniScan(int[] processes, int flags);
	
	/*
	 * ============================================================
	 */

    public static ProcList<?> execute(Context context, ScanMode mode, ProcList<?> processList) {
        if (hasLibrary()) {
            int flags = mode == ScanMode.COLLECT_PROCESSES ? FLAG_ALL : 0;
            int [] processes = null;

            if (mode == ScanMode.EVALUATE_COLLECTION && processList != null) {
                processes = new int[processList.getEntitySize() * 3];
                int i = 0;

                for (ProcEntity<?> entity : processList) {
                    processes[i++] = entity.getProcessId();
                    processes[i++] = entity.getProcessUid();
                    processes[i++] = entity.getImportance();
                }

            } else if (oCheckServiceManager) {
                ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                List<RunningAppProcessInfo> runningProcesses = manager.getRunningAppProcesses();

                /*
                 * From Android 5.1.1 and onwards, we no longer have access to getRunningAppProcesses().
                 * All we will get is information about our own processes. That is unless we include the
                 * REAL_GET_TASK permission and copy the app to /system/priv-app.
                 *
                 * Since this feature used to help us get information like Importance, we keep it alive
                 * for older Android versions and open the possibility to bypass the security with a small Xposed Hook
                 * for those who enable the attached module.
                 */
                if (runningProcesses != null && runningProcesses.size() > 1) {
                    processes = new int[runningProcesses.size() * 3];
                    int i = 0;

                    for (RunningAppProcessInfo androidProcess : runningProcesses) {
                        processes[i++] = androidProcess.pid;
                        processes[i++] = androidProcess.uid;
                        processes[i++] = androidProcess.importance;
                    }

                } else {
                    /*
                     * Activate the process type sorting in the native scanner
                     */
                    flags |= FLAG_SORT;

                    /*
                     * Do not attempt this again
                     */
                    oCheckServiceManager = false;
                }

            } else {
                flags |= FLAG_SORT;
            }

            /*
             * Start scanning processes
             */
            List<String[]> statCollection = null;

            try {
                statCollection = jniScan(processes, flags);

                if (Constants.ENABLE_DEBUG) {
                    Log.d("Java_GuardianScanner", "Received " + statCollection.size() + " processes");
                }

            } catch (Throwable e) {
                throw new RuntimeException(e.getMessage(), e);
            }

            if (statCollection.size() > 0) {
                StatSystem systemProcess = new StatSystem(statCollection.size());
                systemProcess.updateStat(statCollection.remove(0), StatSystem.cast(processList));

                List<ProcessLockInfo> processLockInfo = null;
                WakeLockManager lockManager = ((Controller) context.getApplicationContext()).getWakeLockManager();
                if (lockManager != null) {
                    processLockInfo = lockManager.getProcessLockInfo();
                }

                for (String[] stats : statCollection) {
                    int type = Integer.valueOf(stats[0]);
                    int uid = Integer.valueOf(stats[1]);
                    int pid = Integer.valueOf(stats[2]);
                    String processName = stats[3];

                    ProcEntity<?> oldEntity = processList != null ? processList.findEntity(pid) : null;

                    if (type > 0) {
                        EntityAndroid newEntity = new EntityAndroid();
                        ProcessLockInfo newLockInfo = null;

                        if (processLockInfo != null) {
                            for (ProcessLockInfo lockInfo : processLockInfo) {
                                /*
                                 * It is much faster to compare two int values than long string values.
                                 * But one uid might have multiple processes, so we need to check this to, but no need if the uid does not match.
                                 */
                                if (lockInfo.getUid() == uid && !lockInfo.isBroken() && lockInfo.getProcessName().equals(processName)) {
                                    newLockInfo = lockInfo; break;
                                }
                            }
                        }

                        newEntity.updateStat(stats, EntityAndroid.cast(oldEntity), newLockInfo);
                        systemProcess.addEntity(newEntity);

                    } else {
                        EntityLinux newEntity = new EntityLinux();
                        newEntity.updateStat(stats, EntityLinux.cast(oldEntity));
                        systemProcess.addEntity(newEntity);
                    }
                }

                return systemProcess;
            }
        }

        return null;
    }
}
