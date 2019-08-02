package com.example.ducks.screen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class Autorun extends BroadcastReceiver {

    //Запуск сервиса Sync при перезагрузке телефона (для того, чтобы успеть синхронизировать)
    //Sync service launch on phone reboot (to synchronize them)
    @Override
    public void onReceive(Context context, Intent arg1) {
        Intent intent = new Intent(context, Sync.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
        Log.i("Autorun", "started");
    }
}