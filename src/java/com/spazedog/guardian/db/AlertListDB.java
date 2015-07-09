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

import com.spazedog.guardian.backend.containers.ThresholdItem;
import com.spazedog.guardian.scanner.containers.ProcEntity;
import com.spazedog.guardian.utils.JSONParcel;

import org.json.JSONException;

import java.util.Iterator;


public class AlertListDB extends SQLiteOpenHelper implements Iterable<AlertListDB.ThresholdItemRow> {

    private static final Integer DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "alert_cache";
    private static final String TABLE_NAME = "alerts";

    private static final String COLUMN_ID = "id";
    private static final String COLUMN_PROCESS = "cProcess";
    private static final String COLUMN_ENTITY = "cEntity";
    private static final String COLUMN_DATE = "cDate";

    protected final Object mLock = new Object();

    private Context mContext;

    public AlertListDB(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);

        mContext = context.getApplicationContext();
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
        db.delete(TABLE_NAME, null, null);
    }

    public void clear() {
        synchronized (mLock) {
            SQLiteDatabase database = getWritableDatabase();

            database.delete(TABLE_NAME, null, null);
            database.close();
        }
    }

    public void addThresholdItem(ThresholdItem thresholdItem) {
        synchronized (mLock) {
            SQLiteDatabase database = getWritableDatabase();
            ContentValues values = new ContentValues();
            ProcEntity<?> entity = thresholdItem.getEntity();

            values.put(COLUMN_PROCESS, entity.getProcessName());
            values.put(COLUMN_ENTITY, thresholdItem.getJSONParcel(mContext).toString());
            values.put(COLUMN_DATE, System.currentTimeMillis());

            database.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            database.close();
        }
    }

    @Override
    public Iterator<ThresholdItemRow> iterator() {
        synchronized (mLock) {
            final SQLiteDatabase database = getReadableDatabase();
            final Cursor cursor = database.rawQuery(String.format("SELECT %s, %s FROM %s ORDER BY %s DESC", COLUMN_ENTITY, COLUMN_DATE, TABLE_NAME, COLUMN_DATE), null);

            return new Iterator<ThresholdItemRow>() {

                private Boolean mHasNext;

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
                public ThresholdItemRow next() {
                    synchronized (mLock) {
                        ThresholdItemRow row = new ThresholdItemRow();

                        try {
                            if (database.isOpen()) {
                                row.mThresholdItem = new JSONParcel(cursor.getString(0)).readJSONParcelable(ThresholdItem.class.getClassLoader());
                                row.mTime = cursor.getLong(1);
                            }

                        } catch (JSONException e) {
                        }

                        return row;
                    }
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    public static class ThresholdItemRow {

        protected ThresholdItem mThresholdItem;
        protected long mTime = 0;

        protected ThresholdItemRow() {}

        public ThresholdItem getThresholdItem() {
            return mThresholdItem;
        }

        public long getTime() {
            return mTime;
        }
    }
}
