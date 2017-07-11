package com.modoutech.myzxing;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.google.zxing.Result;
import com.modoutech.myzxing.camera.CameraManager;

public class MainActivity extends AppCompatActivity {
    public final static int reCode = 8;

    private CameraManager cameraManager;//相机管理类
    private ViewfinderView viewfinderView;//扫描视图
    private BeepManager beepManager;//提示音管理类
    private AmbientLightManager ambientLightManager;//灯光管理类
    private Result savedResultToShow;//结果
    private CaptureActivityHandler handler;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkCameraPermission();

        viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
        beepManager = new BeepManager(this);
        ambientLightManager = new AmbientLightManager(this);

        ambientLightManager.start(cameraManager);


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

    }

    @Override
    protected void onPause() {
        ambientLightManager.stop();
        beepManager.close();
        cameraManager.closeDriver();
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
        Toast.makeText(this, rawResult.getText(), Toast.LENGTH_SHORT).show();
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
