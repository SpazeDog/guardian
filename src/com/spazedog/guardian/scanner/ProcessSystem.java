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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.util.Log;

import com.spazedog.guardian.scanner.IProcess.IProcessList;
import com.spazedog.guardian.scanner.IProcess.Process;
import com.spazedog.guardian.scanner.IProcessEntity.ProcessEntity;

@SuppressLint("UseSparseArrays")
public class ProcessSystem extends Process implements IProcessList {

	private final List<IProcessEntity> mOrderedEntities = new ArrayList<IProcessEntity>();
	private final Map<Integer, IProcessEntity> mMappedEntities = new HashMap<Integer, IProcessEntity>();
	
	protected ProcessSystem() {
		super();
	}

	@Override
	public Iterator<IProcessEntity> iterator() {
		return mOrderedEntities.iterator();
	}
	
	@Override
	public void addEntity(IProcessEntity entity) {
		if (entity != null) {
			int pid = entity.getProcessId();
			
			if (!mMappedEntities.containsKey(pid)) {
				mMappedEntities.put(pid, entity);
				mOrderedEntities.add(entity);
			}
		}
	}
	
	@Override
	public IProcessEntity getEntity(int location) {
		return mOrderedEntities.get(location);
	}
	
	@Override
	public IProcessEntity findEntity(int pid) {
		return mMappedEntities.get(pid);
	}
	
	@Override
	public IProcessEntity removeEntity(int location) {
		return removeEntity( mOrderedEntities.get(location) );
	}
	
	@Override
	public IProcessEntity removeEntity(IProcessEntity entity) {
		if (entity != null) {
			int key = 0;
			
			for (Entry<Integer, IProcessEntity> entry : mMappedEntities.entrySet()) {
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
	
	@Override
	public int getEntitySize() {
		return mOrderedEntities.size();
	}
	
	@Override
	public IProcessList sortEntities() {
		Collections.sort(mOrderedEntities); return this;
	}
	
	@Override
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
		
		for (IProcessEntity entity: mOrderedEntities) {
			out.writeParcelable(entity, flags);
		}
	}
	
	@Override
	public void readFromParcel(Parcel in) {
		super.readFromParcel(in);
		
		int size = in.readInt();
		
		for (int i=0; i < size; i++) {
			IProcessEntity entity = (IProcessEntity) in.readParcelable(IProcessEntity.class.getClassLoader());
			
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
	public JSONObject writeToJSON() {
		try {
			JSONObject out = super.writeToJSON();
			
			if (mOrderedEntities.size() > 0) {
				JSONArray jsonEntities = new JSONArray();
				
				for (IProcessEntity entity: mOrderedEntities) {
					jsonEntities.put(entity.writeToJSON());
				}
				
				out.put("entities", jsonEntities);
			}
			
			return out;
			
		} catch (JSONException e) {
			Log.e(getClass().getName(), e.getMessage(), e);
		}
		
		return null;
	}
	
	@Override
	public void readFromJSON(JSONObject in) {
		super.readFromJSON(in);
		
		try {
			if (!in.isNull("entities")) {
				JSONArray jsonEntities = new JSONArray(in.optString("entities"));
				
				for (int i=0; i < jsonEntities.length(); i++) {
					IProcessEntity entity = (IProcessEntity) ProcessEntity.getInstance(jsonEntities.optString(i));
					
					addEntity(entity);
				}
			}
			
		} catch (JSONException e) {
			Log.e(getClass().getName(), e.getMessage(), e);
		}
	}
}
