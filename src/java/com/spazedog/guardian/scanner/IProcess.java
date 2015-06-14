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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

public interface IProcess extends Parcelable {
	
	/*
	 * The abstract keyword has no importance. 
	 * What they do is provide an easier overview to distinguish 
	 * the interface methods from the below class methods. 
	 */
	abstract <T extends IProcess> void updateStat(String[] stat, T process);
	abstract double getCpuUsage();
	abstract double getAverageCpu();
	abstract long getUptime();
	abstract void readFromParcel(Parcel in);
	abstract JSONObject loadToJSON(Context context);
	abstract JSONObject writeToJSON();
	abstract void readFromJSON(JSONObject in);
	
	public static interface IProcessList extends IProcess, Iterable<IProcessEntity> {
		abstract void addEntity(IProcessEntity entity);
		abstract IProcessEntity getEntity(int location);
		abstract IProcessEntity findEntity(int pid);
		abstract IProcessEntity removeEntity(int location);
		abstract IProcessEntity removeEntity(IProcessEntity entity);
		abstract int getEntitySize();
		abstract IProcessList sortEntities();
		abstract void clearEntities();
	}
	
	public static abstract class Process implements IProcess {
		
		protected long[] mStatUptime = new long[] {0l, 0l};
		protected long[] mStatIdle = new long[] {0l, 0l};
		
		protected Process() {}
		
		@Override
		public <T extends IProcess> void updateStat(String[] stat, T process) {
			if (process != null && process instanceof Process) {
				Process convProcess = (Process) process;
				mStatUptime = convProcess.mStatUptime;
				mStatIdle = convProcess.mStatIdle;
			}
			
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
				mStatUptime[pos] = Long.valueOf( stat[ size-1 ] );
				mStatIdle[pos] = Long.valueOf( stat[ size-2 ] );
			}
		}
		
		@Override
		public double getCpuUsage() {
			long idle = mStatIdle[1] - mStatIdle[0];
			long uptime = mStatUptime[1] - mStatUptime[0];
			long time = uptime - idle;

			return uptime > 0l && time > 0l ? 
					Math.round( (1000 * time) / uptime ) / 10.0d : 
						0.0d;
		}
		
		@Override
		public long getUptime() {
			return 0l;
		}
		
		@Override
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
		
		@Override
		public void readFromParcel(Parcel in) {
			in.readLongArray(mStatUptime);
			in.readLongArray(mStatIdle);
		}
		
		public static final Parcelable.Creator<IProcess> CREATOR = new Parcelable.Creator<IProcess>() {
			@Override
			public IProcess createFromParcel(Parcel in) {
				return getInstance(in);
			}
			
			@Override
			public IProcess[] newArray(int size) {
				return new IProcess[size];
			}
		};
		
		public static IProcess getInstance(Parcel in) {
			try {
				Class<?> clazz = Class.forName(in.readString(), true, IProcess.class.getClassLoader());
				Constructor<?> constrcutor = clazz.getDeclaredConstructor();
				
				IProcess process = (IProcess) constrcutor.newInstance();
				process.readFromParcel(in);
				
				return process;
				
			} catch (InstantiationException e) {
				Log.e(Process.class.getName(), e.getMessage(), e);
				
			} catch (IllegalAccessException e) {
				Log.e(Process.class.getName(), e.getMessage(), e);
				
			} catch (InvocationTargetException e) {
				Log.e(Process.class.getName(), e.getMessage(), e);
				
			} catch (NoSuchMethodException e) {
				Log.e(Process.class.getName(), e.getMessage(), e);
				
			} catch (ClassNotFoundException e) {
				Log.e(Process.class.getName(), e.getMessage(), e);
			}
			
			return null;
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
			return writeToJSON();
		}
		
		@Override
		public JSONObject writeToJSON() {
			try {
				JSONObject out = new JSONObject();
				out.put("class", getClass().getName());
				
				JSONArray jsonArray = new JSONArray();
				for (int i=0; i < mStatUptime.length; i++) {
					jsonArray.put(mStatUptime[i]);
				}
				out.put("mStatUptime", jsonArray);
				
				jsonArray = new JSONArray();
				for (int i=0; i < mStatIdle.length; i++) {
					jsonArray.put(mStatIdle[i]);
				}
				out.put("mStatIdle", jsonArray);
				
				return out;
				
			} catch (JSONException e) {
				Log.e(getClass().getName(), e.getMessage(), e);
			}
			
			return null;
		}
		
		@Override
		public void readFromJSON(JSONObject in) {
			try {
				JSONArray jsonArray = new JSONArray(in.optString("mStatUptime"));
				for (int i=0; i < mStatUptime.length; i++) {
					mStatUptime[i] = jsonArray.optLong(i);
				}
				
				jsonArray = new JSONArray(in.optString("mStatIdle"));
				for (int i=0; i < mStatIdle.length; i++) {
					mStatIdle[i] = jsonArray.optLong(i);
				}
				
			} catch (JSONException e) {
				Log.e(getClass().getName(), e.getMessage(), e);
			}
		}
		
		public static IProcess getInstance(String json) {
			if (json != null) {
				try {
					return getInstance( new JSONObject(json) );
					
				} catch (JSONException e) {
					Log.e(Process.class.getName(), e.getMessage(), e);
				}
			}
			
			return null;
		}
		
		public static IProcess getInstance(JSONObject in) {
			if (in != null) {
				try {
					Class<?> clazz = Class.forName(in.optString("class"), true, IProcess.class.getClassLoader());
					Constructor<?> constrcutor = clazz.getDeclaredConstructor();
					
					IProcess process = (IProcess) constrcutor.newInstance();
					process.readFromJSON(in);
					
					return process;
					
				} catch (InstantiationException e) {
					Log.e(Process.class.getName(), e.getMessage(), e);
					
				} catch (IllegalAccessException e) {
					Log.e(Process.class.getName(), e.getMessage(), e);
					
				} catch (InvocationTargetException e) {
					Log.e(Process.class.getName(), e.getMessage(), e);
					
				} catch (NoSuchMethodException e) {
					Log.e(Process.class.getName(), e.getMessage(), e);
					
				} catch (ClassNotFoundException e) {
					Log.e(Process.class.getName(), e.getMessage(), e);
				}
			}
			
			return null;
		}
	}
}
