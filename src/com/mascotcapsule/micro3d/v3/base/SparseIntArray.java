/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Ported from KEmulator ru.woesss.j2me.micro3d into com.mascotcapsule.micro3d.v3.base.
 * Pure JDK: int->int map backed by parallel arrays.
 */
package com.mascotcapsule.micro3d.v3.base;

import java.util.Arrays;

public class SparseIntArray implements Cloneable {
    private int[] mKeys;
    private int[] mValues;
    private int mSize;

    public SparseIntArray() {
        this(0);
    }

    public SparseIntArray(int initialCapacity) {
        if (initialCapacity == 0) {
            mKeys = new int[0];
            mValues = new int[0];
        } else {
            mKeys = new int[initialCapacity];
            mValues = new int[mKeys.length];
        }
        mSize = 0;
    }

    @Override
    public SparseIntArray clone() {
        SparseIntArray clone = null;
        try {
            clone = (SparseIntArray) super.clone();
            clone.mKeys = mKeys.clone();
            clone.mValues = mValues.clone();
        } catch (CloneNotSupportedException cnse) {
            /* ignore */
        }
        return clone;
    }

    public int get(int key) {
        return get(key, 0);
    }

    public int get(int key, int valueIfKeyNotFound) {
        int i = search(mKeys, mSize, key);
        if (i < 0) {
            return valueIfKeyNotFound;
        } else {
            return mValues[i];
        }
    }

    public void delete(int key) {
        int i = search(mKeys, mSize, key);
        if (i >= 0) {
            removeAt(i);
        }
    }

    public void removeAt(int index) {
        System.arraycopy(mKeys, index + 1, mKeys, index, mSize - (index + 1));
        System.arraycopy(mValues, index + 1, mValues, index, mSize - (index + 1));
        mSize--;
    }

    public void put(int key, int value) {
        int i = search(mKeys, mSize, key);
        if (i >= 0) {
            mValues[i] = value;
        } else {
            i = ~i;
            mKeys = insert(mKeys, mSize, i, key);
            mValues = insert(mValues, mSize, i, value);
            mSize++;
        }
    }

    public int size() {
        return mSize;
    }

    public int keyAt(int index) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return mKeys[index];
    }

    public int valueAt(int index) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return mValues[index];
    }

    public void setValueAt(int index, int value) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        mValues[index] = value;
    }

    public int indexOfKey(int key) {
        return search(mKeys, mSize, key);
    }

    public int indexOfValue(int value) {
        for (int i = 0; i < mSize; i++)
            if (mValues[i] == value)
                return i;
        return -1;
    }

    public void clear() {
        mSize = 0;
    }

    public void append(int key, int value) {
        if (mSize != 0 && key <= mKeys[mSize - 1]) {
            put(key, value);
            return;
        }
        mKeys = append(mKeys, mSize, key);
        mValues = append(mValues, mSize, value);
        mSize++;
    }

    public int[] copyKeys() {
        if (size() == 0) {
            return null;
        }
        return Arrays.copyOf(mKeys, size());
    }

    @Override
    public String toString() {
        if (size() <= 0) {
            return "{}";
        }
        StringBuilder buffer = new StringBuilder(mSize * 28);
        buffer.append('{');
        for (int i=0; i<mSize; i++) {
            if (i > 0) {
                buffer.append(", ");
            }
            int key = keyAt(i);
            buffer.append(key);
            buffer.append('=');
            int value = valueAt(i);
            buffer.append(value);
        }
        buffer.append('}');
        return buffer.toString();
    }

    private static int search(int[] mKeys, int mSize, int key) {
        for (int i = 0; i < mSize && i < mKeys.length; i++) {
            if (mKeys[i] == key) return i;
        }
        return -1;
    }

    private static int[] append(int[] mKeys, int mSize, int key) {
        if (mKeys.length <= mSize) {
            System.arraycopy(mKeys, 0, mKeys = new int[mSize + 16], 0, mSize);
        }
        mKeys[mSize] = key;
        return mKeys;
    }

    private static int[] insert(int[] mKeys, int mSize, int i, int key) {
        if (mKeys.length <= mSize) {
            System.arraycopy(mKeys, 0, mKeys = new int[mSize + 16], 0, mSize);
        }
        int size = mSize - i;
        if (size > 0)
            System.arraycopy(mKeys, i, mKeys, i + 1, size);
        mKeys[i] = key;
        return mKeys;
    }
}
