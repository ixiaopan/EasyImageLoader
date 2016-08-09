package me.xiaopan.sketchsample.util;

import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import me.xiaopan.sketch.Configuration;
import me.xiaopan.sketch.Sketch;
import me.xiaopan.sketch.cache.DiskCache;
import me.xiaopan.sketch.feature.ImagePreprocessor;
import me.xiaopan.sketch.feature.PreProcessResult;
import me.xiaopan.sketch.request.ImageFrom;
import me.xiaopan.sketch.request.LoadRequest;
import me.xiaopan.sketch.request.UriScheme;
import me.xiaopan.sketch.util.DiskLruCache;
import me.xiaopan.sketch.util.SketchUtils;

/**
 * 在继承ImagePreprocessor的基础上扩展了解析XPK文件的图标
 */
public class MyImagePreprocessor extends ImagePreprocessor {

    public MyImagePreprocessor() {
        logName = "MyImagePreprocessor";
    }

    @Override
    public boolean isSpecific(LoadRequest loadRequest) {
        return super.isSpecific(loadRequest) || isXpkFile(loadRequest);
    }

    @Override
    public PreProcessResult process(LoadRequest loadRequest) {
        if (isXpkFile(loadRequest)) {
            return getXpkIconCacheFile(loadRequest);
        }
        return super.process(loadRequest);
    }

    private boolean isXpkFile(LoadRequest loadRequest) {
        return loadRequest.getAttrs().getUriScheme() == UriScheme.FILE
                && SketchUtils.checkSuffix(loadRequest.getAttrs().getRealUri(), ".xpk");
    }

    /**
     * 获取XPK图标的缓存文件
     */
    private PreProcessResult getXpkIconCacheFile(LoadRequest loadRequest) {
        String realUri = loadRequest.getAttrs().getRealUri();
        Configuration configuration = loadRequest.getSketch().getConfiguration();
        DiskCache diskCache = configuration.getDiskCache();

        File xpkFile = new File(realUri);
        if (!xpkFile.exists()) {
            return null;
        }
        long lastModifyTime = xpkFile.lastModified();
        String diskCacheKey = realUri + "." + lastModifyTime;

        ReentrantLock diskCacheEditLock = diskCache.getEditLock(diskCacheKey);
        if (diskCacheEditLock != null) {
            diskCacheEditLock.lock();
        }
        PreProcessResult result = readXpkIcon(diskCache, loadRequest, diskCacheKey, realUri);
        if (diskCacheEditLock != null) {
            diskCacheEditLock.unlock();
        }
        return result;
    }

    private PreProcessResult readXpkIcon(DiskCache diskCache, LoadRequest loadRequest, String diskCacheKey, String realUri) {
        DiskCache.Entry xpkIconDiskCacheEntry = diskCache.get(diskCacheKey);
        if (xpkIconDiskCacheEntry != null) {
            return new PreProcessResult(xpkIconDiskCacheEntry, ImageFrom.DISK_CACHE);
        }

        ZipFile zipFile;
        try {
            zipFile = new ZipFile(realUri);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        InputStream inputStream;
        ZipEntry zipEntry = zipFile.getEntry("icon.png");
        if (zipEntry == null) {
            if (Sketch.isDebugMode()) {
                Log.w(Sketch.TAG, SketchUtils.concat(logName,
                        " - ", "not found icon.png in ",
                        " - ", loadRequest.getAttrs().getId()));
            }
            return null;
        }

        try {
            inputStream = zipFile.getInputStream(zipEntry);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        DiskCache.Editor diskCacheEditor = diskCache.edit(diskCacheKey);
        OutputStream outputStream;
        if (diskCacheEditor != null) {
            try {
                outputStream = new BufferedOutputStream(diskCacheEditor.newOutputStream(), 8 * 1024);
            } catch (IOException e) {
                e.printStackTrace();
                diskCacheEditor.abort();
                SketchUtils.close(inputStream);
                return null;
            }
        } else {
            outputStream = new ByteArrayOutputStream();
        }

        try {
            byte[] buffer = new byte[8 * 1024];
            int realLength;
            while (true) {
                realLength = inputStream.read(buffer);
                if (realLength < 0) {
                    break;
                }
                outputStream.write(buffer, 0, realLength);
            }

            if (diskCacheEditor != null) {
                diskCacheEditor.commit();
            }
        } catch (DiskLruCache.EditorChangedException e) {
            e.printStackTrace();
            diskCacheEditor.abort();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            if (diskCacheEditor != null) {
                diskCacheEditor.abort();
            }
            return null;
        } catch (DiskLruCache.ClosedException e) {
            e.printStackTrace();
            diskCacheEditor.abort();
            return null;
        } finally {
            SketchUtils.close(inputStream);
            SketchUtils.close(outputStream);
        }

        if (diskCacheEditor != null) {
            xpkIconDiskCacheEntry = diskCache.get(diskCacheKey);
            if (xpkIconDiskCacheEntry != null) {
                return new PreProcessResult(xpkIconDiskCacheEntry, ImageFrom.LOCAL);
            } else {
                if (Sketch.isDebugMode()) {
                    Log.w(Sketch.TAG, SketchUtils.concat(logName,
                            " - ", "not found xpk icon cache file",
                            " - ", loadRequest.getAttrs().getId()));
                }
                return null;
            }
        } else {
            return new PreProcessResult(((ByteArrayOutputStream) outputStream).toByteArray(), ImageFrom.LOCAL);
        }
    }
}
