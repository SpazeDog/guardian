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

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.spazedog.guardian.Common.LOG;
import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectMethod;
import com.spazedog.lib.reflecttools.utils.ReflectConstants.Match;
import com.spazedog.lib.reflecttools.utils.ReflectException;

import de.robv.android.xposed.XC_MethodHook;

public class WakeLockService extends IRWakeLockService.Stub {
	
	protected ReflectClass mInstance;
	protected Map<String, ProcessLockInfo> mProcessLockInfo = new HashMap<String, ProcessLockInfo>();
	protected Map<IBinder, WakeLockInfo> mWakeLockInfo = new HashMap<IBinder, WakeLockInfo>();
	protected Map<IBinder, String> mProcessNameCache = new HashMap<IBinder, String>();
	
	public static void init() {
		WakeLockService instance = new WakeLockService();
		
		try {
			LOG.Info(instance, "Instantiating WakeLock service");
			
			ReflectClass clazz = ReflectClass.forName("com.android.server.power.PowerManagerService");
			
			if (clazz != null) {
				clazz.inject("systemReady", instance.systemReady);
			}
			
		} catch (ReflectException e) {
			LOG.Error(instance, "The WakeLock service crashed", e);
		}
	}
	
	protected XC_MethodHook systemReady = new XC_MethodHook() {
		@Override
		protected final void afterHookedMethod(final MethodHookParam param) {
			try {
				LOG.Info(WakeLockService.this, "System is ready, instantiating service hooks");
				
				mInstance = ReflectClass.forReceiver(param.thisObject);
				
				if (mInstance != null) {
					if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
						ReflectClass.forName("android.os.ServiceManager")
							.findMethod("addService", Match.BEST, String.class, IBinder.class)
							.invoke("user.guardian.wakelock", WakeLockService.this);
						
					} else {
						ReflectClass.forName("android.os.ServiceManager")
							.findMethod("addService", Match.BEST, String.class, IBinder.class, Boolean.TYPE)
							.invoke("user.guardian.wakelock", WakeLockService.this, true);
					}
					
					mInstance.inject("notifyWakeLockAcquiredLocked", notifyWakeLockAcquired);
					mInstance.inject("notifyWakeLockChangingLocked", notifyWakeLockChanging);
					mInstance.inject("notifyWakeLockReleasedLocked", notifyWakeLockReleased);
				}
				
			} catch (ReflectException e) {
				LOG.Error(WakeLockService.this, "The WakeLock service crashed", e);
			}
		}
	};
	
	protected XC_MethodHook notifyWakeLockAcquired = new XC_MethodHook() {
		@Override
		protected final void afterHookedMethod(final MethodHookParam param) {
			synchronized (mWakeLockInfo) {
				ReflectClass pmWakeLock = ReflectClass.forReceiver(param.args[0]);
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
	
	protected XC_MethodHook notifyWakeLockChanging = new XC_MethodHook() {
		@Override
		protected final void afterHookedMethod(final MethodHookParam param) {
			synchronized (mWakeLockInfo) {
				ReflectClass pmWakeLock = ReflectClass.forReceiver(param.args[0]);
				IBinder identifier = getPMWakeLockBinder(pmWakeLock);
				String processName = mProcessNameCache.get(identifier);
				
				if (processName != null) {
					int pid = getPMWakeLockPid(pmWakeLock);
					String tag = getPMWakeLockTag(pmWakeLock);
					WakeLockInfo wakeLock = mWakeLockInfo.get(identifier);
					ProcessLockInfo processLock = mProcessLockInfo.get(processName);
					
					if (wakeLock != null && processLock != null) {
						LOG.Debug(WakeLockService.this, "Update WakeLock: " + tag + "[" + processName + "(" + pid + ")], Total Locks: " + mWakeLockInfo.size());
						
						processLock.updateLockTime(wakeLock);
						wakeLock.updateTimestamp();
					}
				}
			}
		}
	};
	
	protected XC_MethodHook notifyWakeLockReleased = new XC_MethodHook() {
		@Override
		protected final void afterHookedMethod(final MethodHookParam param) {
			synchronized (mWakeLockInfo) {
				ReflectClass pmWakeLock = ReflectClass.forReceiver(param.args[0]);
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
							processLock.updateLockTime(wakeLock);
							processLock.removeWakeLock(wakeLock);
						}
					}
				}
			}
		}
	};
	
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
		return (IBinder) pmWakeLock.findField("mLock").getValue();
	}
	
	protected int getPMWakeLockPid(ReflectClass pmWakeLock) {
		return (Integer) pmWakeLock.findField("mOwnerPid").getValue();
	}
	
	protected int getPMWakeLockUid(ReflectClass pmWakeLock) {
		return (Integer) pmWakeLock.findField("mOwnerUid").getValue();
	}
	
	protected int getPMWakeLockFlags(ReflectClass pmWakeLock) {
		return (Integer) pmWakeLock.findField("mFlags").getValue();
	}
	
	protected String getPMWakeLockTag(ReflectClass pmWakeLock) {
		return (String) pmWakeLock.findField("mTag").getValue();
	}
	
	@Override
	public void srv_releaseForPid(int pid) {
		Set<IBinder> cache = new HashSet<IBinder>();
		ReflectMethod releaseMethod = mInstance.findMethod("releaseWakeLockInternal", Match.BEST, IBinder.class, Integer.TYPE);
		
		for (Entry<IBinder, WakeLockInfo> entry : mWakeLockInfo.entrySet()) {
			if (entry.getValue().getPid() == pid) {
				cache.add(entry.getKey());
			}
		}
		
		for (IBinder identifier : cache) {
			releaseMethod.invoke(identifier, 0);
		}
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
	
	public static class ProcessLockInfo implements Parcelable {
		private String mProcessName;
		private int mUid;
		private long mLockTime = 0l;
		private List<WakeLockInfo> mWakeLocks = new ArrayList<WakeLockInfo>();
		
		@Override
		public int describeContents() {
			return 0;
		}
		
		public JSONObject writeToJSON() {
			try {
				JSONObject out = new JSONObject();
				out.put("mUid", mUid);
				out.put("mProcessName", mProcessName);
				out.put("mLockTime", mLockTime);
				
				return out;
				
			} catch (JSONException e) {
				Log.e(getClass().getName(), e.getMessage(), e);
			}
			
			return null;
		}

		@Override
		public void writeToParcel(Parcel out, int flags) {
			out.writeInt(mUid);
			out.writeString(mProcessName);
			out.writeLong(mLockTime);
			out.writeTypedList(mWakeLocks);
		}
		
		public ProcessLockInfo(JSONObject in) {
			try {
				mUid = in.getInt("mUid");
				mProcessName = in.getString("mProcessName");
				mLockTime = in.getLong("mLockTime");
				
			} catch (JSONException e) {
				Log.e(getClass().getName(), e.getMessage(), e);
			}
		}
		
		public ProcessLockInfo(Parcel in) {
			mUid = in.readInt();
			mProcessName = in.readString();
			mLockTime = in.readLong();
			in.readTypedList(mWakeLocks, WakeLockInfo.CREATOR);
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
		
		protected void updateLockTime(WakeLockInfo lockInfo) {
			lockInfo.updateTime();
			mLockTime += lockInfo.getTime();
		}
		
		public long getLockTime() {
			return mLockTime;
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
		
		public static final Parcelable.Creator<ProcessLockInfo> CREATOR = new Parcelable.Creator<ProcessLockInfo>() {
			@Override
			public ProcessLockInfo createFromParcel(Parcel in) {
				return new ProcessLockInfo(in);
			}
			
			@Override
			public ProcessLockInfo[] newArray(int size) {
				return new ProcessLockInfo[size];
			}
		};
	}
	
	public static class WakeLockInfo implements Parcelable {
		private String mTag;
		private int mPid;
		private int mFlags;
		private long mTimestamp;
		private long mTime;
		
		@Override
		public int describeContents() {
			return 0;
		}
		
		public JSONObject writeToJSON() {
			try {
				JSONObject out = new JSONObject();
				out.put("mTag", mTag);
				out.put("mPid", mPid);
				out.put("mFlags", mFlags);
				out.put("mTimestamp", mTimestamp);
				out.put("mTime", mTime);
				
				return out;
				
			} catch (JSONException e) {
				Log.e(getClass().getName(), e.getMessage(), e);
			}
			
			return null;
		}

		@Override
		public void writeToParcel(Parcel out, int flags) {
			out.writeString(mTag);
			out.writeInt(mPid);
			out.writeInt(mFlags);
			out.writeLong(mTimestamp);
			out.writeLong(mTime);
		}
		
		public WakeLockInfo(JSONObject in) {
			try {
				mTag = in.getString("mTag");
				mPid = in.getInt("mPid");
				mFlags = in.getInt("mFlags");
				mTimestamp = in.getLong("mTimestamp");
				mTime = in.getLong("mTime");
				
			} catch (JSONException e) {
				Log.e(getClass().getName(), e.getMessage(), e);
			}
		}
		
		public WakeLockInfo(Parcel in) {
			mTag = in.readString();
			mPid = in.readInt();
			mFlags = in.readInt();
			mTimestamp = in.readLong();
			mTime = in.readLong();
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
		
		public static final Parcelable.Creator<WakeLockInfo> CREATOR = new Parcelable.Creator<WakeLockInfo>() {
			@Override
			public WakeLockInfo createFromParcel(Parcel in) {
				return new WakeLockInfo(in);
			}
			
			@Override
			public WakeLockInfo[] newArray(int size) {
				return new WakeLockInfo[size];
			}
		};
	}
}
