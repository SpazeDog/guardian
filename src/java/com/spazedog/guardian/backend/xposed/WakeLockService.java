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

package com.spazedog.guardian.backend.xposed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.spazedog.guardian.Common.LOG;
import com.spazedog.guardian.Constants;
import com.spazedog.guardian.utils.JSONParcel;
import com.spazedog.guardian.utils.JSONParcelable;
import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectException;
import com.spazedog.lib.reflecttools.ReflectMember.Match;
import com.spazedog.lib.reflecttools.ReflectMethod;
import com.spazedog.lib.reflecttools.bridge.MethodBridge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class WakeLockService extends IRWakeLockService.Stub {
	protected boolean mIsInteractive = true;
	protected boolean mHasPowerPackage;
	protected ReflectClass mInstance;
	protected Map<String, ProcessLockInfo> mProcessLockInfo = new HashMap<String, ProcessLockInfo>();
	protected Map<IBinder, WakeLockInfo> mWakeLockInfo = new HashMap<IBinder, WakeLockInfo>();
	protected Map<IBinder, String> mProcessNameCache = new HashMap<IBinder, String>();
	
	public static void init() {
		WakeLockService instance = new WakeLockService();
		
		try {
			LOG.Info(instance, "Instantiating WakeLock service");
			
			ReflectClass clazz = null;
			
			try {
				/*
				 * Android >= Jellybean MR1
				 */
				clazz = ReflectClass.fromName("com.android.server.power.PowerManagerService");
				instance.mHasPowerPackage = true;
				
				LOG.Info(instance, "Using newer PowerManagerService in the Power Package");
				
			} catch (ReflectException ignorer) {
				/*
				 * Android >= ICS MR0 and <= Jellybean MR0
				 */
				clazz = ReflectClass.fromName("com.android.server.PowerManagerService");
				instance.mHasPowerPackage = false;
				
				LOG.Info(instance, "Using older stand-alone PowerManagerService");
			}
			
			if (clazz != null) {
				clazz.bridge("systemReady", instance.systemReady);
			}
			
		} catch (ReflectException e) {
			LOG.Error(instance, "The WakeLock service crashed", e);
		}
	}
	
	protected MethodBridge systemReady = new MethodBridge() {
		@Override
		public void bridgeEnd(BridgeParams params) {
			try {
				LOG.Info(WakeLockService.this, "System is ready, instantiating service hooks");
				
				mInstance = ReflectClass.fromReceiver(params.receiver);
				
				if (mInstance != null) {
					if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
						ReflectClass.fromName("android.os.ServiceManager")
							.findMethod("addService", Match.BEST, String.class, IBinder.class)
							.invoke("user.guardian.wakelock", WakeLockService.this);
						
					} else {
						ReflectClass.fromName("android.os.ServiceManager")
							.findMethod("addService", Match.BEST, String.class, IBinder.class, Boolean.TYPE)
							.invoke("user.guardian.wakelock", WakeLockService.this, true);
					}
					
					if (mHasPowerPackage) {
						mInstance.bridge("notifyWakeLockAcquiredLocked", notifyWakeLockAcquired);
						mInstance.bridge("notifyWakeLockChangingLocked", notifyWakeLockChanging);
						mInstance.bridge("notifyWakeLockReleasedLocked", notifyWakeLockReleased);
					
					} else {
						mInstance.bridge("noteStartWakeLocked", notifyWakeLockAcquired);
						mInstance.bridge("noteStopWakeLocked", notifyWakeLockReleased);
					}
					
					int injections = 0;
					
					try {
						/*
						 * Android >= Lollipop MR0
						 */
						injections = mInstance.bridge("setHalInteractiveModeLocked", notifyInteractiveModeChanging);
						
						if (injections > 0) {
							LOG.Info(WakeLockService.this, "Monitoring interactive state via Hal");
						}
						
					} catch (ReflectException e) {} finally {
						if (injections == 0) {
							IntentFilter intentFilter = new IntentFilter();
							intentFilter.addAction(Intent.ACTION_SCREEN_ON);
							intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
							
							Context context = (Context) mInstance.findField("mContext").getValue();
							context.registerReceiver(recieveInteractiveModeChanging, intentFilter);
							
							LOG.Info(WakeLockService.this, "Monitoring interactive state via Broadcast Receiver");
						}
					}
				}
				
			} catch (ReflectException e) {
				LOG.Error(WakeLockService.this, "The WakeLock service crashed", e);
			}
		}
	};
	
	protected BroadcastReceiver recieveInteractiveModeChanging = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			synchronized (mWakeLockInfo) {
				if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
					setInteractive(true);
					
				} else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
					setInteractive(false);
				}
			}
		}
	};
	
	protected MethodBridge notifyInteractiveModeChanging = new MethodBridge() {
		@Override
		public void bridgeEnd(BridgeParams params) {
			synchronized (mWakeLockInfo) {
				setInteractive((Boolean) params.args[0]);
			}
		}
	};
	
	protected MethodBridge notifyWakeLockAcquired = new MethodBridge() {
		@Override
		public void bridgeEnd(BridgeParams params) {
			synchronized (mWakeLockInfo) {
				ReflectClass pmWakeLock = ReflectClass.fromReceiver(params.args[0]);
				int flags = getPMWakeLockFlags(pmWakeLock);
				boolean partialWakelock = (flags & 0x00000001) != 0;

				if (partialWakelock) {
					IBinder identifier = getPMWakeLockBinder(pmWakeLock);
					int pid = getPMWakeLockPid(pmWakeLock);
					int uid = getPMWakeLockUid(pmWakeLock);
					String tag = getPMWakeLockTag(pmWakeLock);
					String processName = getProcessName(pid);
					
					if (processName != null) {
						LOG.Debug(WakeLockService.this, "Acquire WakeLock: " + tag + "[" + processName + "(" + pid + ")], Total Locks: " + (mWakeLockInfo.size()+1));
						
						ProcessLockInfo processLock = mProcessLockInfo.get(processName);
						if (processLock == null) {
							mProcessLockInfo.put(processName, (processLock = new ProcessLockInfo(processName, uid)));
						}
						
						WakeLockInfo wakeLock = new WakeLockInfo(tag, pid, flags);
						processLock.addWakeLock(wakeLock);
						mWakeLockInfo.put(identifier, wakeLock);
						mProcessNameCache.put(identifier, processName);
					}
				}
			}
		}
	};
	
	protected MethodBridge notifyWakeLockChanging = new MethodBridge() {
		@Override
		public void bridgeEnd(BridgeParams params) {
			synchronized (mWakeLockInfo) {
				ReflectClass pmWakeLock = ReflectClass.fromReceiver(params.args[0]);
				IBinder identifier = getPMWakeLockBinder(pmWakeLock);
				String processName = mProcessNameCache.get(identifier);
				
				if (processName != null) {
					int pid = getPMWakeLockPid(pmWakeLock);
					String tag = getPMWakeLockTag(pmWakeLock);
					WakeLockInfo wakeLock = mWakeLockInfo.get(identifier);
					ProcessLockInfo processLock = mProcessLockInfo.get(processName);
					
					if (wakeLock != null && processLock != null) {
						LOG.Debug(WakeLockService.this, "Update WakeLock: " + tag + "[" + processName + "(" + pid + ")], Total Locks: " + mWakeLockInfo.size());
						
						processLock.updateLockTime(wakeLock, isInteractive());
						wakeLock.updateTimestamp();
					}
				}
			}
		}
	};
	
	protected MethodBridge notifyWakeLockReleased = new MethodBridge() {
		@Override
		public void bridgeEnd(BridgeParams params) {
			synchronized (mWakeLockInfo) {
				ReflectClass pmWakeLock = ReflectClass.fromReceiver(params.args[0]);
				IBinder identifier = getPMWakeLockBinder(pmWakeLock);
				String processName = mProcessNameCache.remove(identifier);
				
				if (processName != null) {
					int pid = getPMWakeLockPid(pmWakeLock);
					String tag = getPMWakeLockTag(pmWakeLock);
					WakeLockInfo wakeLock = mWakeLockInfo.remove(identifier);
					
					if (wakeLock != null) {
						LOG.Debug(WakeLockService.this, "Release WakeLock: " + tag + "[" + processName + "(" + pid + ")], Total Locks: " + (mWakeLockInfo.size()));
						
						ProcessLockInfo processLock = mProcessLockInfo.get(processName);
						
						if (processLock != null) {
							processLock.updateLockTime(wakeLock, isInteractive());
							processLock.removeWakeLock(wakeLock);
						}
					}
				}
			}
		}
	};
	
	protected void setInteractive(boolean interactive) {
		synchronized (mWakeLockInfo) {
			if (mIsInteractive != interactive) {
				LOG.Debug(WakeLockService.this, "The device is " + (interactive ? "waking up" : "going to sleep"));
				
				for (ProcessLockInfo lockInfo : mProcessLockInfo.values()) {
					lockInfo.updateLockTime(interactive);
				}
				
				mIsInteractive = interactive;
			}
		}
	}
	
	protected boolean isInteractive() {
		return mIsInteractive;
	}
	
	protected String getProcessName(int pid) {
		String processName = null;
		BufferedReader reader = null;
		int ch;
		
		try {
			reader = new BufferedReader(new InputStreamReader( new FileInputStream( "/proc/" + pid + "/cmdline"), "iso-8859-1" ) );
			processName = "";
			
			while ((ch = reader.read()) > 0) {
				processName += (char) ch;
			}
			
        	try {
				reader.close();
				
			} catch (IOException e) {}

		} catch (Throwable e) {}
		
		return processName;
	}
	
	protected IBinder getPMWakeLockBinder(ReflectClass pmWakeLock) {
		return (IBinder) pmWakeLock.findField(mHasPowerPackage ? "mLock" : "binder").getValue();
	}
	
	protected int getPMWakeLockPid(ReflectClass pmWakeLock) {
		return (Integer) pmWakeLock.findField(mHasPowerPackage ? "mOwnerPid" : "pid").getValue();
	}
	
	protected int getPMWakeLockUid(ReflectClass pmWakeLock) {
		return (Integer) pmWakeLock.findField(mHasPowerPackage ? "mOwnerUid" : "uid").getValue();
	}
	
	protected int getPMWakeLockFlags(ReflectClass pmWakeLock) {
		return (Integer) pmWakeLock.findField(mHasPowerPackage ? "mFlags" : "flags").getValue();
	}
	
	protected String getPMWakeLockTag(ReflectClass pmWakeLock) {
		return (String) pmWakeLock.findField(mHasPowerPackage ? "mTag" : "tag").getValue();
	}
	
	@Override
	public void srv_releaseForPid(int pid) {
		Set<IBinder> cache = new HashSet<IBinder>();
		ReflectMethod releaseMethod = mInstance.findMethod(mHasPowerPackage ? "releaseWakeLockInternal" : "releaseWakeLock", Match.BEST, IBinder.class, Integer.TYPE);

        synchronized(mWakeLockInfo) {
            for (Entry<IBinder, WakeLockInfo> entry : mWakeLockInfo.entrySet()) {
                if (entry.getValue().getPid() == pid) {
                    cache.add(entry.getKey());
                }
            }
        }

        long callingId = Binder.clearCallingIdentity();
		
		for (IBinder identifier : cache) {
			releaseMethod.invoke(identifier, 0);
		}

        Binder.restoreCallingIdentity(callingId);
	}
	
	@Override
	public List<ProcessLockInfo> srv_getProcessLockInfo() {
		synchronized(mWakeLockInfo) {
			for (WakeLockInfo lockInfo : mWakeLockInfo.values()) {
				lockInfo.updateTime();
			}
			
			return new ArrayList<ProcessLockInfo>(mProcessLockInfo.values());
		}
	}
	
	public static class ProcessLockInfo implements Parcelable, JSONParcelable {
		private boolean mParcelMatches = true;
		private String mProcessName;
		private int mUid = 0;
		private long mLockTime = 0l;
		private long mLockTimeOn = 0l;
		private long mLockTimeOff = 0l;
		private List<WakeLockInfo> mWakeLocks = new ArrayList<WakeLockInfo>();
		
		@Override
		public int describeContents() {
			return 0;
		}
		
		public void writeToJSON(JSONParcel out) {
			try {
                out.writeInt(mParcelMatches ? 1 : 0);
                out.writeInt(mUid);
                out.writeString(mProcessName);
                out.writeLong(mLockTime);
                out.writeLong(mLockTimeOn);
                out.writeLong(mLockTimeOff);
                out.writeList(mWakeLocks);
				
			} catch (JSONException e) {
				Log.e(getClass().getName(), e.getMessage(), e);
			}
		}

		@Override
		public void writeToParcel(Parcel out, int flags) {
			out.writeInt( Constants.SERVICE_PARCEL_ID );
			out.writeInt(mUid);
			out.writeString(mProcessName);
			out.writeLong(mLockTime);
			out.writeLong(mLockTimeOn);
			out.writeLong(mLockTimeOff);
			out.writeTypedList(mWakeLocks);
		}
		
		public ProcessLockInfo(JSONParcel in) {
			try {
                mParcelMatches = in.readInt() > 0;
                mUid = in.readInt();
                mProcessName = in.readString();
                mLockTime = in.readLong();
                mLockTimeOn = in.readLong();
                mLockTimeOff = in.readLong();
                in.fillList(mWakeLocks);
				
			} catch (JSONException e) {
				Log.e(getClass().getName(), e.getMessage(), e);
			}
		}
		
		public ProcessLockInfo(Parcel in) {
			mParcelMatches = in.readInt() == Constants.SERVICE_PARCEL_ID;
			
			if (mParcelMatches) {
                mUid = in.readInt();
                mProcessName = in.readString();
				mLockTime = in.readLong();
				mLockTimeOn = in.readLong();
				mLockTimeOff = in.readLong();
				in.readTypedList(mWakeLocks, WakeLockInfo.CREATOR);
			}
		}

		protected ProcessLockInfo(String processName, int uid) {
			mUid = uid;
			mProcessName = processName;
		}
		
		public int getUid() {
			return mUid;
		}
		
		public String getProcessName() {
			return mProcessName;
		}
		
		protected void updateLockTime(WakeLockInfo lockInfo, boolean interactive) {
			lockInfo.updateTime();
			mLockTime += lockInfo.getTime();
			
			if (interactive) {
				mLockTimeOn += lockInfo.getTime();
				
			} else {
				mLockTimeOff += lockInfo.getTime();
			}
		}
		
		protected void updateLockTime(boolean interactive) {
			for (WakeLockInfo lockInfo : mWakeLocks) {
				updateLockTime(lockInfo, interactive);
				lockInfo.updateTimestamp();
			}
		}
		
		public boolean isBroken() {
			return !mParcelMatches;
		}
		
		public long getLockTime() {
			return mLockTime;
		}
		
		public long getLockTimeOn() {
			return mLockTimeOn;
		}
		
		public long getLockTimeOff() {
			return mLockTimeOff;
		}
		
		protected void addWakeLock(WakeLockInfo lockInfo) {
			if (!mWakeLocks.contains(lockInfo)) {
				mWakeLocks.add(lockInfo);
			}
		}
		
		protected void removeWakeLock(WakeLockInfo lockInfo) {
			mWakeLocks.remove(lockInfo);
		}
		
		public List<WakeLockInfo> getWakeLocks() {
			return mWakeLocks;
		}
		
		public static final Creator CREATOR = new Creator();

        protected static class Creator implements Parcelable.Creator<ProcessLockInfo>, JSONParcelable.JSONCreator<ProcessLockInfo> {
            @Override
            public ProcessLockInfo createFromParcel(Parcel in) {
                return new ProcessLockInfo(in);
            }

            @Override
            public ProcessLockInfo createFromJSON(JSONParcel in) {
                return new ProcessLockInfo(in);
            }

            @Override
            public ProcessLockInfo[] newArray(int size) {
                return new ProcessLockInfo[size];
            }
        }
	}
	
	public static class WakeLockInfo implements Parcelable, JSONParcelable {
		private boolean mParcelMatches = true;
		private String mTag;
		private int mPid = 0;
		private int mFlags = 0;
		private long mTimestamp = 0l;
		private long mTime = 0l;
		
		@Override
		public int describeContents() {
			return 0;
		}
		
		public void writeToJSON(JSONParcel out) {
            out.writeInt( mParcelMatches ? 1 : 0 );
            out.writeString(mTag);
            out.writeInt(mPid);
            out.writeInt(mFlags);
            out.writeLong(mTimestamp);
            out.writeLong(mTime);
		}

		@Override
		public void writeToParcel(Parcel out, int flags) {
			out.writeInt( Constants.SERVICE_PARCEL_ID );
			out.writeString(mTag);
			out.writeInt(mPid);
			out.writeInt(mFlags);
			out.writeLong(mTimestamp);
			out.writeLong(mTime);
		}
		
		public WakeLockInfo(JSONParcel in) {
			try {
                mParcelMatches = in.readInt() > 0;
                mTag = in.readString();
                mPid = in.readInt();
                mFlags = in.readInt();
                mTimestamp = in.readLong();
                mTime = in.readLong();
				
			} catch (JSONException e) {
				Log.e(getClass().getName(), e.getMessage(), e);
			}
		}
		
		public WakeLockInfo(Parcel in) {
			mParcelMatches = in.readInt() == Constants.SERVICE_PARCEL_ID;
			
			if (mParcelMatches) {
				mTag = in.readString();
				mPid = in.readInt();
				mFlags = in.readInt();
				mTimestamp = in.readLong();
				mTime = in.readLong();
			}
		}
		
		protected WakeLockInfo(String tag, int pid, int flags) {
			mPid = pid;
			mTag = tag;
			mFlags = flags;
			
			updateTimestamp();
		}
		
		protected void updateTimestamp() {
			mTimestamp = System.currentTimeMillis();
			mTime = 0;
		}
		
		protected void updateTime() {
			mTime = mTimestamp > 0 ? System.currentTimeMillis() - mTimestamp : 0l;
		}
		
		public boolean isBroken() {
			return !mParcelMatches;
		}
		
		public String getTag() {
			return mTag;
		}
		
		public int getPid() {
			return mPid;
		}
		
		public int getFlags() {
			return mFlags;
		}
		
		public long getTime() {
			return mTime;
		}
		
		public long getTimestamp() {
			return mTimestamp;
		}

        public static final Creator CREATOR = new Creator();

        protected static class Creator implements Parcelable.Creator<WakeLockInfo>, JSONParcelable.JSONCreator<WakeLockInfo> {
            @Override
            public WakeLockInfo createFromParcel(Parcel in) {
                return new WakeLockInfo(in);
            }

            @Override
            public WakeLockInfo createFromJSON(JSONParcel in) {
                return new WakeLockInfo(in);
            }

            @Override
            public WakeLockInfo[] newArray(int size) {
                return new WakeLockInfo[size];
            }
        }
	}
}
