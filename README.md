# Novel Reader

**Novel Reader** は、小説投稿サイトの作品を快適に読むためのAndroidアプリケーションです。

## 概要 / Overview

このアプリは、オンライン小説プラットフォームから小説を取得・管理し、オフラインでも快適に読書できる環境を提供します。縦書き表示、ルビ対応、カスタムフォント、自動更新チェックなど、日本語小説の読書に特化した機能を備えています。

*An Android application for comfortably reading novels from Japanese novel platforms Features offline reading, vertical text display, ruby text support, and automatic update checking.*

## 主な機能 / Features

### 📚 小説管理
- 小説の検索・登録
- 小説一覧の表示・並び替え・フィルタリング
- 更新チェック・自動通知
- 読書履歴の管理

### 📖 読書機能
- **縦書き・横書き表示対応**
- **ルビ（ふりがな）表示**
- カスタムフォント対応
- フォントサイズ調整
- しおり機能
- 読書進度の自動保存
- 既読管理

### 🔄 同期機能
- 外部SQLiteデータベースとの同期
- 自動更新チェック（スケジュール設定可能）
- バックグラウンド更新処理

### ⚙️ カスタマイズ
- テーマ設定（ライト・ダーク・システム連動）
- 表示項目のカスタマイズ
- 背景色・文字色の設定
- 自動更新設定

### 🌐 Webブラウザ機能
- 小説サイトの直接閲覧
- WebViewからの小説登録

## 技術スタック / Tech Stack

- **言語**: Kotlin
- **UI**: Jetpack Compose
- **データベース**: Room
- **非同期処理**: Coroutines
- **ネットワーク**: OkHttp + Jsoup
- **設定管理**: DataStore
- **バックグラウンド処理**: WorkManager
- **ナビゲーション**: Navigation Component

## セットアップ / Setup

### 必要環境
- Android Studio 2023.1.1 (Hedgehog) 以降
- Kotlin 1.9.20 以降
- Android SDK 21+ (対象: SDK 34)

### インストール手順

1. リポジトリをクローン
```bash
git clone [repository-url]
cd Novel_reader
```

2. Android Studioでプロジェクトを開く

3. 依存関係を同期
```bash
./gradlew build
```

4. エミュレータまたは実機でアプリを実行

## 使い方 / Usage

### 基本的な使い方

1. **小説の登録**
   - WebViewで小説サイトを閲覧
   - 読みたい小説のページで「+」ボタンをタップ
   - 小説情報が自動取得され、データベースに登録

2. **エピソードの取得**
   - 「新着・更新情報」から「一括更新」を実行
   - または個別の小説から「小説を更新」を選択

3. **読書**
   - 小説一覧から読みたい小説を選択
   - エピソード一覧から読みたい話を選択
   - 縦書き・横書きの切り替えや文字サイズ調整が可能

4. **設定のカスタマイズ**
   - 「設定」から表示設定やフォント設定を調整
   - 自動更新の時間設定も可能

### 高度な機能

- **データベース同期**: 外部SQLiteファイルからデータをインポート
- **カスタムフォント**: TTF/OTFファイルを追加してフォントを変更
- **自動更新**: 指定した時間に自動で更新をチェック

## プロジェクト構造 / Project Structure

```
app/src/main/java/com/shunlight_library/novel_reader/
├── data/                   # データ層
│   ├── dao/               # Data Access Objects
│   ├── entity/            # データベースエンティティ
│   ├── database/          # Room データベース設定
│   ├── repository/        # リポジトリパターン
│   └── sync/              # データ同期処理
├── ui/                     # UI層
│   ├── components/        # 再利用可能なUIコンポーネント
│   └── theme/             # テーマ設定
├── navigation/            # ナビゲーション管理
├── api/                   # API通信
├── service/               # バックグラウンドサービス
├── utils/                 # ユーティリティ
└── worker/                # WorkManager関連
```

## 注意事項 / Important Notes

⚠️ **利用規約の遵守**: このアプリは小説投稿サイトのAPI等を使用します。適切な間隔でのアクセスを心がけてください。

⚠️ **個人利用厳守**: 商用利用や大量アクセスは避け、個人の読書用途での利用をしてください。

⚠️ **データの取り扱い**: 取得した小説データは個人利用の範囲内で適切に管理してください。

## ライセンス / License

このプロジェクトのライセンスについては、プロジェクトオーナーにお問い合わせください。

## 貢献 / Contributing

バグ報告や機能提案は Issue でお願いします。プルリクエストも歓迎です。

## 免責事項 / Disclaimer

このアプリケーションは非公式のツールです。小説投稿サイトの運営に影響を与えないよう、適切な利用をお願いします。アプリの使用によって生じた問題について、開発者は責任を負いません。

---

*This application is an unofficial tool. Please use it responsibly and in accordance with the terms of service of the novel platforms.*
