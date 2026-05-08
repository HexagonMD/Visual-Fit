package com.example.cameramaltiagent.agent;

import com.example.cameramaltiagent.api.GeminiApiClient;

/**
 * Agent 4: StylistAgent
 *
 * TryOnResult の試着画像URL と元のユーザーテキストを受け取り、
 * Gemini のマルチモーダル機能（画像URL + テキスト）でスタイリングコメントを生成する。
 */
public class StylistAgent {

    private final GeminiApiClient geminiClient;

    public StylistAgent() {
        this.geminiClient = new GeminiApiClient();
    }

    /**
     * @param tryOnImageUrl  Replicateが生成した試着結果画像URL
     * @param originalText   ユーザーが入力した服装テキスト
     * @param productName    選択された商品名
     */
    public String generateComment(String tryOnImageUrl, String originalText,
                                  String productName) throws Exception {
        String prompt = buildPrompt(originalText, productName);
        return geminiClient.generateWithImageUrl(prompt, tryOnImageUrl);
    }

    private String buildPrompt(String originalText, String productName) {
        return "あなたはプロのファッションスタイリストです。\n"
                + "この画像はバーチャル試着の結果です。\n"
                + "元のリクエスト: 「" + originalText + "」\n"
                + "試着した商品: 「" + productName + "」\n\n"
                + "以下の観点で200字以内でコメントしてください:\n"
                + "1. 全体の印象とコーデの評価\n"
                + "2. 改善提案（アクセサリー・靴・バッグなど）\n"
                + "3. このスタイルが映えるシーン\n\n"
                + "親しみやすい日本語でお願いします。";
    }
}

