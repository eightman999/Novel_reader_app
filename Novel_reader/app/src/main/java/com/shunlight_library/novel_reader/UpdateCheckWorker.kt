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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.SocketTimeoutException

class UpdateCheckWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "UpdateCheckWorker"
        private const val NOTIFICATION_CHANNEL_ID = "novel_update_channel"
        private const val NOTIFICATION_GROUP_ID = "novel_updates"

        // ユーザーエージェントリスト
        private val USER_AGENTS = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:54.0) Gecko/20100101 Firefox/54.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/602.3.12 (KHTML, like Gecko) Version/10.0.3 Safari/602.3.12",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.157 Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 10_3_1 like Mac OS X) AppleWebKit/603.1.30 (KHTML, like Gecko) Version/10.0 Mobile/14E304 Safari/602.1",
            "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3",
            "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:54.0) Gecko/20100101 Firefox/54.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/602.3.12 (KHTML, like Gecko) Version/10.0.3 Safari/602.3.12",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.157 Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 10_3_1 like Mac OS X) AppleWebKit/603.1.30 (KHTML, like Gecko) Version/10.0 Mobile/14E304 Safari/602.1"
        )
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
            var fixedCount = 0

            // 各小説の更新をチェック
            for (novel in novels) {
                try {
                    // エラー修正の実行
                    val fixedEpisodes = fixEpisodeErrors(novel.ncode, novel.rating == 1)
                    fixedCount += fixedEpisodes

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
            if (newCount > 0 || updatedCount > 0 || fixedCount > 0) {
                sendUpdateNotification(newCount, updatedCount, fixedCount)
            }

            Log.d(TAG, "自動更新チェック完了: 新着${newCount}件、更新あり${updatedCount}件、エラー修正${fixedCount}件")
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
    private fun sendUpdateNotification(newCount: Int, updatedCount: Int, fixedCount: Int) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)

            // 通知の作成
            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("小説の更新があります")
                .setContentText("新着${newCount}件・更新あり${updatedCount}件・エラー修正${fixedCount}件の小説が見つかりました")
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

    // リトライロジックとランダムユーザーエージェント選択を実装した関数
    private suspend fun fetchWithRetry(url: String, maxRetries: Int = 3): Document? {
        var retryCount = 0
        var lastException: Exception? = null

        while (retryCount < maxRetries) {
            try {
                // ランダムなユーザーエージェントを選択
                val randomUserAgent = USER_AGENTS.random()

                Log.d(TAG, "接続試行 ${retryCount + 1}/$maxRetries: $url")
                Log.d(TAG, "使用中のユーザーエージェント: $randomUserAgent")

                return Jsoup.connect(url)
                    .userAgent(randomUserAgent)
                    .timeout(30000)  // 30秒に設定
                    .maxBodySize(0)  // 無制限のボディサイズ
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .get()
            } catch (e: SocketTimeoutException) {
                lastException = e
                Log.w(TAG, "タイムアウト発生 ${retryCount + 1}/$maxRetries: $url - ${e.message}")
                retryCount++

                // 指数バックオフ（リトライ間隔を徐々に増加）
                val delayTime = 2000L * (retryCount + 1)
                Log.d(TAG, "$delayTime ミリ秒後に再試行します")
                delay(delayTime)
            } catch (e: IOException) {
                lastException = e
                Log.w(TAG, "IO例外発生 ${retryCount + 1}/$maxRetries: $url - ${e.message}")
                retryCount++

                // 指数バックオフ
                val delayTime = 2000L * (retryCount + 1)
                Log.d(TAG, "$delayTime ミリ秒後に再試行します")
                delay(delayTime)
            }
        }

        // 最大リトライ回数を超えた場合
        Log.e(TAG, "最大リトライ回数を超えました: $url", lastException)
        return null
    }

    // エラー修正機能を追加
    private suspend fun fixEpisodeErrors(ncode: String, isR18: Boolean): Int = withContext(Dispatchers.IO) {
        try {
            val repository = NovelReaderApplication.getRepository()
            var fixedCount = 0

            // エピソードを取得
            val episodes = repository.getEpisodesByNcode(ncode).first()

            // エラーのあるエピソードを見つける
            val errorEpisodes = episodes.filter { episode ->
                episode.body.isEmpty() || episode.e_title.isEmpty()
            }

            // エピソードの番号リスト（IntとStringの両方を持つ）
            val episodeNumberMap = episodes.associate { episode ->
                val numericValue = episode.episode_no.toIntOrNull() ?: 0
                numericValue to episode.episode_no
            }

            // 最大エピソード番号を取得
            val maxEpisodeNo = episodeNumberMap.keys.maxOrNull() ?: 0

            // 欠番リスト（1から最大の番号の範囲で）
            val missingEpisodes = (1..maxEpisodeNo).filter { epNo ->
                !episodeNumberMap.containsKey(epNo)
            }

            // エラーのあるエピソードの番号リスト
            val errorEpisodeNumbers = errorEpisodes.mapNotNull { episode -> episode.episode_no.toIntOrNull() }

            // 再取得対象の番号リスト（エラーと欠番を合わせる）
            val redownloadTargets = (errorEpisodeNumbers + missingEpisodes).distinct().sorted()

            if (redownloadTargets.isEmpty()) {
                return@withContext 0
            }

            // エピソードの再取得
            for (episodeNo in redownloadTargets) {
                try {
                    val baseUrl = if (isR18) {
                        "https://novel18.syosetu.com"
                    } else {
                        "https://ncode.syosetu.com"
                    }

                    val url = "$baseUrl/$ncode/$episodeNo/"
                    val doc = fetchWithRetry(url)

                    if (doc != null) {
                        val title = doc.select("h1.p-novel__title.p-novel__title--rensai").text()
                        val bodyElements = doc.select("div.p-novel__body > div")
                        val body = StringBuilder()

                        if (bodyElements.isNotEmpty()) {
                            bodyElements.forEachIndexed { index, element ->
                                body.append(element.outerHtml())
                                if (index < bodyElements.size - 1) {
                                    body.append("\n<hr>\n")
                                }
                            }
                        }

                        if (title.isNotEmpty() && body.isNotEmpty()) {
                            val episode = repository.getEpisodeByNcodeAndNo(ncode, episodeNo.toString())
                            if (episode != null) {
                                val updatedEpisode = episode.copy(
                                    e_title = title,
                                    body = body.toString()
                                )
                                repository.updateEpisode(updatedEpisode)
                                fixedCount++
                            }
                        }
                    }

                    // サーバーに負荷をかけないように少し待機
                    delay(200)
                } catch (e: Exception) {
                    Log.e(TAG, "エピソード再取得エラー: $ncode-$episodeNo", e)
                }
            }

            fixedCount
        } catch (e: Exception) {
            Log.e(TAG, "エラー修正処理中にエラー: ${e.message}", e)
            0
        }
    }
}