package com.example.ducks.screen.connection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.example.ducks.screen.connection.Sync;

public class Autorun extends BroadcastReceiver {

    //Запуск сервиса Sync при перезагрузке телефона (для того, чтобы успеть синхронизировать)
    //Sync service launch on phone reboot (to synchronize them)
    @Override
    public void onReceive(Context context, Intent arg1) {
        Intent intent = new Intent(context, Sync.class);
        context.startService(intent);
        Log.i("Autorun", "started");
    }
}