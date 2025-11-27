/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Migration tests for NovelDatabase.
 */
package com.shunlight_library.novel_reader.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import org.junit.Assert.*

/**
 * NovelDatabaseのマイグレーションテスト
 *
 * データベースのバージョンアップ時にデータが失われないことを検証する。
 * MigrationTestHelperを使用して各マイグレーションをテストする。
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
    // v4→v5のマイグレーションテスト
    // ========================================

    @Test
    @Throws(IOException::class)
    fun migrate4To5_addsReadingRateColumn() {
        // Given: v4のデータベースを作成してエピソードを挿入
        helper.createDatabase(TEST_DB_NAME, 4).apply {
            execSQL(
                "INSERT INTO episodes (ncode, episode_no, body, e_title, update_time, is_read, is_bookmark) " +
                        "VALUES ('n1234ab', '1', 'テスト本文', '第1話', '2025-01-01 12:00:00', 0, 0)"
            )
            close()
        }

        // When: v5にマイグレーション
        val db = helper.runMigrationsAndValidate(TEST_DB_NAME, 5, true, MIGRATION_4_5)

        // Then: reading_rateカラムが追加されていることを確認
        val cursor = db.query("SELECT ncode, episode_no, reading_rate FROM episodes WHERE ncode = 'n1234ab'")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("n1234ab", it.getString(0))
            assertEquals("1", it.getString(1))
            assertEquals(0.0, it.getDouble(2), 0.001)  // デフォルト値が0.0
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate4To5_preservesExistingData() {
        // Given: v4のデータベースに複数のエピソードを挿入
        helper.createDatabase(TEST_DB_NAME, 4).apply {
            execSQL(
                "INSERT INTO episodes (ncode, episode_no, body, e_title, update_time, is_read, is_bookmark) " +
                        "VALUES ('n1234ab', '1', '本文1', '第1話', '2025-01-01 12:00:00', 1, 0)"
            )
            execSQL(
                "INSERT INTO episodes (ncode, episode_no, body, e_title, update_time, is_read, is_bookmark) " +
                        "VALUES ('n1234ab', '2', '本文2', '第2話', '2025-01-02 12:00:00', 0, 1)"
            )
            close()
        }

        // When: v5にマイグレーション
        val db = helper.runMigrationsAndValidate(TEST_DB_NAME, 5, true, MIGRATION_4_5)

        // Then: 既存データが保持されていることを確認
        val cursor = db.query("SELECT ncode, episode_no, e_title, is_read, is_bookmark FROM episodes ORDER BY episode_no")
        cursor.use {
            assertTrue(it.moveToFirst())

            // エピソード1
            assertEquals("n1234ab", it.getString(0))
            assertEquals("1", it.getString(1))
            assertEquals("第1話", it.getString(2))
            assertEquals(1, it.getInt(3))  // is_read
            assertEquals(0, it.getInt(4))  // is_bookmark

            // エピソード2
            assertTrue(it.moveToNext())
            assertEquals("n1234ab", it.getString(0))
            assertEquals("2", it.getString(1))
            assertEquals("第2話", it.getString(2))
            assertEquals(0, it.getInt(3))  // is_read
            assertEquals(1, it.getInt(4))  // is_bookmark
        }
    }

    // ========================================
    // v9→v10のマイグレーションテスト
    // ========================================

    @Test
    @Throws(IOException::class)
    fun migrate9To10_createsEpisodeMappingTable() {
        // Given: v9のデータベースを作成
        helper.createDatabase(TEST_DB_NAME, 9).apply {
            close()
        }

        // When: v10にマイグレーション
        val db = helper.runMigrationsAndValidate(TEST_DB_NAME, 10, true, MIGRATION_9_10)

        // Then: episode_mappingテーブルが作成されていることを確認
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='episode_mapping'")
        cursor.use {
            assertTrue("episode_mappingテーブルが存在すること", it.moveToFirst())
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate9To10_episodeMappingTableHasCorrectStructure() {
        // Given: v9のデータベースを作成
        helper.createDatabase(TEST_DB_NAME, 9).apply {
            close()
        }

        // When: v10にマイグレーション
        val db = helper.runMigrationsAndValidate(TEST_DB_NAME, 10, true, MIGRATION_9_10)

        // Then: episode_mappingテーブルにデータを挿入できることを確認
        db.execSQL(
            "INSERT INTO episode_mapping (ncode, episode_no, kakuyomu_episode_id) " +
                    "VALUES ('K9zXYt1A2B3', 1, '1177354054887277844')"
        )

        val cursor = db.query("SELECT ncode, episode_no, kakuyomu_episode_id FROM episode_mapping")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("K9zXYt1A2B3", it.getString(0))
            assertEquals(1, it.getInt(1))
            assertEquals("1177354054887277844", it.getString(2))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate9To10_preservesExistingTables() {
        // Given: v9のデータベースに小説とエピソードを挿入
        helper.createDatabase(TEST_DB_NAME, 9).apply {
            execSQL(
                "INSERT INTO novels_descs (ncode, title, author, Synopsis, main_tag, sub_tag, rating, " +
                        "last_update_date, total_ep, general_all_no, updated_at, site_type, registered_at) " +
                        "VALUES ('K9zXYt1A2B3', 'カクヨム小説', 'テスト作者', 'あらすじ', 'ファンタジー', " +
                        "'異世界', 2, '2025-01-01 12:00:00', 10, 5, '2025-01-01 12:00:00', 2, '2025-01-01 10:00:00')"
            )
            execSQL(
                "INSERT INTO episodes (ncode, episode_no, body, e_title, update_time, is_read, is_bookmark, reading_rate) " +
                        "VALUES ('K9zXYt1A2B3', '1', '本文', '第1話', '2025-01-01 12:00:00', 0, 0, 0.0)"
            )
            close()
        }

        // When: v10にマイグレーション
        val db = helper.runMigrationsAndValidate(TEST_DB_NAME, 10, true, MIGRATION_9_10)

        // Then: 既存の小説データが保持されていることを確認
        val novelCursor = db.query("SELECT ncode, title, site_type FROM novels_descs WHERE ncode = 'K9zXYt1A2B3'")
        novelCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("K9zXYt1A2B3", it.getString(0))
            assertEquals("カクヨム小説", it.getString(1))
            assertEquals(2, it.getInt(2))  // site_type=2（カクヨム）
        }

        // Then: 既存のエピソードデータが保持されていることを確認
        val episodeCursor = db.query("SELECT ncode, episode_no, e_title FROM episodes WHERE ncode = 'K9zXYt1A2B3'")
        episodeCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("K9zXYt1A2B3", it.getString(0))
            assertEquals("1", it.getString(1))
            assertEquals("第1話", it.getString(2))
        }
    }

    // ========================================
    // v10→v11のマイグレーションテスト
    // ========================================

    @Test
    @Throws(IOException::class)
    fun migrate10To11_addsRegisteredAtColumn() {
        // Given: v10のデータベースに小説を挿入
        helper.createDatabase(TEST_DB_NAME, 10).apply {
            execSQL(
                "INSERT INTO novels_descs (ncode, title, author, Synopsis, main_tag, sub_tag, rating, " +
                        "last_update_date, total_ep, general_all_no, updated_at, site_type) " +
                        "VALUES ('n1234ab', 'テスト小説', 'テスト作者', 'あらすじ', 'ファンタジー', " +
                        "'異世界', 2, '2025-01-15 12:00:00', 100, 50, '2025-01-15 12:00:00', 1)"
            )
            close()
        }

        // When: v11にマイグレーション
        val db = helper.runMigrationsAndValidate(TEST_DB_NAME, 11, true, MIGRATION_10_11)

        // Then: registered_atカラムが追加されていることを確認
        val cursor = db.query("SELECT ncode, registered_at FROM novels_descs WHERE ncode = 'n1234ab'")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("n1234ab", it.getString(0))
            assertNotNull(it.getString(1))
            // registered_atはlast_update_dateの値で初期化される
            assertEquals("2025-01-15 12:00:00", it.getString(1))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11_copiesLastUpdateDateToRegisteredAt() {
        // Given: v10のデータベースに異なる更新日時の小説を複数挿入
        helper.createDatabase(TEST_DB_NAME, 10).apply {
            execSQL(
                "INSERT INTO novels_descs (ncode, title, author, Synopsis, main_tag, sub_tag, rating, " +
                        "last_update_date, total_ep, general_all_no, updated_at, site_type) " +
                        "VALUES ('n1111aa', '小説1', '作者1', 'あらすじ1', 'タグ1', 'サブタグ1', 2, " +
                        "'2024-12-01 10:00:00', 50, 25, '2024-12-01 10:00:00', 1)"
            )
            execSQL(
                "INSERT INTO novels_descs (ncode, title, author, Synopsis, main_tag, sub_tag, rating, " +
                        "last_update_date, total_ep, general_all_no, updated_at, site_type) " +
                        "VALUES ('n2222bb', '小説2', '作者2', 'あらすじ2', 'タグ2', 'サブタグ2', 2, " +
                        "'2025-01-20 15:30:00', 100, 50, '2025-01-20 15:30:00', 1)"
            )
            close()
        }

        // When: v11にマイグレーション
        val db = helper.runMigrationsAndValidate(TEST_DB_NAME, 11, true, MIGRATION_10_11)

        // Then: 各小説のregistered_atがlast_update_dateからコピーされていることを確認
        val cursor = db.query("SELECT ncode, last_update_date, registered_at FROM novels_descs ORDER BY ncode")
        cursor.use {
            // 小説1
            assertTrue(it.moveToFirst())
            assertEquals("n1111aa", it.getString(0))
            assertEquals("2024-12-01 10:00:00", it.getString(1))
            assertEquals("2024-12-01 10:00:00", it.getString(2))

            // 小説2
            assertTrue(it.moveToNext())
            assertEquals("n2222bb", it.getString(0))
            assertEquals("2025-01-20 15:30:00", it.getString(1))
            assertEquals("2025-01-20 15:30:00", it.getString(2))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11_createsIndexOnRegisteredAt() {
        // Given: v10のデータベースを作成
        helper.createDatabase(TEST_DB_NAME, 10).apply {
            close()
        }

        // When: v11にマイグレーション
        val db = helper.runMigrationsAndValidate(TEST_DB_NAME, 11, true, MIGRATION_10_11)

        // Then: idx_novels_registeredインデックスが作成されていることを確認
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='index' AND name='idx_novels_registered'")
        cursor.use {
            assertTrue("idx_novels_registeredインデックスが存在すること", it.moveToFirst())
        }
    }

    // ========================================
    // 複数バージョンのマイグレーションテスト
    // ========================================

    @Test
    @Throws(IOException::class)
    fun migrateAll_from4To11() {
        // Given: v4のデータベースにデータを挿入
        helper.createDatabase(TEST_DB_NAME, 4).apply {
            // 小説データ（v4時点ではis_favoriteなし）
            execSQL(
                "INSERT INTO novels_descs (ncode, title, author, Synopsis, main_tag, sub_tag, rating, " +
                        "last_update_date, total_ep, general_all_no, updated_at) " +
                        "VALUES ('n1234ab', 'テスト小説', 'テスト作者', 'あらすじ', 'ファンタジー', " +
                        "'異世界', 2, '2025-01-01 12:00:00', 100, 50, '2025-01-01 12:00:00')"
            )
            // エピソードデータ（v4時点ではreading_rateなし）
            execSQL(
                "INSERT INTO episodes (ncode, episode_no, body, e_title, update_time, is_read, is_bookmark) " +
                        "VALUES ('n1234ab', '1', '本文', '第1話', '2025-01-01 12:00:00', 1, 0)"
            )
            close()
        }

        // When: v4からv11まで全てのマイグレーションを実行
        val db = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 11, true,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11
        )

        // Then: 小説データが正しく保持され、新しいカラムが追加されていることを確認
        val novelCursor = db.query(
            "SELECT ncode, title, is_favorite, userid, noveltype, length, site_type, registered_at " +
                    "FROM novels_descs WHERE ncode = 'n1234ab'"
        )
        novelCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("n1234ab", it.getString(0))
            assertEquals("テスト小説", it.getString(1))
            assertEquals(0, it.getInt(2))  // is_favorite（v6で追加）
            assertNull(it.getString(3))  // userid（v8で追加、null許容）
            assertNull(it.getString(4))  // noveltype（v8で追加、null許容）
            assertNull(it.getString(5))  // length（v8で追加、null許容）
            assertEquals(1, it.getInt(6))  // site_type（v9で追加、デフォルト1）
            assertEquals("2025-01-01 12:00:00", it.getString(7))  // registered_at（v11で追加）
        }

        // Then: エピソードデータが正しく保持され、reading_rateが追加されていることを確認
        val episodeCursor = db.query(
            "SELECT ncode, episode_no, e_title, is_read, is_bookmark, reading_rate " +
                    "FROM episodes WHERE ncode = 'n1234ab'"
        )
        episodeCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("n1234ab", it.getString(0))
            assertEquals("1", it.getString(1))
            assertEquals("第1話", it.getString(2))
            assertEquals(1, it.getInt(3))  // is_read
            assertEquals(0, it.getInt(4))  // is_bookmark
            assertEquals(0.0, it.getDouble(5), 0.001)  // reading_rate（v5で追加）
        }

        // Then: episode_mappingテーブルが作成されていることを確認（v10で追加）
        val tableCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='episode_mapping'")
        tableCursor.use {
            assertTrue("episode_mappingテーブルが存在すること", it.moveToFirst())
        }
    }

    // ========================================
    // エラーケースのテスト
    // ========================================

    @Test
    @Throws(IOException::class)
    fun migrate4To5_emptyDatabase_completesSuccessfully() {
        // Given: v4のデータベースを作成（データなし）
        helper.createDatabase(TEST_DB_NAME, 4).apply {
            close()
        }

        // When: v5にマイグレーション
        val db = helper.runMigrationsAndValidate(TEST_DB_NAME, 5, true, MIGRATION_4_5)

        // Then: エラーなくマイグレーションが完了することを確認
        assertNotNull(db)
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11_emptyDatabase_completesSuccessfully() {
        // Given: v10のデータベースを作成（データなし）
        helper.createDatabase(TEST_DB_NAME, 10).apply {
            close()
        }

        // When: v11にマイグレーション
        val db = helper.runMigrationsAndValidate(TEST_DB_NAME, 11, true, MIGRATION_10_11)

        // Then: エラーなくマイグレーションが完了することを確認
        assertNotNull(db)
    }
}
