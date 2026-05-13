package com.example.cameramaltiagent.api;

import android.util.Base64;

import com.example.cameramaltiagent.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Gemini APIクライアント。
 * - generateText: テキストのみ（StyleAnalystAgent / StylistAgent）
 * - generateWithBase64Image: 自撮り画像+テキスト（TryOnAgent Step1）
 * - generateWithImageUrl: 画像URL+テキスト（StylistAgent マルチモーダル）
 * - generateImageFromText: テキストから画像生成（TryOnAgent Step2, AI修正）
 */
public class GeminiApiClient {

    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    private static final String IMAGE_GEN_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final String apiKey;

    public GeminiApiClient() {
        this.apiKey = BuildConfig.GEMINI_API_KEY;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * テキストのみのプロンプトを送信。
     * StyleAnalystAgent / StylistAgent（テキスト部分）で使用。
     */
    public String generateText(String prompt) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        JSONObject part = new JSONObject();
        part.put("text", prompt);
        parts.put(part);
        content.put("parts", parts);
        content.put("role", "user");
        contents.put(content);
        body.put("contents", contents);

        return executeRequest(body);
    }

    /**
     * 画像URL + テキストのマルチモーダルプロンプトを送信。
     * StylistAgentで試着結果画像を解析するときに使用。
     */
    public String generateWithImageUrl(String prompt, String imageUrl) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();

        // 画像パート（URL指定）
        JSONObject imagePart = new JSONObject();
        JSONObject fileData = new JSONObject();
        fileData.put("mimeType", "image/jpeg");
        fileData.put("fileUri", imageUrl);
        imagePart.put("fileData", fileData);
        parts.put(imagePart);

        // テキストパート
        JSONObject textPart = new JSONObject();
        textPart.put("text", prompt);
        parts.put(textPart);

        content.put("parts", parts);
        content.put("role", "user");
        contents.put(content);
        body.put("contents", contents);

        return executeRequest(body);
    }

    /**
     * ローカル画像（バイト列）をbase64でインライン送信するプロンプト。
     * TryOnAgentで自撮り画像を解析するときに使用。
     */
    public String generateWithBase64Image(String prompt, byte[] imageBytes)
            throws IOException, JSONException {
        JSONObject body = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();

        // インライン画像パート（base64）
        JSONObject imagePart = new JSONObject();
        JSONObject inlineData = new JSONObject();
        inlineData.put("mimeType", "image/jpeg");
        inlineData.put("data", Base64.encodeToString(imageBytes, Base64.NO_WRAP));
        imagePart.put("inlineData", inlineData);
        parts.put(imagePart);

        // テキストパート
        JSONObject textPart = new JSONObject();
        textPart.put("text", prompt);
        parts.put(textPart);

        content.put("parts", parts);
        content.put("role", "user");
        contents.put(content);
        body.put("contents", contents);

        return executeRequest(body);
    }

    /**
     * テキストのみで画像生成（人物写真なし）。
     * TryOnAgentのStep2・AI修正チャットで使用。
     * モデル: gemini-2.5-flash-image
     *
     * @return PNG画像のバイト列（失敗時はnull）
     */
    public byte[] generateImageFromText(String prompt) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();

        JSONObject textPart = new JSONObject();
        textPart.put("text", prompt);
        parts.put(textPart);

        content.put("parts", parts);
        content.put("role", "user");
        contents.put(content);
        body.put("contents", contents);

        JSONObject genConfig = new JSONObject();
        JSONArray modalities = new JSONArray();
        modalities.put("IMAGE");
        modalities.put("TEXT");
        genConfig.put("responseModalities", modalities);
        body.put("generationConfig", genConfig);

        String url = IMAGE_GEN_URL + "?key=" + apiKey;
        RequestBody reqBody = RequestBody.create(body.toString(), JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(reqBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Gemini ImageGen error: " + response.code() + " " + responseBody);
            }
            JSONObject json = new JSONObject(responseBody);
            JSONArray responseParts = json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts");
            for (int i = 0; i < responseParts.length(); i++) {
                JSONObject part = responseParts.getJSONObject(i);
                if (part.has("inlineData")) {
                    String b64 = part.getJSONObject("inlineData").getString("data");
                    return Base64.decode(b64, Base64.NO_WRAP);
                }
            }
            return null;
        }
    }

    private String executeRequest(JSONObject body) throws IOException, JSONException {
        String url = BASE_URL + "?key=" + apiKey;
        RequestBody reqBody = RequestBody.create(body.toString(), JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(reqBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Gemini API error: " + response.code()
                        + " " + (response.body() != null ? response.body().string() : ""));
            }
            String responseStr = response.body().string();
            JSONObject json = new JSONObject(responseStr);
            return json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");
        }
    }
}

