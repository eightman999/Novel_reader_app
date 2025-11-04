/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Worker that checks for novel updates.
 */
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
import com.shunlight_library.novel_reader.utils.NovelUpdateCoordinator
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

            var workCount = 0  // 新着・更新された作品数
            var episodeCount = 0  // 新着・更新された話数の合計

            // 各小説の更新をチェック
            for (novel in novels) {
                val session = NovelUpdateCoordinator.beginUpdate(novel.ncode)
                if (session == null) {
                    Log.d(TAG, "小説\"${novel.ncode}\"は他の処理中のためスキップします")
                    continue
                }

                try {
                    if (session.isCancelled()) {
                        Log.d(TAG, "小説\"${novel.ncode}\"の更新確認はキャンセルされました")
                        continue
                    }

                    // APIから最新情報を取得
                    val info = NovelApiUtils.fetchNovelInfo(
                        novel.ncode,
                        novel.rating == 1
                    )
                    if (info != null) {
                        if (session.isCancelled()) {
                            Log.d(TAG, "小説\"${novel.ncode}\"の更新確認はキャンセルされました")
                            continue
                        }

                        // 常に最新情報を保存
                        val updatedNovel = novel.copy(
                            general_all_no = info.generalAllNo,
                            updated_at = info.updatedAt,
                            userid = novel.userid ?: info.userid,
                            noveltype = novel.noveltype ?: info.noveltype,
                            length = novel.length ?: info.length
                        )
                        repository.updateNovel(updatedNovel)

                        if (session.isCancelled()) {
                            Log.d(TAG, "小説\"${novel.ncode}\"の更新確認はキャンセルされました")
                            continue
                        }

                        // 更新がある場合
                        if (info.generalAllNo > novel.general_all_no) {
                            val updateQueue = UpdateQueueEntity(
                                ncode = novel.ncode,
                                total_ep = novel.total_ep,
                                general_all_no = info.generalAllNo,
                                update_time = info.updatedAt
                            )
                            repository.insertUpdateQueue(updateQueue)

                            workCount++

                            if (novel.general_all_no == 0) {
                                // 新着作品：全話数を加算
                                episodeCount += info.generalAllNo
                            } else {
                                // 更新作品：新しく追加された話数を加算
                                episodeCount += (info.generalAllNo - novel.general_all_no)
                            }

                            Log.d(TAG, "小説「${novel.title}」の更新を検出: ${info.generalAllNo} > ${novel.general_all_no}")
                        }
                    }

                    // APIへの負荷軽減のために少し待機
                    delay(20)
                } catch (e: Exception) {
                    Log.e(TAG, "小説「${novel.title}」の更新チェック中にエラー: ${e.message}")
                    // 1つの小説のエラーで全体が失敗しないよう、継続する
                } finally {
                    NovelUpdateCoordinator.finishUpdate(session)
                }
            }

            // 更新があれば通知を送信
            if (workCount > 0) {
                sendUpdateNotification(workCount, episodeCount)
            }

            Log.d(TAG, "自動更新チェック完了: ${workCount}作品、${episodeCount}話")
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
    private fun sendUpdateNotification(workCount: Int, episodeCount: Int) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)

            // 通知の作成
            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("小説の更新があります")
                .setContentText("${workCount}作品${episodeCount}話の更新が見つかりました")
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