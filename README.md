# VirtualFit AI

カメラで自撮りして「着たい服」をテキストで入力すると、商品検索・購入リンク取得・バーチャル試着画像生成・スタイリングコメントの生成を自動で行うAndroidアプリです。

---

## アプリ概要

| 項目 | 内容 |
|------|------|
| アプリ名 | VirtualFit AI |
| 対象端末 | Nexus 7 (API 23 / Android 6.0+) |
| 言語 | Java |
| 主要技術 | Camera2 API / Gemini API / Rakuten API / Replicate IDM-VTON / OkHttp |

---

## 機能概要

1. カメラで自分を撮影する
2. 「こんな服が欲しい」とテキストで入力するだけで候補商品を検索
3. 自分の写真に実際の商品画像を合成してバーチャル試着
4. 試着結果をもとにコーディネートのコメントを生成

---

## セットアップ

### 前提条件

- Android Studio（最新版推奨）
- JDK 11 以上
- Android SDK（API 23+）
- 各種 API キー（下記参照）

### API キーの設定

プロジェクトルートの `local.properties`（**Git には含めない**）に以下を追記してください。

```properties
gemini_api_key=YOUR_GEMINI_API_KEY
replicate_api_token=YOUR_REPLICATE_API_TOKEN
rakuten_app_id=YOUR_RAKUTEN_APP_ID
```

### ビルドと実行

1. このリポジトリをクローンする
2. Android Studio でプロジェクトを開く
3. `local.properties` に API キーを設定する
4. 実機またはエミュレータ（API 23+）で実行する

---

## アーキテクチャ

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

## 処理パイプライン

| エージェント | 使用 API | 役割 |
|------------|---------|------|
| StyleAnalystAgent | Gemini 1.5 Flash | テキスト入力を構造化データ（JSON）に変換 |
| ShoppingAgent | Rakuten Ichiba API | 商品検索・購入 URL リスト取得 |
| TryOnAgent | Replicate IDM-VTON | 自撮り画像への服の合成（バーチャル試着） |
| StylistAgent | Gemini 1.5 Flash (multimodal) | 試着画像をもとにスタイリングコメントを生成 |

---

## クラス構成

```
com.example.cameramaltiagent/
├── ui/
│   ├── CameraActivity.java          # Camera2 プレビュー・撮影
│   ├── InputActivity.java           # テキスト入力・パイプライン起動
│   ├── ProcessingActivity.java      # 進捗表示
│   └── ResultActivity.java          # 結果表示
├── agent/
│   ├── AgentPipeline.java           # 4 エージェントの順次実行
│   ├── StyleAnalystAgent.java       # テキスト構造化
│   ├── ShoppingAgent.java           # 商品検索
│   ├── TryOnAgent.java              # IDM-VTON 実行
│   └── StylistAgent.java            # スタイリングコメント生成
├── api/
│   ├── GeminiApiClient.java         # Gemini API HTTP 通信
│   ├── ReplicateApiClient.java      # Replicate API HTTP 通信
│   └── ShoppingApiClient.java       # Rakuten API 通信
├── model/
│   ├── StyleAnalysis.java           # エージェント間データ
│   ├── Product.java                 # 商品データ
│   ├── TryOnResult.java             # 試着結果データ
│   └── AgentResult.java             # パイプライン最終結果
├── camera/
│   └── Camera2Helper.java           # Camera2 API 抽象化
└── util/
    ├── ImageUtil.java               # 画像処理ユーティリティ
    └── ApiKeyManager.java           # API キー管理（BuildConfig 経由）
```

---

## 実装のポイント

1. **パイプライン設計**
   - `AgentPipeline` が `ExecutorService` 上で 4 つの処理を順次実行する
   - 各ステップの出力が次ステップの入力になるデータパイプライン構造

2. **非同期ポーリングとタイムアウト制御（IDM-VTON）**
   - Replicate の推論が非同期のため、5 秒間隔・最大 24 回のポーリングを実装
   - 失敗時は別商品 URL で自動リトライ

3. **マルチモーダルリクエスト**
   - StylistAgent で画像 URL とテキストを組み合わせて Gemini に送信
   - 試着後の画像を元にコメントを生成

4. **JSON 出力の安定化**
   - JSON 形式のみを返すようプロンプトを設計
   - 正規表現による JSON ブロック抽出のフォールバック処理を実装

5. **Camera2 API のリソース管理**
   - `onPause()` での `closeCamera()` 実装によりメモリリークを防止
   - Nexus 7 向けに `inSampleSize` による画像縮小処理を適用

---


## ライセンス

このプロジェクトは個人・学習目的で作成されています。
