# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 📖 Quick Reference

### Current State (2026-07-01)
- **Version**: 2.0.20 (versionCode: 220)
- **Database**: Version 17 with 9 tables + performance indices
- **Supported Sites**: Syosetu (なろう小説) + Kakuyomu (カクヨム)
- **Architecture**: Clean + MVVM + Repository + Adapter Pattern
- **Testing**: Unit tests + Instrumented tests available
- **Performance**: Optimized for 1000+ novels, 320000+ episodes

### Key Numbers
- **Entities**: 9 (NovelDesc, Episode, LastReadNovel, UpdateQueue, URL, ImageCache, EpisodeMapping, RegistrationQueue, TempEpisode)
- **DAOs**: 9 (matching entities)
- **Screens**: 9 main screens (incl. SettingsMenuScreen hub) + 1 sync activity (DatabaseSyncActivity)
- **Adapters**: 2 (SyosetuAdapter, KakuyomuAdapter)
- **Migrations**: v1→v17 (16 migrations)
- **Database Indices**: 20+ (including composite indices for performance)

### Common Tasks
- **Add new feature**: Update version in build.gradle.kts, write Japanese commit message
- **Database change**: Create new migration, update version to v18
- **Add new site**: Implement NovelSiteAdapter interface, add to factory
- **Fetching episodes**: Always use incremental saving (fetch 1 → save → fetch 2 → save)
- **Site detection**: Check `novel.site_type` (1=Syosetu, 2=Kakuyomu)
- **Logging**: Use `AppLogger` (respects BuildConfig.ENABLE_LOGGING)

### Quick Links to Documentation
- Architecture details → [Architecture Overview](#architecture-overview)
- Database schema → [Database Schema (Version 17)](#database-schema-version-17)
- Performance optimization → [Performance Optimizations](#performance-optimizations)
- Multi-site support → [Multi-Site Architecture](#multi-site-architecture)
- API specifications → [なろう小説API仕様](#なろう小説api仕様), [カクヨムダウンロードプロトコル](#カクヨムダウンロードプロトコル)

---

## 🔨 最重要ルール - ドキュメント更新プロセス

### 新しいルールの追加プロセス

ユーザーから今回限りではなく常に対応が必要だと思われる指示を受けた場合：

1. 「これを標準のルールにしますか？」と質問する
2. YESの回答を得た場合、CLAUDE.md及びAGENTS.mdに追加ルールとして記載する
3. 以降は標準ルールとして常に適用する

このプロセスにより、プロジェクトのルールを継続的に改善していきます。

### 仕様変更時のCLAUDE.md更新ルール

**必須**: コードベースに仕様変更を加えた場合、必ずCLAUDE.mdを最新状態に更新する

#### 更新が必要な変更

以下のような変更を行った場合、CLAUDE.mdを即座に更新すること：

1. **データベーススキーマの変更**
   - テーブル追加/削除/変更時は「Database Schema」セクションを更新
   - マイグレーション追加時は「Migration History」と「Current State」のバージョン番号を更新
   - エンティティ/DAO数の変更時は「Key Numbers」セクションを更新

2. **アーキテクチャの変更**
   - 新しいデザインパターン導入時は「Architecture Overview」を更新
   - 新しい画面追加時は「Screens」と「Key Numbers」セクションを更新
   - 新しいAdapterやサービス追加時は該当セクションを更新

3. **API仕様の変更**
   - なろう小説API仕様変更時は「なろう小説API仕様」セクションを更新
   - カクヨムスクレイピング仕様変更時は「カクヨムダウンロードプロトコル」セクションを更新
   - 新しいサイト対応追加時は新セクションを追加

4. **バージョンアップ**
   - build.gradle.ktsでバージョン変更時は「Current State」の**Version**を更新
   - 例：1.6.1 (versionCode: 160) → 1.6.2 (versionCode: 161)

5. **新機能の追加**
   - 重要な新機能追加時は「Special Features」セクションに追記
   - 新しいUtilityクラス追加時は「Utilities and Helpers」セクションを更新
   - 新しい実装パターン確立時は「Development Guidelines」に追記

6. **プロジェクト構造の変更**
   - ディレクトリ構造変更時は「Directory Layout」を更新
   - ファイル移動/名前変更時は「Key Files by Function」を更新

#### 更新時の注意事項

- **Quick Reference**セクションの情報は常に最新に保つこと（特にCurrent StateとKey Numbers）
- バージョン番号、テーブル数、エンティティ数などの**数値は正確に**記載する
- 新しいルールやパターンを追加する際は、**コード例を含める**こと
- 古くなった情報は削除し、**履歴として残すべき情報はMigration History等に移動**する
- 更新日時がある場合は「Current State」の日付を更新する

#### 更新のタイミング

- コード変更と**同じコミット**でCLAUDE.mdを更新することを推奨
- 複数の小さな変更がある場合は、機能完成時にまとめて更新してもよい
- データベースマイグレーションやAPI仕様変更など**重要な変更は即座に更新**すること

このルールにより、CLAUDE.mdは常にコードベースの最新状態を正確に反映し、AIアシスタントが適切な支援を提供できるようになります。

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

## Project Structure

### Directory Layout
```
Novel_reader_app/
├── Novel_reader/                    # Main Android project
│   ├── app/
│   │   ├── build.gradle.kts        # App build config (version: 2.0.18, code: 218)
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
├── CLAUDE.md                       # This file - AI assistant guide
├── AGENTS.md                       # Additional AI agent guidelines
├── README.md                       # Project documentation
├── STRUCTURE.md                    # Project structure summary
├── FUTURE_IMPROVEMENTS.md          # Planned improvements
├── WORK_LOG.md                     # Development work log
├── 統合テスト方法.md                # Integration testing guide
└── release/                        # Release APKs
```

### Key Files by Function

#### Application Core
- `NovelReaderApplication.kt` - Application class, singleton repository/database access
- `MainActivity.kt` - Single Activity with Compose, navigation, back handling
- `AppInfo.kt` - Application information

#### Data Layer (9 Entities, 9 DAOs, 1 Repository)
- **Entities**: NovelDescEntity, EpisodeEntity, LastReadNovelEntity, UpdateQueueEntity, URLEntity, ImageCacheEntity, EpisodeMappingEntity, RegistrationQueueEntity, TempEpisodeEntity
- **DTO**: EpisodeMeta (本文なし射影 + body_empty フラグ、エピソード一覧の軽量ロード用)
- **DAOs**: Matching DAOs for each entity (9 total)
- **Database**: NovelDatabase.kt (v17 with full migration chain, exportSchema=true)
- **Repository**: NovelRepository.kt (single source of data access, withTransaction 対応)

#### Adapters (Multi-Site Support)
- `NovelSiteAdapter.kt` - Interface
- `SyosetuAdapter.kt` - Syosetu implementation (YAML API)
- `KakuyomuAdapter.kt` - Kakuyomu implementation (HTML scraping)
- `NovelSiteAdapterFactory.kt` - Factory pattern

#### Screens (9 total)
- NovelListScreen, EpisodeListScreen, EpisodeViewScreen, WebViewScreen
- RecentlyReadNovelsScreen, RecentlyUpdatedNovelsScreen
- UpdateInfoScreen, SettingsScreen, SettingsMenuScreen (設定ハブ)

#### Utilities
- Base62Converter, PseudoNcodeGenerator, AppLogger
- FontUtils, ImageCacheUtils, NovelUpdateCoordinator, ReleaseUtils
- VjapTextConverter (縦書きテキスト変換)
- DatabaseSync*, MetadataUpdateManager, RegistrationQueueManager

#### Background Work
- `AutoUpdateWorker.kt` - WorkManager worker for scheduled updates
- `AutoUpdateScheduler.kt` - Update scheduling logic
- `UpdateService.kt` - Update service
- `MyFirebaseMessagingService.kt` - FCM push messaging service

## Architecture Overview

This is an Android novel reader application built with modern Android architecture patterns:

### Core Architecture
- **Pattern**: Clean Architecture + MVVM + Repository Pattern + Adapter Pattern (for multi-site support)
- **UI**: Jetpack Compose with single Activity pattern
- **Database**: Room with migration support (currently v17)
- **Navigation**: Custom NavigationManager with screen stack and scroll position preservation
- **Background Work**: WorkManager for scheduled updates
- **State**: Flow-based reactive programming
- **Multi-Site Support**: Adapter pattern for site-specific implementations (Syosetu, Kakuyomu)

### Key Components

#### Application Entry Point
- `NovelReaderApplication.kt` - Application class with singleton pattern for global database/repository access
- `MainActivity.kt` - Single Activity hosting all Compose screens

#### Data Layer
- `NovelDatabase.kt` - Room database with 9 tables and proper migrations (v17, exportSchema=true)
- `NovelRepository.kt` - Single repository managing all data access via DAOs (withTransaction でアトミック操作)
- **Entities** (9 total):
  - `NovelDescEntity` - Novel metadata with R18 support, favorite flag, site type, sub_site, end_flag, registration date
  - `EpisodeEntity` - Episode content with reading progress, bookmarks, and reading rate
  - `LastReadNovelEntity` - Reading history tracking
  - `UpdateQueueEntity` - Update notifications queue
  - `URLEntity` - API/Web URLs with R18 site support
  - `ImageCacheEntity` - Image caching for novel covers (v7+)
  - `EpisodeMappingEntity` - Episode ID mapping for Kakuyomu (v10+)
  - `RegistrationQueueEntity` - Download/registration queue for novels
  - `TempEpisodeEntity` - Temporary episode storage for timeout-resumable downloads
  - (`EpisodeMeta` - body-less DTO projection for lightweight episode list loading)
- **DAOs** (9 total): NovelDescDao, EpisodeDao, LastReadNovelDao, UpdateQueueDao, URLEntityDao, ImageCacheDao, EpisodeMappingDao, RegistrationQueueDao, TempEpisodeDao
- **Adapter Pattern** for multi-site support:
  - `NovelSiteAdapter` - Interface for site-specific implementations
  - `SyosetuAdapter` - なろう小説 (Syosetu) implementation
  - `KakuyomuAdapter` - カクヨム (Kakuyomu) implementation
  - `NovelSiteAdapterFactory` - Factory for creating appropriate adapters

#### Navigation
- `NavigationManager.kt` - Custom navigation with sealed class hierarchy and back stack management
- Main flow: Main → NovelList → EpisodeList → EpisodeView

#### Key Screens
- `NovelListScreen.kt` - Advanced filtering/sorting with enum-based configuration and persistent settings
- `EpisodeListScreen.kt` - Episode list with reading progress and bookmark management
- `EpisodeViewScreen.kt` - WebView-based reading with JavaScript interface for progress tracking and reading rate
- `WebViewScreen.kt` - Novel site browsing with R18 content support
- `RecentlyReadNovelsScreen.kt` - Recently read novels history
- `RecentlyUpdatedNovelsScreen.kt` - Recently updated novels with update notifications
- `UpdateInfoScreen.kt` - Update information and notification management
- `SettingsScreen.kt` - Application settings with DataStore persistence
- `SettingsMenuScreen.kt` - Settings hub navigating to detailed settings sections
- `DatabaseSyncActivity.kt` - External SQLite database synchronization (in `ui/`)

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

### Database Schema (Version 17)

#### Migration History
- v1→v2: Added `update_queue` table
- v2→v3: Added `is_read` and `is_bookmark` to episodes
- v3→v4: Added `url_entity` table
- v4→v5: Added `reading_rate` to episodes
- v5→v6: Added `is_favorite` to novels_descs
- v6→v7: Added `image_cache` table
- v7→v8: Added `userid`, `noveltype`, `length` to novels_descs
- v8→v9: Added `site_type` to novels_descs (multi-site support)
- v9→v10: Added `episode_mapping` table (Kakuyomu episode ID mapping)
- v10→v11: Added `registered_at` to novels_descs (download date tracking)
- v11→v12: **Performance optimization** - Added indices for sorting/filtering (total_ep, author, title, is_read, is_bookmark) and composite indices (site_type+is_favorite, ncode+is_read, ncode+is_bookmark)
- v12→v15: Various feature additions (see git history)
- v15→v16: Added `sub_site`, `end_flag`, `last_checked_at` to novels_descs; added indices for sub_site, end_flag, last_checked_at; initialized sub_site for existing records
- v16→v17: `image_cache`に`idx_image_cache_hash`インデックスを追加（ImageCacheEntityがv2.0.7で宣言済みだったがマイグレーション未整備だった不整合を解消）。エクスポート済み16.jsonはインデックス無しの正しいv16スキーマに再生成

#### Tables (9 total)
1. **`novels_descs`** - Novel metadata
   - Basic info: ncode, title, author, synopsis, tags, rating
   - Stats: total_ep, general_all_no, length, noveltype
   - Dates: last_update_date, updated_at, registered_at, last_checked_at
   - Flags: is_favorite, site_type (1=Syosetu, 2=Kakuyomu), sub_site (0=不明, 1=なろう, 2=ノクターン, 3=ムーンライト, 4=ミッドナイト), end_flag (0=不明, 1=完結, 2=連載中)
   - **Indices (v16)**: last_update_date, favorite, length, type, site, registered, **total_ep, author, title, (site_type+is_favorite)**, sub_site, end_flag, last_checked_at

2. **`episodes`** - Episode content
   - Content: ncode, e_no, e_title, e_body, chapter_title
   - Progress: is_read, is_bookmark, reading_rate
   - Date: last_update_date
   - **Indices (v12)**: **is_read, is_bookmark, (ncode+is_read), (ncode+is_bookmark)**

3. **`last_read_novels`** - Reading history
   - Tracking: ncode, last_read_episode, last_read_time

4. **`update_queue`** - Update notifications
   - Updates: ncode, total_ep, general_all_no, update_time
   - Index: update_time

5. **`url_entity`** - Site URLs
   - URLs: ncode, api_url, url, is_r18
   - Index: ncode

6. **`image_cache`** - Image caching (v7+)
   - Cache: hash (PK), original_url, local_path, mime_type
   - Index: hash

7. **`episode_mapping`** - Kakuyomu episode mapping (v10+)
   - Mapping: ncode, episode_no, kakuyomu_episode_id
   - Composite PK: (ncode, episode_no)
   - Indices: (ncode, episode_no), (ncode, kakuyomu_episode_id)

8. **`registration_queue`** - Download/registration queue
   - Queue management for novel downloads (pending/in-progress/completed状態管理)

9. **`temp_episodes`** - Temporary episode storage
   - Timeout-resumable downloads: タイムアウト時に一時データを保持し、リトライ時に続きから再開
   - Cleared atomically when registration completes or is cancelled

### Special Features
- Custom font loading and CSS generation for WebView
- Ruby text (furigana) support for Japanese novels
- Reading progress tracking via JavaScript bridge with reading rate calculation
- Background update scheduling with WorkManager
- Database synchronization with external SQLite files
- R18 content handling with separate site configurations
- Multi-site support (Syosetu + Kakuyomu) via adapter pattern
- Image caching for novel covers
- Favorite novels management
- Episode ID mapping for Kakuyomu integration
- Download date tracking with registered_at field
- Timeout resume: タイムアウト時に一時テーブルデータを保持し、リトライ時に続きからダウンロード再開
- Kakuyomu streaming download: カクヨムのエピソードを1話ずつ取得→保存するストリーミング方式（メモリ効率改善）
- Sub-site classification: sub_site フィールドでなろう/ノクターン/ムーンライト/ミッドナイト/カクヨムを区別
- Completion flag: end_flag フィールドで完結/連載中を管理（Syosetu API + Kakuyomu HTML scraping）
- Simple list mode: シンプルリストモード設定で全リスト画面のカード表示をフラット化
- Advanced filtering: 未読/完結/媒体フィルターを NovelListScreen・RecentlyReadScreen に追加
- Error fix options: 欠落修正にオプションダイアログ追加（期間・短編除外・完結除外・媒体・挿絵エラー検知）
- Download status screen: ダウンロード状況画面にフィルターチップと一括削除ボタンを追加
- ToC recovery: カクヨムDL中断時の復帰策 - episode_mappingテーブルからエピソードスタブを再構築するバナーをEpisodeListScreenに追加（エラー修正で本文を再取得）
- Auto ruby conversion toggle: 「漢字（よみがな）」形式を自動でルビ変換するON/OFF設定（設定画面・DataStore永続化、横書きWebView表示に適用。既存の壊れた<ruby>タグ修正は常時適用）
- Vertical mode ruby parity: applyRubyFixes() をトップレベル関数として抽出。縦書きモード（VjapVerticalTextView）にも破損タグ修復と autoRubyEnabled 変換を適用
- WebView resource cleanup: AndroidView の onRelease コールバックで stopLoading/destroy を呼び出しネイティブリソースリークを防止（EpisodeViewScreen・WebViewScreen）
- Read-status preservation on re-download: 全話再DL（UPDATE_TYPE_DOWNLOAD）時にエピソードを削除せず insertEpisode のマージで is_read/is_bookmark/reading_rate を保持
- Revision date fix: fetchEpisodeRevisionsFromToc で改稿日時を span[title] 属性から正しく取得（従来は公開日時を誤取得）
- Error-body prevention: カクヨムエピソード取得失敗時にエラー文字列 ★HTMLページ読み込みエラー を本文として保存しないよう修正
- Lightweight error scan: 欠落修正スキャンのカクヨム話数確認を fetchUpdateSummary（軽量サマリー）に変更（全話本文DLを排除）
- Streaming re-download: EpisodeListScreen のカクヨム再DLをストリーミング方式（repository 渡しで1話取得→保存）に変更
- Gap prevention on partial failure: 一括更新・自動DLで一部失敗時は total_ep を保存済み最大話数に留め、更新キューを残してリトライ可能に（全話成功時のみ確定）
- DB transactions: deleteNovelWithRelations・insertKakuyomuEpisodesWithMappings・deleteEpisodesByNcode を withTransaction でアトミック化（NovelRepository に RoomDatabase 参照を追加）
- Schema export: exportSchema=true + room.schemaLocation 設定（v16以降のマイグレーションテスト基盤）
- Cancel race fix: RegistrationQueueManager.cancelQueue でジョブを cancelAndJoin してから一時データ削除（孤児 temp_episodes 防止）
- Episode list lightweight loading: EpisodeMeta DTO（本文なし射影 + body_empty フラグ）と getEpisodeMetasByNcode により、EpisodeListScreen が全話の本文をメモリにロードせず一覧表示（1000話超でも軽量）
- Author page navigation (v2.0.15): 作者ボタンをサイト対応化。なろう一般=mypage.syosetu.com/{userid}/、R18=xmypage.syosetu.com/{xid}/（novel18apiはuseridを返さず数値IDでは404のため、作品ページからxidをスクレイプしuseridにキャッシュ。NovelApiUtils.fetchR18AuthorId）、カクヨム=kakuyomu.jp/users/{スクリーンネーム}（Apollo stateのUserAccount.nameから取得。KakuyomuAdapter.fetchAuthorUserName、parseNovelInfoでもuserid保存）。解決したIDはrepository.updateNovelで永続化し次回以降ネットワーク不要
- Vertical mode page progress (v2.0.15): VTextLayoutにOnPageChangedListener追加。縦書きでページをめくるたびに進捗率（page/totalPage）を保存できるようになった（従来は開いた時点と読了時のみ）
- Vertical mode position preservation (v2.0.15): VjapVerticalTextViewのupdateで設定の差分適用（無条件setFontSize/setColorによる再レイアウト・ページ喪失を防止）。フォントサイズ変更時はrestoreRateで直前の読書位置を復元
- Vertical kinsoku (v2.0.15): VTextViewの行頭禁則文字を拡充（小書き仮名・ー〜…‥！？：；等、ぶら下げ処理）、開き括弧の行末禁則を追加（行の最終マスなら先に改行）、余白を密度スケール化（18dp）
- Episode navigation fix (v2.0.15): 前後話ナビでLaunchedEffect先頭にepisode=nullリセットを追加（旧話の本文が表示され続けるバグ修正）。EnhancedHtmlRubyWebViewをkey(ncode, episodeNo)で再生成（JSブリッジが古いepisodeNoで別話の進捗を上書きするバグ修正）。WebViewのHTML再ロードはタグ比較で変更時のみ
- Error-body prevention (adapter paths, v2.0.15): KakuyomuAdapter内部の保存経路（一括DL・ストリーミング・旧方式）でnormalizeEpisodeBodyによりエラー文字列を空文字に正規化。EpisodeListScreenのエラー修正（カクヨム）、RegistrationQueueManager（初回登録ストリーミング）、NovelApiUtils（カクヨム単話取得）にもガード追加（全fetchEpisodeContent保存経路でエラー文字列の本文化を防止）
- WebViewScreen fixes (v2.0.15): AndroidView updateでの再ロードを廃止（再コンポーズのたびにページがリロードされるバグ修正）。mailto:等の非http(s)スキームは外部アプリへ委譲。xmypage.syosetu.comにもover18 Cookieを事前設定
- Batch update check (v2.0.17): なろう作品の更新確認を `?ncode=n1-n2-n3&of=n-ga-ua-u-nt-l&lim=N&gzip=5` のOR検索で一括取得（`NovelApiUtils.fetchNovelInfoBatch`、最大100件/リクエスト、R18は novel18api に分離、小文字ncodeで突合）。AutoUpdateWorker と UpdateInfoScreen の全更新確認で「1作品=1リクエスト→100作品=1リクエスト」に削減。一括取得で取れなかった作品（検索除外・通信失敗）は個別 `fetchNovelInfo` にフォールバック。YAMLパースを `yamlData.drop(1)` ループ化（従来の単発 `yamlData[1]` を維持しつつ一括用を追加）
- Short-novel update exclusion (v2.0.17): 短編（noveltype=2）は新規エピソードが増えないため、設定 `excludeShortFromUpdate`（DataStore、デフォルトON）で更新確認の対象から除外。AutoUpdateWorker・UpdateInfoScreen全更新確認に適用、設定画面「自動更新設定」にトグル追加（noveltype=null は除外しない安全側）
- Completed-novel update exclusion (v2.0.18): 完結作品（end_flag=1）を更新確認から除外する設定 `excludeCompletedFromUpdate`（DataStore、デフォルトOFF＝後日談を見逃さないため）。AutoUpdateWorker・UpdateInfoScreen全更新確認に適用、設定画面「自動更新設定」にトグル追加（短編除外と独立）
- Audit fixes (v2.0.20): コード監査（AUDIT_REPORT.md）で確認されたDB非依存の重要バグを修正（スキーマ・同期系は除く）。
  - Ruby toggle reachable (H7): `SettingsReadingScreen` に自動ルビ変換トグルを追加し UI から到達可能化（旧 orphan `SettingsScreenUpdated` のみだった保存経路を本番ハブ画面へ）
  - Update-exclusion toggles reachable (H8): `SettingsAutoUpdateScreen` に「短編を更新確認から除外」「完結作品を更新確認から除外」トグルを追加（本番自動更新サブ画面から設定可能化）
  - Notification "すべてダウンロード" wired (M17): 重複していた未登録 `DownloadActionReceiver` を削除し、AutoUpdateWorker のシステム通知に `addAction`（PendingIntent.getBroadcast + FLAG_IMMUTABLE）で `DownloadAllReceiver` を配線。未DLのキューが残る場合のみ表示
  - Prev/next nav stack fix (H6): `NavigationManager.replaceCurrent(screen)` を追加し、前後話ナビ（MainActivity onPrevious/onNext）を navigateTo→replaceCurrent に変更。EpisodeView フレームのバックスタック無限蓄積を解消
  - WebView body sanitize (H5/L12): `EnhancedHtmlRubyWebView` で表示前に `Jsoup.clean`(Safelist.relaxed + ruby/rt/rp/rb 許可, img は file/content/data 許可) により本文の `<script>`/on* を除去（XSS防止）。`allowFileAccessFromFileURLs/allowUniversalAccessFromFileURLs=false` を明示。`WebViewScrollInterface.saveScrollPosition` はネイティブ側で `coerceIn(0f,1f)`
  - Kakuyomu mapping race fix (H1): `KakuyomuAdapter` の共有可変 `cachedMappings` / `getCachedMappings()` を廃止。`parseEpisodeList` がマッピングを戻り値で返し、`fetchNovelMetadataWithEpisodeListAndMappings`（→ `NovelWithEpisodesAndMappings`）でメタ・エピソード・マッピングをアトミック取得。全呼び出し側（UpdateService/AutoUpdateWorker/RegistrationQueueManager/UpdateInfoScreen）を更新
  - Rate-limit serialized (M1): `applyRateLimit` を companion `Mutex.withLock` で直列化（30並列同時通過による403/遮断を防止）
  - Queue start race fix (H3): `RegistrationQueueManager.processQueue` を `CoroutineStart.LAZY` 生成→map登録→PROCESSING更新→`job.start()` の順にし、「DBはPROCESSINGだがjob未登録」窓を除去。`processingQueues` アクセスを `synchronized` 化（孤児 temp_episodes 防止）
  - Syosetu missing-episode resume fix (H4): `fetchSyosetuWithTempDb` で取得失敗話を空本文（body_empty）プレースホルダとして temp に記録。MAX(episode_no) レジュームによる恒久欠番化を防ぎ、欠落修正スキャン（body='' OR e_title=''）で再取得可能に（カクヨム経路と挙動統一）
  - Missing-fix scan batched (H11): UpdateInfoScreen の欠落修正スキャンで、なろう作品の話数確認を `fetchNovelInfoBatch`（rating分離・100件チャンクのOR検索）に変更し 1作品=1リクエストのN+1を解消（取れなかった作品のみ個別 `fetchNovelInfo` フォールバック）

### Performance Optimizations

#### Database Performance (v12)
**Target**: 1000+ novels, 320,000+ episodes

1. **N+1 Query Elimination**
   - Added `getNovelsByNcodes()` for bulk novel fetching
   - Optimized `RecentlyReadNovelsScreen` and `UpdateInfoScreen`
   - **Impact**: 50-80% faster screen loads

2. **Database Indices**
   - Single-column: `total_ep`, `author`, `title`, `is_read`, `is_bookmark`
   - Composite: `(site_type, is_favorite)`, `(ncode, is_read)`, `(ncode, is_bookmark)`
   - **Impact**: 30-60% faster filtering/sorting

3. **Query Optimization**
   - Added `getErrorEpisodes()` for targeted error episode queries
   - Added `getEpisodesByNcodeList()` for list-based episode fetching
   - **Impact**: Reduced memory usage, faster error detection

#### UI Performance
1. **Compose Recomposition**
   - Removed unnecessary `isUpdating` checks in LazyColumn items
   - Optimized state derivation with `derivedStateOf`
   - **Impact**: Smoother scrolling, no frame drops

2. **Background Processing**
   - AutoUpdateWorker batch size: 30 parallel requests
   - Coroutine-based parallel processing with `chunked()` + `async()`
   - **Impact**: 3-10x faster update checks

#### Memory Management
- Incremental episode saving (fetch → save → fetch → save)
- Flow-based data streams instead of full list loading
- Optimized for 1000 novels × 320 episodes average = 320,000 total episodes
- **Impact**: Stable memory usage even with large datasets

### Multi-Site Architecture

#### Adapter Pattern Implementation
The application uses the Adapter pattern to support multiple novel sites with site-specific implementations:

**Core Interface**: `NovelSiteAdapter`
- Defines common operations: `getSiteType()`, `getSiteName()`, `generateWebUrl()`, `fetchNovelMetadata()`, `fetchEpisodeList()`, `fetchEpisodeContent()`
- Site type constants: `SITE_TYPE_SYOSETU = 1`, `SITE_TYPE_KAKUYOMU = 2`

**Implementations**:
1. **`SyosetuAdapter`** - なろう小説 (Syosetu)
   - Uses official YAML API with gzip compression
   - Supports both general (ncode.syosetu.com) and R18 (novel18.syosetu.com) sites
   - Standard ncode format (e.g., "n1234ab")

2. **`KakuyomuAdapter`** - カクヨム (Kakuyomu)
   - HTML scraping (no official API)
   - 1-second rate limiting between requests
   - Pseudo-ncode format: "KK-{Base62(workId)}" for compatibility
   - Episode ID mapping table for internal episode numbering
   - Multiple fallback patterns for robust HTML parsing

**Factory Pattern**: `NovelSiteAdapterFactory`
- Creates appropriate adapter based on site type
- Centralizes adapter instantiation logic

#### Site Type Detection
```kotlin
// In NovelDescEntity
val site_type: Int = 1  // 1=Syosetu, 2=Kakuyomu

// Usage
val adapter = NovelSiteAdapterFactory.createAdapter(novel.site_type)
val episodes = adapter.fetchEpisodeList(novel.ncode)
```

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

### Testing Infrastructure

#### Unit Tests (`test/`)
- **`Base62ConverterTest`** - Base62 encoding/decoding tests
- **`PseudoNcodeGeneratorTest`** - Pseudo-ncode generation tests
- **`KakuyomuTextCleanupTest`** - Text cleanup function tests

#### Instrumented Tests (`androidTest/`)
- **DAO Tests**:
  - `NovelDescDaoTest` - Novel metadata DAO tests
  - `EpisodeDaoTest` - Episode DAO tests
- **Repository Tests**:
  - `NovelRepositoryTest` - Repository layer integration tests
- **Database Tests**:
  - `MigrationTest` - Database migration validation tests

**Test Libraries**:
- JUnit 4 for test framework
- Coroutines Test for async testing
- Google Truth for assertions
- Room Testing for database tests
- Work Testing for WorkManager tests

### Development Guidelines
1. **Database Migrations**: Always create Room migrations for schema changes. Current version is 17.
2. **Compose State**: Use proper Compose state management with state hoisting
3. **Repository Pattern**: Follow the Repository pattern for all data operations. Never access DAOs directly.
4. **Navigation**: Use NavigationManager for navigation to maintain proper back stack
5. **R18 Content**: Handle R18 content appropriately with dialog-based site selection
6. **Reading Progress**: Maintain reading progress, bookmark functionality, and reading rate in EpisodeViewScreen
7. **Version Management**: Always increment both `versionCode` by +1 and `versionName` patch version (e.g., 2.0.0 → 2.0.1) when making any code changes in `Novel_reader/app/build.gradle.kts`. Current version: 2.0.20 (versionCode 220).
8. **Commit Messages**: Always write commit messages in Japanese
9. **Incremental Episode Saving**: When fetching episodes (both Kakuyomu and Syosetu), always fetch and save one episode at a time. Never fetch all episodes into memory first - instead use: fetch episode 1 → save to DB → fetch episode 2 → save to DB, etc. This applies to all re-download, update, and error-fix operations.
10. **Multi-Site Support**: Use the Adapter pattern for site-specific logic. Never hardcode site-specific behavior outside of adapter implementations.
11. **Logging**: Use `AppLogger` for all logging, which respects `BuildConfig.ENABLE_LOGGING` flag (true in debug, false in release)
12. **Testing**: Write unit tests for utilities and instrumented tests for DAOs/Repository. Run tests before major changes.
13. **Image Caching**: Use `ImageCacheUtils` and the `image_cache` table for novel cover caching
14. **Episode Mapping**: For Kakuyomu novels, always maintain the `episode_mapping` table to map internal episode numbers to Kakuyomu episode IDs

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

### 自動更新の実行フロー

```
1. ユーザーが設定画面で「自動更新を有効」→「更新時刻: 03:00」設定
   ↓
2. AutoUpdateSchedulerがWorkManagerに24時間周期の定期タスク登録
   ↓
3. 指定時刻（03:00）にAutoUpdateWorkerが起動
   ↓
3.1. update_queueをリセット
   ↓
4. 全ての小説の更新確認（通常の更新確認と同様）
   ↓
5. 更新検出 → update_queueに追加 + システム通知 + アプリ内通知保存
   ↓
6. ユーザーがスマホの通知を見る
   - タップでアプリ起動
   - 「すべてダウンロード」で一括DL開始
   ↓
7. アプリ内の通知ボタン（🔔）で履歴確認・管理可能
```

**重要なポイント**:
- 自動更新開始時に`update_queue`を必ずリセットする（`repository.clearAllUpdateQueue()`）
- 全ての小説を対象に更新確認を実行（お気に入りのみではない）
- 30件ずつバッチ処理で並列実行してパフォーマンスを最適化
- なろう小説: YAML API経由で最新話数を即座に取得
- カクヨム: HTMLスクレイピングでメタデータのみ取得（本文は取得しない）

## なろう小説API仕様

**必須**: なろう小説APIはYAML形式でデータを返す

### APIレスポンス形式
- **フォーマット**: YAML（デフォルト）
- **圧縮**: gzip圧縮（`gzip=5`パラメータ使用時）
- **注意**: `&json` パラメータは使用しない（YAMLがデフォルト）

### レスポンス構造
```yaml
---
-
  allcount: 1
-
  title: はぐるまどらいぶ。
  userid: 939213
  writer: かばやきだれ
  story: |
    あらすじ...
    複数行のテキスト
    （改行が保持される）
  # または
  story: >
    複数行のテキストが
    1行にまとめられる
    （改行が空白に置き換えられる）
  noveltype: 1
  general_all_no: 1216
  length: 4739978
  updated_at: 2025-11-04 18:35:37
```

**YAMLの複数行記法**:
- `|` (リテラルスカラー): 改行をそのまま保持
- `>` (折り畳みスカラー): 改行を空白に置き換え（段落をまとめる）
- SnakeYAMLライブラリが両方を自動処理

### 実装パターン
```kotlin
// API呼び出し（YAML形式、gzip圧縮）
val apiUrl = if (isR18) {
    "https://api.syosetu.com/novel18api/api/?of=t-w-ga-s-ua&ncode=$ncode&gzip=5"
} else {
    "https://api.syosetu.com/novelapi/api/?of=t-w-ga-s-ua&ncode=$ncode&gzip=5"
}

// YAMLパース（インデント修正を適用）
// なろう小説APIから返されるYAMLには、折り畳みスカラー（>）や
// リテラルスカラー（|）のインデントが不統一な場合があり、
// SnakeYAMLがパースエラーを起こすことがある。
// そのため、パース前にfixYamlFoldedScalarIndentation()でインデントを修正する。
val fixedYaml = fixYamlFoldedScalarIndentation(responseContent)
val yaml = Yaml()
val yamlData = yaml.load<List<Map<String, Any>>>(fixedYaml)
val novelData = yamlData[1]  // 0番目はメタデータ、1番目が小説情報

// GZIP解凍判定
val useGzip = url.contains("gzip=", ignoreCase = true) ||
              connection.contentEncoding?.contains("gzip", ignoreCase = true) == true
```

**重要なルール**:
- URLに `gzip=` パラメータがある場合は必ずGZIP解凍を行う
- Content-Encodingヘッダーだけでなく、URLパラメータもチェックする
- レスポンスの0番目の要素はメタデータ（allcount）、1番目が実際の小説情報

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
    private const val RATE_LIMIT_DELAY_MS = 500L  // 0.5秒（スクレイピング時の推奨間隔）
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
- レート制限は**0.5秒間隔**（500ms）
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
// 個別エピソードページからのタイトル取得（KakuyomuAdapter.kt）
// パターン1: header#contentMain-header（最優先、Pascalコード参考）
val titleElement1 = doc.select("header#contentMain-header")
if (titleElement1.isNotEmpty()) {
    episodeTitle = titleElement1.text()
}

// パターン2: widget-episodeTitle
if (episodeTitle.isEmpty()) {
    val titleElement2 = doc.select("p.widget-episodeTitle")
    if (titleElement2.isNotEmpty()) {
        episodeTitle = titleElement2.text()
    }
}

// パターン3: h1タグ（第2フォールバック、Pascalコード参考）
if (episodeTitle.isEmpty()) {
    val titleElement3 = doc.select("h1").firstOrNull()
    if (titleElement3 != null) {
        episodeTitle = titleElement3.text()
    }
}

// パターン4: 最後のフォールバック（Pascalコード参考）
if (episodeTitle.isEmpty()) {
    episodeTitle = "第${episodeNo}話"
}

// タイトルのクリーンアップ処理
episodeTitle = cleanupText(episodeTitle)
```

**重要なルール**:
- `header#contentMain-header`を**最優先**で使用
- `p.widget-episodeTitle`は第2優先
- `h1`タグは第3フォールバック
- 最後のフォールバックとして「第X話」形式を生成
- 取得したタイトルは必ず`cleanupText()`でクリーンアップ

### 章タイトル取得の優先順位

```kotlin
// 個別エピソードページからの章タイトル取得（Pascalコード参考）
// パターン1: chapterTitle level1
val chapterElement1 = doc.select("p.chapterTitle.level1 span")
if (chapterElement1.isNotEmpty()) {
    chapterTitle = chapterElement1.text()
}

// パターン2: chapterTitle level2
if (chapterTitle.isEmpty()) {
    val chapterElement2 = doc.select("p.chapterTitle.level2 span")
    if (chapterElement2.isNotEmpty()) {
        chapterTitle = chapterElement2.text()
    }
}

// チャプタータイトルのクリーンアップ処理
chapterTitle = cleanupText(chapterTitle)
```

**重要なルール**:
- `p.chapterTitle.level1 span`を**最優先**で使用
- `p.chapterTitle.level2 span`はフォールバック
- 章タイトルが存在しない場合もある（空文字列）
- 取得した章タイトルは必ず`cleanupText()`でクリーンアップ

### エピソード本文のエラーチェック

```kotlin
// HTMLページ読み込みエラーのチェック（Pascalコード参考）
private fun checkForLoadingError(html: String): Boolean {
    val errorIndicator = "<div class=\"dots-indicator\" id=\"LoadingEpisode\">"
    val checkLength = minOf(html.length, 200)  // 最初の200文字をチェック
    val prefix = html.take(checkLength)
    return prefix.contains(errorIndicator)
}

// エラーチェックの使用例
if (checkForLoadingError(episodeBody)) {
    android.util.Log.w("KakuyomuAdapter", "HTMLページ読み込みエラーを検出: $episodeId")
    return@withContext "★HTMLページ読み込みエラー\n本文を正しく取得できませんでした。\n後ほど再度お試しください。"
}
```

**重要なルール**:
- 本文の先頭200文字以内に`<div class="dots-indicator" id="LoadingEpisode">`が含まれていればエラー
- エラーを検出した場合は、分かりやすいエラーメッセージを返す
- エラーログを出力して問題の追跡を容易にする

### HTTP取得の再試行ロジック

```kotlin
// HTTP取得の再試行実装パターン（KakuyomuAdapter.kt）
private suspend fun performHttpRequest(urlString: String, maxRetries: Int = 3): String {
    var lastException: Exception? = null

    for (attempt in 1..maxRetries) {
        try {
            // HTTP接続処理
            when (responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    // 完全なHTMLをバッファリングして取得（Pascalコード参考）
                    val contentLength = connection.contentLength
                    val html = StringBuilder()

                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val buffer = CharArray(8192)  // 8KBバッファ
                        var charsRead: Int

                        // 全データを読み込むまでループ（Pascalコードと同様）
                        while (reader.read(buffer).also { charsRead = it } != -1) {
                            html.append(buffer, 0, charsRead)
                        }
                    }

                    val htmlString = html.toString()
                    android.util.Log.d("KakuyomuAdapter", "HTTP取得成功: $urlString (Content-Length: $contentLength, Actual: ${htmlString.length})")

                    return htmlString
                }
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
- **完全なHTMLをバッファリングして取得**（Pascalコードと同様、全データを読み込むまでループ）
- 8KBバッファを使用して効率的に読み込み
- Content-Lengthと実際のデータサイズをログで確認
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

### エピソード一覧取得の複数フォールバック

**必須**: エピソード一覧の取得には複数の方法を試行し、確実に全エピソードを取得する

```kotlin
// エピソード一覧取得の優先順位（KakuyomuAdapter.kt）
private suspend fun parseEpisodeList(workId: String, pseudoNcode: String): List<EpisodeEntity> {
    // 方法1: エピソードページの目次から取得（最優先、最も確実）
    val episodesFromToc = extractEpisodesFromToc(workId, pseudoNcode)
    if (episodesFromToc.isNotEmpty()) {
        return episodesFromToc
    }

    // 方法2: 作品ページのHTMLから取得（フォールバック）
    // ...
}

// エピソードページの目次から全エピソードを抽出
private suspend fun extractEpisodesFromToc(workId: String, pseudoNcode: String): List<EpisodeEntity> {
    // 1. 作品ページから最初のエピソードリンクを取得
    val workUrl = "https://kakuyomu.jp/works/$workId"
    val firstEpisodeLink = doc.select("a.widget-toc-episode-episodeTitle, a.WorkTocSection_link__ocg9K").firstOrNull()?.attr("href")

    // 2. エピソードページを取得（完全な目次が含まれる）
    val episodeUrl = "https://kakuyomu.jp$firstEpisodeLink"
    val episodeHtml = performHttpRequest(episodeUrl)
    val episodeDoc = Jsoup.parse(episodeHtml)

    // 3. 目次から全エピソードを抽出
    val tocItems = episodeDoc.select("ol.widget-toc-items li.widget-toc-episode")
    tocItems.forEach { item ->
        val link = item.select("a.widget-toc-episode-episodeTitle").attr("href")
        val episodeId = link.substringAfterLast("/")
        val title = item.select("span.widget-toc-episode-titleLabel").text()
        val publishedAt = item.select("time.widget-toc-episode-datePublished").attr("datetime")
        // エピソードを追加
    }

    return episodes
}
```

**重要なルール**:
- **方法1（エピソードページの目次）**: 任意のエピソードページには完全な目次が表示されている（最優先、最も確実）
  - 作品ページから最初のエピソードリンクを取得
  - そのエピソードページを取得し、目次セクション（`ol.widget-toc-items`）から全エピソードを抽出
  - セレクタ：`li.widget-toc-episode` → `a.widget-toc-episode-episodeTitle`（リンク）、`span.widget-toc-episode-titleLabel`（タイトル）、`time.widget-toc-episode-datePublished`（公開日時）
- **方法2（HTMLフォールバック）**: 目次が取得できない場合のフォールバック
- 992話のような大量のエピソードも確実に取得できる
- レート制限を守るため、各HTTPリクエスト前に`applyRateLimit()`を呼び出す

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
