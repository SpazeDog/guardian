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

package com.spazedog.guardian.backend.containers;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.spazedog.guardian.scanner.containers.ProcEntity;
import com.spazedog.guardian.utils.JSONParcel;
import com.spazedog.guardian.utils.JSONParcelable;

import org.json.JSONException;


public class ThresholdItem implements Parcelable, JSONParcelable {

    public static final int FLAG_CPU = 0x00000001;
    public static final int FLAG_WAKELOCK = 0x00000002;
    public static final int FLAG_ACTION_KILLED = 0x00000400;
    public static final int FLAG_ACTION_RELEASED = 0x00000800;
    public static final int FLAG_ACTION_REBOOTED = 0x00001000;
    public static final int FLAG_ACTION_NOTIFIED = 0x00002000;

    protected int mCount = 0;
    protected int mFlagThreshold = 0;
    protected ProcEntity<?> mEntity;

    public ThresholdItem(ProcEntity<?> entity, int flags) {
        setEntity(entity, flags);
    }

    public ThresholdItem(Parcel in) {
        mCount = in.readInt();
        mFlagThreshold = in.readInt();
        mEntity = in.readParcelable(ProcEntity.class.getClassLoader());
    }

    public ThresholdItem(JSONParcel in) {
        try {
            mCount = in.readInt();
            mFlagThreshold = in.readInt();
            mEntity = in.readJSONParcelable(ProcEntity.class.getClassLoader());

        } catch (JSONException e) {
            Log.e(getClass().getName(), e.getMessage(), e);
        }
    }

    public JSONParcel getJSONParcel(Context context) {
        JSONParcel parcel = new JSONParcel(context);
        parcel.writeJSONParcelable(this);

        return parcel;
    }

    @Override
    public void writeToJSON(JSONParcel out) {
        out.writeInt(mCount);
        out.writeInt(mFlagThreshold);
        out.writeJSONParcelable(mEntity);
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mCount);
        out.writeInt(mFlagThreshold);
        out.writeParcelable(mEntity, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public int getFlags() {
        return mFlagThreshold;
    }

    public void setFlags(int flags) {
        mFlagThreshold = flags;
    }

    public ProcEntity<?> getEntity() {
        return mEntity;
    }

    public void setEntity(ProcEntity<?> entity, int flags) {
        mEntity = entity;
        mFlagThreshold = flags;
    }

    public int getCheckCount() {
        return mCount;
    }

    public void setCheckCount(int count) {
        mCount = count;
    }

    public static final Creator CREATOR = new Creator();

    protected static class Creator implements Parcelable.Creator<ThresholdItem>, JSONParcelable.JSONCreator<ThresholdItem> {
        @Override
        public ThresholdItem createFromParcel(Parcel in) {
            return new ThresholdItem(in);
        }

        @Override
        public ThresholdItem createFromJSON(JSONParcel in) {
            return new ThresholdItem(in);
        }

        @Override
        public ThresholdItem[] newArray(int size) {
            return new ThresholdItem[size];
        }
    }
}
