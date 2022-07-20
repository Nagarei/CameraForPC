package com.github.noreply.users.nagarei.cameraforpc;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.core.app.ActivityCompat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Camera {
    // utility
    private HandlerThread backgroundHandlerThread;
    private Handler backgroundHandler;
    private void startBackgroundHandler() {
        if (backgroundHandler != null) {
            return;
        }
        backgroundHandlerThread = new HandlerThread("CameraBackground");
        backgroundHandlerThread.start();
        backgroundHandler = new Handler(backgroundHandlerThread.getLooper());
    }
    private void stopBackgroundHandler() {
        if (backgroundHandler == null) {
            return;
        }
        try {
            backgroundHandlerThread.quitSafely();
            backgroundHandlerThread.join();
            backgroundHandlerThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    public void setPreviewCallback(PreviewCallback previewCallbackParam) {
        previewCallback = previewCallbackParam;
    }
    public void start(Context context, Surface surface) {
        previewSurface = surface;
        openCamera(context);
    }
    public void stop() {
        Log.d("Camera", "stop");
        if (null != captureSession) {
            captureSession.close();
            captureSession = null;
        }
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null !=  imageReaderJPG) {
            imageReaderJPG.close();
            imageReaderJPG = null;
        }
        stopBackgroundHandler();
    }

    // Camera
    private CameraDevice cameraDevice;
    private void openCamera(Context context) {
        Log.d("Camera", "openCamera");
        // check permission
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e("Camera", "checkSelfPermission fail");
            return;//TODO: ERROR処理
        }

        startBackgroundHandler();
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            Log.e("Camera", "context.getSystemService fail");
            return;//TODO: ERROR処理
        }
        String cameraId = getCameraId(manager, CameraCharacteristics.LENS_FACING_BACK);
        if (cameraId == null) {
            Log.e("Camera", "getCameraId fail");
            return;//TODO: ERROR処理
        }

        CameraCharacteristics characteristics = null;
        try {
            characteristics = manager.getCameraCharacteristics(cameraId);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        if (characteristics == null) {
            return;//TODO: ERROR処理
        }
        setUpImageReaderJpeg(characteristics);

        //openCamera
        CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice cameraDeviceParam) {
                cameraDevice = cameraDeviceParam;
                createCameraPreviewSession();
            }

            @Override
            public void onDisconnected(CameraDevice cameraDeviceParam) {
                cameraDeviceParam.close();
                cameraDevice = null;
            }

            @Override
            public void onError(CameraDevice cameraDeviceParam, int error) {
                cameraDeviceParam.close();
                cameraDevice = null;
                //TODO: ERROR処理
            }
        };
        try {
            manager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            //TODO: ERROR処理
        }
        Log.d("Camera", "openCamera Done");
    }
    private String getCameraId(CameraManager manager, int cameraFacing) {
        String cameraId = null;
        try {
            String[] ids = manager.getCameraIdList();
            for (String id : ids) {
                CameraCharacteristics c
                        = manager.getCameraCharacteristics(id);
                int facing = c.get(CameraCharacteristics.LENS_FACING);
                if (facing == cameraFacing) {
                    cameraId = id;
                    break;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return cameraId;
    }

    // CameraPreview
    private final int IMAGE_READER_MAX_IMAGES = 4;
    private final int JPEG_QUALITY = 80;
    private ImageReader imageReaderJPG;
    private Surface previewSurface;
    private CameraCaptureSession captureSession;
    private final int frameSizeW = 640;
    private final int frameSizeH = 480;

    public interface PreviewCallback {
        void onPreview(byte[] bytes);
    }

    private PreviewCallback previewCallback;

    // JPEG 形式の ImageReaderを生成する
    private void setUpImageReaderJpeg(CameraCharacteristics characteristics) {
        //サイズ取得
        StreamConfigurationMap map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] supportedSizes = map.getOutputSizes(ImageFormat.JPEG);
        Size jpegSizeLargest = Collections.max(Arrays.asList(supportedSizes),
                (lhs, rhs) -> Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight()));

        imageReaderJPG = ImageReader.newInstance(
                jpegSizeLargest.getWidth(),
                jpegSizeLargest.getHeight(),
                ImageFormat.JPEG, IMAGE_READER_MAX_IMAGES);
        startBackgroundHandler();
        imageReaderJPG.setOnImageAvailableListener(
                this::callPreviewCallback, backgroundHandler);
    }
    private void callPreviewCallback(ImageReader reader) {
        try (
                Image image = reader.acquireNextImage()
        ) {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.capacity()];
            buffer.get(bytes);

            // resize
            Bitmap bitmap = BitmapFactory.decodeByteArray(
                    bytes, 0, bytes.length, null);
            bitmap = resizeBitmap(bitmap, frameSizeW, frameSizeH);
            bytes = convBitmapToJpegByteArray(bitmap);

            if(previewCallback != null) {
                previewCallback.onPreview(bytes);
            }
        }
    }
    private static Bitmap resizeBitmap(Bitmap source, int width, int height) {
        int src_width = source.getWidth() ;
        int src_height = source.getHeight();
        int limit_width = (int)( src_width * 0.8 );
        int limit_height = (int)( src_height * 0.8 );
        if( width > limit_width) {
            return source;
        }
        if( height >  limit_height) {
            return source;
        }

        Bitmap bitmap = Bitmap.createScaledBitmap(
                source, width, height, true );
        return bitmap;
    }
    private byte[] convBitmapToJpegByteArray(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos);
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("Camera", "failed to convert");
        }
        return null;
    }

    private void createCameraPreviewSession() {
        // skip, if not open camera
        if (cameraDevice == null) return;

        Log.d("Camera", "createCameraPreviewSession");
        try {
            // We set up a CaptureRequest.Builder with the output Surface.
            Surface imageReaderSurface = imageReaderJPG.getSurface();
            CaptureRequest.Builder previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(previewSurface);
            previewRequestBuilder.addTarget(imageReaderSurface);
            // Here, we create a CameraCaptureSession for camera preview.
            List<Surface> outputs = new ArrayList<>();
            outputs.add(previewSurface);
            outputs.add(imageReaderSurface);
            CameraCaptureSession.StateCallback previewSession = new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    // The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }

                    // When the session is ready, we start displaying the preview.
                    captureSession = cameraCaptureSession;
                    try {
                        // Auto focus should be continuous for camera preview.
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        //if (isFlashSupported) {
                        //    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, mFlashMode);
                        //}

                        // Finally, we start displaying the camera preview.
                        captureSession.setRepeatingRequest(previewRequestBuilder.build(),
                                null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                        //TODO: ERROR処理
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {}
            };
            cameraDevice.createCaptureSession(outputs, previewSession, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private Bitmap convImageJpegToBitmap(Image imageJpeg) {
        //log_d("convImageJpegToBitmap");
        // ImageJpeg ->JpegByteArray
        ByteBuffer buffer = imageJpeg.getPlanes()[0].getBuffer();
        int size = buffer.capacity();
        byte[] bytes = new byte[size];
        buffer.get(bytes);

        // JpegByteArray -> Bitmap
        int length = bytes.length;
        Bitmap bitmap = BitmapFactory.decodeByteArray(
                bytes, 0, length, null);
        return bitmap;
    }
}
