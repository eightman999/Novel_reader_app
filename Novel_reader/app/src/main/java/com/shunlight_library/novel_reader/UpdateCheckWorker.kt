package com.shunlight_library.novel_reader.worker

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shunlight_library.novel_reader.NovelReaderApplication
import com.shunlight_library.novel_reader.R
import com.shunlight_library.novel_reader.api.NovelApiUtils
import com.shunlight_library.novel_reader.data.entity.UpdateQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class UpdateCheckWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "UpdateCheckWorker"
        private const val NOTIFICATION_CHANNEL_ID = "novel_update_channel"
        private const val NOTIFICATION_GROUP_ID = "novel_updates"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "自動更新チェック開始")

            // リポジトリの取得
            val repository = NovelReaderApplication.getRepository()

            // 更新対象の小説を取得
            val novels = repository.getNovelsForUpdate()
            if (novels.isEmpty()) {
                Log.d(TAG, "更新対象の小説がありません")
                return@withContext Result.success()
            }

            Log.d(TAG, "${novels.size}件の小説の更新をチェックします")

            // 通知チャンネルの作成
            createNotificationChannel()

            var updatedCount = 0
            var newCount = 0

            // 各小説の更新をチェック
            for (novel in novels) {
                try {
                    // APIから最新情報を取得
                    val (newGeneralAllNo, newUpdatedAt) = NovelApiUtils.fetchNovelInfo(
                        novel.ncode,
                        novel.rating == 1
                    )

                    // 更新がある場合
                    if (newGeneralAllNo > novel.general_all_no) {
                        // 小説情報を更新
                        val updatedNovel = novel.copy(
                            general_all_no = newGeneralAllNo,
                            updated_at = newUpdatedAt
                        )
                        repository.updateNovel(updatedNovel)

                        // 更新キューに追加
                        val updateQueue = UpdateQueueEntity(
                            ncode = novel.ncode,
                            total_ep = novel.total_ep,
                            general_all_no = newGeneralAllNo,
                            update_time = newUpdatedAt
                        )
                        repository.insertUpdateQueue(updateQueue)

                        // 新規か更新かをカウント
                        if (novel.general_all_no == 0) {
                            newCount++
                        } else {
                            updatedCount++
                        }

                        Log.d(TAG, "小説「${novel.title}」の更新を検出: $newGeneralAllNo > ${novel.general_all_no}")
                    }

                    // APIへの負荷軽減のために少し待機
                    delay(20)
                } catch (e: Exception) {
                    Log.e(TAG, "小説「${novel.title}」の更新チェック中にエラー: ${e.message}")
                    // 1つの小説のエラーで全体が失敗しないよう、継続する
                }
            }

            // 更新があれば通知を送信
            if (newCount > 0 || updatedCount > 0) {
                sendUpdateNotification(newCount, updatedCount)
            }

            Log.d(TAG, "自動更新チェック完了: 新着${newCount}件、更新あり${updatedCount}件")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "自動更新チェック処理中にエラー: ${e.message}", e)
            Result.failure()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(
            NOTIFICATION_CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_DEFAULT
        )
            .setName("小説更新通知")
            .setDescription("小説の更新をお知らせします")
            .build()

        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    private fun sendUpdateNotification(newCount: Int, updatedCount: Int) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)

            // 通知の作成
            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("小説の更新があります")
                .setContentText("新着${newCount}件・更新あり${updatedCount}件の小説が見つかりました")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setGroup(NOTIFICATION_GROUP_ID)
                .setAutoCancel(true)
                .build()

            // 通知の送信
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {
            Log.e(TAG, "通知送信エラー: ${e.message}", e)
        }
    }
}