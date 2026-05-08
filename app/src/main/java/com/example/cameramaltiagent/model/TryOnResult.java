package com.example.cameramaltiagent.model;

/**
 * Agent3(TryOnAgent)のReplicate IDM-VTON実行結果。
 */
public class TryOnResult {
    public String outputImageUrl;   // 試着結果画像URL
    public String predictionId;     // Replicate prediction ID
    public long durationMs;         // 生成にかかった時間(ms)
    public boolean success;

    public TryOnResult() {}

    public TryOnResult(String outputImageUrl, String predictionId, long durationMs) {
        this.outputImageUrl = outputImageUrl;
        this.predictionId = predictionId;
        this.durationMs = durationMs;
        this.success = true;
    }

    public static TryOnResult failure() {
        TryOnResult r = new TryOnResult();
        r.success = false;
        return r;
    }
}

