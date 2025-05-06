package com.shunlight_library.novel_reader

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

// DisplaySettings用のデータクラス
data class DisplaySettings(
    val showTitle: Boolean = true,
    val showAuthor: Boolean = true,
    val showSynopsis: Boolean = false,
    val showTags: Boolean = true,
    val showRating: Boolean = false,
    val showUpdateDate: Boolean = true,
    val showEpisodeCount: Boolean = true
)

// DataStoreのインスタンスをトップレベルで定義
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val FONT_SIZE = intPreferencesKey("font_size")
        val BACKGROUND_COLOR = stringPreferencesKey("background_color")
        val SELF_SERVER_ACCESS = booleanPreferencesKey("self_server_access")
        val TEXT_ORIENTATION = stringPreferencesKey("text_orientation")
        val SELF_SERVER_PATH_KEY = stringPreferencesKey("self_server_path")

        // 追加する表示設定のキー
        val SHOW_TITLE = booleanPreferencesKey("show_title")
        val SHOW_AUTHOR = booleanPreferencesKey("show_author")
        val SHOW_SYNOPSIS = booleanPreferencesKey("show_synopsis")
        val SHOW_TAGS = booleanPreferencesKey("show_tags")
        val SHOW_RATING = booleanPreferencesKey("show_rating")
        val SHOW_UPDATE_DATE = booleanPreferencesKey("show_update_date")
        val SHOW_EPISODE_COUNT = booleanPreferencesKey("show_episode_count")

        val FONT_COLOR = stringPreferencesKey("font_color")
        val EPISODE_BACKGROUND_COLOR = stringPreferencesKey("episode_background_color")
        val USE_DEFAULT_BACKGROUND = booleanPreferencesKey("use_default_background")
    }

    val defaultFontColor = "#FFFFFF" // W
    val defaultEpisodeBackgroundColor = "#000000" // B
    val defaultUseDefaultBackground = true

    val defaultThemeMode = "System"
    val defaultFontFamily = "Gothic"
    val defaultFontSize = 16
    val defaultBackgroundColor = "White"
    val defaultSelfServerAccess = false
    val defaultTextOrientation = "Horizontal"
    val defaultSelfServerPath = ""

    // デフォルト値
    val defaultShowTitle = true
    val defaultShowAuthor = true
    val defaultShowSynopsis = false
    val defaultShowTags = true
    val defaultShowRating = false
    val defaultShowUpdateDate = true
    val defaultShowEpisodeCount = true
    val themeMode: Flow<String> = context.dataStore.data
        .catch { exception: Throwable ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences: Preferences ->
            preferences[THEME_MODE] ?: defaultThemeMode
        }

    val fontFamily: Flow<String> = context.dataStore.data
        .catch { exception: Throwable ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences: Preferences ->
            preferences[FONT_FAMILY] ?: defaultFontFamily
        }

    val fontSize: Flow<Int> = context.dataStore.data
        .catch { exception: Throwable ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences: Preferences ->
            preferences[FONT_SIZE] ?: defaultFontSize
        }

    val backgroundColor: Flow<String> = context.dataStore.data
        .catch { exception: Throwable ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences: Preferences ->
            preferences[BACKGROUND_COLOR] ?: defaultBackgroundColor
        }

    val selfServerAccess: Flow<Boolean> = context.dataStore.data
        .catch { exception: Throwable ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences: Preferences ->
            preferences[SELF_SERVER_ACCESS] ?: defaultSelfServerAccess
        }

    val textOrientation: Flow<String> = context.dataStore.data
        .catch { exception: Throwable ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences: Preferences ->
            preferences[TEXT_ORIENTATION] ?: defaultTextOrientation
        }

    val selfServerPath = context.dataStore.data.map { preferences: Preferences ->
        preferences[SELF_SERVER_PATH_KEY] ?: ""
    }
    val fontColor: Flow<String> = context.dataStore.data
        .catch { exception: Throwable ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences: Preferences ->
            preferences[FONT_COLOR] ?: defaultFontColor
        }

    val episodeBackgroundColor: Flow<String> = context.dataStore.data
        .catch { exception: Throwable ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences: Preferences ->
            preferences[EPISODE_BACKGROUND_COLOR] ?: defaultEpisodeBackgroundColor
        }

    val useDefaultBackground: Flow<Boolean> = context.dataStore.data
        .catch { exception: Throwable ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences: Preferences ->
            preferences[USE_DEFAULT_BACKGROUND] ?: defaultUseDefaultBackground
        }
    // すべての設定を保存するためのメソッド
    suspend fun saveAllSettings(
        themeMode: String,
        fontFamily: String,
        fontSize: Int,
        backgroundColor: String,
        selfServerAccess: Boolean,
        textOrientation: String,
        selfServerPath: String,
        fontColor: String,
        episodeBackgroundColor: String,
        useDefaultBackground: Boolean
    ) {
        context.dataStore.edit { preferences ->
            // 既存の設定
            preferences[THEME_MODE] = themeMode
            preferences[FONT_FAMILY] = fontFamily
            preferences[FONT_SIZE] = fontSize
            preferences[BACKGROUND_COLOR] = backgroundColor
            preferences[SELF_SERVER_ACCESS] = selfServerAccess
            preferences[TEXT_ORIENTATION] = textOrientation
            preferences[SELF_SERVER_PATH_KEY] = selfServerPath

            // 新しい設定
            preferences[FONT_COLOR] = fontColor
            preferences[EPISODE_BACKGROUND_COLOR] = episodeBackgroundColor
            preferences[USE_DEFAULT_BACKGROUND] = useDefaultBackground
        }
    }

    // 表示設定の取得
    suspend fun getDisplaySettings(): DisplaySettings {
        val preferences = context.dataStore.data.first()
        return DisplaySettings(
            showTitle = preferences[SHOW_TITLE] ?: defaultShowTitle,
            showAuthor = preferences[SHOW_AUTHOR] ?: defaultShowAuthor,
            showSynopsis = preferences[SHOW_SYNOPSIS] ?: defaultShowSynopsis,
            showTags = preferences[SHOW_TAGS] ?: defaultShowTags,
            showRating = preferences[SHOW_RATING] ?: defaultShowRating,
            showUpdateDate = preferences[SHOW_UPDATE_DATE] ?: defaultShowUpdateDate,
            showEpisodeCount = preferences[SHOW_EPISODE_COUNT] ?: defaultShowEpisodeCount
        )
    }

    // 表示設定の保存
    suspend fun saveDisplaySettings(settings: DisplaySettings) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_TITLE] = settings.showTitle
            preferences[SHOW_AUTHOR] = settings.showAuthor
            preferences[SHOW_SYNOPSIS] = settings.showSynopsis
            preferences[SHOW_TAGS] = settings.showTags
            preferences[SHOW_RATING] = settings.showRating
            preferences[SHOW_UPDATE_DATE] = settings.showUpdateDate
            preferences[SHOW_EPISODE_COUNT] = settings.showEpisodeCount
        }
    }

    // 個別の設定を保存するメソッド（既存のメソッド）
    suspend fun saveSelfServerPath(path: String) {
        context.dataStore.edit { preferences: MutablePreferences ->
            preferences[SELF_SERVER_PATH_KEY] = path
        }
    }
    suspend fun saveFontColor(color: String) {
        context.dataStore.edit { preferences ->
            preferences[FONT_COLOR] = color
        }
    }

    suspend fun saveEpisodeBackgroundColor(color: String) {
        context.dataStore.edit { preferences ->
            preferences[EPISODE_BACKGROUND_COLOR] = color
        }
    }

    suspend fun saveUseDefaultBackground(useDefault: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_DEFAULT_BACKGROUND] = useDefault
        }
    }
    suspend fun saveFontSize(size: Int) {
        context.dataStore.edit { preferences ->
            preferences[FONT_SIZE] = size
        }
    }

}