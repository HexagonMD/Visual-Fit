package com.example.cameramaltiagent.model;

/**
 * Agent2(ShoppingAgent)が楽天APIから取得する商品データ。
 */
public class Product {
    public String name;         // 商品名
    public String imageUrl;     // 商品画像URL（TryOnAgentに渡す）
    public String purchaseUrl;  // 購入ページURL（ResultActivityで開く）
    public String price;        // 価格（円）
    public String shopName;     // ショップ名

    public Product() {}

    public Product(String name, String imageUrl, String purchaseUrl,
                   String price, String shopName) {
        this.name = name;
        this.imageUrl = imageUrl;
        this.purchaseUrl = purchaseUrl;
        this.price = price;
        this.shopName = shopName;
    }
}

