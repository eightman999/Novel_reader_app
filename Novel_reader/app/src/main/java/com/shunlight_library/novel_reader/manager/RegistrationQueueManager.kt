/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Manager for new novel registration queue.
 */
package com.shunlight_library.novel_reader.manager

import android.util.Log
import com.shunlight_library.novel_reader.NovelReaderApplication
import com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter
import com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapterFactory
import com.shunlight_library.novel_reader.data.entity.RegistrationQueueEntity
import com.shunlight_library.novel_reader.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 新規小説登録キューの管理クラス
 *
 * 同時処理数（2件）の制限を実現し、バックグラウンドで小説情報・エピソードを取得する。
 * 処理完了後は保持される（上限10件まで）。
 */
object RegistrationQueueManager {
    private const val TAG = "RegistrationQueueManager"
    const val MAX_CONCURRENT = 2

    private val repository = NovelReaderApplication.getRepository()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var monitoringJob: Job? = null
    private val processingQueues = mutableMapOf<Long, Job>()

    /**
     * キューの監視を開始する
     */
    fun startMonitoring() {
        AppLogger.d(TAG, "キュー監視を開始")
        monitoringJob = scope.launch {
            while (true) {
                try {
                    processNextQueue()
                    delay(1000) // 1秒ごとにチェック
                } catch (e: Exception) {
                    AppLogger.e(TAG, "キュー監視中にエラー発生", e)
                    delay(5000) // エラー時は5秒待機
                }
            }
        }
    }

    /**
     * キューの監視を停止する
     */
    fun stopMonitoring() {
        AppLogger.d(TAG, "キュー監視を停止")
        monitoringJob?.cancel()
        monitoringJob = null
    }

    /**
     * キューに新しい登録要求を追加する
     *
     * @param url 小説サイトのURL
     * @return 追加されたキューのID（失敗時はnull）
     */
    suspend fun addToQueue(url: String): Long? {
        return withContext(Dispatchers.IO) {
            try {
                // URLからアダプターと小説IDを取得
                val (adapter, novelId) = NovelSiteAdapterFactory.getAdapterByUrl(url)
                    ?: throw Exception("サポート対象外のURLです: $url")

                // 既に登録済みの場合
                val existingNovel = repository.getNovelByNcode(novelId)
                if (existingNovel != null) {
                    throw Exception("この小説は既に登録されています: ${existingNovel.title}")
                }

                // 既にキューに存在する場合
                val allQueues = repository.getAllRegistrationQueue().first()
                if (allQueues.any { it.ncode == novelId }) {
                    throw Exception("この小説は既にダウンロードキューに存在します")
                }

                // URLからR18判定を取得
                val (ncode, isR18) = com.shunlight_library.novel_reader.api.NovelApiUtils.extractNcodeFromUrl(url)

                // キューを作成
                val queue = RegistrationQueueEntity(
                    ncode = novelId,
                    site_type = adapter.getSiteType(),
                    title = "取得中...",
                    url = url,
                    is_r18 = isR18,
                    status = RegistrationQueueEntity.STATUS_PENDING,
                    current_episode = 0,
                    total_episodes = 0,
                    error_message = null,
                    created_at = getCurrentDateTimeString(),
                    started_at = null,
                    completed_at = null
                )

                val queueId = repository.insertRegistrationQueue(queue)
                AppLogger.d(TAG, "キューに追加: id=$queueId, ncode=$novelId")

                queueId
            } catch (e: Exception) {
                AppLogger.e(TAG, "キュー追加失敗: $url", e)
                throw e
            }
        }
    }

    /**
     * キューをキャンセルする
     *
     * @param id キューID
     */
    suspend fun cancelQueue(id: Long) {
        withContext(Dispatchers.IO) {
            repository.cancelRegistrationQueue(id)
            processingQueues[id]?.cancel()
            processingQueues.remove(id)
            AppLogger.d(TAG, "キューをキャンセル: id=$id")
        }
    }

    /**
     * エラーキューを再試行する
     *
     * @param id キューID
     */
    suspend fun retryQueue(id: Long) {
        withContext(Dispatchers.IO) {
            repository.retryRegistrationQueue(id)
            AppLogger.d(TAG, "キューを再試行: id=$id")
        }
    }

    /**
     * 次のキューを処理する
     */
    private suspend fun processNextQueue() {
        try {
            // 処理中のキュー数を取得
            val processingCount = repository.getProcessingRegistrationQueueCountSync()

            if (processingCount < MAX_CONCURRENT) {
                // 次の待機中キューを取得
                val queue = repository.getNextPendingRegistrationQueue()

                if (queue != null) {
                    // 処理中に変更
                    repository.updateRegistrationQueueStatus(
                        queue.id,
                        RegistrationQueueEntity.STATUS_PROCESSING,
                        null
                    )

                    // 処理開始
                    processQueue(queue)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "次のキュー処理エラー", e)
        }
    }

    /**
     * キューを処理する
     *
     * @param queue 処理対象のキュー
     */
    private fun processQueue(queue: RegistrationQueueEntity) {
        val job = scope.launch {
            try {
                AppLogger.d(TAG, "キュー処理開始: id=${queue.id}, ncode=${queue.ncode}")

                // 登録を開始（制限チェック）
                val registrationResult = com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator.beginRegistration(queue.ncode)
                val session = when (registrationResult) {
                    is com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator.RegistrationResult.Success -> registrationResult.session
                    else -> {
                        withContext(Dispatchers.IO) {
                            updateQueueError(queue.id, "同時処理数の上限に達しました")
                        }
                        return@launch
                    }
                }

                // 既に登録済みの場合
                val novel = repository.getNovelByNcode(queue.ncode)
                if (novel != null) {
                    withContext(Dispatchers.IO) {
                        updateQueueError(queue.id, "既に登録されています: ${novel.title}")
                        deleteQueue(queue.id)
                    }
                    com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator.finishRegistration(session)
                    return@launch
                }

                // サイト種別に応じて取得処理
                val adapter = NovelSiteAdapterFactory.getAdapter(queue.site_type)
                val fetchSuccess = when (adapter.getSiteType()) {
                    NovelSiteAdapter.SITE_TYPE_KAKUYOMU -> {
                        val kakuyomuAdapter = adapter as com.shunlight_library.novel_reader.data.adapter.KakuyomuAdapter
                        val result = kakuyomuAdapter.fetchNovelWithEpisodesIncludingMappings(
                            queue.ncode,
                            repository,
                            onProgress = { current, total ->
                                scope.launch {
                                    updateQueueProgress(queue.id, current, total)
                                }
                            }
                        )

                        // 小説情報を保存
                        repository.insertNovel(result.novelDesc)
                        val episodesCount = result.episodes.size
                        AppLogger.d(TAG, "小説登録完了: ${result.novelDesc.title}, ${episodesCount}話")
                        true
                    }
                    NovelSiteAdapter.SITE_TYPE_SYOSETU -> {
                        val syosetuAdapter = adapter as com.shunlight_library.novel_reader.data.adapter.SyosetuAdapter
                        val (novelDesc, episodes) = syosetuAdapter.fetchNovelWithEpisodesR18(
                            queue.ncode,
                            queue.is_r18,
                            repository,
                            onProgress = { current, total ->
                                scope.launch {
                                    updateQueueProgress(queue.id, current, total)
                                }
                            }
                        )

                        // total_epを正しいエピソード数で更新
                        val updatedNovelDesc = novelDesc.copy(total_ep = novelDesc.general_all_no)

                        // 小説情報を保存
                        repository.insertNovel(updatedNovelDesc)
                        val episodesCount = if (episodes.isNotEmpty()) {
                            episodes.size
                        } else {
                            novelDesc.general_all_no
                        }
                        AppLogger.d(TAG, "小説登録完了: ${updatedNovelDesc.title}, ${episodesCount}話")
                        true
                    }
                    else -> {
                        throw Exception("未対応のサイト種別: ${queue.site_type}")
                    }
                }

                if (!fetchSuccess) {
                    throw Exception("小説の取得に失敗しました")
                }

                // 完了に変更
                repository.updateRegistrationQueueStatus(
                    queue.id,
                    RegistrationQueueEntity.STATUS_COMPLETED,
                    null
                )

                AppLogger.d(TAG, "キュー処理完了: id=${queue.id}, ncode=${queue.ncode}")

                // 完了キューの上限管理（10個以上の場合、古いものから削除）
                scope.launch {
                    try {
                        val completedQueues = repository.getRegistrationQueueByStatus(
                            RegistrationQueueEntity.STATUS_COMPLETED
                        ).first()

                        if (completedQueues.size > 10) {
                            val toDelete = completedQueues.take(completedQueues.size - 10)
                            toDelete.forEach { completedQueue ->
                                repository.deleteRegistrationQueue(completedQueue.id)
                                AppLogger.d(TAG, "完了キューを削除（上限超過）: id=${completedQueue.id}")
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "完了キューのクリーンアップエラー", e)
                    }
                }

            } catch (e: Exception) {
                AppLogger.e(TAG, "キュー処理エラー: id=${queue.id}", e)
                withContext(Dispatchers.IO) {
                    updateQueueError(queue.id, e.message ?: "不明なエラー")
                }
            } finally {
                processingQueues.remove(queue.id)
                // 登録セッションを終了
                com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator.finishRegistrationByNcode(queue.ncode)
            }
        }

        processingQueues[queue.id] = job
    }

    /**
     * キューの進捗を更新する
     *
     * @param id キューID
     * @param currentEpisode 現在のエピソード数
     * @param totalEpisodes 総エピソード数
     */
    private suspend fun updateQueueProgress(id: Long, currentEpisode: Int, totalEpisodes: Int) {
        repository.updateRegistrationQueueProgress(id, currentEpisode)
        if (totalEpisodes > 0) {
            repository.updateRegistrationQueueNovelInfo(id, "", totalEpisodes)
        }
    }

    /**
     * キューのエラー状態を更新する
     *
     * @param id キューID
     * @param errorMessage エラーメッセージ
     */
    private suspend fun updateQueueError(id: Long, errorMessage: String) {
        repository.updateRegistrationQueueStatus(id, RegistrationQueueEntity.STATUS_ERROR, errorMessage)
    }

    /**
     * キューを削除する
     *
     * @param id キューID
     */
    private suspend fun deleteQueue(id: Long) {
        repository.deleteRegistrationQueue(id)
    }

    /**
     * 現在日時を文字列で取得する
     *
     * @return yyyy-MM-dd HH:mm:ss形式の日時文字列
     */
    private fun getCurrentDateTimeString(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
}
