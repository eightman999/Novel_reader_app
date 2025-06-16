package com.shunlight_library.novel_reader.data.export

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import com.shunlight_library.novel_reader.NovelReaderApplication
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.LastReadNovelEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.repository.NovelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class DatabaseExportManager(private val context: Context) {
    companion object {
        private const val TAG = "DatabaseExportManager"
    }

    private val repository: NovelRepository = NovelReaderApplication.getRepository()

    suspend fun exportSelectedNovels(uri: Uri, ncodes: List<String>): Boolean {
        return withContext(Dispatchers.IO) {
            var db: SQLiteDatabase? = null
            var tempFile: File? = null
            try {
                tempFile = File.createTempFile("export", ".db", context.cacheDir)
                db = SQLiteDatabase.openOrCreateDatabase(tempFile, null)

                createTables(db)

                for (ncode in ncodes) {
                    repository.getNovelByNcode(ncode)?.let { db.insert("novels_descs", null, it.toContentValues()) }
                    repository.getEpisodesListByNcode(ncode).forEach { ep ->
                        db.insert("episodes", null, ep.toContentValues())
                    }
                    repository.getLastReadByNcode(ncode)?.let { db.insert("last_read_novel", null, it.toContentValues()) }
                }

                context.contentResolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(tempFile).use { input ->
                        input.copyTo(output)
                    }
                } ?: return@withContext false

                true
            } catch (e: Exception) {
                Log.e(TAG, "export error", e)
                false
            } finally {
                db?.close()
                tempFile?.delete()
            }
        }
    }

    private fun createTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS novels_descs (ncode TEXT PRIMARY KEY, title TEXT, author TEXT, Synopsis TEXT, main_tag TEXT, sub_tag TEXT, rating INTEGER, last_update_date TEXT, total_ep INTEGER, general_all_no INTEGER, updated_at TEXT, is_favorite INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_novels_last_update ON novels_descs (last_update_date)")
        db.execSQL("CREATE TABLE IF NOT EXISTS episodes (ncode TEXT NOT NULL, episode_no TEXT NOT NULL, body TEXT NOT NULL, e_title TEXT, update_time TEXT, is_read INTEGER NOT NULL DEFAULT 0, is_bookmark INTEGER NOT NULL DEFAULT 0, reading_rate REAL NOT NULL DEFAULT 0.0, PRIMARY KEY(ncode, episode_no))")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_episodes_ncode ON episodes (ncode, episode_no)")
        db.execSQL("CREATE TABLE IF NOT EXISTS last_read_novel (ncode TEXT PRIMARY KEY, date TEXT, episode_no INTEGER)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_last_read ON last_read_novel (ncode, date)")
    }

    private fun NovelDescEntity.toContentValues(): ContentValues = ContentValues().apply {
        put("ncode", ncode)
        put("title", title)
        put("author", author)
        put("Synopsis", Synopsis)
        put("main_tag", main_tag)
        put("sub_tag", sub_tag)
        put("rating", rating)
        put("last_update_date", last_update_date)
        put("total_ep", total_ep)
        put("general_all_no", general_all_no)
        put("updated_at", updated_at)
        put("is_favorite", if (is_favorite) 1 else 0)
    }

    private fun EpisodeEntity.toContentValues(): ContentValues = ContentValues().apply {
        put("ncode", ncode)
        put("episode_no", episode_no)
        put("body", body)
        put("e_title", e_title)
        put("update_time", update_time)
        put("is_read", if (is_read) 1 else 0)
        put("is_bookmark", if (is_bookmark) 1 else 0)
        put("reading_rate", reading_rate)
    }

    private fun LastReadNovelEntity.toContentValues(): ContentValues = ContentValues().apply {
        put("ncode", ncode)
        put("date", date)
        put("episode_no", episode_no)
    }
}
