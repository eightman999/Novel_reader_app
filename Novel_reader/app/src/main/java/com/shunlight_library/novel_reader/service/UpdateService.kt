/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Foreground service performing manual updates.
 */
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
import android.app.AlarmManager
import androidx.core.app.NotificationCompat
import com.shunlight_library.novel_reader.MainActivity
import com.shunlight_library.novel_reader.NovelReaderApplication
import com.shunlight_library.novel_reader.R
import com.shunlight_library.novel_reader.api.NovelApiUtils
import com.shunlight_library.novel_reader.data.adapter.KakuyomuAdapter
import com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter
import com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapterFactory
import com.shunlight_library.novel_reader.data.entity.EpisodeMappingEntity
import com.shunlight_library.novel_reader.data.entity.UpdateQueueEntity
import com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator
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
        const val UPDATE_TYPE_CHECK_REVISION = 5
    }

    // Service binding
    private val binder = UpdateBinder()

    // オペレーションキュー
    private val operationQueue = ArrayDeque<UpdateOperation>()
    private data class UpdateOperation(
        val ncode: String,
        val updateType: Int
    )

    // 更新タイプ名のマップ
    private val updateTypeNames = mapOf(
        UPDATE_TYPE_CHECK to "更新チェック",
        UPDATE_TYPE_DOWNLOAD to "再ダウンロード",
        UPDATE_TYPE_FIX_ERRORS to "エラー修正",
        UPDATE_TYPE_BULK_UPDATE to "一括更新",
        UPDATE_TYPE_CHECK_REVISION to "改稿チェック"
    )

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
    private var currentUpdateSession: NovelUpdateCoordinator.UpdateSession? = null
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
                val requestedUpdateType = intent.getIntExtra(EXTRA_UPDATE_TYPE, UPDATE_TYPE_CHECK)

                if (!isRunning) {
                    isRunning = true

                    // キューに追加してから処理を開始（空キューで即完了するバグを防ぐ）
                    operationQueue.add(UpdateOperation(ncode, requestedUpdateType))

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

                    // キューから最初のオペレーションを取り出して実行
                    processNextOperation()
                } else {
                    // 実行中の場合もキューに追加（次に処理される）
                    operationQueue.add(UpdateOperation(ncode, requestedUpdateType))
                    updateNotificationWithQueueCount()
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

    private fun processNextOperation() {
        if (operationQueue.isEmpty()) {
            // 全てのオペレーション完了
            isRunning = false
            currentNcode = ""
            updateType = 0

            // 最終通知
            val notification = createNotification("全ての更新処理が完了しました")
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)

            // フォアグラウンドサービス停止
            serviceScope.launch {
                delay(3000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    stopForeground(Service.STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
            return
        }

        // 次のオペレーションを取得
        val operation = operationQueue.removeFirst()
        currentNcode = operation.ncode
        updateType = operation.updateType

        // 通知更新（詳細表示）
        updateNotificationWithQueueCount()

        // 適切な処理を開始
        when (updateType) {
            UPDATE_TYPE_CHECK -> checkForUpdates(operation.ncode)
            UPDATE_TYPE_DOWNLOAD -> downloadEpisodes(operation.ncode)
            UPDATE_TYPE_FIX_ERRORS -> fixEpisodeErrors(operation.ncode)
            UPDATE_TYPE_BULK_UPDATE -> performBulkUpdate()
            UPDATE_TYPE_CHECK_REVISION -> checkRevision(operation.ncode)
        }
    }

    private fun updateNotificationWithQueueCount() {
        val queueCount = operationQueue.size

        // 現在処理中のタイプ名を取得
        val currentTypeName = updateTypeNames[updateType] ?: "更新処理"

        // 残りのオペレーションをタイプごとに集計
        val remainingTypeCounts = operationQueue
            .groupBy { it.updateType }
            .mapValues { (_, ops) -> ops.size }

        val contentText = if (queueCount > 0) {
            // 詳細表示を作成
            val remainingDetails = remainingTypeCounts.entries
                .sortedByDescending { it.value }
                .joinToString(", ") { (type, count) ->
                    val typeName = updateTypeNames[type] ?: "更新処理"
                    "${typeName}×${count}"
                }
            "${currentTypeName}中...（残り${queueCount}件：$remainingDetails）"
        } else {
            "${currentTypeName}中..."
        }

        val notification = createNotification(contentText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
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
        currentUpdateSession?.let {
            NovelUpdateCoordinator.finishUpdate(it)
            currentUpdateSession = null
        }
        updateListeners.forEach { it.onUpdateComplete(success, resultMessage) }

        // エラーの場合、2秒待機
        if (!success) {
            serviceScope.launch {
                delay(2000)
                isRunning = true
                processNextOperation()
            }
        } else {
            isRunning = true
            processNextOperation()
        }
    }

    private fun stopUpdate() {
        if (isRunning) {
            currentUpdateSession?.cancel()
            val ncodeToCancel = currentNcode
            if (ncodeToCancel.isNotEmpty()) {
                serviceScope.launch {
                    NovelUpdateCoordinator.cancelAndWait(ncodeToCancel)
                }
            }
            isRunning = false
        }
    }

    // 403エラー判定ヘルパーメソッド
    private fun is403Error(exception: Exception): Boolean {
        return when (exception) {
            is java.net.SocketException -> exception.message?.contains("403", ignoreCase = true) == true
            is java.net.UnknownHostException -> false
            else -> {
                // スタックトレースから403を検索
                val stackTrace = exception.stackTraceToString()
                stackTrace.contains("403", ignoreCase = true) ||
                exception.message?.contains("403", ignoreCase = true) == true ||
                (exception.cause?.message?.contains("403", ignoreCase = true) == true)
            }
        }
    }

    // 403エラー時の再開処理
    private fun handle403Error(ncode: String, updateType: Int) {
        Log.w(TAG, "403エラー検出: $ncode, 10分後に再開します")

        // 10分後にオペレーションを再キュー
        val intent = Intent(this, UpdateService::class.java).apply {
            action = ACTION_START_UPDATE
            putExtra(EXTRA_NCODE, ncode)
            putExtra(EXTRA_UPDATE_TYPE, updateType)
        }

        val pendingIntent = PendingIntent.getService(
            this,
            ncode.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAtMillis = System.currentTimeMillis() + (10 * 60 * 1000) // 10分後

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }

        // 通知で403エラーを通知
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("403エラー発生")
            .setContentText("アクセス制限がかかりました。10分後に自動的に再開します。")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(NOTIFICATION_ID + 1, notification)

        // 次のオペレーションへ（2秒待機）
        serviceScope.launch {
            delay(2000)
            isRunning = true
            processNextOperation()
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
            val session = NovelUpdateCoordinator.beginUpdate(ncode)
            if (session == null) {
                updateComplete(false, "この小説はすでに更新処理中です")
                return@launch
            }

            currentUpdateSession = session
            currentNcode = ncode

            try {
                updateProgress(0.1f, "APIで最新情報を確認中...")

                if (!isRunning || session.isCancelled()) {
                    updateComplete(false, "更新処理が中断されました")
                    return@launch
                }

                // Get current novel info
                val novel = repository.getNovelByNcode(ncode)
                if (novel == null) {
                    updateComplete(false, "小説情報が見つかりませんでした")
                    return@launch
                }

                if (!isRunning || session.isCancelled()) {
                    updateComplete(false, "更新処理が中断されました")
                    return@launch
                }

                // サイトタイプに応じて最新情報を取得
                val generalAllNo: Int
                val updatedAt: String

                if (novel.site_type == NovelSiteAdapter.SITE_TYPE_KAKUYOMU) {
                    // カクヨムの場合、HTMLスクレイピングで取得
                    val adapter = NovelSiteAdapterFactory.getAdapter(NovelSiteAdapter.SITE_TYPE_KAKUYOMU) as com.shunlight_library.novel_reader.data.adapter.KakuyomuAdapter
                    val workId = PseudoNcodeGenerator.extractKakuyomuWorkId(ncode)
                    val updateSummary = adapter.fetchUpdateSummary(workId)

                    if (!isRunning || session.isCancelled()) {
                        updateComplete(false, "更新処理が中断されました")
                        return@launch
                    }

                    generalAllNo = updateSummary.latestEpisodeCount
                    updatedAt = updateSummary.novelDesc.updated_at

                    if (generalAllNo <= novel.total_ep) {
                        // 更新なしの場合
                        updateComplete(true, "この小説に更新はありません")
                        return@launch
                    }

                    // 小説情報を更新
                    val updatedNovel = novel.copy(
                        general_all_no = generalAllNo,
                        updated_at = updatedAt,
                        title = updateSummary.novelDesc.title,
                        author = updateSummary.novelDesc.author,
                        Synopsis = updateSummary.novelDesc.Synopsis,
                        main_tag = updateSummary.novelDesc.main_tag,
                        sub_tag = updateSummary.novelDesc.sub_tag,
                        last_update_date = updateSummary.novelDesc.last_update_date
                    )
                    repository.updateNovel(updatedNovel)
                } else {
                    // 小説家になろうの場合、APIから取得
                    val urlEntity = repository.getOrCreateURL(ncode, novel.rating == 1)

                    val info = NovelApiUtils.fetchNovelInfo(
                        ncode = ncode,
                        isR18 = novel.rating == 1,
                        apiUrl = urlEntity.api_url
                    )

                    if (!isRunning || session.isCancelled()) {
                        updateComplete(false, "更新処理が中断されました")
                        return@launch
                    }

                    if (info == null) {
                        updateComplete(false, "APIからデータが取得できませんでした")
                        return@launch
                    }

                    generalAllNo = info.generalAllNo
                    updatedAt = info.updatedAt

                    // 常に最新情報を保存
                    val updatedNovel = novel.copy(
                        general_all_no = generalAllNo,
                        updated_at = updatedAt,
                        userid = novel.userid ?: info.userid,
                        noveltype = novel.noveltype ?: info.noveltype,
                        length = novel.length ?: info.length
                    )
                    repository.updateNovel(updatedNovel)
                }

                if (!isRunning || session.isCancelled()) {
                    updateComplete(false, "更新処理が中断されました")
                    return@launch
                }

                // Check for new episodes
                if (generalAllNo > novel.general_all_no) {
                    // Add to update queue
                    val updateQueue = UpdateQueueEntity(
                        ncode = novel.ncode,
                        total_ep = novel.total_ep,
                        general_all_no = generalAllNo,
                        update_time = updatedAt
                    )
                    repository.insertUpdateQueue(updateQueue)

                    updateComplete(true, "更新が見つかりました。更新キューに追加しました。")
                } else {
                    updateComplete(true, "この小説に更新はありません")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check error", e)

                // 403エラー検出
                if (is403Error(e)) {
                    handle403Error(ncode, UPDATE_TYPE_CHECK)
                    return@launch
                }

                updateComplete(false, "エラー: ${e.message}")
            } finally {
                if (currentUpdateSession === session) {
                    NovelUpdateCoordinator.finishUpdate(session)
                    currentUpdateSession = null
                } else {
                    NovelUpdateCoordinator.finishUpdate(session)
                }
            }
        }
    }
    private fun downloadEpisodes(ncode: String) {
        serviceScope.launch {
            val session = NovelUpdateCoordinator.beginUpdate(ncode)
            if (session == null) {
                updateComplete(false, "この小説はすでに更新処理中です")
                return@launch
            }

            currentUpdateSession = session
            currentNcode = ncode

            try {
                updateProgress(0.1f, "小説情報を取得中...")

                if (!isRunning || session.isCancelled()) {
                    updateComplete(false, "更新処理が中断されました")
                    return@launch
                }

                // Get novel info
                val novel = repository.getNovelByNcode(ncode)
                if (novel == null) {
                    updateComplete(false, "小説情報が見つかりませんでした")
                    return@launch
                }

                if (!isRunning || session.isCancelled()) {
                    updateComplete(false, "更新処理が中断されました")
                    return@launch
                }

                updateProgress(0.2f, "APIで最新情報を確認中...")

                // サイトタイプに応じて最新情報を取得
                val generalAllNoValue: Int
                val newUpdatedAt: String

                if (novel.site_type == NovelSiteAdapter.SITE_TYPE_KAKUYOMU) {
                    // カクヨムの場合、HTMLスクレイピングでメタデータとエピソード一覧（本文なし）を取得
                    val kakuyomuAdapter = NovelSiteAdapterFactory.getAdapter(NovelSiteAdapter.SITE_TYPE_KAKUYOMU) as com.shunlight_library.novel_reader.data.adapter.KakuyomuAdapter
                    val workId = PseudoNcodeGenerator.extractKakuyomuWorkId(ncode)

                    // メタデータとエピソードリスト（本文なし）＋マッピングをアトミックに取得
                    val (updatedNovelDesc, episodeListWithoutBody, mappings) = kakuyomuAdapter.fetchNovelMetadataWithEpisodeListAndMappings(workId)

                    if (!isRunning || session.isCancelled()) {
                        updateComplete(false, "更新処理が中断されました")
                        return@launch
                    }

                    generalAllNoValue = episodeListWithoutBody.size
                    newUpdatedAt = updatedNovelDesc.updated_at

                    // 小説情報を更新（total_epは削除後に0にリセット）
                    val updatedNovel = novel.copy(
                        general_all_no = generalAllNoValue,
                        total_ep = 0,
                        updated_at = newUpdatedAt,
                        title = updatedNovelDesc.title,
                        author = updatedNovelDesc.author,
                        Synopsis = updatedNovelDesc.Synopsis,
                        main_tag = updatedNovelDesc.main_tag,
                        sub_tag = updatedNovelDesc.sub_tag,
                        last_update_date = updatedNovelDesc.last_update_date
                    )
                    repository.updateNovel(updatedNovel)

                    if (!isRunning || session.isCancelled()) {
                        updateComplete(false, "更新処理が中断されました")
                        return@launch
                    }

                    // 既存エピソードは削除しない。insertEpisode が is_read/is_bookmark/reading_rate を保持してマージする。

                    // 全エピソードを取得対象とする
                    val episodesToDownload = episodeListWithoutBody

                    if (episodesToDownload.isEmpty()) {
                        updateComplete(true, "ダウンロードするエピソードがありません")
                        return@launch
                    }

                    updateProgress(0.3f, "エピソードを取得中... (0/${episodesToDownload.size})")

                    var successCount = 0

                    // カクヨムのエピソード本文を1話ずつ取得→保存
                    episodesToDownload.forEachIndexed { index, episode ->
                        if (!isRunning || session.isCancelled()) {
                            updateComplete(false, "更新処理が中断されました")
                            return@launch
                        }

                        try {
                            // エピソード番号からカクヨムの実際のエピソードIDを取得
                            val episodeNoInt = episode.episode_no.toIntOrNull() ?: (index + novel.total_ep + 1)
                            val kakuyomuEpisodeId = mappings[episodeNoInt] ?: episode.episode_no

                            // エピソード本文を取得（再試行あり）
                            var episodeBody = ""
                            var retryCount = 0
                            val maxRetries = 3

                            while (retryCount < maxRetries) {
                                episodeBody = kakuyomuAdapter.fetchEpisodeContent(workId, kakuyomuEpisodeId)

                                // 本文が空、またはエラーメッセージの場合は再試行
                                if (episodeBody.isNotEmpty() && !episodeBody.startsWith("★HTMLページ読み込みエラー")) {
                                    break
                                }

                                retryCount++
                                if (retryCount < maxRetries) {
                                    Log.w(TAG, "Episode body is empty or error, retrying (${retryCount}/${maxRetries}): ${episode.episode_no}")
                                    delay(1000) // 1秒待機してから再試行
                                }
                            }

                            if (episodeBody.isEmpty() || episodeBody.startsWith("★HTMLページ読み込みエラー")) {
                                Log.e(TAG, "Failed to fetch episode body after ${maxRetries} retries: ${episode.episode_no}")
                                // エラー文字列で既存の本文を上書きしない
                            } else {
                                // 正常取得できた場合のみ保存
                                val episodeWithBody = episode.copy(body = episodeBody)
                                repository.insertEpisode(episodeWithBody)
                                successCount++
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to fetch episode ${episode.episode_no}: ${e.message}")
                            // エラー発生時も既存データを保持（本文なしでの上書き禁止）
                        }

                        val progress = (index + 1).toFloat() / episodesToDownload.size
                        updateProgress(0.3f + (0.7f * progress), "エピソードを取得中... (${index + 1}/${episodesToDownload.size})")

                        delay(500) // カクヨムのレート制限を考慮
                    }

                    if (!isRunning || session.isCancelled()) {
                        updateComplete(false, "更新処理が中断されました")
                        return@launch
                    }

                    // マッピング情報を保存（カクヨム用）
                    if (mappings.isNotEmpty()) {
                        val mappingEntities = mappings.map { (episodeNo, kakuyomuId) ->
                            com.shunlight_library.novel_reader.data.entity.EpisodeMappingEntity(
                                ncode = ncode,
                                episode_no = episodeNo,
                                kakuyomu_episode_id = kakuyomuId
                            )
                        }
                        repository.insertEpisodeMappings(mappingEntities)
                    }

                    // 小説のtotal_ep値を更新（再取得なのでsuccessCountがそのままtotal_ep）
                    val updatedNovelAfterDownload = updatedNovel.copy(total_ep = successCount)
                    repository.updateNovel(updatedNovelAfterDownload)

                    // 全話完了なら更新キューから削除
                    if (successCount >= generalAllNoValue) {
                        repository.deleteUpdateQueueByNcode(ncode)
                    }

                    updateComplete(true, "完了: 成功${successCount}件")
                    return@launch
                } else {
                    // 小説家になろうの場合、APIから取得
                    val info = NovelApiUtils.fetchNovelInfo(ncode, novel.rating == 1)

                    if (!isRunning || session.isCancelled()) {
                        updateComplete(false, "更新処理が中断されました")
                        return@launch
                    }

                    generalAllNoValue = info?.generalAllNo ?: novel.general_all_no
                    newUpdatedAt = info?.updatedAt ?: novel.updated_at
                    val updatedNovel = novel.copy(
                        general_all_no = generalAllNoValue,
                        total_ep = 0,
                        updated_at = newUpdatedAt,
                        userid = novel.userid ?: info?.userid,
                        noveltype = novel.noveltype ?: info?.noveltype,
                        length = novel.length ?: info?.length
                    )
                    repository.updateNovel(updatedNovel)

                    // 既存エピソードは削除しない。insertEpisode が is_read/is_bookmark/reading_rate を保持してマージする。
                }

                if (!isRunning || session.isCancelled()) {
                    updateComplete(false, "更新処理が中断されました")
                    return@launch
                }

                // Start downloading episodes
                updateProgress(0.3f, "エピソードを取得中... (0/$generalAllNoValue)")

                var successCount = 0
                var failCount = 0

                // 全話を1話から再取得
                val episodesToDownload = (1..generalAllNoValue).toList()

                if (episodesToDownload.isEmpty()) {
                    updateComplete(true, "ダウンロードするエピソードがありません")
                    return@launch
                }

                for ((index, episodeNo) in episodesToDownload.withIndex()) {
                    if (!isRunning || session.isCancelled()) {
                        updateComplete(false, "更新処理が中断されました")
                        return@launch
                    }

                    val episode = NovelApiUtils.fetchEpisodeWithRetry(
                        novel.ncode,
                        episodeNo.toString(),
                        novel.rating == 1,
                        novel.noveltype
                    )

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

                if (!isRunning || session.isCancelled()) {
                    updateComplete(false, "更新処理が中断されました")
                    return@launch
                }

                // Update novel total_ep value（再取得なので実DBから再集計）
                val updatedEpisodesAfterDownload = repository.getEpisodesByNcode(ncode).first()
                val actualTotalEp = updatedEpisodesAfterDownload.mapNotNull { it.episode_no.toIntOrNull() }.maxOrNull() ?: successCount
                if (successCount > 0) {
                    val updatedNovelAfterDownload = repository.getNovelByNcode(ncode)?.copy(
                        total_ep = actualTotalEp,
                        general_all_no = generalAllNoValue,
                        updated_at = newUpdatedAt
                    )
                    if (updatedNovelAfterDownload != null) {
                        repository.updateNovel(updatedNovelAfterDownload)
                    }

                    // Remove from update queue if all episodes downloaded
                    if (actualTotalEp >= generalAllNoValue) {
                        repository.deleteUpdateQueueByNcode(ncode)
                    }
                }

                updateComplete(true, "完了: 成功${successCount}件、失敗${failCount}件")
            } catch (e: Exception) {
                Log.e(TAG, "Download episodes error", e)

                // 403エラー検出
                if (is403Error(e)) {
                    handle403Error(ncode, UPDATE_TYPE_DOWNLOAD)
                    return@launch
                }

                updateComplete(false, "エラー: ${e.message}")
            } finally {
                if (currentUpdateSession === session) {
                    NovelUpdateCoordinator.finishUpdate(session)
                    currentUpdateSession = null
                } else {
                    NovelUpdateCoordinator.finishUpdate(session)
                }
            }
        }
    }

    private fun fixEpisodeErrors(ncode: String) {
        serviceScope.launch {
            val session = NovelUpdateCoordinator.beginUpdate(ncode)
            if (session == null) {
                updateComplete(false, "この小説はすでに更新処理中です")
                return@launch
            }

            currentUpdateSession = session
            currentNcode = ncode

            try {
                updateProgress(0.1f, "エピソードをチェック中...")

                if (!isRunning || session.isCancelled()) {
                    updateComplete(false, "更新処理が中断されました")
                    return@launch
                }

                // Get novel info
                val novel = repository.getNovelByNcode(ncode)
                if (novel == null) {
                    updateComplete(false, "小説情報が見つかりませんでした")
                    return@launch
                }

                if (!isRunning || session.isCancelled()) {
                    updateComplete(false, "更新処理が中断されました")
                    return@launch
                }

                // Get episodes
                val episodes = repository.getEpisodesByNcode(ncode).first()

                if (!isRunning || session.isCancelled()) {
                    updateComplete(false, "更新処理が中断されました")
                    return@launch
                }

                // Find episodes with errors
                val errorEpisodes = episodes.filter { episode ->
                    episode.body.isEmpty() || episode.e_title.isEmpty()
                }

                updateProgress(0.2f, "APIで最新情報を確認中...")

                if (!isRunning || session.isCancelled()) {
                    updateComplete(false, "更新処理が中断されました")
                    return@launch
                }

                // サイトタイプに応じて最新情報を取得
                val generalAllNoValue: Int

                if (novel.site_type == NovelSiteAdapter.SITE_TYPE_KAKUYOMU) {
                    // カクヨムの場合、HTMLスクレイピングでメタデータとエピソード一覧（本文なし）を取得
                    val kakuyomuAdapter = NovelSiteAdapterFactory.getAdapter(NovelSiteAdapter.SITE_TYPE_KAKUYOMU) as com.shunlight_library.novel_reader.data.adapter.KakuyomuAdapter
                    val workId = PseudoNcodeGenerator.extractKakuyomuWorkId(ncode)

                    // メタデータとエピソードリスト（本文なし）＋マッピングをアトミックに取得
                    val (updatedNovelDesc, episodeListWithoutBody, mappings) = kakuyomuAdapter.fetchNovelMetadataWithEpisodeListAndMappings(workId)

                    if (!isRunning || session.isCancelled()) {
                        updateComplete(false, "更新処理が中断されました")
                        return@launch
                    }

                    generalAllNoValue = episodeListWithoutBody.size

                    // 小説情報を更新
                    val updatedNovel = novel.copy(
                        general_all_no = generalAllNoValue,
                        updated_at = updatedNovelDesc.updated_at,
                        title = updatedNovelDesc.title,
                        author = updatedNovelDesc.author,
                        Synopsis = updatedNovelDesc.Synopsis,
                        main_tag = updatedNovelDesc.main_tag,
                        sub_tag = updatedNovelDesc.sub_tag,
                        last_update_date = updatedNovelDesc.last_update_date
                    )
                    repository.updateNovel(updatedNovel)

                    if (!isRunning || session.isCancelled()) {
                        updateComplete(false, "更新処理が中断されました")
                        return@launch
                    }

                    // エラーのあるエピソードを特定
                    val errorEpisodeNos = errorEpisodes.mapNotNull { it.episode_no.toIntOrNull() }.toSet()

                    // 欠番エピソードを特定
                    val episodeNumberMap = episodes.associate { episode ->
                        val numericValue = episode.episode_no.toIntOrNull() ?: 0
                        numericValue to episode.episode_no
                    }
                    val maxEpisodeNo = episodeNumberMap.keys.maxOrNull() ?: 0
                    val checkRangeMax = maxOf(generalAllNoValue, maxEpisodeNo)
                    val missingEpisodeNos = (1..checkRangeMax).filter { epNo ->
                        !episodeNumberMap.containsKey(epNo)
                    }.toSet()

                    // エラーおよび欠番エピソードを再取得
                    val redownloadTargetNos = (errorEpisodeNos + missingEpisodeNos).sorted()

                    if (redownloadTargetNos.isEmpty()) {
                        updateComplete(true, "エラーや欠番は見つかりませんでした")
                        return@launch
                    }

                    updateProgress(0.3f, "エラーまたは欠番のあるエピソードを再取得中... (0/${redownloadTargetNos.size})")

                    var successCount = 0

                    // 再取得対象のエピソードを1話ずつ取得→保存
                    redownloadTargetNos.forEachIndexed { index, episodeNoInt ->
                        if (!isRunning || session.isCancelled()) {
                            updateComplete(false, "更新処理が中断されました")
                            return@launch
                        }

                        try {
                            // エピソードリストから該当するエピソード情報（本文なし）を探す
                            val episodeInfo = episodeListWithoutBody.find { it.episode_no == episodeNoInt.toString() }

                            if (episodeInfo != null) {
                                // カクヨムの実際のエピソードIDを取得
                                val kakuyomuEpisodeId = mappings[episodeNoInt] ?: episodeInfo.episode_no

                                // エピソード本文を取得（再試行あり）
                                var episodeBody = ""
                                var retryCount = 0
                                val maxRetries = 3

                                while (retryCount < maxRetries) {
                                    episodeBody = kakuyomuAdapter.fetchEpisodeContent(workId, kakuyomuEpisodeId)

                                    // 本文が空、またはエラーメッセージの場合は再試行
                                    if (episodeBody.isNotEmpty() && !episodeBody.startsWith("★HTMLページ読み込みエラー")) {
                                        break
                                    }

                                    retryCount++
                                    if (retryCount < maxRetries) {
                                        Log.w(TAG, "Episode body is empty or error, retrying (${retryCount}/${maxRetries}): $episodeNoInt")
                                        delay(1000) // 1秒待機してから再試行
                                    }
                                }

                                if (episodeBody.isEmpty() || episodeBody.startsWith("★HTMLページ読み込みエラー")) {
                                    Log.e(TAG, "Failed to fetch episode body after ${maxRetries} retries: $episodeNoInt")
                                } else {
                                    // 正常取得できた場合のみ保存（エラー文字列で上書きしない）
                                    val episodeWithBody = episodeInfo.copy(body = episodeBody)
                                    repository.insertEpisode(episodeWithBody)
                                    successCount++
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to fetch episode $episodeNoInt: ${e.message}")
                            // エラーでも処理を継続
                        }

                        val progress = (index + 1).toFloat() / redownloadTargetNos.size
                        updateProgress(0.3f + (0.7f * progress), "エラーまたは欠番のあるエピソードを再取得中... (${index + 1}/${redownloadTargetNos.size})")

                        delay(500) // カクヨムのレート制限を考慮
                    }

                    if (!isRunning || session.isCancelled()) {
                        updateComplete(false, "更新処理が中断されました")
                        return@launch
                    }

                    // マッピング情報も保存（カクヨム用）
                    if (mappings.isNotEmpty()) {
                        val mappingEntities = mappings.map { (episodeNo, kakuyomuId) ->
                            com.shunlight_library.novel_reader.data.entity.EpisodeMappingEntity(
                                ncode = ncode,
                                episode_no = episodeNo,
                                kakuyomu_episode_id = kakuyomuId
                            )
                        }
                        repository.insertEpisodeMappings(mappingEntities)
                    }

                    // 小説のtotal_ep値を更新
                    val updatedEpisodes = repository.getEpisodesByNcode(novel.ncode).first()
                    val maxEpisodeNoAfterFix = updatedEpisodes.mapNotNull { it.episode_no.toIntOrNull() }.maxOrNull() ?: 0

                    if (maxEpisodeNoAfterFix > novel.total_ep) {
                        val updatedNovelAfterFix = novel.copy(total_ep = maxEpisodeNoAfterFix)
                        repository.updateNovel(updatedNovelAfterFix)
                    }

                    updateComplete(true, "完了: 成功${successCount}件")
                    return@launch
                } else {
                    // 小説家になろうの場合、APIから取得
                    val info = NovelApiUtils.fetchNovelInfo(ncode, novel.rating == 1)

                    if (!isRunning || session.isCancelled()) {
                        updateComplete(false, "更新処理が中断されました")
                        return@launch
                    }

                    generalAllNoValue = info?.generalAllNo ?: novel.general_all_no
                    val updatedNovel = novel.copy(
                        general_all_no = generalAllNoValue,
                        updated_at = info?.updatedAt ?: novel.updated_at,
                        userid = novel.userid ?: info?.userid,
                        noveltype = novel.noveltype ?: info?.noveltype,
                        length = novel.length ?: info?.length
                    )
                    repository.updateNovel(updatedNovel)
                }

                if (!isRunning || session.isCancelled()) {
                    updateComplete(false, "更新処理が中断されました")
                    return@launch
                }

                // カクヨムかどうかをチェック
                val isKakuyomu = com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator.isKakuyomuNcode(novel.ncode)

                val missingEpisodes: List<String>
                if (isKakuyomu) {
                    // カクヨムの場合は欠番チェックをしない（エピソードIDが連番でないため）
                    missingEpisodes = emptyList()
                } else {
                    // 小説家になろうの場合は欠番チェック
                    val episodeNumberMap = episodes.associate { episode ->
                        val numericValue = episode.episode_no.toIntOrNull() ?: 0
                        numericValue to episode.episode_no
                    }

                    val maxEpisodeNo = episodeNumberMap.keys.maxOrNull() ?: 0
                    val checkRangeMax = maxOf(generalAllNoValue, maxEpisodeNo)
                    missingEpisodes = (1..checkRangeMax).filter { epNo ->
                        !episodeNumberMap.containsKey(epNo)
                    }.map { it.toString() }
                }

                // Combine error and missing episodes
                val errorEpisodeNumbers = errorEpisodes.map { it.episode_no }
                val redownloadTargets = (errorEpisodeNumbers + missingEpisodes).distinct().let { list ->
                    // 小説家になろうの場合のみソート（数値として）
                    if (isKakuyomu) {
                        list
                    } else {
                        list.sortedBy { it.toIntOrNull() ?: 0 }
                    }
                }

                if (redownloadTargets.isEmpty()) {
                    updateComplete(true, "エラーや欠番は見つかりませんでした")
                    return@launch
                }

                updateProgress(0.3f, "エラーまたは欠番のあるエピソードを再取得中... (0/${redownloadTargets.size})")

                var successCount = 0
                var failCount = 0

                // Download error episodes
                for ((index, episodeNo) in redownloadTargets.withIndex()) {
                    if (!isRunning || session.isCancelled()) {
                        updateComplete(false, "更新処理が中断されました")
                        return@launch
                    }

                    val episode = NovelApiUtils.fetchEpisodeWithRetry(
                        novel.ncode,
                        episodeNo,
                        novel.rating == 1,
                        novel.noveltype
                    )

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

                if (!isRunning || session.isCancelled()) {
                    updateComplete(false, "更新処理が中断されました")
                    return@launch
                }

                // Update novel total_ep value if needed
                val updatedEpisodes = repository.getEpisodesByNcode(novel.ncode).first()
                val maxEpisodeNoAfterFix = updatedEpisodes.mapNotNull { it.episode_no.toIntOrNull() }.maxOrNull() ?: 0

                if (maxEpisodeNoAfterFix > novel.total_ep) {
                    val updatedNovelAfterFix = novel.copy(total_ep = maxEpisodeNoAfterFix)
                    repository.updateNovel(updatedNovelAfterFix)
                }

                updateComplete(true, "完了: 成功${successCount}件、失敗${failCount}件")
            } catch (e: Exception) {
                Log.e(TAG, "Fix episodes error", e)

                // 403エラー検出
                if (is403Error(e)) {
                    handle403Error(ncode, UPDATE_TYPE_FIX_ERRORS)
                    return@launch
                }

                updateComplete(false, "エラー: ${e.message}")
            } finally {
                if (currentUpdateSession === session) {
                    NovelUpdateCoordinator.finishUpdate(session)
                    currentUpdateSession = null
                } else {
                    NovelUpdateCoordinator.finishUpdate(session)
                }
            }
        }
    }

    private fun performBulkUpdate() {
        serviceScope.launch {
            try {
                updateProgress(0.1f, "更新キューを取得中...")

                if (!isRunning) {
                    updateComplete(false, "更新処理が中断されました")
                    return@launch
                }

                // Get update queue
                val updateQueue = repository.getAllUpdateQueue()

                if (updateQueue.isEmpty()) {
                    updateComplete(true, "更新キューが空です")
                    return@launch
                }

                updateProgress(0.2f, "更新対象を計算中...")

                // Count total episodes to download and prepare plan
                var totalEpisodes = 0
                val plannedEpisodes = mutableMapOf<String, Int>()
                updateQueue.forEach { queueItem ->
                    val novel = repository.getNovelByNcode(queueItem.ncode)
                    if (novel != null) {
                        val episodesToDownload = queueItem.general_all_no - novel.total_ep
                        if (episodesToDownload > 0) {
                            plannedEpisodes[queueItem.ncode] = episodesToDownload
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
                val skippedNovels = mutableSetOf<String>()

                // Process each queue item
                for (queueItem in updateQueue) {
                    if (!isRunning) {
                        updateComplete(false, "更新処理が中断されました")
                        return@launch
                    }

                    val novel = repository.getNovelByNcode(queueItem.ncode) ?: continue
                    val plannedCount = plannedEpisodes[queueItem.ncode] ?: continue
                    if (plannedCount <= 0) continue

                    val startEpisode = novel.total_ep + 1
                    val endEpisode = queueItem.general_all_no
                    if (startEpisode > endEpisode) {
                        continue
                    }

                    val session = NovelUpdateCoordinator.awaitUpdateSlot(queueItem.ncode)
                    if (session == null) {
                        skippedNovels.add(queueItem.ncode)
                        totalEpisodes -= plannedCount
                        if (totalEpisodes < processedEpisodes) {
                            totalEpisodes = processedEpisodes
                        }
                        continue
                    }

                    currentUpdateSession = session
                    currentNcode = queueItem.ncode

                    val episodesToDownload = (startEpisode..endEpisode).toList()
                    var processedForNovel = 0
                    var successForNovel = 0
                    var failForNovel = 0
                    var cancelledForNovel = false

                    try {
                        if (novel.site_type == NovelSiteAdapter.SITE_TYPE_KAKUYOMU) {
                            // カクヨム: mapping を更新してから本文取得
                            val kakuyomuAdapter = NovelSiteAdapterFactory.getAdapter(
                                NovelSiteAdapter.SITE_TYPE_KAKUYOMU
                            ) as KakuyomuAdapter
                            val workId = PseudoNcodeGenerator.extractKakuyomuWorkId(novel.ncode)

                            val (_, episodeList, mappings) = kakuyomuAdapter.fetchNovelMetadataWithEpisodeListAndMappings(workId)

                            if (mappings.isNotEmpty()) {
                                val mappingEntities = mappings.map { (epNo, kakuyomuId) ->
                                    EpisodeMappingEntity(novel.ncode, epNo, kakuyomuId)
                                }
                                repository.insertEpisodeMappings(mappingEntities)
                            }

                            val episodeTitles = episodeList.associate {
                                it.episode_no.toIntOrNull() to (it.e_title ?: "第${it.episode_no}話")
                            }

                            for (episodeNo in episodesToDownload) {
                                if (!isRunning) { updateComplete(false, "更新処理が中断されました"); return@launch }
                                if (session.isCancelled()) { cancelledForNovel = true; break }

                                val kakuyomuId = mappings[episodeNo]
                                if (kakuyomuId == null) { failForNovel++; processedForNovel++; processedEpisodes++; continue }

                                val body = kakuyomuAdapter.fetchEpisodeContent(workId, kakuyomuId)
                                if (body.isNotEmpty() && !body.startsWith("★HTMLページ読み込みエラー")) {
                                    val ep = com.shunlight_library.novel_reader.data.entity.EpisodeEntity(
                                        ncode = novel.ncode,
                                        episode_no = episodeNo.toString(),
                                        e_title = episodeTitles[episodeNo] ?: "第${episodeNo}話",
                                        body = body,
                                        update_time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                                    )
                                    repository.insertEpisode(ep)
                                    successForNovel++
                                } else {
                                    failForNovel++
                                }
                                processedForNovel++
                                processedEpisodes++

                                val denom = maxOf(totalEpisodes, processedEpisodes, 1)
                                updateProgress(0.3f + (0.7f * processedEpisodes.toFloat() / denom), "エピソードをダウンロード中... ($processedEpisodes/$denom)")
                                delay(500)
                            }
                        } else {
                            // なろう（Syosetu）
                            for (episodeNo in episodesToDownload) {
                                if (!isRunning) {
                                    updateComplete(false, "更新処理が中断されました")
                                    return@launch
                                }

                                if (session.isCancelled()) {
                                    cancelledForNovel = true
                                    break
                                }

                                val episode = NovelApiUtils.fetchEpisodeWithRetry(
                                    novel.ncode,
                                    episodeNo.toString(),
                                    novel.rating == 1,
                                    novel.noveltype
                                )

                                if (episode != null) {
                                    repository.insertEpisode(episode)
                                    successForNovel++
                                } else {
                                    failForNovel++
                                }

                                processedForNovel++
                                processedEpisodes++

                                val denominator = if (totalEpisodes <= 0) {
                                    if (processedEpisodes == 0) 1 else processedEpisodes
                                } else {
                                    maxOf(totalEpisodes, processedEpisodes)
                                }
                                val fraction = processedEpisodes.toFloat() / denominator.toFloat()
                                updateProgress(
                                    0.3f + (0.7f * fraction),
                                    "エピソードをダウンロード中... ($processedEpisodes/$denominator)"
                                )

                                delay(200)
                            }
                        }
                    } finally {
                        NovelUpdateCoordinator.finishUpdate(session)
                        if (currentUpdateSession === session) {
                            currentUpdateSession = null
                        }
                    }

                    if (cancelledForNovel) {
                        skippedNovels.add(queueItem.ncode)
                        val remaining = episodesToDownload.size - processedForNovel
                        totalEpisodes -= remaining
                        if (totalEpisodes < processedEpisodes) {
                            totalEpisodes = processedEpisodes
                        }
                        continue
                    }

                    successCount += successForNovel
                    failCount += failForNovel

                    if (failForNovel == 0) {
                        // 全話成功: total_ep を確定しキューから削除
                        val updatedNovel = novel.copy(
                            total_ep = endEpisode,
                            general_all_no = endEpisode
                        )
                        repository.updateNovel(updatedNovel)
                        repository.deleteUpdateQueueByNcode(queueItem.ncode)
                    } else {
                        // 一部失敗: 実際に保存された最大話数を total_ep に反映し、
                        // 末尾まで届いていない場合はキューを残して次回リトライ可能にする
                        val maxSavedEp = repository.getEpisodesByNcode(novel.ncode).first()
                            .mapNotNull { it.episode_no.toIntOrNull() }.maxOrNull() ?: novel.total_ep
                        repository.updateNovel(novel.copy(
                            total_ep = maxSavedEp,
                            general_all_no = endEpisode
                        ))
                        if (maxSavedEp >= endEpisode) {
                            // 末尾まで保存済み（途中の欠番はエラー修正で再取得可能）
                            repository.deleteUpdateQueueByNcode(queueItem.ncode)
                        }
                        Log.w(TAG, "一括更新で${failForNovel}件失敗: ${queueItem.ncode} (保存済み最大話数: $maxSavedEp)")
                    }
                }

                val message = if (skippedNovels.isEmpty()) {
                    "完了: 成功${successCount}件、失敗${failCount}件"
                } else {
                    "完了: 成功${successCount}件、失敗${failCount}件（${skippedNovels.size}件の小説をスキップ）"
                }
                updateComplete(true, message)
            } catch (e: Exception) {
                Log.e(TAG, "Bulk update error", e)

                // 403エラー検出（performBulkUpdateは特定のncodeを持たないため、最初のncodeを使用）
                if (is403Error(e)) {
                    val queueList = repository.getAllUpdateQueue()
                    val firstNcode = queueList.firstOrNull()?.ncode ?: ""
                    if (firstNcode.isNotEmpty()) {
                        handle403Error(firstNcode, UPDATE_TYPE_BULK_UPDATE)
                    } else {
                        updateComplete(false, "エラー: ${e.message}")
                    }
                    return@launch
                }

                updateComplete(false, "エラー: ${e.message}")
            } finally {
                currentUpdateSession?.let {
                    NovelUpdateCoordinator.finishUpdate(it)
                    currentUpdateSession = null
                }
            }
        }
    }

    private fun checkRevision(ncode: String) {
        serviceScope.launch {
            val session = NovelUpdateCoordinator.beginUpdate(ncode)
            if (session == null) {
                updateComplete(false, "この小説はすでに更新処理中です")
                return@launch
            }

            currentUpdateSession = session
            currentNcode = ncode

            try {
                updateProgress(0.1f, "小説情報を取得中...")

                if (!isRunning || session.isCancelled()) {
                    updateComplete(false, "更新処理が中断されました")
                    return@launch
                }

                // Get current novel info
                val novel = repository.getNovelByNcode(ncode)
                if (novel == null) {
                    updateComplete(false, "小説情報が見つかりませんでした")
                    return@launch
                }

                if (!isRunning || session.isCancelled()) {
                    updateComplete(false, "更新処理が中断されました")
                    return@launch
                }

                // カクヨムまたは短編小説の場合は改稿チェックをスキップ
                if (novel.site_type == NovelSiteAdapter.SITE_TYPE_KAKUYOMU) {
                    updateComplete(false, "カクヨムは改稿チェックに対応していません")
                    return@launch
                }

                if (novel.noveltype == 2) {
                    updateComplete(false, "短編小説は改稿チェックに対応していません")
                    return@launch
                }

                updateProgress(0.2f, "改稿情報を確認中...")

                if (!isRunning || session.isCancelled()) {
                    updateComplete(false, "更新処理が中断されました")
                    return@launch
                }

                var revisedCount = 0

                try {
                    // 目次から改稿情報を取得
                    val revisionInfos = NovelApiUtils.fetchEpisodeRevisionsFromToc(
                        ncode = ncode,
                        isR18 = novel.rating == 1,
                        noveltype = novel.noveltype
                    )

                    if (revisionInfos.isEmpty()) {
                        updateComplete(true, "改稿は見つかりませんでした")
                        return@launch
                    }

                    // 既存のエピソードを取得
                    val existingEpisodes = repository.getEpisodesByNcode(ncode).first()
                    val episodeMap = existingEpisodes.associateBy { it.episode_no.toIntOrNull() ?: 0 }

                    if (!isRunning || session.isCancelled()) {
                        updateComplete(false, "更新処理が中断されました")
                        return@launch
                    }

                    updateProgress(0.3f, "改稿されたエピソードを確認中... (0/${revisionInfos.size})")

                    // 改稿されたエピソードをチェック
                    var processedCount = 0
                    for (revisionInfo in revisionInfos) {
                        val existingEpisode = episodeMap[revisionInfo.episodeNo]
                        if (existingEpisode != null) {
                            // 改稿日時を比較（目次の日時が新しい場合は改稿あり）
                            if (revisionInfo.updateTime > existingEpisode.update_time) {
                                // エピソードを再取得
                                val updatedEpisode = NovelApiUtils.fetchEpisodeWithRetry(
                                    ncode = ncode,
                                    episodeNo = revisionInfo.episodeNo.toString(),
                                    isR18 = novel.rating == 1,
                                    noveltype = novel.noveltype
                                )

                                if (updatedEpisode != null) {
                                    // update_timeを目次から取得した改稿日時で更新
                                    val episodeWithRevisionTime = updatedEpisode.copy(
                                        update_time = revisionInfo.updateTime
                                    )
                                    repository.insertEpisode(episodeWithRevisionTime)
                                    revisedCount++
                                    Log.d(TAG, "改稿を検出: 第${revisionInfo.episodeNo}話 (${revisionInfo.updateTime})")
                                }

                                // サーバー負荷軽減
                                delay(200)

                                if (!isRunning || session.isCancelled()) {
                                    updateComplete(false, "更新処理が中断されました")
                                    return@launch
                                }
                            }
                        }

                        processedCount++
                        val progress = processedCount.toFloat() / revisionInfos.size
                        updateProgress(0.3f + (0.7f * progress), "改稿されたエピソードを確認中... ($processedCount/${revisionInfos.size})")
                    }

                    if (revisedCount > 0) {
                        updateComplete(true, "改稿が見つかりました（${revisedCount}話を更新）。")
                    } else {
                        updateComplete(true, "改稿は見つかりませんでした")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "改稿チェックエラー", e)
                    updateComplete(false, "エラー: ${e.message}")
                }
                } catch (e: Exception) {
                    Log.e(TAG, "Revision check error", e)

                    // 403エラー検出
                    if (is403Error(e)) {
                        handle403Error(ncode, UPDATE_TYPE_CHECK_REVISION)
                        return@launch
                    }

                    updateComplete(false, "エラー: ${e.message}")
                } finally {
                if (currentUpdateSession === session) {
                    NovelUpdateCoordinator.finishUpdate(session)
                    currentUpdateSession = null
                } else {
                    NovelUpdateCoordinator.finishUpdate(session)
                }
            }
        }
    }
}
