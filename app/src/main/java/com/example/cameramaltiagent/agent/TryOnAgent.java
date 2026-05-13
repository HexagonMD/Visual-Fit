package com.example.cameramaltiagent.agent;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.example.cameramaltiagent.api.GeminiApiClient;
import com.example.cameramaltiagent.model.Product;
import com.example.cameramaltiagent.model.TryOnResult;

import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * Agent 3: TryOnAgent（Gemini版）
 *
 * 自撮り画像をGeminiに送り「この人がこの服を着たらどう見えるか」を
 * テキストで描写させる。商品画像（楽天URL）を表示用として使用。
 *
 * ※ Replicateは起動5分以上かかるため廃止。Geminiなら10〜15秒で完了。
 */
public class TryOnAgent {

    private static final String TAG = "TryOnAgent";
    private final GeminiApiClient geminiClient;

    public TryOnAgent() {
        this.geminiClient = new GeminiApiClient();
    }

    public TryOnResult execute(File selfieFile, Product product, String garmentDesc)
            throws Exception {
        long startTime = System.currentTimeMillis();
        try {
            // 自撮り画像をリサイズしてバイト列に変換
            Bitmap selfie = loadAndResize(selfieFile, 768);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            selfie.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            byte[] selfieBytes = baos.toByteArray();

            // Geminiに試着描写を依頼
            String prompt = buildTryOnPrompt(garmentDesc, product.name);
            String description = geminiClient.generateWithBase64Image(prompt, selfieBytes);

            long duration = System.currentTimeMillis() - startTime;
            Log.d(TAG, "TryOn Gemini done in " + duration + "ms");

            // 商品画像URLを表示用に、Geminiの描写をテキスト結果として返す
            return new TryOnResult(product.imageUrl, description, duration);

        } catch (Exception e) {
            Log.e(TAG, "TryOn failed", e);
            return TryOnResult.failure(e.getMessage());
        }
    }

    private String buildTryOnPrompt(String garmentDesc, String productName) {
        return "あなたはAIファッションアシスタントです。\n"
                + "この画像の人物が、次の商品を試着したとき、どのように見えるか詳しく描写してください。\n\n"
                + "試着する商品: 「" + productName + "」\n"
                + "服の特徴: " + garmentDesc + "\n\n"
                + "以下の観点で150字程度で描写してください:\n"
                + "・この人物の体型・雰囲気にその服がどう合うか\n"
                + "・全体的なシルエットと印象\n"
                + "・カラーバランスと全体の調和\n\n"
                + "まるで実際に試着を目の前で見ているように、自然な日本語で描写してください。";
    }

    private Bitmap loadAndResize(File file, int maxSide) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        int sampleSize = 1;
        while (opts.outWidth / sampleSize > maxSide
                || opts.outHeight / sampleSize > maxSide) {
            sampleSize *= 2;
        }
        opts.inSampleSize = sampleSize;
        opts.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
    }
}