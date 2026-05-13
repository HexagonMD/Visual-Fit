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
 * Agent 3: TryOnAgent（2ステップ方式）
 *
 * Step1: 自撮り写真を gemini-2.5-flash で体型テキスト分析
 * Step2: テキストのみで gemini-2.5-flash-image に画像生成を依頼
 *        （実物の顔写真を画像生成モデルに渡さないのでポリシー違反を回避）
 */
public class TryOnAgent {

    private static final String TAG = "TryOnAgent";
    private final GeminiApiClient geminiClient;

    public TryOnAgent() {
        this.geminiClient = new GeminiApiClient();
    }

    public TryOnResult execute(File selfieFile, Product topProduct, Product bottomProduct,
                               String garmentDesc, File outputDir) throws Exception {
        long startTime = System.currentTimeMillis();
        try {
            // Step1: 自撮りをリサイズしてバイト列に変換
            Bitmap selfie = loadAndResize(selfieFile, 512);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            selfie.compress(Bitmap.CompressFormat.JPEG, 75, baos);
            byte[] selfieBytes = baos.toByteArray();

            // Step1: 自撮りから体型をテキスト分析（gemini-2.5-flash）
            Log.d(TAG, "Step1: 体型分析開始");
            String bodyAnalysis = geminiClient.generateWithBase64Image(
                    "この人物の外見的特徴を以下の項目で簡潔に答えてください（プライバシーに配慮した一般的な描写のみ）:\n"
                    + "- 性別: \n"
                    + "- 体型: （細め/普通/がっしり）\n"
                    + "- 身長: （低め/普通/高め）\n"
                    + "- 肌トーン: （明るい/中間/暗め）\n"
                    + "各項目を括弧内の選択肢で答えてください。",
                    selfieBytes);
            Log.d(TAG, "Step1 body analysis: " + bodyAnalysis);

            // Step2: テキストのみで画像生成（gemini-2.5-flash-image）
            Log.d(TAG, "Step2: ファッション画像生成開始");
            String imagePrompt = buildImagePrompt(bodyAnalysis, topProduct, bottomProduct, garmentDesc);

            byte[] imageBytes = geminiClient.generateImageFromText(imagePrompt);
            long duration = System.currentTimeMillis() - startTime;

            if (imageBytes == null || imageBytes.length == 0) {
                Log.w(TAG, "画像生成失敗。テキスト描写にフォールバック");
                return new TryOnResult(
                        topProduct != null ? topProduct.imageUrl : null,
                        buildFallbackDescription(bodyAnalysis, topProduct, bottomProduct, garmentDesc),
                        duration);
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

    private String buildImagePrompt(String bodyAnalysis, Product top, Product bottom, String garmentDesc) {
        return "Professional fashion magazine photo of a fashion model. "
                + "Model characteristics: " + bodyAnalysis + ". "
                + "The model is wearing: "
                + (top != null ? top.name + ". " : "")
                + (bottom != null ? bottom.name + ". " : "")
                + "Style: " + garmentDesc + ". "
                + "Clean white studio background. Full body shot. "
                + "High-end fashion photography. Natural lighting. Professional quality.";
    }

    private String buildFallbackDescription(String bodyAnalysis, Product top, Product bottom, String garmentDesc) {
        return "あなたの体型分析: " + bodyAnalysis + "\n\n"
                + "試着コーデ:\n"
                + (top != null ? "・上着: " + top.name + "\n" : "")
                + (bottom != null ? "・下着: " + bottom.name + "\n" : "")
                + "・スタイル: " + garmentDesc;
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