/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Worker performing scheduled updates.
 */
package com.shunlight_library.novel_reader.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import com.shunlight_library.novel_reader.MainActivity
import com.shunlight_library.novel_reader.NovelReaderApplication
import com.shunlight_library.novel_reader.R
import com.shunlight_library.novel_reader.SettingsStore
import com.shunlight_library.novel_reader.data.entity.UpdateQueueEntity
import com.shunlight_library.novel_reader.data.AppNotification
import com.shunlight_library.novel_reader.data.NotificationStore
import com.shunlight_library.novel_reader.data.NotificationType
import com.shunlight_library.novel_reader.data.EpisodeInfo
import com.shunlight_library.novel_reader.data.NovelDownloadInfo
import com.shunlight_library.novel_reader.data.ErrorLog
import com.shunlight_library.novel_reader.data.ErrorLogStore
import com.shunlight_library.novel_reader.worker.AutoUpdateScheduler
import com.shunlight_library.novel_reader.api.NovelApiUtils
import com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapterFactory
import com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter
import com.shunlight_library.novel_reader.data.adapter.KakuyomuAdapter
import com.shunlight_library.novel_reader.data.entity.EpisodeMappingEntity
import com.shunlight_library.novel_reader.utils.AppLogger
import com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator
import com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator
import com.shunlight_library.novel_reader.utils.ReleaseUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.GZIPInputStream

class AutoUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "novel_update_channel"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "AutoUpdateWorker"
        private const val BATCH_SIZE = 30 // 並列処理のバッチサイズ（パフォーマンス最適化）
        private const val MAX_RETRIES = 3
    }

    private val repository = NovelReaderApplication.getRepository()
    private val notificationStore = NotificationStore(context)
    private val errorLogStore = ErrorLogStore(context)
    private val settingsStore = SettingsStore(context)

    override suspend fun doWork(): Result {
        // 自動更新の開始を通知
        com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator.setAutoUpdateRunning(true)

        return try {
            Log.d(TAG, "自動更新処理開始")

            // 通知チャンネルを作成
            createNotificationChannel()

            // フォアグラウンドサービスとして実行（Android 12+ではバックグラウンド起動時に失敗する場合があるため try-catch）
            try {
                setForeground(createForegroundInfo("小説更新確認中..."))
            } catch (e: Exception) {
                Log.w(TAG, "フォアグラウンドサービス開始に失敗（バックグラウンドで継続）: ${e.message}")
            }
            AppLogger.logNotification("自動更新開始", "小説更新確認中...")

            // 更新確認処理
            val updateResults = performUpdateCheck()

            // 結果の処理と通知
            handleUpdateResults(updateResults)

            // GitHubの最新リリースを確認
            ReleaseUtils.checkForNewRelease(applicationContext)

            // 成功実行時刻を記録
            try {
                SettingsStore(applicationContext).setLastAutoUpdateRunAt(System.currentTimeMillis())
            } catch (e: Exception) {
                Log.w(TAG, "最終自動更新実行時刻の保存に失敗", e)
            }

            Log.d(TAG, "自動更新処理完了")
            Result.success()
        } catch (e: CancellationException) {
            // WorkManager による正常キャンセル（REPLACE, 10分制限等）はエラー通知しない
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "自動更新処理中にエラーが発生", e)
            val errorMsg = e.message ?: "不明なエラー"
            sendErrorNotification(errorMsg)

            try {
                val stackTraceWriter = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(stackTraceWriter))
                errorLogStore.addErrorLog(
                    ErrorLog(
                        id = "fatal_${System.currentTimeMillis()}",
                        timestamp = System.currentTimeMillis(),
                        ncode = "",
                        novelTitle = "自動更新全体",
                        episodeNo = 0,
                        episodeTitle = null,
                        errorType = "AutoUpdateFatal",
                        errorMessage = errorMsg,
                        stackTrace = stackTraceWriter.toString()
                    )
                )
            } catch (logErr: Exception) {
                Log.e(TAG, "致命的エラーのログ保存に失敗", logErr)
            }

            Result.failure()
        } finally {
            // 自動更新の終了を通知
            com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator.setAutoUpdateRunning(false)
        }
    }

    private suspend fun performUpdateCheck(): UpdateResult {
        return withContext(Dispatchers.IO) {
            var newNovelsCount = 0
            var updatedNovelsCount = 0
            val errors = mutableListOf<String>()
            val downloadDetails = mutableListOf<NovelDownloadInfo>()
            var totalSuccessEpisodes = 0
            var totalFailedEpisodes = 0

            try {
                // 自動更新開始時にupdate_queueをリセット
                repository.clearAllUpdateQueue()
                Log.d(TAG, "update_queueをリセットしました")

                // 更新対象の小説を取得（全ての小説）
                val allNovels = repository.getNovelsForUpdate()
                // 短編は新規エピソードが増えないため、設定により更新確認から除外
                val excludeShort = settingsStore.excludeShortFromUpdate.first()
                val novels = if (excludeShort) {
                    allNovels.filter { it.noveltype != 2 }
                } else {
                    allNovels
                }
                Log.d(TAG, "更新対象小説数: ${novels.size} (全${allNovels.size}件, 短編除外=$excludeShort)")

                // なろう作品はAPI一括取得（ncode OR検索）でリクエスト数を削減する
                // （1作品=1リクエスト → 最大100作品=1リクエスト）
                val syosetuNovels = novels.filter { it.site_type == NovelSiteAdapter.SITE_TYPE_SYOSETU }
                val syosetuInfoMap = HashMap<String, NovelApiUtils.NovelApiInfo>()
                try {
                    val generalNcodes = syosetuNovels.filter { it.rating != 1 }.map { it.ncode }
                    val r18Ncodes = syosetuNovels.filter { it.rating == 1 }.map { it.ncode }
                    syosetuInfoMap.putAll(NovelApiUtils.fetchNovelInfoBatch(generalNcodes, isR18 = false))
                    syosetuInfoMap.putAll(NovelApiUtils.fetchNovelInfoBatch(r18Ncodes, isR18 = true))
                    Log.d(TAG, "なろう一括取得完了: 対象${syosetuNovels.size}件中 ${syosetuInfoMap.size}件取得")
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "なろう一括取得エラー（個別取得にフォールバック）", e)
                }

                // 更新キュー（エピソード数確認の結果）
                val updateQueueItems = mutableListOf<UpdateQueueEntity>()

                // バッチ処理で並列実行（パフォーマンス最適化）
                novels.chunked(BATCH_SIZE).forEach { batch ->
                    coroutineScope {
                        val results = batch.map { novel ->
                            async {
                                var localNewCount = 0
                                var localUpdatedCount = 0
                                var error: String? = null
                                var updateQueueItem: UpdateQueueEntity? = null

                                try {
                                    // ロック機構を使用して同時実行を防ぐ
                                    val session = NovelUpdateCoordinator.beginUpdate(novel.ncode)
                                    if (session == null) {
                                        Log.d(TAG, "小説 ${novel.ncode} は既に更新処理中のためスキップ")
                                        return@async Pair(Pair(0, 0), Pair(null, null))
                                    }

                                    try {
                                        // サイト種別に応じて最新エピソード数を取得
                                        val adapter = NovelSiteAdapterFactory.getAdapter(novel.site_type)
                                        val latestEpisodeCount = when (adapter.getSiteType()) {
                                            NovelSiteAdapter.SITE_TYPE_SYOSETU -> {
                                                // 一括取得の結果を優先。取れなかった場合のみ個別取得にフォールバック
                                                val batchInfo = syosetuInfoMap[novel.ncode.lowercase()]
                                                if (batchInfo != null) {
                                                    batchInfo.generalAllNo
                                                } else {
                                                    val isR18 = novel.rating == 1
                                                    val apiInfo = NovelApiUtils.fetchNovelInfo(
                                                        ncode = novel.ncode,
                                                        isR18 = isR18
                                                    )
                                                    apiInfo?.generalAllNo ?: novel.total_ep
                                                }
                                            }
                                            NovelSiteAdapter.SITE_TYPE_KAKUYOMU -> {
                                                val kakuyomuAdapter = adapter as KakuyomuAdapter
                                                val workId = PseudoNcodeGenerator.extractKakuyomuWorkId(novel.ncode)
                                                val updateSummary = kakuyomuAdapter.fetchUpdateSummary(workId)
                                                updateSummary.latestEpisodeCount
                                            }
                                            else -> novel.total_ep
                                        }

                                        // エピソード数を比較して更新キューに追加
                                        if (latestEpisodeCount > novel.total_ep) {
                                            updateQueueItem = UpdateQueueEntity(
                                                ncode = novel.ncode,
                                                total_ep = novel.total_ep,
                                                general_all_no = latestEpisodeCount,
                                                update_time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                                            )

                                            if (novel.total_ep == 0) {
                                                localNewCount = 1
                                            } else {
                                                localUpdatedCount = 1
                                            }

                                            Log.d(TAG, "更新検出: ${novel.title} (${novel.total_ep} -> $latestEpisodeCount)")
                                        }
                                        // 更新確認日時を記録
                                        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                                        repository.updateLastCheckedAt(novel.ncode, nowStr)
                                    } finally {
                                        NovelUpdateCoordinator.finishUpdate(session)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "小説 ${novel.ncode} の更新確認エラー", e)
                                    error = "${novel.title}: ${e.message}"

                                    try {
                                        val sw = java.io.StringWriter()
                                        e.printStackTrace(java.io.PrintWriter(sw))
                                        errorLogStore.addErrorLog(
                                            ErrorLog(
                                                id = "update_${System.currentTimeMillis()}_${novel.ncode}",
                                                timestamp = System.currentTimeMillis(),
                                                ncode = novel.ncode,
                                                novelTitle = novel.title,
                                                episodeNo = 0,
                                                episodeTitle = null,
                                                errorType = "UpdateCheckError",
                                                errorMessage = e.message ?: "更新確認エラー",
                                                stackTrace = sw.toString()
                                            )
                                        )
                                    } catch (_: Exception) {}
                                }

                                Pair(Pair(localNewCount, localUpdatedCount), Pair(updateQueueItem, error))
                            }
                        }.awaitAll()

                        // 結果を集計
                        results.forEach { result ->
                            val counts = result.first
                            val itemAndError = result.second
                            val newCount = counts?.first ?: 0
                            val updatedCount = counts?.second ?: 0
                            val updateQueueItem = itemAndError?.first
                            val error = itemAndError?.second
                            newNovelsCount += newCount
                            updatedNovelsCount += updatedCount
                            updateQueueItem?.let { updateQueueItems.add(it) }
                            error?.let { errors.add(it) }
                        }
                    }
                }

                // 更新キューをデータベースに保存
                if (updateQueueItems.isNotEmpty()) {
                    updateQueueItems.forEach { item ->
                        repository.insertUpdateQueue(item)
                    }
                }

                // エピソードをダウンロード（更新があった小説のみ）
                val autoDownloadEnabled = settingsStore.autoDownloadEnabled.first()
                if (autoDownloadEnabled && updateQueueItems.isNotEmpty()) {
                    Log.d(TAG, "エピソードダウンロードを開始します: ${updateQueueItems.size}作品")

                    updateQueueItems.forEach { queueItem ->
                    try {
                        val session = NovelUpdateCoordinator.beginUpdate(queueItem.ncode)
                        if (session == null) {
                            Log.d(TAG, "小説 ${queueItem.ncode} は既にダウンロード処理中のためスキップ")
                            return@forEach
                        }

                        try {
                            val novel = repository.getNovelByNcode(queueItem.ncode)
                            if (novel == null) {
                                Log.e(TAG, "小説情報が見つかりません: ${queueItem.ncode}")
                                return@forEach
                            }

                            val downloadInfo = downloadEpisodesForNovel(novel, queueItem.total_ep, queueItem.general_all_no)
                            if (downloadInfo != null) {
                                downloadDetails.add(downloadInfo)
                                totalSuccessEpisodes += downloadInfo.successEpisodes.size
                                totalFailedEpisodes += downloadInfo.failedEpisodes.size

                                // ダウンロードしたエピソードをエラーログに保存（失敗のみ）
                                downloadInfo.failedEpisodes.forEach { failedEpisode ->
                                    errorLogStore.addErrorLog(
                                        ErrorLog(
                                            id = "error_${System.currentTimeMillis()}_${queueItem.ncode}_${failedEpisode.episodeNo}",
                                            timestamp = System.currentTimeMillis(),
                                            ncode = queueItem.ncode,
                                            novelTitle = novel.title,
                                            episodeNo = failedEpisode.episodeNo,
                                            episodeTitle = failedEpisode.title,
                                            errorType = "DownloadError",
                                            errorMessage = failedEpisode.error ?: "不明なエラー",
                                            stackTrace = null
                                        )
                                    )
                                }

                                if (downloadInfo.failedEpisodes.isEmpty()) {
                                    // 全話成功時のみ total_ep を確定し更新キューから削除
                                    val updatedNovel = novel.copy(total_ep = queueItem.general_all_no)
                                    repository.updateNovel(updatedNovel)
                                    repository.deleteUpdateQueueByNcode(queueItem.ncode)
                                } else {
                                    // 一部失敗: 実際に保存された最大話数を total_ep に反映し、
                                    // キューを残して次回の更新確認でリトライ可能にする
                                    val maxSavedEp = repository.getEpisodesByNcode(novel.ncode).first()
                                        .mapNotNull { it.episode_no.toIntOrNull() }.maxOrNull() ?: novel.total_ep
                                    repository.updateNovel(novel.copy(total_ep = maxSavedEp))
                                    if (maxSavedEp >= queueItem.general_all_no) {
                                        // 末尾まで保存済み（途中の欠番はエラー修正で再取得可能）
                                        repository.deleteUpdateQueueByNcode(queueItem.ncode)
                                    }
                                    Log.w(TAG, "自動DLで${downloadInfo.failedEpisodes.size}件失敗: ${queueItem.ncode} (保存済み最大話数: $maxSavedEp)")
                                }
                            }
                        } finally {
                            NovelUpdateCoordinator.finishUpdate(session)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "エピソードダウンロードエラー: ${queueItem.ncode}", e)
                        errors.add("${queueItem.ncode}: ダウンロードエラー - ${e.message}")

                        try {
                            val sw = java.io.StringWriter()
                            e.printStackTrace(java.io.PrintWriter(sw))
                            errorLogStore.addErrorLog(
                                ErrorLog(
                                    id = "dl_${System.currentTimeMillis()}_${queueItem.ncode}",
                                    timestamp = System.currentTimeMillis(),
                                    ncode = queueItem.ncode,
                                    novelTitle = repository.getNovelByNcode(queueItem.ncode)?.title ?: queueItem.ncode,
                                    episodeNo = 0,
                                    episodeTitle = null,
                                    errorType = "DownloadBatchError",
                                    errorMessage = "エピソードダウンロード全体エラー: ${e.message}",
                                    stackTrace = sw.toString()
                                )
                            )
                        } catch (_: Exception) {}
                    }
                }
                } else {
                    Log.d(TAG, "自動ダウンロードが無効のため、エピソードのダウンロードをスキップしました")
                }

            } catch (e: Exception) {
                Log.e(TAG, "更新確認処理全体でエラー", e)
                errors.add("全体処理エラー: ${e.message}")
            }

            UpdateResult(newNovelsCount, updatedNovelsCount, errors, downloadDetails, totalSuccessEpisodes, totalFailedEpisodes)
        }
    }

    private suspend fun downloadEpisodesForNovel(
        novel: com.shunlight_library.novel_reader.data.entity.NovelDescEntity,
        startEpisodeNo: Int,
        endEpisodeNo: Int
    ): NovelDownloadInfo? {
        val successEpisodes = mutableListOf<EpisodeInfo>()
        val failedEpisodes = mutableListOf<EpisodeInfo>()

        val episodeNos = (startEpisodeNo + 1)..endEpisodeNo
        Log.d(TAG, "「${novel.title}」のエピソードをダウンロード: ${episodeNos.first} - ${episodeNos.last}")

        try {
            if (novel.site_type == NovelSiteAdapter.SITE_TYPE_KAKUYOMU) {
                downloadKakuyomuEpisodes(novel, episodeNos, successEpisodes, failedEpisodes)
            } else {
                downloadSyosetuEpisodes(novel, episodeNos, successEpisodes, failedEpisodes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "「${novel.title}」のダウンロード中にエラー", e)
        }

        return NovelDownloadInfo(
            ncode = novel.ncode,
            title = novel.title,
            successEpisodes = successEpisodes,
            failedEpisodes = failedEpisodes
        )
    }

    private suspend fun downloadSyosetuEpisodes(
        novel: com.shunlight_library.novel_reader.data.entity.NovelDescEntity,
        episodeNos: IntRange,
        successEpisodes: MutableList<EpisodeInfo>,
        failedEpisodes: MutableList<EpisodeInfo>
    ) {
        val isR18 = novel.rating == 1

        episodeNos.forEach { episodeNo ->
            var success = false
            var errorMessage: String? = null

            for (retry in 1..MAX_RETRIES) {
                try {
                    val episode = NovelApiUtils.fetchEpisodeWithRetry(
                        ncode = novel.ncode,
                        episodeNo = episodeNo.toString(),
                        isR18 = isR18,
                        noveltype = novel.noveltype
                    )

                    if (episode != null) {
                        repository.insertEpisode(episode)
                        successEpisodes.add(EpisodeInfo(
                            episodeNo = episodeNo,
                            title = episode.e_title ?: "第${episodeNo}話"
                        ))
                        success = true
                        break
                    }
                } catch (e: Exception) {
                    errorMessage = e.message
                    Log.w(TAG, "エピソード ${episodeNo} のダウンロード失敗 (試行 ${retry}/${MAX_RETRIES})", e)
                    if (retry < MAX_RETRIES) {
                        kotlinx.coroutines.delay((1000 * retry).toLong()) // 指数バックオフ: 1秒、2秒、4秒
                    }
                }
            }

            if (!success) {
                failedEpisodes.add(EpisodeInfo(
                    episodeNo = episodeNo,
                    title = "第${episodeNo}話",
                    error = errorMessage ?: "ダウンロードに失敗しました"
                ))
            }

            kotlinx.coroutines.delay(200) // サーバー負荷軽減
        }
    }

    private suspend fun downloadKakuyomuEpisodes(
        novel: com.shunlight_library.novel_reader.data.entity.NovelDescEntity,
        episodeNos: IntRange,
        successEpisodes: MutableList<EpisodeInfo>,
        failedEpisodes: MutableList<EpisodeInfo>
    ) {
        val workId = PseudoNcodeGenerator.extractKakuyomuWorkId(novel.ncode)
        val kakuyomuAdapter = NovelSiteAdapterFactory.getAdapter(NovelSiteAdapter.SITE_TYPE_KAKUYOMU) as KakuyomuAdapter

        // 並列更新確認処理でcachedMappingsが上書きされている可能性があるため、
        // ダウンロード前に当該作品のエピソードリストを再取得して正確なマッピングを得る
        val episodeListFromWeb: List<com.shunlight_library.novel_reader.data.entity.EpisodeEntity>
        try {
            val (_, fetchedEpisodes) = kakuyomuAdapter.fetchNovelMetadataWithEpisodeList(workId)
            episodeListFromWeb = fetchedEpisodes
            Log.d(TAG, "カクヨムエピソードリスト再取得完了: ${novel.ncode}, ${fetchedEpisodes.size}話")
        } catch (e: Exception) {
            Log.e(TAG, "カクヨムエピソードリスト再取得エラー: ${novel.ncode}", e)

            try {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                errorLogStore.addErrorLog(
                    ErrorLog(
                        id = "list_${System.currentTimeMillis()}_${novel.ncode}",
                        timestamp = System.currentTimeMillis(),
                        ncode = novel.ncode,
                        novelTitle = novel.title,
                        episodeNo = 0,
                        episodeTitle = null,
                        errorType = "EpisodeListError",
                        errorMessage = "エピソードリスト取得失敗: ${e.message}",
                        stackTrace = sw.toString()
                    )
                )
            } catch (_: Exception) {}

            // 再取得失敗時は全エピソードをエラーとして記録
            episodeNos.forEach { episodeNo ->
                failedEpisodes.add(EpisodeInfo(
                    episodeNo = episodeNo,
                    title = "第${episodeNo}話",
                    error = "エピソードリスト取得失敗: ${e.message}"
                ))
            }
            return
        }

        // エピソード番号→IDとタイトルのマッピングを構築
        val mappings = kakuyomuAdapter.getCachedMappings()
        val episodeTitles = episodeListFromWeb.associate {
            it.episode_no.toIntOrNull() to (it.e_title ?: "第${it.episode_no}話")
        }

        // episode_mapping テーブルに最新マッピングを保存（CLAUDE.md ルール14）
        if (mappings.isNotEmpty()) {
            try {
                val mappingEntities = mappings.map { (episodeNo, kakuyomuId) ->
                    EpisodeMappingEntity(
                        ncode = novel.ncode,
                        episode_no = episodeNo,
                        kakuyomu_episode_id = kakuyomuId
                    )
                }
                repository.insertEpisodeMappings(mappingEntities)
                Log.d(TAG, "episode_mapping 保存完了: ${novel.ncode}, ${mappingEntities.size}件")
            } catch (e: Exception) {
                Log.w(TAG, "episode_mapping 保存失敗: ${novel.ncode}", e)
            }
        }

        episodeNos.forEach { episodeNo ->
            var success = false
            var errorMessage: String? = null

            val kakuyomuEpisodeId = mappings[episodeNo]
            if (kakuyomuEpisodeId == null) {
                Log.e(TAG, "カクヨムエピソードIDが見つかりません: ${novel.ncode} エピソード${episodeNo}")
                failedEpisodes.add(EpisodeInfo(
                    episodeNo = episodeNo,
                    title = "第${episodeNo}話",
                    error = "エピソードIDが見つかりません"
                ))
                return@forEach
            }

            val episodeTitle = episodeTitles[episodeNo] ?: "第${episodeNo}話"

            for (retry in 1..MAX_RETRIES) {
                try {
                    val episodeBody = kakuyomuAdapter.fetchEpisodeContent(workId, kakuyomuEpisodeId)

                    if (episodeBody.isNotEmpty() && !episodeBody.startsWith("★HTMLページ読み込みエラー")) {
                        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                        val episode = com.shunlight_library.novel_reader.data.entity.EpisodeEntity(
                            ncode = novel.ncode,
                            episode_no = episodeNo.toString(),
                            e_title = episodeTitle,
                            body = episodeBody,
                            update_time = currentTime,
                            is_read = 0,
                            is_bookmark = 0,
                            reading_rate = 0f
                        )
                        repository.insertEpisode(episode)
                        successEpisodes.add(EpisodeInfo(
                            episodeNo = episodeNo,
                            title = episodeTitle
                        ))
                        success = true
                        break
                    }
                } catch (e: Exception) {
                    errorMessage = e.message
                    Log.w(TAG, "カクヨム エピソード ${episodeNo} のダウンロード失敗 (試行 ${retry}/${MAX_RETRIES})", e)
                    if (retry < MAX_RETRIES) {
                        kotlinx.coroutines.delay((1000 * retry).toLong()) // 指数バックオフ
                    }
                }
            }

            if (!success) {
                failedEpisodes.add(EpisodeInfo(
                    episodeNo = episodeNo,
                    title = episodeTitle,
                    error = errorMessage ?: "ダウンロードに失敗しました"
                ))
            }

            kotlinx.coroutines.delay(500) // カクヨムのレート制限を考慮
        }
    }

    private suspend fun handleUpdateResults(results: UpdateResult) {
        val totalUpdates = results.newNovelsCount + results.updatedNovelsCount

        if (totalUpdates > 0 || results.downloadDetails.isNotEmpty()) {
            // システム通知を送信
            sendSystemNotification(results)

            // アプリ内通知データを保存（次回アプリ起動時に表示）
            saveAppNotification(results)
        }

        Log.d(TAG, "更新結果: 新規${results.newNovelsCount}件、更新${results.updatedNovelsCount}件、DL成功${results.totalSuccessEpisodes}話、DL失敗${results.totalFailedEpisodes}話")
    }

    private fun sendSystemNotification(results: UpdateResult) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val totalUpdates = results.newNovelsCount + results.updatedNovelsCount
        val title = "自動更新完了"
        val content = buildString {
            if (totalUpdates > 0) {
                if (results.newNovelsCount > 0) {
                    append("新規${results.newNovelsCount}作品")
                }
                if (results.updatedNovelsCount > 0) {
                    if (results.newNovelsCount > 0) append("、")
                    append("更新${results.updatedNovelsCount}作品")
                }
                append("の")
            }
            append("${results.totalSuccessEpisodes}話をDLしました")
            if (results.totalFailedEpisodes > 0) {
                append("（失敗${results.totalFailedEpisodes}話）")
            }
        }

        // アプリを開くIntentを作成
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        AppLogger.logNotification(title, content)
    }

    private suspend fun saveAppNotification(results: UpdateResult) {
        val totalUpdates = results.newNovelsCount + results.updatedNovelsCount

        if (totalUpdates > 0 || results.downloadDetails.isNotEmpty()) {
            val title = "自動更新完了"
            val content = buildString {
                if (totalUpdates > 0) {
                    if (results.newNovelsCount > 0) {
                        append("新規${results.newNovelsCount}作品")
                    }
                    if (results.updatedNovelsCount > 0) {
                        if (results.newNovelsCount > 0) append("、")
                        append("更新${results.updatedNovelsCount}作品")
                    }
                    append("の")
                }
                append("${results.totalSuccessEpisodes}話をDLしました")
                if (results.totalFailedEpisodes > 0) {
                    append("（失敗${results.totalFailedEpisodes}話）")
                }
                if (results.errors.isNotEmpty()) {
                    append("\n\n※一部の作品で取得エラーが発生しました")
                }
            }

            val notification = AppNotification(
                id = "auto_update_${System.currentTimeMillis()}",
                title = title,
                content = content,
                timestamp = System.currentTimeMillis(),
                type = NotificationType.UPDATE,
                downloadDetails = if (results.downloadDetails.isNotEmpty()) results.downloadDetails else null
            )

            notificationStore.addNotification(notification)
            AppLogger.logNotification(notification.title, notification.content)
        }

        // エラーのみの場合も通知
        if (totalUpdates == 0 && results.downloadDetails.isEmpty() && results.errors.isNotEmpty()) {
            val notification = AppNotification(
                id = "auto_update_error_${System.currentTimeMillis()}",
                title = "自動更新エラー",
                content = "更新確認中にエラーが発生しました。ネットワーク接続を確認してください。",
                timestamp = System.currentTimeMillis(),
                type = NotificationType.ERROR
            )

            notificationStore.addNotification(notification)
        }
    }

    private suspend fun sendErrorNotification(message: String) {
        createNotificationChannel()

        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("自動更新エラー")
            .setContentText("更新処理中にエラーが発生しました: $message")
            .setPriority(NotificationCompat.PRIORITY_HIGH)  // プッシュ通知として表示
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID + 1, notification)

        val appNotification = AppNotification(
            id = "auto_update_error_${System.currentTimeMillis()}",
            title = "自動更新エラー",
            content = "更新処理中にエラーが発生しました: $message",
            timestamp = System.currentTimeMillis(),
            type = NotificationType.ERROR
        )
            notificationStore.addNotification(appNotification)
            AppLogger.logNotification(appNotification.title, appNotification.content)
    }

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        createNotificationChannel()
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("小説リーダー 自動更新")
            .setContentText(progress)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID + 100,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID + 100, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "小説更新通知"
            val descriptionText = "小説の更新を通知します"
            val importance = NotificationManager.IMPORTANCE_HIGH  // プッシュ通知として表示
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)  // バイブレーション有効化
                enableLights(true)     // LED通知有効化
            }

            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    data class UpdateResult(
        val newNovelsCount: Int,
        val updatedNovelsCount: Int,
        val errors: List<String>,
        val downloadDetails: List<NovelDownloadInfo>,
        val totalSuccessEpisodes: Int,
        val totalFailedEpisodes: Int
    )
}
