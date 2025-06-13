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
    val type: NotificationType = NotificationType.UPDATE
)

enum class NotificationType {
    UPDATE, // 更新通知
    ERROR,  // エラー通知
    INFO    // 情報通知
}

class NotificationStore(private val context: Context) {
    
    companion object {
        val PENDING_NOTIFICATIONS = stringSetPreferencesKey("pending_notifications")
        private const val NOTIFICATION_TITLE_PREFIX = "notification_title_"
        private const val NOTIFICATION_CONTENT_PREFIX = "notification_content_"
        private const val NOTIFICATION_TIMESTAMP_PREFIX = "notification_timestamp_"
        private const val NOTIFICATION_TYPE_PREFIX = "notification_type_"
        private const val NOTIFICATION_READ_PREFIX = "notification_read_"
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
            
            if (title != null && content != null && timestamp != null && typeString != null) {
                AppNotification(
                    id = id,
                    title = title,
                    content = content,
                    timestamp = timestamp,
                    isRead = isRead,
                    type = try { NotificationType.valueOf(typeString) } catch (e: Exception) { NotificationType.INFO }
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