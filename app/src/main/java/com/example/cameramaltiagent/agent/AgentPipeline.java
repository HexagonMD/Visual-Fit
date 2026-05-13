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
 * Step1: StyleAnalystAgent  (Gemini: テキスト→構造化)
 * Step2: ShoppingAgent      (Rakuten: 単品 or 複合並列検索)
 * Step3: TryOnAgent         (Gemini画像生成: 自撮り+コーデ説明→試着画像)
 * Step4: StylistAgent       (Gemini: スタイリングコメント)
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

    private final StyleAnalystAgent styleAgent   = new StyleAnalystAgent();
    private final ShoppingAgent     shoppingAgent = new ShoppingAgent();
    private final TryOnAgent        tryOnAgent    = new TryOnAgent();
    private final StylistAgent      stylistAgent  = new StylistAgent();

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
                // ── Step 1: StyleAnalystAgent ────────────────────────
                notifyStep(callback, 1, " スタイルを解析中...");
                StyleAnalysis analysis = styleAgent.analyze(clothingText);
                result.styleAnalysis = analysis;
                Log.d(TAG, "Step1 done: compound=" + analysis.isCompound
                        + " top=" + analysis.topSearchQueryJa
                        + " bottom=" + analysis.bottomSearchQueryJa);

                // ── Step 2: ShoppingAgent ────────────────────────────
                String shopMsg = analysis.isCompound
                        ? " 上着・下着を同時に検索中..."
                        : " 商品を検索中...";
                notifyStep(callback, 2, shopMsg);
                List<Product> products = shoppingAgent.search(analysis);

                Product topProduct    = products.get(0);
                Product bottomProduct = (products.size() >= 2) ? products.get(1) : null;
                result.selectedProduct = topProduct;
                result.bottomProduct   = bottomProduct;
                Log.d(TAG, "Step2 done: top=" + topProduct.name
                        + (bottomProduct != null ? " bottom=" + bottomProduct.name : ""));

                // ── Step 3: TryOnAgent ───────────────────────────────
                notifyStep(callback, 3, " AIが試着画像を生成中...\n(15〜30秒)");
                File outputDir = selfieFile.getParentFile();
                TryOnResult tryOn = tryOnAgent.execute(
                        selfieFile, topProduct, bottomProduct,
                        analysis.garmentDescForTryOn, outputDir);
                result.tryOnResult = tryOn;

                if (!tryOn.success) {
                    String reason = tryOn.failureReason != null
                            ? tryOn.failureReason : "試着画像の生成に失敗しました。";
                    throw new Exception(reason);
                }
                Log.d(TAG, "Step3 done in " + tryOn.durationMs + "ms isLocal=" + tryOn.isLocalFile);

                // ── Step 4: StylistAgent ─────────────────────────────
                notifyStep(callback, 4, "✨ スタイリングコメントを生成中...");
                String imageRef = tryOn.isLocalFile ? null : tryOn.outputImageUrl;
                String comment = stylistAgent.generateComment(
                        imageRef, tryOn.tryOnDescription, clothingText,
                        topProduct.name + (bottomProduct != null ? " + " + bottomProduct.name : ""));
                result.stylingComment = comment;

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
