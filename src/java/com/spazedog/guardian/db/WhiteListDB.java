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

import com.spazedog.guardian.Common;
import com.spazedog.guardian.scanner.containers.ProcEntity;
import com.spazedog.lib.utilsLib.JSONParcel;
import com.spazedog.lib.utilsLib.JSONParcel.JSONException;

import java.util.Iterator;

public class WhiteListDB extends SQLiteOpenHelper implements Iterable<ProcEntity<?>> {

    protected static final Integer DATABASE_VERSION = 2;
    protected static final String DATABASE_NAME = "white_list";
    protected static final String TABLE_NAME = "listing";

    protected static final String COLUMN_ID = "id";
    protected static final String COLUMN_PROCESS = "cProcess";
    protected static final String COLUMN_ENTITY = "cEntity";

    protected final Object mLock = new Object();

	private Context mContext;

	public WhiteListDB(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		
		mContext = context.getApplicationContext();
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(
			"CREATE TABLE " + TABLE_NAME + "("
					+ COLUMN_ID + " INTEGER PRIMARY KEY,"
					+ COLUMN_PROCESS + " STRING UNIQUE,"
					+ COLUMN_ENTITY + " TEXT)"
		);
	}
	
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.delete(TABLE_NAME, null, null);
    }
	
	public void addEntity(ProcEntity<?> entity) {
        synchronized (mLock) {
            SQLiteDatabase database = getWritableDatabase();
            ContentValues values = new ContentValues();

            values.put(COLUMN_PROCESS, entity.getProcessName());
            values.put(COLUMN_ENTITY, entity.getDataLoader(mContext).getJSONParcel().toString());

            database.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            database.close();
        }
	}

    public void removeEntity(String processName) {
        synchronized (mLock) {
            SQLiteDatabase database = getWritableDatabase();
            database.execSQL(String.format("DELETE FROM %s WHERE %s = '%s'", TABLE_NAME, COLUMN_PROCESS, processName));
            database.close();
        }
    }

    public boolean hasEntity(String processName) {
        synchronized (mLock) {
            SQLiteDatabase database = getReadableDatabase();
            Cursor cursor = database.rawQuery(String.format("SELECT %s FROM %s WHERE %s = '%s'", COLUMN_ENTITY, TABLE_NAME, COLUMN_PROCESS, processName), null);
            boolean hasEntity = cursor != null && cursor.moveToFirst();
            cursor.close();
            database.close();

            return hasEntity;
        }
    }
	
	@Override
	public Iterator<ProcEntity<?>> iterator() {
        synchronized (mLock) {
            final SQLiteDatabase database = getReadableDatabase();
            final Cursor cursor = database.rawQuery(String.format("SELECT %s FROM %s ORDER BY %s", COLUMN_ENTITY, TABLE_NAME, COLUMN_PROCESS), null);

            return new Iterator<ProcEntity<?>>() {

                private boolean mHasNext;

                @Override
                public boolean hasNext() {
                    synchronized (mLock) {
                        mHasNext = database.isOpen() && cursor.moveToNext();

                        if (!mHasNext) {
                            cursor.close();
                            database.close();
                        }

                        return mHasNext;
                    }
                }

                @Override
                public ProcEntity<?> next() {
                    synchronized (mLock) {
                        ProcEntity<?> entity = null;

                        try {
                            if (database.isOpen()) {
                                entity = new JSONParcel(cursor.getString(0)).readJSONParcelable(ProcEntity.class.getClassLoader());
                            }

                        } catch (JSONException e) {
                            Common.LOG.Error(this, e.getMessage(), e);
                        }

                        return entity;
                    }
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
	}
}
