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

package com.spazedog.guardian.scanner.containers;


import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.util.Log;

import com.spazedog.guardian.Common;
import com.spazedog.guardian.R;
import com.spazedog.lib.utilsLib.JSONParcel;
import com.spazedog.lib.utilsLib.JSONParcel.JSONException;
import com.spazedog.lib.utilsLib.utils.Conversion;

public abstract class ProcEntity<T extends ProcEntity> extends ProcStat<T> implements Comparable<ProcEntity<?>> {

    protected String mEntityName;
    protected int mEntityImportance = 0;
    protected int mEntityUid = 0;
    protected int mEntityPid = 0;
    protected long[] mEntityUTime = new long[]{0l, 0l};
    protected long[] mEntitySTime = new long[]{0l, 0l};
    protected long[] mEntityCUTime = new long[]{0l, 0l};
    protected long[] mEntityCSTime = new long[]{0l, 0l};
    protected long[] mEntityUptime = new long[]{0l, 0l};

    public ProcEntity() {}

    public abstract <L extends DataLoader> L getDataLoader(Context context);

    @Override
    public int compareTo(ProcEntity<?> sibling) {
        int comp = Double.compare(sibling.getCpuUsage(), getCpuUsage());

        if (comp == 0) {
            comp = sibling.getImportance() - getImportance();
        }

        return comp;
    }

    @Override
    public void updateStat(String[] stat, T process) {
        super.updateStat(stat, process);

        if (process != null) {
            mEntityUTime = process.mEntityUTime;
            mEntitySTime = process.mEntitySTime;
            mEntityCUTime = process.mEntityCUTime;
            mEntityCSTime = process.mEntityCSTime;
            mEntityUptime = process.mEntityUptime;
        }

        if (stat != null) {
            int size = stat.length;
            int pos = 0;

            if (size >= 8) {
                if (mEntityUTime[0] > 0l && mEntitySTime[0] > 0l) {
                    if (mEntityUTime[1] > 0l && mEntitySTime[1] > 0l) {
                        mEntityUTime[0] = mEntityUTime[1];
                        mEntitySTime[0] = mEntitySTime[1];
                        mEntityCUTime[0] = mEntityCUTime[1];
                        mEntityCSTime[0] = mEntityCSTime[1];
                        mEntityUptime[0] = mEntityUptime[1];
                    }

                    pos = 1;
                }

                /*
                 * This follows the schema from libprocessScanner.so
                 */
                mEntityName = stat[3];
                mEntityUid = Integer.valueOf(stat[1]);
                mEntityPid = Integer.valueOf(stat[2]);
                mEntityImportance = Integer.valueOf(stat[0]);
                mEntityUTime[pos] = Long.valueOf(stat[4]);
                mEntitySTime[pos] = Long.valueOf(stat[5]);
                mEntityCUTime[pos] = Long.valueOf(stat[6]);
                mEntityCSTime[pos] = Long.valueOf(stat[7]);
                mEntityUptime[pos] = Long.valueOf(stat[8]);
            }
        }
    }

    @Override
    public double getCpuUsage() {
        long uptime = (mStatUptime[1] - mEntityUptime[1]) - (mStatUptime[0] - mEntityUptime[0]);
        long idle = uptime - ((mEntityUTime[1] + mEntitySTime[1]) - (mEntityUTime[0] + mEntitySTime[0]));
        long time = uptime - idle;

        return uptime > 0l && time > 0l ?
                Math.round( (1000 * time) / uptime ) / 10.0d :
                0.0d;
    }

    @Override
    public double getAverageCpu() {
        long uptime = mStatUptime[1] > 0 ? (mStatUptime[1] - mEntityUptime[1]) : (mStatUptime[0] - mEntityUptime[0]);
        long idle = uptime - (mEntityUTime[1] > 0 ? (mEntityUTime[1] + mEntitySTime[1]) : (mEntityUTime[0] + mEntitySTime[0]));
        long time = uptime - idle;

        return uptime > 0l && time > 0l ?
                Math.round( (10000 * time) / uptime ) / 100.0d :
                0.0d;
    }

    /*
     * TODO: Finish this
     */
    @Override
    public long getUptime() {
        return 0l;
    }

    public String getProcessName() {
        return mEntityName;
    }

    public int getProcessUid() {
        return mEntityUid;
    }

    public int getProcessId() {
        return mEntityPid;
    }

    public int getImportance() {
        return mEntityImportance;
    }

    public boolean isPerceptible() {
        int importance = getImportance();

        return importance <= 0 ||
                importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                importance == RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE ||
                importance == RunningAppProcessInfo.IMPORTANCE_VISIBLE;
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

        out.writeValue(mEntityName);
        out.writeInt(mEntityUid);
        out.writeInt(mEntityPid);
        out.writeInt(mEntityImportance);
        out.writeLongArray(mEntityUTime);
        out.writeLongArray(mEntitySTime);
        out.writeLongArray(mEntityCUTime);
        out.writeLongArray(mEntityCSTime);
        out.writeLongArray(mEntityUptime);
    }

    @Override
    public void readFromParcel(Parcel in) {
        super.readFromParcel(in);

        mEntityName = (String) in.readValue(String.class.getClassLoader());
        mEntityUid = in.readInt();
        mEntityPid = in.readInt();
        mEntityImportance = in.readInt();

        in.readLongArray(mEntityUTime);
        in.readLongArray(mEntitySTime);
        in.readLongArray(mEntityCUTime);
        in.readLongArray(mEntityCSTime);
        in.readLongArray(mEntityUptime);
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
            out.writeValue(mEntityName);
            out.writeInt(mEntityUid);
            out.writeInt(mEntityPid);
            out.writeInt(mEntityImportance);
            out.writeLongArray(mEntityUTime);
            out.writeLongArray(mEntitySTime);
            out.writeLongArray(mEntityCUTime);
            out.writeLongArray(mEntityCSTime);
            out.writeLongArray(mEntityUptime);

        } catch (JSONException e) {
            Log.e(getClass().getName(), e.getMessage(), e);
        }
    }

    @Override
    public void readFromJSON(JSONParcel in) {
        super.readFromJSON(in);

        try {
            mEntityName = (String) in.readValue(String.class.getClassLoader());
            mEntityUid = in.readInt();
            mEntityPid = in.readInt();
            mEntityImportance = in.readInt();

            mEntityUTime = in.readLongArray();
            mEntitySTime = in.readLongArray();
            mEntityCUTime = in.readLongArray();
            mEntityCSTime = in.readLongArray();
            mEntityUptime = in.readLongArray();

        } catch (JSONException e) {
            Log.e(getClass().getName(), e.getMessage(), e);
        }
    }



    /* ============================================================================================================
	 * ------------------------------------------------------------------------------------------------------------
	 *
	 * DATA LOADER CLASS
	 *
	 * ------------------------------------------------------------------------------------------------------------
	 */

    public static abstract class DataLoader<L extends DataLoader> {

        protected Context mContext;

        protected DataLoader(Context context) {
            mContext = context.getApplicationContext();
        }

        public abstract JSONParcel getJSONParcel();
        public abstract Drawable getPackageDrawable();
        public abstract String getPackageName();
        public abstract String getPackageLabel();
        public abstract String getProcessName();
        public abstract int getProcessUid();
        public abstract int getProcessId();
        public abstract int getImportance();

        public String getImportanceLabel() {
            int importanceRes = 0;
            int importance = getImportance();

            switch(importance) {
                case ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND: importanceRes = R.string.process_importance_background; break;
                case ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND: importanceRes = R.string.process_importance_foreground; break;
                case ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE: importanceRes = R.string.process_importance_perceptible; break;
                case ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE: importanceRes = R.string.process_importance_service; break;
                case ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE: importanceRes = R.string.process_importance_visible; break;
                case ActivityManager.RunningAppProcessInfo.IMPORTANCE_EMPTY: importanceRes = R.string.process_importance_idle; break;

                /*
                 * Used by Android 5.1.1+
                 */
                case 1: importanceRes = R.string.process_importance_android; break;

                default:
                    importanceRes = importance > 0 ? R.string.process_importance_dead : R.string.process_importance_linux;
            }

            return mContext.getResources().getString(importanceRes);
        }

        public Bitmap getPackageBitmap() {
            return getPackageBitmap(0f, 0f);
        }

        public Bitmap getPackageBitmap(float width, float height) {
            Drawable drawable = getPackageDrawable();
            Integer canvasWidth = 0;
            Integer canvasHeight = 0;

            if (width <= 0) {
                canvasWidth = drawable.getIntrinsicWidth();

            } else {
                canvasWidth = Conversion.dipToPixels(width);
            }

            if (height <= 0) {
                canvasHeight = drawable.getIntrinsicHeight();

            } else if (width != height) {
                canvasHeight = Conversion.dipToPixels(height);

            } else {
                canvasHeight = canvasWidth;
            }

            if (drawable != null) {
                Bitmap bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);

                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);

                return bitmap;
            }

            return null;
        }
    }
}
