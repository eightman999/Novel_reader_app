# 監査用プロンプト

以下をそのまま監査担当AIに渡してください。必要なら「監査対象の差分」「重点的に見たい領域」を追記して使えます。

```text
あなたは Android / Kotlin アプリのシニア監査担当です。以下のリポジトリを、表面的な感想ではなく「壊れるか・データが壊れるか・仕様から外れていないか・重くないか」の観点で厳密に監査してください。

【対象リポジトリ】
- プロジェクト: Novel Reader
- 種別: Android アプリ
- 主技術: Kotlin, Jetpack Compose, Room, WorkManager, DataStore, WebView
- アーキテクチャ: Clean Architecture + MVVM + Repository Pattern + Adapter Pattern
- 対応サイト: 小説家になろう（一般）, 小説家になろう R18, カクヨム

【重要】
- 実コードを読んで判断し、README や補助ドキュメントの記述を鵜呑みにしないこと
- スタイル指摘より、機能不全・回帰・データ破損・多サイト分岐漏れ・競合状態・性能劣化を優先すること
- 「もしかすると危ない」ではなく、できるだけコード上の根拠と再現条件を示すこと
- 差分監査なら、変更ファイルだけでなく影響先も追うこと
- テストが不足している場合は、どのケースが未検証かまで具体化すること

【作業前提】
- Gradle コマンドは `Novel_reader/` ディレクトリで実行する
- 現在の主要状態の目安:
  - app versionCode: 219
  - app versionName: 2.0.19
  - Room Database version: 17
  - Entity/DAO は 9 系統
- ただし上記も必ず実コードで再確認すること

【最重要の監査観点】
1. 機能回帰
2. データ破損・欠損・取りこぼし
3. 並行処理、排他制御、レースコンディション
4. Room スキーマ、Migration、Entity 定義の整合性
5. WorkManager / Service / Receiver / 通知権限まわり
6. WebView / JavaScript bridge / ローカルファイルアクセスの安全性
7. 小説サイトごとの分岐漏れ
8. 大量データ時のメモリ、N+1、無駄な再取得
9. 既存プロジェクトルール違反
10. テスト不足、またはテストが守れていない仕様

【特に重点的に見るファイル群】
- `Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/adapter/`
- `Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/api/NovelApiUtils.kt`
- `Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/repository/NovelRepository.kt`
- `Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/database/NovelDatabase.kt`
- `Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/service/UpdateService.kt`
- `Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/worker/`
- `Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/manager/RegistrationQueueManager.kt`
- `Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/EpisodeListScreen.kt`
- `Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/EpisodeViewScreen.kt`
- `Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/WebViewScreen.kt`
- `Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/MainActivity.kt`
- `Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/SettingsStore.kt`

【このプロジェクト固有の必須ルール】

### アーキテクチャ / データアクセス
- DAO 直アクセスは原則禁止。Repository 経由で扱うこと
- 読み取りは Flow ベース、書き込みは suspend 関数ベースであること
- 複数テーブルをまたぐ重要更新は、必要に応じてトランザクション整合性が取れていること

### Room / DB
- スキーマ変更時は Migration が必要
- Entity 定義、Migration、schema JSON、インデックス定義に不整合がないこと
- 同期やインポート処理で既存の既読状態、しおり、読書率、site_type、R18情報、マッピング情報が壊れないこと

### ナビゲーション
- deprecated な `onBackPressed()` ではなく `OnBackPressedDispatcher` を使うこと
- `NavigationManager` を通じてバックスタックが自然に戻ること
- 「戻る」操作が `navigateTo()` になってスタック増殖していないか確認すること

### 設定永続化
- リスト画面のフィルタ・ソート設定は `SettingsStore` で永続化され、次回起動時に復元されること
- enum 復元時の例外安全性があること

### なろう / R18 判定
- R18 判定は必ず `rating == 1`
- `rating == 1` → `novel18.syosetu.com` / `novel18api`
- `rating == 2` → `ncode.syosetu.com` / `novelapi`
- API URL、Web URL、更新確認、WebView 表示でこのルールが統一されていること

### 短編小説のタイトル取得
- 連載タイトル取得は `h1.p-novel__title.p-novel__title--rensai`
- これが空なら必ず `h1.p-novel__title` にフォールバックすること
- 短編時にタイトルが空で保存されないこと

### カクヨム監査ルール
- カクヨムは公式 API ではなく HTML スクレイピング前提
- 全 HTTP リクエスト前に 0.5 秒レート制限が必要
- 本文取得は以下の順序でフォールバックすること
  1. `div.widget-episodeBody.js-episode-body`
  2. `div.widget-episodeBody`
  3. `div.js-episode-body`
- タイトル取得は以下の順序を守ること
  1. `header#contentMain-header`
  2. `p.widget-episodeTitle`
  3. `h1`
  4. 最後は `第X話`
- 章タイトル取得は以下の順序
  1. `p.chapterTitle.level1 span`
  2. `p.chapterTitle.level2 span`
- 本文先頭 200 文字以内に `<div class="dots-indicator" id="LoadingEpisode">` があれば取得失敗扱いにすること
- HTTP 取得は最大 3 回再試行し、指数バックオフを使うこと
- HTML 取得は途中で切らず、最後までバッファリングして読むこと
- テキストクリーンアップは、HTML エスケープ → 数値エスケープ → Unicode エスケープの順にデコードされること
- エピソード一覧取得は、まず「任意のエピソードページ内の完全な目次」から全件取得できるか確認すること
- カクヨムの作品IDとエピソードIDは独立しており、連番扱いしていないこと
- episode mapping が保存されない経路がないか確認すること
- 疑似 Ncode 生成・保存・参照の整合性があること

### 孤立エピソード検知・復元
- `episodes` に存在するが `novels_descs` にない ncode を検出できること
- 復元時は本文再取得ではなくメタデータ復元中心であること
- `total_ep` と `episode_count` は API 値ではなく DB 実数で整合させること
- なろうは一般 API → 失敗時に R18 API 再試行の順で確認すること

### ログ / 実装の一貫性
- ログ出力はプロジェクトのロギング方針に沿っているか確認すること
- 多サイト対応なのに、Syosetu 固定 URL や固定分岐が UI / Service / Worker に残っていないか確認すること

【期待する監査出力形式】
- まず findings を列挙すること
- 重要度順に並べること
- 各 finding には以下を含めること
  - 重要度: `Critical` / `High` / `Medium` / `Low`
  - タイトル
  - ファイルパスと行番号
  - 問題の内容
  - なぜ危険か
  - どの条件で起こるか
  - 修正方針
- 可能なら再現シナリオや、壊れるデータの種類も書くこと
- 指摘がない場合は「重大な指摘なし」と明言し、その代わり残留リスクと未検証範囲を書くこと

【出力ルール】
- 要約より findings を優先
- 些細な命名や好みのコードスタイルには寄り道しない
- 「可能性」だけでなく、なるべくコード上の根拠を書く
- 良い実装があれば最後に短く触れてよい

必要なら以下も監査対象に含めてください:
- 監査対象差分:
- 重点確認したい機能:
- 実行してよい検証コマンド:
```
