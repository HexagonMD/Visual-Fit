package com.example.cameramaltiagent.agent;

import com.example.cameramaltiagent.api.ShoppingApiClient;
import com.example.cameramaltiagent.model.Product;
import com.example.cameramaltiagent.model.StyleAnalysis;

import java.util.List;

/**
 * Agent 2: ShoppingAgent
 *
 * StyleAnalystAgent の出力（StyleAnalysis）を受け取り、
 * 楽天市場APIで国内商品を検索して購入リンク付きの商品リストを返す。
 */
public class ShoppingAgent {

    private final ShoppingApiClient shoppingApiClient;

    public ShoppingAgent() {
        this.shoppingApiClient = new ShoppingApiClient();
    }

    /**
     * 日本語クエリで楽天市場を検索して上位5件を返す。
     * クエリはStyleAnalystAgentが生成済みの searchQueryJa を使用。
     */
    public List<Product> search(StyleAnalysis analysis) throws Exception {
        String query = analysis.searchQueryJa;
        if (query == null || query.isEmpty()) {
            // フォールバック: 色 + 種類の最低限クエリ
            query = analysis.color + " " + analysis.garmentType;
        }
        List<Product> products = shoppingApiClient.searchProducts(query, 5);
        if (products.isEmpty()) {
            throw new Exception("ShoppingAgent: 商品が見つかりませんでした。クエリ: " + query);
        }
        return products;
    }
}

