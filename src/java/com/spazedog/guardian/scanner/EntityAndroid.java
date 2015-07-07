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
import com.spazedog.guardian.scanner.containers.ProcEntity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class EntityAndroid extends ProcEntity<EntityAndroid> {

    protected AndroidDataLoader mDataLoader;
    protected ProcessLockInfo mProcessLockInfo;

    public static EntityAndroid cast(ProcEntity<?> instance) {
        if (instance != null && instance instanceof EntityAndroid) {
            return (EntityAndroid) instance;
        }

        return null;
    }

    public static AndroidDataLoader cast(DataLoader<?> instance) {
        if (instance != null && instance instanceof AndroidDataLoader) {
            return (AndroidDataLoader) instance;
        }

        return null;
    }

    public EntityAndroid() {
        super();
    }

    @Override
    public AndroidDataLoader getDataLoader(Context context) {
        if (mDataLoader == null) {
            mDataLoader = new AndroidDataLoader(context);
        }

        return mDataLoader;
    }

    public void updateStat(String[] stat, EntityAndroid process, ProcessLockInfo processLockInfo) {
        updateStat(stat, process);

        mProcessLockInfo = processLockInfo;
    }

    @Override
    public void updateStat(String[] stat, EntityAndroid process) {
        super.updateStat(stat, process);

        if (process != null) {
            if (process.mEntityPackageName != null) {
                mEntityPackageName = process.mEntityPackageName;
            }

            if (process.mEntityPackageLabel != null) {
                mEntityPackageLabel = process.mEntityPackageLabel;
            }
        }
    }

    public ProcessLockInfo getProcessLockInfo() {
        return mProcessLockInfo;
    }

    /*
     * Priorities:
     *      1: CPU Usage
     *      2: WakeLocks
     *      3: Android Processes
     */
    @Override
    public int compareTo(ProcEntity<?> sibling) {
        int comp = Double.compare(sibling.getCpuUsage(), getCpuUsage());

        if (comp == 0) {
            EntityAndroid androidSibling = cast(sibling);

            if (comp == 0 && mProcessLockInfo != null && androidSibling != null) {
                if (androidSibling.mProcessLockInfo != null) {
                    comp = (int) (androidSibling.mProcessLockInfo.getLockTime() - mProcessLockInfo.getLockTime());

                } else {
                    comp = -1;
                }

            } else if (androidSibling != null && androidSibling.mProcessLockInfo != null) {
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
                AndroidDataLoader loader = getDataLoader(context);

                out.put("mEntityPackageName", loader.getPackageName());
                out.put("mEntityPackageLabel", loader.getPackageLabel());
                out.put("mEntityCallingPid", loader.getCallingProcessId());
            }

            return out;

        } catch (JSONException e) {
            Log.e(EntityAndroid.class.getName(), e.getMessage(), e);
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

                if (mEntityCallingPid >= 0) {
                    out.put("mEntityCallingPid", mEntityCallingPid);
                }

                if (mProcessLockInfo != null) {
                    out.put("mProcessLockInfo", mProcessLockInfo.writeToJSON());
                }

                return out;
            }

        } catch (JSONException e) {
            Log.e(EntityAndroid.class.getName(), e.getMessage(), e);
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

            if (!in.isNull("mEntityCallingPid")) {
                mEntityCallingPid = in.getInt("mEntityCallingPid");
            }

            if (!in.isNull("mProcessLockInfo")) {
                mProcessLockInfo = new ProcessLockInfo( new JSONObject( in.getString("mProcessLockInfo") ) );
            }

        } catch (JSONException e) {
            Log.e(EntityAndroid.class.getName(), e.getMessage(), e);
        }
    }



    /* ============================================================================================================
	 * ------------------------------------------------------------------------------------------------------------
	 *
	 * DATA LOADER CLASS
	 *
	 * ------------------------------------------------------------------------------------------------------------
	 */

    protected String mEntityPackageName;
    protected String mEntityPackageLabel;
    protected int mEntityCallingPid = -1;

    public class AndroidDataLoader extends DataLoader<AndroidDataLoader> {

        protected RunningAppProcessInfo mAndroidAppInfo;

        protected AndroidDataLoader(Context context) {
            super(context);
        }

        protected RunningAppProcessInfo getAndroidAppInfo() {
            if (mAndroidAppInfo == null) {
                int pid = getProcessId();
                ActivityManager activityManager = (ActivityManager) mContext.getSystemService(Activity.ACTIVITY_SERVICE);
                List<RunningAppProcessInfo> runningApps = activityManager.getRunningAppProcesses();

                for(RunningAppProcessInfo appInfo : runningApps) {
                    try {
                        if (appInfo.pid == pid) {
                            mAndroidAppInfo = appInfo;
                        }

                    } catch (Throwable e) {
                        break;
                    }
                }
            }

            return mAndroidAppInfo;
        }

        @Override
        public String getProcessName() {
            return EntityAndroid.this.getProcessName();
        }

        @Override
        public int getProcessUid() {
            return EntityAndroid.this.getProcessUid();
        }

        @Override
        public int getProcessId() {
            return EntityAndroid.this.getProcessId();
        }

        @Override
        public int getImportance() {
            return EntityAndroid.this.getImportance();
        }

        @Override
        public Drawable getPackageDrawable() {
            String packageName = getPackageName();
            PackageManager pm = mContext.getPackageManager();
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
                ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);

                for (int i=0; i < 2; i++) {
                    if (packageInfo != null || i > 0) {
                        try {
                            resources = i > 0 ? Resources.getSystem() : pm.getResourcesForApplication(packageInfo.applicationInfo);
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
        public String getPackageName() {
            if (mEntityPackageName == null) {
                String processName = getProcessName();

                mEntityPackageName = processName.contains(":") ?
                        processName.substring(0, processName.indexOf(":")) :
                        processName;

                try {
                    /*
                     * Make sure that the package name is correct
                     */
                    mContext.getPackageManager().getPackageInfo(mEntityPackageName, PackageManager.GET_META_DATA);

                } catch (Throwable e) {
                    /*
                     * Otherwise we will have to look for it
                     */
                    RunningAppProcessInfo appInfo = getAndroidAppInfo();

                    if (appInfo != null) {
                        mEntityPackageName = appInfo.pkgList[0];
                    }
                }
            }

            return mEntityPackageName;
        }

        @Override
        public String getPackageLabel() {
            if (mEntityPackageLabel == null) {
                try {
                    String packageName = getPackageName();
                    PackageManager pm = mContext.getPackageManager();
                    PackageInfo packageInfo = pm.getPackageInfo(packageName, 0);
                    mEntityPackageLabel = (String) packageInfo.applicationInfo.loadLabel(pm);

                } catch (Throwable e) {}
            }

            return mEntityPackageLabel;
        }

        public int getCallingProcessId() {
            if (mEntityCallingPid < 0) {
                RunningAppProcessInfo appInfo = getAndroidAppInfo();

                if (appInfo != null) {
                    mEntityCallingPid = appInfo.importanceReasonPid;
                }
            }

            return mEntityCallingPid;
        }
    }
}
