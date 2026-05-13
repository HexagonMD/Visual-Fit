package com.example.cameramaltiagent.api;

import com.example.cameramaltiagent.BuildConfig;
import com.example.cameramaltiagent.model.Product;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 楽天市場 Ichiba Item Search API クライアント。
 * 無料枠: 5000回/日。日本語クエリで国内ファッション商品を検索。
 *
 * API docs: https://webservice.rakuten.co.jp/documentation/ichiba-item-search
 */
public class ShoppingApiClient {

    private static final String RAKUTEN_BASE_URL =
            "https://openapi.rakuten.co.jp/ichibams/api/IchibaItem/Search/20260401";

    private final OkHttpClient client;
    private final String appId;
    private final String accessKey;

    public ShoppingApiClient() {
        this.appId = BuildConfig.RAKUTEN_APP_ID;
        this.accessKey = BuildConfig.RAKUTEN_ACCESS_KEY;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 日本語クエリで楽天市場を検索して商品リストを返す。
     * @param query  日本語検索クエリ（StyleAnalysis.searchQueryJa）
     * @param hits   取得件数（最大30）
     */
    public List<Product> searchProducts(String query, int hits) throws IOException, JSONException {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
        String url = RAKUTEN_BASE_URL
                + "?format=json"
                + "&formatVersion=2"
                + "&keyword=" + encodedQuery
                + "&applicationId=" + appId
                + "&accessKey=" + accessKey
                + "&hits=" + hits
                + "&imageFlag=1"
                + "&genreId=100371"
                + "&sort=standard";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Referer", "https://github.com/HexagonMD/Visual-Fit")
                .addHeader("Origin", "https://github.com")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            String bodyStr = response.body() != null ? response.body().string() : "null";
            if (!response.isSuccessful()) {
                throw new IOException("Rakuten API error: " + response.code() + " body=" + bodyStr);
            }
            return parseProducts(new JSONObject(bodyStr));
        }
    }

    private List<Product> parseProducts(JSONObject json) throws JSONException {
        List<Product> products = new ArrayList<>();
        JSONArray items = json.getJSONArray("Items");
        for (int i = 0; i < items.length(); i++) {
            // formatVersion=2: Items[i].itemName （itemネスト不要）
            JSONObject item = items.getJSONObject(i);

            String name = item.getString("itemName");
            String purchaseUrl = item.getString("itemUrl");
            String price = item.getString("itemPrice") + "円";
            String shopName = item.getString("shopName");

            String imageUrl = "";
            JSONArray images = item.optJSONArray("mediumImageUrls");
            if (images != null && images.length() > 0) {
                imageUrl = images.getJSONObject(0).getString("imageUrl");
            }

            if (!imageUrl.isEmpty()) {
                products.add(new Product(name, imageUrl, purchaseUrl, price, shopName));
            }
        }
        return products;
    }
}
