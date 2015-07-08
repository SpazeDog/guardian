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


import android.os.Parcel;
import android.os.Parcelable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ThresholdMap implements Parcelable {

    protected Map<Integer, ThresholdItem> mMap = new HashMap<Integer, ThresholdItem>();

    public ThresholdMap() {

    }

    public ThresholdMap(Parcel in) {
        int size = in.readInt();
        int key;
        ThresholdItem item;

        for (int i=0; i < size; i++) {
            key = in.readInt();
            item = in.readParcelable(ThresholdItem.class.getClassLoader());

            mMap.put(key, item);
        }
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        int size = mMap.size();
        out.writeInt(size);

        for (Map.Entry<Integer, ThresholdItem> entry : mMap.entrySet()) {
            out.writeInt(entry.getKey());
            out.writeParcelable(entry.getValue(), flags);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public int size() {
        return mMap.size();
    }

    public void clear() {
        mMap.clear();
    }

    public ThresholdItem remove(int pid) {
        return mMap.remove(pid);
    }

    public boolean hasPid(int pid) {
        return mMap.containsKey(pid);
    }

    public void put(int pid, ThresholdItem value) {
        mMap.put(pid, value);
    }

    public ThresholdItem get(int pid) {
        return mMap.get(pid);
    }

    public Set<Map.Entry<Integer, ThresholdItem>> entrySet() {
        return mMap.entrySet();
    }

    public static final Creator CREATOR = new Creator();

    protected static class Creator implements Parcelable.Creator<ThresholdMap> {
        @Override
        public ThresholdMap createFromParcel(Parcel in) {
            return new ThresholdMap(in);
        }

        @Override
        public ThresholdMap[] newArray(int size) {
            return new ThresholdMap[size];
        }
    }
}
