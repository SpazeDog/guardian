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

import com.spazedog.guardian.db.AlertListDB;
import com.spazedog.guardian.db.WhiteListDB;
import com.spazedog.guardian.utils.AbstractHandler;

public class Settings implements ApplicationImpl {
	
	public enum Type {
		SERVICE_NOTIFY_PERSISTENT,
		SERVICE_STATE,
		SERVICE_INTERVAL,
		SERVICE_THRESHOLD,
		SERVICE_ACTION,
		SERVICE_ENGINE,
		SERVICE_WAKELOCK_TIME,
		SERVICE_WAKELOCK_ACTION,
		ALLOW_ROOT,
		MONITOR_LINUX,
		LISTENER_ADDED
	}
	
	public static interface ISettingsListener {
		public void onSettingsChange(Type type);
	}
	
	protected static class SettingsHandler extends AbstractHandler<Settings> {
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
	
	protected SettingsHandler mSettingsHandler;

	protected Controller mController;
	protected SharedPreferences mPreferences;

    protected volatile Boolean mSettingsServiceNotify;
	protected volatile Boolean mSettingsServiceEnabled;
	protected volatile Integer mSettingsServiceInterval;
	protected volatile Integer mSettingsServiceThresholdOn;
	protected volatile Integer mSettingsServiceThresholdOff;
	protected volatile String mSettingsServiceActionOn;
	protected volatile String mSettingsServiceActionOff;
	protected volatile String mSettingsServiceEngine;
	protected volatile Boolean mSettingsRootEnabled;
	protected volatile Boolean mSettingsMonitorLinux;
	protected volatile Long mSettingsServiceWakeLockTime;
	protected volatile String mSettingsServiceWakeLockAction;
	
	protected Set<ISettingsListener> mSettingsListeners = Collections.newSetFromMap(new WeakHashMap<ISettingsListener, Boolean>());

    protected WhiteListDB mWhiteListDB;
    protected AlertListDB mAlertListDB;
	
	protected Settings(Controller controller) {
		mController = controller;

        instantiateHandler();
        instantiatePreferences();
	}

    protected void instantiateHandler() {
        mSettingsHandler = new SettingsHandler(this);
    }

    protected void instantiatePreferences() {
        mPreferences = mController.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public WhiteListDB getWhiteListDatabase() {
        if (mWhiteListDB == null) {
            mWhiteListDB = new WhiteListDB(getController());
        }

        return mWhiteListDB;
    }

    public AlertListDB getAlertListDatabase() {
        if (mAlertListDB == null) {
            mAlertListDB = new AlertListDB(getController());
        }

        return mAlertListDB;
    }

	@Override
	public Controller getController() {
		return mController;
	}

    @Override
    public Settings getSettings() {
        return this;
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

    public void persistentNotify(Boolean notify) {
        synchronized(mPreferences) {
            if (persistentNotify() != notify) {
                mPreferences.edit().putBoolean("persistent_notify", (mSettingsServiceNotify = notify)).apply();
            }

            invokeServiceListeners(Type.SERVICE_NOTIFY_PERSISTENT);
        }
    }

    public Boolean persistentNotify() {
        if (mSettingsServiceNotify == null) {
            mSettingsServiceNotify = mPreferences.getBoolean("persistent_notify", true);
        }

        return mSettingsServiceNotify;
    }

	public void isServiceEnabled(Boolean enabled) {
		synchronized(mPreferences) {
			if (isServiceEnabled() != enabled) {
				mPreferences.edit().putBoolean("enable_service", (mSettingsServiceEnabled = enabled)).apply();
			}
			
			invokeServiceListeners(Type.SERVICE_STATE);
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
			}
			
			invokeServiceListeners(Type.SERVICE_INTERVAL);
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
				
			} else if (!interactive && !getServiceThreshold(interactive).equals(threshold)) {
				mPreferences.edit().putInt("cpu_threshold_off", (mSettingsServiceThresholdOff = threshold)).apply();
			}
			
			invokeServiceListeners(Type.SERVICE_THRESHOLD);
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
	
	public void setServiceWakeLockTime(Long lockTime) {
		synchronized(mPreferences) {
			if (!getServiceWakeLockTime().equals(lockTime)) {
				mPreferences.edit().putLong("wakelock_time", (mSettingsServiceWakeLockTime = lockTime)).apply();
			}
			
			invokeServiceListeners(Type.SERVICE_WAKELOCK_TIME);
		}
	}
	
	public Long getServiceWakeLockTime() {
		if (mSettingsServiceWakeLockTime == null) {
			mSettingsServiceWakeLockTime = mPreferences.getLong("wakelock_time", 300000); // Default: 5 minutes
		}
		
		return mSettingsServiceWakeLockTime;
	}

	public void setServiceWakeLockAction(String action) {
		synchronized(mPreferences) {
			if (!getServiceWakeLockAction().equals(action)) {
				mPreferences.edit().putString("wakelock_action", (mSettingsServiceWakeLockAction = action)).apply();
			}
			
			invokeServiceListeners(Type.SERVICE_WAKELOCK_ACTION);
		}
	}
	
	public String getServiceWakeLockAction() {
		if (mSettingsServiceWakeLockAction == null) {
			mSettingsServiceWakeLockAction = mPreferences.getString("wakelock_action", "notify");
		}
		
		return mSettingsServiceWakeLockAction;
	}
	
	public void setServiceAction(String action, Boolean interactive) {
		synchronized(mPreferences) {
			if (interactive && !getServiceAction(interactive).equals(action)) {
				mPreferences.edit().putString("threshold_action_on", (mSettingsServiceActionOn = action)).apply();
				
			} else if (!interactive && !getServiceAction(interactive).equals(action)) {
				mPreferences.edit().putString("threshold_action_off", (mSettingsServiceActionOff = action)).apply();
			}
			
			invokeServiceListeners(Type.SERVICE_ACTION);
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
			}
			
			invokeServiceListeners(Type.SERVICE_ENGINE);
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
			}
			
			invokeServiceListeners(Type.MONITOR_LINUX);
		}
	}
	
	public Boolean monitorLinux() {
		if (mSettingsMonitorLinux == null) {
			mSettingsMonitorLinux = mPreferences.getBoolean("enable_linux_monitoring", false);
		}
		
		return mSettingsMonitorLinux;
	}
	
	protected void invokeServiceListeners(Type type) {
		synchronized(mSettingsListeners) {
			if (mSettingsListeners.size() > 0) {
				mSettingsHandler.obtainMessage(0, type).sendToTarget();
			}
		}
	}
}
