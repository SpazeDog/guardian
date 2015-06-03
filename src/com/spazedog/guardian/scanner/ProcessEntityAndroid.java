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

import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.util.Log;

import com.spazedog.guardian.backend.xposed.WakeLockService.ProcessLockInfo;
import com.spazedog.guardian.scanner.IProcessEntity.ProcessEntity;

public class ProcessEntityAndroid extends ProcessEntity {
	
	protected String mEntityPackageName;
	protected String mEntityPackageLabel;
	protected ProcessLockInfo mProcessLockInfo;
	
	protected ProcessEntityAndroid() {
		super();
	}
	
	protected void updateLocks(ProcessLockInfo processLockInfo) {
		mProcessLockInfo = processLockInfo;
	}
	
	public ProcessLockInfo getProcessLockInfo() {
		return mProcessLockInfo;
	}
	
	@Override
	public <T extends IProcess> void updateStat(String[] stat, T process) {
		super.updateStat(stat, process);
		
		if (process instanceof ProcessEntityAndroid) {
			ProcessEntityAndroid convProcess = (ProcessEntityAndroid) process;
			
			if (convProcess.mEntityPackageName != null) {
				mEntityPackageName = convProcess.mEntityPackageName;
			}
			
			if (convProcess.mEntityPackageLabel != null) {
				mEntityPackageLabel = convProcess.mEntityPackageLabel;
			}
		}
	}

	@SuppressLint("NewApi")
	@Override
	public Drawable loadPackageDrawable(Context context) {
		String packageName = loadPackageName(context);
		PackageManager pm = context.getPackageManager();
		PackageInfo packageInfo = null;
		
		try {
			packageInfo = pm.getPackageInfo(packageName, 0);
			
		} catch (Throwable e) {}
		
		if (android.os.Build.VERSION.SDK_INT <= 14) {
			if (packageInfo != null) {
				Drawable icon = packageInfo.applicationInfo.loadIcon(pm);
				
				if (icon != null) {
					return icon;
				}
			}
			
		} else {
			Drawable icon = null;
			Resources resources = null;
			ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
			
			for (int i=0; i < 2; i++) {
				if (packageInfo != null || i > 0) {
					try {
						resources = i > 0 ? Resources.getSystem() : context.getPackageManager().getResourcesForApplication(packageInfo.applicationInfo);
						int iconId = i > 0 ? android.R.mipmap.sym_def_app_icon : packageInfo.applicationInfo.icon;
						int iconDpi = am.getLauncherLargeIconDensity();

						icon = resources.getDrawableForDensity(iconId, iconDpi);
						
						if (icon != null) {
							return icon;
						}
						
					} catch (Throwable e) {}
				}
			}
		}
		
		return Resources.getSystem().getDrawable(android.R.mipmap.sym_def_app_icon);
	}

	@Override
	public String loadPackageName(Context context) {
		if (mEntityPackageName == null) {
			String processName = getProcessName();
			
			mEntityPackageName = processName.contains(":") ? 
					processName.substring(0, processName.indexOf(":")) : 
						processName;
					
			try {
				/*
				 * Make sure that the package name is correct
				 */
				context.getPackageManager().getPackageInfo(mEntityPackageName, PackageManager.GET_META_DATA);
				
			} catch (Throwable e) {
				/*
				 * Otherwise we will have to look for it
				 */
				int pid = getProcessId();
				ActivityManager activityManager = (ActivityManager) context.getSystemService(Activity.ACTIVITY_SERVICE);
				List<RunningAppProcessInfo> runningApps = activityManager.getRunningAppProcesses();
				
				for(RunningAppProcessInfo appInfo : runningApps) {
					try {
						if (appInfo.pid == pid) {
							mEntityPackageName = appInfo.pkgList[0];
						}
						
					} catch (Throwable ei) {
						break;
					}
				}
			}
		}
		
		return mEntityPackageName;
	}

	@Override
	public String loadPackageLabel(Context context) {
		if (mEntityPackageLabel == null) {
			try {
				String packageName = loadPackageName(context);
				PackageManager pm = context.getPackageManager();
				PackageInfo packageInfo = pm.getPackageInfo(packageName, 0);
				mEntityPackageLabel = (String) packageInfo.applicationInfo.loadLabel(pm);
				
			} catch (Throwable e) {}
		}
		
		return mEntityPackageLabel;
	}
	
	@Override
	public int compareTo(IProcessEntity sibling) {
		int comp = Double.compare(sibling.getCpuUsage(), getCpuUsage());
		
		if (comp == 0) {
			if (comp == 0 && mProcessLockInfo != null && sibling.getImportance() > 0) {
				ProcessLockInfo siblingLockInfo = ((ProcessEntityAndroid) sibling).mProcessLockInfo;
				
				if (siblingLockInfo != null) {
					comp = (int) (siblingLockInfo.getLockTime() - mProcessLockInfo.getLockTime());
					
				} else {
					comp = -1;
				}
				
			} else if (sibling.getImportance() > 0 && ((ProcessEntityAndroid) sibling).mProcessLockInfo != null) {
				comp = 1;
			}
			
			if (comp == 0) {
				comp = sibling.getImportance() - getImportance();
			}
		}
		
		return comp;
	}
	
	
	/* ============================================================================================================
	 * ------------------------------------------------------------------------------------------------------------
	 * 
	 * PARCEL IMPLEMENTATION
	 * 
	 * ------------------------------------------------------------------------------------------------------------
	 */
	
	@Override
	public void writeToParcel(Parcel out, int flags) {
		super.writeToParcel(out, flags);
		
		out.writeInt(mProcessLockInfo != null ? 1 : 0);
		
		if (mProcessLockInfo != null) {
			out.writeParcelable(mProcessLockInfo, flags);
		}
	}
	
	@Override
	public void readFromParcel(Parcel in) {
		super.readFromParcel(in);
		
		if (in.readInt() > 0) {
			mProcessLockInfo = (ProcessLockInfo) in.readParcelable(ProcessLockInfo.class.getClassLoader());
		}
	}
	
	
	/* ============================================================================================================
	 * ------------------------------------------------------------------------------------------------------------
	 * 
	 * JSON IMPLEMENTATION
	 * 
	 * ------------------------------------------------------------------------------------------------------------
	 */
	
	@Override
	public JSONObject loadToJSON(Context context) {
		/*
		 * This is useful when storing in a database. 
		 * This package might no longer be installed when this is used, 
		 * this will make sure to load and store important info.
		 */
		try {
			JSONObject out = super.loadToJSON(context);
			
			if (out != null) {
				out.put("mEntityPackageName", loadPackageName(context));
				out.put("mEntityPackageLabel", loadPackageLabel(context));
			}
			
			return out;
			
		} catch (JSONException e) {
			Log.e(Process.class.getName(), e.getMessage(), e);
		}
		
		return null;
	}
	
	@Override
	public JSONObject writeToJSON() {
		try {
			JSONObject out = super.writeToJSON();
			
			if (out != null) {
				if (mEntityPackageName != null) {
					out.put("mEntityPackageName", mEntityPackageName);
				}
				
				if (mEntityPackageLabel != null) {
					out.put("mEntityPackageLabel", mEntityPackageLabel);
				}
				
				if (mProcessLockInfo != null) {
					out.put("mProcessLockInfo", mProcessLockInfo);
				}
				
				return out;
			}
			
		} catch (JSONException e) {
			Log.e(Process.class.getName(), e.getMessage(), e);
		}
		
		return null;
	}
	
	@Override
	public void readFromJSON(JSONObject in) {
		super.readFromJSON(in);
		
		try {
			if (!in.isNull("mEntityPackageLabel")) {
				mEntityPackageLabel = in.getString("mEntityPackageLabel");
			}
			
			if (!in.isNull("mEntityPackageName")) {
				mEntityPackageName = in.getString("mEntityPackageName");
			}
			
			if (!in.isNull("mProcessLockInfo")) {
				mProcessLockInfo = new ProcessLockInfo( new JSONObject(in.getString("mProcessLockInfo")) );
			}
			
		} catch (JSONException e) {
			Log.e(Process.class.getName(), e.getMessage(), e);
		}
	}
}
