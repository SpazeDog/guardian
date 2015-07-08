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

import com.spazedog.guardian.Common;
import com.spazedog.guardian.application.Controller;
import com.spazedog.guardian.backend.xposed.WakeLockManager;
import com.spazedog.guardian.backend.xposed.WakeLockService.ProcessLockInfo;
import com.spazedog.guardian.scanner.containers.ProcEntity;
import com.spazedog.guardian.scanner.containers.ProcList;

import java.util.ArrayList;
import java.util.List;

public class ProcessScanner {
	
	public static enum ScanMode { 
		COLLECT_CPU, 				// Only get the CPU stat content
		COLLECT_PROCESSES, 			// Get the stat content for all currently running processes
		COLLECT_APPLICATIONS, 		// Only get the stat content for currently running Android processes
		EVALUATE_COLLECTION 		// Only get the stat content for the defined processes
	}
	
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
				
			} catch (Throwable e) {
                Common.LOG.Error(ProcessScanner.class.getName(), e.getMessage(), e);
                oIsLoaded = false;
			}
		}

		/*
		 * pidList:
		 * 					pidList[i] = Process ID
		 * 					pidList[i+1] = Process Type (Importance or 0 for Linux)
		 * 					...
		 */
		private static native String[][] getProcessList(int[] pidList, boolean collectFromList);
	
	/*
	 * ============================================================
	 */
		
	public static ProcList<?> execute(Context context, ScanMode mode, ProcList<?> processList) {
		if (hasLibrary()) {
			List<Integer> tempList = new ArrayList<Integer>();
			int[] pidList = null;
			
			if (mode == ScanMode.EVALUATE_COLLECTION && processList != null) {
				for (ProcEntity<?> entity : processList) {
					tempList.add(entity.getProcessId());
					tempList.add(entity.getProcessUid());
					tempList.add(entity.getImportance());
				}
				
			} else if (mode != ScanMode.EVALUATE_COLLECTION && mode != ScanMode.COLLECT_CPU) {
				ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
				List<RunningAppProcessInfo> runningProcesses = manager.getRunningAppProcesses();
				
				if (runningProcesses != null) {
					for (RunningAppProcessInfo androidProcess : runningProcesses) {
						tempList.add(androidProcess.pid);
						tempList.add(androidProcess.uid);
						tempList.add(androidProcess.importance);
					}
				}
			}
			
			pidList = new int[tempList.size()];
			for (int i=0; i < pidList.length; i++) {
				pidList[i] = tempList.get(i);
			}
			
			String[][] statCollection = getProcessList(pidList, mode != ScanMode.COLLECT_PROCESSES);
			
			if (statCollection.length > 0) {
                StatSystem systemProcess = new StatSystem();
				systemProcess.updateStat(statCollection[0], StatSystem.cast(processList));
				
				List<ProcessLockInfo> processLockInfo = null;
				WakeLockManager lockManager = ((Controller) context.getApplicationContext()).getWakeLockManager();
				if (lockManager != null) {
					processLockInfo = lockManager.getProcessLockInfo();
				}
				
				for (String[] stats : statCollection) {
					if (stats.length >= 8) {
						int type = Integer.valueOf(stats[0]);
						int uid = Integer.valueOf(stats[1]);
						int pid = Integer.valueOf(stats[2]);

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
                                    if (lockInfo.getUid() == uid && !lockInfo.isBroken() && lockInfo.getProcessName().equals(newEntity.getProcessName())) {
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
				}
				
				return systemProcess;
			}
		}
		
		return null;
	}
}
