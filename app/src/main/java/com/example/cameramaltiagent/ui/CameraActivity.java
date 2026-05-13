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
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.cameramaltiagent.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Camera2 API カメラ画面（iPhoneスタイル）
 * ・前後カメラ切り替え（フリップボタン）デフォルトは背面カメラ
 * ・タイマー撮影（OFF / 3秒 / 5秒）
 * ・カウントダウン表示
 * ・ギャラリーから写真選択（一人での撮影が難しい場合）
 */
public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    public static final String EXTRA_SELFIE_PATH = "selfie_path";

    // UI
    private TextureView textureView;
    private TextView txtCountdown;
    private TextView btnTimer;
    private TextView txtTimerIndicator;

    // Camera2
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private String frontCameraId;
    private String backCameraId;
    // ★ デフォルトを背面カメラに変更
    private boolean isFrontCamera = false;

    // Timer
    // CountDownTimer は最後のonTickがスキップされる既知バグがあるためHandler/Runnableで代替
    private int timerSeconds = 0;
    private Handler countDownHandler;
    private Runnable countDownRunnable;
    private boolean isCapturing = false;

    private File selfieFile;

    // ★ ギャラリー選択ランチャー
    private ActivityResultLauncher<Intent> galleryLauncher;

    // ─── Surface Listener ───────────────────────────────────────────────
    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture s, int w, int h) {
            openCamera();
        }
        @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture s, int w, int h) {}
        @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture s) { return true; }
        @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture s) {}
    };

    // ─── Camera State Callback ───────────────────────────────────────────
    private final CameraDevice.StateCallback cameraStateCallback =
            new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            startPreview();
        }
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close(); cameraDevice = null;
        }
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close(); cameraDevice = null;
            Log.e(TAG, "CameraDevice error: " + error);
        }
    };

    // ─── onCreate ───────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        textureView      = findViewById(R.id.texture_view);
        txtCountdown     = findViewById(R.id.txt_countdown);
        btnTimer         = findViewById(R.id.btn_timer);
        txtTimerIndicator = findViewById(R.id.txt_timer_indicator);

        selfieFile = new File(
                getExternalFilesDir(Environment.DIRECTORY_PICTURES), "selfie.jpg");

        // シャッターボタン
        findViewById(R.id.btn_capture).setOnClickListener(v -> onShutterPressed());

        // フリップボタン（前後カメラ切り替え）
        findViewById(R.id.btn_flip).setOnClickListener(v -> flipCamera());

        // タイマーボタン（OFF → 3秒 → 5秒 → OFF）
        btnTimer.setOnClickListener(v -> cycleTimer());

        // ★ ギャラリーボタン
        findViewById(R.id.btn_gallery).setOnClickListener(v -> openGallery());

        // ★ ギャラリー選択結果ハンドラー
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                activityResult -> {
                    if (activityResult.getResultCode() == RESULT_OK
                            && activityResult.getData() != null) {
                        Uri selectedUri = activityResult.getData().getData();
                        if (selectedUri != null) {
                            handleGalleryImage(selectedUri);
                        }
                    }
                });

        findCameraIds();
    }

    // ★ ギャラリーを開く
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        galleryLauncher.launch(Intent.createChooser(intent, "写真を選択"));
    }

    // ★ ギャラリーから選んだ画像を処理
    private void handleGalleryImage(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) {
                Toast.makeText(this, "画像を読み込めませんでした", Toast.LENGTH_SHORT).show();
                return;
            }
            // デコードしてリサイズ（大きすぎる画像対策）
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (bitmap == null) {
                Toast.makeText(this, "画像を読み込めませんでした", Toast.LENGTH_SHORT).show();
                return;
            }
            // 長辺1024px以下にリサイズ
            int maxSide = 1024;
            if (bitmap.getWidth() > maxSide || bitmap.getHeight() > maxSide) {
                float scale = (float) maxSide / Math.max(bitmap.getWidth(), bitmap.getHeight());
                bitmap = Bitmap.createScaledBitmap(
                        bitmap,
                        (int)(bitmap.getWidth() * scale),
                        (int)(bitmap.getHeight() * scale),
                        true);
            }
            try (FileOutputStream fos = new FileOutputStream(selfieFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            }
            Log.d(TAG, "Gallery image saved: " + selfieFile.getAbsolutePath());

            // InputActivityへ遷移
            Intent intent = new Intent(this, InputActivity.class);
            intent.putExtra(EXTRA_SELFIE_PATH, selfieFile.getAbsolutePath());
            startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "handleGalleryImage error", e);
            Toast.makeText(this, "画像の読み込みに失敗しました", Toast.LENGTH_SHORT).show();
        }
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
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        cancelCountdown();
        closeCamera();
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQUEST_CAMERA_PERMISSION && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        } else {
            Toast.makeText(this, "カメラ権限が必要です", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // ─── Camera IDs ─────────────────────────────────────────────────────
    private void findCameraIds() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) frontCameraId = id;
                else if (facing == CameraCharacteristics.LENS_FACING_BACK) backCameraId = id;
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "findCameraIds error", e);
        }
    }

    private void openCamera() {
        String cameraId = isFrontCamera ? frontCameraId : backCameraId;
        if (cameraId == null) {
            Toast.makeText(this, "カメラが見つかりません", Toast.LENGTH_SHORT).show();
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                manager.openCamera(cameraId, cameraStateCallback, null);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "openCamera error", e);
        }
    }

    // ─── Camera Flip ────────────────────────────────────────────────────
    private void flipCamera() {
        if (isCapturing) return;
        closeCamera();
        isFrontCamera = !isFrontCamera;
        ImageView btnFlip = findViewById(R.id.btn_flip);
        btnFlip.animate().rotationBy(360f).setDuration(300).start();
        openCamera();
    }

    // ─── Timer Cycle ────────────────────────────────────────────────────
    private void cycleTimer() {
        if (isCapturing) return;
        if (timerSeconds == 0) {
            timerSeconds = 3;
            btnTimer.setText("⏱ 3秒");
        } else if (timerSeconds == 3) {
            timerSeconds = 5;
            btnTimer.setText("⏱ 5秒");
        } else {
            timerSeconds = 0;
            btnTimer.setText("⏱ OFF");
        }
    }

    // ─── Shutter ────────────────────────────────────────────────────────
    private void onShutterPressed() {
        if (isCapturing) return;
        if (timerSeconds == 0) {
            takePicture();
        } else {
            startCountdown(timerSeconds);
        }
    }

    private void startCountdown(int seconds) {
        isCapturing = true;
        txtCountdown.setVisibility(View.VISIBLE);
        txtTimerIndicator.setVisibility(View.VISIBLE);

        // CountDownTimer は最後のonTickがスキップされるバグがあるため
        // Handler + postDelayed で自前実装（3秒→3,2,1と確実に表示）
        countDownHandler = new Handler(Looper.getMainLooper());
        final int[] remaining = {seconds};

        // 初回即座に表示
        updateCountdownUI(remaining[0]);

        countDownRunnable = new Runnable() {
            @Override
            public void run() {
                remaining[0]--;
                if (remaining[0] > 0) {
                    // まだカウント中
                    updateCountdownUI(remaining[0]);
                    countDownHandler.postDelayed(this, 1000);
                } else {
                    // カウント完了 → 「1」をフェードアウトしてから即撮影
                    txtTimerIndicator.setVisibility(View.INVISIBLE);
                    txtCountdown.animate()
                            .alpha(0f)
                            .setDuration(250)
                            .withEndAction(() -> {
                                txtCountdown.setVisibility(View.GONE);
                                txtCountdown.setAlpha(1f); // 次回用にリセット
                                takePicture();
                            }).start();
                }
            }
        };
        countDownHandler.postDelayed(countDownRunnable, 1000);
    }

    /** カウントダウン数字UIを更新（点滅アニメーション付き） */
    private void updateCountdownUI(int count) {
        txtCountdown.setText(String.valueOf(count));
        txtTimerIndicator.setText(String.valueOf(count));
        txtCountdown.setAlpha(1f);
        txtCountdown.animate().alpha(0.3f).setDuration(800).start();
    }

    private void cancelCountdown() {
        if (countDownHandler != null && countDownRunnable != null) {
            countDownHandler.removeCallbacks(countDownRunnable);
        }
        countDownHandler = null;
        countDownRunnable = null;
        isCapturing = false;
        runOnUiThread(() -> {
            txtCountdown.setVisibility(View.GONE);
            if (txtTimerIndicator != null)
                txtTimerIndicator.setVisibility(View.INVISIBLE);
        });
    }

    // ─── Camera Preview ─────────────────────────────────────────────────
    private void startPreview() {
        if (cameraDevice == null || !textureView.isAvailable()) return;
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(textureView.getWidth(), textureView.getHeight());
            Surface surface = new Surface(texture);

            previewRequestBuilder = cameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            cameraDevice.createCaptureSession(Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                session.setRepeatingRequest(
                                        previewRequestBuilder.build(), null, null);
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

    // ─── Take Picture ────────────────────────────────────────────────────
    private void takePicture() {
        isCapturing = false;
        if (textureView == null) return;
        Bitmap bitmap = textureView.getBitmap();
        if (bitmap == null) return;

        // シャッターフラッシュ効果
        View flash = new View(this);
        flash.setBackgroundColor(0xFFFFFFFF);
        flash.setAlpha(0.8f);
        addContentView(flash, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        flash.animate().alpha(0f).setDuration(200).withEndAction(() ->
                ((android.view.ViewGroup) flash.getParent()).removeView(flash)).start();

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
