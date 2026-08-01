# Database Schema (Version 18)

`NovelDatabase.kt` — Room database, 9 tables, `exportSchema=true`（room.schemaLocation でスキーマJSONを出力）。

## Migration History
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
- v15→v16: Added `sub_site`, `end_flag`, `last_checked_at` to novels_descs; added indices for sub_site, end_flag, last_checked_at; 一般作品のみ sub_site=1、R18は 0(不明)（L4）
- v16→v17: `image_cache`に`idx_image_cache_hash`インデックスを追加（ImageCacheEntityがv2.0.7で宣言済みだったがマイグレーション未整備だった不整合を解消）。エクスポート済み16.jsonはインデックス無しの正しいv16スキーマに再生成
- v17→v18: L4修正 — 旧15→16で R18 を一律 `sub_site=2` にしていた誤分類を `sub_site=0` に戻す（物理スキーマ変更なし）

## Tables (9 total)

1. **`novels_descs`** - Novel metadata
   - Basic info: ncode, title, author, synopsis, tags, rating
   - Stats: total_ep, general_all_no, length, noveltype
   - Dates: last_update_date, updated_at, registered_at, last_checked_at
   - Flags: is_favorite, site_type (1=Syosetu, 2=Kakuyomu), sub_site (0=不明, 1=なろう, 2=ノクターン, 3=ムーンライト, 4=ミッドナイト), end_flag (0=不明, 1=完結, 2=連載中)
   - **Indices (v16)**: last_update_date, favorite, length, type, site, registered, total_ep, author, title, (site_type+is_favorite), sub_site, end_flag, last_checked_at

2. **`episodes`** - Episode content
   - Content: ncode, e_no, e_title, e_body, chapter_title
   - Progress: is_read, is_bookmark, reading_rate
   - Date: last_update_date
   - **Indices (v12)**: is_read, is_bookmark, (ncode+is_read), (ncode+is_bookmark)

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

## Performance Optimizations

### Database Performance (v12)
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

### UI Performance
1. **Compose Recomposition**
   - Removed unnecessary `isUpdating` checks in LazyColumn items
   - Optimized state derivation with `derivedStateOf`
   - **Impact**: Smoother scrolling, no frame drops

2. **Background Processing**
   - AutoUpdateWorker batch size: 30 parallel requests
   - Coroutine-based parallel processing with `chunked()` + `async()`
   - **Impact**: 3-10x faster update checks

### Memory Management
- Incremental episode saving (fetch → save → fetch → save)
- Flow-based data streams instead of full list loading
- Optimized for 1000 novels × 320 episodes average = 320,000 total episodes
- **Impact**: Stable memory usage even with large datasets

## Version History (機能追加ログ)

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
- Multi-format DB restore (v2.0.21): データベース同期(DatabaseSyncActivity)の復元を複数形式に対応。選択ファイルの先頭バイトで形式を自動判別（`WebNovelReaderImportManager.looksLikeZip`）。
  - SQLite形式（`ImprovedDatabaseSyncManager`）: 本アプリの.dbバックアップに加え、旧形式SQLite（`n_code`/`Synopsis`/`rast_read_novel` の命名揺れ）にも既存フォールバックで対応済み（`novel_status.db` 等）。
  - WebNovelReaderバックアップ(.zip)（新規 `WebNovelReaderImportManager`）: 別アプリのエクスポートZIP（`webnovel.db`(fetch_target/episode) + `ep_data/{fetch_target_id}/{episode._id}` の入れ子ZIP内生HTML）を復元。`fetch_target.url` からサイト判別（ncode.syosetu.com=なろう一般, novel18.syosetu.com=R18, kakuyomu.jp/works/{id}=カクヨム→`PseudoNcodeGenerator`でPseudo-Ncode化）。本文は入れ子ZIP→生HTMLをJsoupで抽出（なろう=`div.p-novel__body > div`を`<hr>`連結＝NovelApiUtilsと同一, カクヨム=`div.widget-episodeBody.js-episode-body`）。カクヨムは`episode_mapping`（話番号→episodes URLの数値ID）も再構築。なろうは作品トップページ(no=0)から作者名/あらすじ/作者IDを補完。話の`is_read`を取り込み、`insertEpisodes(preserveExisting)`でローカル既読とマージ。バッチ20件でインクリメンタル保存（rule #9準拠）。非対応サイト（syosetu.org=ハーメルン等）はスキップ数を集計してログ。
- DB sync audit fixes (v2.0.22): AUDIT_REPORT.md でv2.0.20から繰り越されていたDB/同期系の要修正事項を修正。
  - Read-status restore (C1): `ImprovedDatabaseSyncManager`・`DatabaseSyncManager` の `syncEpisodes` が外部DBの `is_read`/`is_bookmark`/`reading_rate` を読まずデフォルト0で挿入していた不具合を修正（`getColumnIndexSafely`で読み取り、`EpisodeEntity`へ設定）。`insertEpisodes(preserveExisting)`により「内部に既存があれば内部優先、無ければ外部値採用」で復元。**バックアップ復元で全話の既読・しおり・読書位置が消失する問題を解消**。
  - episode_mapping sync (H10): 両同期マネージャに `syncEpisodeMappings` を追加。外部DBに `episode_mapping` テーブルがあれば `insertEpisodeMappings` で復元（カクヨム作品が復元後に本文再取得不能になる問題を解消）。旧形式SQLite等でテーブルが無ければ何もしない。
  - Old sync site_type preservation (H9): 旧 `DatabaseSyncManager.syncNovelDescs` が `site_type`/`sub_site`/`end_flag`/`is_favorite` を読まずデフォルトのSyosetu値で `REPLACE` し、カクヨム作品になろうURLを付与していた破壊を修正。`getNovelByNcode` で既存値を保持し、URLEntity生成をなろう作品(site_type≠2)に限定。`n_code` 命名揺れにもフォールバック。
  - IN-clause chunking (H2): `NovelRepository.getNovelsByNcodes` を `chunked(900)` 分割に変更し、1000件超で `SQLiteException: too many SQL variables` クラッシュを解消（RecentlyReadNovelsScreen・UpdateInfoScreenの全呼び出しに適用）。
  - Progress throttling fix (L14): `ImprovedDatabaseSyncManager` の進捗スロットリング判定 `abs(lastProgress?.progress ?: 0f - progress.progress)` の演算子優先順位ミス（常にtrue化＝無効）を `abs((lastProgress?.progress ?: 0f) - progress.progress)` に修正。
  - ToC recovery total_ep (L10): `EpisodeListScreen` のカクヨム目次復帰で `total_ep` を `mappings.size` でなく挿入後の実話数（既存＋新規スタブ）で設定。
  - 未対応（別途要対応）: （v2.0.24で M15/M16/M4/L4 を消化）
- Maintenance (v2.0.24): 既知課題の消化とリリース最適化。
  - M15: `insertNovels` を `runInTransaction` 化。DB同期完了後に `recalculateAllTotalEpFromEpisodes()` で episodes 実件数から `total_ep` を一括再計算（Improved/旧/WebNovelReader 全経路）。
  - M16: `DatabaseSyncActivity` の同期コールバックで Compose State 更新を `Dispatchers.Main.immediate` に戻す。
  - M4: MigrationTest をスキーマJSONがある 16→17→18 検証＋手組みDBでの直接 Migration 検証に再構成（欠落JSON依存の 4/9/10/11 createDatabase を廃止）。
  - L4: `MIGRATION_15_16` が R18 を一律 `sub_site=2` にしていたのをやめ R18 は 0(不明) のまま。既存DB向け `MIGRATION_17_18` で `rating=1 AND sub_site=2` を 0 に戻す。`NovelApiUtils` も R18 の sub_site 既定を 0 に変更。
  - Room DB version 17→18。R8 minify + shrinkResources 有効化、LazyColumn key、検索 debounce 等の UI/APK 最適化も同梱。
