package com.github.noreply.users.nagarei.cameraforpc;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

import java.net.InetAddress;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class MainActivity extends AppCompatActivity {
    private CameraView cameraView;
    private static final int MY_CAMERA_REQUEST_CODE = 1121;
    private TextView myIpAddressView;
    private Thread serverThread = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Do not sleep the screen
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Make the screen FULL Screen
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);
        cameraView = findViewById(R.id.photo_view);
        cameraView.setPreviewCallback(this::saveCameraImage);
        myIpAddressView = (TextView) findViewById(R.id.TextView_ipaddr);

        if (
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.ACCESS_NETWORK_STATE
            }, MY_CAMERA_REQUEST_CODE);
        }

        serverThread = new Thread(new MjpegServer(this::loadCameraImage));
        loadPreferences();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraView.stop();
        enableSleep();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (serverThread != null&& !serverThread.isAlive()) {
            serverThread.start();
        }
        disableSleep();
        cameraView.start();
    }

    private final ReentrantReadWriteLock cameraImageLock = new ReentrantReadWriteLock();
    private byte[] cameraImage = null;

    private void saveCameraImage(byte[] bytes) {
        try {
            cameraImageLock.writeLock().lock();
            cameraImage = bytes;
        } finally {
            cameraImageLock.writeLock().unlock();
        }
    }

    private byte[] loadCameraImage() {
        try {
            cameraImageLock.readLock().lock();
            return cameraImage;
        } finally {
            cameraImageLock.readLock().unlock();
        }
    }


    private String myIpAddress;

    private void loadPreferences() {
        //SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        {
            Thread t = new Thread(() -> {
                try {
                    myIpAddress = InetAddress.getLocalHost().getHostAddress();
                } catch (Exception e) {
                    //TODO: ERROR
                    Log.e("MainActivity", "loadPreferences: can not get HostAddress");
                    myIpAddress = "";
                }
            });
            t.start();
            try {
                t.join();
            } catch (Exception e) {
                //TODO: ERROR
                Log.e("MainActivity", "loadPreferences: can not join");
                myIpAddress = "";
            }
        }
        Log.d("MainActivity", myIpAddress);
        myIpAddressView.setText(myIpAddress);
        //Integer port = Integer.parseInt(preferences.getString("port", "8080"));
        Integer port = 8080;
        MjpegServer.setPort(port);
    }

    //スリープ禁止設定
    private PowerManager.WakeLock wakeLock;
    private void disableSleep() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CameraForPC:Camera-Lock");
    }
    private void enableSleep() {
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}