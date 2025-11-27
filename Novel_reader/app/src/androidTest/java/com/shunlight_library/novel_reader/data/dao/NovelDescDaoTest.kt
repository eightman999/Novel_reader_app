/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Instrumented tests for NovelDescDao.
 */
package com.shunlight_library.novel_reader.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shunlight_library.novel_reader.data.database.NovelDatabase
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * NovelDescDaoの計装テスト
 *
 * インメモリデータベースを使用してDAOのCRUD操作を検証する。
 * 実際のAndroid環境で実行されるため、Room DatabaseとFlowの動作を確認できる。
 */
@RunWith(AndroidJUnit4::class)
class NovelDescDaoTest {

    private lateinit var database: NovelDatabase
    private lateinit var novelDescDao: NovelDescDao

    @Before
    fun createDb() {
        // インメモリデータベースを作成（テスト終了時に自動削除される）
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NovelDatabase::class.java
        )
            .allowMainThreadQueries()  // テスト用に許可
            .build()

        novelDescDao = database.novelDescDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    // ========================================
    // ヘルパー関数
    // ========================================

    /**
     * テスト用のNovelDescEntityを作成
     */
    private fun createTestNovel(
        ncode: String = "n1234ab",
        title: String = "テスト小説",
        author: String = "テスト作者",
        rating: Int = 2,
        isFavorite: Boolean = false,
        siteType: Int = 1
    ): NovelDescEntity {
        return NovelDescEntity(
            ncode = ncode,
            title = title,
            author = author,
            Synopsis = "これはテスト用のあらすじです。",
            main_tag = "ファンタジー",
            sub_tag = "異世界転生",
            rating = rating,
            last_update_date = "2025-01-01 12:00:00",
            total_ep = 100,
            general_all_no = 50,
            userid = "12345",
            noveltype = 1,
            length = 100000,
            updated_at = "2025-01-01 12:00:00",
            is_favorite = isFavorite,
            site_type = siteType,
            registered_at = "2025-01-01 10:00:00"
        )
    }

    // ========================================
    // 挿入と取得のテスト
    // ========================================

    @Test
    fun insertNovel_and_getByNcode_returnsCorrectNovel() = runTest {
        // Given: テスト用の小説を作成
        val novel = createTestNovel(ncode = "n1234ab")

        // When: 小説を挿入
        novelDescDao.insertNovel(novel)

        // Then: Ncodeで取得できることを確認
        val retrieved = novelDescDao.getNovelByNcode("n1234ab")
        assertNotNull(retrieved)
        assertEquals("n1234ab", retrieved?.ncode)
        assertEquals("テスト小説", retrieved?.title)
        assertEquals("テスト作者", retrieved?.author)
    }

    @Test
    fun insertNovel_duplicateNcode_replacesExisting() = runTest {
        // Given: 同じNcodeの小説を2つ作成
        val novel1 = createTestNovel(ncode = "n1234ab", title = "旧タイトル")
        val novel2 = createTestNovel(ncode = "n1234ab", title = "新タイトル")

        // When: 同じNcodeで2回挿入（OnConflictStrategy.REPLACE）
        novelDescDao.insertNovel(novel1)
        novelDescDao.insertNovel(novel2)

        // Then: 新しいデータで上書きされていることを確認
        val retrieved = novelDescDao.getNovelByNcode("n1234ab")
        assertEquals("新タイトル", retrieved?.title)
    }

    @Test
    fun insertNovels_multipleNovels_allInserted() = runTest {
        // Given: 複数の小説を作成
        val novels = listOf(
            createTestNovel(ncode = "n1111aa", title = "小説1"),
            createTestNovel(ncode = "n2222bb", title = "小説2"),
            createTestNovel(ncode = "n3333cc", title = "小説3")
        )

        // When: 一括挿入
        novelDescDao.insertNovels(novels)

        // Then: すべて取得できることを確認
        val all = novelDescDao.getAllNovels().first()
        assertEquals(3, all.size)
    }

    @Test
    fun getNovelByNcode_nonexistentNcode_returnsNull() = runTest {
        // When: 存在しないNcodeで取得
        val retrieved = novelDescDao.getNovelByNcode("nonexistent")

        // Then: nullが返ることを確認
        assertNull(retrieved)
    }

    // ========================================
    // 更新と削除のテスト
    // ========================================

    @Test
    fun updateNovel_modifiesExistingNovel() = runTest {
        // Given: 小説を挿入
        val novel = createTestNovel(ncode = "n1234ab", title = "旧タイトル")
        novelDescDao.insertNovel(novel)

        // When: タイトルを変更して更新
        val updatedNovel = novel.copy(title = "新タイトル")
        novelDescDao.updateNovel(updatedNovel)

        // Then: 更新されていることを確認
        val retrieved = novelDescDao.getNovelByNcode("n1234ab")
        assertEquals("新タイトル", retrieved?.title)
    }

    @Test
    fun deleteNovel_removesNovelFromDatabase() = runTest {
        // Given: 小説を挿入
        val novel = createTestNovel(ncode = "n1234ab")
        novelDescDao.insertNovel(novel)

        // When: 削除
        novelDescDao.deleteNovel(novel)

        // Then: 取得できないことを確認
        val retrieved = novelDescDao.getNovelByNcode("n1234ab")
        assertNull(retrieved)
    }

    // ========================================
    // Flow発火のテスト
    // ========================================

    @Test
    fun getAllNovels_emitsFlowOnDataChange() = runTest {
        // Given: 初期状態は空
        val initialNovels = novelDescDao.getAllNovels().first()
        assertEquals(0, initialNovels.size)

        // When: 小説を挿入
        novelDescDao.insertNovel(createTestNovel(ncode = "n1234ab"))

        // Then: Flowが新しいデータを発火
        val updatedNovels = novelDescDao.getAllNovels().first()
        assertEquals(1, updatedNovels.size)
    }

    @Test
    fun getAllNovels_orderedByLastUpdateDate() = runTest {
        // Given: 異なる更新日時の小説を挿入
        val novel1 = createTestNovel(ncode = "n1111aa", title = "古い").copy(
            last_update_date = "2024-01-01 00:00:00"
        )
        val novel2 = createTestNovel(ncode = "n2222bb", title = "新しい").copy(
            last_update_date = "2025-12-31 23:59:59"
        )
        val novel3 = createTestNovel(ncode = "n3333cc", title = "中間").copy(
            last_update_date = "2025-06-15 12:00:00"
        )

        novelDescDao.insertNovels(listOf(novel1, novel2, novel3))

        // When: 全小説を取得（降順ソート）
        val novels = novelDescDao.getAllNovels().first()

        // Then: 新しい順にソートされていることを確認
        assertEquals(3, novels.size)
        assertEquals("新しい", novels[0].title)
        assertEquals("中間", novels[1].title)
        assertEquals("古い", novels[2].title)
    }

    // ========================================
    // タグ検索のテスト
    // ========================================

    @Test
    fun getNovelsByTag_mainTagMatch_returnsNovels() = runTest {
        // Given: 異なるタグの小説を挿入
        val fantasy = createTestNovel(ncode = "n1111aa").copy(main_tag = "ファンタジー")
        val scifi = createTestNovel(ncode = "n2222bb").copy(main_tag = "SF")

        novelDescDao.insertNovels(listOf(fantasy, scifi))

        // When: "ファンタジー"タグで検索
        val result = novelDescDao.getNovelsByTag("ファンタジー").first()

        // Then: 該当する小説のみ取得
        assertEquals(1, result.size)
        assertEquals("n1111aa", result[0].ncode)
    }

    @Test
    fun getNovelsByTag_subTagMatch_returnsNovels() = runTest {
        // Given: サブタグを含む小説を挿入
        val novel = createTestNovel(ncode = "n1111aa").copy(
            main_tag = "ファンタジー",
            sub_tag = "異世界転生, チート"
        )

        novelDescDao.insertNovel(novel)

        // When: サブタグの一部で検索（LIKE検索）
        val result = novelDescDao.getNovelsByTag("異世界転生").first()

        // Then: 該当する小説を取得
        assertEquals(1, result.size)
        assertEquals("n1111aa", result[0].ncode)
    }

    // ========================================
    // お気に入り機能のテスト
    // ========================================

    @Test
    fun updateFavoriteStatus_togglesFavorite() = runTest {
        // Given: お気に入りではない小説を挿入
        val novel = createTestNovel(ncode = "n1234ab", isFavorite = false)
        novelDescDao.insertNovel(novel)

        // When: お気に入りに設定
        novelDescDao.updateFavoriteStatus("n1234ab", true)

        // Then: お気に入りフラグが更新されていることを確認
        val retrieved = novelDescDao.getNovelByNcode("n1234ab")
        assertTrue(retrieved?.is_favorite == true)
    }

    @Test
    fun getFavoriteNovels_returnsOnlyFavorites() = runTest {
        // Given: お気に入りと非お気に入りの小説を挿入
        val favorite1 = createTestNovel(ncode = "n1111aa", isFavorite = true)
        val favorite2 = createTestNovel(ncode = "n2222bb", isFavorite = true)
        val notFavorite = createTestNovel(ncode = "n3333cc", isFavorite = false)

        novelDescDao.insertNovels(listOf(favorite1, favorite2, notFavorite))

        // When: お気に入り小説のみ取得
        val favorites = novelDescDao.getFavoriteNovels().first()

        // Then: お気に入りのみ取得されることを確認
        assertEquals(2, favorites.size)
        assertTrue(favorites.all { it.is_favorite })
    }

    // ========================================
    // 最近更新された小説の取得テスト
    // ========================================

    @Test
    fun getRecentlyUpdatedNovels_limitsResults() = runTest {
        // Given: 5件の小説を挿入
        val novels = (1..5).map { i ->
            createTestNovel(ncode = "n${i}111aa", title = "小説$i").copy(
                last_update_date = "2025-01-0$i 00:00:00"
            )
        }
        novelDescDao.insertNovels(novels)

        // When: 上位3件のみ取得
        val recentNovels = novelDescDao.getRecentlyUpdatedNovels(3).first()

        // Then: 3件のみ取得されることを確認
        assertEquals(3, recentNovels.size)
        // 新しい順にソートされていることを確認
        assertEquals("小説5", recentNovels[0].title)
        assertEquals("小説4", recentNovels[1].title)
        assertEquals("小説3", recentNovels[2].title)
    }

    // ========================================
    // カウント関数のテスト
    // ========================================

    @Test
    fun getNovelCount_returnsCorrectCount() = runTest {
        // Given: 3件の小説を挿入
        val novels = listOf(
            createTestNovel(ncode = "n1111aa"),
            createTestNovel(ncode = "n2222bb"),
            createTestNovel(ncode = "n3333cc")
        )
        novelDescDao.insertNovels(novels)

        // When: カウントを取得
        val count = novelDescDao.getNovelCount()

        // Then: 正しい件数を返すことを確認
        assertEquals(3, count)
    }

    @Test
    fun getNovelCount_emptyDatabase_returnsZero() = runTest {
        // When: 空のデータベースでカウント
        val count = novelDescDao.getNovelCount()

        // Then: 0を返すことを確認
        assertEquals(0, count)
    }

    // ========================================
    // マルチサイト対応のテスト
    // ========================================

    @Test
    fun insertNovel_kakuyomuNcode_handlesCorrectly() = runTest {
        // Given: カクヨムのPseudo-Ncodeを持つ小説
        val kakuyomuNovel = createTestNovel(
            ncode = "K9zXYt1A2B3",  // カクヨム形式
            title = "カクヨム小説",
            siteType = 2  // カクヨム
        )

        // When: 挿入
        novelDescDao.insertNovel(kakuyomuNovel)

        // Then: 正しく取得できることを確認
        val retrieved = novelDescDao.getNovelByNcode("K9zXYt1A2B3")
        assertNotNull(retrieved)
        assertEquals(2, retrieved?.site_type)
        assertEquals("カクヨム小説", retrieved?.title)
    }

    @Test
    fun insertNovel_r18Novel_handlesCorrectly() = runTest {
        // Given: R18小説
        val r18Novel = createTestNovel(
            ncode = "n1234ab",
            title = "R18小説",
            rating = 1  // R18
        )

        // When: 挿入
        novelDescDao.insertNovel(r18Novel)

        // Then: ratingが正しく保存されていることを確認
        val retrieved = novelDescDao.getNovelByNcode("n1234ab")
        assertEquals(1, retrieved?.rating)
    }
}
