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

## カクヨムダウンロードプロトコル

**必須**: カクヨムには公式APIが存在しないため、HTMLスクレイピングで情報を取得する

### レート制限とアクセス制御

```kotlin
// KakuyomuAdapter.kt での実装パターン
companion object {
    private const val RATE_LIMIT_DELAY_MS = 1000L  // 1秒（スクレイピング時の推奨間隔）
    private var lastAccessTime = 0L
}

private suspend fun applyRateLimit() {
    val elapsed = System.currentTimeMillis() - lastAccessTime
    if (elapsed < RATE_LIMIT_DELAY_MS) {
        delay(RATE_LIMIT_DELAY_MS - elapsed)
    }
    lastAccessTime = System.currentTimeMillis()
}
```

**重要なルール**:
- レート制限は**1秒間隔**（0.5秒や他の値は使用しない）
- 全てのHTTPリクエスト前に`applyRateLimit()`を呼び出す
- サーバー負荷軽減のため、この間隔を必ず守る

### エピソード本文取得の優先順位

```kotlin
// エピソード本文取得パターン（KakuyomuAdapter.kt）
// パターン1: 両方のクラスを持つdiv（最優先）
val bodyElement1 = doc.select("div.widget-episodeBody.js-episode-body")
if (bodyElement1.isNotEmpty()) {
    episodeBody = bodyElement1.html()
}

// パターン2: widget-episodeBody のみ（フォールバック）
if (episodeBody.isEmpty()) {
    val bodyElement2 = doc.select("div.widget-episodeBody")
    if (bodyElement2.isNotEmpty()) {
        episodeBody = bodyElement2.html()
    }
}

// パターン3: js-episode-body のみ（さらにフォールバック）
if (episodeBody.isEmpty()) {
    val bodyElement3 = doc.select("div.js-episode-body")
    // ...
}
```

**重要なルール**:
- `div.widget-episodeBody.js-episode-body`（両方のクラス）を**最優先**で使用
- 複数のフォールバックパターンを用意し、確実な取得を保証
- パターンの優先順位を守る

### エピソードタイトル取得の優先順位

```kotlin
// エピソードタイトル取得パターン（NovelApiUtils.kt等）
val title = doc.select("header#contentMain-header").text()
    .ifEmpty { doc.select("h1").first()?.text() ?: "" }
    .ifEmpty { "第${episodeNo}話" }
```

**重要なルール**:
- `header#contentMain-header`を**最優先**で使用
- `h1`タグは第2フォールバック
- 最後のフォールバックとして「第X話」形式を生成

### HTTP取得の再試行ロジック

```kotlin
// HTTP取得の再試行実装パターン（KakuyomuAdapter.kt）
private suspend fun performHttpRequest(urlString: String, maxRetries: Int = 3): String {
    var lastException: Exception? = null

    for (attempt in 1..maxRetries) {
        try {
            // HTTP接続処理
            when (responseCode) {
                HttpURLConnection.HTTP_OK -> return html
                HttpURLConnection.HTTP_NOT_FOUND -> throw Exception("HTTP 404")
                HttpURLConnection.HTTP_FORBIDDEN -> throw Exception("HTTP 403")
                else -> throw Exception("HTTP error: $responseCode")
            }
        } catch (e: SocketTimeoutException) {
            if (attempt < maxRetries) {
                delay(1000L * attempt)  // 指数バックオフ: 1秒、2秒、3秒
            }
        } catch (e: UnknownHostException) {
            if (attempt < maxRetries) {
                delay(1000L * attempt)
            }
        } catch (e: ConnectException) {
            if (attempt < maxRetries) {
                delay(1000L * attempt)
            }
        }
    }

    throw lastException ?: Exception("HTTP取得失敗")
}
```

**重要なルール**:
- 最大**3回**の再試行を実装
- 再試行時は**指数バックオフ**（1秒、2秒、3秒）を使用
- `SocketTimeoutException`、`UnknownHostException`、`ConnectException`は再試行対象
- HTTPエラー（404、403等）は即座にスロー（再試行しない）
- 詳細なログ出力で問題の特定を容易にする

### テキストクリーンアップ処理

**必須**: HTMLから取得したテキストは必ずクリーンアップ処理を行う

#### HTMLエスケープ文字のデコード

```kotlin
// HTMLエスケープ文字のデコード（KakuyomuAdapter.kt）
private fun decodeHtmlEntities(text: String): String {
    var decoded = text
    decoded = decoded.replace("&lt;", "<")
    decoded = decoded.replace("&gt;", ">")
    decoded = decoded.replace("&quot;", "\"")
    decoded = decoded.replace("&nbsp;", " ")
    decoded = decoded.replace("&yen;", "\\")
    decoded = decoded.replace("&brvbar;", "|")
    decoded = decoded.replace("&copy;", "©")
    // &amp; は最後に処理（他のエスケープ文字に影響しないように）
    decoded = decoded.replace("&amp;", "&")
    return decoded
}
```

#### Unicodeエスケープ文字のデコード

```kotlin
// 数値エスケープ文字のデコード（&#xxxx; 形式）
private fun decodeNumericEntities(text: String): String {
    var decoded = text

    // 16進数形式: &#x????;
    val hexPattern = Regex("&#x([0-9A-Fa-f]+);")
    decoded = hexPattern.replace(decoded) { matchResult ->
        try {
            val codePoint = matchResult.groupValues[1].toInt(16)
            String(Character.toChars(codePoint))
        } catch (e: Exception) {
            "？"  // デコード失敗時は？に置換
        }
    }

    // 10進数形式: &#????;
    val decPattern = Regex("&#([0-9]+);")
    decoded = decPattern.replace(decoded) { matchResult ->
        try {
            val codePoint = matchResult.groupValues[1].toInt(10)
            String(Character.toChars(codePoint))
        } catch (e: Exception) {
            "？"  // デコード失敗時は？に置換
        }
    }

    return decoded
}

// Unicodeエスケープ文字のデコード（\uxxxx 形式）
private fun decodeUnicodeEscapes(text: String): String {
    val pattern = Regex("\\\\u([0-9A-Fa-f]{4})")
    return pattern.replace(text) { matchResult ->
        try {
            val codePoint = matchResult.groupValues[1].toInt(16)
            String(Character.toChars(codePoint))
        } catch (e: Exception) {
            "？"  // デコード失敗時は？に置換
        }
    }
}
```

#### 本文のクリーンアップ処理

```kotlin
// エピソード本文のクリーンアップ（KakuyomuAdapter.kt）
private fun cleanupEpisodeBody(html: String): String {
    var cleaned = html

    // 1. 改行タグを実際の改行に変換（<br />、<br>、<br/>）
    cleaned = cleaned.replace(Regex("<br\\s*/?>"), "\n")

    // 2. HTMLエスケープ文字のデコード
    cleaned = decodeHtmlEntities(cleaned)

    // 3. Unicodeエスケープ文字のデコード（&#xxxx; 形式）
    cleaned = decodeNumericEntities(cleaned)

    // 4. Unicodeエスケープ文字のデコード（\uxxxx 形式）
    cleaned = decodeUnicodeEscapes(cleaned)

    // 5. 余計なタグの除去（空の <p> タグなど）
    cleaned = cleaned.replace(Regex("<p[^>]*>\\s*</p>"), "")

    // 6. 各行の先頭にある半角空白を除去
    cleaned = cleaned.lines().joinToString("\n") { line ->
        line.trimStart(' ')
    }

    return cleaned
}
```

#### あらすじのクリーンアップ処理

```kotlin
// あらすじのクリーンアップ（KakuyomuAdapter.kt）
private fun cleanupSynopsis(text: String): String {
    var cleaned = text

    // 1. あらすじ部分の"が\"とエスケープされているため"に戻す
    cleaned = cleaned.replace("\\\"", "\"")

    // 2. 改行が\n表記となっている場合は実際の改行に変換
    cleaned = cleaned.replace("\\n", "\n")

    // 3. HTMLエスケープ文字のデコード
    cleaned = decodeHtmlEntities(cleaned)

    // 4. Unicodeエスケープ文字のデコード（&#xxxx; 形式）
    cleaned = decodeNumericEntities(cleaned)

    // 5. Unicodeエスケープ文字のデコード（\uxxxx 形式）
    cleaned = decodeUnicodeEscapes(cleaned)

    // 6. 前書きの最後にある"…続きを読む"を削除する
    cleaned = cleaned.replace("…続きを読む", "")

    // 7. 前後の空白を除去
    cleaned = cleaned.trim()

    return cleaned
}
```

**重要なルール**:
- **タイトル、作者名、エピソードタイトル**: `cleanupText()` を使用
- **あらすじ**: `cleanupSynopsis()` を使用（エスケープされた引用符や改行の処理を含む）
- **エピソード本文**: `cleanupEpisodeBody()` を使用（改行タグの変換や余計なタグの除去を含む）
- HTMLから取得したすべてのテキストに適用すること
- デコード処理の順序を守る：HTMLエスケープ → 数値エスケープ → Unicodeエスケープ

### URL構造

```kotlin
// カクヨムのURL構造
val workUrl = "https://kakuyomu.jp/works/{workId}"
val episodeUrl = "https://kakuyomu.jp/works/{workId}/episodes/{episodeId}"
```

**重要なルール**:
- 作品IDとエピソードIDは独立した19桁の数値
- 連番ではないため、目次から全エピソードIDを取得する必要がある
- Pseudo-Ncode形式（`KK-{workId}`）でデータベースに格納

### エラーハンドリング

**必須**: 全てのカクヨム関連処理でエラーハンドリングを実装

- ネットワークエラー時は再試行ロジックを適用
- HTMLパースエラー時は複数のフォールバックパターンを試行
- 取得失敗時は詳細なログを出力し、空データまたはnullを返す
- ユーザーには適切なエラーメッセージを表示

このルールは全てのカクヨム関連処理（小説情報取得、エピソード一覧取得、エピソード本文取得、更新確認）で統一して適用すること。
