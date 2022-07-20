package com.github.noreply.users.nagarei.cameraforpc;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.net.InetAddress;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class MainActivity extends AppCompatActivity {
    private CameraView cameraView;
    private static final int MY_CAMERA_REQUEST_CODE = 1120;
    private TextView myIpAddressView;
    private Thread serverThread = new Thread(new MjpegServer(this::loadCameraImage));

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
            checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.ACCESS_NETWORK_STATE}, MY_CAMERA_REQUEST_CODE);
        }
        if (
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.e("MainActivity", "checkSelfPermission: fail");
            return;
        }

        loadPreferences();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraView.stop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!serverThread.isAlive()) {
            serverThread.start();
        }
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
}