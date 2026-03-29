package com.example.helloworld;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

import bsh.Interpreter;

/**
 * Runs a BeanShell REPL over a local TCP socket.
 * Connect from Termux: rlwrap nc localhost 8023
 */
public class ReplService extends Service {

    private static final String TAG = "BshRepl";
    private static final int PORT = 8023;
    private ServerSocket serverSocket;
    private Thread serverThread;

    @Override
    public void onCreate() {
        super.onCreate();
        serverThread = new Thread(new Runnable() {
            @Override public void run() { runServer(); }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        Log.i(TAG, "REPL service started on port " + PORT);
    }

    private void runServer() {
        try {
            serverSocket = new ServerSocket(PORT);
            while (!Thread.currentThread().isInterrupted()) {
                Socket client = serverSocket.accept();
                final Socket c = client;
                new Thread(new Runnable() {
                    @Override public void run() { handleClient(c); }
                }).start();
            }
        } catch (IOException e) {
            if (!Thread.currentThread().isInterrupted()) {
                Log.e(TAG, "Server error", e);
            }
        }
    }

    private void handleClient(Socket client) {
        try {
            Log.i(TAG, "Client connected: " + client.getRemoteSocketAddress());

            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();
            PrintStream ps = new PrintStream(out, true);

            Interpreter interpreter = new Interpreter(
                    new InputStreamReader(in), ps, ps, false);

            // Inject useful objects into the interpreter
            interpreter.set("context", getApplicationContext());
            interpreter.set("service", this);

            // If notification filter service is running, inject it
            NotificationFilterService nfs = NotificationFilterService.getInstance();
            if (nfs != null) {
                interpreter.set("notifService", nfs);
            }

            ps.println("BeanShell REPL (Android)");
            ps.println("Available: context, service, notifService");
            ps.flush();

            interpreter.run();

            client.close();
        } catch (Exception e) {
            Log.e(TAG, "Client error", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) { /* ignore */ }
        if (serverThread != null) serverThread.interrupt();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
