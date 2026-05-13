package com.example.cameramaltiagent.agent;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.cameramaltiagent.model.AgentResult;
import com.example.cameramaltiagent.model.Product;
import com.example.cameramaltiagent.model.StyleAnalysis;
import com.example.cameramaltiagent.model.TryOnResult;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AgentPipeline — 4エージェントの順次オーケストレーター。
 *
 * 処理フロー:
 *   Step1: StyleAnalystAgent  (Gemini: テキスト → 構造化)
 *   Step2: ShoppingAgent      (Rakuten: 商品検索)
 *   Step3: TryOnAgent         (Replicate IDM-VTON: 試着画像生成)
 *   Step4: StylistAgent       (Gemini Multimodal: コメント生成)
 */
public class AgentPipeline {

    private static final String TAG = "AgentPipeline";

    /** ProcessingActivityからUIを更新するためのコールバック */
    public interface PipelineCallback {
        /** ステップ進捗通知 (step: 1〜4, message: 表示テキスト) */
        void onStepStarted(int step, String message);
        /** パイプライン完了 */
        void onComplete(AgentResult result);
        /** エラー発生 */
        void onError(String errorMessage);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final StyleAnalystAgent styleAgent = new StyleAnalystAgent();
    private final ShoppingAgent shoppingAgent = new ShoppingAgent();
    private final TryOnAgent tryOnAgent = new TryOnAgent();
    private final StylistAgent stylistAgent = new StylistAgent();

    /**
     * パイプラインを開始する。バックグラウンドスレッドで実行される。
     *
     * @param selfieFile    Camera2で撮影・保存した自撮り画像ファイル
     * @param clothingText  ユーザーが入力した服装テキスト
     * @param callback      結果通知先（ActivityのUI更新に使用）
     */
    public void execute(File selfieFile, String clothingText, PipelineCallback callback) {
        executor.submit(() -> {
            AgentResult result = new AgentResult();
            try {
                // ── Step 1: StyleAnalystAgent ─────────────────────────
                notifyStep(callback, 1, "👔 スタイルを解析中...");
                StyleAnalysis analysis = styleAgent.analyze(clothingText);
                result.styleAnalysis = analysis;
                Log.d(TAG, "Step1 done: " + analysis.searchQueryJa);

                // ── Step 2: ShoppingAgent ─────────────────────────────
                notifyStep(callback, 2, "🛍 商品を検索中...");
                List<Product> products = shoppingAgent.search(analysis);
                result.products = products;
                Product selected = products.get(0); // 先頭商品を試着対象に選択
                result.selectedProduct = selected;
                Log.d(TAG, "Step2 done: " + selected.name);

                // ── Step 3: TryOnAgent (Gemini版) ────────────────────
                notifyStep(callback, 3, "👗 AIが試着をシミュレーション中...\n(10〜15秒)");
                TryOnResult tryOn = tryOnAgent.execute(
                        selfieFile, selected, analysis.garmentDescForTryOn);
                result.tryOnResult = tryOn;

                if (!tryOn.success) {
                    String reason = (tryOn.failureReason != null && !tryOn.failureReason.isEmpty())
                            ? tryOn.failureReason
                            : "試着シミュレーションに失敗しました。再試行してください。";
                    throw new Exception(reason);
                }
                Log.d(TAG, "Step3 done in " + tryOn.durationMs + "ms");

                // ── Step 4: StylistAgent ──────────────────────────────
                notifyStep(callback, 4, "✨ スタイリングコメントを生成中...");
                String comment = stylistAgent.generateComment(
                        tryOn.outputImageUrl, tryOn.tryOnDescription, clothingText, selected.name);
                result.stylingComment = comment;
                Log.d(TAG, "Step4 done: " + comment.substring(0, Math.min(50, comment.length())));

                // ── 完了 ──────────────────────────────────────────────
                result.success = true;
                mainHandler.post(() -> callback.onComplete(result));

            } catch (Exception e) {
                Log.e(TAG, "Pipeline error", e);
                result.errorMessage = e.getMessage();
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /** UIスレッドへのステップ通知 */
    private void notifyStep(PipelineCallback callback, int step, String message) {
        mainHandler.post(() -> callback.onStepStarted(step, message));
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}

