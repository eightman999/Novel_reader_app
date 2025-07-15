# プロジェクト構造まとめ

本書では Android アプリ **Novel Reader** のディレクトリ構成と主要コンポーネントを簡潔に整理します。詳細な説明は README も参照してください。

## ルートディレクトリ

- `Novel_reader/` - アプリ本体の Gradle プロジェクト
  - `app/` - アプリケーションモジュール
  - `gradle/` や各種 `*.gradle.kts` - ビルド設定
- `release/` - 生成済み APK など

## パッケージ構成

メインモジュール `app/src/main/java/com/shunlight_library/novel_reader/` 以下は次のように整理されています。

```
app/src/main/java/com/shunlight_library/novel_reader/
├── data/       # DAO・エンティティ・データベース・リポジトリ
├── ui/         # Compose 画面とテーマ
├── navigation/ # 画面遷移管理
├── api/        # API 通信ユーティリティ
├── service/    # バックグラウンドサービス
├── utils/      # 各種ヘルパー
└── worker/     # WorkManager 用タスク
```

## 主要ファイル

- `NovelReaderApplication.kt` - データベースとリポジトリのシングルトンを初期化
- `MainActivity.kt` - 1 つのアクティビティで Compose 画面をホスト
- `AutoUpdateWorker.kt` / `AutoUpdateScheduler.kt` - 自動更新機能と通知

## アーキテクチャ概要

`AGENTS.md` に記載されている通り、クリーンアーキテクチャ + MVVM + リポジトリパターンを採用しています。主要なポイントは以下の通りです。

- 画面は Jetpack Compose で実装し、`NavigationManager` により画面遷移を管理
- データは Room データベース (v5) を `NovelRepository` 経由でアクセス
- 更新処理は WorkManager により定期実行
- `SettingsStore` (DataStore) を用いた設定保存
- 読み込み系は `Flow`、書き込み系は `suspend` 関数で実装

この構造により、オフライン読書や R18 対応、自動更新といった機能をモジュール化して実現しています。
