package com.example.ducks.screen;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.*;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;

import static com.example.ducks.screen.ExtractMpegFramesTest.FPS;

public class VideoSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private DrawThread drawThread;
    public static int ax = 0, ay = 0, bx = 640, by = 480;
    public static boolean end = false, start = false;

    public VideoSurfaceView(Context context) {
        super(context);
        getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        end = false;
        drawThread = new DrawThread(getContext(), getHolder());
        drawThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        drawThread.requestStop();
    }

    class DrawThread extends Thread {

        private SurfaceHolder surfaceHolder;

        private volatile boolean running = true; // флаг для остановки потока

        public DrawThread(Context context, SurfaceHolder surfaceHolder) {
            this.surfaceHolder = surfaceHolder;
        }

        public void requestStop() {
            running = false;
            end = true;
        }

        @Override
        public void run() {
            final Timer timer = new Timer();
            DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
            Log.d("Metrics ", metrics.heightPixels + "x" + metrics.widthPixels);
            Matrix matrix = new Matrix();
            float scale = (float) metrics.heightPixels / (by - ay);
            matrix.preScale(scale, scale);
            matrix.postTranslate(-scale * ax, -scale * ay);

            while (!start) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            Log.e("FPS", 1000 / FPS + "");

            timer.schedule(new TimerTask() {
                public void run() {
                    if (ExtractMpegFramesTest.list.size() > 0) {
                        if (!running)
                            timer.cancel();
                        Canvas canvas = surfaceHolder.lockCanvas();
                        Paint paint = new Paint();
                        try {
                            synchronized (ExtractMpegFramesTest.list) {
                                canvas.drawBitmap(ExtractMpegFramesTest.list.get(0), matrix, paint);
                                ExtractMpegFramesTest.list.remove(0);
                            }
                        } catch (NoSuchElementException e) {
                            e.printStackTrace();
                            if (end)
                                running = false;
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            if (running)
                                surfaceHolder.unlockCanvasAndPost(canvas);
                        }
                    }
                }
            }, 0, 1000 / FPS);
        }
    }
}

