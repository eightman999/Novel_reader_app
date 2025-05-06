package com.shunlight_library.novel_reader.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shunlight_library.novel_reader.data.dao.EpisodeDao
import com.shunlight_library.novel_reader.data.dao.LastReadNovelDao
import com.shunlight_library.novel_reader.data.dao.NovelDescDao
import com.shunlight_library.novel_reader.data.dao.UpdateQueueDao
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.LastReadNovelEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.entity.UpdateQueueEntity
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS update_queue (" +
                    "ncode TEXT NOT NULL PRIMARY KEY, " +
                    "total_ep INTEGER NOT NULL, " +
                    "general_all_no INTEGER NOT NULL, " +
                    "update_time TEXT NOT NULL)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_update_queue_time ON update_queue (update_time)"
        )
    }
}
@Database(
    entities = [
        EpisodeEntity::class,
        NovelDescEntity::class,
        LastReadNovelEntity::class,
        UpdateQueueEntity::class
    ],
    version = 2,  // バージョンを1から2に更新
    exportSchema = false
)
abstract class NovelDatabase : RoomDatabase() {
    abstract fun episodeDao(): EpisodeDao
    abstract fun novelDescDao(): NovelDescDao
    abstract fun lastReadNovelDao(): LastReadNovelDao
    abstract fun updateQueueDao(): UpdateQueueDao
    companion object {
        @Volatile
        private var INSTANCE: NovelDatabase? = null

        fun getDatabase(context: Context): NovelDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NovelDatabase::class.java,
                    "novel_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}