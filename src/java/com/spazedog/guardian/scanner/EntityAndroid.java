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
import com.spazedog.lib.utilsLib.JSONParcel;
import com.spazedog.lib.utilsLib.JSONParcel.JSONException;

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
    public void writeToJSON(JSONParcel out) {
        super.writeToJSON(out);

        try {
            Context context = out.getContext();
            if (context != null) {
                AndroidDataLoader dataLoader = getDataLoader(context);
                /*
                 * Make sure that these values are there
                 */
                mEntityPackageName = dataLoader.getPackageName();
                mEntityPackageLabel = dataLoader.getPackageLabel();
                mEntityCallingPid = dataLoader.getCallingProcessId();
            }

            out.writeString(mEntityPackageName);
            out.writeString(mEntityPackageLabel);
            out.writeInt(mEntityCallingPid);
            out.writeInt(mProcessLockInfo != null ? 1 : 0);

            if (mProcessLockInfo != null) {
                out.writeJSONParcelable(mProcessLockInfo);
            }

        } catch (JSONException e) {
            Log.e(getClass().getName(), e.getMessage(), e);
        }
    }

    @Override
    public void readFromJSON(JSONParcel in) {
        super.readFromJSON(in);

        try {
            mEntityPackageName = in.readString();
            mEntityPackageLabel = in.readString();
            mEntityCallingPid = in.readInt();

            if (in.readInt() > 0) {
                mProcessLockInfo = (ProcessLockInfo) in.readJSONParcelable(ProcessLockInfo.class.getClassLoader());
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

        @Override
        public JSONParcel getJSONParcel() {
            try {
                JSONParcel parcel = new JSONParcel(mContext);
                parcel.writeJSONParcelable(EntityAndroid.this);

                return parcel;

            } catch (JSONException e) {
                Log.e(getClass().getName(), e.getMessage(), e);
            }

            return null;
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
