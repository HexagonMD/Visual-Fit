# 📱 VirtualFit AI — 設計書

> カメラで自撮りして「着たい服」を入力すると、AIエージェントが商品を検索・購入リンク取得・バーチャル試着画像生成・スタイリングコメントまでを自動で行うAndroidアプリ

---

## 📌 アプリ概要

| 項目 | 内容 |
|------|------|
| アプリ名 | VirtualFit AI |
| 対象端末 | Nexus 7 (API 23 / Android 6.0+) |
| 言語 | Java |
| 主要技術 | Camera2 API / Gemini API / Rakuten API / Replicate IDM-VTON / OkHttp |

---

## 🎯 利用シーン

1. 服を買おうと思っているが、自分に似合うか想像できない
2. テキストで「こんな服が欲しい」と入力するだけで候補商品を提示
3. 自分の写真に実際の商品画像を合成して試着イメージを確認
4. スタイリストAIがコーディネートアドバイスを提供

---

## 🏗 アーキテクチャ図

```
[CameraActivity]──selfieUri──▶[InputActivity]
                                     │ (selfieUri + clothingText)
                                     ▼
                            [ProcessingActivity]
                                     │
                            [AgentPipeline] ← ExecutorService
                                     │
          ┌──────────────────────────┼───────────────────────────┐
          ▼                          ▼                           ▼
[StyleAnalystAgent]       [ShoppingAgent]              [TryOnAgent]
    Gemini API              Rakuten API              Replicate IDM-VTON
    テキスト構造化          商品検索・URLリスト         バーチャル試着画像
          │                     │                           │
          └──────────┬──────────┘                           │
                     ▼                                      │
              [StylistAgent] ◀──────────────────────────────┘
               Gemini API (multimodal)
               スタイリングコメント生成
                     │
                     ▼
            [ResultActivity]
          試着画像／購入リンク／コメント表示
```

---

## 📱 画面遷移

```
┌─────────────────┐    撮影完了    ┌─────────────────┐
│ CameraActivity  │──────────────▶│  InputActivity  │
│                 │               │                 │
│ [カメラプレビュー] │               │ 📷 自撮りサムネイル │
│                 │               │                 │
│  [📷 撮影する]  │               │ 「着たい服を入力」│
└─────────────────┘               │ [EditText]      │
                                  │                 │
                                  │ [✨ 試着開始]   │
                                  └────────┬────────┘
                                           │ 開始
                                           ▼
                                  ┌─────────────────┐
                                  │ProcessingActivity│
                                  │                 │
                                  │ ① 👔 スタイル解析中..  │
                                  │ ② 🛍 商品を検索中..    │
                                  │ ③ 👗 試着画像生成中..  │
                                  │ ④ ✨ コメント生成中..  │
                                  └────────┬────────┘
                                           │ 完了
                                           ▼
                                  ┌─────────────────┐
                                  │  ResultActivity  │
                                  │                 │
                                  │  [試着結果画像]  │
                                  │  💬 スタイリスト  │
                                  │    コメント      │
                                  │  [🛍 購入する]   │
                                  │  [別商品で試す]  │
                                  └─────────────────┘
```

---

## 🤖 エージェント詳細設計

### Agent 1: StyleAnalystAgent

| 項目 | 内容 |
|------|------|
| 入力 | `String clothingText`（ユーザーのテキスト入力） |
| 使用API | Gemini 1.5 Flash（テキストのみ） |
| 出力 | `StyleAnalysis`オブジェクト（JSON） |

**プロンプト例:**
```
以下の服装説明を解析し、必ずJSON形式のみで返してください。
JSON以外のテキストは絶対に含めないでください。

服装説明: 「{clothingText}」

{
  "garment_type": "T-shirt",
  "color": "brown",
  "search_query_ja": "茶色 半袖 Tシャツ メンズ",
  "search_query_en": "brown short sleeve t-shirt",
  "garment_description_for_tryon": "brown short sleeve cotton t-shirt",
  "season": "summer"
}
```

---

### Agent 2: ShoppingAgent

| 項目 | 内容 |
|------|------|
| 入力 | `StyleAnalysis` |
| 使用API | Rakuten Ichiba API（5000回/日・無料） |
| 出力 | `List<Product>`（name, imageUrl, purchaseUrl, price） |

**Rakuten API エンドポイント:**
```
GET https://app.rakuten.co.jp/services/api/IchibaItem/Search/20170706
  ?format=json
  &keyword={search_query_ja}
  &applicationId={APP_ID}
  &hits=5
  &imageFlag=1
```

---

### Agent 3: TryOnAgent

| 項目 | 内容 |
|------|------|
| 入力 | `Uri selfiePath` + `Product selectedProduct` |
| 使用API | Replicate API（yisol/idm-vton モデル） |
| 出力 | `TryOnResult`（outputImageUrl） |
| 注意 | 非同期ポーリング必須（30〜120秒）、有料（約$0.01/回） |

**処理フロー:**
1. 自撮り画像を`POST /v1/files`でReplicateにアップロード → URL取得
2. `POST /v1/predictions`で試着開始 → `predictionId`取得
3. `GET /v1/predictions/{id}`を5秒間隔でポーリング（最大24回）
4. `status == "succeeded"` → `output[0]`が試着画像URL

---

### Agent 4: StylistAgent

| 項目 | 内容 |
|------|------|
| 入力 | `TryOnResult` + `String originalText` |
| 使用API | Gemini 1.5 Flash（マルチモーダル: 画像URL + テキスト） |
| 出力 | `String stylingComment` |

**プロンプト例:**
```
あなたはプロのスタイリストです。
この画像はバーチャル試着の結果です。元のリクエスト：「{clothingText}」

以下の観点でコメントしてください（200字以内）:
1. 全体の印象とコーデの評価
2. 改善提案（アクセサリー・靴など）
3. このスタイルが似合うシーン
```

---

## 📁 クラス構成

```
com.example.cameramaltiagent/
├── ui/
│   ├── CameraActivity.java          # Camera2プレビュー・撮影
│   ├── InputActivity.java           # テキスト入力・パイプライン起動
│   ├── ProcessingActivity.java      # 進捗表示
│   └── ResultActivity.java          # 結果表示
├── agent/
│   ├── AgentPipeline.java           # 4エージェントの順次実行
│   ├── StyleAnalystAgent.java       # テキスト構造化
│   ├── ShoppingAgent.java           # 商品検索
│   ├── TryOnAgent.java              # IDM-VTON実行
│   └── StylistAgent.java            # スタイリングコメント生成
├── api/
│   ├── GeminiApiClient.java         # Gemini API HTTP通信
│   ├── ReplicateApiClient.java      # Replicate API HTTP通信
│   └── ShoppingApiClient.java       # Rakuten API通信
├── model/
│   ├── StyleAnalysis.java           # エージェント間データ
│   ├── Product.java                 # 商品データ
│   ├── TryOnResult.java             # 試着結果データ
│   └── AgentResult.java             # パイプライン最終結果
├── camera/
│   └── Camera2Helper.java           # Camera2 API抽象化
└── util/
    ├── ImageUtil.java               # 画像処理ユーティリティ
    └── ApiKeyManager.java           # APIキー管理（BuildConfig経由）
```

---

## ⚠️ 実現可能性と注意点

| API | 無料枠 | 注意 |
|-----|--------|------|
| Gemini 1.5 Flash | 1500回/日 無料 | 4エージェント×1フロー=4回消費 |
| Rakuten Ichiba API | 5000回/日 無料 | 国内商品限定、日本語クエリ推奨 |
| Replicate IDM-VTON | **有料** ($0.01/回) | デモ用途なら数百円で十分 |
| Camera2 API | ネイティブ | API 21+対応、Nexus 7 OK |

---

## 🎤 技術ポイント

1. **マルチエージェントパイプライン設計**
   - `AgentPipeline`が`ExecutorService`上で4エージェントを順次実行
   - 各ステップの出力が次エージェントの入力になる「データパイプライン」パターン

2. **Async Pollingとタイムアウト制御（IDM-VTON）**
   - Replicateは予測が非同期なため、5秒間隔×最大24回のポーリングループを実装
   - `status=="failed"`時は別商品URLで自動リトライ

3. **マルチモーダルAI活用**
   - StylistAgentで画像URL＋テキストをGeminiに渡す
   - 試着画像を「視覚的に理解」してコメントを生成

4. **Prompt Engineeringによる安定出力**
   - JSON強制出力のプロンプト設計
   - 正規表現によるJSONブロック抽出のフォールバック処理

5. **Camera2 APIのリソース管理**
   - `onPause()`での確実な`closeCamera()`実装でメモリリーク防止
   - Nexus 7向けの`inSampleSize`による縮小処理

---

## 🗓 開発フェーズ

| フェーズ | 内容 | 目安 |
|---------|------|------|
| Phase 1 | Camera2 + InputUI + Gemini接続（StyleAnalyst + Stylist） | Week 1 |
| Phase 2 | ShoppingAgent（Rakuten）+ ResultActivity UI | Week 2 |
| Phase 3 | TryOnAgent（Replicate IDM-VTON）+ ポーリングロジック | Week 3 |
| Phase 4 | 統合 + エラーハンドリング + デモ用ポリッシュ | Week 4 |

---

## 🔑 APIキー管理

```
local.properties（Gitに含めない）:
  gemini_api_key=xxx
  replicate_api_token=xxx
  rakuten_app_id=xxx

build.gradle.kts:
  buildConfigField("String", "GEMINI_API_KEY", "\"${properties["gemini_api_key"]}\"")
```

