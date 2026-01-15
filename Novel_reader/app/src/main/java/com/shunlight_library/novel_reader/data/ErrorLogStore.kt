/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * DataStore utilities for error logs.
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
import org.json.JSONArray
import org.json.JSONObject

val Context.errorLogDataStore: DataStore<Preferences> by preferencesDataStore(name = "error_logs")

class ErrorLogStore(private val context: Context) {

    companion object {
        private const val ERROR_LOGS = "error_logs"
        private const val MAX_ERROR_LOG_COUNT = 100
        private const val ERROR_LOG_ID_PREFIX = "error_log_id_"
        private const val ERROR_LOG_TIMESTAMP_PREFIX = "error_log_timestamp_"
        private const val ERROR_LOG_NCODE_PREFIX = "error_log_ncode_"
        private const val ERROR_LOG_NOVEL_TITLE_PREFIX = "error_log_novel_title_"
        private const val ERROR_LOG_EPISODE_NO_PREFIX = "error_log_episode_no_"
        private const val ERROR_LOG_EPISODE_TITLE_PREFIX = "error_log_episode_title_"
        private const val ERROR_LOG_ERROR_TYPE_PREFIX = "error_log_error_type_"
        private const val ERROR_LOG_ERROR_MESSAGE_PREFIX = "error_log_error_message_"
        private const val ERROR_LOG_STACK_TRACE_PREFIX = "error_log_stack_trace_"
    }

    suspend fun addErrorLog(errorLog: ErrorLog) {
        context.errorLogDataStore.edit { preferences ->
            val currentLogs = preferences[stringSetPreferencesKey(ERROR_LOGS)] ?: emptySet()
            preferences[stringSetPreferencesKey(ERROR_LOGS)] = currentLogs + errorLog.id

            preferences[stringPreferencesKey("${ERROR_LOG_ID_PREFIX}${errorLog.id}")] = errorLog.id
            preferences[longPreferencesKey("${ERROR_LOG_TIMESTAMP_PREFIX}${errorLog.id}")] = errorLog.timestamp
            preferences[stringPreferencesKey("${ERROR_LOG_NCODE_PREFIX}${errorLog.id}")] = errorLog.ncode
            preferences[stringPreferencesKey("${ERROR_LOG_NOVEL_TITLE_PREFIX}${errorLog.id}")] = errorLog.novelTitle
            preferences[intPreferencesKey("${ERROR_LOG_EPISODE_NO_PREFIX}${errorLog.id}")] = errorLog.episodeNo
            preferences[stringPreferencesKey("${ERROR_LOG_EPISODE_TITLE_PREFIX}${errorLog.id}")] = errorLog.episodeTitle ?: ""
            preferences[stringPreferencesKey("${ERROR_LOG_ERROR_TYPE_PREFIX}${errorLog.id}")] = errorLog.errorType
            preferences[stringPreferencesKey("${ERROR_LOG_ERROR_MESSAGE_PREFIX}${errorLog.id}")] = errorLog.errorMessage
            preferences[stringPreferencesKey("${ERROR_LOG_STACK_TRACE_PREFIX}${errorLog.id}")] = errorLog.stackTrace ?: ""

            enforceErrorLogLimit(preferences)
        }
    }

    private fun enforceErrorLogLimit(preferences: MutablePreferences) {
        val logIds = preferences[stringSetPreferencesKey(ERROR_LOGS)] ?: emptySet()
        if (logIds.size <= MAX_ERROR_LOG_COUNT) {
            return
        }

        val sortedByNewest = logIds
            .map { id ->
                val timestamp = preferences[longPreferencesKey("${ERROR_LOG_TIMESTAMP_PREFIX}${id}")] ?: Long.MIN_VALUE
                id to timestamp
            }
            .sortedByDescending { it.second }

        val idsToKeep = sortedByNewest
            .take(MAX_ERROR_LOG_COUNT)
            .mapTo(linkedSetOf<String>()) { it.first }

        val idsToRemove = logIds - idsToKeep

        preferences[stringSetPreferencesKey(ERROR_LOGS)] = idsToKeep

        idsToRemove.forEach { id ->
            preferences.remove(stringPreferencesKey("${ERROR_LOG_ID_PREFIX}${id}"))
            preferences.remove(longPreferencesKey("${ERROR_LOG_TIMESTAMP_PREFIX}${id}"))
            preferences.remove(stringPreferencesKey("${ERROR_LOG_NCODE_PREFIX}${id}"))
            preferences.remove(stringPreferencesKey("${ERROR_LOG_NOVEL_TITLE_PREFIX}${id}"))
            preferences.remove(intPreferencesKey("${ERROR_LOG_EPISODE_NO_PREFIX}${id}"))
            preferences.remove(stringPreferencesKey("${ERROR_LOG_EPISODE_TITLE_PREFIX}${id}"))
            preferences.remove(stringPreferencesKey("${ERROR_LOG_ERROR_TYPE_PREFIX}${id}"))
            preferences.remove(stringPreferencesKey("${ERROR_LOG_ERROR_MESSAGE_PREFIX}${id}"))
            preferences.remove(stringPreferencesKey("${ERROR_LOG_STACK_TRACE_PREFIX}${id}"))
        }
    }

    suspend fun getAllErrorLogs(): List<ErrorLog> {
        val preferences = context.errorLogDataStore.data.first()
        val logIds = preferences[stringSetPreferencesKey(ERROR_LOGS)] ?: emptySet()

        return logIds.mapNotNull { id ->
            val timestamp = preferences[longPreferencesKey("${ERROR_LOG_TIMESTAMP_PREFIX}${id}")]
            val ncode = preferences[stringPreferencesKey("${ERROR_LOG_NCODE_PREFIX}${id}")]
            val novelTitle = preferences[stringPreferencesKey("${ERROR_LOG_NOVEL_TITLE_PREFIX}${id}")]
            val episodeNo = preferences[intPreferencesKey("${ERROR_LOG_EPISODE_NO_PREFIX}${id}")]
            val episodeTitle = preferences[stringPreferencesKey("${ERROR_LOG_EPISODE_TITLE_PREFIX}${id}")]
            val errorType = preferences[stringPreferencesKey("${ERROR_LOG_ERROR_TYPE_PREFIX}${id}")]
            val errorMessage = preferences[stringPreferencesKey("${ERROR_LOG_ERROR_MESSAGE_PREFIX}${id}")]
            val stackTrace = preferences[stringPreferencesKey("${ERROR_LOG_STACK_TRACE_PREFIX}${id}")]

            if (timestamp != null && ncode != null && novelTitle != null && episodeNo != null && errorType != null && errorMessage != null) {
                ErrorLog(
                    id = id,
                    timestamp = timestamp,
                    ncode = ncode,
                    novelTitle = novelTitle,
                    episodeNo = episodeNo,
                    episodeTitle = episodeTitle?.takeIf { it.isNotEmpty() },
                    errorType = errorType,
                    errorMessage = errorMessage,
                    stackTrace = stackTrace?.takeIf { it.isNotEmpty() }
                )
            } else null
        }.sortedByDescending { it.timestamp }
    }

    suspend fun deleteErrorLog(logId: String) {
        context.errorLogDataStore.edit { preferences ->
            val currentLogs = preferences[stringSetPreferencesKey(ERROR_LOGS)] ?: emptySet()
            preferences[stringSetPreferencesKey(ERROR_LOGS)] = currentLogs - logId

            preferences.remove(stringPreferencesKey("${ERROR_LOG_ID_PREFIX}${logId}"))
            preferences.remove(longPreferencesKey("${ERROR_LOG_TIMESTAMP_PREFIX}${logId}"))
            preferences.remove(stringPreferencesKey("${ERROR_LOG_NCODE_PREFIX}${logId}"))
            preferences.remove(stringPreferencesKey("${ERROR_LOG_NOVEL_TITLE_PREFIX}${logId}"))
            preferences.remove(intPreferencesKey("${ERROR_LOG_EPISODE_NO_PREFIX}${logId}"))
            preferences.remove(stringPreferencesKey("${ERROR_LOG_EPISODE_TITLE_PREFIX}${logId}"))
            preferences.remove(stringPreferencesKey("${ERROR_LOG_ERROR_TYPE_PREFIX}${logId}"))
            preferences.remove(stringPreferencesKey("${ERROR_LOG_ERROR_MESSAGE_PREFIX}${logId}"))
            preferences.remove(stringPreferencesKey("${ERROR_LOG_STACK_TRACE_PREFIX}${logId}"))
        }
    }

    suspend fun clearAllErrorLogs() {
        val logs = getAllErrorLogs()
        context.errorLogDataStore.edit { preferences ->
            preferences[stringSetPreferencesKey(ERROR_LOGS)] = emptySet()

            logs.forEach { log ->
                preferences.remove(stringPreferencesKey("${ERROR_LOG_ID_PREFIX}${log.id}"))
                preferences.remove(longPreferencesKey("${ERROR_LOG_TIMESTAMP_PREFIX}${log.id}"))
                preferences.remove(stringPreferencesKey("${ERROR_LOG_NCODE_PREFIX}${log.id}"))
                preferences.remove(stringPreferencesKey("${ERROR_LOG_NOVEL_TITLE_PREFIX}${log.id}"))
                preferences.remove(intPreferencesKey("${ERROR_LOG_EPISODE_NO_PREFIX}${log.id}"))
                preferences.remove(stringPreferencesKey("${ERROR_LOG_EPISODE_TITLE_PREFIX}${log.id}"))
                preferences.remove(stringPreferencesKey("${ERROR_LOG_ERROR_TYPE_PREFIX}${log.id}"))
                preferences.remove(stringPreferencesKey("${ERROR_LOG_ERROR_MESSAGE_PREFIX}${log.id}"))
                preferences.remove(stringPreferencesKey("${ERROR_LOG_STACK_TRACE_PREFIX}${log.id}"))
            }
        }
    }

    fun formatErrorLogsForEmail(logs: List<ErrorLog>): String {
        val sb = StringBuilder()
        sb.append("小説リーダーアプリ エラーログ\n")
        sb.append("========================\n\n")

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())

        logs.forEachIndexed { index, log ->
            sb.append("[エラー ${index + 1}]\n")
            sb.append("日時: ${sdf.format(java.util.Date(log.timestamp))}\n")
            sb.append("小説名: ${log.novelTitle}\n")
            sb.append("Nコード: ${log.ncode}\n")
            sb.append("エピソード: ${if (log.episodeTitle != null) "${log.episodeTitle} (第${log.episodeNo}話)" else "第${log.episodeNo}話"}\n")
            sb.append("エラー種類: ${log.errorType}\n")
            sb.append("エラーメッセージ: ${log.errorMessage}\n")

            if (log.stackTrace != null) {
                sb.append("スタックトレース:\n")
                sb.append(log.stackTrace ?: "")
                sb.append("\n")
            }

            sb.append("\n------------------------\n\n")
        }

        sb.append("========================\n")
        sb.append("合計: ${logs.size}件のエラー\n")

        return sb.toString()
    }
}
