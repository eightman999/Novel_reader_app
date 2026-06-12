# コードベース全体監査レポート (2026-06-11)

対象: Novel_reader_app (バージョン 2.0.6 / DB v16)
監査範囲: ①ダウンロード・サイトアダプター層 ②データベース・マイグレーション・リポジトリ ③バックグラウンド処理・自動更新・通知 ④UI・パフォーマンス・多サイト対応

---

## 🔴 最優先（機能が壊れている / データ消失・クラッシュ）

### 1. 手動更新が一切実行されない回帰
- `service/UpdateService.kt:115-141`
- 最初の `ACTION_START_UPDATE` でオペレーションを `operationQueue` に積まずに `processNextOperation()` を呼ぶため、キューが空 → 即「全ての更新処理が完了しました」通知 → 3秒後に stopSelf。手動の更新チェック/再DL/エラー修正/一括更新がすべて空振りする。コミット ae467cd のキュー化リファクタで混入。
- 修正: 分岐内で `operationQueue.add(UpdateOperation(ncode, requestedUpdateType))` してから `processNextOperation()`。

### 2. update_queue のフィールド逆転（3観点の監査すべてで検出）
- `worker/AutoUpdateWorker.kt:205-209`
- `total_ep=API最新値, general_all_no=ローカル値` で格納しており、エンティティ定義・`UpdateService.kt:511-516`・`UpdateInfoScreen.kt:586-591` の正準（total_ep=現状、general_all_no=最新）と真逆。自動DL無効時、未取得話数が0/負数になり一括DLが全件スキップ → **#1と組み合わさると自動DLオフ環境では更新が一切取り込めない**。
- 修正: `total_ep=novel.total_ep, general_all_no=latestEpisodeCount` に統一。`AutoUpdateWorker.kt:296-303` の `downloadEpisodesForNovel` 引数も反転修正。

### 3. カクヨム登録キューの ncode 不整合 — 新規登録が本体DBに統合されない
- `manager/RegistrationQueueManager.kt:100, 275-287, 322-383, 157`
- `addToQueue` は生の workId 数字列を `queue.ncode` に保存するが、temp_episodes・novels_descs・episode_mapping は疑似Ncode（"K"+Base62）で保存される。完了時の `mergeTempEpisodesToMain(queue.ncode)` が0件ヒット → **エピソードが episodes テーブルへ統合されず temp行が孤児として永久残留**。タイムアウトレジューム・重複登録チェック・キャンセル時のtemp掃除もすべて機能しない。
- 修正: `addToQueue` でカクヨムは `PseudoNcodeGenerator.generateKakuyomuNcode(novelId)` に変換してから格納（1箇所の修正で merge/resume/dup/cleanup すべて復旧）。

### 4. 通知の「すべてダウンロード」がカクヨム新着で系統的に失敗
- `service/UpdateService.kt:1241-1261`（performBulkUpdate）
- サイト分岐なしで `NovelApiUtils.fetchEpisodeWithRetry` を使用。カクヨム新着話は episode_mapping 未登録のため連番をカクヨムIDとして URL 生成 → 404 → 全話失敗。マッピング更新処理もない（CLAUDE.md ルール14違反）。
- 修正: カクヨム分岐を追加し `fetchNovelMetadataWithEpisodeList` で mapping 更新後に本文取得。

### 5. KakuyomuAdapter の cachedMappings 共有可変状態 — 別作品の本文混入リスク
- `data/adapter/KakuyomuAdapter.kt:65,79,816-831` + `NovelSiteAdapterFactory.kt:22-25`
- シングルトン共有インスタンスの `cachedMappings` を AutoUpdateWorker と UpdateService/UI 操作の双方が「fetch→getCachedMappings」の2段階で利用。ncode単位ロックは別小説間の共有状態を守らないため、並行処理時に**別作品のエピソードIDで本文を取得・保存**し得る。
- 修正: fetch系メソッドがマッピングを戻り値で返す設計にして可変共有状態を排除。

### 6. DB同期の互換性チェックがテーブル名不一致で破綻
- `data/sync/ImprovedDatabaseSyncManager.kt:144` vs `:567,580`
- チェックは `rast_read_novel` を必須とするが読取は `last_read_novel` を照会。本アプリ自身のエクスポートが生成するDBは `last_read_novel` のみ → **自分のバックアップのインポートが常に拒否される**。レガシーDBなら逆に途中で SQLiteException。
- 修正: `isTableExists` で動的判定し、存在する方を要求・照会。

### 7. DB同期で is_favorite / site_type / sub_site / end_flag が消失
- `data/sync/DatabaseSyncManager.kt:220-236` / `ImprovedDatabaseSyncManager.kt:337-356`
- 外部DBからこれらを読まずデフォルト値（site_type=1, is_favorite=0）で REPLACE 上書き。**同期でお気に入り全消失、カクヨム作品がなろう扱いに化けてアダプター誤選択**。
- 修正: 外部DBに列があれば読み、無ければ内部DBの既存値とマージ。

### 8. 同期バッチで既読・しおり・読書率がリセット
- `data/sync/DatabaseSyncManager.kt:310-336` + `NovelRepository.kt:168`
- 20件バッチが複数ncodeをまたぐと `insertEpisodes(preserveExisting=true)` が先頭ncodeの既存分しか参照せず、他ncodeの既読情報が REPLACE で0に。
- 修正: 保存前に `groupBy { it.ncode }` してncode単位で呼ぶ。

### 9. v6以前からのアップグレードで Room スキーマ検証クラッシュ
- `NovelDatabase.kt:110-112` + `ImageCacheEntity.kt:11`
- MIGRATION_6_7 が `idx_image_cache_hash` を作成するがエンティティは indices 未宣言。Room 2.7.0 は余分なインデックスも不一致扱い → "Migration didn't properly handle: image_cache" でクラッシュ。
- 修正: エンティティに `Index(value=["hash"], name="idx_image_cache_hash")` を追加。

### 10. Android 13+ で通知が全て無音破棄（POST_NOTIFICATIONS 未要求）
- `MainActivity.kt` 全体 / `AndroidManifest.xml:7`
- マニフェスト宣言のみでランタイム要求がない（targetSdk=34）。自動更新結果・エラー・FGS通知が表示されない。
- 修正: 起動時に `ActivityResultContracts.RequestPermission` で要求。

### 11. アプリ起動毎の REPLACE 再スケジュールが実行中の自動更新を殺す
- `NovelReaderApplication.kt:62-68` + `worker/AutoUpdateScheduler.kt:53-57`
- Worker実行のためのプロセス起動時にも `ExistingPeriodicWorkPolicy.REPLACE` で再enqueueされ、実行中ワークがキャンセルされ得る。さらに `AutoUpdateWorker.kt:111-136` の `catch (e: Exception)` が CancellationException を握り潰し「自動更新エラー」通知に化ける。
- 修正: 起動時復元は UPDATE/KEEP に、REPLACE は設定変更時のみ。catch 先頭で `if (e is CancellationException) throw e`。

### 12. 縦書きモードの読書進捗が保存されない
- `ui/components/VjapVerticalTextView.kt:61-73` + `EpisodeViewScreen.kt:89,190-195`
- `onReadingRateChanged` が「復元直後」と「最終ページ」でしか発火せず、途中のページ送りが追跡されない。保存されるのは開いた時点のレートか1.0のみ（横書きはJS監視で逐次保存＝パリティ欠陥）。
- 修正: ページ変更コールバックを追加し毎ページ通知。

### 13. カクヨムHTMLフォールバック経路で NumberFormatException クラッシュ / 全話スキップ
- `data/adapter/KakuyomuAdapter.kt:869-944`
- 方法3（sidebar/目次とも失敗時）で `episode_no` に19桁のカクヨムIDを格納。`fetchNovelWithEpisodesIncludingMappings:194` の `toInt()` で即クラッシュ、`RegistrationQueueManager.kt:458` では全話を黙ってスキップ。cachedMappings 未更新で別作品のマッピング残留（クロス汚染）も。
- 修正: 方法3でも連番 episode_no＋マッピング構築。fetch系メソッド冒頭で `cachedMappings` リセット。

### 14. TopAppBar の戻るボタンでバックスタックが増殖
- `MainActivity.kt:330, 341-343`
- 戻るが `navigateBack()` ではなく `navigateTo(Screen.Main)` 等（navigateTo は現画面を push）。戻るたびにスタックが増え、システムバックで過去画面が延々再表示。EpisodeList の `source` も無視される。
- 修正: onBack は `navigateBack()`（EpisodeList は `navigateBackTo(source)`）へ統一。

---

## 🟠 中（パフォーマンス劣化・ルール違反・条件付き不具合）

### ダウンロード・アダプター層
- **D1** `UpdateInfoScreen.kt:291` — 欠落修正スキャンが `fetchNovelWithEpisodes` で**全話本文をDL**して話数カウントのみに使用（1000話作品で約10分＋数十MB浪費）。→ `fetchUpdateSummary()` に置換。
- **D2** `EpisodeListScreen.kt:654-667` — カクヨム再取得が repository 引数なしの旧方式で全話メモリ蓄積（逐次保存ルール違反、OOMリスク）。→ `repository` を渡すストリーミング方式に。
- **D3** `api/NovelApiUtils.kt:530-540` — カクヨム分岐がタイトル取得のため同一ページを `Jsoup.connect` で**レート制限なしに二重取得**。タイトル優先順位から `p.widget-episodeTitle` も欠落。→ `fetchEpisodeDetails()` に差し替え。
- **D4** `worker/AutoUpdateWorker.kt:445-559` — カクヨムDLで episode_mapping を保存しない（ルール14違反）。新着話の閲覧URL生成・エラー修正が失敗する。→ `getCachedMappings()` を `insertEpisodeMappings()` で保存。
- **D5** `KakuyomuAdapter.kt:424-430` / `NovelApiUtils.kt:57-63` — レート制限の `lastAccessTime` が非volatile・無同期。30並列バッチで0.5秒間隔が実質無効化。→ Mutex で保護。
- **D6** `api/NovelApiUtils.kt:967-988` — 改稿チェックが `<span title="...改稿">` の title 属性を読まず初出掲載日を参照、**改稿が検出されない**。→ `span[title]` から改稿日時をパース。
- **D7** `UpdateService.kt:1305-1312` / `AutoUpdateWorker.kt:296-325` / `UpdateInfoScreen.kt:1057-1062` — DL失敗話があっても total_ep を最新値に更新しキューを削除 → **欠番が恒久化**（エラー修正以外で復旧不能）。→ 失敗がある場合は実保存数で更新しキュー保持。
- **D8** `RegistrationQueueManager.kt:498-510` — なろう登録で total_ep をDL前に確定。途中失敗しても不足分が更新チェックで検出されない。→ 完了時に実保存数で上書き。
- **D9** `UpdateService.kt:619` — 再取得（UPDATE_TYPE_DOWNLOAD）が全削除→再挿入のため**既読・しおり・読書率・マッピングが全消失**。途中キャンセルで中途半端な状態が残る。→ 削除せず上書きマージに。
- **D10** `RegistrationQueueManager.kt:574-579` — 進捗更新のたびに `updateNovelInfo(id, "", total)` でキューの **title を空文字上書き**（DL状況画面が空欄に）。→ total のみ更新する専用クエリに分離。
- **D11** `EpisodeListScreen.kt:995-1007` — エラー修正（カクヨム）が「★HTMLページ読み込みエラー」文をそのまま本文として保存し成功扱い。→ UpdateService 同等の判定＋再試行を追加。
- **D12** `DatabaseSyncManager.kt:242-244` — `&json` 付き api_url を生成・保存（CLAUDE.md の YAML ルール違反）。→ `getOrCreateURL` と同一形式に統一。

### バックグラウンド・サービス
- **B1** `UpdateService.kt:306-338` — 403時の再開が `PendingIntent.getService`＋exact alarm。API26+のBG起動制限で機能せず、API31-32では `canScheduleExactAlarms()` 未チェックで SecurityException の恐れ。→ WorkManager フォールバック。
- **B2** `AndroidManifest.xml:16-17` — `USE_EXACT_ALARM` 宣言は Play 審査リジェクトリスク。→ 削除して `SCHEDULE_EXACT_ALARM`＋権限チェックに一本化。
- **B3** `receiver/DownloadAllReceiver.kt:36` — 通知アクションから `startService()`（API26+で IllegalStateException）。`ACTION_DOWNLOAD_ALL` の送信側も未実装、`DownloadActionReceiver` はマニフェスト未登録のデッドコード。
- **B4** `worker/AutoUpdateScheduler.kt:87-99` — 手動更新が非uniqueの enqueue で多重並走可能（`clearAllUpdateQueue` が互いのキューを消し合う）。→ `enqueueUniqueWork(KEEP)`。
- **B5** `worker/AutoUpdateWorker.kt:86-90` — setForeground 失敗時にBG継続 → 非FGS Workerの約10分制限で大量DLが途中キャンセル。
- **B6** `UpdateService.kt:63,91` — operationQueue / updateListeners / isRunning をメインと IO から無同期アクセス。

### データベース・同期
- **DB1** `NovelRepository.kt:379-393, 249-303, 1126-1147` — `deleteNovelWithRelations`（5テーブル削除）等の複合書込にトランザクションなし（プロジェクト全体で withTransaction 使用ゼロ）。→ `database.withTransaction {}` で包む。
- **DB2** `RegistrationQueueManager.kt:66-79` — プロセス強制終了後の STATUS_PROCESSING 残骸を復旧せず、2件溜まると **MAX_CONCURRENT=2 に達してキューが永久停止**。→ 起動時に PROCESSING → PENDING リセット。
- **DB3** `RegistrationQueueManager.kt:152-167` — cancelQueue が temp削除→job cancel の順で temp_episodes が孤児化するレース。→ `cancelAndJoin()` 後に削除。
- **DB4** `DatabaseSyncManager.kt:381` / `ImprovedDatabaseSyncManager.kt:604` — インポート時に読書履歴の日時を現在時刻で上書き（「最近読んだ」順序が全件潰れる）。
- **DB5** `NovelDatabase.kt:305` — exportSchema=false かつ room.schemaLocation 未設定で **MigrationTest が実行不能**。テストも v11 まででv12-16未カバー。
- **DB6** `DatabaseSchemaAnalyzer.kt:84-85` — DBクローズ・一時ファイル削除が finally 外（ハンドルリーク）。

### UI・パフォーマンス
- **U1** `EpisodeListScreen.kt:149-157` — 目次表示が `SELECT *`（本文込み）の全話を Compose state に保持。長編で数十MB消費＋emit毎の全件ソート。→ body 除外のプロジェクションDTOクエリに。
- **U2** `UpdateInfoScreen.kt:875,912` — 一括DLループが1話ごとに全話（本文込み）を `.first()` で再ロード（N×M）。→ `getEpisode(ncode, no)` 単発取得に。
- **U3** `EpisodeViewScreen.kt:1004-1011` — AndroidView update で毎リコンポジション `loadDataWithBaseURL` 実行 → しおりトグル等でページ再読込・スクロール巻き戻り。
- **U4** `EpisodeViewScreen.kt:768-793` vs `utils/VjapTextConverter.kt` — ルビ処理のパリティ欠如: 壊れタグ修復＋autoRubyEnabled は横書きWebViewのみで、**縦書きでは設定が完全に無視される**。→ 縦書き変換前に fixRubyTags 相当を適用。
- **U5** `EpisodeViewScreen.kt:976-1001` — JS有効＋JSブリッジ＋allowFileAccess=true でスクレイピング由来の未サニタイズHTMLをロード（XSS→ブリッジ呼出し・file://参照が可能）。→ script除去 or allowFileAccess無効化＋WebViewAssetLoader。
- **U6** `WebViewScreen.kt:452-458` — update ブロックが状態変化のたび `loadUrl` 再実行（入力・スクロール喪失）。
- **U7** WebView 未破棄（両画面、`onCreateWindow` の tempWebView も）→ ネイティブリソースリーク。→ `AndroidView(onRelease = { it.destroy() })`。
- **U8** `NovelListScreen.kt:209-347` — 全小説をメモリ保持し検索1文字ごとにUIスレッドで filter＋sort（DBインデックスが活きない）。→ SQL側絞り込み or Dispatchers.Default。
- **U9** `EpisodeListScreen.kt:1601-1615` — 作者ページURLが site_type 分岐なしで syosetu mypage 固定（アダプター外ハードコード、カクヨムで壊れる）。
- **U10** `RecentlyReadNovelsScreen.kt:51` — フィルター設定の永続化なし（CLAUDE.md ルール違反）。
- **U11** `NovelDescDao.kt:49` — `getNovelsByNcodes` の IN句未チャンクで999件超に SQLite 変数上限例外リスク。

---

## 🟡 低（品質・保守性）

- LazyColumn の key 未指定: EpisodeListScreen:1880 / RecentlyRead:199 / RecentlyUpdated:81 / UpdateInfo:1237
- `EpisodeViewScreen.kt` — saveReadingRate が onDispose/ON_PAUSE で呼ばれない（ホーム遷移で進捗喪失）。fontSize 初期値18 vs デフォルト16のドリフト
- `fixRubyTags` が半角括弧のみ対象（仕様の全角「漢字（よみがな）」が変換されない）＋漢字限定でないため英文 `word(note)` を誤ルビ化
- end_flag=0（不明）が完結/未完結フィルターのどちらでも除外され不可視に
- `NovelDatabase.kt:280-281` — v16 の sub_site 初期化が R18 を一律ノクターン(2)扱い（ムーンライト/ミッドナイト誤分類）
- `ImprovedDatabaseSyncManager.kt:262` — エルビス演算子の優先順位バグで進捗スロットリングが無効
- 通知ID衝突（FCM 1000+ と AutoUpdateWorker 1001/1002）、1話毎 notify のレート制限超過
- `AutoUpdateWorker.kt:136` — 常に failure で retry 不使用（一時障害でその日の自動更新消失）。PeriodicWork のドリフトで指定時刻からズレ続ける
- `PER_EPISODE_TIMEOUT_MS` 未使用（1話ハングは全体10分まで待つ）、進捗更新の fire-and-forget で逆行
- `NovelRepository.kt:600-864` の `addNovelByUrl`/`addNovelByNcode` はデッドコード（かつバグ含み）→ 削除推奨
- `android.util.Log` 直接使用が多数（AppLogger ルール違反、リリースでもログ出力）: NovelApiUtils / UpdateService / AutoUpdateWorker / EpisodeListScreen / KakuyomuAdapter
- アダプターパターン違反: `NovelApiUtils.kt:507-558` のカクヨム分岐＋直接 new、`EpisodeListScreen.kt:650,966` のファクトリ不使用
- 完結判定が `doc.text()` 全体への「完結済」包含チェック（あらすじ等で誤検知）
- `shouldOverrideUrlLoading` が非httpスキーム（intent:// 等）を loadUrl（WebViewScreen.kt:222）
- MainActivity のデッド state（showSettings 等）と重複初期化
- CLAUDE.md のドキュメント乖離: 本文に「7 Entities / 7 DAOs / v11 / 2.0.4」が残存（正: 9/9/v16/2.0.6）。episodes のカラム名（e_no/e_body→実際は episode_no/body）、テーブル名 `last_read_novels`→実際は `last_read_novel`、疑似Ncode「KK-」→実装は「K」プレフィックス、KakuyomuAdapter コメントの「0.1秒間隔」→実装500ms

---

## ✅ 正しく実装されていると確認した主要項目

- **マイグレーションチェーン v1→v16**: 15本すべて定義・登録済み、destructive migration なし。v12/v16 のインデックスはエンティティ・マイグレーション両方に存在し名前一致
- **エンティティ9 / DAO9 / DB v16**: CLAUDE.md Quick Reference と実コード一致
- **Repositoryパターン / Flow・suspend 規約**: DAO直アクセスはDI組み立てのみ。メインスレッドDBアクセスなし
- **Syosetu YAML/gzip**: gzip二重判定・fixYamlFoldedScalarIndentation・レスポンス[1]参照すべて規定通り
- **R18判定**: 全ライブフローで rating==1 → novel18 系を一貫使用
- **短編タイトルフォールバック**: `--rensai` → 汎用セレクタの順で実装済み
- **KakuyomuAdapter 規定実装**: 本文/タイトル/章セレクタ優先順位、dots-indicator チェック、3回再試行＋指数バックオフ、cleanup 系、アダプター内全リクエストへの applyRateLimit 適用
- **逐次保存**: 主要DL経路（RegistrationQueueManager temp経由 / UpdateService / AutoUpdateWorker / UpdateInfoScreen）は1話ずつ保存（例外は上記 D2）
- **既読保持マージ**: insertEpisode 系は本文空なら旧本文保持＋既読/しおり/読書率保持
- **自動更新フロー**: 開始時 clearAllUpdateQueue、全小説対象、chunked(30)+async、カクヨムはメタデータのみ — 仕様準拠
- **マニフェスト**: receiver/service すべて exported=false、FGS dataSync 型宣言済み（Android 14対応）
- **バックハンドラー**: OnBackPressedCallback＋navigateBack（deprecated onBackPressed 不使用）
- **NovelListScreen**: フィルター永続化・enum安全復元・スクロール位置保存・LazyColumn key すべて実装済み
- **N+1対策**: UpdateInfoScreen / RecentlyRead の getNovelsByNcodes 一括取得は実装済み

---

## 推奨対応順序

1. **#1 + #2**（手動更新の回帰＋キュー逆転）— 組み合わせで「更新が取り込めない」状態。即修正
2. **#3**（カクヨム登録の ncode 不整合）— 新規登録の根幹が壊れている。1行修正で広範囲復旧
3. **#6 + #7 + #8**（DB同期系）— バックアップ/復元がデータ破壊装置になっている
4. **#9 + #10**（アップグレードクラッシュ・通知権限）— ユーザー影響大、修正容易
5. **#4 + #5 + #13**（カクヨム mapping 系）— データ汚染リスク
6. **#11 + #12 + #14**、その後 中項目（パフォーマンス系は U1/U2/D1 から）
