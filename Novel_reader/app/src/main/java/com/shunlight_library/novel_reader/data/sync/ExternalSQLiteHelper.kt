package com.shunlight_library.novel_reader.data.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 外部SQLiteデータベースとの操作を助けるヘルパークラス
 */
class ExternalSQLiteHelper(private val context: Context) {

    companion object {
        private const val TAG = "ExternalSQLiteHelper"
    }

    /**
     * URIから一時的なデータベースファイルを作成し、それを開くためのSQLiteDatabaseインスタンスを返します
     * @param uri 外部データベースのURI
     * @return 開いたデータベースとそれに対応する一時ファイルのペア、またはnull（エラーの場合）
     */
    suspend fun openExternalDatabase(uri: Uri): Pair<SQLiteDatabase, File>? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "外部DBを開く: $uri")

                // 一時ファイルを作成
                val tempDbFile = createTempDbFile(uri)
                if (tempDbFile == null) {
                    Log.e(TAG, "一時データベースファイルの作成に失敗しました")
                    return@withContext null
                }

                // 外部DBを開く
                val db = try {
                    SQLiteDatabase.openDatabase(
                        tempDbFile.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READONLY
                    )
                } catch (e: SQLiteException) {
                    Log.e(TAG, "SQLiteデータベースを開く際にエラーが発生しました", e)
                    tempDbFile.delete()
                    return@withContext null
                }

                return@withContext Pair(db, tempDbFile)
            } catch (e: Exception) {
                Log.e(TAG, "外部DBを開く際にエラーが発生しました", e)
                return@withContext null
            }
        }
    }

    /**
     * URIから一時的なデータベースファイルを作成します
     */
    private suspend fun createTempDbFile(uri: Uri): File? {
        return withContext(Dispatchers.IO) {
            try {
                val tempFile = File(context.cacheDir, "temp_external_db_${System.currentTimeMillis()}.db")
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
     * データベースリソースを閉じ、一時ファイルを削除します
     */
    fun closeAndCleanup(db: SQLiteDatabase?, tempFile: File?) {
        try {
            db?.close()
            tempFile?.delete()
        } catch (e: Exception) {
            Log.e(TAG, "リソースのクリーンアップ中にエラーが発生しました", e)
        }
    }

    /**
     * テーブルが存在するかチェックします
     */
    fun isTableExists(db: SQLiteDatabase, tableName: String): Boolean {
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        )
        val exists = cursor.use { it.count > 0 }
        return exists
    }

    /**
     * 指定されたテーブルのレコード数を取得します
     */
    fun getTableCount(db: SQLiteDatabase, tableName: String): Int {
        var count = 0
        try {
            val cursor = db.rawQuery("SELECT COUNT(*) FROM $tableName", null)
            cursor.use {
                if (it.moveToFirst()) {
                    count = it.getInt(0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "テーブル $tableName のレコード数取得中にエラーが発生しました", e)
        }
        return count
    }

    /**
     * 現在のデータベースが有効なSQLiteデータベースかどうかを確認します
     */
    fun isValidSQLiteDatabase(db: SQLiteDatabase): Boolean {
        try {
            // SQLiteデータベースの基本的なクエリを実行
            val cursor = db.rawQuery("SELECT sqlite_version()", null)
            cursor.use {
                return it.moveToFirst() // バージョン情報が取得できればOK
            }
        } catch (e: Exception) {
            Log.e(TAG, "SQLiteデータベースの検証中にエラーが発生しました", e)
            return false
        }
    }
}