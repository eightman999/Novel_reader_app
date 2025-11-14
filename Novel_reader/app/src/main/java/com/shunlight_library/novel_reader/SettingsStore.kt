/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * DataStore wrapper managing persistent settings.
 */
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

// CustomFont用のデータクラス
data class CustomFontInfo(
    val id: String,
    val name: String,
    val path: String,
    val type: String
)

// NovelListFilter設定用のデータクラス
data class NovelListFilterSettings(
    val sortField: String = "LAST_UPDATE_DATE",
    val sortDirection: String = "DESCENDING",
    val minRating: Int = 0,
    val maxRating: Int = 5,
    val hideRating5WithNoEpisodes: Boolean = false,
    val showCompleted: Boolean = true,
    val showOngoing: Boolean = true,
    val showFavoritesOnly: Boolean = false,
    val showLongNovels: Boolean = true,
    val showShortNovels: Boolean = true,
    val siteFilter: String = "ALL"
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
        val IMAGE_SAVE_LOCATION = stringPreferencesKey("image_save_location")

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

        // 左右スワイプ機能のキー
        val SWIPE_ENABLED = booleanPreferencesKey("swipe_enabled")

        // タップでページ移動機能のキー
        val TAP_ENABLED = booleanPreferencesKey("tap_enabled")

        // 自動更新設定のキー
        val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
        val AUTO_UPDATE_TIME = stringPreferencesKey("auto_update_time")
        val CUSTOM_FONT_PATH = stringPreferencesKey("custom_font_path")

        // GitHubリリース通知用のキー
        val LAST_NOTIFIED_RELEASE = stringPreferencesKey("last_notified_release")

        // カスタムフォント管理のためのキー
        val CUSTOM_FONTS = stringSetPreferencesKey("custom_fonts")
        private const val CUSTOM_FONT_NAME_PREFIX = "custom_font_name_"
        private const val CUSTOM_FONT_PATH_PREFIX = "custom_font_path_"
        private const val CUSTOM_FONT_TYPE_PREFIX = "custom_font_type_"
        
        // 小説リストフィルター設定のキー
        val NOVEL_LIST_SORT_FIELD = stringPreferencesKey("novel_list_sort_field")
        val NOVEL_LIST_SORT_DIRECTION = stringPreferencesKey("novel_list_sort_direction")
        val NOVEL_LIST_MIN_RATING = intPreferencesKey("novel_list_min_rating")
        val NOVEL_LIST_MAX_RATING = intPreferencesKey("novel_list_max_rating")
        val NOVEL_LIST_HIDE_RATING5_NO_EPISODES = booleanPreferencesKey("novel_list_hide_rating5_no_episodes")
        val NOVEL_LIST_SHOW_COMPLETED = booleanPreferencesKey("novel_list_show_completed")
        val NOVEL_LIST_SHOW_ONGOING = booleanPreferencesKey("novel_list_show_ongoing")
        val NOVEL_LIST_SHOW_FAVORITES_ONLY = booleanPreferencesKey("novel_list_show_favorites_only")
        val NOVEL_LIST_SHOW_LONG = booleanPreferencesKey("novel_list_show_long")
        val NOVEL_LIST_SHOW_SHORT = booleanPreferencesKey("novel_list_show_short")
        val NOVEL_LIST_SITE_FILTER = stringPreferencesKey("novel_list_site_filter")
    }

    val defaultFontColor = "#000000" // 黒
    val defaultEpisodeBackgroundColor = "#F5F5DC" // クリーム色
    val defaultUseDefaultBackground = true
    val defaultSwipeEnabled = true
    val defaultTapEnabled = false

    val defaultThemeMode = "System"
    val defaultFontFamily = "Gothic"
    val defaultFontSize = 16
    val defaultBackgroundColor = "White"
    val defaultSelfServerAccess = false
    val defaultTextOrientation = "Horizontal"
    val defaultSelfServerPath = ""
    val defaultImageSaveLocation = ""

    // デフォルト値
    val defaultShowTitle = true
    val defaultShowAuthor = true
    val defaultShowSynopsis = false
    val defaultShowTags = true
    val defaultShowRating = false
    val defaultShowUpdateDate = true
    val defaultShowEpisodeCount = true
    val defaultAutoUpdateEnabled = false
    val defaultAutoUpdateTime = "03:00" // デフォルトは午前3時
    val defaultCustomFontPath = ""
    val defaultCustomFonts = emptySet<String>()
    val defaultLastNotifiedRelease = ""
    
    // 小説リストフィルター設定のデフォルト値
    val defaultNovelListSortField = "LAST_UPDATE_DATE"
    val defaultNovelListSortDirection = "DESCENDING"
    val defaultNovelListMinRating = 0
    val defaultNovelListMaxRating = 5
    val defaultNovelListHideRating5NoEpisodes = false
    val defaultNovelListShowCompleted = true
    val defaultNovelListShowOngoing = true
    val defaultNovelListShowFavoritesOnly = false
    val defaultNovelListShowLong = true
    val defaultNovelListShowShort = true
    val defaultNovelListSiteFilter = "ALL"

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

    val imageSaveLocation: Flow<String> = context.dataStore.data
        .catch { exception: Throwable ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences: Preferences ->
            preferences[IMAGE_SAVE_LOCATION] ?: defaultImageSaveLocation
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

    val swipeEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception: Throwable ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences: Preferences ->
            preferences[SWIPE_ENABLED] ?: defaultSwipeEnabled
        }

    val tapEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception: Throwable ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences: Preferences ->
            preferences[TAP_ENABLED] ?: defaultTapEnabled
        }

    // 自動更新有効/無効の設定値を取得
    val autoUpdateEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception: Throwable ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences: Preferences ->
            preferences[AUTO_UPDATE_ENABLED] ?: defaultAutoUpdateEnabled
        }

    // 自動更新時間の設定値を取得
    val autoUpdateTime: Flow<String> = context.dataStore.data
        .catch { exception: Throwable ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences: Preferences ->
            preferences[AUTO_UPDATE_TIME] ?: defaultAutoUpdateTime
        }

    // 最後に通知したGitHubリリースバージョンを取得
    val lastNotifiedRelease: Flow<String> = context.dataStore.data
        .catch { exception: Throwable ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences: Preferences ->
            preferences[LAST_NOTIFIED_RELEASE] ?: defaultLastNotifiedRelease
        }

    // カスタムフォントIDのリストを取得するFlow
    val customFontIds: Flow<Set<String>> = context.dataStore.data
        .catch { exception: Throwable ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences: Preferences ->
            preferences[CUSTOM_FONTS] ?: defaultCustomFonts
        }

    // カスタムフォント情報を取得するメソッド
    suspend fun getCustomFontInfo(fontId: String): CustomFontInfo? {
        val preferences = context.dataStore.data.first()
        val name = preferences[stringPreferencesKey("${CUSTOM_FONT_NAME_PREFIX}${fontId}")] ?: return null
        val path = preferences[stringPreferencesKey("${CUSTOM_FONT_PATH_PREFIX}${fontId}")] ?: return null
        val type = preferences[stringPreferencesKey("${CUSTOM_FONT_TYPE_PREFIX}${fontId}")] ?: return null

        return CustomFontInfo(
            id = fontId,
            name = name,
            path = path,
            type = type
        )
    }

    // すべてのカスタムフォント情報を取得するメソッド
    suspend fun getAllCustomFontInfo(): List<CustomFontInfo> {
        val preferences = context.dataStore.data.first()
        val fontIds = preferences[CUSTOM_FONTS] ?: emptySet()

        return fontIds.mapNotNull { fontId ->
            val name = preferences[stringPreferencesKey("${CUSTOM_FONT_NAME_PREFIX}${fontId}")] ?: return@mapNotNull null
            val path = preferences[stringPreferencesKey("${CUSTOM_FONT_PATH_PREFIX}${fontId}")] ?: return@mapNotNull null
            val type = preferences[stringPreferencesKey("${CUSTOM_FONT_TYPE_PREFIX}${fontId}")] ?: return@mapNotNull null

            CustomFontInfo(
                id = fontId,
                name = name,
                path = path,
                type = type
            )
        }
    }

    // カスタムフォントを保存するメソッド
    suspend fun saveCustomFont(fontId: String, fontName: String, fontPath: String, fontType: String) {
        context.dataStore.edit { preferences ->
            // 現在のカスタムフォントIDリストを取得
            val currentFonts = preferences[CUSTOM_FONTS] ?: emptySet()

            // IDリストを更新
            preferences[CUSTOM_FONTS] = currentFonts + fontId

            // フォント情報を保存
            preferences[stringPreferencesKey("${CUSTOM_FONT_NAME_PREFIX}${fontId}")] = fontName
            preferences[stringPreferencesKey("${CUSTOM_FONT_PATH_PREFIX}${fontId}")] = fontPath
            preferences[stringPreferencesKey("${CUSTOM_FONT_TYPE_PREFIX}${fontId}")] = fontType
        }
    }

    // カスタムフォントを削除するメソッド
    suspend fun deleteCustomFont(fontId: String) {
        context.dataStore.edit { preferences ->
            // 現在のカスタムフォントIDリストを取得
            val currentFonts = preferences[CUSTOM_FONTS] ?: emptySet()

            // IDリストから削除
            preferences[CUSTOM_FONTS] = currentFonts - fontId

            // フォント情報も削除
            preferences.remove(stringPreferencesKey("${CUSTOM_FONT_NAME_PREFIX}${fontId}"))
            preferences.remove(stringPreferencesKey("${CUSTOM_FONT_PATH_PREFIX}${fontId}"))
            preferences.remove(stringPreferencesKey("${CUSTOM_FONT_TYPE_PREFIX}${fontId}"))
        }
    }

    // すべての設定を保存するためのメソッド
    suspend fun saveAllSettings(
        themeMode: String,
        fontFamily: String,
        fontSize: Int,
        selfServerAccess: Boolean,
        textOrientation: String,
        selfServerPath: String,
        fontColor: String,
        episodeBackgroundColor: String,
        useDefaultBackground: Boolean
    ) {
        context.dataStore.edit { preferences ->
            // 既存の設定（BACKGROUND_COLOR は削除）
            preferences[THEME_MODE] = themeMode
            preferences[FONT_FAMILY] = fontFamily
            preferences[FONT_SIZE] = fontSize
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

    suspend fun saveImageSaveLocation(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[IMAGE_SAVE_LOCATION] = uri
        }
    }

    suspend fun clearImageSaveLocation() {
        context.dataStore.edit { preferences ->
            preferences.remove(IMAGE_SAVE_LOCATION)
        }
    }

    suspend fun getImageSaveLocation(): String {
        val preferences = context.dataStore.data.first()
        return preferences[IMAGE_SAVE_LOCATION] ?: defaultImageSaveLocation
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

    suspend fun saveSwipeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SWIPE_ENABLED] = enabled
        }
    }

    suspend fun saveTapEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TAP_ENABLED] = enabled
        }
    }
    suspend fun saveFontSize(size: Int) {
        context.dataStore.edit { preferences ->
            preferences[FONT_SIZE] = size
        }
    }

    // 自動更新設定を保存するメソッド
    suspend fun saveAutoUpdateSettings(enabled: Boolean, time: String) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_UPDATE_ENABLED] = enabled
            preferences[AUTO_UPDATE_TIME] = time
        }
    }

    // カスタムフォントパスを保存するメソッド
    suspend fun saveCustomFontPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_FONT_PATH] = path
        }
    }

    // 後方互換性のための非推奨メソッド
    @Deprecated(
        "Ambiguous method name. Use saveCustomFontPath instead",
        ReplaceWith("saveCustomFontPath(path)")
    )
    suspend fun saveCustomFont(path: String) {
        saveCustomFontPath(path)
    }

    // GitHubリリース通知バージョンを保存
    suspend fun saveLastNotifiedRelease(version: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_NOTIFIED_RELEASE] = version
        }
    }

    // 最後に通知したリリースバージョンを取得
    suspend fun getLastNotifiedRelease(): String {
        val preferences = context.dataStore.data.first()
        return preferences[LAST_NOTIFIED_RELEASE] ?: defaultLastNotifiedRelease
    }
    
    // 小説リストフィルター設定の取得
    suspend fun getNovelListFilterSettings(): NovelListFilterSettings {
        val preferences = context.dataStore.data.first()
        return NovelListFilterSettings(
            sortField = preferences[NOVEL_LIST_SORT_FIELD] ?: defaultNovelListSortField,
            sortDirection = preferences[NOVEL_LIST_SORT_DIRECTION] ?: defaultNovelListSortDirection,
            minRating = preferences[NOVEL_LIST_MIN_RATING] ?: defaultNovelListMinRating,
            maxRating = preferences[NOVEL_LIST_MAX_RATING] ?: defaultNovelListMaxRating,
            hideRating5WithNoEpisodes = preferences[NOVEL_LIST_HIDE_RATING5_NO_EPISODES] ?: defaultNovelListHideRating5NoEpisodes,
            showCompleted = preferences[NOVEL_LIST_SHOW_COMPLETED] ?: defaultNovelListShowCompleted,
            showOngoing = preferences[NOVEL_LIST_SHOW_ONGOING] ?: defaultNovelListShowOngoing,
            showFavoritesOnly = preferences[NOVEL_LIST_SHOW_FAVORITES_ONLY] ?: defaultNovelListShowFavoritesOnly,
            showLongNovels = preferences[NOVEL_LIST_SHOW_LONG] ?: defaultNovelListShowLong,
            showShortNovels = preferences[NOVEL_LIST_SHOW_SHORT] ?: defaultNovelListShowShort,
            siteFilter = preferences[NOVEL_LIST_SITE_FILTER] ?: defaultNovelListSiteFilter
        )
    }

    // 小説リストフィルター設定の保存
    suspend fun saveNovelListFilterSettings(settings: NovelListFilterSettings) {
        context.dataStore.edit { preferences ->
            preferences[NOVEL_LIST_SORT_FIELD] = settings.sortField
            preferences[NOVEL_LIST_SORT_DIRECTION] = settings.sortDirection
            preferences[NOVEL_LIST_MIN_RATING] = settings.minRating
            preferences[NOVEL_LIST_MAX_RATING] = settings.maxRating
            preferences[NOVEL_LIST_HIDE_RATING5_NO_EPISODES] = settings.hideRating5WithNoEpisodes
            preferences[NOVEL_LIST_SHOW_COMPLETED] = settings.showCompleted
            preferences[NOVEL_LIST_SHOW_ONGOING] = settings.showOngoing
            preferences[NOVEL_LIST_SHOW_FAVORITES_ONLY] = settings.showFavoritesOnly
            preferences[NOVEL_LIST_SHOW_LONG] = settings.showLongNovels
            preferences[NOVEL_LIST_SHOW_SHORT] = settings.showShortNovels
            preferences[NOVEL_LIST_SITE_FILTER] = settings.siteFilter
        }
    }
}