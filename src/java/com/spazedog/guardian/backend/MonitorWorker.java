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

package com.spazedog.guardian.backend;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.util.Pair;

import com.spazedog.guardian.ActivityLaunch;
import com.spazedog.guardian.Common;
import com.spazedog.guardian.Constants;
import com.spazedog.guardian.R;
import com.spazedog.guardian.application.Controller;
import com.spazedog.guardian.application.Settings;
import com.spazedog.guardian.backend.xposed.WakeLockManager;
import com.spazedog.guardian.backend.xposed.WakeLockService.ProcessLockInfo;
import com.spazedog.guardian.backend.xposed.WakeLockService.WakeLockInfo;
import com.spazedog.guardian.db.AlertsDB;
import com.spazedog.guardian.scanner.EntityAndroid;
import com.spazedog.guardian.scanner.ProcessScanner;
import com.spazedog.guardian.scanner.ProcessScanner.ScanMode;
import com.spazedog.guardian.scanner.containers.ProcEntity;
import com.spazedog.guardian.scanner.containers.ProcList;
import com.spazedog.lib.rootfw4.RootFW;
import com.spazedog.lib.rootfw4.Shell;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class MonitorWorker {
	
	public static class ThresholdItem implements Parcelable {
		public long LOCK_TIME = 0l;
		public double CPU_USAGE = 0d;
		public int LOCK_TIME_COUNT = 0;
		public int CPU_USAGE_COUNT = 0;
		public boolean LOCK_TIME_INVALID = false;
		public boolean CPU_USAGE_INVALID = false;
		
		public ThresholdItem() {}
		
		public ThresholdItem(Parcel in) {
			LOCK_TIME = in.readLong();
			CPU_USAGE = in.readDouble();
			LOCK_TIME_COUNT = in.readInt();
			CPU_USAGE_COUNT = in.readInt();
			LOCK_TIME_INVALID = in.readInt() > 0;
			CPU_USAGE_INVALID = in.readInt() > 0;
		}
		
		@Override
		public void writeToParcel(Parcel out, int flags) {
			out.writeLong(LOCK_TIME);
			out.writeDouble(CPU_USAGE);
			out.writeInt(LOCK_TIME_COUNT);
			out.writeInt(CPU_USAGE_COUNT);
			out.writeInt(LOCK_TIME_INVALID ? 1 : 0);
			out.writeInt(CPU_USAGE_INVALID ? 1 : 0);
		}
		
		@Override
		public int describeContents() {
			return 0;
		}
		
		public static final Parcelable.Creator<ThresholdItem> CREATOR = new Parcelable.Creator<ThresholdItem>() {
			@Override
			public ThresholdItem createFromParcel(Parcel in) {
				return new ThresholdItem(in);
			}
			
			@Override
			public ThresholdItem[] newArray(int size) {
				return new ThresholdItem[size];
			}
		};
	}
	
	public static class ThresholdMap implements Parcelable {
		@SuppressLint("UseSparseArrays")
		private Map<Integer, ThresholdItem> mMap = new HashMap<Integer, ThresholdItem>();
		
		public ThresholdMap() {}
		
		public int size() {
			return mMap.size();
		}
		
		public void clear() {
			mMap.clear();
		}
		
		public ThresholdItem remove(int pid) {
			return mMap.remove(pid);
		}
		
		public boolean hasPid(int pid) {
			return mMap.containsKey(pid);
		}
		
		public void put(int pid, ThresholdItem value) {
			mMap.put(pid, value);
		}
		
		public ThresholdItem get(int pid) {
			return mMap.get(pid);
		}
		
		public Set<Entry<Integer, ThresholdItem>> entrySet() {
			return mMap.entrySet();
		}
		
		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel out, int flags) {
			out.writeInt(mMap.size());
			
			for (Entry<Integer, ThresholdItem> entry : mMap.entrySet()) {
				out.writeInt(entry.getKey());
				out.writeParcelable(entry.getValue(), flags);
			}
		}

		public ThresholdMap(Parcel in) {
			int size = in.readInt();
			
			for (int i=0; i < size; i++) {
				int key = in.readInt();
				ThresholdItem value = (ThresholdItem) in.readParcelable(ThresholdItem.class.getClassLoader());
				
				mMap.put(key, value);
			}
		}
		
		public static final Parcelable.Creator<ThresholdMap> CREATOR = new Parcelable.Creator<ThresholdMap>() {
			@Override
			public ThresholdMap createFromParcel(Parcel in) {
				return new ThresholdMap(in);
			}
			
			@Override
			public ThresholdMap[] newArray(int size) {
				return new ThresholdMap[size];
			}
		};
	}
	
	protected Controller mController;
	protected Settings mSettings;
	protected boolean mIsInteractive;
	protected int mThreshold;
	protected Bundle mData;
	protected ThresholdMap mThresholdData = new ThresholdMap();
	
	@SuppressWarnings("deprecation")
	@SuppressLint("NewApi")
	public MonitorWorker(Controller controller, Bundle data) {
		PowerManager pm = (PowerManager) controller.getSystemService(Context.POWER_SERVICE);
		AudioManager am = (AudioManager) controller.getSystemService(Context.AUDIO_SERVICE);
		
		mData = data;
		mController = controller;
		mSettings = controller.getSettings();
		mIsInteractive = am.getMode() != AudioManager.MODE_NORMAL
				|| am.isMusicActive()
				|| (android.os.Build.VERSION.SDK_INT >= 20 ? pm.isInteractive() : pm.isScreenOn());
		
		mThreshold = mSettings.getServiceThreshold(mIsInteractive);
	}
	
	public void start() {
		ThresholdMap lastThresholdData = mData.getParcelable("evaluate");
		ScanMode mode = mSettings.monitorLinux() ? ScanMode.COLLECT_PROCESSES : ScanMode.COLLECT_APPLICATIONS;
		ProcList<?> processList = ProcessScanner.execute(mController, mode, (ProcList<?>) mData.getParcelable("processes"));
		boolean scanWakelocks = !mIsInteractive && mController.getWakeLockManager() != null;
		
		Common.LOG.Debug(this, "Beginning analizing the scan result, Process Count = " + (processList != null ? processList.getEntitySize() : 0) + ", Evaluation Count = " + (lastThresholdData != null ? lastThresholdData.size() : 0));
		
		if (processList != null) {
			boolean validThreshold = checkProcessThreshold(processList);
			boolean validWakelocks = !scanWakelocks || checkProcessWakelocks(processList);
			
			if ((!validThreshold || !validWakelocks) && lastThresholdData != null) {
				Common.LOG.Debug(this, "Checking possible rough processes, Rough Count = " + mThresholdData.size());
				
				Set<Pair<? extends ProcEntity<?>, ThresholdItem>> alertList = new HashSet<Pair<? extends ProcEntity<?>, ThresholdItem>>();
				
				for (Entry<Integer, ThresholdItem> thresholdEntry : lastThresholdData.entrySet()) {
					int pid = thresholdEntry.getKey();
					ProcEntity<?> entity = processList.findEntity(pid);
					ThresholdItem lastThresholdItem = thresholdEntry.getValue();
					ThresholdItem newThresholdItem = mThresholdData.remove(pid);
					
					if (entity != null) {
						if (newThresholdItem != null && newThresholdItem.CPU_USAGE_INVALID) {
							if (lastThresholdItem.CPU_USAGE_COUNT > 0 && newThresholdItem.CPU_USAGE >= lastThresholdItem.CPU_USAGE) {
								Common.LOG.Debug(this, "Adding process to the alert list, Check Count = " + lastThresholdItem.CPU_USAGE_COUNT + ", CPU Usage = " + newThresholdItem.CPU_USAGE + "%, PID = " + entity.getProcessId() + ", Process Name = " + entity.getProcessName());
								alertList.add( Pair.create(entity, newThresholdItem) );
								
							} else {
								if (newThresholdItem.CPU_USAGE >= lastThresholdItem.CPU_USAGE) {
									Common.LOG.Debug(this, "Letting the process calm down until next check, Check Count = " + lastThresholdItem.CPU_USAGE_COUNT + ", CPU Usage = " + newThresholdItem.CPU_USAGE + "%, PID = " + entity.getProcessId() + ", Process Name = " + entity.getProcessName());
								} else {
									Common.LOG.Debug(this, "The process has calmed down a bit since last check, checking again later, Check Count = " + lastThresholdItem.CPU_USAGE_COUNT + ", CPU Usage = " + newThresholdItem.CPU_USAGE + "%, PID = " + entity.getProcessId() + ", Process Name = " + entity.getProcessName());
								}
								
								newThresholdItem.CPU_USAGE_COUNT = lastThresholdItem.CPU_USAGE_COUNT + 1;
								mThresholdData.put(pid, newThresholdItem);
							}
							
						} else if (lastThresholdItem.CPU_USAGE_INVALID) {
							Common.LOG.Debug(this, "The process has gone down below the threshold, not checking it further, PID = " + entity.getProcessId() + ", Process Name = " + entity.getProcessName());
						}
						
						if (newThresholdItem != null && newThresholdItem.LOCK_TIME_INVALID) {
							if (lastThresholdItem.LOCK_TIME_COUNT > 0) {
								Common.LOG.Debug(this, "Adding process to the alert list, Check Count = " + lastThresholdItem.LOCK_TIME_COUNT + ", WakeLock Time = " + newThresholdItem.LOCK_TIME + "ms, PID = " + entity.getProcessId() + ", Process Name = " + entity.getProcessName());
								alertList.add( Pair.create(entity, newThresholdItem) );
								
							} else {
								Common.LOG.Debug(this, "Letting the process get a chance to release the lock until next check, Check Count = " + lastThresholdItem.LOCK_TIME_COUNT + ", WakeLock Time = " + newThresholdItem.LOCK_TIME + "ms, PID = " + entity.getProcessId() + ", Process Name = " + entity.getProcessName());
								newThresholdItem.LOCK_TIME_COUNT = lastThresholdItem.LOCK_TIME_COUNT + 1;
								mThresholdData.put(pid, newThresholdItem);
							}
							
						} else if (lastThresholdItem.LOCK_TIME_INVALID) {
							Common.LOG.Debug(this, "The process has released the lock, not checking it further, PID = " + entity.getProcessId() + ", Process Name = " + entity.getProcessName());
						}
					}
				}
				
				if (alertList.size() > 0) {
					Common.LOG.Debug(this, "Alerting user about rough processes, Rough Count = " + alertList.size());
					sendUserAlert(alertList);
				}
			}
			
			/*
			 * Parse everything to the next scan cycle
			 */
			if (mThresholdData.size() > 0) {
				mData.putParcelable("evaluate", mThresholdData);
				mData.putInt("timeout", (mSettings.getServiceInterval() >= (10 * 60000) ? 5 : 1) * 60000);
				
			} else {
				mData.remove("evaluate");
				mData.remove("timeout");
			}
			
			mData.putParcelable("processes", processList);
		}
	}
	
	protected boolean checkProcessThreshold(ProcList<?> processList) {
		double cpuUsage = processList.getCpuUsage();
		boolean valid = true;
		
		if (processList != null && (cpuUsage > mThreshold || (cpuUsage > 0 && Constants.ENABLE_REPORT_TESTING))) {
			Common.LOG.Debug(this, "The CPU is above the threshold, CPU Usage = " + cpuUsage + "%, CPU Threshold = " + mThreshold + "%");
			
			for (ProcEntity<?> entity : processList) {
				boolean important = isProcessImportant(entity.getImportance());
				double usage = entity.getCpuUsage();
				
				if ((usage > mThreshold && !important) || (usage > 0 && Constants.ENABLE_REPORT_TESTING)) {
					Common.LOG.Debug(this, "Process detected above the threshold, PID = " + entity.getProcessId() + ", Process Name = " + entity.getProcessName() + ", CPU Usage = " + usage + "%, CPU Threshold = " + mThreshold + "%");
					
					int pid = entity.getProcessId();
					ThresholdItem item = mThresholdData.get(pid);
					
					if (item == null) {
						item = new ThresholdItem();
					}
					
					item.CPU_USAGE = usage;
					item.CPU_USAGE_INVALID = true;
					valid = false;
					
					mThresholdData.put(pid, item);
				}
			}
			
			if (valid) {
				Common.LOG.Debug(this, " - No processes seams to exceed the threshold");
			}
			
		} else {
			Common.LOG.Debug(this, "The CPU is beneath the threshold, CPU Usage = " + cpuUsage + "%, CPU Threshold = " + mThreshold + "%");
		}
		
		return valid;
	}
	
	protected boolean checkProcessWakelocks(ProcList<?> processList) {
		long lockTime = mSettings.getServiceWakeLockTime();
		boolean valid = true;
		
		for (ProcEntity<?> entity : processList) {
            EntityAndroid androidEntity = EntityAndroid.cast(entity);

			if (androidEntity != null) {
				ProcessLockInfo lockInfo = androidEntity.getProcessLockInfo();
				
				if (lockInfo != null) {
					List<WakeLockInfo> wakeLocks = lockInfo.getWakeLocks();
					
					for (WakeLockInfo wakeLock : wakeLocks) {
						if (wakeLock.getTime() > lockTime || (wakeLock.getTime() > 0 && Constants.ENABLE_REPORT_TESTING)) {
							Common.LOG.Debug(this, "Active wakelock detected, PID = " + entity.getProcessId() + ", Process Name = " + entity.getProcessName() + ", Wakelock Time = " + wakeLock.getTime());
							
							int pid = entity.getProcessId();
							ThresholdItem item = mThresholdData.get(pid);
							
							if (item == null) {
								item = new ThresholdItem();
							}
							
							item.LOCK_TIME = wakeLock.getTime();
							item.LOCK_TIME_INVALID = true;
							valid = false;
							
							mThresholdData.put(pid, item);
							
							/*
							 * We only need to know if one of it's locks has been required to long
							 */
							break;
						}
					}
				}
			}
		}
		
		if (valid) {
			Common.LOG.Debug(this, " - No processes seams to misuse their wakelocks");
		}
		
		return valid;
	}
	
	protected boolean isProcessImportant(int importance) {
		return mIsInteractive && 
				(importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
						importance == RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE ||
								importance == RunningAppProcessInfo.IMPORTANCE_VISIBLE);
	}
	
	protected void sendUserAlert(Set<Pair<? extends ProcEntity<?>, ThresholdItem>> entityList) {
		Intent intent = new Intent(mController, ActivityLaunch.class);
		intent.putExtra("tab.id", "tab_process_alerts");
		
		PendingIntent pendingIntent = PendingIntent.getActivity(mController, 0, intent, 0);
		NotificationCompat.Builder builder = new NotificationCompat.Builder(mController)
			.setContentTitle("Rough Process Alert")
			.setContentText("One or more rough processes has been detected")
			.setSmallIcon(R.mipmap.ic_launcher)
			.setContentIntent(pendingIntent)
			.setAutoCancel(true)
			.setNumber(entityList.size());
	
		Notification notify = builder.build();
		notify.defaults = Notification.DEFAULT_ALL;
		
		NotificationManager notificationManager = (NotificationManager) mController.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(mController.getPackageName(), MonitorService.NOTIFICATION_ID);
		notificationManager.notify(mController.getPackageName(), MonitorService.NOTIFICATION_ID, notify);
		
		AlertsDB db = new AlertsDB(mController);
		
		for (Pair<? extends ProcEntity<?>, ThresholdItem> pair : entityList) {
			db.addProcessEntity(pair.first);
		}
		
		db.close();
		
		String thresholdAction = mSettings.getServiceAction(mIsInteractive);
		String lockAction = mSettings.getServiceWakeLockAction();
		
		for (Pair<? extends ProcEntity<?>, ThresholdItem> pair : entityList) {
            ProcEntity<?> entity = pair.first;
			ThresholdItem thesholdItem = pair.second;
			
			if (thesholdItem.CPU_USAGE_INVALID && !"notify".equals(thresholdAction)) {
				Common.LOG.Debug(this, "Force closing process, PID = " + entity.getProcessId() + ", Process Name = " + entity.getProcessName());
				
				String packageName = entity.getDataLoader(mController).getPackageLabel();
				int importance = entity.getImportance();
				
				/*
				 * These levels cannot be killed without root
				 * 
				 * TODO: 
				 * 			Indicate in the log that an action was taken
				 */
				boolean perceptible = packageName != null && entity.isPerceptible();
				
				if (!thresholdAction.equals("reboot") && (!perceptible || mSettings.isRootEnabled())) {
					if (mSettings.isRootEnabled() && RootFW.connect() && RootFW.isRoot()) {
						RootFW.getProcess(entity.getProcessId()).kill();
						
					} else {
						ActivityManager manager = (ActivityManager) mController.getSystemService(Context.ACTIVITY_SERVICE);
						manager.killBackgroundProcesses(packageName);
					}
					
				} else if (thresholdAction.equals("reboot") || thresholdAction.equals("auto")) {
					if (mSettings.isRootEnabled() && RootFW.connect()) {
						RootFW.getDevice().reboot();
						
					} else {
						/*
						 * This might not work on SELinux restricted devices
						 */
						Shell shell = new Shell(false);
						shell.getDevice().reboot();
						shell.destroy();
					}
				}
			}
			
			if (thesholdItem.LOCK_TIME_INVALID && !"notify".equals(lockAction)) {
				Common.LOG.Debug(this, "Releasing process wakelocks, PID = " + entity.getProcessId() + ", Process Name = " + entity.getProcessName());
				
				WakeLockManager manager = mController.getWakeLockManager();
				
				if (manager != null) {
					manager.releaseForPid(entity.getProcessId());
				}
			}
		}
	}
}
