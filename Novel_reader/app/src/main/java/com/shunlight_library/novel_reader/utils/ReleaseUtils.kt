/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Utility for release checks and notifications.
 */
package com.shunlight_library.novel_reader.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.shunlight_library.novel_reader.AppInfo
import com.shunlight_library.novel_reader.R
import com.shunlight_library.novel_reader.SettingsStore
import com.shunlight_library.novel_reader.data.AppNotification
import com.shunlight_library.novel_reader.data.NotificationStore
import com.shunlight_library.novel_reader.data.NotificationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object ReleaseUtils {
    private const val TAG = "ReleaseUtils"
    private const val CHANNEL_ID = "novel_update_channel"
    private const val NOTIFICATION_ID = 2001
    private const val RELEASE_API = "https://api.github.com/repos/eightman999/Novel_reader_app/releases/latest"
    private const val RELEASE_PAGE = "https://github.com/eightman999/Novel_reader_app/releases/latest"

    /**
     * バージョン文字列を比較して最新かどうかを判定する
     * @param latest APIから取得した最新バージョン
     * @param current 現在のアプリバージョン
     * @return latestがcurrentより新しい場合はtrue
     */
    private fun isNewerVersion(latest: String, current: String): Boolean {
        val l = latest.trimStart('v', 'V').split(".")
        val c = current.trimStart('v', 'V').split(".")
        val size = maxOf(l.size, c.size)
        for (i in 0 until size) {
            val lv = l.getOrNull(i)?.toIntOrNull() ?: 0
            val cv = c.getOrNull(i)?.toIntOrNull() ?: 0
            if (lv != cv) return lv > cv
        }
        return false
    }

    /**
     * GitHub上の最新リリースをチェックし、必要に応じて通知を行う
     * ネットワーク処理はIOスレッドで実行される
     */
    suspend fun checkForNewRelease(context: Context) {
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(RELEASE_API).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                    val json = JSONObject(response)

                    // リリース情報を取得
                    val tagName = json.getString("tag_name")
                    val name = json.optString("name", tagName)
                    val body = json.optString("body", "")
                    val publishedAt = json.optString("published_at", "")
                    val htmlUrl = json.optString("html_url", RELEASE_PAGE)

                    val store = SettingsStore(context)
                    val last = store.getLastNotifiedRelease()
                    if (isNewerVersion(tagName, AppInfo.VERSION_NAME) && last != tagName) {
                        sendSystemNotification(context, tagName, name, publishedAt)
                        NotificationStore(context).addNotification(
                            AppNotification(
                                id = "release_${System.currentTimeMillis()}",
                                title = "新しいバージョン $tagName",
                                content = if (name.isNotEmpty() && name != tagName) {
                                    "$name\n公開日時: ${formatPublishedDate(publishedAt)}"
                                } else {
                                    "公開日時: ${formatPublishedDate(publishedAt)}"
                                },
                                timestamp = System.currentTimeMillis(),
                                type = NotificationType.INFO,
                                releaseTagName = tagName,
                                releaseName = name,
                                releaseBody = body,
                                releasePublishedAt = publishedAt,
                                releaseUrl = htmlUrl
                            )
                        )
                        store.saveLastNotifiedRelease(tagName)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "release check failed", e)
            } finally {
                connection?.disconnect()
            }
        }
    }

    /**
     * 新しいバージョンをシステム通知で知らせる
     * @param context コンテキスト
     * @param tagName バージョンタグ名
     * @param name リリース名
     * @param publishedAt 公開日時（ISO 8601形式）
     */
    private fun sendSystemNotification(context: Context, tagName: String, name: String, publishedAt: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, "Updates", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RELEASE_PAGE))
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 通知のタイトルと内容を構築
        val title = if (name.isNotEmpty() && name != tagName) {
            "新しいバージョン $tagName - $name"
        } else {
            "新しいバージョン $tagName"
        }
        val content = "公開日時: ${formatPublishedDate(publishedAt)}"

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * ISO 8601形式の日時を読みやすい形式に変換
     * @param isoDate ISO 8601形式の日時（例: "2025-11-28T12:00:00Z"）
     * @return フォーマット済みの日時文字列（例: "2025-11-28 12:00"）
     */
    private fun formatPublishedDate(isoDate: String): String {
        if (isoDate.isEmpty()) return "不明"
        return try {
            // ISO 8601形式をパース（例: 2025-11-28T12:00:00Z）
            val instant = java.time.Instant.parse(isoDate)
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(java.time.ZoneId.systemDefault())
            formatter.format(instant)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse date: $isoDate", e)
            isoDate
        }
    }
}
