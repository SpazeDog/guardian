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

import android.app.Application;
import android.os.Message;

import com.spazedog.guardian.Common;
import com.spazedog.guardian.application.Settings.ISettingsListener;
import com.spazedog.guardian.application.Settings.Type;
import com.spazedog.guardian.backend.MonitorService.MonitorServiceControl;
import com.spazedog.guardian.backend.MonitorService.MonitorServiceControl.IMonitorServiceListener;
import com.spazedog.guardian.backend.MonitorService.MonitorServiceControl.Status;
import com.spazedog.guardian.backend.xposed.WakeLockManager;
import com.spazedog.guardian.scanner.ProcessScanner;
import com.spazedog.guardian.utils.AbstractHandler;

public class Controller extends Application implements ApplicationImpl, ISettingsListener, IMonitorServiceListener {
	
	public static interface IServiceListener {
		public void onServiceChange(Integer status, Boolean sticky);
	}
	
	protected static class ServiceHandler extends AbstractHandler<Controller> {
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
	
	protected ServiceHandler mServiceHandler;
	protected Set<IServiceListener> mServiceListeners = Collections.newSetFromMap(new WeakHashMap<IServiceListener, Boolean>());
	protected final Object mServiceLock = new Object();
	protected MonitorServiceControl mServiceControl;
	
	protected Settings mSettings;
	protected WakeLockManager mWakelockManager;
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		mServiceHandler = new ServiceHandler(this);
		mSettings = new Settings(this);
		mWakelockManager = WakeLockManager.getInstance();
		mServiceControl = MonitorServiceControl.getInstance(this, mSettings.getServiceEngine());
		
		/*
		 * mServiceControl needs to be configured before adding settings listener. 
		 * When adding the listener, LISTENER_ADDED will be invoked, which in turn 
		 * will invoke either startService() or stopService()
		 */
		mServiceControl.setMonitorServiceListener(this);
		mSettings.addListener(this);
	}

	@SuppressWarnings("incomplete-switch")
	@Override
	public void onSettingsChange(Type type) {
		switch (type) {
			case LISTENER_ADDED:
			case SERVICE_STATE: 
				
				if (mSettings.isServiceEnabled()) {
					startService();
					
				} else if (!mSettings.isServiceEnabled()) {
					stopService();
				}
				
				break;
				
			case SERVICE_INTERVAL: 
			case SERVICE_ENGINE: 

				if (mSettings.isServiceEnabled()) {
					restartService();
				}
		}
	}
	
	@Override
	public Settings getSettings() {
		return mSettings;
	}

    @Override
    public Controller getController() {
        return this;
    }
	
	public WakeLockManager getWakeLockManager() {
        return mWakelockManager;
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
			if (ProcessScanner.hasLibrary()) {
				if (mServiceControl.status() == Status.STOPPED) {
					String engine = mSettings.getServiceEngine();
					
					if (!mServiceControl.identifier().equals( engine )) {
						Common.LOG.Debug(this, "Switching Service Engine from " + mServiceControl.identifier() + " to " + engine);
						mServiceControl = MonitorServiceControl.getInstance(this, engine);
						mServiceControl.setMonitorServiceListener(this);
					}
					
					Common.LOG.Debug(this, "Requesting Service Start, Engine = " + mServiceControl.identifier());
					mServiceControl.start();
					
				} else {
					Common.LOG.Debug(this, "Request for Service Start failed as it is alrady started, Engine = " + mServiceControl.identifier());
				}
				
			} else {
				Common.LOG.Error(this, "Request for Service Start failed as the ProcessScanner Library has not been loaded");
			}
		}
	}
	
	public void stopService() {
		synchronized(mServiceLock) {
			if (mServiceControl.status() == Status.STARTED) {
				Common.LOG.Debug(this, "Requesting Service Stop, Engine = " + mServiceControl.identifier());
				mServiceControl.stop();
				
			} else {
				Common.LOG.Debug(this, "Request for Service Stop failed as it is alrady stopped, Engine = " + mServiceControl.identifier());
			}
		}
	}

	public void restartService() {
		synchronized(mServiceLock) {
			Common.LOG.Debug(this, "Requesting Service Restart");
			
			if (getServiceState() == Status.STOPPED) {
				startService();
				
			} else {
				addServiceListener(new IServiceListener(){
					@Override
					public void onServiceChange(Integer status, Boolean sticky) {
						if (status == Status.STOPPED) {
							Common.LOG.Debug(Controller.this, "The Service has been stopped, continuing Service Restart Request");
							removeServiceListener(this);
							startService();
						}
					}
				});
				
				stopService();
			}
		}
	}
	
	public int getServiceState() {
        return mServiceControl.status();
	}
	
	public MonitorServiceControl getServiceControl() {
        return mServiceControl;
	}
	
	protected void invokeServiceListeners(int status, Boolean sticky) {
		synchronized(mServiceListeners) {
			if (mServiceListeners.size() > 0) {
				String stateName = status == Status.STOPPED ? "STOPPED" : 
					status == Status.STARTED ? "STARTED" : "PENDING";
				
				Common.LOG.Debug(this, "Notifying Service Changed Listeners, State = " + stateName);
				mServiceHandler.obtainMessage(0, status, sticky ? 1 : 0).sendToTarget();
			}
		}
	}

	@Override
	public void onServiceStateChanged(int state) {
        invokeServiceListeners(state, false);
	}
}
