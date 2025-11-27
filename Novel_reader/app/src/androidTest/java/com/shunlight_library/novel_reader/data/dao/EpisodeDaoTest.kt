/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Instrumented tests for EpisodeDao.
 */
package com.shunlight_library.novel_reader.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shunlight_library.novel_reader.data.database.NovelDatabase
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * EpisodeDaoの計装テスト
 *
 * インメモリデータベースを使用してエピソード関連のCRUD操作を検証する。
 * 読書進捗、しおり、既読状態などの機能をテストする。
 */
@RunWith(AndroidJUnit4::class)
class EpisodeDaoTest {

    private lateinit var database: NovelDatabase
    private lateinit var episodeDao: EpisodeDao

    @Before
    fun createDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NovelDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        episodeDao = database.episodeDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    // ========================================
    // ヘルパー関数
    // ========================================

    /**
     * テスト用のEpisodeEntityを作成
     */
    private fun createTestEpisode(
        ncode: String = "n1234ab",
        episodeNo: String = "1",
        title: String = "第${episodeNo}話",
        body: String = "これはテスト用の本文です。",
        isRead: Boolean = false,
        isBookmark: Boolean = false,
        readingRate: Float = 0f
    ): EpisodeEntity {
        return EpisodeEntity(
            ncode = ncode,
            episode_no = episodeNo,
            e_title = title,
            body = body,
            update_time = "2025-01-01 12:00:00",
            is_read = isRead,
            is_bookmark = isBookmark,
            reading_rate = readingRate
        )
    }

    // ========================================
    // 挿入と取得のテスト
    // ========================================

    @Test
    fun insertEpisode_and_getEpisode_returnsCorrectEpisode() = runTest {
        // Given: テスト用のエピソードを作成
        val episode = createTestEpisode(ncode = "n1234ab", episodeNo = "1")

        // When: エピソードを挿入
        episodeDao.insertEpisode(episode)

        // Then: 正しく取得できることを確認
        val retrieved = episodeDao.getEpisode("n1234ab", "1")
        assertNotNull(retrieved)
        assertEquals("n1234ab", retrieved?.ncode)
        assertEquals("1", retrieved?.episode_no)
        assertEquals("第1話", retrieved?.e_title)
    }

    @Test
    fun insertEpisode_duplicateEpisode_replacesExisting() = runTest {
        // Given: 同じncode+episode_noのエピソードを2つ作成
        val episode1 = createTestEpisode(ncode = "n1234ab", episodeNo = "1", title = "旧タイトル")
        val episode2 = createTestEpisode(ncode = "n1234ab", episodeNo = "1", title = "新タイトル")

        // When: 同じ複合キーで2回挿入
        episodeDao.insertEpisode(episode1)
        episodeDao.insertEpisode(episode2)

        // Then: 新しいデータで上書きされていることを確認
        val retrieved = episodeDao.getEpisode("n1234ab", "1")
        assertEquals("新タイトル", retrieved?.e_title)
    }

    @Test
    fun insertEpisodes_multipleEpisodes_allInserted() = runTest {
        // Given: 複数のエピソードを作成
        val episodes = listOf(
            createTestEpisode(ncode = "n1234ab", episodeNo = "1"),
            createTestEpisode(ncode = "n1234ab", episodeNo = "2"),
            createTestEpisode(ncode = "n1234ab", episodeNo = "3")
        )

        // When: 一括挿入
        episodeDao.insertEpisodes(episodes)

        // Then: すべて取得できることを確認
        val all = episodeDao.getEpisodesByNcode("n1234ab").first()
        assertEquals(3, all.size)
    }

    @Test
    fun getEpisode_nonexistentEpisode_returnsNull() = runTest {
        // When: 存在しないエピソードを取得
        val retrieved = episodeDao.getEpisode("nonexistent", "999")

        // Then: nullが返ることを確認
        assertNull(retrieved)
    }

    // ========================================
    // エピソード一覧取得のテスト
    // ========================================

    @Test
    fun getEpisodesByNcode_orderedByEpisodeNo() = runTest {
        // Given: 順序が異なるエピソードを挿入
        val episodes = listOf(
            createTestEpisode(ncode = "n1234ab", episodeNo = "3", title = "第3話"),
            createTestEpisode(ncode = "n1234ab", episodeNo = "1", title = "第1話"),
            createTestEpisode(ncode = "n1234ab", episodeNo = "2", title = "第2話")
        )
        episodeDao.insertEpisodes(episodes)

        // When: エピソード一覧を取得
        val retrieved = episodeDao.getEpisodesByNcode("n1234ab").first()

        // Then: episode_no順にソートされていることを確認
        assertEquals(3, retrieved.size)
        assertEquals("1", retrieved[0].episode_no)
        assertEquals("2", retrieved[1].episode_no)
        assertEquals("3", retrieved[2].episode_no)
    }

    @Test
    fun getEpisodesByNcode_differentNcodes_returnOnlyMatchingNcode() = runTest {
        // Given: 異なるncodeのエピソードを挿入
        episodeDao.insertEpisode(createTestEpisode(ncode = "n1111aa", episodeNo = "1"))
        episodeDao.insertEpisode(createTestEpisode(ncode = "n2222bb", episodeNo = "1"))
        episodeDao.insertEpisode(createTestEpisode(ncode = "n1111aa", episodeNo = "2"))

        // When: 特定のncodeで取得
        val episodes = episodeDao.getEpisodesByNcode("n1111aa").first()

        // Then: そのncodeのエピソードのみ取得
        assertEquals(2, episodes.size)
        assertTrue(episodes.all { it.ncode == "n1111aa" })
    }

    // ========================================
    // 更新と削除のテスト
    // ========================================

    @Test
    fun updateEpisode_modifiesExistingEpisode() = runTest {
        // Given: エピソードを挿入
        val episode = createTestEpisode(ncode = "n1234ab", episodeNo = "1", title = "旧タイトル")
        episodeDao.insertEpisode(episode)

        // When: タイトルを変更して更新
        val updatedEpisode = episode.copy(e_title = "新タイトル")
        episodeDao.updateEpisode(updatedEpisode)

        // Then: 更新されていることを確認
        val retrieved = episodeDao.getEpisode("n1234ab", "1")
        assertEquals("新タイトル", retrieved?.e_title)
    }

    @Test
    fun deleteEpisode_removesEpisodeFromDatabase() = runTest {
        // Given: エピソードを挿入
        val episode = createTestEpisode(ncode = "n1234ab", episodeNo = "1")
        episodeDao.insertEpisode(episode)

        // When: 削除
        episodeDao.deleteEpisode(episode)

        // Then: 取得できないことを確認
        val retrieved = episodeDao.getEpisode("n1234ab", "1")
        assertNull(retrieved)
    }

    @Test
    fun deleteEpisodesByNcode_removesAllEpisodesForNcode() = runTest {
        // Given: 複数のエピソードを挿入
        episodeDao.insertEpisode(createTestEpisode(ncode = "n1234ab", episodeNo = "1"))
        episodeDao.insertEpisode(createTestEpisode(ncode = "n1234ab", episodeNo = "2"))
        episodeDao.insertEpisode(createTestEpisode(ncode = "n5678cd", episodeNo = "1"))

        // When: 特定のncodeのエピソードをすべて削除
        episodeDao.deleteEpisodesByNcode("n1234ab")

        // Then: そのncodeのエピソードが削除されていることを確認
        val remainingN1234 = episodeDao.getEpisodesByNcode("n1234ab").first()
        val remainingN5678 = episodeDao.getEpisodesByNcode("n5678cd").first()

        assertEquals(0, remainingN1234.size)
        assertEquals(1, remainingN5678.size)
    }

    // ========================================
    // 既読状態の管理テスト
    // ========================================

    @Test
    fun updateReadStatus_togglesReadFlag() = runTest {
        // Given: 未読のエピソードを挿入
        val episode = createTestEpisode(ncode = "n1234ab", episodeNo = "1", isRead = false)
        episodeDao.insertEpisode(episode)

        // When: 既読に設定
        episodeDao.updateReadStatus("n1234ab", "1", true)

        // Then: 既読フラグが更新されていることを確認
        val retrieved = episodeDao.getEpisode("n1234ab", "1")
        assertTrue(retrieved?.is_read == true)
    }

    @Test
    fun markEpisodesAsReadUpTo_marksMultipleEpisodes() = runTest {
        // Given: 5話分のエピソードを挿入（すべて未読）
        val episodes = (1..5).map { i ->
            createTestEpisode(ncode = "n1234ab", episodeNo = i.toString(), isRead = false)
        }
        episodeDao.insertEpisodes(episodes)

        // When: 第3話まで既読に設定
        episodeDao.markEpisodesAsReadUpTo("n1234ab", 3)

        // Then: 第1~3話が既読、第4~5話が未読
        val allEpisodes = episodeDao.getEpisodesByNcode("n1234ab").first()
        assertTrue(allEpisodes[0].is_read)  // 第1話
        assertTrue(allEpisodes[1].is_read)  // 第2話
        assertTrue(allEpisodes[2].is_read)  // 第3話
        assertFalse(allEpisodes[3].is_read) // 第4話
        assertFalse(allEpisodes[4].is_read) // 第5話
    }

    @Test
    fun getReadEpisodes_returnsOnlyReadEpisodes() = runTest {
        // Given: 既読と未読のエピソードを挿入
        episodeDao.insertEpisode(createTestEpisode(ncode = "n1234ab", episodeNo = "1", isRead = true))
        episodeDao.insertEpisode(createTestEpisode(ncode = "n1234ab", episodeNo = "2", isRead = false))
        episodeDao.insertEpisode(createTestEpisode(ncode = "n1234ab", episodeNo = "3", isRead = true))

        // When: 既読エピソードのみ取得
        val readEpisodes = episodeDao.getReadEpisodes("n1234ab").first()

        // Then: 既読エピソードのみ取得されることを確認
        assertEquals(2, readEpisodes.size)
        assertTrue(readEpisodes.all { it.is_read })
        assertEquals("1", readEpisodes[0].episode_no)
        assertEquals("3", readEpisodes[1].episode_no)
    }

    // ========================================
    // しおり機能のテスト
    // ========================================

    @Test
    fun updateBookmarkStatus_togglesBookmarkFlag() = runTest {
        // Given: しおりなしのエピソードを挿入
        val episode = createTestEpisode(ncode = "n1234ab", episodeNo = "1", isBookmark = false)
        episodeDao.insertEpisode(episode)

        // When: しおりを設定
        episodeDao.updateBookmarkStatus("n1234ab", "1", true)

        // Then: しおりフラグが更新されていることを確認
        val retrieved = episodeDao.getEpisode("n1234ab", "1")
        assertTrue(retrieved?.is_bookmark == true)
    }

    @Test
    fun getBookmarkedEpisodes_returnsOnlyBookmarkedEpisodes() = runTest {
        // Given: しおりありとなしのエピソードを挿入
        episodeDao.insertEpisode(createTestEpisode(ncode = "n1234ab", episodeNo = "1", isBookmark = true))
        episodeDao.insertEpisode(createTestEpisode(ncode = "n1234ab", episodeNo = "2", isBookmark = false))
        episodeDao.insertEpisode(createTestEpisode(ncode = "n1234ab", episodeNo = "3", isBookmark = true))

        // When: しおり付きエピソードのみ取得
        val bookmarked = episodeDao.getBookmarkedEpisodes("n1234ab").first()

        // Then: しおり付きエピソードのみ取得されることを確認
        assertEquals(2, bookmarked.size)
        assertTrue(bookmarked.all { it.is_bookmark })
    }

    // ========================================
    // 読書進捗のテスト
    // ========================================

    @Test
    fun updateReadingRate_updatesProgressCorrectly() = runTest {
        // Given: 読書進捗0%のエピソードを挿入
        val episode = createTestEpisode(ncode = "n1234ab", episodeNo = "1", readingRate = 0f)
        episodeDao.insertEpisode(episode)

        // When: 読書進捗を50%に更新
        episodeDao.updateReadingRate("n1234ab", "1", 0.5f)

        // Then: 読書進捗が更新されていることを確認
        val retrieved = episodeDao.getEpisode("n1234ab", "1")
        assertEquals(0.5f, retrieved?.reading_rate)
    }

    @Test
    fun updateReadingRate_completed_setsToOne() = runTest {
        // Given: エピソードを挿入
        val episode = createTestEpisode(ncode = "n1234ab", episodeNo = "1", readingRate = 0f)
        episodeDao.insertEpisode(episode)

        // When: 読了（100%）に設定
        episodeDao.updateReadingRate("n1234ab", "1", 1.0f)

        // Then: 読書進捗が1.0になっていることを確認
        val retrieved = episodeDao.getEpisode("n1234ab", "1")
        assertEquals(1.0f, retrieved?.reading_rate)
    }

    // ========================================
    // カウント関数のテスト
    // ========================================

    @Test
    fun getEpisodeCount_returnsCorrectCount() = runTest {
        // Given: 複数のエピソードを挿入
        episodeDao.insertEpisode(createTestEpisode(ncode = "n1234ab", episodeNo = "1"))
        episodeDao.insertEpisode(createTestEpisode(ncode = "n1234ab", episodeNo = "2"))
        episodeDao.insertEpisode(createTestEpisode(ncode = "n5678cd", episodeNo = "1"))

        // When: カウントを取得
        val count = episodeDao.getEpisodeCount()

        // Then: 正しい件数を返すことを確認
        assertEquals(3, count)
    }

    @Test
    fun getEpisodeCount_emptyDatabase_returnsZero() = runTest {
        // When: 空のデータベースでカウント
        val count = episodeDao.getEpisodeCount()

        // Then: 0を返すことを確認
        assertEquals(0, count)
    }

    // ========================================
    // Flow発火のテスト
    // ========================================

    @Test
    fun getEpisodesByNcode_emitsFlowOnDataChange() = runTest {
        // Given: 初期状態は空
        val initialEpisodes = episodeDao.getEpisodesByNcode("n1234ab").first()
        assertEquals(0, initialEpisodes.size)

        // When: エピソードを挿入
        episodeDao.insertEpisode(createTestEpisode(ncode = "n1234ab", episodeNo = "1"))

        // Then: Flowが新しいデータを発火
        val updatedEpisodes = episodeDao.getEpisodesByNcode("n1234ab").first()
        assertEquals(1, updatedEpisodes.size)
    }

    // ========================================
    // エッジケース
    // ========================================

    @Test
    fun insertEpisode_longBody_handlesCorrectly() = runTest {
        // Given: 非常に長い本文を持つエピソード
        val longBody = "これは非常に長い本文です。".repeat(1000)
        val episode = createTestEpisode(ncode = "n1234ab", episodeNo = "1", body = longBody)

        // When: 挿入
        episodeDao.insertEpisode(episode)

        // Then: 正しく保存されていることを確認
        val retrieved = episodeDao.getEpisode("n1234ab", "1")
        assertEquals(longBody, retrieved?.body)
    }

    @Test
    fun insertEpisode_specialCharactersInTitle_handlesCorrectly() = runTest {
        // Given: 特殊文字を含むタイトル
        val specialTitle = "第1話「テスト」～異世界転生～ <前編>"
        val episode = createTestEpisode(ncode = "n1234ab", episodeNo = "1", title = specialTitle)

        // When: 挿入
        episodeDao.insertEpisode(episode)

        // Then: 特殊文字が正しく保存されていることを確認
        val retrieved = episodeDao.getEpisode("n1234ab", "1")
        assertEquals(specialTitle, retrieved?.e_title)
    }
}
