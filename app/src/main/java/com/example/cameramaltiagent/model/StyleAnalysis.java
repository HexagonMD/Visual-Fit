package com.example.cameramaltiagent.model;

/**
 * Agent1(StyleAnalystAgent)の出力データ。
 */
public class StyleAnalysis {
    public String garmentType;          // 例: "T-shirt"
    public String color;                // 例: "brown"
    public String searchQueryJa;        // 楽天API用日本語クエリ（単品）
    public String garmentDescForTryOn;  // TryOnAgent のコーデ説明に使用
    public String season;               // 例: "summer"

    // 複合コーデ用
    public boolean isCompound;           // TOP+BOTTOMが含まれているか
    public String topSearchQueryJa;      // 上着の検索クエリ
    public String bottomSearchQueryJa;   // 下着の検索クエリ

    public StyleAnalysis() {}
}