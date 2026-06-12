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
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.RegistrationQueueEntity
import com.shunlight_library.novel_reader.data.entity.TempEpisodeEntity
import com.shunlight_library.novel_reader.utils.AppLogger
import com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 新規小説登録キューの管理クラス
 *
 * 同時処理数（2件）の制限を実現し、バックグラウンドで小説情報・エピソードを取得する。
 * エピソードは一時テーブル（temp_episodes）に保存し、完了またはタイムアウト時に
 * 本体テーブル（episodes）に統合する。
 *
 * タイムアウト時はステータスをTIMEOUTに設定し、一時データを保持してリトライ可能にする。
 */
object RegistrationQueueManager {
    private const val TAG = "RegistrationQueueManager"
    const val MAX_CONCURRENT = 2

    /**
     * ダウンロード全体のタイムアウト（ミリ秒）
     * デフォルト: 10分（600,000ms）
     */
    private const val DOWNLOAD_TIMEOUT_MS = 600_000L

    /**
     * 1話あたりの最大取得時間（ミリ秒）
     * この時間を超えた場合、該当エピソードをスキップする
     * デフォルト: 60秒
     */
    private const val PER_EPISODE_TIMEOUT_MS = 60_000L

    private val repository = NovelReaderApplication.getRepository()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var monitoringJob: Job? = null
    private val processingQueues = mutableMapOf<Long, Job>()
    // 一時停止によるキャンセルかどうかを識別するためのセット
    private val pausedQueueIds = mutableSetOf<Long>()

    /**
     * キューの監視を開始する
     */
    fun startMonitoring() {
        AppLogger.d(TAG, "キュー監視を開始")
        monitoringJob = scope.launch {
            // 起動時に STATUS_PROCESSING 残骸をリセット（プロセス強制終了で残ったもの）
            try {
                val reset = repository.resetStuckProcessingQueues()
                if (reset > 0) AppLogger.w(TAG, "STATUS_PROCESSING の残骸 ${reset} 件を PENDING にリセットしました")
            } catch (e: Exception) {
                AppLogger.e(TAG, "PROCESSING リセット失敗", e)
            }

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
                val (adapter, rawNovelId) = NovelSiteAdapterFactory.getAdapterByUrl(url)
                    ?: throw Exception("サポート対象外のURLです: $url")

                // カクヨムは疑似Ncodeに変換（workId数値列のままだと temp→main のマージが失敗する）
                val novelId = if (adapter.getSiteType() == NovelSiteAdapter.SITE_TYPE_KAKUYOMU) {
                    PseudoNcodeGenerator.generateKakuyomuNcode(rawNovelId)
                } else {
                    rawNovelId
                }

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
     * キューを削除する（全ステータス対応）
     *
     * 処理中のジョブがあればキャンセルし、一時エピソードも削除してからキューを削除する。
     *
     * @param id キューID
     */
    suspend fun cancelQueue(id: Long) {
        withContext(Dispatchers.IO) {
            // 先に処理中のジョブを停止して完了を待つ
            // （削除後にジョブが temp_episodes へ書き込み、孤児データが残る競合を防ぐ）
            processingQueues[id]?.cancelAndJoin()
            processingQueues.remove(id)
            pausedQueueIds.remove(id)

            val queue = repository.getRegistrationQueueById(id)
            if (queue != null) {
                // 一時エピソードも削除
                repository.deleteTempEpisodesByNcode(queue.ncode)
            }
            // キューを削除
            repository.deleteRegistrationQueue(id)
            AppLogger.d(TAG, "キューを削除: id=$id")
        }
    }

    /**
     * 処理中または待機中のキューを一時停止する
     *
     * 処理中のジョブをキャンセルし、途中までのデータを保持したままSTATUS_PAUSEDに設定する。
     * リトライ時にレジュームポイントから再開できる。
     *
     * @param id キューID
     */
    suspend fun pauseQueue(id: Long) {
        withContext(Dispatchers.IO) {
            val queue = repository.getRegistrationQueueById(id)
            if (queue == null) return@withContext

            when (queue.status) {
                RegistrationQueueEntity.STATUS_PROCESSING -> {
                    // 一時停止フラグを設定してからジョブをキャンセル
                    // processQueueのcatchブロックで一時停止として処理される
                    pausedQueueIds.add(id)
                    processingQueues[id]?.cancel()
                    AppLogger.d(TAG, "処理中キューを一時停止: id=$id")
                }
                RegistrationQueueEntity.STATUS_PENDING -> {
                    // 待機中は即座にPAUSEDに変更
                    repository.updateRegistrationQueueStatus(
                        id,
                        RegistrationQueueEntity.STATUS_PAUSED,
                        "一時停止中"
                    )
                    AppLogger.d(TAG, "待機中キューを一時停止: id=$id")
                }
                else -> {
                    AppLogger.w(TAG, "一時停止できないステータス: id=$id, status=${queue.status}")
                }
            }
        }
    }

    /**
     * エラー/タイムアウト/一時停止キューを再試行する
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
     * キューを処理する（タイムアウト対応・一時DB経由）
     *
     * エピソードは一時テーブルに保存し、完了時に本体テーブルに統合する。
     * タイムアウト時は一時データを保持し、リトライ時に続きから取得できる。
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

                // リトライ時のレジュームポイントを検出
                // 1. まず一時テーブルから最大エピソード番号を取得
                // 2. 一時テーブルが空の場合は本体テーブルから取得（前回タイムアウトでマージ済みの場合）
                val existingTempCount = repository.getTempEpisodeCountByNcode(queue.ncode)
                val existingMainCount = repository.getMainEpisodeCountByNcode(queue.ncode)
                val resumeFrom = if (existingTempCount > 0) {
                    val maxNo = repository.getTempMaxEpisodeNo(queue.ncode) ?: 0
                    AppLogger.d(TAG, "リトライ: 一時データ ${existingTempCount}話あり（最大No: $maxNo）、続きから取得")
                    maxNo
                } else if (existingMainCount > 0) {
                    val mainMaxNo = repository.getMainMaxEpisodeNo(queue.ncode) ?: 0
                    AppLogger.d(TAG, "リトライ: 本体テーブルに ${existingMainCount}話あり（最大No: $mainMaxNo）、続きから取得")
                    mainMaxNo
                } else {
                    0
                }

                // 新規ダウンロード（リトライではない）かつ既に登録済みの場合はスキップ
                if (resumeFrom == 0) {
                    val existingNovel = repository.getNovelByNcode(queue.ncode)
                    if (existingNovel != null) {
                        withContext(Dispatchers.IO) {
                            updateQueueError(queue.id, "既に登録されています: ${existingNovel.title}")
                            repository.deleteTempEpisodesByNcode(queue.ncode)
                            deleteQueue(queue.id)
                        }
                        com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator.finishRegistration(session)
                        return@launch
                    }
                }

                try {
                    // タイムアウト付きでダウンロード実行
                    withTimeout(DOWNLOAD_TIMEOUT_MS) {
                        // サイト種別に応じて取得処理（一時テーブル経由）
                        val adapter = NovelSiteAdapterFactory.getAdapter(queue.site_type)
                        when (adapter.getSiteType()) {
                            NovelSiteAdapter.SITE_TYPE_KAKUYOMU -> {
                                fetchKakuyomuWithTempDb(queue, resumeFrom)
                            }
                            NovelSiteAdapter.SITE_TYPE_SYOSETU -> {
                                fetchSyosetuWithTempDb(queue, resumeFrom)
                            }
                            else -> {
                                throw Exception("未対応のサイト種別: ${queue.site_type}")
                            }
                        }
                    }

                    // 完了: 一時テーブルから本体テーブルに統合
                    val mergedCount = repository.mergeTempEpisodesToMain(queue.ncode)
                    AppLogger.d(TAG, "本体DBに統合完了: ${mergedCount}話")

                    // 完了に変更
                    repository.updateRegistrationQueueStatus(
                        queue.id,
                        RegistrationQueueEntity.STATUS_COMPLETED,
                        null
                    )

                    AppLogger.d(TAG, "キュー処理完了: id=${queue.id}, ncode=${queue.ncode}")

                    // 完了キューの上限管理
                    cleanupCompletedQueues()

                } catch (e: TimeoutCancellationException) {
                    // タイムアウト: 一時データを保持し、ステータスをTIMEOUTに設定
                    val tempCount = repository.getTempEpisodeCountByNcode(queue.ncode)
                    val totalEp = queue.total_episodes

                    // タイムアウト時は一時データを保持したままマージ（リトライ時のレジュームを可能にする）
                    val mergedCount = repository.mergeTempEpisodesToMain(queue.ncode, deleteTempAfterMerge = false)
                    AppLogger.w(TAG, "タイムアウト: id=${queue.id}, ncode=${queue.ncode}, " +
                            "取得済み=${tempCount}話, 統合=${mergedCount}話, 合計=${totalEp}話（一時データ保持）")

                    repository.updateRegistrationQueueStatus(
                        queue.id,
                        RegistrationQueueEntity.STATUS_TIMEOUT,
                        "タイムアウト（${tempCount}/${totalEp}話取得済み、${mergedCount}話統合済み）"
                    )
                }

            } catch (e: CancellationException) {
                // ジョブキャンセル（一時停止 or 削除）
                val isPaused = pausedQueueIds.remove(queue.id)
                if (isPaused) {
                    // 一時停止: tempデータを保持したままマージし、PAUSEDステータスに設定
                    AppLogger.d(TAG, "一時停止処理: id=${queue.id}, ncode=${queue.ncode}")
                    try {
                        val tempCount = repository.getTempEpisodeCountByNcode(queue.ncode)
                        val mergedCount = repository.mergeTempEpisodesToMain(queue.ncode, deleteTempAfterMerge = false)
                        val totalEp = queue.total_episodes

                        repository.updateRegistrationQueueStatus(
                            queue.id,
                            RegistrationQueueEntity.STATUS_PAUSED,
                            "一時停止（${tempCount}/${totalEp}話取得済み、${mergedCount}話統合済み）"
                        )
                        AppLogger.d(TAG, "一時停止完了: id=${queue.id}, 取得済み=${tempCount}話, 統合=${mergedCount}話")
                    } catch (pauseError: Exception) {
                        AppLogger.e(TAG, "一時停止処理エラー", pauseError)
                    }
                } else {
                    // 削除によるキャンセル: 何もしない（cancelQueueで処理済み）
                    AppLogger.d(TAG, "キャンセルによるジョブ終了: id=${queue.id}")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "キュー処理エラー: id=${queue.id}", e)

                // エラー時も途中までの一時データを本体DBに統合
                try {
                    val mergedCount = repository.mergeTempEpisodesToMain(queue.ncode)
                    if (mergedCount > 0) {
                        AppLogger.d(TAG, "エラー発生時の部分統合: ${mergedCount}話を本体DBに統合")
                    }
                } catch (mergeError: Exception) {
                    AppLogger.e(TAG, "部分統合エラー", mergeError)
                }

                withContext(Dispatchers.IO) {
                    updateQueueError(queue.id, e.message ?: "不明なエラー")
                }
            } finally {
                processingQueues.remove(queue.id)
                pausedQueueIds.remove(queue.id)
                // 登録セッションを終了
                com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator.finishRegistrationByNcode(queue.ncode)
            }
        }

        processingQueues[queue.id] = job
    }

    /**
     * カクヨム小説を一時テーブル経由で取得する（ストリーミング方式）
     *
     * エピソードを1話ずつ取得→一時テーブルに保存し、メモリ効率を改善。
     * resumeFromが指定されている場合、それ以降のエピソードのみを取得する（再ダウンロード回避）。
     *
     * @param queue 処理対象のキュー
     * @param resumeFrom リトライ時の開始エピソード番号（0の場合は最初から）
     */
    private suspend fun fetchKakuyomuWithTempDb(queue: RegistrationQueueEntity, resumeFrom: Int) {
        val adapter = NovelSiteAdapterFactory.getAdapter(queue.site_type) as com.shunlight_library.novel_reader.data.adapter.KakuyomuAdapter

        // まず小説情報とエピソード一覧（本文なし）を取得
        val (novelDesc, episodesWithoutBody) = adapter.fetchNovelMetadataWithEpisodeList(queue.ncode)

        // 小説情報を保存（既存のregistered_atを保持）
        val existingNovel = repository.getNovelByNcode(queue.ncode)
        val novelToSave = if (existingNovel != null) {
            novelDesc.copy(registered_at = existingNovel.registered_at)
        } else {
            novelDesc
        }
        repository.insertNovel(novelToSave)

        // マッピング情報を取得して保存
        val mappings = adapter.getCachedMappings()
        if (mappings.isNotEmpty()) {
            val mappingEntities = mappings.map { (episodeNo, kakuyomuEpisodeId) ->
                com.shunlight_library.novel_reader.data.entity.EpisodeMappingEntity(
                    ncode = novelDesc.ncode,
                    episode_no = episodeNo,
                    kakuyomu_episode_id = kakuyomuEpisodeId
                )
            }
            repository.insertEpisodeMappings(mappingEntities)
            AppLogger.d(TAG, "カクヨムマッピング保存: ${mappingEntities.size}件")
        }

        val totalEpisodes = episodesWithoutBody.size
        val startFrom = resumeFrom + 1

        AppLogger.d(TAG, "カクヨムエピソード取得開始: ${novelDesc.title}, " +
                "全${totalEpisodes}話, 開始=${startFrom}話目")

        // workIdを抽出（エピソード本文取得に必要）
        val workId = if (com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator.isKakuyomuNcode(queue.ncode)) {
            com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator.extractKakuyomuWorkId(queue.ncode)
        } else {
            queue.ncode
        }

        // 1話ずつ取得→一時テーブルに保存（ストリーミング方式）
        for (episode in episodesWithoutBody) {
            val epNo = episode.episode_no.toIntOrNull() ?: 0
            if (epNo <= resumeFrom) {
                // レジュームポイント以前のエピソードはスキップ（ダウンロードしない）
                continue
            }

            // マッピングから実際のカクヨムエピソードIDを取得
            val actualEpisodeId = mappings[epNo] ?: episode.episode_no

            // エピソード本文を取得
            val episodeBody = adapter.fetchEpisodeContent(workId, actualEpisodeId)
            val episodeWithBody = episode.copy(body = episodeBody)

            // 一時テーブルに保存
            val tempEpisode = TempEpisodeEntity.fromEpisodeEntity(episodeWithBody, queue.id)
            repository.insertTempEpisode(tempEpisode)

            // 進捗通知
            scope.launch {
                updateQueueProgress(queue.id, epNo, totalEpisodes)
            }
        }

        AppLogger.d(TAG, "カクヨム小説取得完了（一時テーブル・ストリーミング）: ${novelDesc.title}, " +
                "全${totalEpisodes}話, 新規取得=${totalEpisodes - resumeFrom}話")
    }

    /**
     * なろう小説を一時テーブル経由で取得する
     *
     * @param queue 処理対象のキュー
     * @param resumeFrom リトライ時の開始エピソード番号（0の場合は最初から）
     */
    private suspend fun fetchSyosetuWithTempDb(queue: RegistrationQueueEntity, resumeFrom: Int) {
        val adapter = NovelSiteAdapterFactory.getAdapter(queue.site_type) as com.shunlight_library.novel_reader.data.adapter.SyosetuAdapter

        // まず小説情報を取得
        val novelDesc = com.shunlight_library.novel_reader.api.NovelApiUtils.fetchNovelDetails(queue.ncode, queue.is_r18)
            ?: throw Exception("小説情報の取得に失敗: ${queue.ncode}")

        val updatedNovelDesc = novelDesc.copy(
            site_type = NovelSiteAdapter.SITE_TYPE_SYOSETU,
            total_ep = novelDesc.general_all_no
        )

        // 小説情報を保存（既存のregistered_atを保持）
        val existingSyosetuNovel = repository.getNovelByNcode(queue.ncode)
        val novelToSave = if (existingSyosetuNovel != null) {
            updatedNovelDesc.copy(registered_at = existingSyosetuNovel.registered_at)
        } else {
            updatedNovelDesc
        }
        repository.insertNovel(novelToSave)

        val totalEpisodes = novelDesc.general_all_no
        val startFrom = resumeFrom + 1

        AppLogger.d(TAG, "なろう小説エピソード取得開始: ${novelDesc.title}, " +
                "全${totalEpisodes}話, 開始=${startFrom}話目")

        // 1話ずつ取得→一時テーブルに保存
        for (episodeNo in startFrom..totalEpisodes) {
            val episode = com.shunlight_library.novel_reader.api.NovelApiUtils.fetchEpisodeWithRetry(
                ncode = queue.ncode,
                episodeNo = episodeNo.toString(),
                isR18 = queue.is_r18,
                noveltype = novelDesc.noveltype
            )

            if (episode != null) {
                // 一時テーブルに保存
                val tempEpisode = TempEpisodeEntity.fromEpisodeEntity(episode, queue.id)
                repository.insertTempEpisode(tempEpisode)
            } else {
                AppLogger.w(TAG, "エピソード取得失敗（スキップ）: ${queue.ncode} 第${episodeNo}話")
            }

            // 進捗通知
            scope.launch {
                updateQueueProgress(queue.id, episodeNo, totalEpisodes)
            }
        }

        AppLogger.d(TAG, "なろう小説取得完了（一時テーブル）: ${updatedNovelDesc.title}, ${totalEpisodes}話")
    }

    /**
     * 完了キューの上限管理（10個以上の場合、古いものから削除）
     */
    private fun cleanupCompletedQueues() {
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
            // title は変更しない（空文字で上書きするとDL状況画面が空欄になる）
            repository.updateRegistrationQueueTotalEpisodes(id, totalEpisodes)
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
