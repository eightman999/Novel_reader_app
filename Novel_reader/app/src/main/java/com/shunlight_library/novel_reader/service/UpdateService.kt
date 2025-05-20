// UpdateService.kt
package com.shunlight_library.novel_reader.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.shunlight_library.novel_reader.MainActivity
import com.shunlight_library.novel_reader.NovelReaderApplication
import com.shunlight_library.novel_reader.R
import com.shunlight_library.novel_reader.api.NovelApiUtils
import com.shunlight_library.novel_reader.data.entity.UpdateQueueEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

/**
 * 小説の更新処理をバックグラウンドで実行するためのForegroundサービス
 */
class UpdateService : Service() {
    companion object {
        private const val TAG = "UpdateService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "update_service_channel"
        private const val CHANNEL_NAME = "Update Service Channel"

        // Action constants for intent filtering
        const val ACTION_START_UPDATE = "com.shunlight_library.novel_reader.START_UPDATE"
        const val ACTION_STOP_UPDATE = "com.shunlight_library.novel_reader.STOP_UPDATE"

        // Extra keys for intent data
        const val EXTRA_NCODE = "ncode"
        const val EXTRA_UPDATE_TYPE = "update_type"

        // Update types
        const val UPDATE_TYPE_CHECK = 1
        const val UPDATE_TYPE_DOWNLOAD = 2
        const val UPDATE_TYPE_FIX_ERRORS = 3
        const val UPDATE_TYPE_BULK_UPDATE = 4
    }

    // Service binding
    private val binder = UpdateBinder()

    // Coroutine scopes
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Repository
    private val repository by lazy { NovelReaderApplication.getRepository() }

    // Progress tracking
    private var progress = 0f
    private var message = ""
    private var isRunning = false
    private var updateType = 0
    private var currentNcode = ""
    private var updateListeners = mutableListOf<UpdateProgressListener>()

    // Interface for progress updates
    interface UpdateProgressListener {
        fun onProgressUpdate(progress: Float, message: String)
        fun onUpdateComplete(success: Boolean, resultMessage: String)
    }

    // Binder class for local binding
    inner class UpdateBinder : Binder() {
        fun getService(): UpdateService = this@UpdateService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_UPDATE -> {
                val ncode = intent.getStringExtra(EXTRA_NCODE) ?: ""
                updateType = intent.getIntExtra(EXTRA_UPDATE_TYPE, UPDATE_TYPE_CHECK)

                if (!isRunning) {
                    isRunning = true
                    currentNcode = ncode

                    // 通知の作成
                    val notification = createNotification("更新処理を開始しています...")

                    // Android 12以降ではフォアグラウンドサービスタイプを指定
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } else {
                        // 以前のバージョン用
                        startForeground(NOTIFICATION_ID, notification)
                    }

                    // Start update based on type
                    when (updateType) {
                        UPDATE_TYPE_CHECK -> checkForUpdates(ncode)
                        UPDATE_TYPE_DOWNLOAD -> downloadEpisodes(ncode)
                        UPDATE_TYPE_FIX_ERRORS -> fixEpisodeErrors(ncode)
                        UPDATE_TYPE_BULK_UPDATE -> performBulkUpdate()
                    }
                }
            }
            ACTION_STOP_UPDATE -> {
                stopUpdate()
            }
        }

        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    fun addUpdateListener(listener: UpdateProgressListener) {
        updateListeners.add(listener)
        // Send current progress immediately to the new listener
        if (isRunning) {
            listener.onProgressUpdate(progress, message)
        }
    }

    fun removeUpdateListener(listener: UpdateProgressListener) {
        updateListeners.remove(listener)
    }

    private fun updateProgress(newProgress: Float, newMessage: String) {
        progress = newProgress
        message = newMessage

        // Update notification
        val notification = createNotification(newMessage)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)

        // Notify listeners
        updateListeners.forEach { it.onProgressUpdate(newProgress, newMessage) }
    }

    private fun updateComplete(success: Boolean, resultMessage: String) {
        isRunning = false
        updateListeners.forEach { it.onUpdateComplete(success, resultMessage) }

        // Update notification one last time
        val finalMessage = if (success) "更新処理が完了しました" else "更新処理に失敗しました"
        val notification = createNotification(finalMessage)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)

        // Stop foreground service after a delay
        serviceScope.launch {
            delay(3000) // Give notification time to be seen
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }
    }

    private fun stopUpdate() {
        if (isRunning) {
            // Cancel ongoing operations
            isRunning = false
            updateComplete(false, "更新処理がキャンセルされました")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "小説の更新処理を実行中に表示される通知"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
        }

        // Action to stop the update
        val stopIntent = Intent(this, UpdateService::class.java).apply {
            action = ACTION_STOP_UPDATE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("小説更新サービス")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with appropriate icon
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPendingIntent)
            .setProgress(100, (progress * 100).toInt(), progress == 0f)
            .setOngoing(true)
            .build()
    }

    // Implementation of update methods
    // UpdateService.kt の checkForUpdates メソッドを修正
    private fun checkForUpdates(ncode: String) {
        serviceScope.launch {
            try {
                updateProgress(0.1f, "APIで最新情報を確認中...")

                // Get current novel info
                val novel = repository.getNovelByNcode(ncode)
                if (novel == null) {
                    updateComplete(false, "小説情報が見つかりませんでした")
                    return@launch
                }

                // URLEntityを取得または作成
                val urlEntity = repository.getOrCreateURL(ncode, novel.rating == 1)

                // Fetch latest info from API
                val (newGeneralAllNo, newUpdatedAt) = NovelApiUtils.fetchNovelInfo(
                    ncode = ncode,
                    isR18 = novel.rating == 1,
                    apiUrl = urlEntity.api_url
                )

                if (newGeneralAllNo == -1) {
                    updateComplete(false, "APIからデータが取得できませんでした")
                    return@launch
                }

                // Check if update is available
                if (newGeneralAllNo > novel.general_all_no) {
                    // Update novel info
                    val updatedNovel = novel.copy(
                        general_all_no = newGeneralAllNo,
                        updated_at = newUpdatedAt
                    )
                    repository.updateNovel(updatedNovel)

                    // Add to update queue
                    val updateQueue = UpdateQueueEntity(
                        ncode = novel.ncode,
                        total_ep = novel.total_ep,
                        general_all_no = newGeneralAllNo,
                        update_time = newUpdatedAt
                    )
                    repository.insertUpdateQueue(updateQueue)

                    updateComplete(true, "更新が見つかりました。更新キューに追加しました。")
                } else {
                    updateComplete(true, "この小説に更新はありません")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check error", e)
                updateComplete(false, "エラー: ${e.message}")
            }
        }
    }
    private fun downloadEpisodes(ncode: String) {
        serviceScope.launch {
            try {
                updateProgress(0.1f, "小説情報を取得中...")

                // Get novel info
                val novel = repository.getNovelByNcode(ncode)
                if (novel == null) {
                    updateComplete(false, "小説情報が見つかりませんでした")
                    return@launch
                }

                updateProgress(0.2f, "APIで最新情報を確認中...")

                // Get latest info from API
                val (newGeneralAllNo, newUpdatedAt) = NovelApiUtils.fetchNovelInfo(ncode, novel.rating == 1)

                var generalAllNoValue = newGeneralAllNo
                if (generalAllNoValue == -1) {
                    generalAllNoValue = novel.general_all_no
                }

                // Start downloading episodes
                updateProgress(0.3f, "エピソードを取得中... (0/$generalAllNoValue)")

                var successCount = 0
                var failCount = 0

                // Get episodes to download
                val startEpisode = novel.total_ep + 1
                val episodesToDownload = (startEpisode..generalAllNoValue).toList()

                if (episodesToDownload.isEmpty()) {
                    updateComplete(true, "ダウンロードするエピソードがありません")
                    return@launch
                }

                for ((index, episodeNo) in episodesToDownload.withIndex()) {
                    // Check if service is still running
                    if (!isRunning) break

                    val episode = NovelApiUtils.fetchEpisode(novel.ncode, episodeNo, novel.rating == 1)

                    if (episode != null) {
                        repository.insertEpisode(episode)
                        successCount++
                    } else {
                        failCount++
                    }

                    // Update progress
                    val progress = (index + 1).toFloat() / episodesToDownload.size
                    updateProgress(0.3f + (0.7f * progress), "エピソードを取得中... (${index + 1}/${episodesToDownload.size})")

                    // Avoid server overload
                    delay(200)
                }

                // Update novel total_ep value
                if (successCount > 0) {
                    val updatedNovel = novel.copy(
                        total_ep = novel.total_ep + successCount,
                        general_all_no = generalAllNoValue,
                        updated_at = newUpdatedAt
                    )
                    repository.updateNovel(updatedNovel)

                    // Remove from update queue if all episodes downloaded
                    if (updatedNovel.total_ep >= generalAllNoValue) {
                        repository.deleteUpdateQueueByNcode(ncode)
                    }
                }

                updateComplete(true, "完了: 成功${successCount}件、失敗${failCount}件")
            } catch (e: Exception) {
                Log.e(TAG, "Download episodes error", e)
                updateComplete(false, "エラー: ${e.message}")
            }
        }
    }

    private fun fixEpisodeErrors(ncode: String) {
        serviceScope.launch {
            try {
                updateProgress(0.1f, "エピソードをチェック中...")

                // Get novel info
                val novel = repository.getNovelByNcode(ncode)
                if (novel == null) {
                    updateComplete(false, "小説情報が見つかりませんでした")
                    return@launch
                }

                // Get episodes
                val episodes = repository.getEpisodesByNcode(ncode).first()

                // Find episodes with errors
                val errorEpisodes = episodes.filter { episode ->
                    episode.body.isEmpty() || episode.e_title.isEmpty()
                }

                updateProgress(0.2f, "APIで最新情報を確認中...")

                // Get latest info from API
                val (newGeneralAllNo, _) = NovelApiUtils.fetchNovelInfo(ncode, novel.rating == 1)

                var generalAllNoValue = newGeneralAllNo
                if (generalAllNoValue == -1) {
                    generalAllNoValue = novel.general_all_no
                }

                // Find missing episodes
                val episodeNumberMap = episodes.associate { episode ->
                    val numericValue = episode.episode_no.toIntOrNull() ?: 0
                    numericValue to episode.episode_no
                }

                val maxEpisodeNo = episodeNumberMap.keys.maxOrNull() ?: 0
                val checkRangeMax = maxOf(generalAllNoValue, maxEpisodeNo)
                val missingEpisodes = (1..checkRangeMax).filter { epNo ->
                    !episodeNumberMap.containsKey(epNo)
                }

                // Combine error and missing episodes
                val errorEpisodeNumbers = errorEpisodes.mapNotNull { it.episode_no.toIntOrNull() }
                val redownloadTargets = (errorEpisodeNumbers + missingEpisodes).distinct().sorted()

                if (redownloadTargets.isEmpty()) {
                    updateComplete(true, "エラーや欠番は見つかりませんでした")
                    return@launch
                }

                updateProgress(0.3f, "エラーまたは欠番のあるエピソードを再取得中... (0/${redownloadTargets.size})")

                var successCount = 0
                var failCount = 0

                // Download error episodes
                for ((index, episodeNo) in redownloadTargets.withIndex()) {
                    // Check if service is still running
                    if (!isRunning) break

                    val episode = NovelApiUtils.fetchEpisode(novel.ncode, episodeNo, novel.rating == 1)

                    if (episode != null) {
                        repository.insertEpisode(episode)
                        successCount++
                    } else {
                        failCount++
                    }

                    // Update progress
                    val progress = (index + 1).toFloat() / redownloadTargets.size
                    updateProgress(0.3f + (0.7f * progress), "エラーまたは欠番のあるエピソードを再取得中... (${index + 1}/${redownloadTargets.size})")


                }

                // Update novel total_ep value if needed
                val updatedEpisodes = repository.getEpisodesByNcode(novel.ncode).first()
                val maxEpisodeNoAfterFix = updatedEpisodes.mapNotNull { it.episode_no.toIntOrNull() }.maxOrNull() ?: 0

                if (maxEpisodeNoAfterFix > novel.total_ep) {
                    val updatedNovel = novel.copy(total_ep = maxEpisodeNoAfterFix)
                    repository.updateNovel(updatedNovel)
                }

                updateComplete(true, "完了: 成功${successCount}件、失敗${failCount}件")
            } catch (e: Exception) {
                Log.e(TAG, "Fix episodes error", e)
                updateComplete(false, "エラー: ${e.message}")
            }
        }
    }

    private fun performBulkUpdate() {
        serviceScope.launch {
            try {
                updateProgress(0.1f, "更新キューを取得中...")

                // Get update queue
                val updateQueue = repository.getAllUpdateQueue()

                if (updateQueue.isEmpty()) {
                    updateComplete(true, "更新キューが空です")
                    return@launch
                }

                updateProgress(0.2f, "更新対象を計算中...")

                // Count total episodes to download
                var totalEpisodes = 0
                updateQueue.forEach { queueItem ->
                    val novel = repository.getNovelByNcode(queueItem.ncode)
                    if (novel != null) {
                        val episodesToDownload = queueItem.general_all_no - novel.total_ep
                        if (episodesToDownload > 0) {
                            totalEpisodes += episodesToDownload
                        }
                    }
                }

                if (totalEpisodes == 0) {
                    updateComplete(true, "ダウンロードするエピソードがありません")
                    return@launch
                }

                updateProgress(0.3f, "エピソードをダウンロード中... (0/$totalEpisodes)")

                var processedEpisodes = 0
                var successCount = 0
                var failCount = 0

                // Process each queue item
                for (queueItem in updateQueue) {
                    // Check if service is still running
                    if (!isRunning) break

                    val novel = repository.getNovelByNcode(queueItem.ncode)
                    if (novel == null) {
                        continue
                    }

                    // Update progress with current novel info
                    updateProgress(
                        0.3f + (0.7f * processedEpisodes.toFloat() / totalEpisodes),
                        "「${novel.title}」のエピソードをダウンロード中..."
                    )

                    val startEpisode = novel.total_ep + 1
                    val endEpisode = queueItem.general_all_no

                    if (startEpisode <= endEpisode) {
                        val episodesToDownload = (startEpisode..endEpisode).toList()

                        for (episodeNo in episodesToDownload) {
                            // Check if service is still running
                            if (!isRunning) break

                            val episode = NovelApiUtils.fetchEpisode(novel.ncode, episodeNo, novel.rating == 1)

                            if (episode != null) {
                                repository.insertEpisode(episode)
                                successCount++
                            } else {
                                failCount++
                            }

                            processedEpisodes++

                            // Update progress
                            updateProgress(
                                0.3f + (0.7f * processedEpisodes.toFloat() / totalEpisodes),
                                "エピソードをダウンロード中... ($processedEpisodes/$totalEpisodes)"
                            )

                            // Avoid server overload
                            delay(200)
                        }

                        // Update novel info
                        val updatedNovel = novel.copy(
                            total_ep = endEpisode,
                            general_all_no = endEpisode
                        )
                        repository.updateNovel(updatedNovel)

                        // Remove from update queue
                        repository.deleteUpdateQueueByNcode(queueItem.ncode)
                    }
                }

                updateComplete(true, "完了: 成功${successCount}件、失敗${failCount}件")
            } catch (e: Exception) {
                Log.e(TAG, "Bulk update error", e)
                updateComplete(false, "エラー: ${e.message}")
            }
        }
    }
}