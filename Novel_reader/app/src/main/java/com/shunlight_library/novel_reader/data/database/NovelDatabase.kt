package com.shunlight_library.novel_reader.data.database
/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Room database configuration with migrations.
 */


import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shunlight_library.novel_reader.data.dao.EpisodeDao
import com.shunlight_library.novel_reader.data.dao.LastReadNovelDao
import com.shunlight_library.novel_reader.data.dao.NovelDescDao
import com.shunlight_library.novel_reader.data.dao.URLEntityDao
import com.shunlight_library.novel_reader.data.dao.UpdateQueueDao
import com.shunlight_library.novel_reader.data.dao.ImageCacheDao
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.LastReadNovelEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.entity.URLEntity
import com.shunlight_library.novel_reader.data.entity.UpdateQueueEntity
import com.shunlight_library.novel_reader.data.entity.ImageCacheEntity

/**
 * v1→v2のマイグレーション: 更新キュー用テーブルを作成
 */
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
/**
 * v2→v3のマイグレーション: エピソードテーブルに既読・ブックマーク列を追加
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE episodes ADD COLUMN is_read INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE episodes ADD COLUMN is_bookmark INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v3→v4のマイグレーション: URL情報を保存するテーブルを追加
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS url_entity (" +
                    "ncode TEXT NOT NULL PRIMARY KEY, " +
                    "api_url TEXT NOT NULL, " +
                    "url TEXT NOT NULL, " +
                    "is_r18 INTEGER NOT NULL DEFAULT 0)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_url_entity_ncode ON url_entity (ncode)"
        )
    }
}

/**
 * v4→v5のマイグレーション: エピソードに読書進捗率を追加
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE episodes ADD COLUMN reading_rate REAL NOT NULL DEFAULT 0.0")
    }
}

/**
 * v5→v6のマイグレーション: 小説テーブルにお気に入りフラグを追加
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE novels_descs ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_novels_favorite ON novels_descs (is_favorite)")
    }
}

/**
 * v6→v7のマイグレーション: 画像キャッシュ用テーブルを追加
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS image_cache (" +
                    "hash TEXT NOT NULL PRIMARY KEY, " +
                    "original_url TEXT NOT NULL, " +
                    "local_path TEXT NOT NULL, " +
                    "mime_type TEXT NOT NULL)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_image_cache_hash ON image_cache (hash)"
        )
    }
}

/**
 * v7→v8のマイグレーション: 作者ID、作品種別、文字数を追加
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE novels_descs ADD COLUMN userid TEXT")
        database.execSQL("ALTER TABLE novels_descs ADD COLUMN noveltype INTEGER")
        database.execSQL("ALTER TABLE novels_descs ADD COLUMN length INTEGER")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_novels_length ON novels_descs (length)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_novels_type ON novels_descs (noveltype)")
    }
}

/**
 * v8→v9のマイグレーション: サイト種別を追加（マルチサイト対応）
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // サイト種別カラムを追加 (1=小説家になろう, 2=カクヨム)
        database.execSQL("ALTER TABLE novels_descs ADD COLUMN site_type INTEGER NOT NULL DEFAULT 1")
        // サイト種別用のインデックスを作成
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_novels_site ON novels_descs (site_type)")
        // 既存データは全て小説家になろう (site_type=1) に設定
        database.execSQL("UPDATE novels_descs SET site_type = 1")
    }
}

/**
 * アプリ全体で使用するRoomデータベース定義
 */
@Database(
    entities = [
        EpisodeEntity::class,
        NovelDescEntity::class,
        LastReadNovelEntity::class,
        UpdateQueueEntity::class,
        URLEntity::class,
        ImageCacheEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class NovelDatabase : RoomDatabase() {
    abstract fun episodeDao(): EpisodeDao
    abstract fun novelDescDao(): NovelDescDao
    abstract fun lastReadNovelDao(): LastReadNovelDao
    abstract fun updateQueueDao(): UpdateQueueDao
    abstract fun urlEntityDao(): URLEntityDao
    abstract fun imageCacheDao(): ImageCacheDao

    companion object {
        @Volatile
        private var INSTANCE: NovelDatabase? = null

        /**
         * シングルトンとしてデータベースインスタンスを取得する
         */
        fun getDatabase(context: Context): NovelDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NovelDatabase::class.java,
                    "novel_database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}