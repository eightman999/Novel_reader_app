/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Repository coordinating DAO operations.
 */
package com.shunlight_library.novel_reader.data.repository

import com.shunlight_library.novel_reader.data.dao.EpisodeDao
import com.shunlight_library.novel_reader.data.dao.LastReadNovelDao
import com.shunlight_library.novel_reader.data.dao.NovelDescDao
import com.shunlight_library.novel_reader.data.dao.URLEntityDao
import com.shunlight_library.novel_reader.data.dao.UpdateQueueDao
import com.shunlight_library.novel_reader.data.dao.ImageCacheDao
import com.shunlight_library.novel_reader.data.dao.EpisodeMappingDao
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.LastReadNovelEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import com.shunlight_library.novel_reader.data.entity.URLEntity
import com.shunlight_library.novel_reader.data.entity.UpdateQueueEntity
import com.shunlight_library.novel_reader.data.entity.ImageCacheEntity
import com.shunlight_library.novel_reader.data.entity.EpisodeMappingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator

class NovelRepository(
    private val episodeDao: EpisodeDao,
    private val novelDescDao: NovelDescDao,
    private val lastReadNovelDao: LastReadNovelDao,
    private val updateQueueDao: UpdateQueueDao,
    private val urlEntityDao: URLEntityDao,
    private val imageCacheDao: ImageCacheDao,
    private val episodeMappingDao: EpisodeMappingDao
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
        // カクヨムのマッピングも削除
        episodeMappingDao.deleteMappingsByNcode(ncode)
    }

    // EpisodeMapping関連メソッド（カクヨム用）
    suspend fun insertEpisodeMapping(mapping: EpisodeMappingEntity) {
        episodeMappingDao.insertMapping(mapping)
    }

    suspend fun insertEpisodeMappings(mappings: List<EpisodeMappingEntity>) {
        episodeMappingDao.insertMappings(mappings)
    }

    suspend fun getKakuyomuEpisodeId(ncode: String, episodeNo: Int): String? {
        return episodeMappingDao.getKakuyomuEpisodeId(ncode, episodeNo)
    }

    suspend fun getEpisodeNo(ncode: String, kakuyomuEpisodeId: String): Int? {
        return episodeMappingDao.getEpisodeNo(ncode, kakuyomuEpisodeId)
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

    suspend fun deleteNovelWithRelations(novel: NovelDescEntity) {
        val stopped = NovelUpdateCoordinator.cancelAndWait(novel.ncode)
        if (!stopped) {
            throw IllegalStateException("進行中の更新を停止できませんでした: ${novel.ncode}")
        }
        withContext(Dispatchers.IO) {
            episodeDao.deleteEpisodesByNcode(novel.ncode)
            lastReadNovelDao.getLastReadByNcode(novel.ncode)?.let {
                lastReadNovelDao.deleteLastRead(it)
            }
            updateQueueDao.deleteUpdateQueueByNcode(novel.ncode)
            urlEntityDao.deleteURLByNcode(novel.ncode)
            novelDescDao.deleteNovel(novel)
        }
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

    // データベースからUpdate_queueの作品数と話数の合計を取得するメソッド
    suspend fun getUpdateCountsWithEpisodes(): Pair<Int, Int> {
        val allQueue = updateQueueDao.getAllUpdateQueueList()

        // 新着・更新を含む総作品数
        val totalWorks = allQueue.size

        // 新着作品の全話数 + 更新された話数の合計
        val totalEpisodes = allQueue.sumOf { queue ->
            if (queue.general_all_no == queue.total_ep) {
                // 新着作品：全話数を加算
                queue.total_ep
            } else {
                // 更新作品：新しく追加された話数のみを加算
                queue.total_ep - queue.general_all_no
            }
        }

        return Pair(totalWorks, totalEpisodes)
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

    suspend fun updateReadingRate(ncode: String, episodeNo: String, readingRate: Float) {
        episodeDao.updateReadingRate(ncode, episodeNo, readingRate)
    }

    // 画像キャッシュ関連メソッド
    suspend fun getImageByHash(hash: String): ImageCacheEntity? {
        return imageCacheDao.getImageByHash(hash)
    }

    suspend fun getImageByUrl(url: String): ImageCacheEntity? {
        return imageCacheDao.getImageByUrl(url)
    }

    suspend fun insertImageCache(image: ImageCacheEntity) {
        imageCacheDao.insertImage(image)
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

    // お気に入り関連メソッド
    suspend fun updateFavoriteStatus(ncode: String, isFavorite: Boolean) {
        withContext(Dispatchers.IO) {
            novelDescDao.updateFavoriteStatus(ncode, isFavorite)
        }
    }

    val favoriteNovels: Flow<List<NovelDescEntity>> = novelDescDao.getFavoriteNovels()

    suspend fun getDatabaseDebugInfo(): String {
        return withContext(Dispatchers.IO) {
            val novels = novelDescDao.getNovelCount()
            val episodes = episodeDao.getEpisodeCount()
            val lastReads = lastReadNovelDao.getLastReadCount()
            val queues = updateQueueDao.getUpdateQueueCount()
            val urls = urlEntityDao.getURLCount()
            "小説数:$novels, エピソード数:$episodes, 読書履歴:$lastReads, 更新キュー:$queues, URL:$urls"
        }
    }

    // ==================== マルチサイト対応メソッド ====================

    /**
     * URLから小説を追加（マルチサイト対応）
     *
     * URLを解析してサイトを自動判定し、適切なアダプターで小説情報を取得してDBに保存する。
     *
     * @param url 小説のURL（小説家になろう または カクヨム）
     * @return 追加された小説情報（追加失敗時はnull）
     */
    suspend fun addNovelByUrl(url: String): NovelDescEntity? {
        return withContext(Dispatchers.IO) {
            try {
                // URLからアダプターと小説IDを取得
                val (adapter, novelId) = com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapterFactory.getAdapterByUrl(url)
                    ?: return@withContext null

                // 小説情報とエピソード一覧を取得
                val (novel, episodes) = when (adapter.getSiteType()) {
                    com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter.SITE_TYPE_SYOSETU -> {
                        // 小説家になろうの場合、URLからR18判定を取得してから小説データを取得
                        val syosetuAdapter = adapter as com.shunlight_library.novel_reader.data.adapter.SyosetuAdapter
                        val (ncode, isR18) = syosetuAdapter.extractNcodeWithR18FromUrl(url)
                        syosetuAdapter.fetchNovelWithEpisodesR18(novelId, isR18)
                    }
                    else -> {
                        // その他のサイトは通常の取得方法
                        adapter.fetchNovelWithEpisodes(novelId)
                    }
                }

                // DBに保存
                insertNovel(novel)
                insertEpisodes(episodes)

                // URL情報を保存（小説家になろうの場合）
                if (adapter.getSiteType() == com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter.SITE_TYPE_SYOSETU) {
                    val syosetuAdapter = adapter as com.shunlight_library.novel_reader.data.adapter.SyosetuAdapter
                    val (ncode, isR18) = syosetuAdapter.extractNcodeWithR18FromUrl(url)
                    if (ncode != null) {
                        getOrCreateURL(ncode, isR18)
                    }
                }

                novel
            } catch (e: Exception) {
                android.util.Log.e("NovelRepository", "Failed to add novel from URL: $url", e)
                null
            }
        }
    }

    /**
     * Ncodeから小説を追加（マルチサイト対応）
     *
     * NcodeまたはPseudo-Ncodeから自動的にサイトを判定し、適切なアダプターで取得・保存する。
     *
     * @param ncode 小説のNcode（小説家になろう）またはPseudo-Ncode（カクヨム: K+Base62）
     * @param isR18 R18作品かどうか（小説家になろうの場合のみ有効）
     * @return 追加された小説情報（追加失敗時はnull）
     */
    suspend fun addNovelByNcode(ncode: String, isR18: Boolean = false): NovelDescEntity? {
        return withContext(Dispatchers.IO) {
            try {
                // Ncodeからアダプターを取得
                val adapter = com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapterFactory.getAdapterByNcode(ncode)

                // 小説情報とエピソード一覧を取得
                val (novel, episodes) = when (adapter.getSiteType()) {
                    com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter.SITE_TYPE_SYOSETU -> {
                        // 小説家になろうの場合、R18判定を渡す
                        val syosetuAdapter = adapter as com.shunlight_library.novel_reader.data.adapter.SyosetuAdapter
                        syosetuAdapter.fetchNovelWithEpisodesR18(ncode, isR18)
                    }
                    else -> {
                        // カクヨムの場合、Pseudo-NcodeからworkIdを抽出
                        val workId = com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator.extractKakuyomuWorkId(ncode)
                        adapter.fetchNovelWithEpisodes(workId)
                    }
                }

                // DBに保存
                insertNovel(novel)
                insertEpisodes(episodes)

                // URL情報を保存（小説家になろうの場合）
                if (adapter.getSiteType() == com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter.SITE_TYPE_SYOSETU) {
                    getOrCreateURL(ncode, isR18)
                }

                novel
            } catch (e: Exception) {
                android.util.Log.e("NovelRepository", "Failed to add novel by ncode: $ncode", e)
                null
            }
        }
    }

    /**
     * 小説の更新チェック（マルチサイト対応）
     *
     * @param ncode 小説のNcode（またはPseudo-Ncode）
     * @return 更新がある場合 true
     */
    suspend fun checkNovelForUpdates(ncode: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // DBから小説情報を取得
                val novel = getNovelByNcode(ncode) ?: return@withContext false

                // アダプターを取得
                val adapter = com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapterFactory.getAdapter(novel.site_type)

                // 更新チェック
                when (adapter.getSiteType()) {
                    com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter.SITE_TYPE_SYOSETU -> {
                        val syosetuAdapter = adapter as com.shunlight_library.novel_reader.data.adapter.SyosetuAdapter
                        val isR18 = novel.rating == 1
                        syosetuAdapter.checkForUpdatesR18(ncode, novel.total_ep, isR18)
                    }
                    com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter.SITE_TYPE_KAKUYOMU -> {
                        val workId = com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator.extractKakuyomuWorkId(ncode)
                        adapter.checkForUpdates(workId, novel.total_ep)
                    }
                    else -> false
                }
            } catch (e: Exception) {
                android.util.Log.e("NovelRepository", "Failed to check updates for ncode: $ncode", e)
                false
            }
        }
    }

    /**
     * 小説のWebページURLを取得（マルチサイト対応）
     *
     * @param ncode 小説のNcode（またはPseudo-Ncode）
     * @return WebページのURL
     */
    suspend fun getNovelWebUrl(ncode: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val novel = getNovelByNcode(ncode) ?: return@withContext null
                val adapter = com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapterFactory.getAdapter(novel.site_type)

                when (adapter.getSiteType()) {
                    com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter.SITE_TYPE_SYOSETU -> {
                        // 小説家になろうの場合、R18判定を使用してURL生成
                        val syosetuAdapter = adapter as com.shunlight_library.novel_reader.data.adapter.SyosetuAdapter
                        val isR18 = novel.rating == 1
                        syosetuAdapter.generateWebUrlR18(ncode, isR18)
                    }
                    com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter.SITE_TYPE_KAKUYOMU -> {
                        // カクヨムの場合、Pseudo-NcodeからworkIdを抽出してURL生成
                        val workId = com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator.extractKakuyomuWorkId(ncode)
                        adapter.generateWebUrl(workId)
                    }
                    else -> null
                }
            } catch (e: Exception) {
                android.util.Log.e("NovelRepository", "Failed to get web URL for ncode: $ncode", e)
                null
            }
        }
    }

    /**
     * エピソードのWebページURLを取得（マルチサイト対応）
     *
     * @param ncode 小説のNcode（またはPseudo-Ncode）
     * @param episodeNo エピソード番号
     * @return WebページのURL
     */
    suspend fun getEpisodeWebUrl(ncode: String, episodeNo: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val novel = getNovelByNcode(ncode) ?: return@withContext null
                val adapter = com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapterFactory.getAdapter(novel.site_type)

                when (adapter.getSiteType()) {
                    com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter.SITE_TYPE_SYOSETU -> {
                        // 小説家になろうの場合、R18判定を使用してURL生成
                        val syosetuAdapter = adapter as com.shunlight_library.novel_reader.data.adapter.SyosetuAdapter
                        val isR18 = novel.rating == 1
                        syosetuAdapter.generateEpisodeUrlR18(ncode, episodeNo, isR18)
                    }
                    com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter.SITE_TYPE_KAKUYOMU -> {
                        val workId = com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator.extractKakuyomuWorkId(ncode)
                        adapter.generateEpisodeUrl(workId, episodeNo)
                    }
                    else -> null
                }
            } catch (e: Exception) {
                android.util.Log.e("NovelRepository", "Failed to get episode URL for ncode: $ncode, episode: $episodeNo", e)
                null
            }
        }
    }

}