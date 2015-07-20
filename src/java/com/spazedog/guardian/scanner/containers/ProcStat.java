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


import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.spazedog.lib.utilsLib.JSONParcel;
import com.spazedog.lib.utilsLib.JSONParcel.JSONException;
import com.spazedog.lib.utilsLib.MultiParcelable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public abstract class ProcStat<T extends ProcStat> implements MultiParcelable {

    protected long[] mStatUptime = new long[] {0l, 0l};
    protected long[] mStatIdle = new long[] {0l, 0l};

    public ProcStat() {}

    public void updateStat(String[] stat, T process) {
        if (process != null) {
            mStatUptime = process.mStatUptime;
            mStatIdle = process.mStatIdle;
        }

        if (stat != null) {
            int size = stat.length;
            int pos = 0;

            if (size >= 3) {
                if (mStatUptime[0] > 0l && mStatIdle[0] > 0l) {
                    if (mStatUptime[1] > 0l && mStatIdle[1] > 0l) {
                        mStatUptime[0] = mStatUptime[1];
                        mStatIdle[0] = mStatIdle[1];
                    }

                    pos = 1;
                }

                /*
                 * This follows the schema from libprocessScanner.so
                 */
                mStatUptime[pos] = Long.valueOf(stat[size - 1]);
                mStatIdle[pos] = Long.valueOf(stat[size - 2]);
            }
        }
    }

    public double getCpuUsage() {
        long idle = mStatIdle[1] - mStatIdle[0];
        long uptime = mStatUptime[1] - mStatUptime[0];
        long time = uptime - idle;

        return uptime > 0l && time > 0l ?
                Math.round( (1000 * time) / uptime ) / 10.0d :
                0.0d;
    }

    public long getUptime() {
        return 0l;
    }

    public double getAverageCpu() {
        return 0l;
    }


    /* ============================================================================================================
     * ------------------------------------------------------------------------------------------------------------
     *
     * PARCEL IMPLEMENTATION
     *
     * ------------------------------------------------------------------------------------------------------------
     */

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(getClass().getName());
        out.writeLongArray(mStatUptime);
        out.writeLongArray(mStatIdle);
    }

    public void readFromParcel(Parcel in) {
        in.readLongArray(mStatUptime);
        in.readLongArray(mStatIdle);
    }



    /* ============================================================================================================
     * ------------------------------------------------------------------------------------------------------------
     *
     * JSON IMPLEMENTATION
     *
     * ------------------------------------------------------------------------------------------------------------
     */

    public void writeToJSON(JSONParcel out) {
        try {
            out.writeString(getClass().getName());
            out.writeLongArray(mStatUptime);
            out.writeLongArray(mStatIdle);

        } catch (JSONException e) {
            Log.e(getClass().getName(), e.getMessage(), e);
        }
    }

    public void readFromJSON(JSONParcel in) {
        try {
            mStatUptime = in.readLongArray();
            mStatIdle = in.readLongArray();

        } catch (JSONException e) {
            Log.e(getClass().getName(), e.getMessage(), e);
        }
    }



    /* ============================================================================================================
     * ------------------------------------------------------------------------------------------------------------
     *
     * JSON and Parcel creator
     *
     * ------------------------------------------------------------------------------------------------------------
     */

    public static final MultiCreator<ProcStat<?>> CREATOR = new MultiCreator<ProcStat<?>>() {

        @Override
        public ProcStat<?> createFromParcel(Parcel in) {
            String className = in.readString();
            ProcStat<?> instance = getInstance(className);

            instance.readFromParcel(in);

            return instance;
        }

        @Override
        public ProcStat<?> createFromJSON(JSONParcel in, ClassLoader loader) {
            try {
                String className = in.readString();
                ProcStat<?> instance = getInstance(className);

                instance.readFromJSON(in);

                return instance;

            } catch (JSONException e) {
                return null;
            }
        }

        @Override
        public ProcStat<?>[] newArray(int size) {
            return new ProcStat<?>[size];
        }
    };

    protected static ProcStat<?> getInstance(String className) {
        try {
            Class<?> clazz = Class.forName(className, true, ProcStat.class.getClassLoader());
            Constructor<?> constrcutor = clazz.getDeclaredConstructor();

            return (ProcStat) constrcutor.newInstance();

        } catch (InstantiationException e) {
            Log.e(ProcStat.class.getName(), e.getMessage(), e);

        } catch (IllegalAccessException e) {
            Log.e(ProcStat.class.getName(), e.getMessage(), e);

        } catch (InvocationTargetException e) {
            Log.e(ProcStat.class.getName(), e.getMessage(), e);

        } catch (NoSuchMethodException e) {
            Log.e(ProcStat.class.getName(), e.getMessage(), e);

        } catch (ClassNotFoundException e) {
            Log.e(ProcStat.class.getName(), e.getMessage(), e);
        }

        return null;
    }
}
