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

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Message;

import com.spazedog.guardian.application.Controller.IControllerWrapper;
import com.spazedog.guardian.utils.AbstractHandler;

public class Settings implements IControllerWrapper {
	
	public enum Type {
		SERVICE_STATE,
		SERVICE_INTERVAL,
		SERVICE_THRESHOLD,
		SERVICE_ACTION,
		SERVICE_ENGINE,
		ALLOW_ROOT,
		MONITOR_LINUX,
		LISTENER_ADDED
	}
	
	public static interface ISettingsWrapper {
		public Settings getSettings();
	}
	
	public static interface ISettingsListener {
		public void onSettingsChange(Type type);
	}
	
	private static class SettingsHandler extends AbstractHandler<Settings> {
		public SettingsHandler(Settings reference) {
			super(reference);
		}

		@Override
		public void handleMessage(Message msg) {
			Settings settings = getReference();
			
			if (settings != null) {
				Set<ISettingsListener> listeners = new HashSet<ISettingsListener>(settings.mSettingsListeners);
				
				for (ISettingsListener listener : listeners) {
					listener.onSettingsChange((Type) msg.obj);
				}
			}
		}
	}
	
	private SettingsHandler mSettingsHandler;

	private WeakReference<Controller> mController;
	private SharedPreferences mPreferences;
	
	private volatile Boolean mSettingsServiceEnabled;
	private volatile Integer mSettingsServiceInterval;
	private volatile Integer mSettingsServiceThresholdOn;
	private volatile Integer mSettingsServiceThresholdOff;
	private volatile String mSettingsServiceActionOn;
	private volatile String mSettingsServiceActionOff;
	private volatile String mSettingsServiceEngine;
	private volatile Boolean mSettingsRootEnabled;
	private volatile Boolean mSettingsMonitorLinux;
	
	Set<ISettingsListener> mSettingsListeners = Collections.newSetFromMap(new WeakHashMap<ISettingsListener, Boolean>());
	
	public Settings(Controller controller) {
		mSettingsHandler = new SettingsHandler(this);
		mController = new WeakReference<Controller>(controller);
		mPreferences = controller.getSharedPreferences("settings", Context.MODE_PRIVATE);
	}

	@Override
	public Controller getController() {
		return mController.get();
	}
	
	public void addListener(ISettingsListener listener) {
		synchronized(mSettingsListeners) {
			if (listener != null) {
				mSettingsListeners.add(listener);
				
				listener.onSettingsChange(Type.LISTENER_ADDED);
			}
		}
	}
	
	public void removeListener(ISettingsListener listener) {
		synchronized(mSettingsListeners) {
			if (listener != null) {
				mSettingsListeners.remove(listener);
			}
		}
	}
	
	public void isRootEnabled(Boolean enabled) {
		synchronized(mPreferences) {
			if (isRootEnabled() != enabled) {
				mPreferences.edit().putBoolean("enable_root", (mSettingsRootEnabled = enabled)).apply();
			}
			
			invokeServiceListeners(Type.ALLOW_ROOT);
		}
	}
	
	public Boolean isRootEnabled() {
		if (mSettingsRootEnabled == null) {
			mSettingsRootEnabled = mPreferences.getBoolean("enable_root", false);
		}
		
		return mSettingsRootEnabled;
	}
	
	public void isServiceEnabled(Boolean enabled) {
		synchronized(mPreferences) {
			if (isServiceEnabled() != enabled) {
				mPreferences.edit().putBoolean("enable_service", (mSettingsServiceEnabled = enabled)).apply();
				
				invokeServiceListeners(Type.SERVICE_STATE);
			}
		}
	}
	
	public Boolean isServiceEnabled() {
		if (mSettingsServiceEnabled == null) {
			mSettingsServiceEnabled = mPreferences.getBoolean("enable_service", false);
		}
		
		return mSettingsServiceEnabled;
	}
	
	public void setServiceInterval(Integer interval) {
		synchronized(mPreferences) {
			if (!getServiceInterval().equals(interval)) {
				mPreferences.edit().putInt("service_inteval", (mSettingsServiceInterval = interval)).apply();
				
				invokeServiceListeners(Type.SERVICE_INTERVAL);
			}
		}
	}
	
	public Integer getServiceInterval() {
		if (mSettingsServiceInterval == null) {
			mSettingsServiceInterval = mPreferences.getInt("service_inteval", 300000);
		}
		
		return mSettingsServiceInterval;
	}
	
	public void setServiceThreshold(Integer threshold, Boolean interactive) {
		synchronized(mPreferences) {
			if (interactive && !getServiceThreshold(interactive).equals(threshold)) {
				mPreferences.edit().putInt("cpu_threshold_on", (mSettingsServiceThresholdOn = threshold)).apply();
				
				invokeServiceListeners(Type.SERVICE_THRESHOLD);
				
			} else if (!interactive && !getServiceThreshold(interactive).equals(threshold)) {
				mPreferences.edit().putInt("cpu_threshold_off", (mSettingsServiceThresholdOff = threshold)).apply();
				
				invokeServiceListeners(Type.SERVICE_THRESHOLD);
			}
		}
	}
	
	public Integer getServiceThreshold(Boolean interactive) {
		if (interactive && mSettingsServiceThresholdOn == null) {
			mSettingsServiceThresholdOn = mPreferences.getInt("cpu_threshold_on", 25);
			
		} else if (!interactive && mSettingsServiceThresholdOff == null) {
			mSettingsServiceThresholdOff = mPreferences.getInt("cpu_threshold_off", 10);
		}
		
		return interactive ? mSettingsServiceThresholdOn : mSettingsServiceThresholdOff;
	}
	
	public void setServiceAction(String action, Boolean interactive) {
		synchronized(mPreferences) {
			if (interactive && !getServiceAction(interactive).equals(action)) {
				mPreferences.edit().putString("threshold_action_on", (mSettingsServiceActionOn = action)).apply();
				
				invokeServiceListeners(Type.SERVICE_ACTION);
				
			} else if (!interactive && !getServiceAction(interactive).equals(action)) {
				mPreferences.edit().putString("threshold_action_off", (mSettingsServiceActionOff = action)).apply();
				
				invokeServiceListeners(Type.SERVICE_ACTION);
			}
		}
	}
	
	public String getServiceAction(Boolean interactive) {
		if (interactive && mSettingsServiceActionOn == null) {
			mSettingsServiceActionOn = mPreferences.getString("threshold_action_on", "notify");
			
		} else if (!interactive && mSettingsServiceActionOff == null) {
			mSettingsServiceActionOff = mPreferences.getString("threshold_action_off", "notify");
		}
		
		return interactive ? mSettingsServiceActionOn : mSettingsServiceActionOff;
	}

	public void setServiceEngine(String engine) {
		synchronized(mPreferences) {
			if (!getServiceEngine().equals(engine)) {
				mPreferences.edit().putString("monitor_engine", (mSettingsServiceEngine = engine)).apply();
				
				invokeServiceListeners(Type.SERVICE_ENGINE);
			}
		}
	}
	
	public String getServiceEngine() {
		if (mSettingsServiceEngine == null) {
			mSettingsServiceEngine = mPreferences.getString("monitor_engine", "persistent");
		}
		
		return mSettingsServiceEngine;
	}
	
	public void monitorLinux(Boolean enable) {
		synchronized(mPreferences) {
			if (monitorLinux() != enable) {
				mPreferences.edit().putBoolean("enable_linux_monitoring", (mSettingsMonitorLinux = enable)).apply();
				
				invokeServiceListeners(Type.MONITOR_LINUX);
			}
		}
	}
	
	public Boolean monitorLinux() {
		if (mSettingsMonitorLinux == null) {
			mSettingsMonitorLinux = mPreferences.getBoolean("enable_linux_monitoring", false);
		}
		
		return mSettingsMonitorLinux;
	}
	
	private void invokeServiceListeners(Type type) {
		synchronized(mSettingsListeners) {
			if (mSettingsListeners.size() > 0) {
				mSettingsHandler.obtainMessage(0, type).sendToTarget();
			}
		}
	}
}
