package com.example.cameramaltiagent;

import android.os.Handler;
import android.os.Looper;

import com.example.cameramaltiagent.agent.AgentPipeline;
import com.example.cameramaltiagent.model.AgentResult;
import com.example.cameramaltiagent.model.Product;
import com.example.cameramaltiagent.model.StyleAnalysis;
import com.example.cameramaltiagent.model.TryOnResult;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * モックパイプライン — APIキーなしでUIの動作確認に使用する。
 * 実際のAPI呼び出しを行わず、ダミーデータを返す。
 *
 * 使い方: ProcessingActivity で AgentPipeline → MockAgentPipeline に差し替えるだけ。
 */
public class MockAgentPipeline {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void execute(File selfieFile, String clothingText,
                        AgentPipeline.PipelineCallback callback) {
        executor.submit(() -> {
            try {
                // Step 1: StyleAnalystAgent の模擬（1秒待機）
                mainHandler.post(() -> callback.onStepStarted(1, "👔 スタイルを解析中..."));
                Thread.sleep(1500);

                StyleAnalysis analysis = new StyleAnalysis();
                analysis.garmentType = "T-shirt";
                analysis.color = "brown";
                analysis.searchQueryJa = "茶色 半袖 Tシャツ メンズ";
                analysis.garmentDescForTryOn = "brown short sleeve cotton t-shirt";

                // Step 2: ShoppingAgent の模擬（1秒待機）
                mainHandler.post(() -> callback.onStepStarted(2, "🛍 商品を検索中..."));
                Thread.sleep(1500);

                Product mockProduct = new Product(
                        "【テスト商品】ユニクロ ドライカラーTシャツ ブラウン",
                        "https://placehold.co/400x500/8B4513/FFFFFF?text=T-Shirt",  // プレースホルダー画像
                        "https://www.uniqlo.com/jp/",
                        "1,990円",
                        "ユニクロ公式"
                );

                // Step 3: TryOnAgent の模擬（3秒待機）
                mainHandler.post(() -> callback.onStepStarted(3,
                        "👗 バーチャル試着を生成中...\n(30〜120秒かかります)"));
                Thread.sleep(3000);

                TryOnResult mockTryOn = new TryOnResult(
                        "https://placehold.co/512x768/8B4513/FFFFFF?text=TryOn+Result",
                        "mock-prediction-id-12345",
                        3000L
                );

                // Step 4: StylistAgent の模擬（1秒待機）
                mainHandler.post(() -> callback.onStepStarted(4, "✨ スタイリングコメントを生成中..."));
                Thread.sleep(1500);

                // 最終結果を組み立て
                AgentResult result = new AgentResult();
                result.styleAnalysis = analysis;
                result.selectedProduct = mockProduct;
                result.tryOnResult = mockTryOn;
                result.stylingComment =
                        "【モックコメント】シンプルなブラウンのTシャツはどんなボトムスにも合わせやすい万能アイテムです。"
                        + "白いスニーカーやデニムジャケットを合わせるとカジュアルコーデが完成します。"
                        + "カフェやショッピングなど日常シーンで活躍するスタイルです。";
                result.success = true;

                mainHandler.post(() -> callback.onComplete(result));

            } catch (InterruptedException e) {
                mainHandler.post(() -> callback.onError("モック処理が中断されました"));
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}

