package com.example.cameramaltiagent.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.cameramaltiagent.R;

import java.io.File;

/**
 * InputActivity — 自撮りサムネイル表示 + 服装テキスト入力 + パイプライン起動。
 */
public class InputActivity extends AppCompatActivity {

    private String selfiePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_input);

        selfiePath = getIntent().getStringExtra(CameraActivity.EXTRA_SELFIE_PATH);

        // 自撮りサムネイル表示
        ImageView thumbView = findViewById(R.id.img_selfie_thumb);
        if (selfiePath != null) {
            Glide.with(this).load(new File(selfiePath)).into(thumbView);
        }

        EditText editClothing = findViewById(R.id.edit_clothing);

        // ヒントチップのタップでEditTextに文章を挿入
        int[] chipIds = {R.id.chip1, R.id.chip2, R.id.chip3, R.id.chip4};
        String[] chipTexts = {"白いオーバーサイズTシャツ", "黒スキニーパンツ", "ベージュのロングコート", "デニムジャケットとチノパン"};
        for (int i = 0; i < chipIds.length; i++) {
            final String text = chipTexts[i];
            android.widget.TextView chip = findViewById(chipIds[i]);
            if (chip != null) chip.setOnClickListener(v -> editClothing.setText(text));
        }

        // 開始ボタン
        findViewById(R.id.btn_start).setOnClickListener(v -> {
            String clothingText = editClothing.getText().toString().trim();
            if (clothingText.isEmpty()) {
                Toast.makeText(this, "服の説明を入力してください", Toast.LENGTH_SHORT).show();
                return;
            }
            // ProcessingActivityへ遷移してパイプライン開始
            Intent intent = new Intent(this, ProcessingActivity.class);
            intent.putExtra(CameraActivity.EXTRA_SELFIE_PATH, selfiePath);
            intent.putExtra(ProcessingActivity.EXTRA_CLOTHING_TEXT, clothingText);
            startActivity(intent);
        });

        // 再撮影ボタン
        findViewById(R.id.btn_retake).setOnClickListener(v -> finish());
    }
}

