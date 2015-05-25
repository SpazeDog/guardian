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

package com.spazedog.guardian.application;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;

import com.spazedog.guardian.application.Settings.ISettingsListener;
import com.spazedog.guardian.application.Settings.ISettingsWrapper;
import com.spazedog.guardian.application.Settings.Type;
import com.spazedog.guardian.backend.MonitorReceiver;
import com.spazedog.guardian.backend.xposed.WakeLockManager;
import com.spazedog.guardian.utils.AbstractHandler;

public class Controller extends Application implements ISettingsWrapper, ISettingsListener {
	
	/*
	 * This are service status codes that are sent via the internal message system to Activities and Fragments. 
	 * Status.PENDING means that a stop or start request has been sent to the service and Controller is now awaiting response from the service. 
	 * Status.STARTED and Status.STOPPED is the response that Controller received from the service and indicates it's current status. 
	 */
	public static class Status {
		public static final int STOPPED = -1;
		public static final int PENDING = 0;
		public static final int STARTED = 1;
	}
	
	public static interface IControllerWrapper {
		public Controller getController();
	}
	
	public static interface IServiceListener {
		public void onServiceChange(Integer status, Boolean sticky);
	}
	
	public static interface IServiceBinder {
		public boolean ping();
		public void stop();
	}
	
	private static class ServiceHandler extends AbstractHandler<Controller> {
		public ServiceHandler(Controller reference) {
			super(reference);
		}

		@Override
		public void handleMessage(Message msg) {
			Controller controller = getReference();
			
			if (controller != null) {
				Set<IServiceListener> listeners = new HashSet<IServiceListener>(controller.mServiceListeners);
				
				for (IServiceListener listener : listeners) {
					listener.onServiceChange(msg.arg1, msg.arg2 == 1 ? true : false);
				}
			}
		}
	}
	
	private ServiceHandler mServiceHandler;
	private IServiceBinder mServiceBinder;
	
	private Set<IServiceListener> mServiceListeners = Collections.newSetFromMap(new WeakHashMap<IServiceListener, Boolean>());
	
	private Integer mServiceStatus;
	private final Object mServiceLock = new Object();
	private Intent mServiceIntent;
	
	private Settings mSettings;
	private WakeLockManager mWakelockManager;
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		mServiceHandler = new ServiceHandler(this);
		mServiceIntent = new Intent(MonitorReceiver.ACTION_SCHEDULE_SERVICE, null, this, MonitorReceiver.class);
		mSettings = new Settings(this);
		mSettings.addListener(this);
		mWakelockManager = WakeLockManager.getInstance();
	}

	@SuppressWarnings("incomplete-switch")
	@Override
	public void onSettingsChange(Type type) {
		switch (type) {
			case LISTENER_ADDED:
			case SERVICE_STATE: 
				
				if (mSettings.isServiceEnabled() && getServiceState() != Status.STARTED) {
					startService();
					
				} else if (!mSettings.isServiceEnabled() && getServiceState() != Status.STOPPED) {
					stopService();
				}
				
				break;
				
			case SERVICE_INTERVAL: 
			case SERVICE_ENGINE: 
				
				if (getServiceState() == Status.STARTED) {
					restartService();
				}
				
				break;
		}
	}
	
	@Override
	public Settings getSettings() {
		return mSettings;
	}
	
	public WakeLockManager getWakeLockManager() {
		return mWakelockManager;
	}
	
	public void setServiceBinder(IServiceBinder binder) {
		mServiceBinder = binder;
		
		if (binder != null) {
			invokeServiceListeners((mServiceStatus = Status.STARTED), false);
			
		} else if (binder == null) {
			invokeServiceListeners((mServiceStatus = Status.STOPPED), false);
		}
	}
	
	public void addServiceListener(IServiceListener listener) {
		synchronized(mServiceListeners) {
			if (listener != null) {
				mServiceListeners.add(listener);
				listener.onServiceChange(getServiceState(), true);
			}
		}
	}
	
	public void removeServiceListener(IServiceListener listener) {
		synchronized(mServiceListeners) {
			if (listener != null) {
				mServiceListeners.remove(listener);
			}
		}
	}
	
	public void startService() {
		synchronized(mServiceLock) {
			if (getServiceState() != Status.STARTED) {
				invokeServiceListeners((mServiceStatus = Status.PENDING), false);
				setScheduler(1000, null);
				
			} else {
				invokeServiceListeners((mServiceStatus = Status.STARTED), true);
			}
		}
	}
	
	public void stopService() {
		synchronized(mServiceLock) {
			if (mServiceBinder != null && mServiceBinder.ping()) {
				invokeServiceListeners((mServiceStatus = Status.PENDING), false);
				mServiceBinder.stop();
				
			} else if (hasScheduler()) {
				removeScheduler();
				invokeServiceListeners((mServiceStatus = Status.STOPPED), false);
				
			} else {
				invokeServiceListeners((mServiceStatus = Status.STOPPED), true);
			}
		}
	}

	public void restartService() {
		synchronized(mServiceLock) {
			if (getServiceState() == Status.STOPPED) {
				startService();
				
			} else {
				addServiceListener(new IServiceListener(){
					@Override
					public void onServiceChange(Integer status, Boolean sticky) {
						if (status == Status.STOPPED) {
							removeServiceListener(this);
							startService();
						}
					}
				});
				
				stopService();
			}
		}
	}
	
	public Integer getServiceState() {
		if (mServiceStatus == null) {
			mServiceStatus = (mServiceBinder != null && mServiceBinder.ping()) || hasScheduler() ? Status.STARTED : Status.STOPPED;
		}
		
		return mServiceStatus;
	}
	
	private void invokeServiceListeners(int status, Boolean sticky) {
		synchronized(mServiceListeners) {
			if (mServiceListeners.size() > 0) {
				mServiceHandler.obtainMessage(0, status, sticky ? 1 : 0).sendToTarget();
			}
		}
	}
	
	public void resetScheduler(Integer timeout, Bundle extras) {
		if (getServiceState() != Status.STOPPED && mSettings.isServiceEnabled()) {
			setScheduler(timeout, extras);
			
			if (getServiceState() != Status.STARTED) {
				invokeServiceListeners((mServiceStatus = Status.STARTED), false);
			}
		}
	}
	
	private void setScheduler(Integer timeout, Bundle extras) {
		synchronized(mServiceIntent) {
			if (extras != null) {
				mServiceIntent.putExtra("service_extras", extras);
				
			} else {
				mServiceIntent.removeExtra("service_extras");
			}
			
			AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
			PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 1, mServiceIntent, PendingIntent.FLAG_UPDATE_CURRENT);
			
			alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime()+timeout, pendingIntent);
		}
	}
	
	private void removeScheduler() {
		synchronized(mServiceIntent) {
			PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 1, mServiceIntent, PendingIntent.FLAG_NO_CREATE);
			
			if (pendingIntent != null) {
				((AlarmManager) getSystemService(Context.ALARM_SERVICE)).cancel(pendingIntent);
			}
		}
	}
	
	private Boolean hasScheduler() {
		return PendingIntent.getBroadcast(this, 1, mServiceIntent, PendingIntent.FLAG_NO_CREATE) != null;
	}
}
