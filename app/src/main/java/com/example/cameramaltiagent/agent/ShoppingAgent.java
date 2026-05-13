package com.example.cameramaltiagent.agent;

import android.util.Log;

import com.example.cameramaltiagent.api.ShoppingApiClient;
import com.example.cameramaltiagent.model.Product;
import com.example.cameramaltiagent.model.StyleAnalysis;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Agent 2: ShoppingAgent
 *
 * 単品: リトライしながら1件検索
 * 複合コーデ: TOP/BOTTOMを並列検索して2件返す（index 0=TOP, 1=BOTTOM）
 */
public class ShoppingAgent {

    private static final String TAG = "ShoppingAgent";
    private final ShoppingApiClient api;

    public ShoppingAgent() {
        this.api = new ShoppingApiClient();
    }

    public List<Product> search(StyleAnalysis analysis) throws Exception {

        if (analysis.isCompound
                && analysis.topSearchQueryJa != null && !analysis.topSearchQueryJa.isEmpty()
                && analysis.bottomSearchQueryJa != null && !analysis.bottomSearchQueryJa.isEmpty()) {
            // ── 複合コーデ: 並列検索 ──────────────────────────────
            Log.d(TAG, "複合検索: TOP=" + analysis.topSearchQueryJa
                    + " BOTTOM=" + analysis.bottomSearchQueryJa);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            Future<Product> topFuture    = pool.submit(() -> searchFirst(analysis.topSearchQueryJa));
            Future<Product> bottomFuture = pool.submit(() -> searchFirst(analysis.bottomSearchQueryJa));
            pool.shutdown();

            Product top    = topFuture.get();
            Product bottom = bottomFuture.get();

            List<Product> result = new ArrayList<>();
            if (top    != null) result.add(top);
            if (bottom != null) result.add(bottom);
            if (result.isEmpty()) throw new Exception("商品が見つかりませんでした。別のキーワードで再試行してください。");
            return result;
        }

        // ── 単品: リトライ付き検索 ────────────────────────────────
        String[] queries = {
                analysis.searchQueryJa,
                (analysis.color + " " + analysis.garmentType).trim(),
                analysis.garmentType
        };
        for (String q : queries) {
            if (q == null || q.isEmpty()) continue;
            Log.d(TAG, "単品検索: " + q);
            List<Product> r = api.searchProducts(q, 5);
            if (!r.isEmpty()) return r;
        }
        throw new Exception("商品が見つかりませんでした。\n別のキーワードで再試行してください。");
    }

    /** クエリで検索して最初の1件を返す（見つからなければnull） */
    private Product searchFirst(String query) {
        try {
            List<Product> r = api.searchProducts(query, 3);
            return r.isEmpty() ? null : r.get(0);
        } catch (Exception e) {
            Log.e(TAG, "searchFirst error: " + query, e);
            return null;
        }
    }
}