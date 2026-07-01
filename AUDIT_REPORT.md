# Novel Reader コード監査レポート

**監査日**: 2026-06-28  
**対象バージョン**: 2.0.19 (versionCode 219) / Room DB v17  
**監査手法**: 12次元並列精読 → 各findingを別エージェントが反証検証（64エージェント）  
**結果**: 提起52件 → **確認46件 / 棄却6件**（Critical 1 / High 11 / Medium 17 / Low 17）

最優先: **データ破損・取りこぼし系**（バックアップ復元の既読消失、カクヨムマッピングのレース上書き、同期でのsite_type破壊、Syosetu欠番レジューム）と**仕様機能がUIから到達不能**（ルビ／短編・完結除外トグル）の2系統。

---

## 🔴 Critical

### C1. バックアップ復元で全話の既読・しおり・読書率が消失

- **ファイル**: [ImprovedDatabaseSyncManager.kt:505-512](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/sync/ImprovedDatabaseSyncManager.kt) / [DatabaseSyncManager.kt:315-322](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/sync/DatabaseSyncManager.kt) / [NovelRepository.kt:186-214](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/repository/NovelRepository.kt)
- **問題**: `syncEpisodes` が外部DBから `ncode/episode_no/body/e_title/update_time` だけを読み、`is_read`・`is_bookmark`・`reading_rate` を**一切読まない**。生成される `EpisodeEntity` はデフォルト(0,0,0f)。保存は `insertEpisodes(preserveExisting=true)` で内部DBに既存話があれば既読を維持するが、**内部DBに当該ncodeが存在しなければ**（L204-206のelse）既読ゼロのまま新規挿入される。
- **なぜ危険か**: エクスポート（DatabaseExportManagerはDBファイルを生コピー）したバックアップには既読が正しく入っているのに、インポート側がその列を読まないため、**自分のバックアップを復元しても既読・しおり・読書位置が全消失**する。
- **発生条件**: 機種変更・再インストール・新端末移行・DB初期化後の復元（当該ncodeがまだ内部に無い状態でのインポート）。バックアップ復元の最も典型的なユースケース。
- **壊れるデータ**: 全エピソードの `is_read=0 / is_bookmark=0 / reading_rate=0f`。数百話読了済みの作品が全話未読に戻り、しおりも消える。**恒久的**。
- **修正方針**: 外部episodesテーブルから `getColumnIndexSafely` で `is_read/is_bookmark/reading_rate` を読み取り `EpisodeEntity` に設定する。マージは「内部に既存があれば内部優先、無ければ外部値を採用」とし、外部由来の既読も復元できるようにする。

---

## 🟠 High

### H1. カクヨム `cachedMappings` がシングルトン共有可変状態で、並行取得時に別作品マッピングへ上書き

- **ファイル**: [KakuyomuAdapter.kt:65](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/adapter/KakuyomuAdapter.kt)（宣言）, L79（getCachedMappings）, L865/L877/L890/L1018（上書き） / [NovelSiteAdapterFactory.kt:20-25](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/adapter/NovelSiteAdapterFactory.kt)（objectシングルトンで唯一インスタンス）
- **問題**: `KakuyomuAdapter` はFactoryがプロセス唯一のインスタンスを保持。一方 `cachedMappings` はインスタンス可変フィールドで `parseEpisodeList` のたびに丸ごと上書き。`fetchNovelMetadataWithEpisodeList → getCachedMappings()` の2ステップが非アトミックなため、その間に別作品の `parseEpisodeList` が同一シングルトン上で走ると **getCachedMappings() が別作品の連番→EpisodeIDマッピングを返す**。
- **なぜ危険か**: ある作品のエピソードに**別作品の本文が保存される**、または全EpisodeIDが解決できず欠落。誤マッピングは `episode_mapping` に永続化され、以後の再DL・エラー修正でも誤本文を取得し続ける（**恒久データ破損**）。AutoUpdateWorker.kt L503-504 のコメントが開発側もこのレースを認識していることを示す。
- **発生条件**: 登録（RegistrationQueueManager）・自動更新（AutoUpdateWorker）・UpdateServiceのうち2つ以上が同時にカクヨム処理を実行し fetch が交錯したとき。
- **修正方針**: マッピングを共有可変状態にせず、`parseEpisodeList`/`fetchNovelMetadataWithEpisodeList` がMapを**戻り値として直接返す**（`NovelWithEpisodesAndMappings` 形式に統一）。`getCachedMappings()` を廃止。

### H2. `getNovelsByNcodes` のIN句が無制限でSQLite変数上限(999)超過クラッシュ

- **ファイル**: [NovelDescDao.kt:49-50](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/dao/NovelDescDao.kt) / [RecentlyReadNovelsScreen.kt:55-60](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/RecentlyReadNovelsScreen.kt) / [UpdateInfoScreen.kt:86-88](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/UpdateInfoScreen.kt), L727-729
- **問題**: `WHERE ncode IN (:ncodes)` は要素数だけ `?` を展開。件数が `SQLITE_MAX_VARIABLE_NUMBER`（多くの端末999）を超えると `SQLiteException: too many SQL variables`。画面側はチャンク分割せず全件ncodeを渡している。Repository自身の `findOrphanedEpisodeNcodes` は `chunked(500)` しており、**画面側だけガードが抜けている不整合**。
- **なぜ危険か**: 画面オープン時のLaunchedEffect内で例外→最近読んだ一覧・更新情報一覧が表示不能。L728は一括DL経路にもあり更新確認・一括DLが起動不能に。CLAUDE.md想定の「1000+ novels」運用で容易に発生。
- **発生条件**: `update_queue` または `last_read_novels` 紐づきncodeが概ね1000件超で対象画面を開く／一括処理実行。
- **修正方針**: 呼び出し側で `chunked(900)` + `flatMap`、またはRepository内部でチャンク分割して全件返す実装に。

### H3. processQueue起動直後のジョブ未登録ウィンドウで一時停止/キャンセルが無視され孤児tempが残る

- **ファイル**: [RegistrationQueueManager.kt:251-258](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/manager/RegistrationQueueManager.kt)（DBを先にPROCESSING化）, L421（jobをprocessingQueuesへ登録）, L173/L206（cancel/pause）
- **問題**: `processNextQueue` が先にDBを `STATUS_PROCESSING` にし、その後 `processQueue` の `scope.launch` 末尾(L421)で初めて `processingQueues[id]` に登録。「DBはPROCESSINGだがjobが未登録」の時間窓で `pauseQueue`/`cancelQueue` が呼ばれると、`processingQueues[id]==null` のため `cancel()/cancelAndJoin()` が**何もせず**実ジョブが走り続ける。
- **なぜ危険か**: 一時停止/削除したはずのキューがバックグラウンドでtemp_episodesを書き続ける。`cancelQueue` はjoin後にtemp削除するが、join対象がnullなので削除後にジョブがtempを再生成し**孤児temp_episodes**が残る。
- **発生条件**: 監視ループがPENDING→PROCESSINGにした直後（数ms〜launchスケジューリング遅延）にDL状況画面から一時停止/削除/一括削除。
- **修正方針**: `CoroutineStart.LAZY` でjob生成→`processingQueues[id]=job`→`job.start()` とアトミックに登録する。またはPROCESSINGへのDB更新をmap登録後に移す。

### H4. Syosetuで途中失敗した話がMAX(episode_no)レジュームで恒久欠番化

- **ファイル**: [RegistrationQueueManager.kt:294-306](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/manager/RegistrationQueueManager.kt)（resumeFrom）, L535, L541-561（skipループ） / [EpisodeDao.kt:93](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/dao/EpisodeDao.kt)（MAX(CAST)）
- **問題**: レジューム点をtemp/本体の `MAX(episode_no)` で決定。途中の話が `fetchEpisodeWithRetry==null`（本文空・パース失敗）だとcontinueでスキップして先へ進む。第5話が失敗し第6〜20話成功なら MAX=20 → リトライ時 startFrom=21 となり**第5話は二度と取得されない**。
- **なぜ危険か**: 恒久的な欠番。`total_ep` は `general_all_no`（正しい総数）で保存されるため**UI上は全話揃って見えるのに実体に穴**があり、読者は気づけない。
- **発生条件**: DL中に特定話が一時失敗→後続は成功→その後DL全体がタイムアウト/エラー/一時停止でリトライ。
- **修正方針**: レジュームを「実在episode_no集合の欠落分のみ再取得」に。最低限、失敗話をtempに `body_empty` マーカーで記録しリトライ対象に含める。

### H5. 未サニタイズの本文HTMLをJS有効＋JSブリッジ露出＋ファイルアクセス許可のWebViewで実行

- **ファイル**: [EpisodeViewScreen.kt:979-1027](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/EpisodeViewScreen.kt)（WebView設定）/ [KakuyomuAdapter.kt:1453-1505](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/adapter/KakuyomuAdapter.kt)（本文divの `.html()` を保存）
- **問題**: `EnhancedHtmlRubyWebView` が `episode.body`（外部サイトのスクレイプ生HTML）を**サニタイズ無し**で `loadDataWithBaseURL` に流す。同WebViewは `javaScriptEnabled=true`・`addJavascriptInterface(WebViewScrollInterface,"Android")`・`allowFileAccess=true`・`allowContentAccess=true`。`<script>` 除去やJsoup.clean/Safelistは**コード中に一切無い**。
- **なぜ危険か**: 第三者が書いた本文中の `<script>`/`on*=` がWebView内で任意JS実行（XSS）。`allowFileAccess`/`allowContentAccess` 有効のため `file://`/`content://` でローカルリソース読み取りを試行可能。`Android.saveScrollPosition(任意値)` で `reading_rate` を任意上書きし読書位置データを破壊可能。
- **発生条件**: `<script>`/`on*=` を含む本文を横書きモードで開く。カクヨムはinnerHTMLをそのまま保存するためサイト側変化や悪意投稿で混入しうる。
- **修正方針**: 表示前に `Jsoup.clean(body, Safelist…)` で危険タグ/イベント属性除去。JSブリッジ使用WebViewでは `allowFileAccess=false`、baseUrlをアプリ専有ディレクトリに限定。ブリッジ側で `position.coerceIn(0f,1f)`。

### H6. 前後話ナビでEpisodeViewがバックスタックに無限蓄積

- **ファイル**: [MainActivity.kt:386-401](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/MainActivity.kt)（onPrevious/onNext）/ [NavigationManager.kt:28-31](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/navigation/NavigationManager.kt)（navigatoToがpush）
- **問題**: onPrevious/onNextが `navigateTo(Screen.EpisodeView(...))` を呼び、毎回現在画面をpush。次話・前話を読むたびにEpisodeViewフレームが1つずつ積み上がる。
- **なぜ危険か**: プロジェクトルール「navigateToによるスタック増殖をしない」に直接違反。100話読むと戻るボタンを100回押さないと目次へ抜けられない（機能不全）。各フレームがWebView/縦書きビューを保持しメモリ肥大。
- **発生条件**: 「次の話」「前の話」を連続タップして読み進める。
- **修正方針**: NavigationManagerに `replaceCurrent(screen)` を追加し、onPrevious/onNextから呼ぶ。

### H7. ルビ自動変換トグルが実UIから到達不能（保存呼び出しがorphan画面のみ）

- **ファイル**: [SettingsScreen.kt:536](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/SettingsScreen.kt)（orphan画面の保存）, L1864-1969（本番のSettingsReadingScreen＝トグル無し）
- **問題**: `saveAutoRubyEnabled()` を呼ぶUIは旧画面 `SettingsScreenUpdated`（`Screen.Settings`）のみ。本番ハブが遷移する `SettingsReadingScreen` にはルビトグルが**存在せず**保存も呼ばれない。旧 `Screen.Settings` はメニューから到達不能。
- **なぜ危険か**: CLAUDE.md記載機能「自動ルビ変換ON/OFF」が**実質死んでいる**。デフォルトON（SettingsStore.kt:209）のため切りたいユーザーが永久に切れない。
- **発生条件**: 設定→読書設定への通常操作。
- **修正方針**: `SettingsReadingScreen` に状態・スイッチ・読込・`saveAutoRubyEnabled` 呼び出しを追加。

### H8. 短編・完結の更新除外トグルが実UIから到達不能

- **ファイル**: [SettingsScreen.kt:1232](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/SettingsScreen.kt)/L1258（orphan画面のトグル）, L2040-2131（本番SettingsAutoUpdateScreen＝トグル無し）
- **問題**: `saveExcludeShortFromUpdate`/`saveExcludeCompletedFromUpdate` を呼ぶUIは旧 `SettingsScreenUpdated` のみ。本番の自動更新サブ画面にトグルが無く保存も呼ばれない。
- **なぜ危険か**: v2.0.17/v2.0.18機能「短編/完結を更新確認から除外」がUIから設定不可。`excludeShortFromUpdate` はデフォルトONのため、短編の更新を見たいユーザーが解除できず**短編の新規話・連載化が永久に未検出**。
- **発生条件**: 設定→自動更新への通常操作。
- **修正方針**: `SettingsAutoUpdateScreen` に両トグルの状態・スイッチ・読込・保存を追加。

### H9. 旧DatabaseSyncManagerがsite_type/sub_site/end_flagを保全せず、カクヨム作品をなろう扱いで破壊

- **ファイル**: [DatabaseSyncManager.kt:180-284](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/sync/DatabaseSyncManager.kt)（syncNovelDescs, エンティティ生成L220-236, URLEntity生成L240-257）/ [NovelDescDao.kt:23-27](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/dao/NovelDescDao.kt)（@Insert REPLACE）
- **問題**: `syncNovelDescs` が `site_type`・`sub_site`・`end_flag`・`is_favorite` を読まず、生成EntityがデフォルトのSyosetu値になる。`insertNovels` は `OnConflictStrategy.REPLACE` で既存を丸ごと置換。L240-257でratingだけ見て無条件にSyosetuのapi_url/web_urlを生成し、カクヨム作品にもなろうAPI URLを付与。
- **なぜ危険か**: REPLACE置換で既存カクヨム作品の `site_type=2` が1に上書きされ、お気に入り・完結フラグ・サブサイトも消失。site_typeが壊れると `NovelSiteAdapterFactory` が誤アダプタ（なろう）を選び、**更新・本文取得・作者ページが全誤動作**。
- **発生条件**: SettingsScreen経由の旧DatabaseSyncManagerで、カクヨム作品/お気に入り/完結フラグを持つ内部DBへ、それら列を持たない外部DBを同期。
- **修正方針**: `getNovelByNcode` で既存取得し外部に無い列は既存値を保持。URLEntity生成はsite_type分岐（カクヨムに付与しない）。可能なら呼び出しをImprovedDatabaseSyncManagerに統一。

### H10. インポートがepisode_mappingを全く同期せず、カクヨム作品が本文取得不能に

- **ファイル**: [ImprovedDatabaseSyncManager.kt:431-586](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/sync/ImprovedDatabaseSyncManager.kt)（mapping非参照）/ [DatabaseSyncManager.kt](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/sync/DatabaseSyncManager.kt)（同）/ [NovelRepository.kt:970-1003](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/repository/NovelRepository.kt)（getEpisodeWebUrl）
- **問題**: 両同期マネージャとも `episode_mapping` テーブルを読み書きしない。エクスポートはファイルコピーなのでバックアップには mappingが含まれるのに、インポートで復元されない。
- **なぜ危険か**: 新端末復元でカクヨムの `episodes` 行だけ入り `episode_mapping` が空に。以降 `getKakuyomuEpisodeId` がnullを返し正しいURLを組めず、**再DL・更新・エラー修正が破綻**。規約「episode_mapping保存漏れ禁止」違反。
- **発生条件**: カクヨム作品を含むバックアップを、その mappingを持たない端末へインポート。
- **修正方針**: 同期に `episode_mapping` の読み取り・`insertMappings` を追加し、episodesと同一トランザクションで保存。

### H11. 欠落修正スキャンがライブラリ全件にN+1ネットワーク（一括APIを使わない）

- **ファイル**: [UpdateInfoScreen.kt:277-355](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/UpdateInfoScreen.kt)（特にL283/L309/L351）/ 対比の一括取得はL559-567（fetchNovelInfoBatch）
- **問題**: 「欠落修正」スキャンが全件を `forEachIndexed` で逐次ループし、各作品ごとにSyosetuは `fetchNovelInfo`（個別API）、カクヨムは `fetchUpdateSummary` を1回ずつ呼ぶ。全更新確認では `fetchNovelInfoBatch` でOR検索一括取得しているのに、**このスキャンだけ一括を一切使わない**。
- **なぜ危険か**: 1000作品で1000リクエスト（+100ms×件で最低100秒）。最適化方針「100作品=1リクエスト」に真っ向から反し、API過剰アクセス。
- **発生条件**: 「欠落修正」→「スキャン開始」を、評価0-2の作品多数で実行。
- **修正方針**: Syosetuは `fetchNovelInfoBatch`（rating で分離・100件チャンク）でまとめて取得し、結果Mapで欠番判定。個別fetchは取れなかった作品のみのフォールバックに。

---

## 🟡 Medium（17件）

| # | タイトル | ファイル:行 | 要点 / 修正 |
|---|---|---|---|
| M1 | カクヨム `applyRateLimit` のcheck-then-act非アトミックで0.5秒制限が破れる | [KakuyomuAdapter.kt:424-430](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/adapter/KakuyomuAdapter.kt) | `@Volatile` は可視性のみ。30並列(AutoUpdateWorker)が同時にelapsed≥500msと判断し同時アクセス→403/遮断。**Mutexでレート制限区間を直列化**。 |
| M2 | R18のsub_siteを常にノクターン(2)固定でムーンライト/ミッドナイトを誤分類 | [NovelApiUtils.kt:648](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/api/NovelApiUtils.kt) | `if(isR18) 2 else 1`。媒体フィルタ誤動作。判定不能なら**0(不明)**にし誤確定を避ける。 |
| M3 | カクヨム新規登録でnovel・episode・mapping保存が非アトミック | [NovelRepository.kt:696-733](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/repository/NovelRepository.kt)/L838-863 | 途中失敗で「mapping欠落の壊れたリンク」or「孤立ncode」が残る。**insertNovel+insertEpisodeMappingsをrunInTransactionに**。 |
| M4 | MigrationTestがスキーマJSON欠落で全件失敗し、移行安全網が機能不全 | [MigrationTest.kt:293](Novel_reader/app/src/androidTest/java/com/shunlight_library/novel_reader/data/database/MigrationTest.kt)ほか | schemasに16/17.jsonしか無くcreateDatabase(4/9/10/11)がFileNotFoundで落ちる。過去のidx_image_cache_hash漏れと同種の回帰を検知不能。旧版JSON生成or16→17検証に絞る。 |
| M5 | AutoUpdateWorkerの更新判定が `total_ep` 基準で画面側(general_all_no)と不整合 | [AutoUpdateWorker.kt:242](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/worker/AutoUpdateWorker.kt) | 検出時に `general_all_no` を永続化せず、stale値がUpdateInfoScreenの件数差分計算に混入し件数不整合。判定をgeneral_all_noに統一。 |
| M6 | UpdateInfoScreenの全更新確認がupdate_queueをリセットしない | [UpdateInfoScreen.kt:539](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/UpdateInfoScreen.kt) | Workerは `clearAllUpdateQueue` するが画面側は呼ばず古いキューが残存。**開始時にclearAllUpdateQueue**を。 |
| M7 | `is403Error` がスタックトレース文字列の"403"部分一致で誤検知 | [UpdateService.kt:294-306](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/service/UpdateService.kt) | 行番号403等で誤って10分遅延再開。**responseCode==403か"HTTP 403"厳密一致**に。 |
| M8 | なろう一括取得フォールバックが取得失敗作品でtotal_epを返し新着取りこぼし | [AutoUpdateWorker.kt:224-229](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/worker/AutoUpdateWorker.kt) | `apiInfo?.generalAllNo ?: novel.total_ep` で「更新無し確定」。失敗時は判定スキップ(-1)してリトライ可能に。 |
| M9 | ToC復帰バナー判定が固定 `delay(500)` 依存で大量話数に誤発火/取りこぼし | [EpisodeListScreen.kt:137-138](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/EpisodeListScreen.kt) | FlowのinitialEmitが500ms以内に来る保証なし→誤って「目次不完全」表示→不要なスタブ生成・total_ep上書き。COUNTクエリ比較orCollect内判定に。 |
| M10 | 横書きWebViewでフォントサイズ変更時にスクロール位置が失われる | [EpisodeViewScreen.kt:915-924](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/EpisodeViewScreen.kt) | 縦書きにはrestoreRate対策があるが横書きに無い。再ロード前に現在scrollRatioを取得し復元。 |
| M11 | `navigateBackTo` が目的画面不在時にスタックを全消費し中間画面消失 | [NavigationManager.kt:49-61](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/navigation/NavigationManager.kt) | 見つからないとpopした中間フレームを復元せずfinish直行。`indexOfLast` で探索し、見つかった時のみtruncate、無ければnavigateBackフォールバック（非破壊化）。 |
| M12 | NavigationManagerが回転/プロセス再生成で状態を失う | [MainActivity.kt:91](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/MainActivity.kt) | onCreateで毎回new、rememberSaveable/SavedState化されず回転でMainへ。ScreenをParcelable化し保存。（読書位置自体はDB永続なのでデータ破損ではない） |
| M13 | RecentlyReadNovelsScreenのフィルタ設定が永続化されない | [RecentlyReadNovelsScreen.kt:51](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/RecentlyReadNovelsScreen.kt) | `remember` のみでDataStore未使用。規約「フィルタ・ソートは永続化」違反。NovelListScreenのパターンを踏襲。 |
| M14 | `preserveExisting=false` 時にREPLACEで全話が既読ごと破壊 | [NovelRepository.kt:212-214](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/repository/NovelRepository.kt) | C1と複合。設定名から既読消失は予期しづらい。既読・しおり・rateは常にマージ保護。 |
| M15 | novels_descsバッチ挿入が非トランザクションでtotal_epと実話数が乖離する半壊DB | [ImprovedDatabaseSyncManager.kt:404-407](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/sync/ImprovedDatabaseSyncManager.kt) | 2フェーズ分離・外側トランザクション無し。途中失敗で `total_ep>実話数`。ncode単位withTransactionor完了後にCOUNTでtotal_ep再計算。 |
| M16 | 同期コールバックがUIスレッド境界を制御せずCompose StateをIOから直接更新 | [DatabaseSyncActivity.kt:164-186](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/ui/DatabaseSyncActivity.kt) | スナップショット例外/クラッシュリスク。`withContext(Main)` でラップorFlow+collectAsState。 |
| M17 | 通知アクションReceiverが二重定義かつ未配線で「すべてダウンロード」が機能しない | [DownloadAllReceiver.kt:20-41](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/receiver/DownloadAllReceiver.kt) / [DownloadActionReceiver.kt:16-41](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/receiver/DownloadActionReceiver.kt) | 同一アクションが2クラスに重複、片方未登録、`sendBroadcast` 発火箇所が0件、通知に `addAction` 無し。**ドキュメント記載機能が完全に不達**。通知にAction追加しFLAG_IMMUTABLEで配線、重複削除。 |

---

## 🟢 Low（17件）

- **L1** [KakuyomuAdapter.kt:1607-1619](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/adapter/KakuyomuAdapter.kt): `fetchEpisodeDetails` がエラー本文 `★HTMLページ読み込みエラー` を `normalizeEpisodeBody` を通さずTripleで返す。将来この戻り値を保存する経路が追加されるとエラー文字列が本文化。→ 全fetch経路でエラー時 `""` に統一。
- **L2** [KakuyomuAdapter.kt:1819-1836](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/adapter/KakuyomuAdapter.kt): 完結検出が `doc.text()` 全体の「完結済」部分一致でレビュー/関連作品の語に誤反応。→ 作品自身のステータスバッジ限定セレクタ＋JSON `serialState` 優先。
- **L3** [NovelRepository.kt:192](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/repository/NovelRepository.kt): `insertEpisodes(preserveExisting)` がncode毎に `getEpisodesByNcode().first()`（SELECT * 本文込み）で既存全話本文をロード。EpisodeMeta軽量化方針に反す。→ 既読系のみの軽量射影で取得。
- **L4** [NovelDatabase.kt:280-281](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/database/NovelDatabase.kt): MIGRATION_15_16が全R18作品を `sub_site=2` 一律初期化（ムーンライト/ミッドナイト誤分類）。→ R18は `0(不明)` のままにし後続更新で確定（M2と同根）。
- **L5** [UpdateService.kt:65](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/service/UpdateService.kt): `operationQueue`（非スレッドセーフArrayDeque）と非volatile `isRunning` をメイン/IO両スレッドから操作しレース。最悪オペレーション未実行でstopSelf。→ ConcurrentLinkedDeque + 単一ディスパッチャ集約、isRunningを @Volatile。
- **L6** [RegistrationQueueManager.kt:180-183](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/manager/RegistrationQueueManager.kt): `cancelQueue` のtemp削除とキュー削除がトランザクション外で非アトミック。temp削除がncode基準で再キュー時に他キューを巻き込む。→ withTransaction＋`deleteTempEpisodesByQueueId` 基準に。
- **L7** [NovelRepository.kt:1163-1184](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/repository/NovelRepository.kt): `mergeTempEpisodesToMain` が非トランザクションで途中失敗時に重複/部分統合。→ コピー＋temp削除をwithTransactionで一括化。
- **L8** [RegistrationQueueManager.kt:498-500](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/manager/RegistrationQueueManager.kt)/L558-560: 進捗更新を毎話 `scope.launch` で投げ放ち→順序逆転で `current_episode` 巻き戻り、キャンセル未伝播。→ 親suspendで直接await。
- **L9** [RegistrationQueueManager.kt:121-151](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/manager/RegistrationQueueManager.kt): `addToQueue` の重複チェックがcheck-then-insertで非アトミック→二重キュー登録。→ unique index + OnConflict.IGNOREまたはMutex直列化。
- **L10** [EpisodeListScreen.kt:188-192](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/EpisodeListScreen.kt): ToC再構築で `total_ep` をDB実数でなく `mappings.size` に上書き。→ 挿入後にCOUNT/MAXで再取得して設定。
- **L11** [EpisodeListScreen.kt:1901-1916](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/EpisodeListScreen.kt): LazyColumnの `items` にkey未指定（位置ベース）＋未使用isRead計算。→ `key={it.episode_no}`、不要計算削除。
- **L12** [EpisodeViewScreen.kt:760-769](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/EpisodeViewScreen.kt): `saveScrollPosition` が範囲クランプ無しで `reading_rate` 保存。JS側のみクランプ＝信頼境界が誤り。→ ネイティブ側で `coerceIn(0f,1f)`（H5と複合）。
- **L13** [NovelReaderApplication.kt:41](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/NovelReaderApplication.kt): `instance` を非volatile lateinitで、別スレッドから参照（可視性リスク）。→ `@Volatile`。
- **L14** [ImprovedDatabaseSyncManager.kt:262](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/data/sync/ImprovedDatabaseSyncManager.kt): 進捗スロットリング `abs(lastProgress?.progress ?: 0f - progress.progress)` が演算子優先順位ミスで常にtrue化（スロットリング無効）。→ `abs((lastProgress?.progress ?: 0f) - progress.progress)` に括弧追加。
- **L15** [UpdateInfoScreen.kt:585-592](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/UpdateInfoScreen.kt): 一括更新確認の `workCount`/`episodeCount` を並列asyncから非同期加算（データ競合）。→ AtomicIntegerまたはawaitAllの戻り値合算。
- **L16** UpdateInfoScreen.ktほか多数: `AppLogger` を経由せず `android.util.Log` 直接使用→**リリースビルドでもログ出力**。規約違反。→ `AppLogger` に置換。
- **L17** [UpdateInfoScreen.kt:244-246](Novel_reader/app/src/main/java/com/shunlight_library/novel_reader/UpdateInfoScreen.kt): 欠落修正の短編除外が `noveltype!=2` のみで site_typeを見ず、カクヨムにも適用。全更新確認はSyosetu限定なのに不一致。→ `site_type != SITE_TYPE_SYOSETU || noveltype != 2` に揃える。

---

## 棄却した提起（6件）

検証で「コードは実在するが現状の呼び出しグラフでは実害が出ない」と判定したもの。将来の改修で顕在化しうる潜在リスクとして記録。

1. **SyosetuAdapterのインターフェース経由メソッドがR18を常にfalseハードコード** — 当該コードはデッドコード。Repositoryは常に別メソッドで振り分けており到達不能。将来else分岐にSyosetuを流すと顕在化。
2. **`end` フィールドの `as? Int` キャスト型不一致取りこぼし** — `end` は1/2のみでSnakeYAMLは常にIntegerを返しnullにならない。他フィールドとの防御的スタイル不整合だが実害なし。
3. **R18のuserid保存で作者ページ破綻** — `resolveAuthorPageUrl` がR18では `startsWith("x")` ガードで数値useridを確定的に無視し `fetchR18AuthorId` へフォールバック自己修復。整合は崩れない。
4. **カクヨム並列更新確認でgetCachedMappings()競合** — 更新確認フェーズは `fetchUpdateSummary`（cachedMappings非参照）を使い、DLフェーズは逐次ループで同時実行しないため現状レース無し。DL並列化で顕在化する潜在リスク。
5. **`resetStuckProcessingQueues` が生存ジョブを巻き戻し二重実行** — `startMonitoring` の唯一の呼び出し元はApplication.onCreate（プロセス1回）でreset時点でprocessingQueuesは空。二重実行は起きない。
6. **DatabaseSync画面が再コンポーズでActivity多重起動** — DatabaseSyncがcurrentの時スタックは常に非空でnavigateBack()がtrueを返し同期的に遷移、LaunchedEffect(Unit)は再評価されずstartActivityは1回のみ。多重起動は到達不能。

---

## 残留リスク・未検証範囲

- **実機マイグレーション検証は未実施**: MigrationTestがスキーマJSON欠落で実行不能のため、**v1→v17のマイグレーションチェーンが実機で正しく走るかは本監査でも未保証**。`./gradlew connectedAndroidTest` を要する。
- **動的レース再現は未実施**: H1/H3/M1などのレースはタイミング依存で、静的解析と開発側コメントから成立を確認したが実機での再現タイミング計測は未実施。
- **WebViewの実XSS実証は未実施**: H5はサニタイズ不在と設定を確認したが、実際に悪意ある本文を流すPoCは未作成。
- **テスト不足の具体**: 最低限 C1/H4/H10 を再現する instrumentedテストの追加を推奨。未検証の重要仕様＝(a)syncインポートの既読保全、(b)カクヨムepisode_mappingのラウンドトリップ、(c)レジューム時の欠番有無、(d)レート制限の並行直列化、(e)IN句上限。

---

## 着手優先順

1. **C1** — バックアップ復元で既読全消失（最重要・ユーザー信頼直撃）
2. **H1, H10, H9** — データ破損3点（カクヨムマッピング・マッピング同期漏れ・site_type破壊）
3. **H7, H8, M17** — 機能死3点（ルビトグル・更新除外トグル・通知アクション）
4. **H2** — IN句上限超過による大量登録ユーザーでの機能停止
5. **H3, H4** — 競合状態・欠番レジューム（信頼性）
6. **M1, M4** — レート制限レース・MigrationTest（安全網整備）

---

*監査者: Claude Sonnet 4.6（claude-sonnet-4-6）、2026-06-28*  
*検証: 12次元×並列Find→Verify（64エージェント）、反証棄却6件*
