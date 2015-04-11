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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.IntentService;
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
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.spazedog.guardian.ActivityLaunch;
import com.spazedog.guardian.Constants;
import com.spazedog.guardian.R;
import com.spazedog.guardian.application.Controller;
import com.spazedog.guardian.application.Controller.IServiceBinder;
import com.spazedog.guardian.application.Settings;
import com.spazedog.guardian.db.AlertsDB;
import com.spazedog.guardian.scanner.IProcess.IProcessList;
import com.spazedog.guardian.scanner.IProcessEntity;
import com.spazedog.guardian.scanner.ProcessScanner;
import com.spazedog.guardian.scanner.ProcessScanner.ScanMode;
import com.spazedog.lib.rootfw4.RootFW;
import com.spazedog.lib.rootfw4.Shell;

/*
 * This service is build to run in two modes, Alarm Scheduled mode and Persistent mode. 
 */
public class MonitorService extends IntentService implements IServiceBinder {
	
	private static int generateRandom() {
		Random rand = new Random();
		int number = 0;
		
		for (int i=0; i < 10; i++) {
			/*
			 * number += [0-9] * 10^x
			 */
			number += Math.pow(10, i) * rand.nextInt(9);
		}
		
		return number;
	}
	
	public static void doLog(String msg) {
		if (Constants.ENABLE_DEBUG) {
			Log.d(TAG, msg);
		}
	}
	
	public static final String TAG = MonitorService.class.getName();
	
	private static final int PERSISTENT_ID = generateRandom();
	private static final int NOTIFICATION_ID = generateRandom();
	
	protected Bundle mBundle = new Bundle();
	protected ThresholdMap mThresholdAlerts;
	
	protected Controller mController;
	protected Settings mSettings;
	protected WakeLock mWakeLock;
	protected Boolean mIsInteractive;
	protected Boolean mIsPersistent;
	protected Integer mThreshold;
	
	protected Boolean mActivePersistence = false;
	
	/*
	 * Google has not made Maps parcelable, 
	 * so we need to make a small custom one that we can use
	 */
	public static class ThresholdMap implements Parcelable {
		@SuppressLint("UseSparseArrays")
		private Map<Integer, Double> mMap = new HashMap<Integer, Double>();
		
		public ThresholdMap() {}
		
		public int size() {
			return mMap.size();
		}
		
		public void clear() {
			mMap.clear();
		}
		
		public void put(int pid, double usage) {
			mMap.put(pid, usage);
		}
		
		public double get(int pid) {
			if (mMap.containsKey(pid)) {
				return mMap.get(pid);
			}
			
			return -1d;
		}
		
		public double remove(int pid) {
			if (mMap.containsKey(pid)) {
				return mMap.remove(pid);
			}
			
			return -1d;
		}
		
		public boolean hasPid(int pid) {
			return mMap.containsKey(pid);
		}
		
		public Set<Entry<Integer, Double>> entrySet() {
			return mMap.entrySet();
		}
		
		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel out, int flags) {
			out.writeInt(mMap.size());
			
			for (Entry<Integer, Double> entry : mMap.entrySet()) {
				out.writeInt(entry.getKey());
				out.writeDouble(entry.getValue());
			}
		}

		public ThresholdMap(Parcel in) {
			int size = in.readInt();
			
			for (int i=0; i < size; i++) {
				int key = in.readInt();
				double value = in.readDouble();
				
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

	public MonitorService() {
		super("Guardian.MonitorService");
		
		setIntentRedelivery(true);
	}
	
	@Override
	public boolean ping() {
		return mActivePersistence;
	}
	
	@Override
	public void stop() {
		if (mActivePersistence) {
			mIsPersistent = false;
			
			synchronized(this) {
				notifyAll();
			}
		}
	}
	
	@Override
	protected void onHandleIntent(Intent intent) {
		mController = (Controller) getApplicationContext();
		mSettings = mController.getSettings();
		mIsPersistent = mSettings.getServiceEngine().equals("persistent");
		
		if (mIsPersistent) {
			doLog("Starting persistent service");
			
			mActivePersistence = true;
			mController.setServiceBinder(this);
			
			PendingIntent pendingIntent = PendingIntent.getActivity(mController, 0, new Intent(mController, ActivityLaunch.class), 0);
			NotificationCompat.Builder builder = new NotificationCompat.Builder(mController)
			.setContentTitle("Guardian")
			.setContentText("Process monitoring is active")
			.setSmallIcon(R.mipmap.ic_launcher)
			.setContentIntent(pendingIntent)
			.setOngoing(true);
			
			startForeground(PERSISTENT_ID, builder.build());
			
		} else {
			doLog("Instantiating scheduled service");
		}
		
		do {
			onPreMonitor();
			onMonitor(intent);
			onPostMonitor();
			
			if (mIsPersistent) {
				int timeout = mBundle.getInt("timeout", mSettings.getServiceInterval());
				long time = SystemClock.elapsedRealtime() + timeout;
				
				while (mIsPersistent && timeout > 0) {
					synchronized(this) {
						try {
							/*
							 * In deep sleep this counter will be paused by Android. 
							 * It is meant to act this way as we have no reason to scan 
							 * processes if the device has gone into this state. 
							 * We are meant to protect devices from rough processes, 
							 * not to become one.
							 */
							wait( timeout ); break;
							
						} catch (Throwable e) {
							timeout = (int) (time - SystemClock.elapsedRealtime());
						}
					}	
				}
			}
			
		} while (mIsPersistent);
		
		if (mActivePersistence) {
			doLog("Stopping persistent service");
			
			mActivePersistence = false;
			mController.setServiceBinder(null);
			
			stopForeground(true);
			
		} else {
			doLog("Resetting timeout on scheduled service");
			
			int timeout = mBundle.getInt("timeout", mSettings.getServiceInterval());
			
			/*
			 * This uses the same rules as the persistent counter. 
			 * It uses an alarm type that will not awaken the device, 
			 * but only start this service if the device is already awake. 
			 */
			mController.resetScheduler(timeout, mBundle);
		}
		
		mController = null;
		mSettings = null;
	}
	
	@SuppressWarnings("deprecation")
	@SuppressLint("NewApi")
	protected void onPreMonitor() {
		PowerManager pm = (PowerManager) mController.getSystemService(Context.POWER_SERVICE);
		AudioManager am = (AudioManager) mController.getSystemService(Context.AUDIO_SERVICE);
		
		mIsInteractive = am.getMode() != AudioManager.MODE_NORMAL
				|| am.isMusicActive()
				|| (android.os.Build.VERSION.SDK_INT >= 20 ? pm.isInteractive() : pm.isScreenOn());
		
		mThreshold = mSettings.getServiceThreshold(mIsInteractive);
		
		if (!mIsPersistent) {
			/*
			 * On scheduled service make sure that the process is not terminated half way though due to deep sleep. 
			 * If it does, the scheduler will not be reset and this service is by definition dead. 
			 */
			mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "guardian_monitorservice");
		}
	}
	
	protected void onPostMonitor() {
		if (mWakeLock != null && mWakeLock.isHeld()) {
			mWakeLock.release();
			mWakeLock = null;
		}
	}
	
	protected void onMonitor(Intent intent) {
		doLog("--------------------------------------------------------");
		doLog("Starting process scanning at " + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US).format(new Date()));
		
		mThresholdAlerts = new ThresholdMap();
		
		if (mSettings.getServiceEngine().equals("persistent")) {
			doProcessScanning( mBundle );
			
		} else {
			Bundle bundle = intent.getExtras();
			
			if (bundle == null) {
				bundle = new Bundle();
			}
			
			doProcessScanning( bundle );
		}
		
		doLog("Process scanning ended at " + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US).format(new Date()));
		doLog("========================================================");
	}
	
	/*
	 * TODO:
	 * 			It might be a good idea to check whether or not a process above the threshold, 
	 * 			is being used by another process. This is possible for Android processes. 
	 */
	protected void doProcessScanning(Bundle bundle) {
		ThresholdMap evaluations = bundle.getParcelable("evaluate");
		ScanMode mode = mSettings.monitorLinux() ? ScanMode.COLLECT_PROCESSES : ScanMode.COLLECT_APPLICATIONS;
		IProcessList processList = ProcessScanner.execute(mController, mode, (IProcessList) bundle.getParcelable("processes"));
		
		if (processList != null) {
			/*
			 * Start by checking if there is anything wrong
			 */
			if (!checkProcessThreshold(processList) && evaluations != null) {
				doLog("Evaluating processes from the last scan");
				
				Set<IProcessEntity> entityList = new HashSet<IProcessEntity>();
				
				/*
				 * Check processes that was above the threshold during last scan
				 */
				if (evaluations != null) {
					for (Entry<Integer, Double> entry : evaluations.entrySet()) {
						IProcessEntity entity = processList.findEntity(entry.getKey());
						double usage = mThresholdAlerts.remove(entry.getKey());
						
						if (entity != null) {
							if (usage > 0) {
								/*
								 * Do not report yet if the process usage is going down
								 */
								if (usage >= entry.getValue()) {
									doLog(" - The process " + entity.getProcessName() + "(" + entity.getProcessId() + ") has been added to the alert list");
									
									entityList.add(entity);
									
								} else {
									/*
									 * This process seams to stay above the threshold so let's report it
									 */
									doLog(" - The process " + entity.getProcessName() + "(" + entity.getProcessId() + ") has gone down since last check, checking again later");
									
									mThresholdAlerts.put(entry.getKey(), usage);
								}
								
							} else {
								doLog(" - The process " + entity.getProcessName() + "(" + entity.getProcessId() + ") has gone down below the threshold with " + entity.getCpuUsage() + "%");
							}
						}
					}
				}
				
				if (entityList.size() > 0) {
					doLog("Alert user about the last scan results");
					
					sendUserAlert(entityList);
					
				} else {
					doLog("Everything from the last scan seams to have been sorted out");
				}
			}
			
			/*
			 * Parse everything to the next scan cycle
			 */
			if (mThresholdAlerts.size() > 0) {
				mBundle.putParcelable("evaluate", mThresholdAlerts);
				mBundle.putInt("timeout", (mSettings.getServiceInterval() >= (10 * 60000) ? 5 : 1) * 60000);
				
			} else {
				/*
				 * If we are running a persistent service, this bundle is reused. 
				 * So we need to clean it up manually to ensure wrong data is not parsed back here.
				 */
				mBundle.remove("evaluate");
				mBundle.remove("timeout");
			}
			
			mBundle.putParcelable("processes", processList);
		}
	}
	
	private boolean checkProcessThreshold(IProcessList processList) {
		double cpuUsage = processList.getCpuUsage();
		
		if (processList != null && (cpuUsage > mThreshold || (cpuUsage > 0 && Constants.ENABLE_REPORT_TESTING))) {
			doLog("CPU Usage is above the threshold with " + cpuUsage + "% vs. " + mThreshold + "%");
			
			for (IProcessEntity entity : processList) {
				int importance = entity.getImportance();
				boolean important = mIsInteractive && 
						(importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
								importance == RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE ||
										importance == RunningAppProcessInfo.IMPORTANCE_VISIBLE);
				
				double usage = entity.getCpuUsage();

				if ((usage > mThreshold && !important) || (usage > 0 && Constants.ENABLE_REPORT_TESTING)) {
					doLog(" - The process " + entity.getProcessName() + "(" + entity.getProcessId() + ") is above the threshold with " + usage + "%");
					
					mThresholdAlerts.put(entity.getProcessId(), usage);
				}
			}
			
			if (mThresholdAlerts.size() == 0) {
				doLog(" - No processes seams to exceed the threshold");
			}
			
		} else {
			doLog("CPU Usage is beneath the threshold with " + cpuUsage + "% vs. " + mThreshold + "%");
		}
		
		return mThresholdAlerts.size() == 0;
	}
	
	private void sendUserAlert(Set<IProcessEntity> entityList) {
		Intent intent = new Intent(mController, ActivityLaunch.class);
		intent.putExtra("tab.id", "tab_process_alerts");
		
		PendingIntent pendingIntent = PendingIntent.getActivity(mController, 0, intent, 0);
		
		NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle()
			.setBigContentTitle("The following processes was detected above the threshold");
		
		AlertsDB db = new AlertsDB(mController);
		
		for (IProcessEntity entity : entityList) {
			String entityName = entity.loadPackageLabel(mController);
			
			if (entityName == null) {
				entityName = entity.getProcessName();
			}
			
			style.addLine(entityName + " at " + entity.getCpuUsage() + "%");
			db.addProcessEntity(entity);
		}
		
		db.close();
		
		NotificationCompat.Builder builder = new NotificationCompat.Builder(mController)
			.setContentTitle("Process Threshold Alert")
			.setContentText("One or more processes has been detected above the threshold")
			.setSmallIcon(R.mipmap.ic_launcher)
			.setContentIntent(pendingIntent)
			.setAutoCancel(true)
			.setNumber(entityList.size())
			.setStyle(style);
		
		Notification notify = builder.build();
		notify.defaults = Notification.DEFAULT_ALL;
		
		NotificationManager notificationManager = (NotificationManager) mController.getSystemService(NOTIFICATION_SERVICE);
		notificationManager.cancel(mController.getPackageName(), NOTIFICATION_ID);
		notificationManager.notify(mController.getPackageName(), NOTIFICATION_ID, notify);
		
		String action = mSettings.getServiceAction(mIsInteractive);
		
		if (!action.equals("notify")) {
			for (IProcessEntity entity : entityList) {
				String packageName = entity.loadPackageLabel(mController);
				int importance = entity.getImportance();
				
				/*
				 * These levels cannot be killed without root
				 * 
				 * TODO: 
				 * 			Indicate in the log that an action was taken
				 */
				boolean perceptible = packageName != null && 
						(importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
								importance == RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE ||
										importance == RunningAppProcessInfo.IMPORTANCE_VISIBLE || importance == 0);
				
				if (!action.equals("reboot") && (!perceptible || mSettings.isRootEnabled())) {
					if (mSettings.isRootEnabled() && RootFW.connect() && RootFW.isRoot()) {
						RootFW.getProcess(entity.getProcessId()).kill();
						
					} else {
						ActivityManager manager = (ActivityManager) mController.getSystemService(Context.ACTIVITY_SERVICE);
						manager.killBackgroundProcesses(packageName);
					}
					
				} else if (action.equals("reboot") || action.equals("auto")) {
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
			
			if (RootFW.isConnected()) {
				RootFW.disconnect();
			}
		}
	}
}
