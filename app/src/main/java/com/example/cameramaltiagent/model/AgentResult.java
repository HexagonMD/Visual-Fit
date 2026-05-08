package com.example.cameramaltiagent.model;

import java.util.List;

/**
 * AgentPipelineの最終結果。ResultActivityに渡される。
 */
public class AgentResult {
    public StyleAnalysis styleAnalysis;
    public List<Product> products;
    public Product selectedProduct;   // TryOnに使った商品
    public TryOnResult tryOnResult;
    public String stylingComment;     // Agent4のコメント
    public String errorMessage;       // エラー発生時のメッセージ
    public boolean success;

    public AgentResult() {
        this.success = false;
    }
}

