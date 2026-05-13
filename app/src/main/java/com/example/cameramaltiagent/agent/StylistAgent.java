package com.example.cameramaltiagent.agent;

import com.example.cameramaltiagent.api.GeminiApiClient;

/**
 * Agent 4: StylistAgent
 *
 * TryOnAgentが生成したAI試着描写テキストと商品画像URLを受け取り、
 * Geminiでスタイリングアドバイスを生成する。
 */
public class StylistAgent {

    private final GeminiApiClient geminiClient;

    public StylistAgent() {
        this.geminiClient = new GeminiApiClient();
    }

    /**
     * @param productImageUrl  楽天商品画像URL（視覚参考用）
     * @param tryOnDescription TryOnAgentが生成したAI試着描写テキスト
     * @param originalText     ユーザーが入力した服装テキスト
     * @param productName      選択された商品名
     */
    public String generateComment(String productImageUrl, String tryOnDescription,
                                  String originalText, String productName) throws Exception {
        String prompt = buildPrompt(tryOnDescription, originalText, productName);
        // 商品画像があればマルチモーダルで、なければテキストのみ
        if (productImageUrl != null && !productImageUrl.isEmpty()) {
            return geminiClient.generateWithImageUrl(prompt, productImageUrl);
        }
        return geminiClient.generateText(prompt);
    }

    private String buildPrompt(String tryOnDescription, String originalText, String productName) {
        return "あなたはプロのファッションスタイリストです。\n"
                + "商品画像を参考にしながら、以下のAI試着レポートをもとにスタイリングアドバイスをしてください。\n\n"
                + "【AI試着レポート】\n"
                + (tryOnDescription != null ? tryOnDescription : "（試着情報なし）") + "\n\n"
                + "元のリクエスト: 「" + originalText + "」\n"
                + "商品名: 「" + productName + "」\n\n"
                + "以下の観点で200字以内でコメントしてください:\n"
                + "1. コーデ全体の印象と評価\n"
                + "2. おすすめのスタイリング提案（アクセサリー・靴・バッグなど）\n"
                + "3. このスタイルが映えるシーン・場面\n\n"
                + "親しみやすい日本語でお願いします。";
    }
}
