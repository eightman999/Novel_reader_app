package com.shunlight_library.novel_reader.data.sync

import android.database.Cursor
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * データベース同期処理のためのユーティリティメソッドを提供するクラス
 */
object DatabaseSyncUtils {

    private const val TAG = "DatabaseSyncUtils"

    /**
     * 現在の日時文字列を取得します。デフォルト形式は "yyyy-MM-dd HH:mm:ss" です。
     * @param format 日時フォーマット（省略可）
     * @return フォーマットされた日時文字列
     */
    fun getCurrentDateTimeString(format: String = "yyyy-MM-dd HH:mm:ss"): String {
        val dateFormat = SimpleDateFormat(format, Locale.getDefault())
        return dateFormat.format(Date())
    }

    /**
     * 安全にカラムインデックスを取得します。カラムが存在しない場合はnullを返します。
     * @param cursor データベースカーソル
     * @param columnName カラム名
     * @return カラムインデックス、または存在しない場合はnull
     */
    fun getColumnIndexSafely(cursor: Cursor, columnName: String): Int? {
        return try {
            val index = cursor.getColumnIndex(columnName)
            if (index >= 0) index else null
        } catch (e: Exception) {
            Log.e(TAG, "カラム '$columnName' のインデックス取得中にエラーが発生しました", e)
            null
        }
    }

    /**
     * カーソルの特定のカラムからInt値を安全に取得します。データがない場合はデフォルト値を返します。
     * @param cursor データベースカーソル
     * @param columnIndex カラムインデックス
     * @param defaultValue デフォルト値（省略した場合は0）
     * @return カラムの値、またはデフォルト値
     */
    fun getIntSafely(cursor: Cursor, columnIndex: Int?, defaultValue: Int = 0): Int {
        return try {
            if (columnIndex != null && !cursor.isNull(columnIndex)) {
                cursor.getInt(columnIndex)
            } else {
                defaultValue
            }
        } catch (e: Exception) {
            Log.e(TAG, "Int値の取得中にエラーが発生しました", e)
            defaultValue
        }
    }

    /**
     * カーソルの特定のカラムから文字列を安全に取得します。データがない場合はデフォルト値を返します。
     * @param cursor データベースカーソル
     * @param columnIndex カラムインデックス
     * @param defaultValue デフォルト値（省略した場合は空文字列）
     * @return カラムの値、またはデフォルト値
     */
    fun getStringSafely(cursor: Cursor, columnIndex: Int?, defaultValue: String = ""): String {
        return try {
            if (columnIndex != null && !cursor.isNull(columnIndex)) {
                cursor.getString(columnIndex) ?: defaultValue
            } else {
                defaultValue
            }
        } catch (e: Exception) {
            Log.e(TAG, "文字列値の取得中にエラーが発生しました", e)
            defaultValue
        }
    }

    /**
     * カーソルの特定のカラムからLong値を安全に取得します。データがない場合はデフォルト値を返します。
     * @param cursor データベースカーソル
     * @param columnIndex カラムインデックス
     * @param defaultValue デフォルト値（省略した場合は0L）
     * @return カラムの値、またはデフォルト値
     */
    fun getLongSafely(cursor: Cursor, columnIndex: Int?, defaultValue: Long = 0L): Long {
        return try {
            if (columnIndex != null && !cursor.isNull(columnIndex)) {
                cursor.getLong(columnIndex)
            } else {
                defaultValue
            }
        } catch (e: Exception) {
            Log.e(TAG, "Long値の取得中にエラーが発生しました", e)
            defaultValue
        }
    }

    /**
     * テーブル名と同様のカラム名がある場合に、どちらも指す可能性がある両方のカラム名を試して値を取得します。
     * SQLiteでは小文字・大文字が区別されない場合があるため、このような処理が必要な場合があります。
     * @param cursor データベースカーソル
     * @param preferredName 優先するカラム名
     * @param alternativeName 代替カラム名
     * @param defaultValue カラムが見つからない場合のデフォルト値
     * @return カラムの文字列値、またはデフォルト値
     */
    fun getStringWithAlternativeColumn(
        cursor: Cursor,
        preferredName: String,
        alternativeName: String,
        defaultValue: String = ""
    ): String {
        val preferredIndex = getColumnIndexSafely(cursor, preferredName)
        return if (preferredIndex != null) {
            getStringSafely(cursor, preferredIndex, defaultValue)
        } else {
            val alternativeIndex = getColumnIndexSafely(cursor, alternativeName)
            getStringSafely(cursor, alternativeIndex, defaultValue)
        }
    }

    /**
     * データベーススキーマの互換性チェックを行うためのカラム情報クラス
     */
    data class ColumnInfo(
        val name: String,
        val type: String,
        val notNull: Boolean = false,
        val primaryKey: Boolean = false
    )

    /**
     * スキーマの互換性チェックのためのテーブル定義
     */
    object ExpectedSchema {
        // NovelDescEntityのテーブル定義
        val novelDescsColumns = listOf(
            ColumnInfo("ncode", "TEXT", true, true),
            ColumnInfo("title", "TEXT", true),
            ColumnInfo("author", "TEXT", true),
            ColumnInfo("Synopsis", "TEXT"),
            ColumnInfo("main_tag", "TEXT"),
            ColumnInfo("sub_tag", "TEXT"),
            ColumnInfo("rating", "INTEGER"),
            ColumnInfo("last_update_date", "TEXT"),
            ColumnInfo("total_ep", "INTEGER"),
            ColumnInfo("general_all_no", "INTEGER"),
            ColumnInfo("updated_at", "TEXT")
        )

        // EpisodeEntityのテーブル定義
        val episodesColumns = listOf(
            ColumnInfo("ncode", "TEXT", true, true),
            ColumnInfo("episode_no", "TEXT", true, true),
            ColumnInfo("body", "TEXT", true),
            ColumnInfo("e_title", "TEXT"),
            ColumnInfo("update_time", "TEXT")
        )

        // LastReadNovelEntityのテーブル定義
        val lastReadNovelColumns = listOf(
            ColumnInfo("ncode", "TEXT", true, true),
            ColumnInfo("date", "TEXT", true),
            ColumnInfo("episode_no", "INTEGER", true)
        )
    }
}