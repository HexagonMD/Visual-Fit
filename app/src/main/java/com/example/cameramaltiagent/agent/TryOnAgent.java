package com.example.cameramaltiagent.agent;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.example.cameramaltiagent.api.GeminiApiClient;
import com.example.cameramaltiagent.model.Product;
import com.example.cameramaltiagent.model.TryOnResult;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/**
 * Agent 3: TryOnAgent（Gemini画像生成版）
 *
 * 自撮り写真をbase64でGeminiに送り
 * 「この人物に服を着せた画像を生成」させる。
 * 生成画像はローカルファイルに保存し、パスをTryOnResultに格納する。
 */
public class TryOnAgent {

    private static final String TAG = "TryOnAgent";
    private final GeminiApiClient geminiClient;

    public TryOnAgent() {
        this.geminiClient = new GeminiApiClient();
    }

    /**
     * @param selfieFile     自撮り画像ファイル
     * @param topProduct     上着商品（単品の場合はこれだけ）
     * @param bottomProduct  下着商品（単品の場合はnull）
     * @param garmentDesc    コーデ全体の英語説明
     * @param outputDir      生成画像の保存先ディレクトリ
     */
    public TryOnResult execute(File selfieFile, Product topProduct, Product bottomProduct,
                               String garmentDesc, File outputDir) throws Exception {
        long startTime = System.currentTimeMillis();
        try {
            // 自撮りをリサイズしてバイト列に変換
            Bitmap selfie = loadAndResize(selfieFile, 768);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            selfie.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            byte[] selfieBytes = baos.toByteArray();

            // プロンプト組み立て
            String prompt = buildPrompt(topProduct, bottomProduct, garmentDesc);
            Log.d(TAG, "TryOn prompt: " + prompt);

            // Gemini 画像生成
            byte[] imageBytes = geminiClient.generateTryOnImage(prompt, selfieBytes);
            long duration = System.currentTimeMillis() - startTime;

            if (imageBytes == null) {
                // 画像生成失敗→テキスト描写にフォールバック
                Log.w(TAG, "Gemini画像生成失敗。テキスト描写にフォールバック");
                String desc = geminiClient.generateWithBase64Image(
                        buildTextPrompt(topProduct, bottomProduct, garmentDesc), selfieBytes);
                TryOnResult result = new TryOnResult(
                        topProduct != null ? topProduct.imageUrl : null, desc, duration);
                return result;
            }

            // 生成画像をファイル保存
            File outFile = new File(outputDir, "tryon_result.png");
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(imageBytes);
            }
            Log.d(TAG, "TryOn image saved: " + outFile.getAbsolutePath() + " (" + duration + "ms)");

            TryOnResult result = new TryOnResult(outFile.getAbsolutePath(), null, duration);
            result.isLocalFile = true;
            return result;

        } catch (Exception e) {
            Log.e(TAG, "TryOn failed", e);
            return TryOnResult.failure(e.getMessage());
        }
    }

    private String buildPrompt(Product top, Product bottom, String garmentDesc) {
        StringBuilder sb = new StringBuilder();
        sb.append("あなたはプロのファッションデザイナーです。\n");
        sb.append("この写真の人物に、以下の服を実際に着せた自然なファッション写真を生成してください。\n\n");
        if (top != null) sb.append("【上着】").append(top.name).append("\n");
        if (bottom != null) sb.append("【下着】").append(bottom.name).append("\n");
        sb.append("【コーデ説明】").append(garmentDesc).append("\n\n");
        sb.append("・人物の顔・体型・ポーズはそのままにしてください\n");
        sb.append("・服だけを自然に合成してください\n");
        sb.append("・プロのファッション雑誌のような仕上がりにしてください");
        return sb.toString();
    }

    private String buildTextPrompt(Product top, Product bottom, String garmentDesc) {
        return "あなたはAIファッションアシスタントです。\n"
                + "この画像の人物が次のコーデを着たとき、どのように見えるか詳しく描写してください。\n\n"
                + (top != null ? "上着: " + top.name + "\n" : "")
                + (bottom != null ? "下着: " + bottom.name + "\n" : "")
                + "コーデ説明: " + garmentDesc + "\n\n"
                + "150字程度で、シルエット・カラーバランス・全体の印象を自然な日本語で描写してください。";
    }

    private Bitmap loadAndResize(File file, int maxSide) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        int sampleSize = 1;
        while (opts.outWidth / sampleSize > maxSide || opts.outHeight / sampleSize > maxSide) {
            sampleSize *= 2;
        }
        opts.inSampleSize = sampleSize;
        opts.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
    }
}