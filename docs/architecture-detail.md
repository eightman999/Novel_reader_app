# Architecture Detail

CLAUDE.md の概要を補足する詳細リファレンス。

## Directory Layout
```
Novel_reader_app/
├── Novel_reader/                    # Main Android project
│   ├── app/
│   │   ├── build.gradle.kts        # App build config (version は build.gradle.kts が正)
│   │   └── src/
│   │       ├── main/java/com/shunlight_library/novel_reader/
│   │       │   ├── *.kt            # Top-level screens and application
│   │       │   ├── data/
│   │       │   │   ├── adapter/    # Site-specific adapters (Syosetu, Kakuyomu)
│   │       │   │   ├── dao/        # Room DAOs (9 total)
│   │       │   │   ├── database/   # NovelDatabase.kt (v17)
│   │       │   │   ├── entity/     # Room entities (9 total) + EpisodeMeta DTO
│   │       │   │   ├── repository/ # NovelRepository.kt
│   │       │   │   └── sync/       # Database sync utilities
│   │       │   ├── api/            # API utilities (NovelApiUtils)
│   │       │   ├── navigation/     # NavigationManager
│   │       │   ├── ui/             # UI components and screens
│   │       │   │   ├── components/ # Reusable UI components (incl. VjapVerticalTextView)
│   │       │   │   └── theme/      # App theme
│   │       │   ├── utils/          # Utility classes
│   │       │   ├── worker/         # WorkManager workers (AutoUpdate)
│   │       │   ├── service/        # Background services (UpdateService, FCM)
│   │       │   ├── receiver/       # Broadcast receivers
│   │       │   ├── manager/        # RegistrationQueueManager (download queue)
│   │       │   └── metadata/       # Metadata management
│   │       ├── test/               # Unit tests
│   │       └── androidTest/        # Instrumented tests
│   └── build.gradle.kts            # Project build config
├── CLAUDE.md                       # AI assistant guide (this project)
├── AGENTS.md                       # Additional AI agent guidelines
├── README.md                       # Project documentation
├── STRUCTURE.md                    # Project structure summary
├── FUTURE_IMPROVEMENTS.md          # Planned improvements
├── WORK_LOG.md                     # Development work log
├── 統合テスト方法.md                # Integration testing guide
└── release/                        # Release APKs
```

## Key Files by Function

### Application Core
- `NovelReaderApplication.kt` - Application class, singleton repository/database access
- `MainActivity.kt` - Single Activity with Compose, navigation, back handling
- `AppInfo.kt` - Application information

### Data Layer (9 Entities, 9 DAOs, 1 Repository)
- **Entities**: NovelDescEntity, EpisodeEntity, LastReadNovelEntity, UpdateQueueEntity, URLEntity, ImageCacheEntity, EpisodeMappingEntity, RegistrationQueueEntity, TempEpisodeEntity
- **DTO**: EpisodeMeta (本文なし射影 + body_empty フラグ、エピソード一覧の軽量ロード用)
- **DAOs**: Matching DAOs for each entity (9 total)
- **Database**: NovelDatabase.kt (v17 with full migration chain, exportSchema=true)
- **Repository**: NovelRepository.kt (single source of data access, withTransaction 対応)

詳細なテーブル定義・マイグレーション履歴は [db-schema.md](db-schema.md) 参照。

### Adapters (Multi-Site Support)
- `NovelSiteAdapter.kt` - Interface
- `SyosetuAdapter.kt` - Syosetu implementation (YAML API)
- `KakuyomuAdapter.kt` - Kakuyomu implementation (HTML scraping)
- `NovelSiteAdapterFactory.kt` - Factory pattern

**Core Interface**: `NovelSiteAdapter`
- Defines common operations: `getSiteType()`, `getSiteName()`, `generateWebUrl()`, `fetchNovelMetadata()`, `fetchEpisodeList()`, `fetchEpisodeContent()`
- Site type constants: `SITE_TYPE_SYOSETU = 1`, `SITE_TYPE_KAKUYOMU = 2`

```kotlin
// In NovelDescEntity
val site_type: Int = 1  // 1=Syosetu, 2=Kakuyomu

// Usage
val adapter = NovelSiteAdapterFactory.createAdapter(novel.site_type)
val episodes = adapter.fetchEpisodeList(novel.ncode)
```

API/スクレイピング仕様の詳細は [api-spec.md](api-spec.md)（なろう）、[kakuyomu-protocol.md](kakuyomu-protocol.md)（カクヨム）参照。

### Screens (9 total)
- `NovelListScreen.kt` - Advanced filtering/sorting with enum-based configuration and persistent settings
- `EpisodeListScreen.kt` - Episode list with reading progress and bookmark management
- `EpisodeViewScreen.kt` - WebView-based reading with JavaScript interface for progress tracking and reading rate
- `WebViewScreen.kt` - Novel site browsing with R18 content support
- `RecentlyReadNovelsScreen.kt` - Recently read novels history
- `RecentlyUpdatedNovelsScreen.kt` - Recently updated novels with update notifications
- `UpdateInfoScreen.kt` - Update information and notification management
- `SettingsScreen.kt` - Application settings with DataStore persistence
- `SettingsMenuScreen.kt` - Settings hub navigating to detailed settings sections
- `DatabaseSyncActivity.kt` - External SQLite database synchronization (in `ui/`, +1 sync activity)

### Navigation
- `NavigationManager.kt` - Custom navigation with sealed class hierarchy and back stack management
- Main flow: Main → NovelList → EpisodeList → EpisodeView
- `replaceCurrent(screen)` で前後話ナビ時のバックスタック無限蓄積を回避（v2.0.20 H6）

### Utilities and Helpers

#### Core Utilities
- **`Base62Converter`** - Base62 encoding/decoding for Kakuyomu work IDs
- **`PseudoNcodeGenerator`** - Generates pseudo-ncodes for Kakuyomu (KK-{Base62})
- **`AppLogger`** - Centralized logging with BuildConfig-based enable/disable
- **`FontUtils`** - Custom font loading and CSS generation for WebView
- **`ImageCacheUtils`** - Image caching management for novel covers
- **`NovelUpdateCoordinator`** - Coordinates novel update operations
- **`ReleaseUtils`** - Release-related utilities
- **`VjapTextConverter`** - Vertical text (縦書き) conversion for VjapVerticalTextView

#### Download Queue Management
- **`RegistrationQueueManager`** - Manages the download/registration queue; cancels jobs with `cancelAndJoin` before clearing temp data (orphan temp_episodes 防止)

#### Synchronization and Database
- **`DatabaseSyncManager`** - SQLite database synchronization
- **`ImprovedDatabaseSyncManager`** - Enhanced sync with better error handling
- **`DatabaseExportManager`** - Database export functionality
- **`DatabaseSchemaAnalyzer`** - Schema analysis for sync operations
- **`ExternalSQLiteHelper`** - External SQLite database operations

#### Metadata and Updates
- **`MetadataUpdateManager`** - Novel metadata update management
- **`NotificationStore`** - App notification persistence and management
- **`NotificationData`** - Notification data models

### Background Work
- `AutoUpdateWorker.kt` - WorkManager worker for scheduled updates
- `AutoUpdateScheduler.kt` - Update scheduling logic
- `UpdateService.kt` - Update service
- `MyFirebaseMessagingService.kt` - FCM notification display; a `novel_update` notification opens a registered novel's episode list. Device token registration with a server is not configured.

自動更新の実行フローの詳細は [auto-update-flow.md](auto-update-flow.md) 参照。

## Testing Infrastructure

### Unit Tests (`test/`)
- **`Base62ConverterTest`** - Base62 encoding/decoding tests
- **`PseudoNcodeGeneratorTest`** - Pseudo-ncode generation tests
- **`KakuyomuTextCleanupTest`** - Text cleanup function tests

### Instrumented Tests (`androidTest/`)
- **DAO Tests**: `NovelDescDaoTest`, `EpisodeDaoTest`
- **Repository Tests**: `NovelRepositoryTest` - Repository layer integration tests
- **Database Tests**: `MigrationTest` - Database migration validation tests

**Test Libraries**: JUnit 4, Coroutines Test, Google Truth, Room Testing, Work Testing

## Implementation Patterns (詳細コード例)

### Back Navigation Implementation
**必須**: Android標準のナビゲーションバーのバックハンドラーを使用する

```kotlin
// MainActivity.kt での実装パターン
class MainActivity : ComponentActivity() {
    private lateinit var navigationManager: NavigationManager
    private lateinit var backPressedCallback: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NavigationManager は ViewModel に保持（回転・ダークモード切替で画面が初期化されない）
        val navigationViewModel: NavigationViewModel by viewModels()
        navigationManager = navigationViewModel.navigationManager
        // プロセス再生成時は onSaveInstanceState で保存した画面スタックを復元する
        if (!navigationViewModel.restored) {
            savedInstanceState?.getBundle(KEY_NAVIGATION_STATE)?.let { navigationManager.restoreState(it) }
            navigationViewModel.restored = true
        }

        // OnBackPressedCallbackの設定
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!navigationManager.navigateBack()) {
                    finish()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backPressedCallback)

        // 以下setContent...
    }
}
```

**重要**:
- deprecated な `onBackPressed()` は使用しない
- `OnBackPressedDispatcher` を使用してシステムバックボタンとの統合を行う
- NavigationManager と連携して適切なバック処理を実装する
- 画面スタックは `NavigationManager.saveState()` / `restoreState()` で Bundle（JSON）に保存・復元する。`Screen` を追加したら `ScreenCodec` にも追加する
- `navigateBackTo(screen)` は対象がスタックに無い場合スタックを変更せず false を返す。呼び出し側は false 時のフォールバック（1画面戻る等）を実装する

### 読書画面（EpisodeViewScreen）
- 読書位置は横書き・縦書きとも追跡済みの値をアプリ全体のスコープで保存し、ON_STOP（ホーム・画面オフ・構成変更）でも保存する
- 設定の読み込みが終わるまで本文を描画しない（既定値の横書きで描画後に縦書きへ差し替わるのを防ぐ）
- 本文内検索: 横書きは `WebView.findAllAsync/findNext`、縦書きは vjap の本文（青空文庫形式）を検索し該当ページへ移動する（ルビ記法をまたぐ語は検索対象外）
- 進捗表示（`showReadingProgress`）: 縦書きは「ページ/総ページ（％）」、横書きは読了率（％）。ページ移動スライダーは縦書きが右端を先頭にしたページ番号、横書きが読了率
- 行間（`readerLineHeight`、1.2〜2.8、既定1.8）: 横書きは CSS `line-height`、縦書きは行送り倍率として +0.2 して適用（ルビ領域の確保。vjap側で1.5〜3.0に丸める）
- 既定背景はテーマに追従する（ダークテーマ時は背景・文字色ともテーマ色）
- 本文の挿絵は https/http/data に加え、file:// はアプリ専用領域、content:// は SAF ストレージプロバイダのみ許可する

### Filter and Sort Settings Persistence
**必須**: リスト画面のフィルター・ソート設定は永続化し、次回起動時に復元する

```kotlin
// SettingsStore での実装パターン
data class NovelListFilterSettings(
    val sortField: String = "LAST_UPDATE_DATE",
    val sortDirection: String = "DESCENDING",
    val minRating: Int = 0,
    val maxRating: Int = 5,
    val hideRating5WithNoEpisodes: Boolean = false,
    val showCompleted: Boolean = true,
    val showOngoing: Boolean = true
)

// 設定の保存・読み込みメソッド
suspend fun getNovelListFilterSettings(): NovelListFilterSettings { ... }
suspend fun saveNovelListFilterSettings(settings: NovelListFilterSettings) { ... }
```

```kotlin
// Screen での実装パターン
@Composable
fun NovelListScreen() {
    val settingsStore = remember { SettingsStore(context) }
    var sortField by remember { mutableStateOf(SortField.LAST_UPDATE_DATE) }
    var filterSettings by remember { mutableStateOf(FilterSettings()) }

    // 設定の自動保存
    fun saveCurrentSettings() {
        scope.launch {
            settingsStore.saveNovelListFilterSettings(...)
        }
    }

    // 起動時の設定読み込み
    LaunchedEffect(key1 = true) {
        val saved = settingsStore.getNovelListFilterSettings()
        sortField = SortField.valueOf(saved.sortField)
        // ...
    }

    // 設定変更時の自動保存
    LaunchedEffect(sortField, filterSettings) {
        saveCurrentSettings()
    }
}
```

**重要**:
- DataStoreを使用してフィルター・ソート設定を永続化
- 画面起動時に保存された設定を自動読み込み
- 設定変更時（ダイアログ適用、ボタンクリック等）に即座に保存
- 例外処理を含めて enum 値の安全な復元を行う
