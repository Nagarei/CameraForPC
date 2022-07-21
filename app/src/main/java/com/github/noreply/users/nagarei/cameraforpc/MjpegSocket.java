package com.github.noreply.users.nagarei.cameraforpc;

import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.io.DataOutputStream;
import java.util.Timer;
import java.util.TimerTask;

public class MjpegSocket implements Runnable{
    private Socket socket;
    private String boundary = "CameraServeDataBoundary";
    private MjpegServer.ImageGetter imageGetter;
    public MjpegSocket(Socket socket, MjpegServer.ImageGetter imageGetter) {
        this.socket = socket;
        this.imageGetter = imageGetter;
    }

    @Override
    public void run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

        try {
            OutputStream stream = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            stream.write(("HTTP/1.0 200 OK\r\n" +
                    "Server: CameraServe\r\n" +
                    "Connection: close\r\n" +
                    "Max-Age: 0\r\n" +
                    "Expires: 0\r\n" +
                    "Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
                    "Pragma: no-cache\r\n" +
                    "Content-Type: multipart/x-mixed-replace; boundary=" + boundary + "\r\n" +
                    "\r\n" +
                    "--" + boundary + "\r\n").getBytes());
            stream.flush();

            Timer timer = new Timer();
            TimerTask task = new TimerTask() {
                public void run() {
                    try {
                        if (!socket.isConnected()) {
                            if (!socket.isClosed()) {
                                socket.close();
                            }
                            Log.w("MjpegSocket", "end");
                            timer.cancel();
                            return;
                        }
                        byte[] frame = imageGetter.get();
                        if (frame == null) {
                            //Log.e("MjpegSocket", "nothing to send...");
                            return;
                        }

                        stream.write(("Content-type: image/jpeg\r\n" +
                                "Content-Length: " + frame.length + "\r\n" +
                                "\r\n").getBytes());
                        stream.write(frame);
                        stream.write(("\r\n--" + boundary + "\r\n").getBytes());
                        stream.flush();
                    } catch (java.net.SocketException e) {
                        Log.w("MjpegSocket", "end");
                        timer.cancel();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            timer.scheduleAtFixedRate(task, 0, 33);

        } catch (Exception e) {
            e.printStackTrace();
            Log.w("MjpegSocket", "end");
        }
    }
}
