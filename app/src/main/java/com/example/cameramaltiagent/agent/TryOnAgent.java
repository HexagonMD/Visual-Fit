package com.example.cameramaltiagent.agent;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.example.cameramaltiagent.api.ReplicateApiClient;
import com.example.cameramaltiagent.model.Product;
import com.example.cameramaltiagent.model.TryOnResult;

import java.io.File;

/**
 * Agent 3: TryOnAgent
 *
 * 自撮り画像 + 楽天商品画像 を Replicate IDM-VTON API に送り、
 * バーチャル試着画像を生成する。
 * 処理は非同期のため完了まで5秒間隔でポーリングする。
 */
public class TryOnAgent {

    private final ReplicateApiClient replicateClient;

    public TryOnAgent() {
        this.replicateClient = new ReplicateApiClient();
    }

    /**
     * バーチャル試着を実行。
     *
     * @param selfieFile       Camera2で撮影した自撮り画像ファイル
     * @param product          ShoppingAgentが取得した商品（imageUrlを使用）
     * @param garmentDesc      StyleAnalysis.garmentDescForTryOn
     * @return TryOnResult（試着画像URL、所要時間など）
     */
    public TryOnResult execute(File selfieFile, Product product, String garmentDesc)
            throws Exception {
        long startTime = System.currentTimeMillis();

        // Step1: 自撮り画像をリサイズ・アップロード
        Bitmap selfie = loadAndResize(selfieFile, 768);
        String humanImageUrl = replicateClient.uploadHumanImage(selfie);

        // Step2: 試着予測を開始
        String predictionId = replicateClient.createPrediction(
                humanImageUrl,
                product.imageUrl,
                garmentDesc
        );

        // Step3: ポーリングで完了まで待機（最大120秒）
        String outputUrl = replicateClient.pollUntilComplete(predictionId);

        long duration = System.currentTimeMillis() - startTime;

        if (outputUrl == null) {
            return TryOnResult.failure();
        }
        return new TryOnResult(outputUrl, predictionId, duration);
    }

    /**
     * Nexus 7のメモリ制約を考慮して画像を縮小してからデコードする。
     * inSampleSize を使ってOOMを防止。
     */
    private Bitmap loadAndResize(File file, int maxSide) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), opts);

        int sampleSize = 1;
        while (opts.outWidth / sampleSize > maxSide
                || opts.outHeight / sampleSize > maxSide) {
            sampleSize *= 2;
        }
        opts.inSampleSize = sampleSize;
        opts.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
    }
}

