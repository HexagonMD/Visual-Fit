package com.example.cameramaltiagent.model;

/**
 * Agent1(StyleAnalystAgent)の出力データ。
 * Gemini APIからJSON解析して生成される。
 */
public class StyleAnalysis {
    public String garmentType;          // 例: "T-shirt"
    public String color;                // 例: "brown"
    public String searchQueryJa;        // 楽天API用日本語クエリ
    public String searchQueryEn;        // 英語クエリ（補助）
    public String garmentDescForTryOn;  // IDM-VTON の garment_des に使用
    public String season;               // 例: "summer"

    public StyleAnalysis() {}
}

