package com.example.ducks.screen;


import android.Manifest;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.Bitmap.Config;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaMetadataRetriever;
import android.os.*;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.*;
import android.view.*;
import android.widget.Toast;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.*;
import java.util.*;

import static com.example.ducks.screen.Main.REQUEST_START_CAMERA_ACTIVITY;
import static com.example.ducks.screen.Main.android_id;
import static com.example.ducks.screen.Search.*;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class Camera extends AppCompatActivity {
    private CameraManager cameraManager;
    private int cameraFacing;
    private TextureView.SurfaceTextureListener surfaceTextureListener;
    private String cameraId;
    private final int CAMERA_REQUEST_CODE = 1;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private CameraDevice cameraDevice;
    private CameraDevice.StateCallback stateCallback;
    private TextureView textureView;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private CaptureRequest captureRequest;
    private Bitmap bitmap, bitmap2;
    private Size previewSize;
    private long t;
    private OrientationListener orientationListener;
    private int rotate;
    private FloatingActionButton floatingActionButton;
    public static int _R = 3;
    public static final int UNKNOWN = 0xFFFF00FF;
    private double videoHeight, videoWidth;

    //для полноэкранного режима
    //For the fullscreen mode
    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    //рассчитывает оптимальный размеры для TextureView камеры
    //calculates optimal sizes of TextureView
    private Size chooseOptimalSize(Size[] outputSizes, int width, int height) {
        double preferredRatio = (double) height / (double) width;
        Size currentOptimalSize = outputSizes[0];
        double currentOptimalRatio = currentOptimalSize.getWidth() / (double) currentOptimalSize.getHeight();
        for (Size currentSize : outputSizes) {
            double currentRatio = currentSize.getWidth() / (double) currentSize.getHeight();
            if (Math.abs(preferredRatio - currentRatio) <
                    Math.abs(preferredRatio - currentOptimalRatio)) {
                currentOptimalSize = currentSize;
                currentOptimalRatio = currentRatio;
            }
        }
        return currentOptimalSize;
    }

    //получение оптимального размера TextureView камеры
    //getting the optimal size of TextureView
    private void setUpCamera() {
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics =
                        cameraManager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                        cameraFacing) {
                    StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(
                            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    Display display = getWindowManager().getDefaultDisplay();
                    Point size = new Point();
                    display.getSize(size);
                    previewSize = chooseOptimalSize(streamConfigurationMap.getOutputSizes(SurfaceTexture.class), size.x, size.y);
                    this.cameraId = cameraId;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //открытие самой камеры
    //to open camera
    private void openCamera() {
        try {
            //получение разрешения на открытие
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //возобновляет работу камеры
    //resumes camera work
    private void openBackgroundThread() {
        backgroundThread = new HandlerThread("camera_background_thread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    //передает изображение с камеры (превью) на TextureView
    //camera image (preview) to TextureView
    private void createPreviewSession() {
        try {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON);

            fixDarkPreview();
            //на старых телефонах превью очень темное
            //to fix dark camera preview on old phones

            cameraDevice.createCaptureSession(Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            if (cameraDevice == null) {
                                return;
                            }

                            try {
                                captureRequest = captureRequestBuilder.build();
                                Camera.this.cameraCaptureSession = cameraCaptureSession;
                                Camera.this.cameraCaptureSession.setRepeatingRequest(captureRequest,
                                        null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {

                        }
                    }, backgroundHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        hideSystemUI();
        orientationListener = new OrientationListener(Camera.this);
        //для возможности изменения ориентации фотографии без изменения ориентации активности
        //to change photo orientation without changing orientation of the activity
        floatingActionButton = findViewById(R.id.floatingActionButton);
        if (Main.room < 0) {
            Toast.makeText(Camera.this, "Произошла ошибка! Перезапустите приложение.", Toast.LENGTH_LONG).show();
            //на случай прерывания передачи видео на сервер
            //извините за хардкод
            //in case of interruption of video transmission to the server
            //sorry for hardcode
            finish();
        }
        textureView = findViewById(R.id.texture_view);
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        cameraFacing = CameraCharacteristics.LENS_FACING_BACK;

        stateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice cameraDevice) {
                Camera.this.cameraDevice = cameraDevice;
                createPreviewSession();
            }

            @Override
            public void onDisconnected(CameraDevice cameraDevice) {
                cameraDevice.close();
                Camera.this.cameraDevice = null;
            }

            @Override
            public void onError(CameraDevice cameraDevice, int error) {
                cameraDevice.close();
                Camera.this.cameraDevice = null;
            }
        };

        surfaceTextureListener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                setUpCamera();
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

            }
        };

        ActivityCompat.requestPermissions(Camera.this, new String[]{Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE}, CAMERA_REQUEST_CODE);
        //запрос разрешений на запись файлов и доступ к камере
        //request permission to write files and access the camera

        calculateVideoSize();
    }

    private void calculateVideoSize() {
        try {
            FileDescriptor fd = new FileInputStream(ExtractMpegFramesTest.FILES_DIR).getFD();
            MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
            metaRetriever.setDataSource(fd);
            String height = metaRetriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            String width = metaRetriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            videoHeight = Float.parseFloat(height) / 8;
            videoWidth = Float.parseFloat(width) / 8;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStart() {
        orientationListener.enable();
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        openBackgroundThread();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        //блокировка смены ориентации активности
        //lock orientation change of the activity

        if (textureView.isAvailable()) {
            setUpCamera();
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        //отключение блокировки ориентации
        //unlock orientation changing
        closeCamera();
        //освобождение камеры (может работать только в одном приложении одновременно)
        //release the camera (can work only in one application at a time)
        closeBackgroundThread();
        orientationListener.disable();
    }

    //закрытие камеры
    //to close camera
    private void closeCamera() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }

        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    //закрытие потока камеры
    //to close camera thread
    private void closeBackgroundThread() {
        if (backgroundHandler != null) {
            backgroundThread.quitSafely();
            backgroundThread = null;
            backgroundHandler = null;
        }
    }

    //функция, вызываемая при нажатии кнопки фотографирования
    //on camera button clicked
    public void fab(View view) {
        PhotoThread newThread = new PhotoThread();
        newThread.start();
    }

    //делает превью камереры ярче за счет ограничения fps (на старых телефонах превью темное)
    //makes camera previews brighter due to fps limitation (on old phones the preview is dark)
    private void fixDarkPreview() throws CameraAccessException {
        Range<Integer>[] autoExposureFPSRanges = cameraManager
                .getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);

        if (autoExposureFPSRanges != null) {
            for (Range<Integer> autoExposureRange : autoExposureFPSRanges) {
                if (autoExposureRange.equals(Range.create(15, 30))) {
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                            Range.create(15, 30));
                }
            }
        }
    }

    //поток создания фотографий
    //photo processing thread
    class PhotoThread extends Thread {

        @Override
        public void run() {
            SendThread sendThread = new SendThread();
            t = System.currentTimeMillis() + (int) Sync.deltaT + 3000;
            sendThread.start();
            //отправка времени начала фотографирования на сервер
            //sending time of making photos to the server
            java.util.Timer timer = new java.util.Timer();

            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    bitmap = textureView.getBitmap();
                }
            }, t - (System.currentTimeMillis() + (int) Sync.deltaT));
            //первое фото
            //first photo

            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    bitmap2 = textureView.getBitmap();
                }
            }, t - (System.currentTimeMillis() + (int) Sync.deltaT) + 10);
            /*  Данное фото формально не сохраняется!
                Оно делается чтобы "обмануть андроид".
                Дело в том, что время от времени он ленится делать две фотографии с таким маленьким промежутком.
                И вместо второго фото дает первое же.
                Крайне суровый способ оптимизации.
                Третье же фото - настоящее. */

            /*  This photo is not formally saved!
                It is made to "deceive android."
                The fact is that from time to time he is lazy to take two photos with such a small interval.
                And instead of the second photo gives the first one.
                Extremely harsh way to optimize.
                The third photo is the real new photo. */

            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    bitmap2 = textureView.getBitmap();
                    new CordThread().start();
                }
            }, t - (System.currentTimeMillis() + (int) Sync.deltaT) + 60);
            //настоящее второе фото
            //real second photo

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    floatingActionButton.hide();
                }
            });

        }
    }

    class SendThread extends Thread {

        @Override
        public void run() {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(URL)
                    .client(getUnsafeOkHttpClient().build())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            Service service = retrofit.create(Service.class);

            Call<Integer> call = service.putDevice(android_id, Main.room, t);
            try {
                call.execute();
                //отправка времени фотографирования на сервер
                //sending time of making photos to the server
                Log.d("SEND_AND_RETURN", "" + (t - (System.currentTimeMillis() + (int) Sync.deltaT)));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //сохранение фотографий в памяти телефона
    //!!! данная функция используется только для тестов!!!
    //store photos in the phone’s memory
    //!!! this function is used only for tests !!!
    void bitmapUpload(Bitmap b, int i) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byte[] byteArray = stream.toByteArray();
            File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(storageDirectory, "Screen" + i + ".png");
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(byteArray);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //скачивание фотографий из памяти телефона
    //!!!данная функция используется только для тестов!!!
    //download photos from phone’s memory
    //!!! This function is used only for tests !!!
    Bitmap bitmapDownload(int i) {
        File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(storageDirectory, "Screen" + i + ".png");
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return BitmapFactory.decodeStream(fileInputStream);
    }


    class CordThread extends Thread {

        @Override
        public void run() {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotate);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap2 = Bitmap.createBitmap(bitmap2, 0, 0, bitmap2.getWidth(), bitmap2.getHeight(), matrix, true);
            //переворот фотографии в соответствии с ориентацией телефона
            //flip the photo according to the orientation of the phone

            /*bitmap = bitmapDownload(1);
            bitmap2 = bitmapDownload(2);
            !!! This code is used only for testing !!! */

            bitmap = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() / 8, bitmap.getHeight() / 8, false);
            bitmap2 = Bitmap.createScaledBitmap(bitmap2, bitmap2.getWidth() / 8, bitmap2.getHeight() / 8, false);
            //пропорциональное уменьшение размеров фотографий для более быстрой их обработки
            //proportional resizing of photos for faster denoise processing (morphological opening)

            bitmapUpload(bitmap, 1);
            bitmapUpload(bitmap2, 2);

            Bitmap bitmap3 = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            bitmap3 = bitmap3.copy(Bitmap.Config.ARGB_8888, true);
            //битмап для записи результатов обработки фотографий
            //bitmap for photo processing results

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(URL)
                    .client(getUnsafeOkHttpClient().build())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            Service service = retrofit.create(Service.class);

            Call<int[]> colorCall = service.getColors();
            int[] colors = new int[0];
            try {
                Response<int[]> response = colorCall.execute();
                colors = response.body();
                //получение массива возможных цветов
                //getting an array of possible colors
            } catch (IOException e) {
                e.printStackTrace();
            }

            Call<int[]> indexCall = service.getIndexes(Main.room);
            int[] indexes = new int[2];
            try {
                Response<int[]> response = indexCall.execute();
                indexes = response.body();
                //получение последней комбинации цветов в этой комнате
                //комбинации идут последовательно, поэтому из последней комбинации можно получить и все остальные
                // get the last color combination in this room
                // combinations go successively, therefore all the rest can be obtained from the last combination
            } catch (IOException e) {
                e.printStackTrace();
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Camera.this, "Подождите немного", Toast.LENGTH_LONG).show();
                }
            });

            TreeMap<Integer, TreeMap<Integer, LinkedList<Point>>> points = new TreeMap<>();
            //структура, в которую добавляются все точки каждой комбинации цветов
            //для дальнейшего определения координат экранов
            // structure to which all points of each color combination are added
            // to determine the coordinates of the phones
            for (int k = 0; k <= indexes[0]; k++) {
                points.put(k, new TreeMap<>());
                if (k + 1 > indexes[0]) {
                    for (int l = 0; l <= indexes[1]; l++) {
                        if (k != l) {
                            points.get(k).put(l, new LinkedList<>());
                        }
                    }
                } else {
                    for (int l = 0; l < colors.length; l++) {
                        if (k != l) {
                            points.get(k).put(l, new LinkedList<>());
                        }
                    }
                }
            }
            //заполнение этой структуры
            // fill this structure

            for (int i = 0; i < bitmap.getHeight(); i++) {
                for (int j = 0; j < bitmap.getWidth(); j++) {
                    bitmap3.setPixel(j, i, UNKNOWN);
                    //заполнение фоновым цветом
                    //fill with background color

                    Integer first = bitmap.getPixel(j, i);
                    Integer second = bitmap2.getPixel(j, i);

                    //проверка на то, что цвет изменился на второй картинке
                    // check that the color changes on the second image
                    if (comparePixel(first, second)) {

                        first = testColor(Color.red(first), Color.green(first), Color.blue(first));
                        second = testColor(Color.red(second), Color.green(second), Color.blue(second));
                        //определение цвета, который ближе всего к данному
                        // define the color that is closest to this

                        int ind1 = 0;
                        for (int k = 0; k < colors.length; k++) {
                            if (colors[k] == first) {
                                ind1 = k;
                                break;
                            }
                        }
                        int ind2 = 0;
                        for (int k = 0; k < colors.length; k++) {
                            if (colors[k] == second) {
                                ind2 = k;
                                break;
                            }
                        }
                        //получение индексов такой комбинации цветов
                        // getting indices of such colors combination

                        if (ind1 == ind2 || ind1 >= points.size() || points.get(ind1).size() < ind2)
                            continue;
                        //проверка на существование такой комбинации в этой комнате
                        // check for such combination in this room

                        points.get(ind1).get(ind2).add(new Point(j, i));
                        bitmap3.setPixel(j, i, second);
                    }
                }
            }

            if (points.get(0).get(1).size() == 0) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(Camera.this, "Что-то пошло не так! \nВы точно сфотографировали видеостену?", Toast.LENGTH_LONG).show();
                        setResult(-1);
                        finish();
                    }
                });
            }
            //в случае неудачного распознавания
            //in case of unsuccessful recognition

            denoise(bitmap3);
            //устранение шумов
            //remove noise

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Camera.this, "Почти готово!", Toast.LENGTH_LONG).show();
                }
            });

            Comparator<Point> xComparator = new Comparator<Point>() {
                @Override
                public int compare(Point o1, Point o2) {
                    return o1.x - o2.x;
                }
            };
            Comparator<Point> yComparator = new Comparator<Point>() {
                @Override
                public int compare(Point o1, Point o2) {
                    return o1.y - o2.y;
                }
            };

            double left = 0, right = 0, up = 0, down = 0;

            for (int i = 0; i < points.size(); i++) {
                for (int j = 0; j <= points.get(i).size(); j++) {
                    if (j == i)
                        continue;

                    LinkedList<Point> linkedList = points.get(i).get(j);
                    for (int k = 0; k < linkedList.size(); k++) {
                        Point p = linkedList.get(k);
                        if (bitmap3.getPixel(p.x, p.y) != colors[j]) {
                            linkedList.remove(k);
                            k--;
                        }
                    }
                    //удаление точек, оказавшихся шумом
                    //remove noise points

                    if (linkedList.size() > 0) {
                        Collections.sort(linkedList, xComparator);
                        left = linkedList.getFirst().x;
                        right = linkedList.getLast().x;
                        Collections.sort(linkedList, yComparator);
                        up = linkedList.getFirst().y;
                        down = linkedList.getLast().y;

                        double scaleH = (double) bitmap.getHeight() / Main.videoH;
                        double scaleW = (double) bitmap.getWidth() / Main.videoW;
                        double scale = scaleH < scaleW ? scaleH : scaleW;


                        left /= scale;
                        up /= scale;
                        right /= scale;
                        down /= scale;
                        double h = bitmap.getHeight() / scale;
                        double w = bitmap.getWidth() / scale;

                        left = left - w/ 2 + Main.videoW / 2;
                        up = up - h / 2 + Main.videoH / 2;
                        right = right - w / 2 + Main.videoW / 2;
                        down = down - h / 2 + Main.videoH / 2;


                        Log.d("Coords", left + ";" + up + " " + right + ";" + down);

                        int[] ind = {i, j};

                        Call<Void> call = service.putCoords(Main.room, left, up, right, down, ind);
                        try {
                            call.execute();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            setResult(REQUEST_START_CAMERA_ACTIVITY);
            finish();
            //удачное завершение активности
            // successful completion of the activity
        }

    }

    private Bitmap overlay(Bitmap bmp1, Bitmap bmp2) {
        Bitmap bmOverlay = Bitmap.createBitmap(bmp1.getWidth(), bmp1.getHeight(), bmp1.getConfig());
        Canvas canvas = new Canvas(bmOverlay);
        canvas.drawBitmap(bmp1, new Matrix(), null);
        canvas.drawBitmap(bmp2, (float) ((bmp1.getWidth() / 2) - (bmp2.getWidth() / 2)), (float) ((bmp1.getHeight() / 2) - (bmp2.getHeight() / 2)), null);
        return bmOverlay;
    }

    //реализация метода размыкания(Opening)
    //https://habr.com/ru/company/yandex/blog/254955/ - ресурс, который помог с этим разобраться
    // implementation of the opening method (Opening)
    //https://habr.com/ru/company/yandex/blog/254955/ - a resource that helped with this (on russian language)
    public void denoise(Bitmap arr) {
        erosion(arr);
        dilating(arr);
    }

    //реализация метода эрозии(сужения) (один из основных методов мат. морфологии)
    // implementation of the method of erosion(contraction) (one of the main methods of mathematical morphology)
    public void erosion(Bitmap arr) {
        Bitmap arr_ = arr.createBitmap(arr.getWidth(), arr.getHeight(), Bitmap.Config.ARGB_8888);
        arr_ = arr_.copy(Bitmap.Config.ARGB_8888, true);
        for (int i = 0; i < arr.getHeight() - _R; i++) {
            for (int j = 0; j < arr.getWidth() - _R; j++) {
                if (arr.getPixel(j, i) == UNKNOWN) {
                    int _i = i, _j = j;
                    if (i < _R)
                        _i = _R;
                    if (j < _R)
                        _j = _R;
                    for (int k = _i - _R; k <= i + _R; k++) {
                        for (int l = _j - _R; l <= j + _R; l++) {
                            arr_.setPixel(l, k, UNKNOWN);
                        }
                    }
                }
            }
        }
        for (int i = 0; i < arr.getHeight(); i++)
            for (int j = 0; j < arr.getWidth(); j++)
                if (arr_.getPixel(j, i) == UNKNOWN)
                    arr.setPixel(j, i, UNKNOWN);
        return;
    }

    //реализация метода расширения (один из основных методов мат. морфологии)
    // implementation of the extension method (one of the main methods of mathematical morphology)
    public void dilating(Bitmap arr) {
        Bitmap arr_ = arr.createBitmap(arr.getWidth(), arr.getHeight(), Bitmap.Config.ARGB_8888);
        arr_ = arr_.copy(Bitmap.Config.ARGB_8888, true);
        for (int i = _R; i < arr.getHeight() - _R; i++) {
            for (int j = _R; j < arr.getWidth() - _R; j++) {
                if (arr.getPixel(j, i) != UNKNOWN)
                    for (int k = i - _R; k <= i + _R; k++) {
                        for (int l = j - _R; l <= j + _R; l++) {
                            if (arr.getPixel(l, k) == UNKNOWN)
                                arr_.setPixel(l, k, arr.getPixel(j, i));
                        }
                    }
            }
        }
        for (int i = 0; i < arr.getHeight(); i++)
            for (int j = 0; j < arr.getWidth(); j++)
                if (arr_.getPixel(j, i) != 0)
                    arr.setPixel(j, i, arr_.getPixel(j, i));
        return;
    }


    //проверка на наличие кардинального изменения цвета
    //check for a dramatic change in color
    public boolean comparePixel(int pix1, int pix2) {
        int dR = Math.abs(Color.red(pix1) - Color.red(pix2));
        int dG = Math.abs(Color.green(pix1) - Color.green(pix2));
        int dB = Math.abs(Color.blue(pix1) - Color.blue(pix2));
        return dR > 50 || dG > 50 || dB > 50;
    }

    public Integer testColor(int R, int G, int B) {
        int percentR = (int) ((double) R / ((double) (R + G + B) / (double) 100));
        int percentG = (int) ((double) G / ((double) (R + G + B) / (double) 100));
        int percentB = (int) ((double) B / ((double) (R + G + B) / (double) 100));
        //процентное соотношениие цветов
        //percentage of colors

        double deviation = 0.2;
        //% отклонения
        //% deviation

        if (R < 30 && G < 30 && B < 30) return Color.BLACK;
        if (R > 60 && G > 60 && B > 60) return Color.WHITE;
        if (percentR > 40 && R > G + G * deviation && R > B + B * deviation) return Color.RED;
        if (percentG > 40 && G > R + R * deviation && G > B + B * deviation) return Color.GREEN;
        if (percentB > 40 && B > G + G * deviation && B > R + R * deviation) return Color.BLUE;
        // Цвет неизвестен
        // unknown color
        return UNKNOWN;
    }

    /* При изменении ориентации активности на горизонтальную, превью серьезно искривляется.
     *  Поэтому определяется сам градус наклона, чтобы при повороте перевернуть кнопку и само изображение на выходе. */
    /* When you change the orientation of the activity on the horizontal, the preview seriously bends
     * Therefore, the degree of inclination itself is determined in order to turn the button and the image itself at the output while turning. */
    private class OrientationListener extends OrientationEventListener {
        final int ROTATION_O = 1;
        final int ROTATION_90 = 2;
        final int ROTATION_270 = 4;

        private int rotation = 0;

        public OrientationListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if (((orientation < 35 && orientation > 0) || orientation > 325) && rotation != ROTATION_O) {
                //Портретная ориентация
                //Portrait orientation
                rotation = ROTATION_O;
                rotate = 0;
            } else if (orientation > 55 && orientation < 125 && rotation != ROTATION_270) {
                //Реверсивная горизонтальная
                //Reverse horizontal
                rotation = ROTATION_270;
                rotate = 90;
            } else if (orientation > 235 && orientation < 305 && rotation != ROTATION_90) {
                //Горизонтальная
                //Horizontal
                rotation = ROTATION_90;
                rotate = -90;
            }
            floatingActionButton.setRotation(-rotate);
            //Переворот кнопки
            //Button rotation
        }
    }
}