# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 🔨 最重要ルール - 新しいルールの追加プロセス

ユーザーから今回限りではなく常に対応が必要だと思われる指示を受けた場合：

1. 「これを標準のルールにしますか？」と質問する
2. YESの回答を得た場合、CLAUDE.md及びAGENTS.mdに追加ルールとして記載する
3. 以降は標準ルールとして常に適用する

このプロセスにより、プロジェクトのルールを継続的に改善していきます。

## Development Commands

### Build and Run
```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean

# Install debug APK
./gradlew installDebug
```

### Working Directory
All Gradle commands should be run from the `Novel_reader/` directory.

## Architecture Overview

This is an Android novel reader application built with modern Android architecture patterns:

### Core Architecture
- **Pattern**: Clean Architecture + MVVM + Repository Pattern
- **UI**: Jetpack Compose with single Activity pattern
- **Database**: Room with migration support (currently v5)
- **Navigation**: Custom NavigationManager with screen stack
- **Background Work**: WorkManager for scheduled updates
- **State**: Flow-based reactive programming

### Key Components

#### Application Entry Point
- `NovelReaderApplication.kt` - Application class with singleton pattern for global database/repository access
- `MainActivity.kt` - Single Activity hosting all Compose screens

#### Data Layer
- `NovelDatabase.kt` - Room database with 5 tables and proper migrations
- `NovelRepository.kt` - Single repository managing all data access via DAOs
- Entities: NovelDescEntity, EpisodeEntity, LastReadNovelEntity, UpdateQueueEntity, URLEntity

#### Navigation
- `NavigationManager.kt` - Custom navigation with sealed class hierarchy and back stack management
- Main flow: Main → NovelList → EpisodeList → EpisodeView

#### Key Screens
- `NovelListScreen.kt` - Advanced filtering/sorting with enum-based configuration
- `EpisodeViewScreen.kt` - WebView-based reading with JavaScript interface for progress tracking
- `WebViewScreen.kt` - Novel site browsing with R18 content support

### Important Patterns

#### Dependency Injection
Manual DI via Application singleton with lazy initialization:
```kotlin
val repository = NovelReaderApplication.instance.repository
val database = NovelReaderApplication.instance.database
```

#### Data Access
- Always use Repository, never access DAOs directly
- Use Flow for reactive read operations
- Use suspend functions for write operations

#### Navigation
Use NavigationManager for consistent navigation:
```kotlin
navigationManager.navigateTo(NavigationScreen.EpisodeList(novelId))
```

#### Settings Management
Use `SettingsStore` for persistent configuration with DataStore.

### Database Schema
- `novels_descs` - Novel metadata with R18 support
- `episodes` - Episode content with reading progress and bookmarks
- `last_read_novels` - Reading history tracking
- `update_queue` - Update notifications
- `url_entity` - API/Web URLs with R18 site support

### Special Features
- Custom font loading and CSS generation for WebView
- Ruby text (furigana) support for Japanese novels
- Reading progress tracking via JavaScript bridge
- Background update scheduling with WorkManager
- Database synchronization with external SQLite files
- R18 content handling with separate site configurations

### Development Guidelines
1. Always create Room migrations for schema changes
2. Use proper Compose state management with state hoisting
3. Follow the Repository pattern for all data operations
4. Use NavigationManager for navigation to maintain proper back stack
5. Handle R18 content appropriately with dialog-based site selection
6. Maintain reading progress and bookmark functionality in EpisodeViewScreen

#### Back Navigation Implementation
**必須**: Android標準のナビゲーションバーのバックハンドラーを使用する

```kotlin
// MainActivity.kt での実装パターン
class MainActivity : ComponentActivity() {
    private lateinit var navigationManager: NavigationManager
    private lateinit var backPressedCallback: OnBackPressedCallback
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // NavigationManager初期化
        navigationManager = NavigationManager()
        
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

#### Filter and Sort Settings Persistence
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

## 自動更新機能の実装

### WorkManagerによるバックグラウンド自動更新 (実装完了)

**概要**: 設定した時刻に自動でバックグラウンド更新を実行し、システム通知とアプリ内通知で結果を通知する機能

**実装ファイル**:
- `AutoUpdateWorker.kt` - WorkManagerによるバックグラウンド処理
- `AutoUpdateScheduler.kt` - 自動更新の時刻スケジュール管理
- `NotificationData.kt` & `NotificationStore.kt` - アプリ内通知管理
- `NotificationDialog.kt` - 通知一覧UI
- `MainActivity.kt` - メイン画面への通知機能統合
- `AndroidManifest.xml` - バックグラウンド実行権限

**主要機能**:
1. **バックグラウンド自動更新**: 24時間間隔で指定時刻に実行
2. **システム通知**: 更新結果をスマホの通知で即座表示
3. **アプリ内通知**: 詳細な更新履歴と管理機能
4. **通知バッジ**: メイン画面に未読通知数表示
5. **権限管理**: WAKE_LOCK, SCHEDULE_EXACT_ALARM等の適切な権限設定

**効果**: 
- アプリ未起動・画面OFF時でもバックグラウンドで更新確認
- 「新規X作品、更新Y作品」の詳細通知
- 更新履歴の永続化と管理
- 設定変更時の即座反映

## R18作品判定ルール

**必須**: 小説のR18判定は`rating`フィールドで行う

```kotlin
// R18判定の実装パターン
val isR18 = novel.rating == 1

// APIエンドポイント選択（YAML形式）
val apiUrl = if (isR18) {
    "https://api.syosetu.com/novel18api/api/?of=t-w-ga-s-ua&ncode=$ncode&gzip=5"
} else {
    "https://api.syosetu.com/novelapi/api/?of=t-w-ga-s-ua&ncode=$ncode&gzip=5"
}

// WebサイトURL選択
val webUrl = if (isR18) {
    "https://novel18.syosetu.com/$ncode/"
} else {
    "https://ncode.syosetu.com/$ncode/"
}
```

**重要なルール**:
- **rating = 1** → R18サイト（novel18.syosetu.com）
- **rating = 2** → 一般サイト（ncode.syosetu.com）
- R18作品の更新確認・閲覧は専用APIとサイトを使用
- 一般作品の更新確認・閲覧は通常APIとサイトを使用

このルールは全てのAPI呼び出し、URL生成、WebView表示で統一して適用すること。

## 短編小説のタイトル取得ルール

**必須**: 短編小説（noveltype=2）と連載小説（noveltype=1）の両方でタイトルを正しく取得する

### HTML構造の違い

- **連載小説**: `<h1 class="p-novel__title p-novel__title--rensai">タイトル</h1>`
- **短編小説**: `<h1 class="p-novel__title">タイトル</h1>` （`--rensai`クラスなし）

### 実装パターン

```kotlin
// タイトル取得時のフォールバック処理（NovelApiUtils.kt等）
val title = doc.select("h1.p-novel__title.p-novel__title--rensai").text()
    .ifEmpty { doc.select("h1.p-novel__title").text() }
```

**重要なルール**:
- 連載小説専用のCSSセレクタ（`p-novel__title--rensai`）だけでは短編小説のタイトルが取得できない
- 必ずフォールバック処理を実装し、汎用セレクタ（`p-novel__title`）も試す
- タイトルが空の場合の処理を適切に行う
- 短編小説の場合、取得したタイトルを`e_title`フィールドに格納する

このルールはエピソード取得、WebViewでの表示、データベース保存などで統一して適用すること。
