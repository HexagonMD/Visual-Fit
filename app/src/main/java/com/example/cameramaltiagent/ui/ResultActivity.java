package com.example.cameramaltiagent.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.cameramaltiagent.R;
import com.example.cameramaltiagent.model.AgentResult;
import com.google.gson.Gson;

/**
 * ResultActivity — バーチャル試着結果・スタイリストコメント・購入リンクを表示。
 */
public class ResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        // ProcessingActivityからAgentResultをJSON経由で受け取る
        String resultJson = getIntent().getStringExtra(ProcessingActivity.EXTRA_AGENT_RESULT);
        AgentResult result = new Gson().fromJson(resultJson, AgentResult.class);

        if (result == null || !result.success) {
            Toast.makeText(this, "結果の取得に失敗しました", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 試着画像を表示（Glideで非同期ロード）
        ImageView imgResult = findViewById(R.id.img_tryon_result);
        Glide.with(this)
                .load(result.tryOnResult.outputImageUrl)
                .placeholder(R.drawable.ic_launcher_background)
                .into(imgResult);

        // スタイリストコメント
        TextView txtComment = findViewById(R.id.txt_styling_comment);
        txtComment.setText(result.stylingComment);

        // 商品情報
        if (result.selectedProduct != null) {
            TextView txtName = findViewById(R.id.txt_product_name);
            TextView txtPrice = findViewById(R.id.txt_product_price);
            txtName.setText(result.selectedProduct.name);
            txtPrice.setText(result.selectedProduct.price);

            // 購入ボタン → 楽天購入ページをブラウザで開く
            findViewById(R.id.btn_purchase).setOnClickListener(v -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(result.selectedProduct.purchaseUrl));
                startActivity(browserIntent);
            });
        }

        // やり直しボタン → CameraActivityに戻る
        findViewById(R.id.btn_retry).setOnClickListener(v -> {
            Intent intent = new Intent(this, CameraActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }
}

