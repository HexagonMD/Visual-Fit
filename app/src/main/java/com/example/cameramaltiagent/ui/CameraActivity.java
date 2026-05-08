package com.example.cameramaltiagent.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.cameramaltiagent.R;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;

/**
 * Camera2 APIを使ったフロントカメラ自撮り画面。
 * 撮影後は InputActivity へ画像パスを渡して遷移する。
 */
public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    public static final String EXTRA_SELFIE_PATH = "selfie_path";

    private TextureView textureView;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private String frontCameraId;
    private File selfieFile;

    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int w, int h) {
            openFrontCamera();
        }
        @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture s, int w, int h) {}
        @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture s) { return true; }
        @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture s) {}
    };

    private final CameraDevice.StateCallback cameraStateCallback =
            new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            startPreview();
        }
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
            Log.e(TAG, "CameraDevice error: " + error);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        textureView = findViewById(R.id.texture_view);
        selfieFile = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "selfie.jpg");

        findViewById(R.id.btn_capture).setOnClickListener(v -> takePicture());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        if (textureView.isAvailable()) {
            openFrontCamera();
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeCamera();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        } else {
            Toast.makeText(this, "カメラ権限が必要です", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void openFrontCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(cameraId);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontCameraId = cameraId;
                    break;
                }
            }
            if (frontCameraId == null) {
                Toast.makeText(this, "フロントカメラが見つかりません", Toast.LENGTH_SHORT).show();
                return;
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                manager.openCamera(frontCameraId, cameraStateCallback, null);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "openFrontCamera error", e);
        }
    }

    private void startPreview() {
        if (cameraDevice == null || !textureView.isAvailable()) return;
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(textureView.getWidth(), textureView.getHeight());
            Surface previewSurface = new Surface(texture);

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(previewSurface);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            cameraDevice.createCaptureSession(Arrays.asList(previewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                session.setRepeatingRequest(previewRequestBuilder.build(), null, null);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "setRepeatingRequest error", e);
                            }
                        }
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "createCaptureSession failed");
                        }
                    }, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "startPreview error", e);
        }
    }

    private void takePicture() {
        if (textureView == null) return;
        Bitmap bitmap = textureView.getBitmap();
        if (bitmap == null) return;

        try (FileOutputStream fos = new FileOutputStream(selfieFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
        } catch (Exception e) {
            Log.e(TAG, "takePicture error", e);
            Toast.makeText(this, "撮影に失敗しました", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, InputActivity.class);
        intent.putExtra(EXTRA_SELFIE_PATH, selfieFile.getAbsolutePath());
        startActivity(intent);
    }

    private void closeCamera() {
        if (captureSession != null) { captureSession.close(); captureSession = null; }
        if (cameraDevice != null) { cameraDevice.close(); cameraDevice = null; }
    }
}

