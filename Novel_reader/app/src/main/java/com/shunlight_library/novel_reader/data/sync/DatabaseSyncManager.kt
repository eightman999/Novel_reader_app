/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Manager to import external database data.
 */
package com.shunlight_library.novel_reader.data.sync

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.shunlight_library.novel_reader.NovelReaderApplication
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.EpisodeMappingEntity
import com.shunlight_library.novel_reader.data.entity.LastReadNovelEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.entity.URLEntity
import com.shunlight_library.novel_reader.data.repository.NovelRepository
import com.shunlight_library.novel_reader.SettingsStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * 内部RoomデータベースとSDカード上のSQLiteデータベースを同期するためのマネージャクラス
 */
class DatabaseSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "DatabaseSyncManager"
        private const val EXTERNAL_DB_NAME = "novels.db"
    }

    private val repository: NovelRepository = NovelReaderApplication.getRepository()

    /**
     * 指定されたURIのSQLiteデータベースから内部Roomデータベースへデータを同期します
     * @param uri 外部データベースのURI
     * @return 同期が成功したかどうか
     */
    suspend fun syncFromExternalDb(uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "外部DBから同期を開始: $uri")

                // 一時ファイルを作成
                val tempDbFile = createTempDbFile(uri)
                if (tempDbFile == null) {
                    Log.e(TAG, "一時データベースファイルの作成に失敗しました")
                    return@withContext false
                }

                // 外部DBを開く
                val externalDb = openExternalDatabase(tempDbFile.absolutePath)
                if (externalDb == null) {
                    Log.e(TAG, "外部データベースを開けませんでした")
                    tempDbFile.delete()
                    return@withContext false
                }

                // データの同期を実行
                val syncSuccess = syncData(externalDb)

                // リソースの解放
                externalDb.close()
                tempDbFile.delete()

                Log.d(TAG, "外部DBからの同期完了: $syncSuccess")
                return@withContext syncSuccess
            } catch (e: Exception) {
                Log.e(TAG, "外部DBからの同期中にエラーが発生しました", e)
                return@withContext false
            }
        }
    }

    /**
     * URIから一時的なデータベースファイルを作成します
     */
    private suspend fun createTempDbFile(uri: Uri): File? {
        return withContext(Dispatchers.IO) {
            try {
                val tempFile = File(context.cacheDir, "temp_$EXTERNAL_DB_NAME")
                if (tempFile.exists()) {
                    tempFile.delete()
                }

                // URIからファイルを読み込む
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: run {
                    Log.e(TAG, "URIからの入力ストリームを開けませんでした: $uri")
                    return@withContext null
                }

                return@withContext tempFile
            } catch (e: Exception) {
                Log.e(TAG, "一時DBファイルの作成中にエラーが発生しました", e)
                return@withContext null
            }
        }
    }

    /**
     * 外部データベースファイルを開きます
     */
    private fun openExternalDatabase(dbPath: String): SQLiteDatabase? {
        return try {
            // 読み取り専用モードでデータベースを開く
            SQLiteDatabase.openDatabase(
                dbPath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
        } catch (e: Exception) {
            Log.e(TAG, "データベースを開く際にエラーが発生しました: $dbPath", e)
            null
        }
    }

    /**
     * 外部データベースから内部データベースにデータを同期します
     */
    private suspend fun syncData(externalDb: SQLiteDatabase): Boolean {
        try {
            // データベース同期設定を取得
            val settingsStore = SettingsStore(context)
            val dbSyncSettings = settingsStore.getDatabaseSyncSettings()
            
            // テーブルの存在チェック
            val tables = listOf("novels_descs", "episodes", "last_read_novel")
            for (table in tables) {
                if (!isTableExists(externalDb, table)) {
                    Log.e(TAG, "外部DBにテーブルが存在しません: $table")
                    return false
                }
            } 
            
            // 小説説明の同期
            try { syncNovelDescs(externalDb) }
            catch (e: Exception) { Log.e(TAG, "[Step1/3] 小説説明の同期に失敗", e); throw e }

            // エピソードの同期（設定を反映）
            try { syncEpisodes(externalDb, dbSyncSettings.preserveExistingEpisodes) }
            catch (e: Exception) { Log.e(TAG, "[Step2/3] エピソードの同期に失敗", e); throw e }

            // カクヨムのエピソードマッピングを同期（外部DBに存在する場合のみ）
            try { syncEpisodeMappings(externalDb) }
            catch (e: Exception) { Log.e(TAG, "エピソードマッピングの同期に失敗", e) }

            // 最後に読んだ記録の同期
            try { syncLastReadNovels(externalDb) }
            catch (e: Exception) { Log.e(TAG, "[Step3/3] 読書履歴の同期に失敗", e); throw e }

            // M15: episodes 実件数で total_ep を再計算
            try { repository.recalculateAllTotalEpFromEpisodes() }
            catch (e: Exception) { Log.e(TAG, "total_ep 再計算に失敗", e) }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "データ同期中にエラーが発生しました", e)
            return false
        }
    }

    /**
     * テーブルが存在するかチェックします
     */
    private fun isTableExists(db: SQLiteDatabase, tableName: String): Boolean {
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        )
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    /**
     * 小説説明データを同期します
     */
    private suspend fun syncNovelDescs(externalDb: SQLiteDatabase) {
        var cursor: Cursor? = null
        try {
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

            // カラム名の取得とマッピング（旧形式の n_code にもフォールバック）
            val columnNcode = getColumnIndexSafely(cursor, "ncode")
                ?: cursor.getColumnIndexOrThrow("n_code")
            val columnTitle = cursor.getColumnIndexOrThrow("title")
            val columnAuthor = cursor.getColumnIndexOrThrow("author")

            // SQLiteDBではカラム名が大文字小文字を区別しない可能性がある
            val columnSynopsis = getColumnIndexSafely(cursor, "Synopsis") ?:
            getColumnIndexSafely(cursor, "synopsis")

            val columnMainTag = getColumnIndexOrDefault(cursor, "main_tag")
            val columnSubTag = getColumnIndexOrDefault(cursor, "sub_tag")
            val columnRating = getColumnIndexOrDefault(cursor, "rating")
            val columnLastUpdateDate = getColumnIndexOrDefault(cursor, "last_update_date")
            val columnTotalEp = getColumnIndexOrDefault(cursor, "total_ep")
            val columnGeneralAllNo = getColumnIndexOrDefault(cursor, "general_all_no")
            val columnUserid = getColumnIndexOrDefault(cursor, "userid")
            val columnNoveltype = getColumnIndexOrDefault(cursor, "noveltype")
            val columnLength = getColumnIndexOrDefault(cursor, "length")
            val columnUpdatedAt = getColumnIndexOrDefault(cursor, "updated_at")
            val columnRegisteredAt = getColumnIndexOrDefault(cursor, "registered_at")
            // 外部DBにあれば読む（無ければ既存内部値を保持し、カクヨム作品をなろう扱いで破壊しない）
            val columnIsFavorite = getColumnIndexOrDefault(cursor, "is_favorite")
            val columnSiteType = getColumnIndexOrDefault(cursor, "site_type")
            val columnSubSite = getColumnIndexOrDefault(cursor, "sub_site")
            val columnEndFlag = getColumnIndexOrDefault(cursor, "end_flag")
            val urlEntities = mutableListOf<URLEntity>() // URLEntityリスト追加
            val batchSize = 50
            val novels = mutableListOf<NovelDescEntity>()

            // データの読み取りとバッチ処理
            while (cursor.moveToNext()) {
                val ncode = cursor.getString(columnNcode)
                // 外部DBに列が無い場合は既存の内部値を保持する
                val existing = repository.getNovelByNcode(ncode)
                val siteType = columnSiteType?.let { cursor.getInt(it) } ?: existing?.site_type ?: 1
                val subSite = columnSubSite?.let { cursor.getInt(it) } ?: existing?.sub_site ?: 0
                val endFlag = columnEndFlag?.let { cursor.getInt(it) } ?: existing?.end_flag ?: 0
                val isFavorite = columnIsFavorite?.let { cursor.getInt(it) } ?: existing?.is_favorite ?: 0

                val novel = NovelDescEntity(
                    ncode = ncode,
                    title = cursor.getString(columnTitle),
                    author = cursor.getString(columnAuthor),
                    Synopsis = columnSynopsis?.let { cursor.getString(it) } ?: "",
                    main_tag = columnMainTag?.let { cursor.getString(it) } ?: "",
                    sub_tag = columnSubTag?.let { cursor.getString(it) } ?: "",
                    rating = columnRating?.let { cursor.getInt(it) } ?: 0,
                    last_update_date = columnLastUpdateDate?.let { cursor.getString(it) } ?: getCurrentDateString(),
                    total_ep = columnTotalEp?.let { cursor.getInt(it) } ?: 0,
                    general_all_no = columnGeneralAllNo?.let { cursor.getInt(it) } ?: 0,
                    userid = columnUserid?.let { cursor.getString(it) }?.takeIf { it.isNotEmpty() },
                    noveltype = columnNoveltype?.let { cursor.getInt(it) },
                    length = columnLength?.let { cursor.getInt(it) },
                    updated_at = columnUpdatedAt?.let { cursor.getString(it) } ?: getCurrentDateString(),
                    registered_at = columnRegisteredAt?.let { cursor.getString(it) } ?: getCurrentDateString(),
                    is_favorite = isFavorite,
                    site_type = siteType,
                    sub_site = subSite,
                    end_flag = endFlag
                )

                novels.add(novel)
                // URLEntityはなろう作品のみ生成（カクヨム作品になろうAPI/WebURLを付与しない）
                if (siteType != com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter.SITE_TYPE_KAKUYOMU) {
                    val isR18 = novel.rating == 1
                    val apiUrl = if (isR18) {
                        "https://api.syosetu.com/novel18api/api/?of=t-w-ga-s-ua&ncode=${novel.ncode}&gzip=5"
                    } else {
                        "https://api.syosetu.com/novelapi/api/?of=t-w-ga-s-ua&ncode=${novel.ncode}&gzip=5"
                    }
                    val webUrl = if (isR18) {
                        "https://novel18.syosetu.com/${novel.ncode}/"
                    } else {
                        "https://ncode.syosetu.com/${novel.ncode}/"
                    }

                    urlEntities.add(
                        URLEntity(
                            ncode = novel.ncode,
                            api_url = apiUrl,
                            url = webUrl,
                            is_r18 = if (isR18) 1 else 0
                        )
                    )
                }
                // バッチサイズに達したら保存
                if (novels.size >= batchSize) {
                    repository.insertNovels(novels)
                    repository.insertURLs(urlEntities)
                    novels.clear()
                    urlEntities.clear()
                }
            }

            // 残りのデータを保存
            if (novels.isNotEmpty()) {
                repository.insertNovels(novels)
            }
            if (urlEntities.isNotEmpty()) {
                repository.insertURLs(urlEntities)
            }

            Log.d(TAG, "小説説明の同期が完了しました")
        } catch (e: Exception) {
            Log.e(TAG, "小説説明の同期中にエラーが発生しました", e)
            throw e
        } finally {
            cursor?.close()
        }
    }

    /**
     * エピソードデータを同期します
     */
    private suspend fun syncEpisodes(externalDb: SQLiteDatabase, preserveExisting: Boolean = true) {
        var cursor: Cursor? = null
        try {
            // 外部DBから全てのエピソードを取得
            cursor = externalDb.query(
                "episodes",
                null,
                null,
                null,
                null,
                null,
                null
            ) 
            
            // カラム名の取得とマッピング
            val columnNcode = getColumnIndexSafely(cursor, "ncode")
                ?: cursor.getColumnIndexOrThrow("n_code")
            val columnEpisodeNo = cursor.getColumnIndexOrThrow("episode_no")
            val columnBody = cursor.getColumnIndexOrThrow("body")
            val columnETitle = getColumnIndexOrDefault(cursor, "e_title")
            val columnUpdateTime = getColumnIndexOrDefault(cursor, "update_time")
            // 既読・しおり・読書率も読み取り、バックアップ復元で読書状態を保全する
            val columnIsRead = getColumnIndexOrDefault(cursor, "is_read")
            val columnIsBookmark = getColumnIndexOrDefault(cursor, "is_bookmark")
            val columnReadingRate = getColumnIndexOrDefault(cursor, "reading_rate")

            val batchSize = 20
            val episodes = mutableListOf<EpisodeEntity>()

            // データの読み取りとバッチ処理
            while (cursor.moveToNext()) {
                val readingRate = if (columnReadingRate != null && !cursor.isNull(columnReadingRate)) {
                    cursor.getFloat(columnReadingRate)
                } else 0f
                val episode = EpisodeEntity(
                    ncode = cursor.getString(columnNcode),
                    episode_no = cursor.getString(columnEpisodeNo),
                    body = cursor.getString(columnBody),
                    e_title = columnETitle?.let { cursor.getString(it) } ?: "",
                    update_time = columnUpdateTime?.let { cursor.getString(it) } ?:
                    getCurrentDateString(),
                    is_read = columnIsRead?.let { cursor.getInt(it) } ?: 0,
                    is_bookmark = columnIsBookmark?.let { cursor.getInt(it) } ?: 0,
                    reading_rate = readingRate
                )

                episodes.add(episode)
                
                // バッチサイズに達したら保存（設定を反映）
                if (episodes.size >= batchSize) {
                    repository.insertEpisodes(episodes, preserveExisting)
                    episodes.clear()
                }
            } 
            
            // 残りのデータを保存（設定を反映）
            if (episodes.isNotEmpty()) {
                repository.insertEpisodes(episodes, preserveExisting)
            } 
            
            Log.d(TAG, "エピソードの同期が完了しました (既存保持: $preserveExisting)")
        } catch (e: Exception) {
            Log.e(TAG, "エピソードの同期中にエラーが発生しました", e)
            throw e
        } finally {
            cursor?.close()
        }
    }

    /**
     * カクヨム作品のエピソードマッピング（話番号→エピソードID）を同期します。
     * 外部DBに `episode_mapping` テーブルが無い場合は何もしません。
     */
    private suspend fun syncEpisodeMappings(externalDb: SQLiteDatabase) {
        if (!isTableExists(externalDb, "episode_mapping")) return
        var cursor: Cursor? = null
        try {
            cursor = externalDb.query("episode_mapping", null, null, null, null, null, null)
            val columnNcode = getColumnIndexSafely(cursor, "ncode") ?: return
            val columnEpisodeNo = getColumnIndexSafely(cursor, "episode_no") ?: return
            val columnKakuyomuId = getColumnIndexSafely(cursor, "kakuyomu_episode_id") ?: return

            val batchSize = 100
            val mappings = mutableListOf<EpisodeMappingEntity>()
            while (cursor.moveToNext()) {
                val ncode = cursor.getString(columnNcode) ?: continue
                val kakuyomuId = cursor.getString(columnKakuyomuId) ?: continue
                if (ncode.isEmpty() || kakuyomuId.isEmpty()) continue
                mappings.add(
                    EpisodeMappingEntity(
                        ncode = ncode,
                        episode_no = cursor.getInt(columnEpisodeNo),
                        kakuyomu_episode_id = kakuyomuId
                    )
                )
                if (mappings.size >= batchSize) {
                    repository.insertEpisodeMappings(mappings.toList())
                    mappings.clear()
                }
            }
            if (mappings.isNotEmpty()) {
                repository.insertEpisodeMappings(mappings.toList())
            }
            Log.d(TAG, "エピソードマッピングの同期が完了しました")
        } catch (e: Exception) {
            Log.e(TAG, "エピソードマッピングの同期中にエラーが発生しました", e)
        } finally {
            cursor?.close()
        }
    }

    /**
     * 最後に読んだ小説のデータを同期します
     */
    private suspend fun syncLastReadNovels(externalDb: SQLiteDatabase) {
        var cursor: Cursor? = null
        try {
            // テーブル名が内部DBでは "last_read_novel" だが、外部DBでは違う可能性がある
            val tableName = "last_read_novel"

            // 外部DBから全ての最終読書記録を取得
            cursor = externalDb.query(
                tableName,
                null,
                null,
                null,
                null,
                null,
                null
            )

            // カラム名の取得とマッピング
            val columnNcode = cursor.getColumnIndexOrThrow("ncode")
            val columnDate = cursor.getColumnIndexOrThrow("date")
            val columnEpisodeNo = cursor.getColumnIndexOrThrow("episode_no")

            // データの読み取りと処理
            while (cursor.moveToNext()) {
                val lastRead = LastReadNovelEntity(
                    ncode = cursor.getString(columnNcode),
                    date = cursor.getString(columnDate),
                    episode_no = cursor.getInt(columnEpisodeNo)
                )

                // 既存のデータがあれば更新、なければ新規挿入
                repository.updateLastRead(lastRead.ncode, lastRead.episode_no)
            }

            Log.d(TAG, "最終読書記録の同期が完了しました")
        } catch (e: Exception) {
            Log.e(TAG, "最終読書記録の同期中にエラーが発生しました", e)
            throw e
        } finally {
            cursor?.close()
        }
    }

    /**
     * 安全にカラムインデックスを取得します（存在しない場合はnull）
     */
    private fun getColumnIndexSafely(cursor: Cursor, columnName: String): Int? {
        return try {
            val index = cursor.getColumnIndex(columnName)
            if (index >= 0) index else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * カラムインデックスを取得します（存在しない場合はnull）
     */
    private fun getColumnIndexOrDefault(cursor: Cursor, columnName: String): Int? {
        return getColumnIndexSafely(cursor, columnName)
    }

    /**
     * 現在の日時文字列を取得します
     */
    private fun getCurrentDateString(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date())
    }
}