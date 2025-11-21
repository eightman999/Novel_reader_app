# Novel Reader

**Novel Reader** は、小説投稿サイトの作品を快適に読むためのAndroidアプリケーションです。

## 概要 / Overview

このアプリは、オンライン小説プラットフォーム（なろう小説・カクヨム）から小説を取得・管理し、オフラインでも快適に読書できる環境を提供します。縦書き表示、ルビ対応、カスタムフォント、バックグラウンド自動更新など、日本語小説の読書に特化した機能を備えています。

### 対応サイト / Supported Platforms
- **小説家になろう** (ncode.syosetu.com) - 一般作品
- **ノクターンノベルズ/ムーンライトノベルズ** (novel18.syosetu.com) - R18作品
- **カクヨム** (kakuyomu.jp)

*An Android application for comfortably reading novels from Syosetu and Kakuyomu. Features offline reading, vertical text display, ruby text support, and automatic background updates.*

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

### 🔄 自動更新機能
- **バックグラウンド自動更新** - アプリ未起動時でも指定時刻に更新確認
- **システム通知** - 更新結果をスマートフォンの通知で即座表示
- **アプリ内通知** - 詳細な更新履歴と管理機能
- **通知バッジ** - メイン画面に未読通知数表示
- 外部SQLiteデータベースとの同期

### ⚙️ カスタマイズ
- テーマ設定（ライト・ダーク・システム連動）
- 表示項目のカスタマイズ
- 背景色・文字色の設定
- 自動更新設定

### 🌐 Webブラウザ機能
- 小説サイトの直接閲覧
- WebViewからの小説登録

## 技術スタック / Tech Stack

### アーキテクチャ
- **パターン**: Clean Architecture + MVVM + Repository Pattern
- **UI**: Jetpack Compose (Single Activity)
- **ナビゲーション**: カスタムNavigationManager (バックスタック管理)
- **状態管理**: Flow-based reactive programming

### 主要技術
- **言語**: Kotlin
- **データベース**: Room (v5 with migrations)
- **非同期処理**: Coroutines + Flow
- **ネットワーク**: HttpURLConnection + Jsoup + SnakeYAML
- **設定管理**: DataStore (Preferences)
- **バックグラウンド処理**: WorkManager (定期更新スケジューリング)

## セットアップ / Setup

### 必要環境
- Android Studio 2023.1.1 (Hedgehog) 以降
- Kotlin 1.9.20 以降
- Android SDK 21+ (対象: SDK 34)

### インストール手順

1. リポジトリをクローン
```bash
git clone [repository-url]
cd Novel_reader_app
```

2. Android Studioでプロジェクトを開く

3. 依存関係を同期とビルド
```bash
cd Novel_reader
./gradlew build
```
**注意**: Gradleコマンドは `Novel_reader/` ディレクトリから実行してください。

4. エミュレータまたは実機でアプリを実行
```bash
./gradlew installDebug
```

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
Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/
├── data/
│   ├── dao/               # DAOs (5つ: NovelDesc, Episode, LastReadNovel, UpdateQueue, URL)
│   ├── entity/            # Roomエンティティ
│   ├── NovelDatabase.kt   # Room database (v5 with migrations)
│   └── NovelRepository.kt # 単一リポジトリ (全データアクセス管理)
├── ui/
│   ├── NovelListScreen.kt        # 小説一覧（高度なフィルタ・ソート）
│   ├── EpisodeViewScreen.kt      # エピソード閲覧（WebView + 進捗追跡）
│   ├── WebViewScreen.kt          # サイト閲覧（R18対応）
│   └── theme/                    # テーマ設定
├── navigation/
│   └── NavigationManager.kt      # カスタムナビゲーション（スタック管理）
├── api/
│   ├── NovelApiUtils.kt          # なろう小説API（YAML形式）
│   └── KakuyomuAdapter.kt        # カクヨムスクレイピング
├── worker/
│   ├── AutoUpdateWorker.kt       # バックグラウンド自動更新
│   └── AutoUpdateScheduler.kt    # 更新スケジュール管理
├── utils/
│   ├── SettingsStore.kt          # DataStore設定管理
│   ├── NotificationStore.kt      # アプリ内通知管理
│   └── ...
├── NovelReaderApplication.kt     # Application singleton
└── MainActivity.kt               # Single Activity (Compose)
```

### 主要コンポーネント
- **NovelDatabase**: 5テーブル構成（novels_descs, episodes, last_read_novels, update_queue, url_entity）
- **NovelRepository**: 全DAOへの統一アクセス、Flow-based reactive reads
- **NavigationManager**: sealed classによる型安全なナビゲーション
- **AutoUpdateWorker**: WorkManagerによる24時間周期の自動更新

## 注意事項 / Important Notes

⚠️ **利用規約の遵守**: このアプリは小説投稿サイトのAPI・スクレイピングを使用します。
- **なろう小説**: 公式API（YAML形式、gzip圧縮）を使用
- **カクヨム**: HTMLスクレイピング（1秒間隔のレート制限実装済み）

⚠️ **個人利用厳守**: 商用利用や大量アクセスは避け、個人の読書用途での利用をしてください。

⚠️ **R18コンテンツ**: R18作品の閲覧には年齢確認が必要です。各サイトの利用規約を遵守してください。

⚠️ **データの取り扱い**: 取得した小説データは個人利用の範囲内で適切に管理してください。

## ライセンス / License

このプロジェクトのライセンスについては、プロジェクトオーナーにお問い合わせください。

## 貢献 / Contributing

バグ報告や機能提案は Issue でお願いします。プルリクエストも歓迎です。

## 免責事項 / Disclaimer

このアプリケーションは非公式のツールです。小説投稿サイトの運営に影響を与えないよう、適切な利用をお願いします。アプリの使用によって生じた問題について、開発者は責任を負いません。

---

*This application is an unofficial tool. Please use it responsibly and in accordance with the terms of service of the novel platforms.*
