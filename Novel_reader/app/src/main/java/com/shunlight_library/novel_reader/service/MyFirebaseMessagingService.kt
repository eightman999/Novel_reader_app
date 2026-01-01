/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Firebase Cloud Messaging service for push notifications.
 */
package com.shunlight_library.novel_reader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.shunlight_library.novel_reader.BuildConfig
import com.shunlight_library.novel_reader.MainActivity
import com.shunlight_library.novel_reader.R
import com.shunlight_library.novel_reader.utils.AppLogger

/**
 * Firebase Cloud Messagingからの通知を受信するサービス
 *
 * このサービスは以下の機能を提供します：
 * - FCMトークンの更新管理
 * - リモート通知の受信と表示
 * - 通知データの処理
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "fcm_notification_channel"
        private const val CHANNEL_NAME = "プッシュ通知"
        private var notificationId = 1000
    }

    /**
     * FCMトークンが新規作成または更新された時に呼ばれる
     *
     * @param token 新しいFCMトークン
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        AppLogger.d(TAG, "新しいFCMトークンが生成されました: $token")

        // TODO: トークンをサーバーに送信する処理を実装
        // 例: sendTokenToServer(token)
    }

    /**
     * FCMメッセージを受信した時に呼ばれる
     *
     * @param remoteMessage 受信したメッセージ
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        AppLogger.d(TAG, "FCMメッセージを受信しました from: ${remoteMessage.from}")

        var notificationHandled = false

        // データペイロードがある場合
        if (remoteMessage.data.isNotEmpty()) {
            AppLogger.d(TAG, "メッセージデータペイロード: ${remoteMessage.data}")
            notificationHandled = handleDataPayload(remoteMessage.data)
        }

        // 通知ペイロードがある場合（データペイロードで処理されていない場合のみ）
        if (!notificationHandled) {
            remoteMessage.notification?.let {
                AppLogger.d(TAG, "メッセージ通知本文: ${it.body}")
                val title = it.title ?: "小説リーダー"
                val body = it.body ?: ""
                sendNotification(title, body, remoteMessage.data)
            }
        } else {
            AppLogger.d(TAG, "データペイロードで通知を処理済みのため、通知ペイロードをスキップします")
        }
    }

    /**
     * データペイロードを処理する
     *
     * @param data メッセージに含まれるデータ
     * @return 通知を表示した場合はtrue、そうでない場合はfalse
     */
    private fun handleDataPayload(data: Map<String, String>): Boolean {
        // データペイロードの種類に応じて処理を分岐
        when (data["type"]) {
            "novel_update" -> {
                // 小説更新通知
                val ncode = data["ncode"] ?: return false
                val title = data["title"] ?: "小説が更新されました"
                val body = data["body"] ?: "新しいエピソードが公開されました"
                sendNotification(title, body, data)
                return true
            }
            "announcement" -> {
                // お知らせ通知
                val title = data["title"] ?: "お知らせ"
                val body = data["body"] ?: ""
                sendNotification(title, body, data)
                return true
            }
            else -> {
                // デフォルト処理
                val title = data["title"] ?: "通知"
                val body = data["body"] ?: ""
                sendNotification(title, body, data)
                return true
            }
        }
    }

    /**
     * 通知を表示する
     *
     * @param title 通知のタイトル
     * @param messageBody 通知の本文
     * @param data 通知に含まれる追加データ
     */
    private fun sendNotification(title: String, messageBody: String, data: Map<String, String> = emptyMap()) {
        // 通知チャネルを作成（Android 8.0以降）
        createNotificationChannel()

        // アプリ起動用のIntent
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // データペイロードをIntentに追加
            data.forEach { (key, value) ->
                putExtra(key, value)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 通知を作成
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // アプリアイコン
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true) // タップで通知を自動削除
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 優先度高
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody)) // 長文対応

        // 通知を表示
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId++, notificationBuilder.build())

        AppLogger.d(TAG, "通知を表示しました: $title - $messageBody")
    }

    /**
     * 通知チャネルを作成する（Android 8.0以降）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Firebase Cloud Messagingからのプッシュ通知"
                enableLights(true)
                enableVibration(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
