package com.github.noreply.users.nagarei.cameraforpc;

import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class MjpegServer implements Runnable {
    private static int port = 8080;
    public static void setPort(int portNum) {
        port = portNum;
    }

    public interface ImageGetter {
        byte[] get();
    }

    private ImageGetter imageGetter;
    MjpegServer(ImageGetter imageGetter) {
        super();
        this.imageGetter = imageGetter;
    }

    @Override
    public void run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

        ServerSocket server;
        try {
            server = new ServerSocket(port);
            server.setSoTimeout(5000);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        InetAddress lastAddr = null;
        while(true) {
            try {
                Socket socket = server.accept();
                InetAddress target = socket.getInetAddress();
                if (target.isSiteLocalAddress() && !target.equals(lastAddr)) {
                    lastAddr = target;
                    Log.d("MjpegServer", "talkToClient: " + target.getHostAddress());
                    MjpegSocket mjpegSocket = new MjpegSocket(socket, imageGetter);
                    new Thread(mjpegSocket).start();
                } else {
                    lastAddr = null;//MEMO: 同じアドレスから間をおいて二回アクセスしたときに通すようにする処理
                    socket.close();
                }
            } catch (SocketTimeoutException ste) {
                // continue silently
            } catch (IOException ioe) {
                ioe.printStackTrace();
                return;
            }
        }

    }
}
