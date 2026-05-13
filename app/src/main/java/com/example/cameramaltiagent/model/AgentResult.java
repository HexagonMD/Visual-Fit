package com.example.cameramaltiagent.model;

/**
 * AgentPipelineの最終結果。ResultActivityに渡される。
 */
public class AgentResult {
    public StyleAnalysis styleAnalysis;
    public Product selectedProduct;   // 単品 or TOP商品
    public Product bottomProduct;     // 複合コーデのBOTTOM商品（null=単品）
    public TryOnResult tryOnResult;
    public String stylingComment;
    public String errorMessage;
    public boolean success;

    public AgentResult() {
        this.success = false;
    }
}