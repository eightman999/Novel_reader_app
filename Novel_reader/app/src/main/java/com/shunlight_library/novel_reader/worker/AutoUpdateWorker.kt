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
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shunlight_library.novel_reader.MainActivity
import com.shunlight_library.novel_reader.NovelReaderApplication
import com.shunlight_library.novel_reader.R
import com.shunlight_library.novel_reader.SettingsStore
import com.shunlight_library.novel_reader.data.entity.UpdateQueueEntity
import com.shunlight_library.novel_reader.data.AppNotification
import com.shunlight_library.novel_reader.data.NotificationStore
import com.shunlight_library.novel_reader.data.NotificationType
import com.shunlight_library.novel_reader.worker.AutoUpdateScheduler
import com.shunlight_library.novel_reader.utils.ReleaseUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    }

    private val repository = NovelReaderApplication.getRepository()
    private val notificationStore = NotificationStore(context)

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "自動更新処理開始")
            
            // 通知チャンネルを作成
            createNotificationChannel()
            
            // 更新確認処理
            val updateResults = performUpdateCheck()

            // 結果の処理と通知
            handleUpdateResults(updateResults)

            // GitHubの最新リリースを確認
            ReleaseUtils.checkForNewRelease(applicationContext)

            // 次回のスケジュールを再設定
            reschedule()

            Log.d(TAG, "自動更新処理完了")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "自動更新処理中にエラーが発生", e)
            // エラー発生時に通知
            sendErrorNotification(e.message ?: "不明なエラー")
            reschedule()
            Result.failure()
        }
    }

    private suspend fun reschedule() {
        if (!tags.contains("manual_update")) {
            val store = SettingsStore(applicationContext)
            val enabled = store.autoUpdateEnabled.first()
            val time = store.autoUpdateTime.first()
            AutoUpdateScheduler(applicationContext).scheduleAutoUpdate(enabled, time)
        }
    }

    private suspend fun performUpdateCheck(): UpdateResult {
        return withContext(Dispatchers.IO) {
            var newNovelsCount = 0
            var updatedNovelsCount = 0
            val errors = mutableListOf<String>()

            try {
                // 更新対象の小説を取得
                val novels = repository.getNovelsForUpdate()
                Log.d(TAG, "更新対象小説数: ${novels.size}")

                novels.forEach { novel ->
                    try {
                        // 新しいRepository APIを使用して更新チェック（マルチサイト対応）
                        val hasUpdate = repository.checkNovelForUpdates(novel.ncode)

                        if (hasUpdate) {
                            // 更新がある場合、最新情報を取得するためにアダプター経由で詳細取得
                            val adapter = com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapterFactory.getAdapter(novel.site_type)

                            // サイト種別に応じて処理
                            val latestEpisodeCount = when (adapter.getSiteType()) {
                                com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter.SITE_TYPE_SYOSETU -> {
                                    // 小説家になろうの場合、APIから最新情報を取得
                                    val isR18 = novel.rating == 1
                                    val apiInfo = com.shunlight_library.novel_reader.api.NovelApiUtils.fetchNovelInfo(
                                        ncode = novel.ncode,
                                        isR18 = isR18
                                    )
                                    apiInfo?.generalAllNo ?: novel.total_ep
                                }
                                com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter.SITE_TYPE_KAKUYOMU -> {
                                    // カクヨムの場合、HTMLスクレイピングで取得
                                    val workId = com.shunlight_library.novel_reader.utils.PseudoNcodeGenerator.extractKakuyomuWorkId(novel.ncode)
                                    val (updatedNovel, _) = adapter.fetchNovelWithEpisodes(workId)
                                    updatedNovel.total_ep
                                }
                                else -> novel.total_ep
                            }

                            var hasNewEpisodes = false
                            var hasRevisedEpisodes = false
                            
                            if (latestEpisodeCount > novel.total_ep) {
                                hasNewEpisodes = true
                                // 更新キューに追加
                                val updateQueue = UpdateQueueEntity(
                                    ncode = novel.ncode,
                                    total_ep = latestEpisodeCount,
                                    general_all_no = novel.total_ep,
                                    update_time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                                )
                                repository.insertUpdateQueue(updateQueue)
                            }

                            // 改稿チェック（小説家になろうのみ、短編を除く）
                            if (adapter.getSiteType() == com.shunlight_library.novel_reader.data.adapter.NovelSiteAdapter.SITE_TYPE_SYOSETU && novel.noveltype != 2) {
                                try {
                                    val revisionInfos = com.shunlight_library.novel_reader.api.NovelApiUtils.fetchEpisodeRevisionsFromToc(
                                        ncode = novel.ncode,
                                        isR18 = novel.rating == 1,
                                        noveltype = novel.noveltype
                                    )

                                    if (revisionInfos.isNotEmpty()) {
                                        val existingEpisodes = repository.getEpisodesByNcode(novel.ncode).first()
                                        val episodeMap = existingEpisodes.associateBy { it.episode_no.toIntOrNull() ?: 0 }

                                        for (revisionInfo in revisionInfos) {
                                            val existingEpisode = episodeMap[revisionInfo.episodeNo]
                                            if (existingEpisode != null && revisionInfo.updateTime > existingEpisode.update_time) {
                                                val updatedEpisode = com.shunlight_library.novel_reader.api.NovelApiUtils.fetchEpisodeWithRetry(
                                                    ncode = novel.ncode,
                                                    episodeNo = revisionInfo.episodeNo.toString(),
                                                    isR18 = novel.rating == 1,
                                                    noveltype = novel.noveltype
                                                )

                                                if (updatedEpisode != null) {
                                                    val episodeWithRevisionTime = updatedEpisode.copy(
                                                        update_time = revisionInfo.updateTime
                                                    )
                                                    repository.insertEpisode(episodeWithRevisionTime)
                                                    hasRevisedEpisodes = true
                                                }

                                                kotlinx.coroutines.delay(200)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "改稿チェックエラー: ${novel.ncode}", e)
                                }
                            }

                            if (hasNewEpisodes || hasRevisedEpisodes) {
                                if (novel.total_ep == 0) {
                                    newNovelsCount++
                                } else {
                                    updatedNovelsCount++
                                }

                                val revisionMsg = if (hasRevisedEpisodes) " (改稿有)" else ""
                                Log.d(TAG, "更新検出: ${novel.title} (${novel.total_ep} -> $latestEpisodeCount)$revisionMsg")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "小説 ${novel.ncode} の更新確認エラー", e)
                        errors.add("${novel.title}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "更新確認処理全体でエラー", e)
                errors.add("全体処理エラー: ${e.message}")
            }

            UpdateResult(newNovelsCount, updatedNovelsCount, errors)
        }
    }

    private suspend fun handleUpdateResults(results: UpdateResult) {
        val totalUpdates = results.newNovelsCount + results.updatedNovelsCount
        
        if (totalUpdates > 0) {
            // システム通知を送信
            sendSystemNotification(results)
            
            // アプリ内通知データを保存（次回アプリ起動時に表示）
            saveAppNotification(results)
        }
        
        Log.d(TAG, "更新結果: 新規${results.newNovelsCount}件、更新${results.updatedNovelsCount}件")
    }

    private fun sendSystemNotification(results: UpdateResult) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val totalUpdates = results.newNovelsCount + results.updatedNovelsCount
        val title = "小説更新通知"
        val content = buildString {
            if (results.newNovelsCount > 0) {
                append("新規${results.newNovelsCount}作品")
            }
            if (results.updatedNovelsCount > 0) {
                if (results.newNovelsCount > 0) append("、")
                append("更新${results.updatedNovelsCount}作品")
            }
            append("が見つかりました")
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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private suspend fun saveAppNotification(results: UpdateResult) {
        val totalUpdates = results.newNovelsCount + results.updatedNovelsCount
        
        if (totalUpdates > 0) {
            val title = "自動更新完了"
            val content = buildString {
                if (results.newNovelsCount > 0) {
                    append("新規${results.newNovelsCount}作品")
                }
                if (results.updatedNovelsCount > 0) {
                    if (results.newNovelsCount > 0) append("、")
                    append("更新${results.updatedNovelsCount}作品")
                }
                append("を検出しました")
                
                if (results.errors.isNotEmpty()) {
                    append("\n\n※一部の作品で取得エラーが発生しました")
                }
            }
            
            val notification = AppNotification(
                id = "auto_update_${System.currentTimeMillis()}",
                title = title,
                content = content,
                timestamp = System.currentTimeMillis(),
                type = NotificationType.UPDATE
            )
            
            notificationStore.addNotification(notification)
        }
        
        // エラーのみの場合も通知
        if (totalUpdates == 0 && results.errors.isNotEmpty()) {
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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "小説更新通知"
            val descriptionText = "小説の更新を通知します"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    data class UpdateResult(
        val newNovelsCount: Int,
        val updatedNovelsCount: Int,
        val errors: List<String>
    )
}