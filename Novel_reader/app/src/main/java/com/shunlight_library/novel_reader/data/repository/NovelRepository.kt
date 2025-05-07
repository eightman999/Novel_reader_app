package com.shunlight_library.novel_reader.data.repository

import com.shunlight_library.novel_reader.data.dao.EpisodeDao
import com.shunlight_library.novel_reader.data.dao.LastReadNovelDao
import com.shunlight_library.novel_reader.data.dao.NovelDescDao
import com.shunlight_library.novel_reader.data.dao.URLEntityDao
import com.shunlight_library.novel_reader.data.dao.UpdateQueueDao
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.LastReadNovelEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.entity.URLEntity
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
    private val updateQueueDao: UpdateQueueDao,
    private val urlEntityDao: URLEntityDao
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

    suspend fun updateEpisodeReadStatus(ncode: String, episodeNo: String, isRead: Boolean) {
        episodeDao.updateReadStatus(ncode, episodeNo, isRead)
    }

    /**
     * エピソードのしおり状態を更新
     */
    suspend fun updateEpisodeBookmarkStatus(ncode: String, episodeNo: String, isBookmark: Boolean) {
        episodeDao.updateBookmarkStatus(ncode, episodeNo, isBookmark)
    }

    /**
     * 指定されたエピソードまでを既読に設定
     */
    suspend fun markEpisodesAsReadUpTo(ncode: String, episodeNo: Int) {
        episodeDao.markEpisodesAsReadUpTo(ncode, episodeNo)
    }

    /**
     * しおりが付いたエピソードを取得
     */
    fun getBookmarkedEpisodes(ncode: String): Flow<List<EpisodeEntity>> {
        return episodeDao.getBookmarkedEpisodes(ncode)
    }

    fun getAllURLs(): Flow<List<URLEntity>> {
        return urlEntityDao.getAllURLs()
    }

    suspend fun getURLByNcode(ncode: String): URLEntity? {
        return urlEntityDao.getURLByNcode(ncode)
    }

    suspend fun insertURL(urlEntity: URLEntity) {
        urlEntityDao.insertURL(urlEntity)
    }

    suspend fun insertURLs(urlEntities: List<URLEntity>) {
        urlEntityDao.insertURLs(urlEntities)
    }

    suspend fun updateURL(urlEntity: URLEntity) {
        urlEntityDao.updateURL(urlEntity)
    }

    suspend fun deleteURLByNcode(ncode: String) {
        urlEntityDao.deleteURLByNcode(ncode)
    }
    suspend fun getOrCreateURL(ncode: String, isR18: Boolean = false): URLEntity {
        val existingURL = urlEntityDao.getURLByNcode(ncode)
        if (existingURL != null) {
            return existingURL
        }

        // 新しいURLEntityを作成
        val apiUrl = if (isR18) {
            "https://api.syosetu.com/novel18api/api/?of=t-w-ga-s-ua&ncode=$ncode&gzip=5&json"
        } else {
            "https://api.syosetu.com/novelapi/api/?of=t-w-ga-s-ua&ncode=$ncode&gzip=5&json"
        }
        val webUrl = if (isR18) {
            "https://novel18.syosetu.com/$ncode/"
        } else {
            "https://ncode.syosetu.com/$ncode/"
        }

        val newURLEntity = URLEntity(
            ncode = ncode,
            api_url = apiUrl,
            url = webUrl,
            is_r18 = isR18
        )

        urlEntityDao.insertURL(newURLEntity)
        return newURLEntity
    }

}