/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Enhanced manager for syncing external data.
 */
package com.shunlight_library.novel_reader.data.sync

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import com.shunlight_library.novel_reader.NovelReaderApplication
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.EpisodeMappingEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.repository.NovelRepository
import com.shunlight_library.novel_reader.SettingsStore
import com.shunlight_library.novel_reader.data.sync.DatabaseSyncUtils.getColumnIndexSafely
import com.shunlight_library.novel_reader.data.sync.DatabaseSyncUtils.getIntSafely
import com.shunlight_library.novel_reader.data.sync.DatabaseSyncUtils.getStringSafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/**
 * 内部RoomデータベースとSDカード上のSQLiteデータベースを同期するための改良版マネージャクラス
 */
class ImprovedDatabaseSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "ImprovedDbSyncManager"
    }

    private val repository: NovelRepository = NovelReaderApplication.getRepository()
    private val sqliteHelper = ExternalSQLiteHelper(context)
    private var syncCallback: SyncProgressCallback? = null

    // 進捗更新のスロットリングのための変数を追加
    private var lastProgressUpdateTime = 0L
    private val minUpdateIntervalMs = 500 // 最小更新間隔（ミリ秒）
    private val progressThreshold = 0.01f // 進捗更新の閾値（1%）

    // 前回の進捗状態を保持
    private var lastProgress: SyncProgress? = null

    /**
     * 同期プロセスの進行状況を表すデータクラス
     */
    data class SyncProgress(
        val step: SyncStep,
        val message: String,
        val progress: Float, // 0.0f から 1.0f の範囲
        val currentNcode: String = "", // 現在処理中のncode
        val currentTitle: String = "", // 現在処理中のタイトル
        val currentCount: Int = 0,    // 現在の処理済み数
        val totalCount: Int = 0       // 処理対象の総数
    )

    /**
     * 同期ステップを表す列挙型
     */
    enum class SyncStep {
        PREPARING,
        CHECKING_COMPATIBILITY,
        SYNCING_NOVEL_DESCS,
        SYNCING_EPISODES,
        SYNCING_LAST_READ,
        COMPLETED,
        ERROR
    }

    /**
     * 同期の結果を表すデータクラス
     */
    data class SyncResult(
        val success: Boolean,
        val novelDescsCount: Int,
        val episodesCount: Int,
        val lastReadCount: Int,
        val errorMessage: String? = null
    )

    /**
     * 同期の進行状況を通知するコールバック
     */
    interface SyncProgressCallback {
        fun onProgressUpdate(progress: SyncProgress)
        fun onComplete(result: SyncResult)
    }

    /**
     * 指定されたURIのSQLiteデータベースから内部Roomデータベースへデータを同期します
     * @param uri 外部データベースのURI
     * @param callback 進行状況を受け取るコールバック（省略可）
     * @return 同期の結果
     */
    suspend fun syncFromExternalDb(
        uri: Uri,
        callback: SyncProgressCallback? = null
    ): SyncResult {
        return withContext(Dispatchers.IO) {
            var db: SQLiteDatabase? = null
            var tempFile: File? = null
            syncCallback = callback
            try {
                // 進行状況の更新: 準備中
                updateProgress(callback, SyncProgress(
                    step = SyncStep.PREPARING,
                    message = "外部データベースをロード中...",
                    progress = 0.1f
                ))

                // 外部DBを開く
                val dbAndFile = sqliteHelper.openExternalDatabase(uri)
                if (dbAndFile == null) {
                    val errorMsg = "外部データベースを開けませんでした"
                    updateProgress(callback, SyncProgress(
                        step = SyncStep.ERROR,
                        message = errorMsg,
                        progress = 0f
                    ))
                    return@withContext SyncResult(
                        success = false,
                        novelDescsCount = 0,
                        episodesCount = 0,
                        lastReadCount = 0,
                        errorMessage = errorMsg
                    )
                }

                db = dbAndFile.first
                tempFile = dbAndFile.second

                // データベースの互換性チェック
                updateProgress(callback, SyncProgress(
                    step = SyncStep.CHECKING_COMPATIBILITY,
                    message = "データベースの互換性をチェック中...",
                    progress = 0.2f
                ))

                // 必要なテーブルの存在確認（reading history は命名揺れを許容）
                val requiredTables = listOf("novels_descs", "episodes")
                for (table in requiredTables) {
                    if (!sqliteHelper.isTableExists(db, table)) {
                        // 存在するテーブル一覧を取得してログ出力
                        val tableList = mutableListOf<String>()
                        try {
                            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
                                val nameIndex = cursor.getColumnIndexOrThrow("name")
                                while (cursor.moveToNext()) {
                                    tableList.add(cursor.getString(nameIndex))
                                }
                            }
                            Log.e(TAG, "DBに存在するテーブル一覧: ${tableList.joinToString(", ")}")
                        } catch (e: Exception) {
                            Log.e(TAG, "テーブル一覧取得中にエラーが発生", e)
                        }
                        val errorMsg = "必要なテーブルがありません: $table"
                        updateProgress(callback, SyncProgress(
                            step = SyncStep.ERROR,
                            message = errorMsg,
                            progress = 0f
                        ))
                        return@withContext SyncResult(
                            success = false,
                            novelDescsCount = 0,
                            episodesCount = 0,
                            lastReadCount = 0,
                            errorMessage = errorMsg
                        )
                    }
                }

                // データ同期を実行
                // 小説説明の同期
                updateProgress(callback, SyncProgress(
                    step = SyncStep.SYNCING_NOVEL_DESCS,
                    message = "小説情報を同期中...",
                    progress = 0.3f
                ))
                val novelDescsCount = syncNovelDescs(db) 
                
                // エピソードの同期（設定を反映）
                val settingsStore = SettingsStore(context)
                val dbSyncSettings = settingsStore.getDatabaseSyncSettings()
                updateProgress(callback, SyncProgress(
                    step = SyncStep.SYNCING_EPISODES,
                    message = "エピソードを同期中...",
                    progress = 0.6f
                ))
                val episodesCount = syncEpisodes(db, dbSyncSettings.preserveExistingEpisodes)

                // カクヨムのエピソードマッピングを同期（外部DBに存在する場合のみ）
                // これが無いとカクヨム作品は再DL・エラー修正時に本文を取得できない
                val mappingCount = syncEpisodeMappings(db)
                if (mappingCount > 0) {
                    Log.d(TAG, "カクヨムのエピソードマッピングを${mappingCount}件同期しました")
                }

                // 最後に読んだ記録の同期
                updateProgress(callback, SyncProgress(
                    step = SyncStep.SYNCING_LAST_READ,
                    message = "読書履歴を同期中...",
                    progress = 0.9f
                ))
                val lastReadCount = syncLastReadNovels(db)

                // M15: novels_descs と episodes は別フェーズのため、途中失敗で total_ep が乖離しうる。
                // エピソード投入後に実件数で total_ep を再計算して半壊状態を修復する。
                repository.recalculateAllTotalEpFromEpisodes()
                Log.d(TAG, "total_ep を episodes 実件数で再計算しました")

                // 完了通知
                val result = SyncResult(
                    success = true,
                    novelDescsCount = novelDescsCount,
                    episodesCount = episodesCount,
                    lastReadCount = lastReadCount
                )

                updateProgress(callback, SyncProgress(
                    step = SyncStep.COMPLETED,
                    message = "同期が完了しました: 小説${novelDescsCount}件、エピソード${episodesCount}件、履歴${lastReadCount}件",
                    progress = 1.0f
                ))

                callback?.onComplete(result)
                return@withContext result

            } catch (e: Exception) {
                val errorMsg = "データベース同期中にエラーが発生しました: ${e.message}"
                Log.e(TAG, errorMsg, e)

                updateProgress(callback, SyncProgress(
                    step = SyncStep.ERROR,
                    message = errorMsg,
                    progress = 0f
                ))

                val result = SyncResult(
                    success = false,
                    novelDescsCount = 0,
                    episodesCount = 0,
                    lastReadCount = 0,
                    errorMessage = errorMsg
                )

                callback?.onComplete(result)
                return@withContext result
            } finally {
                // リソースのクリーンアップ
                sqliteHelper.closeAndCleanup(db, tempFile)
            }
        }
    }

    /**
     * 進行状況をコールバックに通知します（スロットリング機能付き）
     */
    private fun updateProgress(callback: SyncProgressCallback?, progress: SyncProgress) {
        val currentTime = System.currentTimeMillis()

        // 以下の条件のいずれかが真の場合に更新を送信:
        // 1. 前回の更新から最小間隔以上経過している
        // 2. ステップが変わった
        // 3. 進捗が閾値以上変化した
        // 4. 最初または最後の更新
        val shouldUpdate =
            lastProgress == null || // 初回更新
                    currentTime - lastProgressUpdateTime >= minUpdateIntervalMs || // 時間間隔
                    lastProgress?.step != progress.step || // ステップ変更
                    abs((lastProgress?.progress ?: 0f) - progress.progress) >= progressThreshold || // 進捗変化
                    progress.progress >= 1.0f || progress.step == SyncStep.ERROR // 最終更新またはエラー

        if (shouldUpdate) {
            syncCallback?.onProgressUpdate(progress)
            lastProgressUpdateTime = currentTime
            lastProgress = progress

            // ログに小説情報も表示
            val novelInfo = if (progress.currentNcode.isNotEmpty() && progress.currentTitle.isNotEmpty())
                "[${progress.currentNcode}] ${progress.currentTitle}"
            else ""

            Log.d(TAG, "[${progress.step}] ${progress.message} $novelInfo (${progress.progress * 100}%)")
        }
    }

    /**
     * 小説説明データを同期します
     * @return 同期された小説数
     */
    private suspend fun syncNovelDescs(externalDb: SQLiteDatabase): Int {

        var cursor: Cursor? = null
        var count = 0
        var totalCount = 0

        try {
            // 総レコード数を取得
            totalCount = sqliteHelper.getTableCount(externalDb, "novels_descs")

            // 外部DBから全ての小説説明を取得
            cursor = externalDb.query(
                "novels_descs",
                null,
                null,
                null,
                null,
                null,
                null
            )

            // カラム名の取得とマッピング
            val columnNcode = if (cursor.getColumnIndex("ncode") >= 0) {
                cursor.getColumnIndexOrThrow("ncode")
            } else {
                cursor.getColumnIndexOrThrow("n_code")
            }
            val columnTitle = cursor.getColumnIndexOrThrow("title")
            val columnAuthor = cursor.getColumnIndexOrThrow("author")

            // SQLiteDBではカラム名が大文字小文字を区別しない可能性がある
            val columnSynopsis = getColumnIndexSafely(cursor, "Synopsis") ?:
            getColumnIndexSafely(cursor, "synopsis")

            val columnMainTag = getColumnIndexSafely(cursor, "main_tag")
            val columnSubTag = getColumnIndexSafely(cursor, "sub_tag")
            val columnRating = getColumnIndexSafely(cursor, "rating")
            val columnLastUpdateDate = getColumnIndexSafely(cursor, "last_update_date")
            val columnTotalEp = getColumnIndexSafely(cursor, "total_ep")
            val columnGeneralAllNo = getColumnIndexSafely(cursor, "general_all_no")
            val columnUserid = getColumnIndexSafely(cursor, "userid")
            val columnNoveltype = getColumnIndexSafely(cursor, "noveltype")
            val columnLength = getColumnIndexSafely(cursor, "length")
            val columnUpdatedAt = getColumnIndexSafely(cursor, "updated_at")
            val columnRegisteredAt = getColumnIndexSafely(cursor, "registered_at")
            // 外部DBにこれらカラムがあれば読む（v8+以降のエクスポート）
            val columnIsFavorite = getColumnIndexSafely(cursor, "is_favorite")
            val columnSiteType = getColumnIndexSafely(cursor, "site_type")
            val columnSubSite = getColumnIndexSafely(cursor, "sub_site")
            val columnEndFlag = getColumnIndexSafely(cursor, "end_flag")

            val batchSize = 50
            val novels = mutableListOf<NovelDescEntity>()

            // データの読み取りとバッチ処理
            while (cursor.moveToNext()) {
                val ncode = getStringSafely(cursor, columnNcode)
                val userid = getStringSafely(cursor, columnUserid)
                val noveltype = getIntSafely(cursor, columnNoveltype, -1)
                val length = getIntSafely(cursor, columnLength, -1)

                // 既存の内部レコードを取得し、外部DBに無いフィールドを保持する
                val existing = repository.getNovelByNcode(ncode)

                val isFavorite = when {
                    columnIsFavorite != null -> getIntSafely(cursor, columnIsFavorite)
                    else -> existing?.is_favorite ?: 0
                }
                val siteType = when {
                    columnSiteType != null -> getIntSafely(cursor, columnSiteType, 1)
                    else -> existing?.site_type ?: 1
                }
                val subSite = when {
                    columnSubSite != null -> getIntSafely(cursor, columnSubSite, 0)
                    else -> existing?.sub_site ?: 0
                }
                val endFlag = when {
                    columnEndFlag != null -> getIntSafely(cursor, columnEndFlag, 0)
                    else -> existing?.end_flag ?: 0
                }

                val novel = NovelDescEntity(
                    ncode = ncode,
                    title = getStringSafely(cursor, columnTitle),
                    author = getStringSafely(cursor, columnAuthor),
                    Synopsis = getStringSafely(cursor, columnSynopsis),
                    main_tag = getStringSafely(cursor, columnMainTag),
                    sub_tag = getStringSafely(cursor, columnSubTag),
                    rating = getIntSafely(cursor, columnRating),
                    last_update_date = getStringSafely(cursor, columnLastUpdateDate,
                        DatabaseSyncUtils.getCurrentDateTimeString()),
                    total_ep = getIntSafely(cursor, columnTotalEp),
                    general_all_no = getIntSafely(cursor, columnGeneralAllNo),
                    userid = if (userid.isEmpty()) null else userid,
                    noveltype = if (columnNoveltype != null && !cursor.isNull(columnNoveltype)) noveltype else null,
                    length = if (columnLength != null && !cursor.isNull(columnLength)) length else null,
                    updated_at = getStringSafely(cursor, columnUpdatedAt,
                        DatabaseSyncUtils.getCurrentDateTimeString()),
                    registered_at = getStringSafely(cursor, columnRegisteredAt,
                        DatabaseSyncUtils.getCurrentDateTimeString()),
                    is_favorite = isFavorite,
                    site_type = siteType,
                    sub_site = subSite,
                    end_flag = endFlag
                )

                novels.add(novel)
                count++
                val progressValue = if (totalCount > 0) count.toFloat() / totalCount else 0f
                val progressPercent = (progressValue * 100).toInt()

                updateProgress(syncCallback, SyncProgress(
                    step = SyncStep.SYNCING_NOVEL_DESCS,
                    message = "小説情報を同期中 ($count/$totalCount - $progressPercent%)",
                    progress = 0.3f + (0.3f * progressValue),
                    currentNcode = getStringSafely(cursor, columnNcode),
                    currentTitle = getStringSafely(cursor, columnTitle),
                    currentCount = count,
                    totalCount = totalCount
                ))
                // バッチサイズに達したら保存
                if (novels.size >= batchSize) {
                    repository.insertNovels(novels)
                    novels.clear()
                }
            }

            // 残りのデータを保存
            if (novels.isNotEmpty()) {
                repository.insertNovels(novels)
            }

            Log.d(TAG, "小説説明の同期が完了しました: $count 件")
            return count
        } catch (e: Exception) {
            Log.e(TAG, "小説説明の同期中にエラーが発生しました", e)
            throw e
        } finally {
            cursor?.close()
        }
    }

    /**
     * エピソードデータを同期します
     * @param db 外部データベース
     * @param preserveExisting 既存エピソードを保持するか（デフォルト: true）
     * @return 同期されたエピソード数
     */
    private suspend fun syncEpisodes(externalDb: SQLiteDatabase, preserveExisting: Boolean = true): Int {
        var totalCount = 0
        var processedCount = 0
        val failedNcodes = mutableListOf<Pair<String, String>>() // ncode, errorMessage

        try {
            // 総エピソード数を把握
            externalDb.rawQuery("SELECT COUNT(*) FROM episodes", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    totalCount = cursor.getInt(0)
                }
            }

            // 進捗初期化
            updateProgress(syncCallback, SyncProgress(
                step = SyncStep.SYNCING_EPISODES,
                message = "エピソードの同期を開始 (0/$totalCount)",
                progress = 0.6f,
                currentCount = 0,
                totalCount = totalCount
            ))

            // novels_descsから順にncodeを取得
            val ncodeColumnName = if (isColumnExists(externalDb, "novels_descs", "ncode")) {
                "ncode"
            } else {
                "n_code"
            }

            externalDb.query(
                "novels_descs",
                arrayOf(ncodeColumnName, "title"),
                null, null, null, null, null
            ).use { ncodeCursor ->
                val ncodeIndex = ncodeCursor.getColumnIndexOrThrow(ncodeColumnName)
                val titleIndex = ncodeCursor.getColumnIndexOrThrow("title")

                var novelCount = 0
                val totalNovels = ncodeCursor.count

                // 各ncodeについて処理
                while (ncodeCursor.moveToNext()) {
                    val ncode = ncodeCursor.getString(ncodeIndex)
                    val title = ncodeCursor.getString(titleIndex)
                    novelCount++

                    try {
                        Log.d(TAG, "小説[$novelCount/$totalNovels] \"$title\"($ncode)のエピソードを同期中")

                        // このncodeのエピソードを取得
                        externalDb.query(
                            "episodes",
                            null,
                            "ncode = ?",
                            arrayOf(ncode),
                            null, null, null
                        ).use { episodeCursor ->
                            // カラムインデックスを一度だけ取得
                            val epNcodeIndex = if (episodeCursor.getColumnIndex("ncode") >= 0) {
                                episodeCursor.getColumnIndexOrThrow("ncode")
                            } else {
                                episodeCursor.getColumnIndexOrThrow("n_code")
                            }
                            val episodeNoIndex = episodeCursor.getColumnIndexOrThrow("episode_no")
                            val bodyIndex = episodeCursor.getColumnIndexOrThrow("body")
                            val eTitleIndex = getColumnIndexSafely(episodeCursor, "e_title")
                            val updateTimeIndex = getColumnIndexSafely(episodeCursor, "update_time")
                            // 既読・しおり・読書率も外部DBから読み取る（バックアップ復元で読書状態を保全）
                            val isReadIndex = getColumnIndexSafely(episodeCursor, "is_read")
                            val isBookmarkIndex = getColumnIndexSafely(episodeCursor, "is_bookmark")
                            val readingRateIndex = getColumnIndexSafely(episodeCursor, "reading_rate")

                            val batchSize = 20
                            val episodes = mutableListOf<EpisodeEntity>()
                            var novelEpisodeCount = 0

                            // エピソードを処理
                            while (episodeCursor.moveToNext()) {
                                val readingRate = if (readingRateIndex != null && !episodeCursor.isNull(readingRateIndex)) {
                                    episodeCursor.getFloat(readingRateIndex)
                                } else 0f
                                val episode = EpisodeEntity(
                                    ncode = getStringSafely(episodeCursor, epNcodeIndex),
                                    episode_no = getStringSafely(episodeCursor, episodeNoIndex),
                                    body = getStringSafely(episodeCursor, bodyIndex),
                                    e_title = getStringSafely(episodeCursor, eTitleIndex),
                                    update_time = getStringSafely(episodeCursor, updateTimeIndex,
                                        DatabaseSyncUtils.getCurrentDateTimeString()),
                                    is_read = getIntSafely(episodeCursor, isReadIndex),
                                    is_bookmark = getIntSafely(episodeCursor, isBookmarkIndex),
                                    reading_rate = readingRate
                                )

                                episodes.add(episode)
                                processedCount++
                                novelEpisodeCount++

                                // バッチサイズに達したら保存し、メモリ解放
                                if (episodes.size >= batchSize) {
                                    repository.insertEpisodes(episodes, preserveExisting)
                                    episodes.clear()
                                    
                                    // 進捗更新（バッチごと）
                                    updateProgress(syncCallback, SyncProgress(
                                        step = SyncStep.SYNCING_EPISODES,
                                        message = "小説[$novelCount/$totalNovels] \"$title\"のエピソードを同期中 ($processedCount/$totalCount)",
                                        progress = 0.6f + (0.3f * processedCount.toFloat() / totalCount),
                                        currentNcode = ncode,
                                        currentTitle = title,
                                        currentCount = processedCount,
                                        totalCount = totalCount
                                    ))
                                }
                            }
                            
                            // 残りのエピソードを保存
                            if (episodes.isNotEmpty()) {
                                repository.insertEpisodes(episodes, preserveExisting)
                                episodes.clear()
                            }

                            Log.d(TAG, "小説「$title」($ncode)の${novelEpisodeCount}件のエピソードを同期しました")
                        }

                        // この小説の同期完了を報告
                        updateProgress(syncCallback, SyncProgress(
                            step = SyncStep.SYNCING_EPISODES,
                            message = "小説[$novelCount/$totalNovels] \"$title\"の同期完了 ($processedCount/$totalCount)",
                            progress = 0.6f + (0.3f * processedCount.toFloat() / totalCount),
                            currentNcode = ncode,
                            currentTitle = title,
                            currentCount = processedCount,
                            totalCount = totalCount
                        ))
                    } catch (e: Exception) {
                        // この小説の処理中のエラーを記録し、次の小説に進む
                        Log.e(TAG, "小説「$title」($ncode)のエピソード同期中にエラーが発生しました", e)
                        failedNcodes.add(Pair(ncode, e.message ?: "不明なエラー"))

                        // エラー進捗を報告
                        updateProgress(syncCallback, SyncProgress(
                            step = SyncStep.SYNCING_EPISODES,
                            message = "小説「$title」($ncode)のエピソード同期でエラー: ${e.message}",
                            progress = 0.6f + (0.3f * processedCount.toFloat() / totalCount),
                            currentNcode = ncode,
                            currentTitle = title,
                            currentCount = processedCount,
                            totalCount = totalCount
                        ))
                    }
                }
            }

            // 失敗した小説があれば記録
            if (failedNcodes.isNotEmpty()) {
                val failedMsg = failedNcodes.joinToString("\n") { "${it.first}: ${it.second}" }
                Log.w(TAG, "一部の小説のエピソード同期に失敗しました:\n$failedMsg")
            }

            Log.d(TAG, "全小説のエピソード同期が完了しました: 成功${processedCount}件, 失敗${failedNcodes.size}件")
            return processedCount
        } catch (e: Exception) {
            Log.e(TAG, "エピソード同期の全体処理中にエラーが発生しました", e)
            throw e
        }
    }
    /**
     * カクヨム作品のエピソードマッピング（話番号→エピソードID）を同期します。
     * 外部DBに `episode_mapping` テーブルが無い場合（旧形式SQLite等）は何もしません。
     * @return 同期されたマッピング数
     */
    private suspend fun syncEpisodeMappings(externalDb: SQLiteDatabase): Int {
        if (!sqliteHelper.isTableExists(externalDb, "episode_mapping")) return 0

        var count = 0
        val batchSize = 100
        val batch = mutableListOf<EpisodeMappingEntity>()
        try {
            externalDb.query("episode_mapping", null, null, null, null, null, null).use { cursor ->
                val ncodeIndex = getColumnIndexSafely(cursor, "ncode") ?: return 0
                val episodeNoIndex = getColumnIndexSafely(cursor, "episode_no") ?: return 0
                val kakuyomuIdIndex = getColumnIndexSafely(cursor, "kakuyomu_episode_id") ?: return 0

                while (cursor.moveToNext()) {
                    val ncode = getStringSafely(cursor, ncodeIndex)
                    val kakuyomuId = getStringSafely(cursor, kakuyomuIdIndex)
                    if (ncode.isEmpty() || kakuyomuId.isEmpty()) continue

                    batch.add(
                        EpisodeMappingEntity(
                            ncode = ncode,
                            episode_no = getIntSafely(cursor, episodeNoIndex),
                            kakuyomu_episode_id = kakuyomuId
                        )
                    )
                    count++
                    if (batch.size >= batchSize) {
                        repository.insertEpisodeMappings(batch.toList())
                        batch.clear()
                    }
                }
            }
            if (batch.isNotEmpty()) {
                repository.insertEpisodeMappings(batch.toList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "エピソードマッピングの同期中にエラーが発生しました", e)
        }
        return count
    }

    /**
     * 最後に読んだ小説のデータを同期します
     * @return 同期された記録数
     */
    private suspend fun syncLastReadNovels(externalDb: SQLiteDatabase): Int {
        var cursor: Cursor? = null
        var count = 0
        var totalCount = 0

        // テーブル名の揺れに対応（旧アプリは "rast_read_novel" の誤記名で作られたDBが存在する）
        val lastReadTable = when {
            sqliteHelper.isTableExists(externalDb, "last_read_novel") -> "last_read_novel"
            sqliteHelper.isTableExists(externalDb, "rast_read_novel") -> "rast_read_novel"
            else -> return 0
        }

        try {
            // 総レコード数を取得
            totalCount = sqliteHelper.getTableCount(externalDb, lastReadTable)

            // 進捗初期化の報告
            updateProgress(syncCallback, SyncProgress(
                step = SyncStep.SYNCING_LAST_READ,
                message = "読書履歴データの同期を開始 (0/$totalCount - 0%)",
                progress = 0.9f,
                currentCount = 0,
                totalCount = totalCount
            ))

            // 外部DBから最終読書記録を取得
            cursor = externalDb.query(
                lastReadTable,
                null,
                null,
                null,
                null,
                null,
                null
            )

            // カラム名の取得とマッピング
            val columnNcode = if (cursor.getColumnIndex("ncode") >= 0) {
                cursor.getColumnIndexOrThrow("ncode")
            } else {
                cursor.getColumnIndexOrThrow("n_code")
            }
            val columnDate = cursor.getColumnIndexOrThrow("date")
            val columnEpisodeNo = cursor.getColumnIndexOrThrow("episode_no")

            // データの読み取りと処理
            while (cursor.moveToNext()) {
                val ncode = getStringSafely(cursor, columnNcode)
                val episodeNo = getIntSafely(cursor, columnEpisodeNo)

                // 既存のデータがあれば更新、なければ新規挿入
                repository.updateLastRead(ncode, episodeNo)
                count++
                val progressValue = if (totalCount > 0) count.toFloat() / totalCount else 0f
                val progressPercent = (progressValue * 100).toInt()

                updateProgress(syncCallback, SyncProgress(
                    step = SyncStep.SYNCING_LAST_READ,
                    message = "読書履歴を同期中 ($count/$totalCount - $progressPercent%)",
                    progress = 0.9f + (0.1f * progressValue),
                    currentNcode = ncode,
                    currentTitle = "最終話: ${episodeNo}話",
                    currentCount = count,
                    totalCount = totalCount
                ))
            }

            Log.d(TAG, "最終読書記録の同期が完了しました: $count 件")
            return count
        } catch (e: Exception) {
            Log.e(TAG, "最終読書記録の同期中にエラーが発生しました", e)
            throw e
        } finally {
            cursor?.close()
        }
    }
    private fun isColumnExists(db: SQLiteDatabase, tableName: String, columnName: String): Boolean {
        return db.rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == columnName) {
                    return@use true
                }
            }
            false
        }
    }
}