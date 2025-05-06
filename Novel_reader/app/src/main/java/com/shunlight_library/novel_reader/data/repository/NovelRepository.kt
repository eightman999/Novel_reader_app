package com.shunlight_library.novel_reader.data.repository

import com.shunlight_library.novel_reader.data.dao.EpisodeDao
import com.shunlight_library.novel_reader.data.dao.LastReadNovelDao
import com.shunlight_library.novel_reader.data.dao.NovelDescDao
import com.shunlight_library.novel_reader.data.dao.UpdateQueueDao
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.LastReadNovelEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.entity.UpdateQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NovelRepository(
    private val episodeDao: EpisodeDao,
    private val novelDescDao: NovelDescDao,
    private val lastReadNovelDao: LastReadNovelDao,
    private val updateQueueDao: UpdateQueueDao
) {
    // Novel Description関連メソッド
    val allNovels: Flow<List<NovelDescEntity>> = novelDescDao.getAllNovels()

    suspend fun getNovelByNcode(ncode: String): NovelDescEntity? {
        return novelDescDao.getNovelByNcode(ncode)
    }

    fun getNovelsByTag(tag: String): Flow<List<NovelDescEntity>> {
        return novelDescDao.getNovelsByTag(tag)
    }

    suspend fun insertNovel(novel: NovelDescEntity) {
        novelDescDao.insertNovel(novel)
    }

    suspend fun insertNovels(novels: List<NovelDescEntity>) {
        novelDescDao.insertNovels(novels)
    }

    fun getRecentlyUpdatedNovels(limit: Int): Flow<List<NovelDescEntity>> {
        return novelDescDao.getRecentlyUpdatedNovels(limit)
    }

    // Episode関連メソッド
    fun getEpisodesByNcode(ncode: String): Flow<List<EpisodeEntity>> {
        return episodeDao.getEpisodesByNcode(ncode)
    }

    suspend fun getEpisode(ncode: String, episodeNo: String): EpisodeEntity? {
        return episodeDao.getEpisode(ncode, episodeNo)
    }

    suspend fun insertEpisode(episode: EpisodeEntity) {
        episodeDao.insertEpisode(episode)
    }

    suspend fun insertEpisodes(episodes: List<EpisodeEntity>) {
        episodeDao.insertEpisodes(episodes)
    }

    suspend fun deleteEpisodesByNcode(ncode: String) {
        episodeDao.deleteEpisodesByNcode(ncode)
    }

    // LastReadNovel関連メソッド
    val allLastReadNovels: Flow<List<LastReadNovelEntity>> = lastReadNovelDao.getAllLastReadNovels()

    suspend fun getLastReadByNcode(ncode: String): LastReadNovelEntity? {
        return lastReadNovelDao.getLastReadByNcode(ncode)
    }

    suspend fun getMostRecentlyReadNovel(): LastReadNovelEntity? {
        return lastReadNovelDao.getMostRecentlyReadNovel()
    }

    suspend fun updateLastRead(ncode: String, episodeNo: Int) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val lastRead = LastReadNovelEntity(
            ncode = ncode,
            date = currentDate,
            episode_no = episodeNo
        )
        lastReadNovelDao.insertLastRead(lastRead)
    }
    suspend fun updateNovel(novel: NovelDescEntity) {
        novelDescDao.updateNovel(novel)
    }

    suspend fun deleteLastRead(ncode: String) {
        lastReadNovelDao.getLastReadByNcode(ncode)?.let {
            lastReadNovelDao.deleteLastRead(it)
        }
    }
    val allUpdateQueue: Flow<List<UpdateQueueEntity>> = updateQueueDao.getAllUpdateQueue()

    suspend fun getUpdateQueueByNcode(ncode: String): UpdateQueueEntity? {
        return updateQueueDao.getUpdateQueueByNcode(ncode)
    }

    suspend fun insertUpdateQueue(updateQueue: UpdateQueueEntity) {
        updateQueueDao.insertUpdateQueue(updateQueue)
    }

    suspend fun insertUpdateQueues(updateQueues: List<UpdateQueueEntity>) {
        updateQueueDao.insertUpdateQueues(updateQueues)
    }

    suspend fun deleteUpdateQueueByNcode(ncode: String) {
        updateQueueDao.deleteUpdateQueueByNcode(ncode)
    }
    // NovelRepository.kt に追加

    // 更新チェック対象の小説を取得するメソッド
    suspend fun getNovelsForUpdate(): List<NovelDescEntity> {
        return withContext(Dispatchers.IO) {
            // rating = 1, 2, 3, または null の小説を取得
            novelDescDao.getNovelsForUpdate()
        }
    }

    // データベースからUpdate_queueの総数と更新がある小説の数を取得するメソッド
    suspend fun getUpdateCounts(): Pair<Int, Int> {
        val allQueue = updateQueueDao.getAllUpdateQueueList()

        // 新規追加と更新で分類
        val newCount = allQueue.count { it.general_all_no == it.total_ep }
        val updateCount = allQueue.size - newCount

        return Pair(newCount, updateCount)
    }
    suspend fun getAllUpdateQueue(): List<UpdateQueueEntity> {
        return withContext(Dispatchers.IO) {
            updateQueueDao.getAllUpdateQueueList()
        }
    }
}