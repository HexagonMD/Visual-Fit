package com.example.cameramaltiagent;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cameramaltiagent.ui.CameraActivity;

/**
 * MainActivity — アプリ起動直後にCameraActivityへ遷移するランチャー。
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 即座にCameraActivityに遷移
        startActivity(new Intent(this, CameraActivity.class));
        finish();
    }
}