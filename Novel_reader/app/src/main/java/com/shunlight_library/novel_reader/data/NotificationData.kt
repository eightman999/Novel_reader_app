/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * DataStore utilities for app notifications.
 */
package com.shunlight_library.novel_reader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

// アプリ内通知用のDataStore
val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(name = "notifications")

data class AppNotification(
    val id: String,
    val title: String,
    val content: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val type: NotificationType = NotificationType.UPDATE,
    // リリース情報用フィールド（リリース通知の場合のみ使用）
    val releaseTagName: String? = null,
    val releaseName: String? = null,
    val releaseBody: String? = null,
    val releasePublishedAt: String? = null,
    val releaseUrl: String? = null
)

enum class NotificationType {
    UPDATE, // 更新通知
    ERROR,  // エラー通知
    INFO    // 情報通知
}

class NotificationStore(private val context: Context) {

    companion object {
        val PENDING_NOTIFICATIONS = stringSetPreferencesKey("pending_notifications")
        private const val MAX_NOTIFICATION_COUNT = 100
        private const val NOTIFICATION_TITLE_PREFIX = "notification_title_"
        private const val NOTIFICATION_CONTENT_PREFIX = "notification_content_"
        private const val NOTIFICATION_TIMESTAMP_PREFIX = "notification_timestamp_"
        private const val NOTIFICATION_TYPE_PREFIX = "notification_type_"
        private const val NOTIFICATION_READ_PREFIX = "notification_read_"
        // リリース情報用キー
        private const val NOTIFICATION_RELEASE_TAG_PREFIX = "notification_release_tag_"
        private const val NOTIFICATION_RELEASE_NAME_PREFIX = "notification_release_name_"
        private const val NOTIFICATION_RELEASE_BODY_PREFIX = "notification_release_body_"
        private const val NOTIFICATION_RELEASE_PUBLISHED_PREFIX = "notification_release_published_"
        private const val NOTIFICATION_RELEASE_URL_PREFIX = "notification_release_url_"
    }

    suspend fun addNotification(notification: AppNotification) {
        context.notificationDataStore.edit { preferences ->
            // 通知IDリストに追加
            val currentNotifications = preferences[PENDING_NOTIFICATIONS] ?: emptySet()
            preferences[PENDING_NOTIFICATIONS] = currentNotifications + notification.id

            // 通知データを保存
            preferences[stringPreferencesKey("${NOTIFICATION_TITLE_PREFIX}${notification.id}")] = notification.title
            preferences[stringPreferencesKey("${NOTIFICATION_CONTENT_PREFIX}${notification.id}")] = notification.content
            preferences[longPreferencesKey("${NOTIFICATION_TIMESTAMP_PREFIX}${notification.id}")] = notification.timestamp
            preferences[stringPreferencesKey("${NOTIFICATION_TYPE_PREFIX}${notification.id}")] = notification.type.name
            preferences[booleanPreferencesKey("${NOTIFICATION_READ_PREFIX}${notification.id}")] = notification.isRead

            // リリース情報を保存（nullでない場合のみ）
            notification.releaseTagName?.let {
                preferences[stringPreferencesKey("${NOTIFICATION_RELEASE_TAG_PREFIX}${notification.id}")] = it
            }
            notification.releaseName?.let {
                preferences[stringPreferencesKey("${NOTIFICATION_RELEASE_NAME_PREFIX}${notification.id}")] = it
            }
            notification.releaseBody?.let {
                preferences[stringPreferencesKey("${NOTIFICATION_RELEASE_BODY_PREFIX}${notification.id}")] = it
            }
            notification.releasePublishedAt?.let {
                preferences[stringPreferencesKey("${NOTIFICATION_RELEASE_PUBLISHED_PREFIX}${notification.id}")] = it
            }
            notification.releaseUrl?.let {
                preferences[stringPreferencesKey("${NOTIFICATION_RELEASE_URL_PREFIX}${notification.id}")] = it
            }

            enforceNotificationLimit(preferences)
        }
    }

    private fun enforceNotificationLimit(preferences: MutablePreferences) {
        val notificationIds = preferences[PENDING_NOTIFICATIONS] ?: emptySet()
        if (notificationIds.size <= MAX_NOTIFICATION_COUNT) {
            return
        }

        val sortedByNewest = notificationIds
            .map { id ->
                val timestamp = preferences[longPreferencesKey("${NOTIFICATION_TIMESTAMP_PREFIX}${id}")] ?: Long.MIN_VALUE
                id to timestamp
            }
            .sortedByDescending { it.second }

        val idsToKeep = sortedByNewest
            .take(MAX_NOTIFICATION_COUNT)
            .mapTo(linkedSetOf<String>()) { it.first }

        val idsToRemove = notificationIds - idsToKeep

        preferences[PENDING_NOTIFICATIONS] = idsToKeep

        idsToRemove.forEach { id ->
            preferences.remove(stringPreferencesKey("${NOTIFICATION_TITLE_PREFIX}${id}"))
            preferences.remove(stringPreferencesKey("${NOTIFICATION_CONTENT_PREFIX}${id}"))
            preferences.remove(longPreferencesKey("${NOTIFICATION_TIMESTAMP_PREFIX}${id}"))
            preferences.remove(stringPreferencesKey("${NOTIFICATION_TYPE_PREFIX}${id}"))
            preferences.remove(booleanPreferencesKey("${NOTIFICATION_READ_PREFIX}${id}"))
            // リリース情報も削除
            preferences.remove(stringPreferencesKey("${NOTIFICATION_RELEASE_TAG_PREFIX}${id}"))
            preferences.remove(stringPreferencesKey("${NOTIFICATION_RELEASE_NAME_PREFIX}${id}"))
            preferences.remove(stringPreferencesKey("${NOTIFICATION_RELEASE_BODY_PREFIX}${id}"))
            preferences.remove(stringPreferencesKey("${NOTIFICATION_RELEASE_PUBLISHED_PREFIX}${id}"))
            preferences.remove(stringPreferencesKey("${NOTIFICATION_RELEASE_URL_PREFIX}${id}"))
        }
    }

    suspend fun getAllNotifications(): List<AppNotification> {
        val preferences = context.notificationDataStore.data.first()
        val notificationIds = preferences[PENDING_NOTIFICATIONS] ?: emptySet()

        return notificationIds.mapNotNull { id ->
            val title = preferences[stringPreferencesKey("${NOTIFICATION_TITLE_PREFIX}${id}")]
            val content = preferences[stringPreferencesKey("${NOTIFICATION_CONTENT_PREFIX}${id}")]
            val timestamp = preferences[longPreferencesKey("${NOTIFICATION_TIMESTAMP_PREFIX}${id}")]
            val typeString = preferences[stringPreferencesKey("${NOTIFICATION_TYPE_PREFIX}${id}")]
            val isRead = preferences[booleanPreferencesKey("${NOTIFICATION_READ_PREFIX}${id}")] ?: false

            // リリース情報を読み込み（nullableなので存在しない場合はnull）
            val releaseTagName = preferences[stringPreferencesKey("${NOTIFICATION_RELEASE_TAG_PREFIX}${id}")]
            val releaseName = preferences[stringPreferencesKey("${NOTIFICATION_RELEASE_NAME_PREFIX}${id}")]
            val releaseBody = preferences[stringPreferencesKey("${NOTIFICATION_RELEASE_BODY_PREFIX}${id}")]
            val releasePublishedAt = preferences[stringPreferencesKey("${NOTIFICATION_RELEASE_PUBLISHED_PREFIX}${id}")]
            val releaseUrl = preferences[stringPreferencesKey("${NOTIFICATION_RELEASE_URL_PREFIX}${id}")]

            if (title != null && content != null && timestamp != null && typeString != null) {
                AppNotification(
                    id = id,
                    title = title,
                    content = content,
                    timestamp = timestamp,
                    isRead = isRead,
                    type = try { NotificationType.valueOf(typeString) } catch (e: Exception) { NotificationType.INFO },
                    releaseTagName = releaseTagName,
                    releaseName = releaseName,
                    releaseBody = releaseBody,
                    releasePublishedAt = releasePublishedAt,
                    releaseUrl = releaseUrl
                )
            } else null
        }.sortedByDescending { it.timestamp }
    }

    suspend fun getUnreadNotifications(): List<AppNotification> {
        return getAllNotifications().filter { !it.isRead }
    }

    suspend fun markAsRead(notificationId: String) {
        context.notificationDataStore.edit { preferences ->
            preferences[booleanPreferencesKey("${NOTIFICATION_READ_PREFIX}${notificationId}")] = true
        }
    }

    suspend fun markAllAsRead() {
        val notifications = getAllNotifications()
        context.notificationDataStore.edit { preferences ->
            notifications.forEach { notification ->
                preferences[booleanPreferencesKey("${NOTIFICATION_READ_PREFIX}${notification.id}")] = true
            }
        }
    }

    suspend fun deleteNotification(notificationId: String) {
        context.notificationDataStore.edit { preferences ->
            // IDリストから削除
            val currentNotifications = preferences[PENDING_NOTIFICATIONS] ?: emptySet()
            preferences[PENDING_NOTIFICATIONS] = currentNotifications - notificationId

            // 関連データも削除
            preferences.remove(stringPreferencesKey("${NOTIFICATION_TITLE_PREFIX}${notificationId}"))
            preferences.remove(stringPreferencesKey("${NOTIFICATION_CONTENT_PREFIX}${notificationId}"))
            preferences.remove(longPreferencesKey("${NOTIFICATION_TIMESTAMP_PREFIX}${notificationId}"))
            preferences.remove(stringPreferencesKey("${NOTIFICATION_TYPE_PREFIX}${notificationId}"))
            preferences.remove(booleanPreferencesKey("${NOTIFICATION_READ_PREFIX}${notificationId}"))
            // リリース情報も削除
            preferences.remove(stringPreferencesKey("${NOTIFICATION_RELEASE_TAG_PREFIX}${notificationId}"))
            preferences.remove(stringPreferencesKey("${NOTIFICATION_RELEASE_NAME_PREFIX}${notificationId}"))
            preferences.remove(stringPreferencesKey("${NOTIFICATION_RELEASE_BODY_PREFIX}${notificationId}"))
            preferences.remove(stringPreferencesKey("${NOTIFICATION_RELEASE_PUBLISHED_PREFIX}${notificationId}"))
            preferences.remove(stringPreferencesKey("${NOTIFICATION_RELEASE_URL_PREFIX}${notificationId}"))
        }
    }

    suspend fun clearAllNotifications() {
        val notifications = getAllNotifications()
        context.notificationDataStore.edit { preferences ->
            // IDリストをクリア
            preferences[PENDING_NOTIFICATIONS] = emptySet()

            // 全ての関連データを削除
            notifications.forEach { notification ->
                preferences.remove(stringPreferencesKey("${NOTIFICATION_TITLE_PREFIX}${notification.id}"))
                preferences.remove(stringPreferencesKey("${NOTIFICATION_CONTENT_PREFIX}${notification.id}"))
                preferences.remove(longPreferencesKey("${NOTIFICATION_TIMESTAMP_PREFIX}${notification.id}"))
                preferences.remove(stringPreferencesKey("${NOTIFICATION_TYPE_PREFIX}${notification.id}"))
                preferences.remove(booleanPreferencesKey("${NOTIFICATION_READ_PREFIX}${notification.id}"))
                // リリース情報も削除
                preferences.remove(stringPreferencesKey("${NOTIFICATION_RELEASE_TAG_PREFIX}${notification.id}"))
                preferences.remove(stringPreferencesKey("${NOTIFICATION_RELEASE_NAME_PREFIX}${notification.id}"))
                preferences.remove(stringPreferencesKey("${NOTIFICATION_RELEASE_BODY_PREFIX}${notification.id}"))
                preferences.remove(stringPreferencesKey("${NOTIFICATION_RELEASE_PUBLISHED_PREFIX}${notification.id}"))
                preferences.remove(stringPreferencesKey("${NOTIFICATION_RELEASE_URL_PREFIX}${notification.id}"))
            }
        }
    }

    val unreadCountFlow: Flow<Int> = context.notificationDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val notificationIds = preferences[PENDING_NOTIFICATIONS] ?: emptySet()
            notificationIds.count { id ->
                !(preferences[booleanPreferencesKey("${NOTIFICATION_READ_PREFIX}${id}")] ?: false)
            }
        }
}