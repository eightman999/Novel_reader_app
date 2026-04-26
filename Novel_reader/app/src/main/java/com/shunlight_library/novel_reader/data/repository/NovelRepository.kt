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
import com.shunlight_library.novel_reader.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator
import com.shunlight_library.novel_reader.data.ProcessingState
import com.shunlight_library.novel_reader.data.ProcessingStatusType

class NovelRepository(
    private val episodeDao: EpisodeDao,
    private val novelDescDao: NovelDescDao,
    private val lastReadNovelDao: LastReadNovelDao,
    private val updateQueueDao: UpdateQueueDao,
    private val urlEntityDao: URLEntityDao,
    private val imageCacheDao: ImageCacheDao,
    private val episodeMappingDao: EpisodeMappingDao,
    private val registrationQueueDao: RegistrationQueueDao,
    private val tempEpisodeDao: TempEpisodeDao
) {
    // 処理状態管理（インジケーターランプ用）
    private val _processingStates = MutableStateFlow<List<ProcessingState>>(emptyList())
    val processingStates: StateFlow<List<ProcessingState>> = _processingStates.asStateFlow()

    /**
     * 処理状態を追加
     */
    fun addProcessingState(state: ProcessingState) {
        _processingStates.value = _processingStates.value + state
    }

    /**
     * 処理状態を更新
     */
    fun updateProcessingState(id: String, updater: (ProcessingState) -> ProcessingState) {
        _processingStates.value = _processingStates.value.map { state ->
            if (state.id == id) updater(state) else state
        }
    }

    /**
     * 処理状態を削除
     */
    fun removeProcessingState(id: String) {
        _processingStates.value = _processingStates.value.filter { it.id != id }
    }

    /**
     * すべての処理状態をクリア
     */
    fun clearAllProcessingStates() {
        _processingStates.value = emptyList()
    }

    // Novel Description関連メソッド
    val allNovels: Flow<List<NovelDescEntity>> = novelDescDao.getAllNovels()

    suspend fun getNovelByNcode(ncode: String): NovelDescEntity? {
        return novelDescDao.getNovelByNcode(ncode)
    }

    suspend fun getNovelsByNcodes(ncodes: List<String>): List<NovelDescEntity> {
        return novelDescDao.getNovelsByNcodes(ncodes)
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

    suspend fun getEpisodesByNcodeList(ncode: String): List<EpisodeEntity> {
        return episodeDao.getEpisodesByNcodeList(ncode)
    }

    suspend fun getErrorEpisodes(ncode: String): List<EpisodeEntity> {
        return episodeDao.getErrorEpisodes(ncode)
    }

    suspend fun getEpisode(ncode: String, episodeNo: String): EpisodeEntity? {
        return episodeDao.getEpisode(ncode, episodeNo)
    }

    suspend fun insertEpisode(episode: EpisodeEntity) {
        withContext(Dispatchers.IO) {
            // 既存のエピソードを確認
            val existingEpisode = episodeDao.getEpisode(episode.ncode, episode.episode_no)

            if (existingEpisode != null) {
                // 既存エピソードがある場合、本文と既読情報を保持してマージ
                val mergedEpisode = if (episode.body.isEmpty() && existingEpisode.body.isNotEmpty()) {
                    // 新しいエピソードの本文が空で、既存の本文が存在する場合
                    episode.copy(
                        body = existingEpisode.body,
                        is_read = existingEpisode.is_read,
                        is_bookmark = existingEpisode.is_bookmark,
                        reading_rate = existingEpisode.reading_rate
                    )
                } else {
                    // それ以外の場合、既読情報のみ保持
                    episode.copy(
                        is_read = existingEpisode.is_read,
                        is_bookmark = existingEpisode.is_bookmark,
                        reading_rate = existingEpisode.reading_rate
                    )
                }
                episodeDao.insertEpisode(mergedEpisode)
            } else {
                // 新規エピソード
                episodeDao.insertEpisode(episode)
            }
        }
    }

    suspend fun insertEpisodes(episodes: List<EpisodeEntity>, preserveExisting: Boolean = true) {
        withContext(Dispatchers.IO) {
            if (episodes.isEmpty()) {
                return@withContext
            }
            
            if (preserveExisting) {
                val ncode = episodes.first().ncode
                val existingEpisodes = episodeDao.getEpisodesByNcode(ncode).first()
                val existingMap = existingEpisodes.associateBy { it.episode_no }
                
                val mergedEpisodes = episodes.map { newEpisode ->
                    val existing = existingMap[newEpisode.episode_no]
                    if (existing != null) {
                        newEpisode.copy(
                            body = if (newEpisode.body.isEmpty() && existing.body.isNotEmpty()) existing.body else newEpisode.body,
                            is_read = existing.is_read,
                            is_bookmark = existing.is_bookmark,
                            reading_rate = existing.reading_rate
                        )
                    } else {
                        newEpisode
                    }
                }
                
                episodeDao.insertEpisodes(mergedEpisodes)
            } else {
                episodeDao.insertEpisodes(episodes)
            }
        }
    }

    /**
     * エピソードを保存（既読情報と本文を保持）
     *
     * 既存のエピソードがある場合、既読情報（is_read、is_bookmark、reading_rate）と本文（body）を保持してマージする。
     * これにより、小説の再取得や更新時に既読情報と本文が失われるのを防ぐ。
     *
     * @param episodes 保存するエピソードリスト
     */
    suspend fun insertEpisodesPreservingReadStatus(episodes: List<EpisodeEntity>) {
        withContext(Dispatchers.IO) {
            if (episodes.isEmpty()) {
                android.util.Log.w("NovelRepository", "エピソードリストが空です")
                return@withContext
            }

            val ncode = episodes.first().ncode

            // 既存のエピソードを取得（既読情報と本文を保持するため）
            val existingEpisodes = episodeDao.getEpisodesByNcode(ncode)
            val existingEpisodesSnapshot = existingEpisodes.first()
            val existingMap = existingEpisodesSnapshot.associateBy { it.episode_no }

            // 新しいエピソードと既存のエピソードをマージ（既読情報と本文を保持）
            val mergedEpisodes = episodes.map { newEpisode ->
                val existing = existingMap[newEpisode.episode_no]
                if (existing != null) {
                    // 既存のエピソードがある場合、既読情報を保持
                    newEpisode.copy(
                        body = if (newEpisode.body.isEmpty() && existing.body.isNotEmpty()) existing.body else newEpisode.body,
                        is_read = existing.is_read,
                        is_bookmark = existing.is_bookmark,
                        reading_rate = existing.reading_rate
                    )
                } else {
                    // 新しいエピソード（既読情報はデフォルト値）
                    newEpisode
                }
            }

            // マージしたエピソードを保存
            episodeDao.insertEpisodes(mergedEpisodes)
            android.util.Log.d("NovelRepository", "エピソード保存（既読情報保持）: ${mergedEpisodes.size}件 (ncode=$ncode, 既存: ${existingMap.size}件, 新規: ${mergedEpisodes.size - existingMap.size}件)")
        }
    }

    /**
     * カクヨム小説のエピソードとマッピングを一括保存
     *
     * このメソッドは再取得・更新処理でエピソードマッピング情報を確実に保存する。
     * エピソード番号（連番）とカクヨムの実際のエピソードIDの対応を保持する。
     *
     * **重要**: 既存のエピソードの既読情報（is_read、is_bookmark、reading_rate）と本文（body）を保持する。
     *
     * @param episodes エピソードリスト（episode_noは連番）
     * @param mappings エピソード番号（連番）→カクヨムEpisodeIDのマッピング
     */
    suspend fun insertKakuyomuEpisodesWithMappings(
        episodes: List<EpisodeEntity>,
        mappings: Map<Int, String>
    ) {
        withContext(Dispatchers.IO) {
            if (episodes.isEmpty()) {
                android.util.Log.w("NovelRepository", "エピソードリストが空です")
                return@withContext
            }

            val ncode = episodes.first().ncode

            // 既存のエピソードを取得（既読情報と本文を保持するため）
            val existingEpisodes = episodeDao.getEpisodesByNcode(ncode)
            val existingEpisodesSnapshot = existingEpisodes.first()
            val existingMap = existingEpisodesSnapshot.associateBy { it.episode_no }

            // 新しいエピソードと既存のエピソードをマージ（既読情報と本文を保持）
            val mergedEpisodes = episodes.map { newEpisode ->
                val existing = existingMap[newEpisode.episode_no]
                if (existing != null) {
                    // 既存のエピソードがある場合、既読情報を保持
                    newEpisode.copy(
                        body = if (newEpisode.body.isEmpty() && existing.body.isNotEmpty()) existing.body else newEpisode.body,
                        is_read = existing.is_read,
                        is_bookmark = existing.is_bookmark,
                        reading_rate = existing.reading_rate
                    )
                } else {
                    // 新しいエピソード（既読情報はデフォルト値）
                    newEpisode
                }
            }

            // マージしたエピソードを保存
            episodeDao.insertEpisodes(mergedEpisodes)
            android.util.Log.d("NovelRepository", "カクヨムエピソード保存: ${mergedEpisodes.size}件 (ncode=$ncode, 既存: ${existingMap.size}件, 新規: ${mergedEpisodes.size - existingMap.size}件)")

            // マッピング情報を保存
            val mappingEntities = mappings.map { (episodeNo, kakuyomuId) ->
                EpisodeMappingEntity(
                    ncode = ncode,
                    episode_no = episodeNo,
                    kakuyomu_episode_id = kakuyomuId
                )
            }

            if (mappingEntities.isNotEmpty()) {
                episodeMappingDao.insertMappings(mappingEntities)
                android.util.Log.d("NovelRepository", "カクヨムマッピング保存: ${mappingEntities.size}件 (ncode=$ncode)")
            } else {
                android.util.Log.w("NovelRepository", "マッピング情報が空です (ncode=$ncode)")
            }
        }
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
            deleteEpisodesByNcode(novel.ncode)
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

    // すべての更新キューをクリア
    suspend fun clearAllUpdateQueue() {
        withContext(Dispatchers.IO) {
            updateQueueDao.clearAll()
        }
    }

    suspend fun updateEpisodeReadStatus(ncode: String, episodeNo: String, isRead: Int) {
        episodeDao.updateReadStatus(ncode, episodeNo, isRead)
    }

    /**
     * エピソードのしおり状態を更新
     */
    suspend fun updateEpisodeBookmarkStatus(ncode: String, episodeNo: String, isBookmark: Int) {
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
            "https://api.syosetu.com/novel18api/api/?of=t-w-ga-s-ua&ncode=$ncode&gzip=5"
        } else {
            "https://api.syosetu.com/novelapi/api/?of=t-w-ga-s-ua&ncode=$ncode&gzip=5"
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
            is_r18 = if (isR18) 1 else 0
        )

        urlEntityDao.insertURL(newURLEntity)
        return newURLEntity
    }

    // お気に入り関連メソッド
    suspend fun updateFavoriteStatus(ncode: String, isFavorite: Int) {
        withContext(Dispatchers.IO) {
            novelDescDao.updateFavoriteStatus(ncode, isFavorite)
        }
    }

    val favoriteNovels: Flow<List<NovelDescEntity>> = novelDescDao.getFavoriteNovels()

    // v2.0.0 新規メソッド: sub_site / end_flag / last_checked_at 更新
    suspend fun updateEndFlag(ncode: String, endFlag: Int) {
        withContext(Dispatchers.IO) { novelDescDao.updateEndFlag(ncode, endFlag) }
    }

    suspend fun updateLastCheckedAt(ncode: String, dateTime: String) {
        withContext(Dispatchers.IO) { novelDescDao.updateLastCheckedAt(ncode, dateTime) }
    }

    suspend fun updateSubSite(ncode: String, subSite: Int) {
        withContext(Dispatchers.IO) { novelDescDao.updateSubSite(ncode, subSite) }
    }

    /**
     * URLからサブサイト番号を判定する
     * 0=不明, 1=なろう, 2=ノクターン, 3=ムーンライト, 4=ミッドナイト
     */
    fun detectSubSiteFromUrl(url: String): Int = when {
        url.contains("moonlight.syosetu.com") -> 3
        url.contains("midnight.syosetu.com") -> 4
        url.contains("nocturne.syosetu.com") -> 2
        url.contains("novel18.syosetu.com") -> 2  // デフォルトR18 → ノクターン
        url.contains("ncode.syosetu.com") -> 1
        url.contains("kakuyomu.jp") -> 0  // site_type=2 で判定するため0
        else -> 0
    }

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
            var registrationSession: com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator.RegistrationSession? = null

            try {
                // URLからアダプターと小説IDを取得
                val (adapter, novelId) = com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapterFactory.getAdapterByUrl(url)
                    ?: return@withContext null

                // 処理状態を追加（仮のタイトル）
                addProcessingState(
                    ProcessingState(
                        id = novelId,
                        novelTitle = "取得中...",
                        statusType = ProcessingStatusType.FETCHING
                    )
                )

                // 登録を開始（制限チェック）
                when (val result = com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator.beginRegistration(novelId)) {
                    is com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator.RegistrationResult.Success -> {
                        registrationSession = result.session
                    }
                    is com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator.RegistrationResult.UpdateInProgress -> {
                        AppLogger.w("NovelRepository", "新規登録が拒否されました: 更新処理が実行中です")
                        throw Exception("更新処理が実行中のため、新規登録できません。更新完了後に再度お試しください。")
                    }
                    is com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator.RegistrationResult.MaxConcurrentExceeded -> {
                        AppLogger.w("NovelRepository", "新規登録が拒否されました: 同時登録数が上限（2個）に達しています")
                        throw Exception("同時に登録できる小説は2作品までです。既存の登録が完了してから再度お試しください。")
                    }
                }

                // 小説情報とエピソード一覧を取得
                when (adapter.getSiteType()) {
                    com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter.SITE_TYPE_SYOSETU -> {
                        // 小説家になろうの場合、URLからR18判定を取得してから小説データを取得
                        val syosetuAdapter = adapter as com.shunlight_library.novel_reader.data.adapter.SyosetuAdapter
                        val (ncode, isR18) = syosetuAdapter.extractNcodeWithR18FromUrl(url)

                        // 1話ずつ保存する新方式
                        val (novelDesc, _) = syosetuAdapter.fetchNovelWithEpisodesR18(
                            novelId = novelId,
                            isR18 = isR18,
                            repository = this@NovelRepository,  // 自身を渡す
                            onProgress = { current, total ->
                                AppLogger.d("NovelRepository", "[$novelId] エピソード取得中: $current/$total")
                                // インジケーター状態を更新
                                updateProcessingState(novelId) { state ->
                                    state.copy(
                                        currentEpisode = current,
                                        totalEpisodes = total,
                                        progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
                                    )
                                }
                            }
                        )

                        // タイトルを更新
                        updateProcessingState(novelId) { state ->
                            state.copy(novelTitle = novelDesc.title)
                        }

                        // 小説情報を保存
                        insertNovel(novelDesc)
                        AppLogger.d("NovelRepository", "小説家になろう小説登録（1話ずつ保存）: ${novelDesc.title}, ${novelDesc.general_all_no}話")
                    }
                    com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter.SITE_TYPE_KAKUYOMU -> {
                        // カクヨムの場合、マッピング情報も含めて1話ずつ保存
                        val kakuyomuAdapter = adapter as com.shunlight_library.novel_reader.data.adapter.KakuyomuAdapter
                        val result = kakuyomuAdapter.fetchNovelWithEpisodesIncludingMappings(
                            novelId = novelId,
                            repository = this@NovelRepository,  // 自身を渡す
                            onProgress = { current, total ->
                                AppLogger.d("NovelRepository", "[$novelId] エピソード取得中: $current/$total")
                                // インジケーター状態を更新
                                updateProcessingState(novelId) { state ->
                                    state.copy(
                                        currentEpisode = current,
                                        totalEpisodes = total,
                                        progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
                                    )
                                }
                            }
                        )

                        // タイトルを更新
                        updateProcessingState(novelId) { state ->
                            state.copy(novelTitle = result.novelDesc.title)
                        }

                        // 小説情報を保存
                        insertNovel(result.novelDesc)

                        // マッピング情報を保存
                        val mappingEntities = result.episodeMappings.map { (episodeNo, kakuyomuId) ->
                            com.shunlight_library.novel_reader.data.entity.EpisodeMappingEntity(
                                ncode = result.novelDesc.ncode,
                                episode_no = episodeNo,
                                kakuyomu_episode_id = kakuyomuId
                            )
                        }
                        insertEpisodeMappings(mappingEntities)

                        AppLogger.d("NovelRepository", "カクヨム小説登録（1話ずつ保存）: ${result.novelDesc.title}, マッピング: ${result.episodeMappings.size}件")
                    }
                    else -> {
                        // その他のサイトは通常の取得方法
                        val (novel, episodes) = adapter.fetchNovelWithEpisodes(novelId)

                        // DBに保存
                        insertNovel(novel)
                        insertEpisodes(episodes)
                    }
                }

                // 登録した小説情報を取得して返す
                val novel = getNovelByNcode(novelId)
                    ?: throw Exception("小説情報の取得に失敗しました")

                // URL情報を保存（小説家になろうの場合）
                if (adapter.getSiteType() == com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter.SITE_TYPE_SYOSETU) {
                    val syosetuAdapter = adapter as com.shunlight_library.novel_reader.data.adapter.SyosetuAdapter
                    val (ncode, isR18) = syosetuAdapter.extractNcodeWithR18FromUrl(url)
                    if (ncode != null) {
                        getOrCreateURL(ncode, isR18)
                    }
                }

                // 処理完了後、少し待ってから状態を削除
                kotlinx.coroutines.delay(2000)  // 2秒間表示
                removeProcessingState(novelId)

                novel
            } catch (e: Exception) {
                AppLogger.e("NovelRepository", "Failed to add novel from URL: $url", e)

                // エラー状態に更新
                val (adapter, novelId) = com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapterFactory.getAdapterByUrl(url)
                    ?: return@withContext null
                updateProcessingState(novelId) { state ->
                    state.copy(
                        statusType = ProcessingStatusType.ERROR,
                        errorMessage = e.message
                    )
                }

                // エラー状態を5秒間表示してから削除
                kotlinx.coroutines.delay(5000)
                removeProcessingState(novelId)

                throw e  // エラーメッセージを呼び出し元に伝える
            } finally {
                // 登録セッションを終了
                com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator.finishRegistration(registrationSession)
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
            var registrationSession: com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator.RegistrationSession? = null

            try {
                // 登録を開始（制限チェック）
                when (val result = com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator.beginRegistration(ncode)) {
                    is com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator.RegistrationResult.Success -> {
                        registrationSession = result.session
                    }
                    is com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator.RegistrationResult.UpdateInProgress -> {
                        AppLogger.w("NovelRepository", "新規登録が拒否されました: 更新処理が実行中です")
                        throw Exception("更新処理が実行中のため、新規登録できません。更新完了後に再度お試しください。")
                    }
                    is com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator.RegistrationResult.MaxConcurrentExceeded -> {
                        AppLogger.w("NovelRepository", "新規登録が拒否されました: 同時登録数が上限（2個）に達しています")
                        throw Exception("同時に登録できる小説は2作品までです。既存の登録が完了してから再度お試しください。")
                    }
                }

                // Ncodeからアダプターを取得
                val adapter = com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapterFactory.getAdapterByNcode(ncode)

                // 小説情報とエピソード一覧を取得
                when (adapter.getSiteType()) {
                    com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter.SITE_TYPE_SYOSETU -> {
                        // 小説家になろうの場合、R18判定を渡して1話ずつ保存
                        val syosetuAdapter = adapter as com.shunlight_library.novel_reader.data.adapter.SyosetuAdapter
                        val (novelDesc, _) = syosetuAdapter.fetchNovelWithEpisodesR18(
                            novelId = ncode,
                            isR18 = isR18,
                            repository = this@NovelRepository,  // 自身を渡す
                            onProgress = { current, total ->
                                AppLogger.d("NovelRepository", "[$ncode] エピソード取得中: $current/$total")
                            }
                        )

                        // 小説情報を保存
                        insertNovel(novelDesc)
                        AppLogger.d("NovelRepository", "小説家になろう小説登録（1話ずつ保存）: ${novelDesc.title}, ${novelDesc.general_all_no}話")
                    }
                    com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter.SITE_TYPE_KAKUYOMU -> {
                        // カクヨムの場合、Pseudo-NcodeからworkIdを抽出してマッピング情報も含めて1話ずつ保存
                        val workId = com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator.extractKakuyomuWorkId(ncode)
                        val kakuyomuAdapter = adapter as com.shunlight_library.novel_reader.data.adapter.KakuyomuAdapter
                        val result = kakuyomuAdapter.fetchNovelWithEpisodesIncludingMappings(
                            novelId = workId,
                            repository = this@NovelRepository,  // 自身を渡す
                            onProgress = { current, total ->
                                AppLogger.d("NovelRepository", "[$ncode] エピソード取得中: $current/$total")
                            }
                        )

                        // 小説情報を保存
                        insertNovel(result.novelDesc)

                        // マッピング情報を保存
                        val mappingEntities = result.episodeMappings.map { (episodeNo, kakuyomuId) ->
                            com.shunlight_library.novel_reader.data.entity.EpisodeMappingEntity(
                                ncode = result.novelDesc.ncode,
                                episode_no = episodeNo,
                                kakuyomu_episode_id = kakuyomuId
                            )
                        }
                        insertEpisodeMappings(mappingEntities)

                        AppLogger.d("NovelRepository", "カクヨム小説登録（1話ずつ保存）: ${result.novelDesc.title}, マッピング: ${result.episodeMappings.size}件")
                    }
                    else -> {
                        // その他のサイト
                        val (novel, episodes) = adapter.fetchNovelWithEpisodes(ncode)

                        // DBに保存
                        insertNovel(novel)
                        insertEpisodes(episodes)
                    }
                }

                // 登録した小説情報を取得して返す
                val novel = getNovelByNcode(ncode)
                    ?: throw Exception("小説情報の取得に失敗しました")

                // URL情報を保存（小説家になろうの場合）
                if (adapter.getSiteType() == com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter.SITE_TYPE_SYOSETU) {
                    getOrCreateURL(ncode, isR18)
                }

                novel
            } catch (e: Exception) {
                AppLogger.e("NovelRepository", "Failed to add novel by ncode: $ncode", e)
                throw e  // エラーメッセージを呼び出し元に伝える
            } finally {
                // 登録セッションを終了
                com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator.finishRegistration(registrationSession)
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
                AppLogger.e("NovelRepository", "Failed to check updates for ncode: $ncode", e)
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
                AppLogger.e("NovelRepository", "Failed to get web URL for ncode: $ncode", e)
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
                        // カクヨムの場合、マッピングテーブルから実際のエピソードIDを取得
                        val workId = com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator.extractKakuyomuWorkId(ncode)
                        val kakuyomuEpisodeId = episodeMappingDao.getKakuyomuEpisodeId(ncode, episodeNo.toInt())

                        if (kakuyomuEpisodeId != null) {
                            adapter.generateEpisodeUrl(workId, kakuyomuEpisodeId)
                        } else {
                            // マッピングが見つからない場合は、episodeNoをそのまま使用（フォールバック）
                            AppLogger.w("NovelRepository", "カクヨムエピソードマッピングが見つかりません: ncode=$ncode, episodeNo=$episodeNo")
                            adapter.generateEpisodeUrl(workId, episodeNo)
                        }
                    }
                    else -> null
                }
            } catch (e: Exception) {
                AppLogger.e("NovelRepository", "Failed to get episode URL for ncode: $ncode, episode: $episodeNo", e)
                null
            }
        }
    }

    // ==================== 新規登録キュー管理メソッド ====================

    suspend fun insertRegistrationQueue(queue: RegistrationQueueEntity): Long {
        return registrationQueueDao.insert(queue)
    }

    fun getAllRegistrationQueue(): Flow<List<RegistrationQueueEntity>> {
        return registrationQueueDao.getAll()
    }

    fun getRegistrationQueueByStatus(status: Int): Flow<List<RegistrationQueueEntity>> {
        return registrationQueueDao.getByStatus(status)
    }

    suspend fun getRegistrationQueueById(id: Long): RegistrationQueueEntity? {
        return registrationQueueDao.getById(id)
    }

    suspend fun updateRegistrationQueue(queue: RegistrationQueueEntity) {
        registrationQueueDao.update(queue)
    }

    suspend fun deleteRegistrationQueue(id: Long) {
        registrationQueueDao.deleteById(id)
    }

    suspend fun deleteCompletedRegistrationQueue() {
        registrationQueueDao.deleteByStatus(RegistrationQueueEntity.STATUS_COMPLETED)
    }

    fun getProcessingRegistrationQueueCount(): Flow<Int> {
        return registrationQueueDao.getProcessingCount()
    }

    suspend fun getPendingRegistrationQueueCount(): Int {
        return registrationQueueDao.getPendingCount()
    }

    suspend fun getProcessingRegistrationQueueCountSync(): Int {
        return registrationQueueDao.getProcessingCountSync()
    }

    suspend fun getNextPendingRegistrationQueue(): RegistrationQueueEntity? {
        return registrationQueueDao.getNextPendingQueue()
    }

    suspend fun updateRegistrationQueueStatus(id: Long, status: Int, errorMessage: String? = null) {
        registrationQueueDao.updateStatus(id, status, errorMessage)
    }

    suspend fun updateRegistrationQueueProgress(id: Long, currentEpisode: Int) {
        registrationQueueDao.updateProgress(id, currentEpisode)
    }

    suspend fun updateRegistrationQueueNovelInfo(id: Long, title: String, totalEpisodes: Int) {
        registrationQueueDao.updateNovelInfo(id, title, totalEpisodes)
    }

    suspend fun cancelRegistrationQueue(id: Long) {
        val queue = registrationQueueDao.getById(id)
        if (queue != null && queue.status == RegistrationQueueEntity.STATUS_PENDING) {
            registrationQueueDao.deleteById(id)
        }
    }

    suspend fun retryRegistrationQueue(id: Long) {
        val queue = registrationQueueDao.getById(id)
        if (queue != null && (queue.status == RegistrationQueueEntity.STATUS_ERROR
                    || queue.status == RegistrationQueueEntity.STATUS_TIMEOUT
                    || queue.status == RegistrationQueueEntity.STATUS_PAUSED)) {
            registrationQueueDao.updateStatus(id, RegistrationQueueEntity.STATUS_PENDING, null)
        }
    }

    // === 一時エピソード関連メソッド ===

    /**
     * 一時テーブルにエピソードを保存
     */
    suspend fun insertTempEpisode(episode: TempEpisodeEntity) {
        tempEpisodeDao.insert(episode)
    }

    /**
     * 一時テーブルにエピソードを一括保存
     */
    suspend fun insertTempEpisodes(episodes: List<TempEpisodeEntity>) {
        tempEpisodeDao.insertAll(episodes)
    }

    /**
     * 指定ncodeの一時エピソード一覧を取得
     */
    suspend fun getTempEpisodesByNcode(ncode: String): List<TempEpisodeEntity> {
        return tempEpisodeDao.getByNcode(ncode)
    }

    /**
     * 指定ncodeの一時エピソード数を取得
     */
    suspend fun getTempEpisodeCountByNcode(ncode: String): Int {
        return tempEpisodeDao.getCountByNcode(ncode)
    }

    /**
     * 指定ncodeの一時テーブルの最大エピソード番号を取得（リトライ時の続きから取得用）
     */
    suspend fun getTempMaxEpisodeNo(ncode: String): Int? {
        return tempEpisodeDao.getMaxEpisodeNo(ncode)
    }

    /**
     * 指定ncodeの本体テーブルの最大エピソード番号を取得（レジュームポイント検出用）
     */
    suspend fun getMainMaxEpisodeNo(ncode: String): Int? {
        return episodeDao.getMaxEpisodeNo(ncode)
    }

    /**
     * 指定ncodeの本体テーブルのエピソード数を取得
     */
    suspend fun getMainEpisodeCountByNcode(ncode: String): Int {
        return episodeDao.getEpisodeCountByNcode(ncode)
    }

    /**
     * エピソードテーブルに存在するがnovels_descsに存在しない孤立ncodeを検出する
     */
    suspend fun findOrphanedEpisodeNcodes(): List<String> = withContext(Dispatchers.IO) {
        val episodeNcodes = episodeDao.getDistinctNcodes()
        if (episodeNcodes.isEmpty()) return@withContext emptyList()

        // SQLite IN句の制限を考慮してチャンク分割
        val existingNovels = episodeNcodes.chunked(500).flatMap { chunk ->
            novelDescDao.getNovelsByNcodes(chunk)
        }
        val existingNcodes = existingNovels.map { it.ncode }.toSet()

        episodeNcodes.filter { it !in existingNcodes }
    }

    /**
     * 一時エピソードを本体テーブルに統合する。
     * 既読ステータスやブックマークは既存データを保持する。
     *
     * @param ncode 統合対象の小説コード
     * @param deleteTempAfterMerge trueの場合、統合後に一時データを削除する（デフォルト: true）
     *                             タイムアウト時はfalseを指定し、リトライ時のレジュームを可能にする
     * @return 統合されたエピソード数
     */
    suspend fun mergeTempEpisodesToMain(ncode: String, deleteTempAfterMerge: Boolean = true): Int {
        val tempEpisodes = tempEpisodeDao.getByNcode(ncode)
        if (tempEpisodes.isEmpty()) return 0

        var mergedCount = 0
        for (tempEp in tempEpisodes) {
            val mainEpisode = tempEp.toEpisodeEntity()
            // insertEpisode は既存の既読・ブックマーク状態を保持する
            insertEpisode(mainEpisode)
            mergedCount++
        }

        if (deleteTempAfterMerge) {
            // 統合完了後、一時データを削除
            tempEpisodeDao.deleteByNcode(ncode)
            AppLogger.d("NovelRepository", "一時エピソード統合完了（一時データ削除済み）: ncode=$ncode, ${mergedCount}話")
        } else {
            // タイムアウト時: 一時データを保持してリトライ時のレジュームを可能にする
            AppLogger.d("NovelRepository", "一時エピソード統合完了（一時データ保持）: ncode=$ncode, ${mergedCount}話")
        }
        return mergedCount
    }

    /**
     * 指定ncodeの一時エピソードを削除
     */
    suspend fun deleteTempEpisodesByNcode(ncode: String) {
        tempEpisodeDao.deleteByNcode(ncode)
    }

    /**
     * 指定キューIDの一時エピソードを削除
     */
    suspend fun deleteTempEpisodesByQueueId(queueId: Long) {
        tempEpisodeDao.deleteByQueueId(queueId)
    }

    /**
     * 全ての一時エピソードを削除
     */
    suspend fun deleteAllTempEpisodes() {
        tempEpisodeDao.deleteAll()
    }

    /**
     * 孤立エピソードの小説メタデータを復元
     *
     * ncodeからsite_typeを判定し、APIから小説メタデータを取得してnovels_descsに追加する。
     * エピソード本文は再ダウンロードしない。
     *
     * @param ncode 孤立エピソードのncode
     * @param onResult 結果コールバック (成功したかどうか, メッセージ)
     */
    suspend fun restoreNovelMetadata(
        ncode: String,
        onResult: (Boolean, String) -> Unit
    ) {
        try {
            val isKakuyomu = com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator.isKakuyomuNcode(ncode)
            var novelDesc: NovelDescEntity? = null

            if (isKakuyomu) {
                val workId = com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator.extractKakuyomuWorkId(ncode)
                val kakuyomuAdapter = com.shunlight_library.novel_reader.data.adapter.KakuyomuAdapter()
                val (desc, _) = kakuyomuAdapter.fetchNovelMetadataWithEpisodeList(workId)
                novelDesc = desc
            } else {
                var isR18 = false
                novelDesc = com.shunlight_library.novel_reader.api.NovelApiUtils.fetchNovelDetails(ncode, isR18 = false)
                if (novelDesc == null) {
                    isR18 = true
                    novelDesc = com.shunlight_library.novel_reader.api.NovelApiUtils.fetchNovelDetails(ncode, isR18 = true)
                }
            }

            if (novelDesc != null) {
                val actualEpisodeCount = episodeDao.getEpisodeCountByNcode(ncode)
                val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                val updatedNovel = novelDesc.copy(
                    total_ep = actualEpisodeCount,
                    registered_at = currentDate
                )

                insertNovel(updatedNovel)
                onResult(true, "「${updatedNovel.title}」のメタデータを復元しました（${actualEpisodeCount}話）")
                AppLogger.d("NovelRepository", "孤立エピソードメタデータ復元成功: ncode=$ncode, title=${updatedNovel.title}, episodes=$actualEpisodeCount")
            } else {
                onResult(false, "ncode=$ncode の小説情報を取得できませんでした")
                AppLogger.w("NovelRepository", "孤立エピソードメタデータ復元失敗: ncode=$ncode, 小説情報が見つかりません")
            }
        } catch (e: Exception) {
            val errorMsg = "ncode=$ncode のメタデータ復元中にエラーが発生しました: ${e.message}"
            onResult(false, errorMsg)
            AppLogger.e("NovelRepository", "孤立エピソードメタデータ復元エラー: ncode=$ncode", e)
        }
    }

}
