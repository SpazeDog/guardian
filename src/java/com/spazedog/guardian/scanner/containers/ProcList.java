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
import android.util.Log;

import com.spazedog.lib.utilsLib.JSONParcel;
import com.spazedog.lib.utilsLib.JSONParcel.JSONException;
import com.spazedog.lib.utilsLib.SparseList;
import com.spazedog.lib.utilsLib.SparseMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public abstract class ProcList<T extends ProcList> extends ProcStat<T> implements Iterable<ProcEntity<?>> {

    private List<ProcEntity<?>> mOrderedEntities;
    private Map<Integer, ProcEntity<?>> mMappedEntities;

    public ProcList() {
        this(0);
    }

    public ProcList(int dataSize) {
        mOrderedEntities = new SparseList<ProcEntity<?>>(dataSize);
        mMappedEntities = new SparseMap<ProcEntity<?>>(dataSize);
    }

    @Override
    public Iterator<ProcEntity<?>> iterator() {
        return mOrderedEntities.iterator();
    }

    public void addEntity(ProcEntity<?> entity) {
        if (entity != null) {
            int pid = entity.getProcessId();

            if (!mMappedEntities.containsKey(pid)) {
                mMappedEntities.put(pid, entity);
                mOrderedEntities.add(entity);
            }
        }
    }

    public ProcEntity<?> getEntity(int location) {
        return mOrderedEntities.get(location);
    }

    public ProcEntity<?> findEntity(int pid) {
        return mMappedEntities.get(pid);
    }

    public ProcEntity<?> removeEntity(int location) {
        return removeEntity(mOrderedEntities.get(location));
    }

    public ProcEntity<?> removeEntity(ProcEntity<?> entity) {
        if (entity != null) {
            int key = 0;

            for (Map.Entry<Integer, ProcEntity<?>> entry : mMappedEntities.entrySet()) {
                if (entry.getValue() == entity) {
                    key = entry.getKey(); break;
                }
            }

            if (key > 0) {
                mMappedEntities.remove(key);
                mOrderedEntities.remove(entity);
            }
        }

        return entity;
    }

    public int getEntitySize() {
        return mOrderedEntities.size();
    }

    public ProcList<T> sortEntities() {
        Collections.sort(mOrderedEntities); return this;
    }

    public void clearEntities() {
        mOrderedEntities.clear();
        mMappedEntities.clear();
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

        out.writeInt(mOrderedEntities.size());

        for (ProcEntity<?> entity: mOrderedEntities) {
            out.writeParcelable(entity, flags);
        }
    }

    @Override
    public void readFromParcel(Parcel in) {
        super.readFromParcel(in);

        int size = in.readInt();

        for (int i=0; i < size; i++) {
            ProcEntity<?> entity = (ProcEntity<?>) in.readParcelable(ProcEntity.class.getClassLoader());

            addEntity(entity);
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
            out.writeInt(mOrderedEntities.size());

            for (ProcEntity<?> entity : mOrderedEntities) {
                out.writeJSONParcelable(entity);
            }

        } catch (JSONException e) {
            Log.e(getClass().getName(), e.getMessage(), e);
        }
    }

    @Override
    public void readFromJSON(JSONParcel in) {
        super.readFromJSON(in);

        try {
            int size = in.readInt();

            for (int i=0; i < size; i++) {
                ProcEntity<?> entity = (ProcEntity<?>) in.readJSONParcelable(ProcEntity.class.getClassLoader());

                addEntity(entity);
            }

        } catch (JSONException e) {
            Log.e(getClass().getName(), e.getMessage(), e);
        }
    }
}
