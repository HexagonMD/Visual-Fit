package com.example.cameramaltiagent.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ResultActivity — 試着結果・商品リンク・AI修正チャットを表示。
 */
public class ResultActivity extends AppCompatActivity {

    private AgentResult result;
    private ImageView imgResult;
    private File tryOnImageFile;
    // AIチャット用Executor（Activity破棄時にshutdown）
    private final ExecutorService chatExecutor = Executors.newSingleThreadExecutor();

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
                // ※ キャッシュをスキップしないと前回の古い画像が表示されるので必ず無効化
                tryOnImageFile = new File(result.tryOnResult.outputImageUrl);
                Glide.with(this)
                        .load(tryOnImageFile)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
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
            // https/http のみ許可（javascript: や intent: などの不正スキームを弾く）
            String url = product.purchaseUrl;
            if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(i);
            } else {
                Toast.makeText(this, "無効なURLです", Toast.LENGTH_SHORT).show();
            }
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

            btnRefine.setEnabled(false);
            etRefine.setEnabled(false);
            if (txtStatus != null) {
                txtStatus.setText("🔄 AIが修正中...");
                txtStatus.setVisibility(View.VISIBLE);
            }

            String currentCoord = (result.styleAnalysis != null
                    && result.styleAnalysis.garmentDescForTryOn != null)
                    ? result.styleAnalysis.garmentDescForTryOn
                    : "fashion outfit";

            // 共有Executorを使用してリソースリークを防止
            chatExecutor.execute(() -> {
                try {
                    String imagePrompt = "Professional fashion magazine photo of a fashion model. "
                            + "Base outfit: " + currentCoord + ". "
                            + "Modification request: " + request + ". "
                            + "Clean white studio background. Full body shot. "
                            + "High-end fashion photography. Natural lighting.";

                    GeminiApiClient client = new GeminiApiClient();
                    byte[] newImageBytes = client.generateImageFromText(imagePrompt);

                    runOnUiThread(() -> {
                        btnRefine.setEnabled(true);
                        etRefine.setEnabled(true);
                        etRefine.setText("");

                        if (newImageBytes != null && newImageBytes.length > 0) {
                            File dir = getCacheDir();
                            File outFile = new File(dir, "tryon_refined_" + System.currentTimeMillis() + ".png");
                            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
                                fos.write(newImageBytes);
                                tryOnImageFile = outFile;
                                Glide.with(ResultActivity.this)
                                        .load(outFile)
                                        .skipMemoryCache(true)
                                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                                        .into(imgResult);
                                if (txtStatus != null) txtStatus.setText("✅ 修正完了！");
                            } catch (Exception e) {
                                if (txtStatus != null) txtStatus.setText("❌ 保存エラー: " + e.getMessage());
                            }
                        } else {
                            if (txtStatus != null) txtStatus.setText("❌ 画像を生成できませんでした。再度お試しください。");
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        chatExecutor.shutdownNow();
    }
}
