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

package com.spazedog.guardian.utils;


import org.json.JSONArray;
import org.json.JSONException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JSONParcel {

    protected static final int SCHEMA_VERSION = 1;

    protected static final int TYPE_NULL = -1;
    protected static final int TYPE_JSONPARCEL = 0;
    protected static final int TYPE_JSONPARCELARRAY = 1;
    protected static final int TYPE_STRING = 2;
    protected static final int TYPE_STRINGARRAY = 3;
    protected static final int TYPE_INTEGER = 4;
    protected static final int TYPE_INTEGERARRAY = 5;
    protected static final int TYPE_LONG = 6;
    protected static final int TYPE_LONGARRAY = 7;
    protected static final int TYPE_OBJECTARRAY = 512;
    protected static final int TYPE_LIST = 768;

    protected static final Map<ClassLoader, Map<String, JSONParcelable.JSONCreator>> mCreatorCache = new HashMap<ClassLoader, Map<String, JSONParcelable.JSONCreator>>();
    protected JSONArray mData = new JSONArray();
    protected int mSeek = 0;

    public JSONParcel() {

    }

    public JSONParcel(String json) throws JSONException {
        mData = new JSONArray(json);
    }

    public JSONParcelable[] readJSONParcelableArray() throws JSONException {
        return readJSONParcelableArray(null);
    }

    public JSONParcelable[] readJSONParcelableArray(ClassLoader classLoader) throws JSONException {
        int N = readInt();

        if (N >= 0) {
            JSONParcelable[] array = new JSONParcelable[N];

            for (int i = 0; i < N; i++) {
                array[i] = (JSONParcelable) readJSONParcelable(classLoader);
            }

            return array;
        }

        return null;
    }

    public void writeJSONParcelableArray(JSONParcelable[] data) {
        if (data != null) {
            writeInt(data.length);

            for (int i=0; i < data.length; i++) {
                writeJSONParcelable(data[i]);
            }

        } else {
            writeInt(-1);
        }
    }

    public <T extends JSONParcelable> T readJSONParcelable() throws JSONException {
        return readJSONParcelable(null);
    }

    public <T extends JSONParcelable> T readJSONParcelable(ClassLoader loader) throws JSONException {
        synchronized (mCreatorCache) {
            int schema = readInt();
            String className = readString();

            if (className != null && schema == SCHEMA_VERSION) {
                Map<String, JSONParcelable.JSONCreator> loaderCache = mCreatorCache.get(loader);

                if (loaderCache == null) {
                    loaderCache = new HashMap<String, JSONParcelable.JSONCreator>();
                    mCreatorCache.put(loader, loaderCache);
                }

                JSONParcelable.JSONCreator<T> creator = loaderCache.get(className);

                if (creator == null) {
                    try {
                        Class clazz = loader == null ? Class.forName(className) : Class.forName(className, true, loader);
                        Field field = clazz.getField("CREATOR");

                        creator = (JSONParcelable.JSONCreator) field.get(null);
                        loaderCache.put(className, creator);

                    } catch (Throwable e) {
                        throw new JSONException(e.getMessage());
                    }
                }

                return creator.createFromJSON(this);
            }
        }

        return null;
    }

    public void writeJSONParcelable(JSONParcelable data) {
        writeInt(SCHEMA_VERSION);

        if (data != null) {
            writeString(data.getClass().getName());
            data.writeToJSON(this);

        } else {
            writeString(null);
        }
    }

    public String readString() throws JSONException {
        return mData.getString(mSeek++);
    }

    public String[] readStringArray() throws JSONException {
        int N = readInt();

        if (N >= 0) {
            String[] out = new String[N];

            for (int i=0; i < N; i++) {
                out[i] = readString();
            }

            return out;
        }

        return null;
    }

    public void writeString(String data) {
        mData.put(data);
    }

    public void writeStringArray(String[] data) {
        if (data != null) {
            writeInt(data.length);

            for (int i=0; i < data.length; i++) {
                writeString(data[i]);
            }

        } else {
            writeInt(-1);
        }
    }

    public long readLong() throws JSONException {
        return mData.getLong(mSeek++);
    }

    public long[] readLongArray() throws JSONException {
        int N = readInt();

        if (N >= 0) {
            long[] out = new long[N];

            for (int i=0; i < N; i++) {
                out[i] = readLong();
            }

            return out;
        }

        return null;
    }

    public void writeLong(long data) {
        mData.put(data);
    }

    public void writeLongArray(long[] data) {
        if (data != null) {
            writeInt(data.length);

            for (int i=0; i < data.length; i++) {
                writeLong(data[i]);
            }

        } else {
            writeInt(-1);
        }
    }

    public int readInt() throws JSONException {
        return mData.getInt(mSeek++);
    }

    public int[] readIntArray() throws JSONException {
        int N = readInt();

        if (N >= 0) {
            int[] out = new int[N];

            for (int i=0; i < N; i++) {
                out[i] = readInt();
            }

            return out;
        }

        return null;
    }

    public void writeInt(int data) {
        mData.put(data);
    }

    public void writeIntArray(int[] data) {
        if (data != null) {
            writeInt(data.length);

            for (int i=0; i < data.length; i++) {
                writeInt(data[i]);
            }

        } else {
            writeInt(-1);
        }
    }

    public void writeArray(Object[] data) throws JSONException {
        if (data != null) {
            writeInt(data.length);

            for (int i=0; i < data.length; i++) {
                writeValue(data[i]);
            }

        } else {
            writeInt(-1);
        }
    }

    public Object[] readArray() throws JSONException {
        return readArray(null);
    }

    public Object[] readArray(ClassLoader classLoader) throws JSONException {
        int N = readInt();

        if (N >= 0) {
            Object[] out = new Object[N];

            for (int i=0; i < N; i++) {
                out[i] = readValue(classLoader);
            }

            return out;
        }

        return null;
    }

    public List<?> readList() throws JSONException {
        return readList(null);
    }

    public List<?> readList(ClassLoader classLoader) throws JSONException {
        int N = readInt();

        if (N >= 0) {
            List<Object> out = new ArrayList<Object>();

            for (int i=0; i < N; i++) {
                out.add(readValue(classLoader));
            }

            return out;
        }

        return null;
    }

    public void fillList(List out) throws JSONException {
        fillList(out, null);
    }

    public void fillList(List out, ClassLoader classLoader) throws JSONException {
        int N = readInt();

        if (N >= 0) {
            for (int i=0; i < N; i++) {
                out.add(readValue(classLoader));
            }
        }
    }

    public void writeList(List data) throws JSONException {
        if (data != null) {
            int N = data.size();
            writeInt(N);

            for (int i=0; i < N; i++) {
                writeValue(data.get(i));
            }

        } else {
            writeInt(-1);
        }
    }

    public Object readValue() throws JSONException {
        return readValue(null);
    }

    public Object readValue(ClassLoader classLoader) throws JSONException {
        int type = readInt();

        switch (type) {
            case TYPE_NULL:
                return null;

            case TYPE_JSONPARCEL:
                return readJSONParcelable(classLoader);

            case TYPE_JSONPARCELARRAY:
                return readJSONParcelableArray(classLoader);

            case TYPE_STRING:
                return readString();

            case TYPE_STRINGARRAY:
                return readStringArray();

            case TYPE_INTEGER:
                return readInt();

            case TYPE_INTEGERARRAY:
                return readIntArray();

            case TYPE_LONG:
                return readLong();

            case TYPE_LONGARRAY:
                return readLongArray();

            case TYPE_OBJECTARRAY:
                return readArray(classLoader);

            case TYPE_LIST:
                return readList(classLoader);

            default:
                throw new JSONException("Wrong type " + type);
        }
    }

    public void writeValue(Object data) throws JSONException {
        if (data == null) {
            writeInt(TYPE_NULL);

        } else if (data instanceof JSONParcelable) {
            writeInt(TYPE_JSONPARCEL);
            writeJSONParcelable((JSONParcelable) data);

        } else if (data instanceof JSONParcelable[]) {
            writeInt(TYPE_JSONPARCELARRAY);
            writeJSONParcelableArray((JSONParcelable[]) data);

        } else if (data instanceof String) {
            writeInt(TYPE_STRING);
            writeString((String) data);

        } else if (data instanceof String[]) {
            writeInt(TYPE_STRINGARRAY);
            writeStringArray((String[]) data);

        } else if (data instanceof Integer) {
            writeInt(TYPE_INTEGER);
            writeInt((Integer) data);

        } else if (data instanceof int[]) {
            writeInt(TYPE_INTEGERARRAY);
            writeIntArray((int[]) data);

        } else if (data instanceof Long) {
            writeInt(TYPE_LONG);
            writeLong((Long) data);

        } else if (data instanceof long[]) {
            writeInt(TYPE_LONGARRAY);
            writeLongArray((long[]) data);

        } else if (data instanceof List) {
            writeInt(TYPE_LIST);
            writeList((List) data);

        } else {
            Class<?> clazz = data.getClass();
            if (clazz.isArray() && clazz.getComponentType() == Object.class) {
                writeInt(TYPE_OBJECTARRAY);
                writeArray((Object[]) data);

            } else {
                throw new JSONException("Wrong type " + data);
            }
        }
    }

    @Override
    public String toString() {
        return mData.toString();
    }
}
