/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Helper class for exporting the Room database to external storage.
 */
package com.shunlight_library.novel_reader.data.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import com.shunlight_library.novel_reader.data.database.NovelDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

/**
 * 内部のRoomデータベースを外部へエクスポートするためのマネージャクラス。
 */
class DatabaseExportManager(private val context: Context) {

    companion object {
        private const val TAG = "DatabaseExportManager"
        private const val DATABASE_NAME = "novel_database"
    }

    /**
     * 書き出し処理の結果を保持するデータクラス。
     */
    data class ExportResult(
        val success: Boolean,
        val bytesCopied: Long = 0L,
        val errorMessage: String? = null
    )

    /**
     * 指定されたURIへデータベースファイルを書き出す。
     *
     * @param destinationUri 書き出し先のURI
     * @return 書き出し処理の結果
     */
    suspend fun exportDatabase(destinationUri: Uri): ExportResult {
        return withContext(Dispatchers.IO) {
            try {
                // データベースのWALをチェックポイントして最新状態を反映
                val supportDb = NovelDatabase.getDatabase(context).openHelper.writableDatabase
                try {
                    supportDb.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
                        if (cursor.moveToFirst()) {
                            val busy = cursor.getInt(0)
                            if (busy > 0) {
                                Log.w(TAG, "WALチェックポイントが完全に完了していません (busy=$busy)")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "walチェックポイント中にエラー", e)
                }

                val databaseFile = context.getDatabasePath(DATABASE_NAME)
                if (databaseFile == null || !databaseFile.exists()) {
                    val message = "データベースファイルが見つかりません"
                    Log.e(TAG, message)
                    return@withContext ExportResult(success = false, errorMessage = message)
                }

                val bytes = copyFileToUri(databaseFile, destinationUri)
                Log.d(TAG, "データベースを書き出しました: ${bytes} bytes")
                ExportResult(success = true, bytesCopied = bytes)
            } catch (e: Exception) {
                Log.e(TAG, "データベース書き出し中にエラー", e)
                ExportResult(success = false, errorMessage = e.localizedMessage)
            }
        }
    }

    private fun copyFileToUri(sourceFile: File, destinationUri: Uri): Long {
        val contentResolver = context.contentResolver
        var copiedBytes = 0L

        val outputStream: OutputStream = contentResolver.openOutputStream(destinationUri, "w")
            ?: throw IllegalStateException("出力ストリームを開けませんでした")

        outputStream.use { outStream ->
            FileInputStream(sourceFile).use { inputStream ->
                copiedBytes = inputStream.copyTo(outStream)
                outStream.flush()
            }
        }

        return copiedBytes
    }
}
