# Graph Report - Novel_reader_app  (2026-08-02)

## Corpus Check
- 133 files · ~123,752 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1406 nodes · 2312 edges · 97 communities (71 shown, 26 thin omitted)
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 112 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `207488ef`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- VTextLayout
- WebNovelReaderImportManager
- NovelRepository
- VTextView
- Screen
- NovelDescDaoTest
- KakuyomuAdapter
- RegistrationQueueEntity
- KakuyomuTextCleanupTest
- NovelApiUtils
- UpdateService
- DatabaseSyncManager
- SettingsStore
- Base62ConverterTest
- PseudoNcodeGeneratorTest
- NovelUpdateCoordinator
- EpisodeDaoTest
- EpisodeEntity
- DownloadQueueViewModel
- NovelDescEntity
- NovelRepositoryTest
- UpdateQueueEntity
- ProcessingState
- TempEpisodeDao
- NovelSiteAdapter
- URLEntity
- SortField
- NovelReaderApp
- MigrationTest
- SyosetuAdapter
- AutoUpdateScheduler
- EpisodeMappingEntity
- ErrorLogStore
- .onCreate
- AutoUpdateWorker
- NotificationStore
- FontUtils
- SettingsStore.kt
- .deleteNovelWithRelations
- DatabaseSchemaAnalyzer
- ReversedSeekBar.java
- NovelReaderApplication.kt
- ExternalSQLiteHelper
- What You Must Do When Invoked
- MyFirebaseMessagingService
- NotificationDialog
- VjapTextConverter
- AppLogger
- MainScreen
- MetadataUpdateManager
- .checkForNewRelease
- DownloadAllReceiver
- .downloadKakuyomuEpisodes
- Base62Converter
- PseudoNcodeGenerator
- NotificationType
- RecentlyReadNovelsScreen
- RecentNovelItem
- SettingsMenuScreen.kt
- gradlew
- ExampleInstrumentedTest
- .getEpisodeMetasByNcode
- ExampleUnitTest
- AppInfo.kt
- ai-agent-check.sh
- ReversedViewPager
- ImageCacheEntity
- VjapVerticalTextView
- DispView
- EnhancedHtmlRubyWebView
- .onInterceptTouchEvent
- NavigationManager
- カクヨムダウンロードプロトコル
- CLAUDE.md
- graphify reference: extra exports and benchmark
- .onCreate
- External Agent Policy
- Architecture Overview
- DatabaseExportManager
- graphify reference: query, path, explain
- Key Components
- Important Patterns
- テキストクリーンアップ処理
- graphify reference: add a URL and watch a folder
- graphify reference: commit hook and native CLAUDE.md integration
- graphify reference: incremental update and cluster-only
- Development Commands
- graphify.js
- graphify reference: GitHub clone and cross-repo merge
- graphify reference: transcribe video and audio
- extraction-spec.md

## God Nodes (most connected - your core abstractions)
1. `NovelRepository` - 115 edges
2. `SettingsStore` - 60 edges
3. `NovelDescEntity` - 44 edges
4. `KakuyomuAdapter` - 43 edges
5. `VTextView` - 41 edges
6. `EpisodeEntity` - 40 edges
7. `KakuyomuTextCleanupTest` - 37 edges
8. `VTextLayout` - 33 edges
9. `Base62ConverterTest` - 30 edges
10. `PseudoNcodeGeneratorTest` - 30 edges

## Surprising Connections (you probably didn't know these)
- `NovelReaderApp()` --calls--> `EpisodeListScreen()`  [INFERRED]
  Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/MainActivity.kt → Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/EpisodeListScreen.kt
- `resolveAuthorPageUrl()` --calls--> `KakuyomuAdapter`  [INFERRED]
  Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/EpisodeListScreen.kt → Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/adapter/KakuyomuAdapter.kt
- `EpisodeViewScreen()` --calls--> `SettingsStore`  [INFERRED]
  Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/EpisodeViewScreen.kt → Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/SettingsStore.kt
- `EpisodeViewScreen()` --calls--> `VjapVerticalTextView()`  [INFERRED]
  Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/EpisodeViewScreen.kt → Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/ui/components/VjapVerticalTextView.kt
- `NovelReaderApp()` --calls--> `EpisodeViewScreen()`  [INFERRED]
  Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/MainActivity.kt → Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/EpisodeViewScreen.kt

## Import Cycles
- None detected.

## Communities (97 total, 26 thin omitted)

### Community 0 - "VTextLayout"
Cohesion: 0.22
Nodes (10): AttributeSet, Context, PagerAdapter, View, VTextLayout, OnPageClacListener, OnPageChangeListener, ProgressBar (+2 more)

### Community 1 - "WebNovelReaderImportManager"
Cohesion: 0.07
Nodes (27): ByteArray, ColumnInfo, DatabaseSyncUtils, ExpectedSchema, Cursor, ImprovedDatabaseSyncManager, SQLiteDatabase, Uri (+19 more)

### Community 2 - "NovelRepository"
Cohesion: 0.05
Nodes (4): com, Flow, StateFlow, NovelRepository

### Community 3 - "VTextView"
Cohesion: 0.09
Nodes (14): Color, CharSetting, CurrentState, AttributeSet, Canvas, Context, Handler, Override (+6 more)

### Community 4 - "Screen"
Cohesion: 0.11
Nodes (18): DatabaseSync, DownloadQueue, EpisodeList, EpisodeView, Main, NovelList, RecentlyReadNovels, RecentlyUpdatedNovels (+10 more)

### Community 6 - "KakuyomuAdapter"
Cohesion: 0.12
Nodes (8): Document, JSONObject, KakuyomuAdapter, KakuyomuEpisodeWithMapping, KakuyomuUpdateSummary, com, NovelWithEpisodesAndMappings, org

### Community 7 - "RegistrationQueueEntity"
Cohesion: 0.07
Nodes (5): Job, Flow, RegistrationQueueDao, RegistrationQueueEntity, RegistrationQueueManager

### Community 9 - "NovelApiUtils"
Cohesion: 0.10
Nodes (15): EpisodeRevisionInfo, com, NovelApiInfo, NovelApiUtils, authorPageNeedsFetch(), AuthorPageResult, EpisodeItem(), EpisodeListScreen() (+7 more)

### Community 10 - "UpdateService"
Cohesion: 0.15
Nodes (9): Binder, IBinder, Notification, Intent, UpdateBinder, UpdateOperation, UpdateProgressListener, UpdateService (+1 more)

### Community 11 - "DatabaseSyncManager"
Cohesion: 0.12
Nodes (7): Flow, LastReadNovelDao, LastReadNovelEntity, DatabaseSyncManager, Cursor, SQLiteDatabase, Uri

### Community 15 - "NovelUpdateCoordinator"
Cohesion: 0.10
Nodes (7): MaxConcurrentExceeded, NovelUpdateCoordinator, RegistrationResult, RegistrationSession, Success, UpdateInProgress, UpdateSession

### Community 16 - "EpisodeDaoTest"
Cohesion: 0.08
Nodes (7): EpisodeDaoTest, getDatabase(), Context, SupportSQLiteDatabase, migrate(), NovelDatabase, RoomDatabase

### Community 17 - "EpisodeEntity"
Cohesion: 0.11
Nodes (3): EpisodeDao, Flow, EpisodeEntity

### Community 18 - "DownloadQueueViewModel"
Cohesion: 0.12
Nodes (15): MutableStateFlow, DownloadQueueScreen(), EmptyQueueMessage(), QueueItem(), StatusBadge(), StatusSummaryRow(), DownloadQueueViewModel, StateFlow (+7 more)

### Community 19 - "NovelDescEntity"
Cohesion: 0.13
Nodes (3): Flow, NovelDescDao, NovelDescEntity

### Community 21 - "UpdateQueueEntity"
Cohesion: 0.12
Nodes (6): Flow, UpdateQueueDao, UpdateQueueEntity, formatDate(), UpdateInfoScreen(), UpdateQueueItem()

### Community 22 - "ProcessingState"
Cohesion: 0.13
Nodes (16): ProcessingState, ProcessingStatusType, CHECK, ERROR, FETCHING, IDLE, PAUSED, RETRY (+8 more)

### Community 23 - "TempEpisodeDao"
Cohesion: 0.12
Nodes (3): TempEpisodeDao, fromEpisodeEntity(), TempEpisodeEntity

### Community 25 - "URLEntity"
Cohesion: 0.15
Nodes (3): Flow, URLEntityDao, URLEntity

### Community 26 - "SortField"
Cohesion: 0.08
Nodes (27): FilterSettings, com, NovelListItem(), NovelListScreen(), NovelWithReadInfo, SearchField, AUTHOR, NCODE (+19 more)

### Community 27 - "NovelReaderApp"
Cohesion: 0.26
Nodes (14): NovelReaderApp(), OrphanedEpisodeCheckSection(), RadioButtonOption(), SettingsAutoUpdateScreen(), SettingsDeveloperScreen(), SettingsDisplayScreen(), SettingSection(), SettingsNetworkScreen() (+6 more)

### Community 28 - "MigrationTest"
Cohesion: 0.20
Nodes (4): MigrationTestHelper, SupportSQLiteDatabase, MigrationTest, androidx

### Community 30 - "AutoUpdateScheduler"
Cohesion: 0.18
Nodes (4): ExistingPeriodicWorkPolicy, LiveData, AutoUpdateScheduler, WorkInfo

### Community 32 - "ErrorLogStore"
Cohesion: 0.21
Nodes (5): EmailUtils, Context, ErrorLogStore, MutablePreferences, ErrorLog

### Community 33 - ".onCreate"
Cohesion: 0.28
Nodes (6): DatabaseSyncActivity, DatabaseSyncScreen(), Bundle, ComponentActivity, AppSettings, Novel_readerTheme()

### Community 34 - "AutoUpdateWorker"
Cohesion: 0.29
Nodes (5): CoroutineWorker, ForegroundInfo, AutoUpdateWorker, UpdateResult, Result

### Community 35 - "NotificationStore"
Cohesion: 0.23
Nodes (5): AppNotification, Flow, MutablePreferences, NotificationStore, NovelDownloadInfo

### Community 37 - "FontUtils"
Cohesion: 0.20
Nodes (6): CustomFont, FontUtils, com, Context, Typeface, Uri

### Community 38 - "SettingsStore.kt"
Cohesion: 0.17
Nodes (4): CustomFontInfo, DatabaseSyncSettings, DisplaySettings, NovelListFilterSettings

### Community 40 - "DatabaseSchemaAnalyzer"
Cohesion: 0.38
Nodes (3): DatabaseSchemaAnalyzer, SQLiteDatabase, Uri

### Community 41 - "ReversedSeekBar.java"
Cohesion: 0.29
Nodes (7): AttributeSet, Canvas, Context, MotionEvent, SuppressLint, ReversedSeekBar, SeekBar

### Community 42 - "NovelReaderApplication.kt"
Cohesion: 0.20
Nodes (7): Application, CoroutineScope, getAppContext(), getApplicationScope(), getRepository(), Context, NovelReaderApplication

### Community 43 - "ExternalSQLiteHelper"
Cohesion: 0.33
Nodes (3): ExternalSQLiteHelper, SQLiteDatabase, Uri

### Community 44 - "What You Must Do When Invoked"
Cohesion: 0.08
Nodes (24): For /graphify add and --watch, For /graphify query, For the commit hook and native CLAUDE.md integration, For --update and --cluster-only, /graphify, Honesty Rules, Interpreter guard for subcommands, Part A - Structural extraction for code files (+16 more)

### Community 45 - "MyFirebaseMessagingService"
Cohesion: 0.33
Nodes (3): FirebaseMessagingService, MyFirebaseMessagingService, RemoteMessage

### Community 46 - "NotificationDialog"
Cohesion: 0.39
Nodes (8): DownloadDetailDialog(), ErrorLogDetailDialog(), ErrorLogDialog(), formatPublishedDate(), com, NotificationDialog(), NotificationItem(), ReleaseDetailDialog()

### Community 47 - "VjapTextConverter"
Cohesion: 0.39
Nodes (3): Element, Node, VjapTextConverter

### Community 49 - "MainScreen"
Cohesion: 0.38
Nodes (6): Context, Modifier, MainScreen(), MenuButton(), SectionHeader(), sendDevNotification()

### Community 52 - "DownloadAllReceiver"
Cohesion: 0.33
Nodes (4): BroadcastReceiver, DownloadAllReceiver, Context, Intent

### Community 53 - ".downloadKakuyomuEpisodes"
Cohesion: 0.60
Nodes (3): IntRange, EpisodeInfo, com

### Community 56 - "NotificationType"
Cohesion: 0.50
Nodes (4): NotificationType, ERROR, INFO, UPDATE

### Community 57 - "RecentlyReadNovelsScreen"
Cohesion: 0.83
Nodes (3): LastReadNovelWithInfo, RecentlyReadNovelItem(), RecentlyReadNovelsScreen()

### Community 58 - "RecentNovelItem"
Cohesion: 0.83
Nodes (3): formatDate(), RecentlyUpdatedNovelsScreen(), RecentNovelItem()

### Community 59 - "SettingsMenuScreen.kt"
Cohesion: 0.67
Nodes (3): SettingsCategory, SettingsCategoryRow(), SettingsMenuScreen()

### Community 60 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 71 - "ReversedViewPager"
Cohesion: 0.18
Nodes (9): Deprecated, AttributeSet, Context, MotionEvent, Override, PagerAdapter, SuppressLint, ReversedViewPager (+1 more)

### Community 72 - "ImageCacheEntity"
Cohesion: 0.16
Nodes (3): ImageCacheDao, ImageCacheEntity, ImageCacheUtils

### Community 73 - "VjapVerticalTextView"
Cohesion: 0.15
Nodes (6): Modifier, VjapAppliedState, VjapVerticalTextView(), OnPageChangedListener, OnPageEndListener, OnReadyListener

### Community 74 - "DispView"
Cohesion: 0.22
Nodes (7): Bitmap, ImageView, DispView, DrawImageTask, Canvas, Handler, RotateAnimation

### Community 75 - "EnhancedHtmlRubyWebView"
Cohesion: 0.23
Nodes (10): applyRubyFixes(), convertPlainTextToHtml(), EnhancedHtmlRubyWebView(), EpisodeViewScreen(), isHtmlContent(), Modifier, sanitizeEpisodeHtml(), WebViewScrollInterface (+2 more)

### Community 78 - "カクヨムダウンロードプロトコル"
Cohesion: 0.20
Nodes (10): HTTP取得の再試行ロジック, URL構造, エピソードタイトル取得の優先順位, エピソード一覧取得の複数フォールバック, エピソード本文のエラーチェック, エピソード本文取得の優先順位, エラーハンドリング, カクヨムダウンロードプロトコル (+2 more)

### Community 79 - "CLAUDE.md"
Cohesion: 0.22
Nodes (9): CLAUDE.md, HTML構造の違い, R18作品判定ルール, WorkManagerによるバックグラウンド自動更新 (実装完了), 孤立エピソード検知・復元機能 (v1.9.0), 実装パターン, 🔨 最重要ルール - 新しいルールの追加プロセス, 短編小説のタイトル取得ルール (+1 more)

### Community 80 - "graphify reference: extra exports and benchmark"
Cohesion: 0.22
Nodes (8): graphify reference: extra exports and benchmark, Step 6b - Wiki (only if --wiki flag), Step 7 - Neo4j export (only if --neo4j or --neo4j-push flag), Step 7a - FalkorDB export (only if --falkordb or --falkordb-push flag), Step 7b - SVG export (only if --svg flag), Step 7c - GraphML export (only if --graphml flag), Step 7d - MCP server (only if --mcp flag), Step 8 - Token reduction benchmark (only if total_words > 5000)

### Community 81 - ".onCreate"
Cohesion: 0.32
Nodes (4): Bundle, ComponentActivity, MainActivity, OnBackPressedCallback

### Community 82 - "External Agent Policy"
Cohesion: 0.29
Nodes (6): Agy / Antigravity CLI, Codex, External Agent Policy, graphify, OpenCode(GLM), Required final report (all external agents)

### Community 83 - "Architecture Overview"
Cohesion: 0.29
Nodes (7): Architecture Overview, Back Navigation Implementation, Core Architecture, Database Schema, Development Guidelines, Filter and Sort Settings Persistence, Special Features

### Community 84 - "DatabaseExportManager"
Cohesion: 0.53
Nodes (3): DatabaseExportManager, ExportResult, Uri

### Community 85 - "graphify reference: query, path, explain"
Cohesion: 0.33
Nodes (5): For /graphify explain, For /graphify path, graphify reference: query, path, explain, Step 0 — Constrained query expansion (REQUIRED before traversal), Step 1 — Traversal

### Community 86 - "Key Components"
Cohesion: 0.40
Nodes (5): Application Entry Point, Data Layer, Key Components, Key Screens, Navigation

### Community 87 - "Important Patterns"
Cohesion: 0.40
Nodes (5): Data Access, Dependency Injection, Important Patterns, Navigation, Settings Management

### Community 88 - "テキストクリーンアップ処理"
Cohesion: 0.40
Nodes (5): HTMLエスケープ文字のデコード, Unicodeエスケープ文字のデコード, あらすじのクリーンアップ処理, テキストクリーンアップ処理, 本文のクリーンアップ処理

### Community 89 - "graphify reference: add a URL and watch a folder"
Cohesion: 0.50
Nodes (3): For /graphify add, For --watch, graphify reference: add a URL and watch a folder

### Community 90 - "graphify reference: commit hook and native CLAUDE.md integration"
Cohesion: 0.50
Nodes (3): For git commit hook, For native CLAUDE.md integration, graphify reference: commit hook and native CLAUDE.md integration

### Community 91 - "graphify reference: incremental update and cluster-only"
Cohesion: 0.50
Nodes (3): For --cluster-only, For --update (incremental re-extraction), graphify reference: incremental update and cluster-only

### Community 92 - "Development Commands"
Cohesion: 0.67
Nodes (3): Build and Run, Development Commands, Working Directory

## Knowledge Gaps
- **150 isolated node(s):** `Usage`, `What graphify is for`, `Step 0 - GitHub repos and multi-path merge (only if a URL or several paths)`, `Step 1 - Ensure graphify is installed`, `Step 2 - Detect files` (+145 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **26 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `NovelRepository` connect `NovelRepository` to `WebNovelReaderImportManager`, `.addNovelByUrl`, `.deleteNovelWithRelations`, `ImageCacheEntity`, `RegistrationQueueEntity`, `NovelReaderApplication.kt`, `DatabaseSyncManager`, `EnhancedHtmlRubyWebView`, `EpisodeEntity`, `MetadataUpdateManager`, `NovelDescEntity`, `NovelRepositoryTest`, `UpdateQueueEntity`, `ProcessingState`, `TempEpisodeDao`, `URLEntity`, `EpisodeMappingEntity`?**
  _High betweenness centrality (0.231) - this node is a cross-community bridge._
- **Why does `NovelDescEntity` connect `NovelDescEntity` to `WebNovelReaderImportManager`, `NovelRepository`, `.addNovelByUrl`, `NovelDescDaoTest`, `KakuyomuAdapter`, `.deleteNovelWithRelations`, `RecentNovelItem`, `NovelApiUtils`, `DatabaseSyncManager`, `MetadataUpdateManager`, `NovelRepositoryTest`, `UpdateQueueEntity`, `NovelSiteAdapter`, `RecentlyReadNovelsScreen`, `SortField`, `SyosetuAdapter`?**
  _High betweenness centrality (0.141) - this node is a cross-community bridge._
- **Why does `SettingsStore` connect `SettingsStore` to `WebNovelReaderImportManager`, `AutoUpdateWorker`, `SettingsStore.kt`, `ImageCacheEntity`, `NovelReaderApplication.kt`, `EnhancedHtmlRubyWebView`, `DatabaseSyncManager`, `.onCreate`, `MainScreen`, `.checkForNewRelease`, `UpdateQueueEntity`, `RecentlyReadNovelsScreen`, `SortField`, `NovelReaderApp`?**
  _High betweenness centrality (0.118) - this node is a cross-community bridge._
- **Are the 21 inferred relationships involving `SettingsStore` (e.g. with `.syncData()` and `.syncFromExternalDb()`) actually correct?**
  _`SettingsStore` has 21 INFERRED edges - model-reasoned connections that need verification._
- **Are the 3 inferred relationships involving `NovelDescEntity` (e.g. with `.syncNovelDescs()` and `.syncNovelDescs()`) actually correct?**
  _`NovelDescEntity` has 3 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Usage`, `What graphify is for`, `Step 0 - GitHub repos and multi-path merge (only if a URL or several paths)` to the rest of the system?**
  _150 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `WebNovelReaderImportManager` be split into smaller, more focused modules?**
  _Cohesion score 0.06826923076923076 - nodes in this community are weakly interconnected._