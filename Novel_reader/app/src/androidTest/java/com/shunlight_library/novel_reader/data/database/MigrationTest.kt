/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Migration tests for NovelDatabase.
 */
package com.shunlight_library.novel_reader.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * NovelDatabaseのマイグレーションテスト
 *
 * M4対策:
 * - exportSchema で生成済みの 16.json / 17.json / 18.json を使う経路を主検証とする
 * - 旧版スキーマJSONが欠落している 4/9/10/11 系の createDatabase 依存テストは廃止し、
 *   重要マイグレーションは SupportSQLiteDatabase を手組みして Migration.migrate() を直接検証する
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val TEST_DB_NAME = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NovelDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    // ========================================
    // v16→v17（スキーマJSONあり）
    // ========================================

    @Test
    @Throws(IOException::class)
    fun migrate16To17_createsImageCacheHashIndex() {
        helper.createDatabase(TEST_DB_NAME, 16).apply {
            execSQL(
                "INSERT INTO image_cache (hash, original_url, local_path, mime_type) " +
                    "VALUES ('abc123', 'https://example.com/a.jpg', '/tmp/a.jpg', 'image/jpeg')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB_NAME, 17, true, MIGRATION_16_17)

        val indexCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_image_cache_hash'"
        )
        indexCursor.use {
            assertTrue("idx_image_cache_hash が存在すること", it.moveToFirst())
        }

        val dataCursor = db.query(
            "SELECT hash FROM image_cache WHERE original_url = 'https://example.com/a.jpg'"
        )
        dataCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("abc123", it.getString(0))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate16To17_emptyDatabase_completesSuccessfully() {
        helper.createDatabase(TEST_DB_NAME, 16).apply { close() }
        val db = helper.runMigrationsAndValidate(TEST_DB_NAME, 17, true, MIGRATION_16_17)
        assertNotNull(db)
    }

    // ========================================
    // v17→v18（L4: R18 sub_site 誤分類の是正）
    // ========================================

    @Test
    @Throws(IOException::class)
    fun migrate17To18_resetsMisclassifiedR18SubSite() {
        helper.createDatabase(TEST_DB_NAME, 17).apply {
            // 一般: sub_site=1 のまま
            execSQL(
                "INSERT INTO novels_descs (ncode, title, author, Synopsis, main_tag, sub_tag, rating, " +
                    "last_update_date, total_ep, general_all_no, updated_at, registered_at, is_favorite, " +
                    "site_type, sub_site, end_flag, last_checked_at) VALUES (" +
                    "'n1111aa', '一般小説', '作者A', 'あらすじ', 'tag', '', 2, " +
                    "'2026-01-01 00:00:00', 1, 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0, " +
                    "1, 1, 0, '')"
            )
            // R18で誤ってノクターン(2)扱い → 0 に戻る対象
            execSQL(
                "INSERT INTO novels_descs (ncode, title, author, Synopsis, main_tag, sub_tag, rating, " +
                    "last_update_date, total_ep, general_all_no, updated_at, registered_at, is_favorite, " +
                    "site_type, sub_site, end_flag, last_checked_at) VALUES (" +
                    "'n2222bb', 'R18誤分類', '作者B', 'あらすじ', 'tag', '', 1, " +
                    "'2026-01-01 00:00:00', 1, 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0, " +
                    "1, 2, 0, '')"
            )
            // 既にムーンライト(3)と判明しているR18 → 維持
            execSQL(
                "INSERT INTO novels_descs (ncode, title, author, Synopsis, main_tag, sub_tag, rating, " +
                    "last_update_date, total_ep, general_all_no, updated_at, registered_at, is_favorite, " +
                    "site_type, sub_site, end_flag, last_checked_at) VALUES (" +
                    "'n3333cc', 'ムーンライト', '作者C', 'あらすじ', 'tag', '', 1, " +
                    "'2026-01-01 00:00:00', 1, 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0, " +
                    "1, 3, 0, '')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB_NAME, 18, true, MIGRATION_17_18)

        assertEquals(1, querySubSite(db, "n1111aa"))
        assertEquals(0, querySubSite(db, "n2222bb"))
        assertEquals(3, querySubSite(db, "n3333cc"))
    }

    @Test
    @Throws(IOException::class)
    fun migrate16To18_chain() {
        helper.createDatabase(TEST_DB_NAME, 16).apply { close() }
        val db = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 18, true,
            MIGRATION_16_17,
            MIGRATION_17_18
        )
        assertNotNull(db)
    }

    // ========================================
    // 旧マイグレーション: 手組みDBで直接検証（スキーマJSON不要）
    // ========================================

    @Test
    fun migrate4To5_direct_addsReadingRateColumn() {
        val db = openScratchDatabase()
        try {
            db.execSQL(
                "CREATE TABLE episodes (" +
                    "ncode TEXT NOT NULL, episode_no TEXT NOT NULL, body TEXT NOT NULL, " +
                    "e_title TEXT NOT NULL, update_time TEXT NOT NULL, " +
                    "is_read INTEGER NOT NULL, is_bookmark INTEGER NOT NULL, " +
                    "PRIMARY KEY(ncode, episode_no))"
            )
            db.execSQL(
                "INSERT INTO episodes (ncode, episode_no, body, e_title, update_time, is_read, is_bookmark) " +
                    "VALUES ('n1234ab', '1', 'テスト本文', '第1話', '2025-01-01 12:00:00', 1, 0)"
            )

            MIGRATION_4_5.migrate(db)

            val cursor = db.query(
                "SELECT reading_rate, is_read FROM episodes WHERE ncode = 'n1234ab' AND episode_no = '1'"
            )
            cursor.use {
                assertTrue(it.moveToFirst())
                assertEquals(0.0, it.getDouble(0), 0.001)
                assertEquals(1, it.getInt(1))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun migrate15To16_direct_doesNotForceR18ToNocturne() {
        val db = openScratchDatabase()
        try {
            // v15相当: sub_site 列なし
            db.execSQL(
                "CREATE TABLE novels_descs (" +
                    "ncode TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, author TEXT NOT NULL, " +
                    "Synopsis TEXT NOT NULL, main_tag TEXT NOT NULL, sub_tag TEXT NOT NULL, " +
                    "rating INTEGER NOT NULL, last_update_date TEXT NOT NULL, total_ep INTEGER NOT NULL, " +
                    "general_all_no INTEGER NOT NULL, userid TEXT, noveltype INTEGER, length INTEGER, " +
                    "updated_at TEXT NOT NULL, registered_at TEXT NOT NULL DEFAULT '', " +
                    "is_favorite INTEGER NOT NULL DEFAULT 0, site_type INTEGER NOT NULL DEFAULT 1)"
            )
            db.execSQL(
                "INSERT INTO novels_descs (ncode, title, author, Synopsis, main_tag, sub_tag, rating, " +
                    "last_update_date, total_ep, general_all_no, updated_at, registered_at, is_favorite, site_type) " +
                    "VALUES ('n_r18', 'R18', 'a', 's', 't', '', 1, '2026-01-01', 1, 1, '2026-01-01', '2026-01-01', 0, 1)"
            )
            db.execSQL(
                "INSERT INTO novels_descs (ncode, title, author, Synopsis, main_tag, sub_tag, rating, " +
                    "last_update_date, total_ep, general_all_no, updated_at, registered_at, is_favorite, site_type) " +
                    "VALUES ('n_gen', '一般', 'a', 's', 't', '', 2, '2026-01-01', 1, 1, '2026-01-01', '2026-01-01', 0, 1)"
            )

            MIGRATION_15_16.migrate(db)

            assertEquals(0, querySubSite(db, "n_r18")) // L4: R18は不明のまま
            assertEquals(1, querySubSite(db, "n_gen")) // 一般はなろう

            // インデックス存在
            val idx = db.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_novels_sub_site'"
            )
            idx.use { assertTrue(it.moveToFirst()) }
        } finally {
            db.close()
        }
    }

    @Test
    fun migrate9To10_direct_createsEpisodeMappingTable() {
        val db = openScratchDatabase()
        try {
            MIGRATION_9_10.migrate(db)
            val cursor = db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='episode_mapping'"
            )
            cursor.use {
                assertTrue("episode_mapping テーブルが存在すること", it.moveToFirst())
            }
        } finally {
            db.close()
        }
    }

    // ========================================
    // helpers
    // ========================================

    private fun querySubSite(db: SupportSQLiteDatabase, ncode: String): Int {
        val cursor = db.query("SELECT sub_site FROM novels_descs WHERE ncode = '$ncode'")
        cursor.use {
            assertTrue("ncode=$ncode が存在すること", it.moveToFirst())
            return it.getInt(0)
        }
    }

    private fun openScratchDatabase(): SupportSQLiteDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // 毎回ユニークな名前で衝突を避ける
        val name = "scratch-migration-${System.nanoTime()}.db"
        context.deleteDatabase(name)
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // 空のDB。各テストが必要なテーブルを作る
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    // no-op
                }
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }
}
