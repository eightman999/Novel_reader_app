/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Instrumented tests for NovelRepository.
 */
package com.shunlight_library.novel_reader.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shunlight_library.novel_reader.data.database.NovelDatabase
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.EpisodeMappingEntity
import com.shunlight_library.novel_reader.data.entity.LastReadNovelEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * NovelRepositoryの計装テスト
 *
 * Repositoryは複数のDAOを統合する層のため、
 * 統合的な動作を確認する。特にカクヨムマッピング機能などの
 * 複雑なロジックを重点的にテストする。
 */
@RunWith(AndroidJUnit4::class)
class NovelRepositoryTest {

    private lateinit var database: NovelDatabase
    private lateinit var repository: NovelRepository

    @Before
    fun createDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NovelDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        repository = NovelRepository(
            episodeDao = database.episodeDao(),
            novelDescDao = database.novelDescDao(),
            lastReadNovelDao = database.lastReadNovelDao(),
            updateQueueDao = database.updateQueueDao(),
            urlEntityDao = database.urlEntityDao(),
            imageCacheDao = database.imageCacheDao(),
            episodeMappingDao = database.episodeMappingDao()
        )
    }

    @After
    fun closeDb() {
        database.close()
    }

    // ========================================
    // ヘルパー関数
    // ========================================

    private fun createTestNovel(
        ncode: String = "n1234ab",
        title: String = "テスト小説",
        siteType: Int = 1
    ): NovelDescEntity {
        return NovelDescEntity(
            ncode = ncode,
            title = title,
            author = "テスト作者",
            Synopsis = "テストあらすじ",
            main_tag = "ファンタジー",
            sub_tag = "異世界転生",
            rating = 2,
            last_update_date = "2025-01-01 12:00:00",
            total_ep = 100,
            general_all_no = 50,
            userid = "12345",
            noveltype = 1,
            length = 100000,
            updated_at = "2025-01-01 12:00:00",
            is_favorite = 0,
            site_type = siteType,
            registered_at = "2025-01-01 10:00:00"
        )
    }

    private fun createTestEpisode(
        ncode: String = "n1234ab",
        episodeNo: String = "1",
        title: String = "第${episodeNo}話"
    ): EpisodeEntity {
        return EpisodeEntity(
            ncode = ncode,
            episode_no = episodeNo,
            e_title = title,
            body = "テスト本文",
            update_time = "2025-01-01 12:00:00",
            is_read = 0,
            is_bookmark = 0,
            reading_rate = 0f
        )
    }

    // ========================================
    // Novel関連の基本テスト
    // ========================================

    @Test
    fun insertNovel_and_getNovelByNcode_returnsNovel() = runTest {
        // Given: 小説を作成
        val novel = createTestNovel(ncode = "n1234ab")

        // When: Repositoryを通じて挿入
        repository.insertNovel(novel)

        // Then: 取得できることを確認
        val retrieved = repository.getNovelByNcode("n1234ab")
        assertNotNull(retrieved)
        assertEquals("n1234ab", retrieved?.ncode)
        assertEquals("テスト小説", retrieved?.title)
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
        repository.insertNovels(novels)

        // Then: 全て取得できることを確認
        val all = repository.allNovels.first()
        assertEquals(3, all.size)
    }

    @Test
    fun allNovels_emitsFlow() = runTest {
        // Given: 初期状態は空
        val initialNovels = repository.allNovels.first()
        assertEquals(0, initialNovels.size)

        // When: 小説を挿入
        repository.insertNovel(createTestNovel(ncode = "n1234ab"))

        // Then: Flowが新しいデータを発火
        val updatedNovels = repository.allNovels.first()
        assertEquals(1, updatedNovels.size)
    }

    // ========================================
    // Episode関連の基本テスト
    // ========================================

    @Test
    fun insertEpisode_and_getEpisode_returnsEpisode() = runTest {
        // Given: エピソードを作成
        val episode = createTestEpisode(ncode = "n1234ab", episodeNo = "1")

        // When: Repositoryを通じて挿入
        repository.insertEpisode(episode)

        // Then: 取得できることを確認
        val retrieved = repository.getEpisode("n1234ab", "1")
        assertNotNull(retrieved)
        assertEquals("n1234ab", retrieved?.ncode)
        assertEquals("1", retrieved?.episode_no)
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
        repository.insertEpisodes(episodes)

        // Then: 全て取得できることを確認
        val all = repository.getEpisodesByNcode("n1234ab").first()
        assertEquals(3, all.size)
    }

    @Test
    fun deleteEpisodesByNcode_removesAllEpisodes() = runTest {
        // Given: エピソードを挿入
        val episodes = listOf(
            createTestEpisode(ncode = "n1234ab", episodeNo = "1"),
            createTestEpisode(ncode = "n1234ab", episodeNo = "2")
        )
        repository.insertEpisodes(episodes)

        // When: Ncodeでエピソードを削除
        repository.deleteEpisodesByNcode("n1234ab")

        // Then: エピソードが削除されていることを確認
        val remaining = repository.getEpisodesByNcode("n1234ab").first()
        assertEquals(0, remaining.size)
    }

    // ========================================
    // カクヨムエピソードマッピング機能のテスト
    // ========================================

    @Test
    fun insertKakuyomuEpisodesWithMappings_savesEpisodesAndMappings() = runTest {
        // Given: カクヨム小説とエピソード、マッピングを作成
        val kakuyomuNcode = "K9zXYt1A2B3"
        val episodes = listOf(
            createTestEpisode(ncode = kakuyomuNcode, episodeNo = "1", title = "第1話"),
            createTestEpisode(ncode = kakuyomuNcode, episodeNo = "2", title = "第2話"),
            createTestEpisode(ncode = kakuyomuNcode, episodeNo = "3", title = "第3話")
        )

        val mappings = mapOf(
            1 to "1177354054887277844",  // エピソード1 → カクヨムID
            2 to "1177354054887854131",  // エピソード2 → カクヨムID
            3 to "1177354054888123456"   // エピソード3 → カクヨムID
        )

        // When: エピソードとマッピングを一括保存
        repository.insertKakuyomuEpisodesWithMappings(episodes, mappings)

        // Then: エピソードが保存されていることを確認
        val savedEpisodes = repository.getEpisodesByNcode(kakuyomuNcode).first()
        assertEquals(3, savedEpisodes.size)

        // Then: マッピングが保存されていることを確認
        val kakuyomuId1 = repository.getKakuyomuEpisodeId(kakuyomuNcode, 1)
        val kakuyomuId2 = repository.getKakuyomuEpisodeId(kakuyomuNcode, 2)
        val kakuyomuId3 = repository.getKakuyomuEpisodeId(kakuyomuNcode, 3)

        assertEquals("1177354054887277844", kakuyomuId1)
        assertEquals("1177354054887854131", kakuyomuId2)
        assertEquals("1177354054888123456", kakuyomuId3)
    }

    @Test
    fun insertKakuyomuEpisodesWithMappings_emptyList_doesNothing() = runTest {
        // Given: 空のエピソードリスト
        val episodes = emptyList<EpisodeEntity>()
        val mappings = emptyMap<Int, String>()

        // When: 空のリストで保存
        repository.insertKakuyomuEpisodesWithMappings(episodes, mappings)

        // Then: エラーが発生しないことを確認（ログ出力のみ）
        // このテストは正常完了すればOK
    }

    @Test
    fun getKakuyomuEpisodeId_returnsCorrectId() = runTest {
        // Given: マッピングを挿入
        val kakuyomuNcode = "K9zXYt1A2B3"
        val mapping = EpisodeMappingEntity(
            ncode = kakuyomuNcode,
            episode_no = 5,
            kakuyomu_episode_id = "1177354054887277844"
        )
        repository.insertEpisodeMapping(mapping)

        // When: カクヨムエピソードIDを取得
        val kakuyomuId = repository.getKakuyomuEpisodeId(kakuyomuNcode, 5)

        // Then: 正しいIDが返ることを確認
        assertEquals("1177354054887277844", kakuyomuId)
    }

    @Test
    fun getEpisodeNo_returnsCorrectEpisodeNo() = runTest {
        // Given: マッピングを挿入
        val kakuyomuNcode = "K9zXYt1A2B3"
        val mapping = EpisodeMappingEntity(
            ncode = kakuyomuNcode,
            episode_no = 10,
            kakuyomu_episode_id = "1177354054887277844"
        )
        repository.insertEpisodeMapping(mapping)

        // When: エピソード番号を取得
        val episodeNo = repository.getEpisodeNo(kakuyomuNcode, "1177354054887277844")

        // Then: 正しいエピソード番号が返ることを確認
        assertEquals(10, episodeNo)
    }

    @Test
    fun deleteEpisodesByNcode_alsoDeletesMappings() = runTest {
        // Given: カクヨムエピソードとマッピングを挿入
        val kakuyomuNcode = "K9zXYt1A2B3"
        val episodes = listOf(
            createTestEpisode(ncode = kakuyomuNcode, episodeNo = "1")
        )
        val mappings = mapOf(1 to "1177354054887277844")

        repository.insertKakuyomuEpisodesWithMappings(episodes, mappings)

        // When: エピソードを削除
        repository.deleteEpisodesByNcode(kakuyomuNcode)

        // Then: マッピングも削除されていることを確認
        val kakuyomuId = repository.getKakuyomuEpisodeId(kakuyomuNcode, 1)
        assertNull(kakuyomuId)
    }

    // ========================================
    // 読書履歴機能のテスト
    // ========================================

    @Test
    fun updateLastRead_savesReadingHistory() = runTest {
        // Given: Ncodeとエピソード番号
        val ncode = "n1234ab"
        val episodeNo = 5

        // When: 読書履歴を更新
        repository.updateLastRead(ncode, episodeNo)

        // Then: 読書履歴が保存されていることを確認
        val lastRead = repository.getLastReadByNcode(ncode)
        assertNotNull(lastRead)
        assertEquals(ncode, lastRead?.ncode)
        assertEquals(5, lastRead?.episode_no)
    }

    @Test
    fun updateLastRead_updatesExistingHistory() = runTest {
        // Given: 既存の読書履歴
        repository.updateLastRead("n1234ab", 3)

        // When: 新しいエピソードで更新
        repository.updateLastRead("n1234ab", 7)

        // Then: 読書履歴が更新されていることを確認
        val lastRead = repository.getLastReadByNcode("n1234ab")
        assertEquals(7, lastRead?.episode_no)
    }

    @Test
    fun getMostRecentlyReadNovel_returnsLatestRead() = runTest {
        // Given: 複数の読書履歴を作成（時間差で挿入）
        repository.updateLastRead("n1111aa", 1)
        Thread.sleep(10)  // 時間差を作る
        repository.updateLastRead("n2222bb", 2)
        Thread.sleep(10)
        repository.updateLastRead("n3333cc", 3)

        // When: 最後に読んだ小説を取得
        val mostRecent = repository.getMostRecentlyReadNovel()

        // Then: 最後に読んだ小説が返ることを確認
        assertNotNull(mostRecent)
        assertEquals("n3333cc", mostRecent?.ncode)
        assertEquals(3, mostRecent?.episode_no)
    }

    @Test
    fun deleteLastRead_removesHistory() = runTest {
        // Given: 読書履歴を作成
        repository.updateLastRead("n1234ab", 5)

        // When: 読書履歴を削除
        repository.deleteLastRead("n1234ab")

        // Then: 読書履歴が削除されていることを確認
        val lastRead = repository.getLastReadByNcode("n1234ab")
        assertNull(lastRead)
    }

    @Test
    fun allLastReadNovels_emitsFlow() = runTest {
        // Given: 初期状態は空
        val initialList = repository.allLastReadNovels.first()
        assertEquals(0, initialList.size)

        // When: 読書履歴を追加
        repository.updateLastRead("n1234ab", 1)

        // Then: Flowが新しいデータを発火
        val updatedList = repository.allLastReadNovels.first()
        assertEquals(1, updatedList.size)
    }

    // ========================================
    // 統合機能のテスト
    // ========================================

    @Test
    fun getRecentlyUpdatedNovels_returnsLimitedResults() = runTest {
        // Given: 複数の小説を挿入
        val novels = (1..5).map { i ->
            createTestNovel(ncode = "n${i}111aa", title = "小説$i").copy(
                last_update_date = "2025-01-0$i 00:00:00"
            )
        }
        repository.insertNovels(novels)

        // When: 最近更新された小説を取得（上位3件）
        val recentNovels = repository.getRecentlyUpdatedNovels(3).first()

        // Then: 3件のみ取得されることを確認
        assertEquals(3, recentNovels.size)
        // 新しい順にソート
        assertEquals("小説5", recentNovels[0].title)
        assertEquals("小説4", recentNovels[1].title)
        assertEquals("小説3", recentNovels[2].title)
    }

    @Test
    fun getNovelsByTag_returnsMatchingNovels() = runTest {
        // Given: 異なるタグの小説を挿入
        val fantasy = createTestNovel(ncode = "n1111aa").copy(main_tag = "ファンタジー")
        val scifi = createTestNovel(ncode = "n2222bb").copy(main_tag = "SF")
        repository.insertNovels(listOf(fantasy, scifi))

        // When: タグで検索
        val results = repository.getNovelsByTag("ファンタジー").first()

        // Then: 該当する小説のみ取得
        assertEquals(1, results.size)
        assertEquals("n1111aa", results[0].ncode)
    }

    // ========================================
    // エッジケースのテスト
    // ========================================

    @Test
    fun insertKakuyomuEpisodesWithMappings_missingMappings_savesOnlyEpisodes() = runTest {
        // Given: マッピング情報が一部欠けている
        val kakuyomuNcode = "K9zXYt1A2B3"
        val episodes = listOf(
            createTestEpisode(ncode = kakuyomuNcode, episodeNo = "1"),
            createTestEpisode(ncode = kakuyomuNcode, episodeNo = "2")
        )
        val mappings = mapOf(1 to "1177354054887277844")  // エピソード2のマッピングなし

        // When: 保存
        repository.insertKakuyomuEpisodesWithMappings(episodes, mappings)

        // Then: エピソードは保存されている
        val savedEpisodes = repository.getEpisodesByNcode(kakuyomuNcode).first()
        assertEquals(2, savedEpisodes.size)

        // Then: マッピングは存在するもののみ保存
        val kakuyomuId1 = repository.getKakuyomuEpisodeId(kakuyomuNcode, 1)
        val kakuyomuId2 = repository.getKakuyomuEpisodeId(kakuyomuNcode, 2)

        assertEquals("1177354054887277844", kakuyomuId1)
        assertNull(kakuyomuId2)  // マッピングなし
    }

    @Test
    fun updateNovel_modifiesExistingNovel() = runTest {
        // Given: 小説を挿入
        val novel = createTestNovel(ncode = "n1234ab", title = "旧タイトル")
        repository.insertNovel(novel)

        // When: タイトルを変更して更新
        val updatedNovel = novel.copy(title = "新タイトル")
        repository.updateNovel(updatedNovel)

        // Then: 更新されていることを確認
        val retrieved = repository.getNovelByNcode("n1234ab")
        assertEquals("新タイトル", retrieved?.title)
    }
}
