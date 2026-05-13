package com.example.cameramaltiagent.model;

/**
 * Agent3(TryOnAgent)の試着シミュレーション結果。
 * outputImageUrl: 楽天商品画像URL（表示用）
 * tryOnDescription: GeminiによるAI試着描写テキスト
 */
public class TryOnResult {
    public String outputImageUrl;    // 表示する画像URL（楽天商品画像）
    public String tryOnDescription;  // GeminiのAI試着描写
    public long durationMs;          // 処理時間(ms)
    public boolean success;
    public boolean isLocalFile;      // true=ローカルファイル, false=URL
    public String failureReason;

    public TryOnResult() {}

    public TryOnResult(String outputImageUrl, String tryOnDescription, long durationMs) {
        this.outputImageUrl   = outputImageUrl;
        this.tryOnDescription = tryOnDescription;
        this.durationMs       = durationMs;
        this.success          = true;
        this.isLocalFile      = false;
    }

    public static TryOnResult failure() {
        TryOnResult r = new TryOnResult();
        r.success = false;
        return r;
    }

    public static TryOnResult failure(String reason) {
        TryOnResult r = new TryOnResult();
        r.success       = false;
        r.failureReason = reason;
        return r;
    }
}