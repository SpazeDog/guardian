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

package com.spazedog.guardian.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.spazedog.guardian.db.AlertsDB.EntityRow;
import com.spazedog.guardian.scanner.containers.ProcEntity;
import com.spazedog.guardian.scanner.containers.ProcStat;

import java.lang.ref.WeakReference;
import java.util.Iterator;

public class AlertsDB extends SQLiteOpenHelper implements Iterable<EntityRow> {
	
	private static final Integer DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "alert_cache";
	private static final String TABLE_NAME = "alerts";
	
	private static final String COLUMN_ID = "id";
	private static final String COLUMN_PROCESS = "cProcess";
	private static final String COLUMN_ENTITY = "cEntity";
	private static final String COLUMN_DATE = "cDate";
	
	private WeakReference<Context> mContext;
	
	public AlertsDB(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		
		mContext = new WeakReference<Context>(context.getApplicationContext());
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(
			"CREATE TABLE " + TABLE_NAME + "("
					+ COLUMN_ID + " INTEGER PRIMARY KEY,"
					+ COLUMN_PROCESS + " STRING UNIQUE,"
					+ COLUMN_ENTITY + " TEXT,"
					+ COLUMN_DATE + " INTEGER)"
		);
	}
	
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		
	}
	
	public void clearProcessEntities() {
		SQLiteDatabase database = getWritableDatabase();
		
		database.delete(TABLE_NAME, null, null);
		database.close();
	}
	
	public void addProcessEntity(ProcEntity<?> entity) {
		SQLiteDatabase database = getWritableDatabase();
		ContentValues values = new ContentValues();
		
		values.put(COLUMN_PROCESS, entity.getProcessName());
		values.put(COLUMN_ENTITY, entity.loadToJSON( mContext.get() ).toString());
		values.put(COLUMN_DATE, System.currentTimeMillis());
		
		database.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
		database.close();
	}
	
	@Override
	public Iterator<EntityRow> iterator() {
		SQLiteDatabase database = getReadableDatabase();
		final Cursor cursor = database.rawQuery(String.format("SELECT %s, %s FROM %s ORDER BY %s DESC", COLUMN_ENTITY, COLUMN_DATE, TABLE_NAME, COLUMN_DATE), null);
		
		return new Iterator<EntityRow>() {
			
			private Boolean mHasNext;
			
			@Override
			public boolean hasNext() {
				if (mHasNext == null) {
					mHasNext = cursor.moveToFirst();
				}
				
				return mHasNext;
			}
			
			@Override
			public EntityRow next() {
				EntityRow row = new EntityRow();
				row.mEntity = (ProcEntity) ProcStat.getInstance(cursor.getString(0));
				row.mTime = cursor.getLong(1);
				
				// Move the cursor
				mHasNext = cursor.moveToNext();
				
				return row;
			}
			
			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
	
	public static class EntityRow {
		
		protected ProcEntity<?> mEntity;
		protected long mTime = 0;
		
		protected EntityRow() {}
		
		public ProcEntity<?> getEntity() {
			return mEntity;
		}
		
		public long getTime() {
			return mTime;
		}
	}
}
