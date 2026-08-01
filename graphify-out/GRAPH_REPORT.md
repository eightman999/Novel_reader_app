# Graph Report - .  (2026-08-02)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 1292 nodes · 2209 edges · 71 communities (51 shown, 20 thin omitted)
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 112 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `dbd4cc72`
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
- NovelListScreen.kt
- NovelReaderApp
- MigrationTest
- SyosetuAdapter
- AutoUpdateScheduler
- EpisodeMappingEntity
- ErrorLogStore
- DatabaseExportManager
- AutoUpdateWorker
- NotificationStore
- FontUtils
- SettingsStore.kt
- .deleteNovelWithRelations
- DatabaseSchemaAnalyzer
- ReversedSeekBar.java
- NovelReaderApplication.kt
- ExternalSQLiteHelper
- SortField
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

## Communities (71 total, 20 thin omitted)

### Community 0 - "VTextLayout"
Cohesion: 0.05
Nodes (34): Bitmap, Deprecated, ImageView, Modifier, VjapAppliedState, VjapVerticalTextView(), AttributeSet, Context (+26 more)

### Community 1 - "WebNovelReaderImportManager"
Cohesion: 0.07
Nodes (27): ByteArray, ColumnInfo, DatabaseSyncUtils, ExpectedSchema, Cursor, ImprovedDatabaseSyncManager, SQLiteDatabase, Uri (+19 more)

### Community 2 - "NovelRepository"
Cohesion: 0.05
Nodes (4): com, Flow, StateFlow, NovelRepository

### Community 3 - "VTextView"
Cohesion: 0.09
Nodes (13): CharSetting, CurrentState, AttributeSet, Canvas, Context, Handler, Override, Typeface (+5 more)

### Community 4 - "Screen"
Cohesion: 0.05
Nodes (34): applyRubyFixes(), convertPlainTextToHtml(), EnhancedHtmlRubyWebView(), EpisodeViewScreen(), isHtmlContent(), Modifier, sanitizeEpisodeHtml(), WebViewScrollInterface (+26 more)

### Community 5 - "NovelDescDaoTest"
Cohesion: 0.06
Nodes (10): NovelDescDaoTest, ImageCacheDao, getDatabase(), Context, SupportSQLiteDatabase, migrate(), NovelDatabase, ImageCacheEntity (+2 more)

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

### Community 17 - "EpisodeEntity"
Cohesion: 0.11
Nodes (3): EpisodeDao, Flow, EpisodeEntity

### Community 18 - "DownloadQueueViewModel"
Cohesion: 0.11
Nodes (16): Color, MutableStateFlow, DownloadQueueScreen(), EmptyQueueMessage(), QueueItem(), StatusBadge(), StatusSummaryRow(), DownloadQueueViewModel (+8 more)

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

### Community 26 - "NovelListScreen.kt"
Cohesion: 0.14
Nodes (17): FilterSettings, com, NovelListItem(), NovelListScreen(), NovelWithReadInfo, SearchField, AUTHOR, NCODE (+9 more)

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

### Community 33 - "DatabaseExportManager"
Cohesion: 0.18
Nodes (9): DatabaseExportManager, ExportResult, Uri, DatabaseSyncActivity, DatabaseSyncScreen(), Bundle, ComponentActivity, AppSettings (+1 more)

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

### Community 44 - "SortField"
Cohesion: 0.20
Nodes (10): SortField, AUTHOR, LAST_CHECKED_AT, LENGTH, NCODE, REGISTERED_AT, TITLE, TOTAL_EP (+2 more)

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

## Knowledge Gaps
- **70 isolated node(s):** `AppInfo`, `UPDATE`, `REDOWNLOAD`, `FIX_ERRORS`, `CHECK_REVISION` (+65 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **20 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `NovelRepository` connect `NovelRepository` to `WebNovelReaderImportManager`, `.addNovelByUrl`, `NovelDescDaoTest`, `Screen`, `.deleteNovelWithRelations`, `RegistrationQueueEntity`, `NovelReaderApplication.kt`, `DatabaseSyncManager`, `EpisodeEntity`, `MetadataUpdateManager`, `NovelDescEntity`, `NovelRepositoryTest`, `UpdateQueueEntity`, `ProcessingState`, `TempEpisodeDao`, `URLEntity`, `EpisodeMappingEntity`?**
  _High betweenness centrality (0.265) - this node is a cross-community bridge._
- **Why does `NovelDescEntity` connect `NovelDescEntity` to `WebNovelReaderImportManager`, `NovelRepository`, `.addNovelByUrl`, `NovelDescDaoTest`, `KakuyomuAdapter`, `.deleteNovelWithRelations`, `RecentNovelItem`, `NovelApiUtils`, `DatabaseSyncManager`, `MetadataUpdateManager`, `NovelRepositoryTest`, `UpdateQueueEntity`, `NovelSiteAdapter`, `RecentlyReadNovelsScreen`, `NovelListScreen.kt`, `SyosetuAdapter`?**
  _High betweenness centrality (0.175) - this node is a cross-community bridge._
- **Why does `SettingsStore` connect `SettingsStore` to `WebNovelReaderImportManager`, `AutoUpdateWorker`, `Screen`, `NovelDescDaoTest`, `SettingsStore.kt`, `NovelReaderApplication.kt`, `DatabaseSyncManager`, `MainScreen`, `.checkForNewRelease`, `UpdateQueueEntity`, `RecentlyReadNovelsScreen`, `NovelListScreen.kt`, `NovelReaderApp`?**
  _High betweenness centrality (0.150) - this node is a cross-community bridge._
- **Are the 21 inferred relationships involving `SettingsStore` (e.g. with `.syncData()` and `.syncFromExternalDb()`) actually correct?**
  _`SettingsStore` has 21 INFERRED edges - model-reasoned connections that need verification._
- **Are the 3 inferred relationships involving `NovelDescEntity` (e.g. with `.syncNovelDescs()` and `.syncNovelDescs()`) actually correct?**
  _`NovelDescEntity` has 3 INFERRED edges - model-reasoned connections that need verification._
- **What connects `AppInfo`, `UPDATE`, `REDOWNLOAD` to the rest of the system?**
  _70 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `VTextLayout` be split into smaller, more focused modules?**
  _Cohesion score 0.05191146881287726 - nodes in this community are weakly interconnected._