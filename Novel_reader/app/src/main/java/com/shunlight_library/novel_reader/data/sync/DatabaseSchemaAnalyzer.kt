package com.shunlight_library.novel_reader.data.sync

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 外部SQLiteデータベースのスキーマを分析するためのユーティリティクラス
 * これは開発およびデバッグの目的で使用されます
 */
class DatabaseSchemaAnalyzer(private val context: Context) {

    companion object {
        private const val TAG = "DatabaseSchemaAnalyzer"
    }

    /**
     * 指定されたURIのSQLiteデータベースのスキーマ情報を取得します
     * @param uri 外部データベースのURI
     * @return スキーマ情報を含む文字列
     */
    suspend fun analyzeExternalDbSchema(uri: Uri): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "外部DBスキーマ分析を開始: $uri")

                // 一時ファイルを作成
                val tempDbFile = createTempDbFile(uri)
                if (tempDbFile == null) {
                    return@withContext "一時データベースファイルの作成に失敗しました"
                }

                // 外部DBを開く
                val externalDb = openExternalDatabase(tempDbFile.absolutePath)
                if (externalDb == null) {
                    tempDbFile.delete()
                    return@withContext "外部データベースを開けませんでした"
                }

                // スキーマ情報の取得
                val schemaInfo = buildString {
                    append("==== 外部データベーススキーマ情報 ====\n\n")

                    // テーブル一覧を取得
                    val tables = getTablesList(externalDb)
                    append("テーブル一覧: $tables\n\n")

                    // 各テーブルの構造を取得
                    for (table in tables) {
                        append("== テーブル: $table ==\n")
                        val tableInfo = getTableInfo(externalDb, table)
                        append(tableInfo)
                        append("\n")

                        // テーブルのサンプルデータ
                        append("サンプルデータ (最大5件):\n")
                        val sampleData = getSampleData(externalDb, table)
                        append(sampleData)
                        append("\n\n")
                    }

                    // インデックス情報
                    append("==== インデックス情報 ====\n")
                    for (table in tables) {
                        append("テーブル「$table」のインデックス:\n")
                        val indexInfo = getTableIndices(externalDb, table)
                        append(indexInfo)
                        append("\n")
                    }
                }

                // リソースの解放
                externalDb.close()
                tempDbFile.delete()

                Log.d(TAG, "外部DBスキーマ分析完了")
                return@withContext schemaInfo
            } catch (e: Exception) {
                Log.e(TAG, "外部DBスキーマ分析中にエラーが発生しました", e)
                return@withContext "エラー: ${e.message}"
            }
        }
    }

    /**
     * URIから一時的なデータベースファイルを作成します
     */
    private suspend fun createTempDbFile(uri: Uri): File? {
        return withContext(Dispatchers.IO) {
            try {
                val tempFile = File(context.cacheDir, "temp_schema_analysis.db")
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
     * テーブル一覧を取得します
     */
    private fun getTablesList(db: SQLiteDatabase): List<String> {
        val tablesList = mutableListOf<String>()
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
            null
        )

        cursor.use {
            while (it.moveToNext()) {
                tablesList.add(it.getString(0))
            }
        }

        return tablesList
    }

    /**
     * テーブルの構造情報を取得します
     */
    private fun getTableInfo(db: SQLiteDatabase, tableName: String): String {
        val tableInfo = StringBuilder()
        val cursor = db.rawQuery("PRAGMA table_info($tableName)", null)

        tableInfo.append("カラム情報:\n")
        cursor.use {
            val columnIndices = mapOf(
                "cid" to it.getColumnIndex("cid"),
                "name" to it.getColumnIndex("name"),
                "type" to it.getColumnIndex("type"),
                "notnull" to it.getColumnIndex("notnull"),
                "dflt_value" to it.getColumnIndex("dflt_value"),
                "pk" to it.getColumnIndex("pk")
            )

            while (it.moveToNext()) {
                val columnId = it.getInt(columnIndices["cid"] ?: 0)
                val columnName = it.getString(columnIndices["name"] ?: 1)
                val columnType = it.getString(columnIndices["type"] ?: 2)
                val notNull = it.getInt(columnIndices["notnull"] ?: 3) == 1
                val defaultValue = it.getString(columnIndices["dflt_value"] ?: 4)
                val isPk = it.getInt(columnIndices["pk"] ?: 5) == 1

                tableInfo.append("  - $columnName ($columnType)")
                if (notNull) tableInfo.append(" NOT NULL")
                if (defaultValue != null) tableInfo.append(" DEFAULT '$defaultValue'")
                if (isPk) tableInfo.append(" PRIMARY KEY")
                tableInfo.append("\n")
            }
        }

        return tableInfo.toString()
    }

    /**
     * テーブルのサンプルデータを取得します
     */
    private fun getSampleData(db: SQLiteDatabase, tableName: String): String {
        val sampleData = StringBuilder()
        val cursor = db.rawQuery("SELECT * FROM $tableName LIMIT 5", null)

        cursor.use {
            val columnNames = it.columnNames
            val rowCount = it.count

            if (rowCount == 0) {
                return "データなし"
            }

            // カラム名を取得
            sampleData.append(columnNames.joinToString(" | "))
            sampleData.append("\n")
            sampleData.append("-".repeat(columnNames.joinToString(" | ").length))
            sampleData.append("\n")

            // データ行を取得
            while (it.moveToNext()) {
                val rowData = mutableListOf<String>()
                for (i in 0 until it.columnCount) {
                    val value = when (it.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> "NULL"
                        Cursor.FIELD_TYPE_INTEGER -> it.getLong(i).toString()
                        Cursor.FIELD_TYPE_FLOAT -> it.getDouble(i).toString()
                        Cursor.FIELD_TYPE_STRING -> it.getString(i) ?: "NULL"
                        else -> "?"
                    }
                    rowData.add(value)
                }
                sampleData.append(rowData.joinToString(" | "))
                sampleData.append("\n")
            }
        }

        return sampleData.toString()
    }

    /**
     * テーブルのインデックス情報を取得します
     */
    private fun getTableIndices(db: SQLiteDatabase, tableName: String): String {
        val indexInfo = StringBuilder()
        val cursor = db.rawQuery(
            "SELECT name, sql FROM sqlite_master WHERE type = 'index' AND tbl_name = ?",
            arrayOf(tableName)
        )

        cursor.use {
            if (it.count == 0) {
                return "インデックスなし\n"
            }

            while (it.moveToNext()) {
                val indexName = it.getString(0)
                val indexSql = it.getString(1)
                indexInfo.append("  - $indexName: $indexSql\n")
            }
        }

        return indexInfo.toString()
    }
}