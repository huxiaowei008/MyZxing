package com.modoutech.myzxing;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.modoutech.myzxing.camera.CameraManager;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback{
    public final static int reCode = 8;

    private CameraManager cameraManager;//相机管理类
    private ViewfinderView viewfinderView;//扫描视图
    private BeepManager beepManager;//提示音管理类
    private AmbientLightManager ambientLightManager;//灯光管理类
    private Result savedResultToShow;//结果
    private MainActivityHandler handler;
    private Collection<BarcodeFormat> decodeFormats;
    private Map<DecodeHintType, ?> decodeHints;
    private String characterSet;
    private IntentSource source;
    private String sourceUrl;
    private boolean hasSurface;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkCameraPermission();

        hasSurface = false;
        viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
        beepManager = new BeepManager(this);
        ambientLightManager = new AmbientLightManager(this);



    }

    @Override
    protected void onResume() {
        super.onResume();
        //最好在这里初始,别在onCreate,原因看英文CameraManager must be initialized here, not in onCreate(). This is necessary because we don't
        // want to open the camera driver and measure the screen size if we're going to show the help on
        // first launch. That led to bugs where the scanning rectangle was the wrong size and partially
        // off screen.
        cameraManager = new CameraManager(getApplication());

        viewfinderView.setCameraManager(cameraManager);

        ambientLightManager.start(cameraManager);

        Intent intent = getIntent();

        if (intent != null) {

            String action = intent.getAction();

            if (Intents.Scan.ACTION.equals(action)) {

                // Scan the formats the intent requested, and return the result to the calling activity.
                source = IntentSource.NATIVE_APP_INTENT;
                decodeFormats = DecodeFormatManager.parseDecodeFormats(intent);
                decodeHints = DecodeHintManager.parseDecodeHints(intent);

                if (intent.hasExtra(Intents.Scan.WIDTH) && intent.hasExtra(Intents.Scan.HEIGHT)) {
                    int width = intent.getIntExtra(Intents.Scan.WIDTH, 0);
                    int height = intent.getIntExtra(Intents.Scan.HEIGHT, 0);
                    if (width > 0 && height > 0) {
                        cameraManager.setManualFramingRect(width, height);
                    }
                }

                if (intent.hasExtra(Intents.Scan.CAMERA_ID)) {
                    int cameraId = intent.getIntExtra(Intents.Scan.CAMERA_ID, -1);
                    if (cameraId >= 0) {
                        cameraManager.setManualCameraId(cameraId);
                    }
                }

                String customPromptMessage = intent.getStringExtra(Intents.Scan.PROMPT_MESSAGE);
                if (customPromptMessage != null) {
                    Toast.makeText(this,customPromptMessage,Toast.LENGTH_SHORT).show();
                }

            }
            characterSet = intent.getStringExtra(Intents.Scan.CHARACTER_SET);
        }

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            // The activity was paused but not stopped, so the surface still exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.
            // 活动暂停了，但没有停止，所以表面仍然存在。因此
            // surface创建()不会被调用，所以在这里初始化相机。
            initCamera(surfaceHolder);
        } else {
            // Install the callback and wait for surfaceCreated() to init the camera.
            // 安装回调，并等待创建()初始化相机
            surfaceHolder.addCallback(this);
        }
    }

    @Override
    protected void onPause() {
        ambientLightManager.stop();
        beepManager.close();
        cameraManager.closeDriver();

        if (!hasSurface) {
            SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.removeCallback(this);
        }

        super.onPause();
    }

    ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    CameraManager getCameraManager() {
        return cameraManager;
    }

    public Handler getHandler() {
        return handler;
    }

    public void drawViewfinder() {
        viewfinderView.drawViewfinder();
    }

    public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor) {
        beepManager.playBeepSoundAndVibrate();
        Toast.makeText(this, rawResult.getText(), Toast.LENGTH_SHORT).show();

        restartPreviewAfterDelay(2000);
    }

    /**
     * 在delay毫秒后重启预览
     *
     * @param delayMS
     */
    public void restartPreviewAfterDelay(long delayMS) {
        if (handler != null) {
            handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
        }
    }
    private void initCamera(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        if (cameraManager.isOpen()) {
//            Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?");
            return;
        }
        try {
            cameraManager.openDriver(surfaceHolder);
            // Creating the handler starts the preview, which can also throw a RuntimeException.
            if (handler == null) {
                handler = new MainActivityHandler(this, decodeFormats, decodeHints, characterSet, cameraManager);
            }

        } catch (IOException ioe) {
//            Log.w(TAG, ioe);
            displayFrameworkBugMessageAndExit();
        } catch (RuntimeException e) {
            // Barcode Scanner has seen crashes in the wild of this variety:
            // java.?lang.?RuntimeException: Fail to connect to camera service
//            Log.w(TAG, "Unexpected error initializing camera", e);
            displayFrameworkBugMessageAndExit();
        }
    }

    /**
     * 无法启动相机，提示并退出
     */
    private void displayFrameworkBugMessageAndExit() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.app_name));
        builder.setMessage(getString(R.string.msg_camera_framework_bug));
        builder.setPositiveButton(R.string.button_ok, new FinishListener(this));
        builder.setOnCancelListener(new FinishListener(this));
        builder.show();
    }

    /*  校验相机权限  */
    private void checkCameraPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //校验是否已具有相机权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA},
                        reCode);
            } else {
                //具有权限

            }
        } else {
            //系统不高于6.0直接执行
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == reCode) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //同意权限

            } else {
                // 权限拒绝，提示用户开启权限
                Toast.makeText(this, "权限被拒绝，此功能无法使用", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (holder == null) {
//            Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!");
        }
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
    }

    /**
     * 长按识别二维码功能
     */
//    private void longClick(){
//        Bitmap obmp = ((BitmapDrawable) imgRq.getDrawable()).getBitmap();
//        int width = obmp.getWidth();
//        int height = obmp.getHeight();
//        int[] data = new int[width * height];
//        obmp.getPixels(data, 0, width, 0, 0, width, height);
//        RGBLuminanceSource source = new RGBLuminanceSource(width, height, data);
//        BinaryBitmap bitmap1 = new BinaryBitmap(new HybridBinarizer(source));
//        QRCodeReader reader = new QRCodeReader();
//        Result re = null;
//        try {
//            re = reader.decode(bitmap1);
//        } catch (NotFoundException | ChecksumException | FormatException e) {
//            e.printStackTrace();
//        }
//        if (re != null) {
//            UiUtils.makeText(MassageActivity.this,re.getText());
//        }
//    }
}
