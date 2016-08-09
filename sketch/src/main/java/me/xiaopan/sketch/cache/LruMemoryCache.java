/*
 * Copyright (C) 2013 Peng fei Pan <sky@xiaopan.me>
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.xiaopan.sketch.cache;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.format.Formatter;
import android.util.Log;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReentrantLock;

import me.xiaopan.sketch.Sketch;
import me.xiaopan.sketch.drawable.RecycleDrawable;
import me.xiaopan.sketch.util.LruCache;
import me.xiaopan.sketch.util.SketchUtils;

public class LruMemoryCache implements MemoryCache {
    private final LruCache<String, Drawable> drawableLruCache;
    protected String logName = "LruMemoryCache";
    private Context context;
    private Map<String, ReentrantLock> editLockMap;
    private boolean closed;

    public LruMemoryCache(Context context, int maxSize) {
        this.context = context;
        this.drawableLruCache = new DrawableLruCache(maxSize);
    }

    public static LruMemoryCache create(Context context) {
        return new LruMemoryCache(context, (int) (Runtime.getRuntime().maxMemory() / 8));
    }

    public static LruMemoryCache createPlaceholder(Context context) {
        long placeholderMemoryMaxSize = Runtime.getRuntime().maxMemory() / 32;

        // 不能小于2M
        placeholderMemoryMaxSize = Math.max(placeholderMemoryMaxSize, 2 * 1024 * 1024);

        return new LruMemoryCache(context, (int) placeholderMemoryMaxSize);
    }

    @Override
    public synchronized void put(String key, Drawable value) {
        if (closed) {
            return;
        }

        if (!(value instanceof RecycleDrawable)) {
            throw new IllegalArgumentException("drawable must be implemented RecycleDrawableInterface");
        }
        int oldCacheSize = 0;
        if (Sketch.isDebugMode()) {
            oldCacheSize = drawableLruCache.size();
        }
        drawableLruCache.put(key, value);
        if (Sketch.isDebugMode()) {
            int newCacheSize = drawableLruCache.size();
            Log.i(Sketch.TAG, SketchUtils.concat(logName,
                    " - ", "put",
                    " - ", "beforeCacheSize=", Formatter.formatFileSize(context, oldCacheSize),
                    " - ", ((RecycleDrawable) value).getInfo(),
                    " - ", "afterCacheSize=", Formatter.formatFileSize(context, newCacheSize)));
        }
    }

    @Override
    public synchronized Drawable get(String key) {
        if (closed) {
            return null;
        }

        return drawableLruCache.get(key);
    }

    @Override
    public synchronized Drawable remove(String key) {
        if (closed) {
            return null;
        }

        Drawable drawable = drawableLruCache.remove(key);
        if (Sketch.isDebugMode()) {
            Log.i(Sketch.TAG, SketchUtils.concat(logName,
                    " - ", "remove",
                    " - ", "MemoryCacheSize: ", Formatter.formatFileSize(context, drawableLruCache.size())));
        }
        return drawable;
    }

    @Override
    public synchronized long getSize() {
        if (closed) {
            return 0;
        }

        return drawableLruCache.size();
    }

    @Override
    public long getMaxSize() {
        return drawableLruCache.maxSize();
    }

    @Override
    public synchronized void clear() {
        if (closed) {
            return;
        }

        if (Sketch.isDebugMode()) {
            Log.i(Sketch.TAG, SketchUtils.concat(logName,
                    " - ", "clear",
                    " - ", "before clean MemoryCacheSize: ", Formatter.formatFileSize(context, drawableLruCache.size())));
        }
        drawableLruCache.evictAll();
    }

    @Override
    public synchronized boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }

        closed = true;

        drawableLruCache.evictAll();

        if (editLockMap != null) {
            editLockMap.clear();
            editLockMap = null;
        }
    }

    @Override
    public synchronized ReentrantLock getEditLock(String key) {
        if (closed) {
            return null;
        }

        if (key == null) {
            return null;
        }
        if (editLockMap == null) {
            synchronized (LruMemoryCache.this) {
                if (editLockMap == null) {
                    editLockMap = Collections.synchronizedMap(new WeakHashMap<String, ReentrantLock>());
                }
            }
        }
        ReentrantLock lock = editLockMap.get(key);
        if (lock == null) {
            lock = new ReentrantLock();
            editLockMap.put(key, lock);
        }
        return lock;
    }

    @Override
    public StringBuilder appendIdentifier(StringBuilder builder) {
        return builder.append(logName)
                .append("(")
                .append("maxSize").append("=").append(Formatter.formatFileSize(context, getMaxSize()))
                .append(")");
    }

    @Override
    public String getIdentifier() {
        return appendIdentifier(new StringBuilder()).toString();
    }

    private class DrawableLruCache extends LruCache<String, Drawable> {

        public DrawableLruCache(int maxSize) {
            super(maxSize);
        }

        @Override
        public Drawable put(String key, Drawable value) {
            ((RecycleDrawable) value).setIsCached(logName + ":put", true);
            return super.put(key, value);
        }

        @Override
        public int sizeOf(String key, Drawable value) {
            int bitmapSize = ((RecycleDrawable) value).getByteCount();
            return bitmapSize == 0 ? 1 : bitmapSize;
        }

        @Override
        protected void entryRemoved(boolean evicted, String key, Drawable oldValue, Drawable newValue) {
            ((RecycleDrawable) oldValue).setIsCached(logName + ":entryRemoved", false);
        }
    }
}