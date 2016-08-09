package me.xiaopan.sketchsample.large;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.lang.ref.WeakReference;

import me.xiaopan.sketch.Sketch;
import me.xiaopan.sketch.decode.ImageFormat;

public class RegionDecodeTask extends AsyncTask<Integer, Integer, Bitmap> {
    private static final String NAME = "RegionDecodeTask";

    private WeakReference<LargeImageDisplay> controllerReference;
    private WeakReference<ImageRegionDecoder> decoderReference;
    private Rect srcRect;
    private boolean canceled;
    private RectF visibleRect;
    private int inSampleSize;

    // TODO 自定义任务执行器以及线程池
    public RegionDecodeTask(LargeImageDisplay controller, ImageRegionDecoder decoder, Rect srcRect, RectF visibleRect, int inSampleSize) {
        this.controllerReference = new WeakReference<LargeImageDisplay>(controller);
        this.decoderReference = new WeakReference<ImageRegionDecoder>(decoder);
        this.srcRect = srcRect;
        this.visibleRect = visibleRect;
        this.inSampleSize = inSampleSize;
    }

    @SuppressWarnings("unused")
    public boolean isCanceled() {
        return canceled;
    }

    public void cancel() {
        canceled = true;
    }

    @Override
    protected Bitmap doInBackground(Integer... params) {
        if (canceled) {
            if (Sketch.isDebugMode()) {
                Log.d(Sketch.TAG, NAME + ". canceled");
            }
            return null;
        }

        ImageRegionDecoder decoder = decoderReference.get();
        if (decoder == null) {
            if (Sketch.isDebugMode()) {
                Log.d(Sketch.TAG, NAME + ". decoder is null");
            }
            return null;
        }

        if (!decoder.isReady()) {
            try {
                decoder.init();
            } catch (IOException e) {
                e.printStackTrace();
                if (Sketch.isDebugMode()) {
                    Log.d(Sketch.TAG, NAME + ". decoder init exception");
                }
                return null;
            }

            if (!decoder.isReady()) {
                if (Sketch.isDebugMode()) {
                    Log.d(Sketch.TAG, NAME + ". init ImageRegionDecoder failed");
                }
                return null;
            }
        }

        if (canceled) {
            if (Sketch.isDebugMode()) {
                Log.d(Sketch.TAG, NAME + ". canceled");
            }
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = inSampleSize;
        ImageFormat imageFormat = decoder.getImageFormat();
        if (imageFormat != null) {
            options.inPreferredConfig = imageFormat.getConfig(false);
        }

        Bitmap bitmap = decoder.decodeRegion(srcRect, options);
        if (bitmap == null || bitmap.isRecycled()) {
            if (Sketch.isDebugMode()) {
                Log.d(Sketch.TAG, NAME + ". bitmap is null or recycled");
            }
            return null;
        }

        Bitmap.Config config = bitmap.getConfig();
        if (Sketch.isDebugMode()) {
            Log.d(Sketch.TAG, NAME + ". decode success - "
                    + "visibleRect=" + visibleRect.toString()
                    + ", srcRect=" + srcRect.toString()
                    + ", inSampleSize=" + inSampleSize
                    + ", bitmapSize=" + bitmap.getWidth() + "x" + bitmap.getHeight()
                    + ", bitmapConfig=" + (config != null ? config.name() : null));
        }
        return bitmap;
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        super.onPostExecute(bitmap);

        if (bitmap == null) {
            if (Sketch.isDebugMode()) {
                Log.d(Sketch.TAG, NAME + ". onPostExecute. bitmap is null");
            }
            return;
        }

        LargeImageDisplay controller = controllerReference.get();
        if (controller == null) {
            if (Sketch.isDebugMode()) {
                Log.d(Sketch.TAG, NAME + ". onPostExecute. controller is null");
            }
            bitmap.recycle();
            return;
        }

        controller.onDecodeCompleted(bitmap, visibleRect);
    }
}
