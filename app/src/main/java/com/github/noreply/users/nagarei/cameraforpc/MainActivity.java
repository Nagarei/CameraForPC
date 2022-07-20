package com.github.noreply.users.nagarei.cameraforpc;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
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
        if (serverThread != null && !serverThread.isAlive()) {
            serverThread.start();
        }
        disableSleep();
        cameraView.start();
    }

    private final ReentrantReadWriteLock cameraImageLock = new ReentrantReadWriteLock();
    private byte[] cameraImage = null;

    private void saveCameraImage(byte[] bytes) {
        //Log.d("MainActivity", "saveCameraImage" + Integer.toString(bytes.length));
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

        myIpAddress = getMyIpAddress();
        Log.d("MainActivity", myIpAddress);
        myIpAddressView.setText(myIpAddress);
        //Integer port = Integer.parseInt(preferences.getString("port", "8080"));
        Integer port = 8080;
        MjpegServer.setPort(port);
    }
    private static String getMyIpAddress() {

        //InetAddress.getLocalHost().getHostAddress();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                NetworkInterface intf = interfaces.nextElement();
                Enumeration<InetAddress> addresses = intf.getInetAddresses();

                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    if (addr.isLoopbackAddress()) {
                        continue;
                    }
                    String h_addr = addr.getHostAddress();
                    if ("0.0.0.0".equals(h_addr) || h_addr.contains("dummy")) {
                        continue;
                    }
                    if (addr instanceof Inet6Address){
                        continue;
                    }
                    return h_addr;
                } // while
            } // while
        } catch (Exception e) {
        }
        Log.e("MainActivity", "can not get HostAddress");
        return "";
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