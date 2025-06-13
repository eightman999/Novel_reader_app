# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 🔨 最重要ルール - 新しいルールの追加プロセス

ユーザーから今回限りではなく常に対応が必要だと思われる指示を受けた場合：

1. 「これを標準のルールにしますか？」と質問する
2. YESの回答を得た場合、CLAUDE.mdに追加ルールとして記載する
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