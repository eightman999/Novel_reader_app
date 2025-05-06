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
import com.shunlight_library.novel_reader.data.entity.LastReadNovelEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.repository.NovelRepository
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
            // テーブルの存在チェック
            val tables = listOf("novels_descs", "episodes", "last_read_novel")
            for (table in tables) {
                if (!isTableExists(externalDb, table)) {
                    Log.e(TAG, "外部DBにテーブルが存在しません: $table")
                    return false
                }
            }

            // 小説説明の同期
            syncNovelDescs(externalDb)

            // エピソードの同期
            syncEpisodes(externalDb)

            // 最後に読んだ記録の同期
            syncLastReadNovels(externalDb)

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

            // カラム名の取得とマッピング
            val columnNcode = cursor.getColumnIndexOrThrow("ncode")
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
            val columnUpdatedAt = getColumnIndexOrDefault(cursor, "updated_at")

            val batchSize = 50
            val novels = mutableListOf<NovelDescEntity>()

            // データの読み取りとバッチ処理
            while (cursor.moveToNext()) {
                val novel = NovelDescEntity(
                    ncode = cursor.getString(columnNcode),
                    title = cursor.getString(columnTitle),
                    author = cursor.getString(columnAuthor),
                    Synopsis = columnSynopsis?.let { cursor.getString(it) } ?: "",
                    main_tag = columnMainTag?.let { cursor.getString(it) } ?: "",
                    sub_tag = columnSubTag?.let { cursor.getString(it) } ?: "",
                    rating = columnRating?.let { cursor.getInt(it) } ?: 0,
                    last_update_date = columnLastUpdateDate?.let { cursor.getString(it) } ?:
                    getCurrentDateString(),
                    total_ep = columnTotalEp?.let { cursor.getInt(it) } ?: 0,
                    general_all_no = columnGeneralAllNo?.let { cursor.getInt(it) } ?: 0,
                    updated_at = columnUpdatedAt?.let { cursor.getString(it) } ?:
                    getCurrentDateString()
                )

                novels.add(novel)

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
    private suspend fun syncEpisodes(externalDb: SQLiteDatabase) {
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
            val columnNcode = cursor.getColumnIndexOrThrow("ncode")
            val columnEpisodeNo = cursor.getColumnIndexOrThrow("episode_no")
            val columnBody = cursor.getColumnIndexOrThrow("body")
            val columnETitle = getColumnIndexOrDefault(cursor, "e_title")
            val columnUpdateTime = getColumnIndexOrDefault(cursor, "update_time")

            val batchSize = 20
            val episodes = mutableListOf<EpisodeEntity>()

            // データの読み取りとバッチ処理
            while (cursor.moveToNext()) {
                val episode = EpisodeEntity(
                    ncode = cursor.getString(columnNcode),
                    episode_no = cursor.getString(columnEpisodeNo),
                    body = cursor.getString(columnBody),
                    e_title = columnETitle?.let { cursor.getString(it) } ?: "",
                    update_time = columnUpdateTime?.let { cursor.getString(it) } ?:
                    getCurrentDateString()
                )

                episodes.add(episode)

                // バッチサイズに達したら保存
                if (episodes.size >= batchSize) {
                    repository.insertEpisodes(episodes)
                    episodes.clear()
                }
            }

            // 残りのデータを保存
            if (episodes.isNotEmpty()) {
                repository.insertEpisodes(episodes)
            }

            Log.d(TAG, "エピソードの同期が完了しました")
        } catch (e: Exception) {
            Log.e(TAG, "エピソードの同期中にエラーが発生しました", e)
            throw e
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