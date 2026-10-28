# Graph Report - Novel_reader_app  (2026-10-28)

## Corpus Check
- 138 files · ~128,961 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 2075 nodes · 2984 edges · 153 communities (111 shown, 42 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 104 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `b9b3ed05`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- AutoUpdateWorker
- WebNovelReaderImportManager
- NovelRepository
- .reset
- Screen
- NovelDescDaoTest
- KakuyomuAdapter
- RegistrationQueueDao
- KakuyomuTextCleanupTest
- NovelApiUtils
- UpdateService
- DatabaseSyncManager
- SettingsStore
- Base62ConverterTest
- PseudoNcodeGeneratorTest
- NovelUpdateCoordinator
- EpisodeDaoTest
- EpisodeDao
- CLAUDE.md
- NovelDescDao
- NovelRepositoryTest
- UpdateQueueDao
- ProcessingStatusType
- TempEpisodeDao
- NovelSiteAdapter
- URLEntityDao
- SortField
- NovelReaderApp
- MigrationTest
- SyosetuAdapter
- AutoUpdateScheduler
- LastReadNovelDao
- ErrorLogStore
- DatabaseExportManager
- Kotlin Coroutines / SDK バージョン 最低要求 Android バージョン
- 直接指定されている依存関係
- NovelDescEntity
- FontUtils
- Flow
- .runInTransaction
- DatabaseSchemaAnalyzer
- ReversedSeekBar.java
- NovelReaderApplication.kt
- ExternalSQLiteHelper
- What You Must Do When Invoked
- MyFirebaseMessagingService
- 2025/6/13
- VjapTextConverter
- AppLogger
- MainScreen
- MetadataUpdateManager
- Firebase Cloud Messaging (FCM) 通知の使い方
- DownloadAllReceiver
- WorkManager バージョン詳細比較
- Base62Converter
- PseudoNcodeGenerator
- 依存関係分析レポート
- VTextView
- NovelDescEntity
- SettingsMenuScreen.kt
- gradlew
- ExampleInstrumentedTest
- .getEpisodeMetasByNcode
- ExampleUnitTest
- AppInfo.kt
- ai-agent-check.sh
- ReversedViewPager
- 🔴 最優先（機能が壊れている / データ消失・クラッシュ）
- VTextLayout
- VTextLayout.java
- EpisodeViewScreen.kt
- NovelDatabase
- ImageCacheDao
- EpisodeEntity
- カクヨムダウンロードプロトコル
- graphify reference: extra exports and benchmark
- MainActivity
- CLAUDE.md
- verify.sh
- VolumeKeyPaging
- graphify reference: query, path, explain
- External Agent Policy
- Architecture Overview
- URLEntity
- graphify reference: add a URL and watch a folder
- graphify reference: commit hook and native CLAUDE.md integration
- graphify reference: incremental update and cluster-only
- .checkForNewRelease
- graphify.js
- graphify reference: GitHub clone and cross-repo merge
- graphify reference: transcribe video and audio
- extraction-spec.md
- Novel Reader
- 🟠 High
- RegistrationQueueManager
- Flows
- RecentlyReadNovelsScreen
- Key Files by Function
- 🔮 将来的な改善課題
- 1. LaunchedEffect の重複削除
- 8. データベーステーブル名のスペルミス（コメント）
- カクヨムダウンロードプロトコル
- Mission
- Novel Reader App - 統合テスト方法
- 計装テスト
- AGENTS.md
- CharSetting
- なろう小説API仕様
- Architecture Detail
- Database Schema (Version 18)
- ベストプラクティス
- Android Studioでの実行（推奨）
- 5. ハードコードされた設定値の外部化
- DownloadQueueScreen.kt
- プロジェクト構造まとめ
- コマンドラインでの実行
- トラブルシューティング
- テキストクリーンアップ処理
- 2. データベーストランザクション管理
- 4. N+1 クエリ問題の最適化
- 6. 例外処理のログ改善
- 7. Flow キャンセル処理の明確化
- テストの種類
- 🎯 改善実施時の注意事項
- 環境セットアップ
- AUDIT_PROMPT.md
- .downloadAndCacheImage
- UpdateInfoScreen
- Key Components
- Important Patterns
- テキストクリーンアップ処理
- Novel Reader — エージェント指示
- VTextView.java
- TempEpisodeEntity
- Development Commands
- Theme.kt
- Bundle
- ComponentActivity
- Flow
- MotionEvent
- NavigationManager
- StateFlow
- KeyEvent
- SuppressLint
- NovelRepository
- PagerAdapter
- ReversedViewPager
- Typeface

## God Nodes (most connected - your core abstractions)
1. `NovelRepository` - 119 edges
2. `SettingsStore` - 68 edges
3. `VTextLayout` - 45 edges
4. `VTextView` - 45 edges
5. `KakuyomuAdapter` - 43 edges
6. `KakuyomuTextCleanupTest` - 37 edges
7. `Base62ConverterTest` - 30 edges
8. `PseudoNcodeGeneratorTest` - 30 edges
9. `UpdateService` - 28 edges
10. `Screen` - 27 edges

## Surprising Connections (you probably didn't know these)
- `NovelReaderApp()` --calls--> `EpisodeListScreen()`  [INFERRED]
  Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/MainActivity.kt → Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/EpisodeListScreen.kt
- `resolveAuthorPageUrl()` --calls--> `KakuyomuAdapter`  [INFERRED]
  Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/EpisodeListScreen.kt → Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/adapter/KakuyomuAdapter.kt
- `EpisodeViewScreen()` --calls--> `ReaderMargins`  [INFERRED]
  Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/EpisodeViewScreen.kt → Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/SettingsStore.kt
- `EpisodeViewScreen()` --calls--> `SettingsStore`  [INFERRED]
  Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/EpisodeViewScreen.kt → Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/SettingsStore.kt
- `EpisodeViewScreen()` --calls--> `VjapPageController`  [INFERRED]
  Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/EpisodeViewScreen.kt → Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/ui/components/VjapVerticalTextView.kt

## Import Cycles
- None detected.

## Communities (153 total, 42 thin omitted)

### Community 0 - "AutoUpdateWorker"
Cohesion: 0.19
Nodes (9): CoroutineWorker, EpisodeInfo, ForegroundInfo, IntRange, AutoUpdateWorker, com, UpdateResult, NovelDownloadInfo (+1 more)

### Community 1 - "WebNovelReaderImportManager"
Cohesion: 0.07
Nodes (29): ByteArray, ColumnInfo, DatabaseSyncUtils, ExpectedSchema, Cursor, ImprovedDatabaseSyncManager, SQLiteDatabase, Uri (+21 more)

### Community 2 - "NovelRepository"
Cohesion: 0.05
Nodes (4): ImageCacheEntity, UpdateQueueEntity, NovelRepository, StateFlow

### Community 4 - "Screen"
Cohesion: 0.07
Nodes (26): NavigationManagerTest, DatabaseSync, DownloadQueue, EpisodeList, EpisodeView, Bundle, JSONObject, Main (+18 more)

### Community 6 - "KakuyomuAdapter"
Cohesion: 0.11
Nodes (13): Document, Exception, HttpStatusException, KakuyomuAdapter, KakuyomuEpisodeWithMapping, KakuyomuUpdateSummary, com, EpisodeEntity (+5 more)

### Community 7 - "RegistrationQueueDao"
Cohesion: 0.06
Nodes (14): MutableStateFlow, Flow, RegistrationQueueDao, RegistrationQueueEntity, NavigationViewModel, DownloadQueueViewModel, StateFlow, QueueFilter (+6 more)

### Community 9 - "NovelApiUtils"
Cohesion: 0.09
Nodes (18): EpisodeRevisionInfo, com, EpisodeEntity, NovelDescEntity, NovelApiInfo, NovelApiUtils, authorPageNeedsFetch(), AuthorPageResult (+10 more)

### Community 10 - "UpdateService"
Cohesion: 0.14
Nodes (10): Binder, IBinder, Notification, Intent, UpdateBinder, UpdateOperation, UpdateProgressListener, UpdateService (+2 more)

### Community 11 - "DatabaseSyncManager"
Cohesion: 0.29
Nodes (4): DatabaseSyncManager, Cursor, SQLiteDatabase, Uri

### Community 12 - "SettingsStore"
Cohesion: 0.05
Nodes (6): CustomFontInfo, DatabaseSyncSettings, DisplaySettings, Flow, NovelListFilterSettings, SettingsStore

### Community 15 - "NovelUpdateCoordinator"
Cohesion: 0.10
Nodes (7): MaxConcurrentExceeded, NovelUpdateCoordinator, RegistrationResult, RegistrationSession, Success, UpdateInProgress, UpdateSession

### Community 17 - "EpisodeDao"
Cohesion: 0.12
Nodes (3): EpisodeDao, Flow, EpisodeEntity

### Community 18 - "CLAUDE.md"
Cohesion: 0.15
Nodes (12): Build and Test Commands, CLAUDE.md, Common Tasks, Development Guidelines, Directory Structure (top level), Documentation Map, Keeping This File Current, Key Constraints / Gotchas (+4 more)

### Community 19 - "NovelDescDao"
Cohesion: 0.14
Nodes (3): Flow, NovelDescEntity, NovelDescDao

### Community 20 - "NovelRepositoryTest"
Cohesion: 0.08
Nodes (3): NovelRepositoryTest, EpisodeMappingDao, EpisodeMappingEntity

### Community 21 - "UpdateQueueDao"
Cohesion: 0.19
Nodes (3): Flow, UpdateQueueDao, UpdateQueueEntity

### Community 22 - "ProcessingStatusType"
Cohesion: 0.14
Nodes (16): ProcessingState, ProcessingStatusType, CHECK, ERROR, FETCHING, IDLE, PAUSED, RETRY (+8 more)

### Community 23 - "TempEpisodeDao"
Cohesion: 0.16
Nodes (3): TempEpisodeDao, fromEpisodeEntity(), TempEpisodeEntity

### Community 25 - "URLEntityDao"
Cohesion: 0.20
Nodes (3): Flow, URLEntityDao, URLEntity

### Community 26 - "SortField"
Cohesion: 0.07
Nodes (28): FilterSettings, com, NovelDescEntity, NovelListItem(), NovelListScreen(), NovelWithReadInfo, SearchField, AUTHOR (+20 more)

### Community 27 - "NovelReaderApp"
Cohesion: 0.22
Nodes (16): NovelReaderApp(), OrphanedEpisodeCheckSection(), RadioButtonOption(), ReaderMarginSettings(), SettingsAutoUpdateScreen(), SettingsDeveloperScreen(), SettingsDisplayScreen(), SettingSection() (+8 more)

### Community 28 - "MigrationTest"
Cohesion: 0.20
Nodes (4): MigrationTestHelper, SupportSQLiteDatabase, MigrationTest, androidx

### Community 30 - "AutoUpdateScheduler"
Cohesion: 0.18
Nodes (4): ExistingPeriodicWorkPolicy, LiveData, AutoUpdateScheduler, WorkInfo

### Community 31 - "LastReadNovelDao"
Cohesion: 0.22
Nodes (3): Flow, LastReadNovelDao, LastReadNovelEntity

### Community 32 - "ErrorLogStore"
Cohesion: 0.08
Nodes (24): JSONObject, EmailUtils, Context, ErrorLogStore, MutablePreferences, AppNotification, EpisodeInfo, ErrorLog (+16 more)

### Community 33 - "DatabaseExportManager"
Cohesion: 0.23
Nodes (7): DatabaseExportManager, ExportResult, Uri, DatabaseSyncActivity, DatabaseSyncScreen(), Bundle, ComponentActivity

### Community 34 - "Kotlin Coroutines / SDK バージョン 最低要求 Android バージョン"
Cohesion: 0.04
Nodes (48): 1. **パフォーマンス向上**, 2. **新機能**, 3. **API の安定化**, 4. **バグ修正**, 5. **Kotlin 1.9+ との統合**, ⚠️ **Android 16 は開発中**, 📊 Android バージョン使用率（参考）, compileSdk 36 → 35 の影響 (+40 more)

### Community 35 - "直接指定されている依存関係"
Cohesion: 0.05
Nodes (42): 1. AGP バージョンの修正, 1. JDKバージョンのアップグレード（オプション）, 1. ビルドファイルの構文チェック, 2. アプリバージョンの増分, 2. 依存関係の解決確認, 2. 定期的な依存関係の確認, 3. 実際のビルド, 4. テストの実行 (+34 more)

### Community 36 - "NovelDescEntity"
Cohesion: 0.14
Nodes (3): EpisodeMappingEntity, NovelDescEntity, ProcessingState

### Community 37 - "FontUtils"
Cohesion: 0.20
Nodes (6): CustomFont, FontUtils, com, Context, Typeface, Uri

### Community 38 - "Flow"
Cohesion: 0.17
Nodes (3): com, Flow, RegistrationQueueEntity

### Community 40 - "DatabaseSchemaAnalyzer"
Cohesion: 0.38
Nodes (3): DatabaseSchemaAnalyzer, SQLiteDatabase, Uri

### Community 41 - "ReversedSeekBar.java"
Cohesion: 0.29
Nodes (7): AttributeSet, Canvas, Context, MotionEvent, SuppressLint, ReversedSeekBar, SeekBar

### Community 42 - "NovelReaderApplication.kt"
Cohesion: 0.25
Nodes (8): Application, CoroutineScope, getAppContext(), getApplicationScope(), getRepository(), Context, NovelReaderApplication, requireInstance()

### Community 43 - "ExternalSQLiteHelper"
Cohesion: 0.33
Nodes (3): ExternalSQLiteHelper, SQLiteDatabase, Uri

### Community 44 - "What You Must Do When Invoked"
Cohesion: 0.08
Nodes (24): For /graphify add and --watch, For /graphify query, For the commit hook and native CLAUDE.md integration, For --update and --cluster-only, /graphify, Honesty Rules, Interpreter guard for subcommands, Part A - Structural extraction for code files (+16 more)

### Community 45 - "MyFirebaseMessagingService"
Cohesion: 0.39
Nodes (3): FirebaseMessagingService, MyFirebaseMessagingService, RemoteMessage

### Community 46 - "2025/6/13"
Cohesion: 0.05
Nodes (41): 10. WorkManagerによるバックグラウンド自動更新機能実装 (コミット予定), 11. R18作品判定ルール追加 (コミット予定), 12. アプリ名変更 (コミット: 実行中), 13. アプリバージョン設定 (コミット: 実行中), 1. NovelListScreen フィルター設定自動保存, 1. SettingsScreenのナビゲーションバー対応 (コミット: 3e21990), 2025/10/05, 2025/6/13 (+33 more)

### Community 47 - "VjapTextConverter"
Cohesion: 0.39
Nodes (3): Element, Node, VjapTextConverter

### Community 49 - "MainScreen"
Cohesion: 0.38
Nodes (6): Context, Modifier, MainScreen(), MenuButton(), SectionHeader(), sendDevNotification()

### Community 50 - "MetadataUpdateManager"
Cohesion: 0.39
Nodes (3): NovelDescEntity, MetadataUpdateManager, MetadataUpdateResult

### Community 51 - "Firebase Cloud Messaging (FCM) 通知の使い方"
Cohesion: 0.05
Nodes (37): 1. FCMトークンの更新, 1. Firebase プロジェクトの設定, 1. 小説更新通知 (`novel_update`), 2. google-services.json の配置, 2. お知らせ通知 (`announcement`), 2. メッセージの受信, 3. アプリのビルドとインストール, 3. カスタム通知（デフォルト） (+29 more)

### Community 52 - "DownloadAllReceiver"
Cohesion: 0.33
Nodes (4): BroadcastReceiver, DownloadAllReceiver, Context, Intent

### Community 53 - "WorkManager バージョン詳細比較"
Cohesion: 0.05
Nodes (36): 1. **Android 14/15 完全対応**, **2.10.0 での主な変更（2024年10月リリース）**, **2.10.1 での主な変更（2024年11月リリース）**, 🎯 2.10.1 を使用する利点, **2.9.0 での主な変更（2023年6月リリース）**, 2. **自動更新機能への影響**, 3. **セキュリティ向上**, 4. **パフォーマンス改善** (+28 more)

### Community 56 - "依存関係分析レポート"
Cohesion: 0.06
Nodes (32): 1. **重複した依存関係**, 2. **SnakeYAML のセキュリティ脆弱性**, 3. **Kotlin Coroutines のバージョンが古い**, 4.1 DataStore, 4.2 Jsoup, 4.3 Compose Material Icons Extended, 4.4 Compose UI Text Google Fonts, 4.5 DocumentFile (+24 more)

### Community 57 - "VTextView"
Cohesion: 0.16
Nodes (7): CurrentState, Canvas, Override, TextStyle, VTextView, PointF, View

### Community 58 - "NovelDescEntity"
Cohesion: 0.24
Nodes (5): com, NovelDescEntity, formatDate(), RecentlyUpdatedNovelsScreen(), RecentNovelItem()

### Community 59 - "SettingsMenuScreen.kt"
Cohesion: 0.67
Nodes (3): SettingsCategory, SettingsCategoryRow(), SettingsMenuScreen()

### Community 60 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 71 - "ReversedViewPager"
Cohesion: 0.18
Nodes (10): Deprecated, AttributeSet, Context, MotionEvent, Override, PagerAdapter, ReversedViewPager, OnPageChangeListener (+2 more)

### Community 72 - "🔴 最優先（機能が壊れている / データ消失・クラッシュ）"
Cohesion: 0.08
Nodes (24): 10. Android 13+ で通知が全て無音破棄（POST_NOTIFICATIONS 未要求）, 11. アプリ起動毎の REPLACE 再スケジュールが実行中の自動更新を殺す, 12. 縦書きモードの読書進捗が保存されない, 13. カクヨムHTMLフォールバック経路で NumberFormatException クラッシュ / 全話スキップ, 14. TopAppBar の戻るボタンでバックスタックが増殖, 1. 手動更新が一切実行されない回帰, 2. update_queue のフィールド逆転（3観点の監査すべてで検出）, 3. カクヨム登録キューの ncode 不整合 — 新規登録が本体DBに統合されない (+16 more)

### Community 73 - "VTextLayout"
Cohesion: 0.09
Nodes (10): Modifier, VjapAppliedState, VjapPageController, VjapVerticalTextView(), OnNextAtEndListener, OnPageChangedListener, OnPageEndListener, OnReadyListener (+2 more)

### Community 74 - "VTextLayout.java"
Cohesion: 0.12
Nodes (16): Bitmap, ImageView, DispView, DrawImageTask, AttributeSet, Canvas, Context, Handler (+8 more)

### Community 75 - "EpisodeViewScreen.kt"
Cohesion: 0.20
Nodes (13): applyRubyFixes(), attachScrollBridge(), colorToHex(), convertPlainTextToHtml(), EnhancedHtmlRubyWebView(), EpisodeViewScreen(), isHtmlContent(), Color (+5 more)

### Community 76 - "NovelDatabase"
Cohesion: 0.18
Nodes (6): getDatabase(), Context, SupportSQLiteDatabase, migrate(), NovelDatabase, RoomDatabase

### Community 79 - "カクヨムダウンロードプロトコル"
Cohesion: 0.20
Nodes (10): HTTP取得の再試行ロジック, URL構造, エピソードタイトル取得の優先順位, エピソード一覧取得の複数フォールバック, エピソード本文のエラーチェック, エピソード本文取得の優先順位, エラーハンドリング, カクヨムダウンロードプロトコル (+2 more)

### Community 80 - "graphify reference: extra exports and benchmark"
Cohesion: 0.22
Nodes (8): graphify reference: extra exports and benchmark, Step 6b - Wiki (only if --wiki flag), Step 7 - Neo4j export (only if --neo4j or --neo4j-push flag), Step 7a - FalkorDB export (only if --falkordb or --falkordb-push flag), Step 7b - SVG export (only if --svg flag), Step 7c - GraphML export (only if --graphml flag), Step 7d - MCP server (only if --mcp flag), Step 8 - Token reduction benchmark (only if total_words > 5000)

### Community 81 - "MainActivity"
Cohesion: 0.18
Nodes (7): KeyEvent, NotificationStore, Bundle, ComponentActivity, Intent, MainActivity, OnBackPressedCallback

### Community 82 - "CLAUDE.md"
Cohesion: 0.22
Nodes (9): CLAUDE.md, HTML構造の違い, R18作品判定ルール, WorkManagerによるバックグラウンド自動更新 (実装完了), 孤立エピソード検知・復元機能 (v1.9.0), 実装パターン, 🔨 最重要ルール - 新しいルールの追加プロセス, 短編小説のタイトル取得ルール (+1 more)

### Community 83 - "verify.sh"
Cohesion: 0.47
Nodes (8): check_gradle_root(), device_connected(), fail(), have(), run_gradle(), verify.sh script, skip_step(), step_banner()

### Community 84 - "VolumeKeyPaging"
Cohesion: 0.40
Nodes (3): forward, KeyEvent, VolumeKeyPaging

### Community 85 - "graphify reference: query, path, explain"
Cohesion: 0.33
Nodes (5): For /graphify explain, For /graphify path, graphify reference: query, path, explain, Step 0 — Constrained query expansion (REQUIRED before traversal), Step 1 — Traversal

### Community 86 - "External Agent Policy"
Cohesion: 0.29
Nodes (6): Agy / Antigravity CLI, Codex, External Agent Policy, graphify, OpenCode(GLM), Required final report (all external agents)

### Community 87 - "Architecture Overview"
Cohesion: 0.29
Nodes (7): Architecture Overview, Back Navigation Implementation, Core Architecture, Database Schema, Development Guidelines, Filter and Sort Settings Persistence, Special Features

### Community 89 - "graphify reference: add a URL and watch a folder"
Cohesion: 0.50
Nodes (3): For /graphify add, For --watch, graphify reference: add a URL and watch a folder

### Community 90 - "graphify reference: commit hook and native CLAUDE.md integration"
Cohesion: 0.50
Nodes (3): For git commit hook, For native CLAUDE.md integration, graphify reference: commit hook and native CLAUDE.md integration

### Community 91 - "graphify reference: incremental update and cluster-only"
Cohesion: 0.50
Nodes (3): For --cluster-only, For --update (incremental re-extraction), graphify reference: incremental update and cluster-only

### Community 97 - "Novel Reader"
Cohesion: 0.08
Nodes (24): Novel Reader, 🌐 Webブラウザ機能, アーキテクチャ, インストール手順, ⚙️ カスタマイズ, セットアップ / Setup, プロジェクト構造 / Project Structure, ライセンス / License (+16 more)

### Community 98 - "🟠 High"
Cohesion: 0.10
Nodes (20): C1. バックアップ復元で全話の既読・しおり・読書率が消失, 🔴 Critical, H10. インポートがepisode_mappingを全く同期せず、カクヨム作品が本文取得不能に, H11. 欠落修正スキャンがライブラリ全件にN+1ネットワーク（一括APIを使わない）, H1. カクヨム `cachedMappings` がシングルトン共有可変状態で、並行取得時に別作品マッピングへ上書き, H2. `getNovelsByNcodes` のIN句が無制限でSQLite変数上限(999)超過クラッシュ, H3. processQueue起動直後のジョブ未登録ウィンドウで一時停止/キャンセルが無視され孤児tempが残る, H4. Syosetuで途中失敗した話がMAX(episode_no)レジュームで恒久欠番化 (+12 more)

### Community 99 - "RegistrationQueueManager"
Cohesion: 0.20
Nodes (3): Job, RegistrationQueueEntity, RegistrationQueueManager

### Community 100 - "Flows"
Cohesion: 0.15
Nodes (12): 1. Normal task flow, 2. Difficult debugging flow, 3. High-risk flow（データ損失・マイグレーション・セキュリティ・並行処理・公開API・大規模設計）, 4. OpenCode(GLM) worktree flow, 5. Agy audit flow, 6. Codex rescue flow, AI Orchestration Guide, Cost-control policy (+4 more)

### Community 101 - "RecentlyReadNovelsScreen"
Cohesion: 0.47
Nodes (5): LastReadNovelEntity, NovelDescEntity, LastReadNovelWithInfo, RecentlyReadNovelItem(), RecentlyReadNovelsScreen()

### Community 102 - "Key Files by Function"
Cohesion: 0.17
Nodes (12): Adapters (Multi-Site Support), Application Core, Background Work, Core Utilities, Data Layer (9 Entities, 9 DAOs, 1 Repository), Download Queue Management, Key Files by Function, Metadata and Updates (+4 more)

### Community 103 - "🔮 将来的な改善課題"
Cohesion: 0.18
Nodes (10): Jetpack Compose, コード品質, データベース最適化, 中優先度, 📊 優先度別実施推奨時期, 📚 参考資料, ✅ 実施済みの修正, 🔮 将来的な改善課題 (+2 more)

### Community 104 - "1. LaunchedEffect の重複削除"
Cohesion: 0.18
Nodes (11): 1. LaunchedEffect の重複削除, 3. WebView 初期化タイミングの問題, 🟡 中優先度の改善課題, 問題の詳細, 問題の詳細, 📍 対象ファイル, 📍 対象ファイル, 影響とリスク (+3 more)

### Community 105 - "8. データベーステーブル名のスペルミス（コメント）"
Cohesion: 0.18
Nodes (11): 8. データベーステーブル名のスペルミス（コメント）, 9. 入力検証の欠如, 📝 その他の軽微な問題, 問題の詳細, 問題の詳細, 📍 対象ファイル, 📍 対象ファイル, 影響とリスク (+3 more)

### Community 106 - "カクヨムダウンロードプロトコル"
Cohesion: 0.18
Nodes (11): HTTP取得の再試行ロジック, URL構造, エピソードタイトル取得の優先順位, エピソード一覧取得の複数フォールバック, エピソード本文のエラーチェック, エピソード本文取得の優先順位, エラーハンドリング, カクヨムダウンロードプロトコル (+3 more)

### Community 107 - "Mission"
Cohesion: 0.20
Nodes (9): Acceptance criteria, Constraints, Context, Do not, Goal, Mission, Relevant files, Required final report (+1 more)

### Community 108 - "Novel Reader App - 統合テスト方法"
Cohesion: 0.20
Nodes (9): CI/CD統合, GitHub Actionsの設定例, Novel Reader App - 統合テスト方法, Pre-commit Hookの設定, まとめ, テストカバレッジ, テスト概要, 📋 目次 (+1 more)

### Community 109 - "計装テスト"
Cohesion: 0.20
Nodes (10): 1. Base62ConverterTest (27テスト), 2. PseudoNcodeGeneratorTest (30テスト), 3. KakuyomuTextCleanupTest (38テスト), 4. NovelDescDaoTest (22テスト), 5. EpisodeDaoTest (25テスト), 6. NovelRepositoryTest (27テスト), 7. MigrationTest (14テスト), テストファイル一覧 (+2 more)

### Community 110 - "AGENTS.md"
Cohesion: 0.25
Nodes (4): Claude Code, WorkManagerによるバックグラウンド自動更新 (実装完了), 自動更新の実行フロー, 自動更新機能の実装

### Community 112 - "なろう小説API仕様"
Cohesion: 0.25
Nodes (8): APIレスポンス形式, HTML構造の違い, R18作品判定ルール, なろう小説API仕様, レスポンス構造, 実装パターン, 実装パターン, 短編小説のタイトル取得ルール

### Community 113 - "Architecture Detail"
Cohesion: 0.22
Nodes (9): Architecture Detail, Back Navigation Implementation, Directory Layout, Filter and Sort Settings Persistence, Implementation Patterns (詳細コード例), Instrumented Tests (`androidTest/`), Testing Infrastructure, Unit Tests (`test/`) (+1 more)

### Community 114 - "Database Schema (Version 18)"
Cohesion: 0.25
Nodes (8): Database Performance (v12), Database Schema (Version 18), Memory Management, Migration History, Performance Optimizations, Tables (9 total), UI Performance, Version History (機能追加ログ)

### Community 115 - "ベストプラクティス"
Cohesion: 0.29
Nodes (7): 1. テストの命名規則, 2. テストの構造（AAA パターン）, 3. テストの独立性, 4. テストデータの管理, 5. 定期的なテスト実行, 6. テストカバレッジの目標, ベストプラクティス

### Community 116 - "Android Studioでの実行（推奨）"
Cohesion: 0.29
Nodes (7): 1. 全テストの実行, 2. ユニットテストのみ実行, 3. 計装テストのみ実行, 4. 個別テストの実行, 5. テスト結果の確認, Android Studioでの実行（推奨）, テスト実行方法

### Community 117 - "5. ハードコードされた設定値の外部化"
Cohesion: 0.33
Nodes (6): 5. ハードコードされた設定値の外部化, 🟢 低優先度の改善課題, 問題の詳細, 📍 対象ファイル, 影響とリスク, 推奨される修正方法

### Community 118 - "DownloadQueueScreen.kt"
Cohesion: 0.36
Nodes (8): DownloadQueueViewModel, DownloadQueueScreen(), EmptyQueueMessage(), Color, RegistrationQueueEntity, QueueItem(), StatusBadge(), StatusSummaryRow()

### Community 119 - "プロジェクト構造まとめ"
Cohesion: 0.33
Nodes (5): アーキテクチャ概要, パッケージ構成, プロジェクト構造まとめ, ルートディレクトリ, 主要ファイル

### Community 120 - "コマンドラインでの実行"
Cohesion: 0.33
Nodes (6): HTMLレポートの生成, コマンドラインでの実行, 全ユニットテストの実行, 全計装テストの実行, 特定のテストクラスの実行, 特定の計装テストの実行

### Community 121 - "トラブルシューティング"
Cohesion: 0.33
Nodes (6): トラブルシューティング, 問題1: テストが見つからない, 問題2: 計装テストが実行できない, 問題3: MigrationTestが失敗する, 問題4: テストがタイムアウトする, 問題5: Gradle ビルドエラー

### Community 122 - "テキストクリーンアップ処理"
Cohesion: 0.40
Nodes (5): HTMLエスケープ文字のデコード, Unicodeエスケープ文字のデコード, あらすじのクリーンアップ処理, テキストクリーンアップ処理, 本文のクリーンアップ処理

### Community 123 - "2. データベーストランザクション管理"
Cohesion: 0.40
Nodes (5): 2. データベーストランザクション管理, 問題の詳細, 📍 対象ファイル, 影響とリスク, 推奨される修正方法

### Community 124 - "4. N+1 クエリ問題の最適化"
Cohesion: 0.40
Nodes (5): 4. N+1 クエリ問題の最適化, 問題の詳細, 📍 対象ファイル, 影響とリスク, 推奨される修正方法

### Community 125 - "6. 例外処理のログ改善"
Cohesion: 0.40
Nodes (5): 6. 例外処理のログ改善, 問題の詳細, 📍 対象ファイル, 影響とリスク, 推奨される修正方法

### Community 126 - "7. Flow キャンセル処理の明確化"
Cohesion: 0.40
Nodes (5): 7. Flow キャンセル処理の明確化, 問題の詳細, 📍 対象ファイル, 影響とリスク, 推奨される修正方法

### Community 127 - "テストの種類"
Cohesion: 0.40
Nodes (5): 1. ユニットテスト (Unit Tests), 2. 計装テスト (Instrumented Tests), テストの種類, 対象ファイル:, 対象ファイル:

### Community 128 - "🎯 改善実施時の注意事項"
Cohesion: 0.50
Nodes (4): 実施前の準備, 実施後の確認, 実施時の原則, 🎯 改善実施時の注意事項

### Community 129 - "環境セットアップ"
Cohesion: 0.50
Nodes (4): エミュレータのセットアップ（計装テスト用）, エミュレータの作成手順:, 必要な依存関係, 環境セットアップ

### Community 132 - "UpdateInfoScreen"
Cohesion: 0.53
Nodes (5): formatDate(), NovelDescEntity, UpdateQueueEntity, UpdateInfoScreen(), UpdateQueueItem()

### Community 133 - "Key Components"
Cohesion: 0.40
Nodes (5): Application Entry Point, Data Layer, Key Components, Key Screens, Navigation

### Community 134 - "Important Patterns"
Cohesion: 0.40
Nodes (5): Data Access, Dependency Injection, Important Patterns, Navigation, Settings Management

### Community 135 - "テキストクリーンアップ処理"
Cohesion: 0.40
Nodes (5): HTMLエスケープ文字のデコード, Unicodeエスケープ文字のデコード, あらすじのクリーンアップ処理, テキストクリーンアップ処理, 本文のクリーンアップ処理

### Community 136 - "Novel Reader — エージェント指示"
Cohesion: 0.50
Nodes (4): Novel Reader — エージェント指示, 作業と検証, 保護する設計・データ契約, 必要なときだけ読む

### Community 137 - "VTextView.java"
Cohesion: 0.32
Nodes (5): Color, AttributeSet, Context, Handler, Typeface

### Community 139 - "Development Commands"
Cohesion: 0.67
Nodes (3): Build and Run, Development Commands, Working Directory

## Knowledge Gaps
- **540 isolated node(s):** `🔨 最重要ルール - 新しいルールの追加プロセス`, `Build and Run`, `Working Directory`, `Core Architecture`, `Application Entry Point` (+535 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **42 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `NovelRepository` connect `NovelRepository` to `WebNovelReaderImportManager`, `NovelDescEntity`, `Flow`, `.runInTransaction`, `TempEpisodeEntity`, `DatabaseSyncManager`, `EpisodeViewScreen.kt`, `NovelReaderApplication.kt`, `EpisodeEntity`, `MetadataUpdateManager`, `NovelRepositoryTest`, `URLEntity`?**
  _High betweenness centrality (0.159) - this node is a cross-community bridge._
- **Why does `NovelRepositoryTest` connect `NovelRepositoryTest` to `NovelRepository`, `NovelDatabase`?**
  _High betweenness centrality (0.098) - this node is a cross-community bridge._
- **Why does `EpisodeViewScreen()` connect `EpisodeViewScreen.kt` to `VTextLayout`, `NovelReaderApp`, `SettingsStore`?**
  _High betweenness centrality (0.091) - this node is a cross-community bridge._
- **Are the 21 inferred relationships involving `SettingsStore` (e.g. with `.syncData()` and `.syncFromExternalDb()`) actually correct?**
  _`SettingsStore` has 21 INFERRED edges - model-reasoned connections that need verification._
- **What connects `🔨 最重要ルール - 新しいルールの追加プロセス`, `Build and Run`, `Working Directory` to the rest of the system?**
  _540 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `WebNovelReaderImportManager` be split into smaller, more focused modules?**
  _Cohesion score 0.06603346901854365 - nodes in this community are weakly interconnected._
- **Should `NovelRepository` be split into smaller, more focused modules?**
  _Cohesion score 0.0467687074829932 - nodes in this community are weakly interconnected._