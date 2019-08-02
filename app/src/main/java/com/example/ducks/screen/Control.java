package com.example.ducks.screen;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static com.example.ducks.screen.Main.room;
import static com.example.ducks.screen.Search.URL;
import static com.example.ducks.screen.Search.getUnsafeOkHttpClient;

public class Control extends AppCompatActivity {

    private static boolean isPaused = true;
    private static boolean isStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);
        ImageButton imageButton = findViewById(R.id.play);
        if (!isPaused)
            imageButton.setImageResource(R.drawable.pause);
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isPaused) {
                    imageButton.setImageResource(R.drawable.pause);
                    isPaused = false;
                    if (!isStarted) {
                        new SendTime().start();
                        isStarted = true;
                    } else {
                        new SetPause().start();
                    }
                } else {
                    imageButton.setImageResource(R.drawable.play);
                    isPaused = true;
                    new SetPause().start();
                }

            }
        });
    }

    class SendTime extends Thread {
        @Override
        public void run() {
            super.run();
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(URL)
                    .client(getUnsafeOkHttpClient().build())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            long time = System.currentTimeMillis() + (int) Sync.deltaT + 15000;
            Call<Void> call = retrofit.create(Service.class).putStartVideo(room, time);
            try {
                call.execute();
                //отправка времени
                //send time
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    class SetPause extends Thread {
        @Override
        public void run() {
            super.run();
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(URL)
                    .client(getUnsafeOkHttpClient().build())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            Call<Void> call = retrofit.create(Service.class).setPause(room, isPaused);
            try {
                call.execute();
                //отправка паузы
                //send pause
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
