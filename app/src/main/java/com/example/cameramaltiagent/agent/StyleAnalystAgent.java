package com.example.cameramaltiagent.agent;

import com.example.cameramaltiagent.api.GeminiApiClient;
import com.example.cameramaltiagent.model.StyleAnalysis;

import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 1: StyleAnalystAgent
 *
 * ユーザーの自然言語テキスト（例：「茶色の半袖Tシャツとジーパン」）を
 * Gemini APIで構造化して StyleAnalysis オブジェクトに変換する。
 *
 * Gemini APIへのプロンプトでJSON形式の出力を強制し、
 * 正規表現フォールバックで安定したパースを保証する。
 */
public class StyleAnalystAgent {

    private final GeminiApiClient geminiClient;

    public StyleAnalystAgent() {
        this.geminiClient = new GeminiApiClient();
    }

    public StyleAnalysis analyze(String clothingText) throws Exception {
        String prompt = buildPrompt(clothingText);
        String rawResponse = geminiClient.generateText(prompt);
        return parseResponse(rawResponse);
    }

    private String buildPrompt(String clothingText) {
        return "以下の服装説明を解析し、必ずJSON形式のみで返してください。\n"
                + "JSON以外のテキストは絶対に含めないでください。\n\n"
                + "服装説明: 「" + clothingText + "」\n\n"
                + "【判定ルール】\n"
                + "- 上着（Tシャツ・シャツ・ジャケット等）と下着（パンツ・スカート等）の両方が含まれていれば is_compound=true\n"
                + "- top_search_query_ja: 上着だけの短い検索ワード（2〜3語）\n"
                + "- bottom_search_query_ja: 下着だけの短い検索ワード（2〜3語）\n"
                + "- search_query_ja: 単品の場合のみ使用（is_compound=falseのとき）\n"
                + "- garment_description_for_tryon: コーデ全体の詳細な英語説明\n\n"
                + "返すべきJSON:\n"
                + "{\n"
                + "  \"is_compound\": true,\n"
                + "  \"garment_type\": \"T-shirt\",\n"
                + "  \"color\": \"white\",\n"
                + "  \"search_query_ja\": \"白 オーバーサイズ Tシャツ\",\n"
                + "  \"top_search_query_ja\": \"白 オーバーサイズ Tシャツ\",\n"
                + "  \"bottom_search_query_ja\": \"黒 スキニーパンツ\",\n"
                + "  \"garment_description_for_tryon\": \"white oversized t-shirt and black skinny pants, casual street style\",\n"
                + "  \"season\": \"spring\"\n"
                + "}";
    }

    /**
     * LLMの出力が不安定なことを想定した堅牢なJSON抽出処理。
     * 正規表現でJSONブロックのみを抜き出してパースする。
     */
    private StyleAnalysis parseResponse(String raw) throws Exception {
        // まずそのままパースを試みる
        String jsonStr = raw.trim();

        // JSON以外のテキストが混入した場合は正規表現で抽出
        if (!jsonStr.startsWith("{")) {
            Pattern p = Pattern.compile("\\{[^{}]*\\}", Pattern.DOTALL);
            Matcher m = p.matcher(jsonStr);
            if (m.find()) {
                jsonStr = m.group();
            } else {
                throw new Exception("StyleAnalystAgent: JSON抽出失敗。生レスポンス: " + raw);
            }
        }

        JSONObject json = new JSONObject(jsonStr);
        StyleAnalysis analysis = new StyleAnalysis();
        analysis.garmentType          = json.optString("garment_type", "");
        analysis.color                = json.optString("color", "");
        analysis.searchQueryJa        = json.optString("search_query_ja", "");
        analysis.garmentDescForTryOn  = json.optString("garment_description_for_tryon", "");
        analysis.season               = json.optString("season", "");
        analysis.isCompound           = json.optBoolean("is_compound", false);
        analysis.topSearchQueryJa     = json.optString("top_search_query_ja", "");
        analysis.bottomSearchQueryJa  = json.optString("bottom_search_query_ja", "");
        return analysis;
    }
}

