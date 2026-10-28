# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview
- **App**: Android novel reader (Syosetu「なろう小説」+ Kakuyomu「カクヨム」対応)
- **Version**: see `Novel_reader/app/build.gradle.kts` (`versionName`/`versionCode`) — this is the single source of truth. Do not hardcode version numbers elsewhere in this file.
- **Database**: Room, version 18, 9 tables + performance indices (詳細 → [docs/db-schema.md](docs/db-schema.md))
- **Architecture**: Clean Architecture + MVVM + Repository Pattern + Adapter Pattern (multi-site)
- **Performance target**: 1000+ novels, 320,000+ episodes

## Key Numbers
- **Entities/DAOs**: 9 each (NovelDesc, Episode, LastReadNovel, UpdateQueue, URL, ImageCache, EpisodeMapping, RegistrationQueue, TempEpisode)
- **Screens**: 9 main screens (incl. SettingsMenuScreen hub) + 1 sync activity (DatabaseSyncActivity)
- **Adapters**: 2 (SyosetuAdapter, KakuyomuAdapter)
- **Migrations**: v1→v18 (詳細 → [docs/db-schema.md](docs/db-schema.md))

## Tech Stack
- **UI**: Jetpack Compose, single Activity pattern
- **DB**: Room with migration support (exportSchema=true)
- **Navigation**: Custom `NavigationManager` (screen stack + scroll position preservation)
- **Background Work**: WorkManager (`AutoUpdateWorker`) — 詳細 → [docs/auto-update-flow.md](docs/auto-update-flow.md)
- **State**: Flow-based reactive programming
- **Multi-Site**: Adapter pattern (`NovelSiteAdapter` → `SyosetuAdapter` / `KakuyomuAdapter`)

## Build and Test Commands
All Gradle commands run from the `Novel_reader/` directory.

```bash
./gradlew build                # Build the project
./gradlew test                 # Run unit tests
./gradlew connectedAndroidTest  # Run instrumented tests
./gradlew clean                # Clean build
./gradlew installDebug          # Install debug APK
```

## Directory Structure (top level)
```
Novel_reader_app/
├── Novel_reader/        # Main Android project (app/, build.gradle.kts)
├── CLAUDE.md            # This file
├── AGENTS.md            # Additional AI agent guidelines
├── README.md
├── docs/                # Detailed reference docs (schema, API specs, architecture)
└── release/             # Release APKs
```
詳細なディレクトリ構成・主要ファイル一覧 → [docs/architecture-detail.md](docs/architecture-detail.md)

## Common Tasks
- **Add new feature**: Bump version in `build.gradle.kts`, write Japanese commit message
- **Database change**: Create new Room migration, bump DB version
- **Add new site**: Implement `NovelSiteAdapter` interface, register in `NovelSiteAdapterFactory`
- **Fetching episodes**: Always use incremental saving (fetch 1 → save → fetch 2 → save; see rule 9 below)
- **Site detection**: Check `novel.site_type` (1=Syosetu, 2=Kakuyomu)
- **Logging**: Use `AppLogger` (respects `BuildConfig.ENABLE_LOGGING`)

## Development Guidelines
1. **Database Migrations**: Always create a Room migration for schema changes.
2. **Compose State**: Use proper Compose state management with state hoisting.
3. **Repository Pattern**: All data access goes through `NovelRepository`. Never access DAOs directly.
4. **Navigation**: Use `NavigationManager` for navigation to maintain proper back stack (use `replaceCurrent` for prev/next-style navigation to avoid back-stack buildup).
5. **R18 Content**: Handle via dialog-based site selection; `rating == 1` → R18 site/API (詳細 → [docs/api-spec.md](docs/api-spec.md)).
6. **Reading Progress**: Maintain reading progress, bookmark status, and reading rate in `EpisodeViewScreen`.
7. **Version Management**: Always increment both `versionCode` (+1) and the `versionName` patch version in `Novel_reader/app/build.gradle.kts` for any code change.
8. **Commit Messages**: Always write commit messages in Japanese.
9. **Incremental Episode Saving**: When fetching episodes (Kakuyomu or Syosetu), always fetch and save one episode at a time — never load all episodes into memory first. Applies to re-download, update, and error-fix operations.
10. **Multi-Site Support**: Use the Adapter pattern for site-specific logic. Never hardcode site-specific behavior outside adapter implementations.
11. **Logging**: Use `AppLogger` for all logging.
12. **Testing**: Write unit tests for utilities and instrumented tests for DAOs/Repository. Run tests before major changes.
13. **Image Caching**: Use `ImageCacheUtils` and the `image_cache` table for novel cover caching.
14. **Episode Mapping**: For Kakuyomu novels, always maintain the `episode_mapping` table (internal episode number → Kakuyomu episode ID).

## Key Constraints / Gotchas
- **なろうAPI**: YAML形式（JSON不可）。gzip判定はURLパラメータとContent-Encoding両方をチェック。詳細 → [docs/api-spec.md](docs/api-spec.md)
- **短編小説のタイトル取得**: 連載用セレクタ（`p-novel__title--rensai`）だけでは短編のタイトルが取れないため、汎用セレクタへのフォールバックが必須。詳細 → [docs/api-spec.md](docs/api-spec.md)
- **カクヨムのレート制限**: 0.5秒間隔必須（`applyRateLimit()`、Mutex直列化済み）。詳細 → [docs/kakuyomu-protocol.md](docs/kakuyomu-protocol.md)
- **カクヨムのHTMLパース**: 複数フォールバックパターン必須（本文/タイトル/章タイトルそれぞれ優先順位あり）。詳細 → [docs/kakuyomu-protocol.md](docs/kakuyomu-protocol.md)
- **DB同期 (DatabaseSyncActivity)**: 復元時に `is_read`/`is_bookmark`/`reading_rate`/`episode_mapping`/`site_type` 等を欠落させないこと。過去に大きな不具合があった領域（v2.0.20〜v2.0.22で修正）。変更前に [docs/db-schema.md](docs/db-schema.md) の Version History を確認。
- **R8/minify (releaseビルド)**: v2.0.24からR8+shrinkResources有効。リフレクション依存箇所（snakeyaml=なろうAPIパース、`WebViewScrollInterface`=読書位置保存のJSブリッジ）は `proguard-rules.pro` のkeepルールで保護済み。新たにリフレクション・`@JavascriptInterface`・ServiceLoaderを使うコードを追加する際は必ずkeepルールも追記すること。
- **消化済み既知課題 (v2.0.24)**: M15(total_ep再計算), M16(同期コールバックMain), M4(MigrationTest再構成), L4(R18 sub_site誤分類→v18 migration)。詳細 → [docs/db-schema.md](docs/db-schema.md)

## Documentation Map
| Topic | File |
|---|---|
| DB schema, migrations, performance, version history | [docs/db-schema.md](docs/db-schema.md) |
| なろう小説API仕様・R18判定・短編タイトル取得 | [docs/api-spec.md](docs/api-spec.md) |
| カクヨムスクレイピングプロトコル | [docs/kakuyomu-protocol.md](docs/kakuyomu-protocol.md) |
| ディレクトリ構成・主要ファイル・実装パターン詳細 | [docs/architecture-detail.md](docs/architecture-detail.md) |
| 自動更新（WorkManager）の実行フロー | [docs/auto-update-flow.md](docs/auto-update-flow.md) |

## Keeping This File Current
When you make a spec-relevant change (schema, API/protocol, architecture, or a new constraint), update this file and the relevant `docs/*.md` in the same change. Keep this file itself under ~200 lines — push details to `docs/`.


## Multi-agent orchestration workflow

You are the technical lead and orchestrator.

Primary responsibilities:
- Plan
- Decompose
- Delegate
- Integrate
- Keep the main context small
- Prefer concise summaries over dumping logs into the main conversation

Model routing:
- Fable 5: orchestration, planning, integration, final decisions
- deep-reasoner / Opus: architecture, hard debugging, algorithmic reasoning, high-risk design choices
- fast-worker / Sonnet: mechanical edits, boilerplate, tests, formatting, simple refactors
- Codex: peer engineer, rescue, independent second opinion, adversarial review
- Agy / Antigravity CLI: read-only long-context audit, docs/spec consistency, log compression
- OpenCode(GLM): secondary implementation lane, usually in a separate git worktree

Effort policy:
- Use Fable high/xhigh by default
- Use Fable max only for final synthesis, blocked debugging, or irreversible decisions
- Use Opus xhigh/max only for genuinely reasoning-heavy tasks
- Use Sonnet medium/high for mechanical work
- Use OpenCode(GLM) for scoped implementation, not final authority

Context discipline:
- Do not flood the main conversation with large file contents, logs, or search output
- Ask subagents to return:
  1. conclusion
  2. evidence
  3. changed files, if any
  4. risks
  5. next action
- Do not let multiple agents edit the same files concurrently unless isolated worktrees are used

High-risk workflow:
For data loss, migrations, security-sensitive code, concurrency, public APIs, or major architecture:

1. Ask deep-reasoner / Opus independently
2. Ask Codex independently
3. Ask Agy to check assumptions and documentation consistency
4. Optionally ask OpenCode(GLM) for an isolated implementation attempt
5. Fable compares all outputs and chooses the minimal safe path

Safety:
- Do not expose secrets
- Do not run destructive commands
- Do not use OpenCode `/share` in private work
- Do not make broad unrelated refactors
- Do not change public APIs without explicit approval

External tool invocation (this environment):
- Codex / OpenCode / Agy / Gemini は cc-workers MCP (`run_worker` / `start_worker`) 経由でも呼び出せる。
  長時間ジョブは `start_worker` + `job_status` を使い、メイン会話にログを流さない。
- 監査・検証・敵対的レビューの定型プロンプトは worker-audit スキルを使う。
