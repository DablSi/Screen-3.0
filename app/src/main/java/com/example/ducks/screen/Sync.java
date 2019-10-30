package com.example.ducks.screen;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

// synchronization class
public class Sync extends Service {

    public static final String SYNC = "Sync";
    public static boolean isStarted = false;
    public static float deltaT;
    int D;
    public static String date;
    static long t1, t2, t3;
    public Socket socket;
    private boolean first = false;


    private SyncThread syncThread;

    public Sync() {
        isStarted = true;
    }

    @Override
    public void onCreate() {
        // получение прошлой дельты из sharedPreferences
        // get last delta from sharedPreferences
        SharedPreferences sp = getSharedPreferences("Sync", MODE_PRIVATE);
        deltaT = sp.getFloat("deltaT", 0);
        if (deltaT == 0) {
            first = true;
        }

        syncThread = new SyncThread();
        syncThread.execute();

        // сохранение дельты в sharedPreferences для быстрой повторной синхронизации
        // save delta to sharedPreferences for fast re-sync
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                SharedPreferences sp = getSharedPreferences("Sync", MODE_PRIVATE);
                SharedPreferences.Editor edit = sp.edit();
                edit.putFloat("deltaT", deltaT);
                edit.commit();
                first = false;
            }
        }, first ? 35000 : 3000, 3000);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    class SyncThread extends AsyncTask<Void, Void, Void> {

        public SyncThread() {

        }

        @Override
        protected Void doInBackground(Void... voids) {
            socket = null;
            isStarted = true;
            try {
                socket = new Socket("178.79.155.166", 5001);
            } catch (IOException e) {
                Log.e("Everything is bad: ", "Not connected");
            }
            if (socket == null) {
                startService(new Intent(Sync.this, Sync.class));
                isStarted = false;
                stopService(new Intent(Sync.this, Sync.class));
                return null;
            }
            // успешное подключение к серверу
            // successfully connected to the server
            Log.d("Everything is fine: ", "Connected");
            DataInputStream input = null;
            DataOutputStream outputStream = null;
            try {
                input = new DataInputStream(socket.getInputStream());
                outputStream = new DataOutputStream(socket.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                // получение дельты времени
                // get time delta
                while (true) {
                    t1 = System.currentTimeMillis() + (int) deltaT;
                    outputStream.writeLong(t1);
                    outputStream.flush();
                    t1 = input.readLong();
                    t2 = input.readLong();
                    t3 = System.currentTimeMillis() + (int) deltaT;
                    int newD = (int) (t2 - (t1 + t3) / 2);
                    D = newD;
                    if (first) {
                        deltaT += (float) D / 10;
                    } else {
                        deltaT += (float) D / 100;
                    }
                    Log.d(SYNC, "delta is " + (int) deltaT);
                    Thread.sleep(200);
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // закрытие соединенеия
                // close connection
                try {
                    input.close();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }
    }
}