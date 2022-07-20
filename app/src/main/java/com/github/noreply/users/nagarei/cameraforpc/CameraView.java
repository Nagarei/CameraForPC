package com.github.noreply.users.nagarei.cameraforpc;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;


public class CameraView extends ViewGroup {

    private final Context context;
    private final Camera camera;
    private final SurfaceView surfaceView;
    private boolean isStartRequested = false;
    private boolean isSurfaceViewAvailable = false;
    private Camera.PreviewCallback previewCallback;
    public void setPreviewCallback(Camera.PreviewCallback previewCallbackParam) {
        camera.setPreviewCallback(previewCallback);
    }

    public CameraView(Context context) {
        this(context, null);
    }
    public CameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Log.d("CameraView", "CameraView.CameraView: ");
        this.context = context;
        camera = new Camera();

        surfaceView = new SurfaceView(context);
        addView(surfaceView);
        SurfaceHolder.Callback mSurfaceViewListener = new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surface) {
                isSurfaceViewAvailable = true;
                startIfReady();
            }
            @Override
            public void surfaceDestroyed(SurfaceHolder surface) {
                isSurfaceViewAvailable = false;
            }
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                // nop
            }
        };
        surfaceView.getHolder().addCallback(mSurfaceViewListener);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int layoutWidth = right - left;
        int layoutHeight = bottom - top;
        int childWidth = layoutWidth;
        int childHeight = layoutHeight;
        for (int i = 0; i < getChildCount(); ++i) {
            getChildAt(i).layout(0, 0, childWidth, childHeight);
        }
    }

    public void start() {
        isStartRequested = true;
        startIfReady();
    }
    private void startIfReady() {
        if (!isStartRequested || !isSurfaceViewAvailable) {
            return;
        }
        isStartRequested = false;
        Log.d("CameraView", "CameraView.start: ");
        SurfaceHolder holder = surfaceView.getHolder();
        camera.start(context, holder.getSurface());
    }
    public void stop() {
        isStartRequested = false;
        camera.stop();
    }


}
