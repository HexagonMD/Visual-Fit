package com.example.cameramaltiagent.api;

import android.graphics.Bitmap;
import android.util.Log;

import com.example.cameramaltiagent.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Replicate API クライアント。
 * IDM-VTON モデル（yisol/idm-vton）を使ったバーチャル試着。
 *
 * フロー:
 *   1. uploadHumanImage()  → Replicate Files APIにアップロードしてURLを取得
 *   2. createPrediction()  → 試着予測を開始してpredictionIdを取得
 *   3. pollUntilComplete() → 5秒間隔でポーリングして結果画像URLを取得
 */
public class ReplicateApiClient {

    private static final String TAG = "ReplicateApiClient";
    private static final String BASE_URL = "https://api.replicate.com/v1";
    // IDM-VTON最新安定バージョン
    private static final String MODEL_VERSION =
            "cuuupid/idm-vton:0513734a452173b8173e907e3a59d19a36266e55b48528559432bd21c7d7e985";

    private static final int POLL_INTERVAL_MS = 5000;
    private static final int MAX_POLL_COUNT = 24; // 120秒タイムアウト

    private final OkHttpClient client;
    private final String apiToken;

    public ReplicateApiClient() {
        this.apiToken = BuildConfig.REPLICATE_API_TOKEN;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Step1: 自撮り画像をReplicate Files APIにアップロードしてURLを返す。
     * IDM-VTONはhuman_imgにURLを要求するため必要。
     */
    public String uploadHumanImage(Bitmap bitmap) throws IOException, JSONException {
        // 画像をJPEGバイト列に変換（Nexus 7の容量対策で720px上限・品質80）
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] imageBytes = baos.toByteArray();

        RequestBody fileBody = RequestBody.create(imageBytes,
                MediaType.get("image/jpeg"));
        RequestBody multipart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("content", "selfie.jpg", fileBody)
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/files")
                .header("Authorization", "Token " + apiToken)
                .post(multipart)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Replicate Files upload error: " + response.code());
            }
            JSONObject json = new JSONObject(response.body().string());
            // Replicate Files APIはアップロード後のURLを "urls" -> "get" に格納する
            return json.getJSONObject("urls").getString("get");
        }
    }

    /**
     * Step2: IDM-VTON予測を開始。predictionIdを返す。
     *
     * @param humanImageUrl   Step1で取得した自撮り画像URL
     * @param garmentImageUrl 楽天APIから取得した商品画像URL
     * @param garmentDesc     服の説明（StyleAnalysis.garmentDescForTryOn）
     */
    public String createPrediction(String humanImageUrl, String garmentImageUrl,
                                   String garmentDesc) throws IOException, JSONException {
        JSONObject input = new JSONObject();
        input.put("human_img", humanImageUrl);
        input.put("garm_img", garmentImageUrl);
        input.put("garment_des", garmentDesc);
        input.put("is_checked", true);
        input.put("is_checked_crop", false);
        input.put("denoise_steps", 30);  // 速度重視（デモ時間短縮）
        input.put("seed", 42);

        JSONObject body = new JSONObject();
        body.put("version", MODEL_VERSION);
        body.put("input", input);

        Request request = new Request.Builder()
                .url(BASE_URL + "/predictions")
                .header("Authorization", "Token " + apiToken)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String bodyStr = response.body() != null ? response.body().string() : "null";
            if (!response.isSuccessful()) {
                if (response.code() == 402) {
                    throw new IOException(
                            "Replicateのクレジットが不足しています。\n" +
                            "https://replicate.com/account/billing でクレジットを購入後、" +
                            "数分待ってから再試行してください。");
                }
                throw new IOException("Replicate create prediction error: " + response.code() + " body=" + bodyStr);
            }
            JSONObject json = new JSONObject(bodyStr);
            return json.getString("id");
        }
    }

    /**
     * Step3: 予測完了までポーリング。最大120秒待機。
     * 成功時は試着結果画像URLを返す。失敗時はnullを返す。
     */
    public String pollUntilComplete(String predictionId) throws IOException, JSONException, InterruptedException {
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < MAX_POLL_COUNT; i++) {
            Thread.sleep(POLL_INTERVAL_MS);

            Request request = new Request.Builder()
                    .url(BASE_URL + "/predictions/" + predictionId)
                    .header("Authorization", "Token " + apiToken)
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) continue;

                JSONObject json = new JSONObject(response.body().string());
                String status = json.getString("status");
                Log.d(TAG, "Poll " + (i + 1) + "/" + MAX_POLL_COUNT
                        + " status=" + status
                        + " elapsed=" + (System.currentTimeMillis() - startTime) + "ms");

                switch (status) {
                    case "succeeded":
                        JSONArray output = json.optJSONArray("output");
                        if (output != null && output.length() > 0) {
                            return output.getString(0);
                        }
                        return null;
                    case "failed":
                    case "canceled":
                        Log.e(TAG, "Prediction failed: " + json.optString("error"));
                        return null;
                    // "starting" | "processing" → 次のポーリングへ
                }
            }
        }
        Log.e(TAG, "Polling timeout for predictionId=" + predictionId);
        return null; // タイムアウト
    }
}

