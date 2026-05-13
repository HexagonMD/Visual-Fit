package com.example.cameramaltiagent.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.cameramaltiagent.R;
import com.example.cameramaltiagent.api.GeminiApiClient;
import com.example.cameramaltiagent.model.AgentResult;
import com.example.cameramaltiagent.model.Product;
import com.google.gson.Gson;

import java.io.File;
import java.util.concurrent.Executors;

/**
 * ResultActivity — 試着結果・商品リンク・AI修正チャットを表示。
 */
public class ResultActivity extends AppCompatActivity {

    private AgentResult result;
    private ImageView imgResult;
    private File tryOnImageFile;  // 現在表示中の試着画像ファイル

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        String resultJson = getIntent().getStringExtra(ProcessingActivity.EXTRA_AGENT_RESULT);
        result = new Gson().fromJson(resultJson, AgentResult.class);

        if (result == null || !result.success) {
            Toast.makeText(this, "結果の取得に失敗しました", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        imgResult = findViewById(R.id.img_tryon_result);

        // ── 試着画像を表示 ─────────────────────────────────────────
        if (result.tryOnResult != null && result.tryOnResult.outputImageUrl != null) {
            if (result.tryOnResult.isLocalFile) {
                // Gemini生成のローカルファイル
                tryOnImageFile = new File(result.tryOnResult.outputImageUrl);
                Glide.with(this).load(tryOnImageFile)
                        .placeholder(R.drawable.ic_launcher_background)
                        .into(imgResult);
            } else {
                // 楽天URL（フォールバック）
                Glide.with(this).load(result.tryOnResult.outputImageUrl)
                        .placeholder(R.drawable.ic_launcher_background)
                        .into(imgResult);
            }
        }

        // ── AI試着レポート（テキストフォールバック時） ─────────────
        TextView txtTryOnDesc = findViewById(R.id.txt_tryon_description);
        if (txtTryOnDesc != null && result.tryOnResult != null
                && result.tryOnResult.tryOnDescription != null) {
            txtTryOnDesc.setText("🤖 AI試着レポート\n" + result.tryOnResult.tryOnDescription);
            txtTryOnDesc.setVisibility(View.VISIBLE);
        }

        // ── TOP商品カード ──────────────────────────────────────────
        if (result.selectedProduct != null) {
            setupProductCard(
                    findViewById(R.id.card_top_product),
                    result.selectedProduct,
                    result.bottomProduct != null ? "TOP" : null);
        }

        // ── BOTTOM商品カード（複合コーデ時のみ表示） ──────────────
        LinearLayout cardBottom = findViewById(R.id.card_bottom_product);
        if (result.bottomProduct != null) {
            cardBottom.setVisibility(View.VISIBLE);
            setupProductCard(cardBottom, result.bottomProduct, "BOTTOM");
        } else {
            cardBottom.setVisibility(View.GONE);
        }

        // ── スタイリングコメント ───────────────────────────────────
        TextView txtComment = findViewById(R.id.txt_styling_comment);
        txtComment.setText(result.stylingComment);

        // ── AI修正チャット ─────────────────────────────────────────
        setupRefinementChat();

        // ── やり直しボタン ─────────────────────────────────────────
        findViewById(R.id.btn_retry).setOnClickListener(v -> {
            Intent intent = new Intent(this, CameraActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    /** 商品カードをセットアップ */
    private void setupProductCard(LinearLayout card, Product product, String label) {
        if (card == null || product == null) return;
        card.setVisibility(View.VISIBLE);

        ImageView img = card.findViewById(R.id.img_product);
        TextView txtName  = card.findViewById(R.id.txt_product_name);
        TextView txtPrice = card.findViewById(R.id.txt_product_price);
        TextView txtLabel = card.findViewById(R.id.txt_product_label);

        if (img != null) {
            Glide.with(this).load(product.imageUrl)
                    .placeholder(R.drawable.ic_launcher_background).into(img);
        }
        if (txtName  != null) txtName.setText(product.name);
        if (txtPrice != null) txtPrice.setText(product.price);
        if (txtLabel != null && label != null) {
            txtLabel.setText(label);
            txtLabel.setVisibility(View.VISIBLE);
        }

        card.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(product.purchaseUrl));
            startActivity(i);
        });
    }

    /** AI修正チャット：ユーザーのリクエストで試着画像を更新 */
    private void setupRefinementChat() {
        EditText etRefine = findViewById(R.id.et_refinement);
        View btnRefine    = findViewById(R.id.btn_send_refinement);
        TextView txtStatus = findViewById(R.id.txt_refinement_status);

        if (btnRefine == null || etRefine == null) return;

        btnRefine.setOnClickListener(v -> {
            String request = etRefine.getText().toString().trim();
            if (request.isEmpty()) return;

            // 自撮りファイルを取得
            String selfiePath = getIntent().getStringExtra(CameraActivity.EXTRA_SELFIE_PATH);
            if (selfiePath == null) {
                Toast.makeText(this, "元の写真が見つかりません", Toast.LENGTH_SHORT).show();
                return;
            }

            btnRefine.setEnabled(false);
            etRefine.setEnabled(false);
            if (txtStatus != null) {
                txtStatus.setText("🔄 AIが修正中...");
                txtStatus.setVisibility(View.VISIBLE);
            }

            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    // 自撮りを読み込んでbase64で送信
                    File selfieFile = new File(selfiePath);
                    android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(selfiePath);
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos);
                    byte[] selfieBytes = baos.toByteArray();

                    String prompt = "あなたはプロのファッションデザイナーです。\n"
                            + "この写真の人物に服を着せた試着画像を生成してください。\n"
                            + "元のコーデ: " + result.styleAnalysis.garmentDescForTryOn + "\n"
                            + "修正リクエスト: " + request + "\n"
                            + "・人物の顔・体型はそのままにしてください\n"
                            + "・修正の指示を反映させてください";

                    GeminiApiClient client = new GeminiApiClient();
                    byte[] newImageBytes = client.generateTryOnImage(prompt, selfieBytes);

                    runOnUiThread(() -> {
                        btnRefine.setEnabled(true);
                        etRefine.setEnabled(true);
                        etRefine.setText("");

                        if (newImageBytes != null) {
                            // 新画像をファイルに保存して表示
                            File dir = getCacheDir();
                            File outFile = new File(dir, "tryon_refined.png");
                            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
                                fos.write(newImageBytes);
                                tryOnImageFile = outFile;
                                Glide.with(this).load(outFile).skipMemoryCache(true)
                                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                                        .into(imgResult);
                                if (txtStatus != null) {
                                    txtStatus.setText("✅ 修正完了！");
                                }
                            } catch (Exception e) {
                                if (txtStatus != null) txtStatus.setText("❌ 保存エラー");
                            }
                        } else {
                            if (txtStatus != null) txtStatus.setText("❌ 画像生成できませんでした");
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        btnRefine.setEnabled(true);
                        etRefine.setEnabled(true);
                        if (txtStatus != null) txtStatus.setText("❌ エラー: " + e.getMessage());
                    });
                }
            });
        });
    }
}
