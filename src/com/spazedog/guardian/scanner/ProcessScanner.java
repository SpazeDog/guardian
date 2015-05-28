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

import java.util.ArrayList;
import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.util.Log;

import com.spazedog.guardian.Common;
import com.spazedog.guardian.application.Controller;
import com.spazedog.guardian.backend.xposed.WakeLockManager;
import com.spazedog.guardian.backend.xposed.WakeLockService.ProcessLockInfo;
import com.spazedog.guardian.scanner.IProcess.IProcessList;
import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectMethod;
import com.spazedog.lib.reflecttools.utils.ReflectConstants.Match;

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
	
		static {
			try {
				System.loadLibrary("processScanner");
				
			} catch (Throwable e) {
				/*
				 * Bypass security restricted xposed modules
				 */
				try {
					ReflectClass clazz = ReflectClass.forClass(System.class);
					ReflectMethod method = clazz.findMethod("loadLibrary", Match.BEST, String.class);
					
					method.invokeOriginal("processScanner");
				
				} catch (Throwable ei) {}
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
		
	public static IProcessList execute(Context context, ScanMode mode, IProcessList processList) {
		List<Integer> tempList = new ArrayList<Integer>();
		int[] pidList = null;
		
		if (mode == ScanMode.EVALUATE_COLLECTION && processList != null) {
			for (IProcessEntity entity : processList) {
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
			ProcessSystem systemProcess = new ProcessSystem();
			systemProcess.updateStat(statCollection[0], processList);
			
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
					
					IProcessEntity oldEntity = processList != null ? processList.findEntity(pid) : null;
					IProcessEntity newEntity = type > 0 ? new ProcessEntityAndroid() : new ProcessEntityLinux();
					newEntity.updateStat(stats, oldEntity);
					
					if (processLockInfo != null && type > 0) {
						for (ProcessLockInfo lockInfo : processLockInfo) {
							/*
							 * It is much faster to compare two int values than long string values. 
							 * But one uid might have multiple processes, so we need to check this to, but no need if the uid does not match. 
							 */
							
							Common.LOG.Debug(IProcessList.class.getName(), "Checking Wakelock Info\n\t\tLock Name = " + lockInfo.getProcessName() + "\n\t\tProcess Name = " + newEntity.getProcessName());
							
							if (lockInfo.getUid() == uid && lockInfo.getProcessName().equals(newEntity.getProcessName())) {
								((ProcessEntityAndroid) newEntity).updateLocks(lockInfo); break;
							}
						}
					}
					
					systemProcess.addEntity(newEntity);
				}
			}
			
			return systemProcess;
		}
		
		return null;
	}
}
