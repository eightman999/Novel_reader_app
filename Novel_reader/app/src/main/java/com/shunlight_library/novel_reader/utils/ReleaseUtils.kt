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
import com.shunlight_library.novel_reader.BuildConfig
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

    suspend fun checkForNewRelease(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val connection = URL(RELEASE_API).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                    val json = JSONObject(response)
                    val tag = json.getString("tag_name")
                    val store = SettingsStore(context)
                    val last = store.getLastNotifiedRelease()
                    if (isNewerVersion(tag, BuildConfig.VERSION_NAME) && last != tag) {
                        sendSystemNotification(context, tag)
                        NotificationStore(context).addNotification(
                            AppNotification(
                                id = "release_${System.currentTimeMillis()}",
                                title = "新しいバージョン $tag",
                                content = "GitHubに新しいリリースがあります",
                                timestamp = System.currentTimeMillis(),
                                type = NotificationType.INFO
                            )
                        )
                        store.saveLastNotifiedRelease(tag)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "release check failed", e)
            }
        }
    }

    private fun sendSystemNotification(context: Context, version: String) {
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
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("新しいバージョン $version")
            .setContentText("GitHubで新しいリリースが公開されています")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
