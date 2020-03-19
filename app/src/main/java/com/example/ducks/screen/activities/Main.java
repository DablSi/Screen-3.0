package com.example.ducks.screen.activities;

import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import com.example.ducks.screen.media_codec.ExtractMpegFramesTest;
import com.example.ducks.screen.R;
import com.example.ducks.screen.connection.Service;
import com.example.ducks.screen.ui.VerticalStepView;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.File;
import java.util.LinkedList;

import static com.example.ducks.screen.activities.Search.URL;
import static com.example.ducks.screen.activities.Search.getUnsafeOkHttpClient;

public class Main extends AppCompatActivity {
    public static final int REQUEST_TAKE_GALLERY_VIDEO = 0, REQUEST_START_CAMERA_ACTIVITY = 1;
    public static byte[] video;
    public static int room = -1;
    private static boolean isUploaded = false;
    public static String android_id;
    private VerticalStepView verticalStepView;
    private static int position = 0;
    private static LinkedList<String> source;
    public static int videoH=0;
    public static int videoW=0;


    // открывает проводник для выбора файла
    // opens explorer to select file
    private void showFileChooser() {
        Intent intent = new Intent();
        intent.setType("video/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Video"), REQUEST_TAKE_GALLERY_VIDEO);
    }

    // открывает таймер, если фото было удачным
    // opens the timer if the photo was successful
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_TAKE_GALLERY_VIDEO) {
                Uri selectedImageUri = data.getData();
                String selectedImagePath = getPath(selectedImageUri);
                if (selectedImagePath != null) {
                    Log.e("FILE", selectedImagePath);
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    retriever.setDataSource(selectedImagePath);
                    videoW = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                    videoH = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                    retriever.release();
                    ExtractMpegFramesTest.FILES_DIR = selectedImagePath;
                    RoomAsync roomAsync = new RoomAsync();
                    roomAsync.start();
                }
            }
        }
        if (requestCode == REQUEST_START_CAMERA_ACTIVITY && resultCode == REQUEST_START_CAMERA_ACTIVITY) {
            startActivity(new Intent(Main.this, Control.class));
        }
    }

    class RoomAsync extends Thread {

        @Override
        public void run() {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(URL)
                    .client(getUnsafeOkHttpClient().build())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            Service service = retrofit.create(Service.class);
            Call<Integer> integerCall = service.getRoom();
            try {
                // получение номера комнаты
                // get room number
                Response<Integer> integerResponse = integerCall.execute();
                room = integerResponse.body();

                // создание комнаты
                // create room
                Call<Integer> call = service.putDevice(android_id, room, null);
                call.execute();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(Main.this, "Подождите, видео загружается", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(Main.this, "Проверьте подключение к интернету", Toast.LENGTH_LONG).show();
                    }
                });
            }
            File file = new File(ExtractMpegFramesTest.FILES_DIR);
            RequestBody requestBody = RequestBody.create(MediaType.parse("*/*"), file);
            MultipartBody.Part fileToUpload = MultipartBody.Part.createFormData("video", file.getName(), requestBody);
            // загрузка видео на сервер
            // upload video to the server
            service.uploadVideo(fileToUpload, room).enqueue(new Callback<Void>() {

                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(Main.this, "Видео загрузилось", Toast.LENGTH_LONG).show();
                            position++;
                            source.remove(1);
                            source.add(1, "Ввести комнату на всех девайсах видеостены (" + room + ")");
                            verticalStepView
                                    .setStepsViewIndicatorComplectingPosition(position)
                                    .setStepViewTexts(source)
                                    .reverseDraw(false);
                        }
                    });
                }


                @Override
                public void onFailure(Call<Void> call, Throwable t) {

                }
            });
        }
    }

    // получение пути из URI
    // get the path from URI
    private String getPath(Uri uri) {
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            String[] projection = {MediaStore.Video.Media.DISPLAY_NAME};
            Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null) {
                int column_index = cursor
                        .getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                cursor.moveToFirst();
                String index = cursor.getString(column_index);
                cursor.close();
                String DownloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();
                String file = DownloadDirectory + "/" + index;
                return file;
            } else
                return null;
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        verticalStepView = findViewById(R.id.steps);
        if(position == 0) {
            source = new LinkedList<>();
            source.add("Выбрать видео");
            source.add("Ввести комнату на всех девайсах видеостены");
            source.add("Сделать фото");
        }

        verticalStepView.reverseDraw(false)
                .setStepsViewIndicatorComplectingPosition(position)
                .setStepViewTexts(source)
                .setLinePaddingProportion(0.85f)
                .setStepsViewIndicatorCompletedLineColor(getResources().getColor(R.color.colorAccent))
                .setStepViewComplectedTextColor(0xff000000)
                .setStepViewUnComplectedTextColor(0xff000000)
                .setStepsViewIndicatorUnCompletedLineColor(getResources().getColor(R.color.colorAccent))
                .setStepsViewIndicatorCompleteIcon(ContextCompat.getDrawable(Main.this, R.drawable.round_check))
                .setStepsViewIndicatorDefaultIcon(ContextCompat.getDrawable(Main.this, R.drawable.round_donut))
                .setStepsViewIndicatorAttentionIcon(ContextCompat.getDrawable(Main.this, R.drawable.round_error))
                .setTextSize(20);

        verticalStepView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (position < source.size() - 1) {
                    verticalStepView
                            .setStepsViewIndicatorComplectingPosition(position)
                            .setStepViewTexts(source)
                            .reverseDraw(false);
                }
                if (position == 0 && !isUploaded) {
                    showFileChooser();
                    isUploaded = true;
                }
                if(position == 1)
                    position++;
                else if (position == 2) {
                    startActivityForResult(new Intent(Main.this, Camera.class), REQUEST_START_CAMERA_ACTIVITY);
                }
            }
        });
        android_id = android.provider.Settings.Secure.getString(this.getContentResolver(),
                Settings.Secure.ANDROID_ID);
    }
}
