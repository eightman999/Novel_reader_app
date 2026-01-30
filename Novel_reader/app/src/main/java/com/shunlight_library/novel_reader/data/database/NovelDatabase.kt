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
import com.shunlight_library.novel_reader.data.dao.EpisodeMappingDao
import com.shunlight_library.novel_reader.data.dao.RegistrationQueueDao
import com.shunlight_library.novel_reader.data.dao.TempEpisodeDao
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.LastReadNovelEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.entity.URLEntity
import com.shunlight_library.novel_reader.data.entity.UpdateQueueEntity
import com.shunlight_library.novel_reader.data.entity.ImageCacheEntity
import com.shunlight_library.novel_reader.data.entity.EpisodeMappingEntity
import com.shunlight_library.novel_reader.data.entity.RegistrationQueueEntity
import com.shunlight_library.novel_reader.data.entity.TempEpisodeEntity

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
 * v9→v10のマイグレーション: カクヨム用エピソードマッピングテーブルを追加
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // エピソードマッピングテーブルを作成
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS episode_mapping (" +
                    "ncode TEXT NOT NULL, " +
                    "episode_no INTEGER NOT NULL, " +
                    "kakuyomu_episode_id TEXT NOT NULL, " +
                    "PRIMARY KEY (ncode, episode_no))"
        )
        // インデックスを作成
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_episode_mapping_ncode_no ON episode_mapping (ncode, episode_no)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_episode_mapping_ncode_id ON episode_mapping (ncode, kakuyomu_episode_id)"
        )
    }
}

/**
 * v10→v11のマイグレーション: データベース登録日時を追加
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // registered_atカラムを追加（既存データにはlast_update_dateの値をコピー）
        database.execSQL("ALTER TABLE novels_descs ADD COLUMN registered_at TEXT NOT NULL DEFAULT ''")
        // 既存データのregistered_atをlast_update_dateと同じ値に設定
        database.execSQL("UPDATE novels_descs SET registered_at = last_update_date")
        // インデックスを作成
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_novels_registered ON novels_descs (registered_at)")
    }
}

/**
 * v11→v12のマイグレーション: パフォーマンス最適化のためのインデックス追加
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // novels_descsテーブルにソート・検索用インデックスを追加
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_novels_total_ep ON novels_descs (total_ep)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_novels_author ON novels_descs (author)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_novels_title ON novels_descs (title)")

        // episodesテーブルにフィルタリング用インデックスを追加
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_episodes_is_read ON episodes (is_read)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_episodes_is_bookmark ON episodes (is_bookmark)")

        // 複合インデックス追加（パフォーマンス最適化）
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_novels_site_favorite ON novels_descs (site_type, is_favorite)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_episodes_ncode_read ON episodes (ncode, is_read)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_episodes_ncode_bookmark ON episodes (ncode, is_bookmark)")
    }
}

/**
 * v12→v13のマイグレーション: スキーマ定義の整合性確認のみ（実際の変更なし）
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // v12で追加されたインデックスがエンティティ定義に追加されたため、
        // スキーマ検証を通過させるためのマイグレーション（実際の変更なし）
    }
}

/**
 * v13→v14のマイグレーション: 新規小説登録キュー用テーブルを追加
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS registration_queue (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "ncode TEXT NOT NULL, " +
                    "site_type INTEGER NOT NULL, " +
                    "title TEXT NOT NULL, " +
                    "url TEXT NOT NULL, " +
                    "is_r18 INTEGER NOT NULL, " +
                    "status INTEGER NOT NULL, " +
                    "current_episode INTEGER NOT NULL, " +
                    "total_episodes INTEGER NOT NULL, " +
                    "error_message TEXT, " +
                    "created_at TEXT NOT NULL, " +
                    "started_at TEXT, " +
                    "completed_at TEXT)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_registration_queue_status ON registration_queue (status)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_registration_queue_ncode ON registration_queue (ncode)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_registration_queue_created_at ON registration_queue (created_at)"
        )
    }
}

/**
 * v14→v15のマイグレーション: 一時エピソードテーブルを追加（ダウンロードステージング用）
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS temp_episodes (" +
                    "ncode TEXT NOT NULL, " +
                    "episode_no TEXT NOT NULL, " +
                    "body TEXT NOT NULL, " +
                    "e_title TEXT NOT NULL, " +
                    "update_time TEXT NOT NULL, " +
                    "is_read INTEGER NOT NULL DEFAULT 0, " +
                    "is_bookmark INTEGER NOT NULL DEFAULT 0, " +
                    "reading_rate REAL NOT NULL DEFAULT 0.0, " +
                    "queue_id INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY (ncode, episode_no))"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_temp_episodes_ncode ON temp_episodes (ncode, episode_no)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_temp_episodes_queue_id ON temp_episodes (queue_id)"
        )
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
        ImageCacheEntity::class,
        EpisodeMappingEntity::class,
        RegistrationQueueEntity::class,
        TempEpisodeEntity::class
    ],
    version = 15,
    exportSchema = false
)
abstract class NovelDatabase : RoomDatabase() {
    abstract fun episodeDao(): EpisodeDao
    abstract fun novelDescDao(): NovelDescDao
    abstract fun lastReadNovelDao(): LastReadNovelDao
    abstract fun updateQueueDao(): UpdateQueueDao
    abstract fun urlEntityDao(): URLEntityDao
    abstract fun imageCacheDao(): ImageCacheDao
    abstract fun episodeMappingDao(): EpisodeMappingDao
    abstract fun registrationQueueDao(): RegistrationQueueDao
    abstract fun tempEpisodeDao(): TempEpisodeDao

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
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}