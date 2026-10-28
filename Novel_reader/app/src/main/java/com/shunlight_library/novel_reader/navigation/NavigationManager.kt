/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * 画面遷移用のスタックを管理するクラス。
 */
package com.shunlight_library.novel_reader.navigation

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import org.json.JSONArray
import org.json.JSONObject

/**
 * シンプルなバックスタック方式で画面遷移を管理するナビゲーションコントローラ。
 */
class NavigationManager {
    /** これまでに訪れた画面のスタック */
    private val screenStack = mutableListOf<Screen>()

    /** 現在表示中の画面 */
    private var _currentScreen = mutableStateOf<Screen>(Screen.Main)
    val currentScreen: Screen
        get() = _currentScreen.value

    /** スクロール位置を保存するMap (画面のキー → (firstVisibleItemIndex, firstVisibleItemScrollOffset)) */
    private val scrollPositions = mutableMapOf<String, Pair<Int, Int>>()

    /** 新しい [screen] を表示し、現在の画面をスタックに積む */
    fun navigateTo(screen: Screen) {
        screenStack.add(_currentScreen.value)
        _currentScreen.value = screen
    }

    /**
     * 現在の画面をスタックに積まずに [screen] へ置き換える。
     * 前後話ナビのようにスタックを増やさず同種画面を差し替えたい場合に使用し、
     * バックスタックの無限蓄積を防ぐ。
     */
    fun replaceCurrent(screen: Screen) {
        _currentScreen.value = screen
    }

    /**
     * 1 つ前の画面に戻る。
     * @return 遷移できた場合は true
     */
    fun navigateBack(): Boolean {
        return if (screenStack.isNotEmpty()) {
            _currentScreen.value = screenStack.removeAt(screenStack.size - 1)
            true
        } else {
            false
        }
    }

    /**
     * 指定した [screen] が現れるまで戻る。
     * スタックに [screen] が無い場合はスタックを変更せず false を返す（中間画面を失わない）。
     */
    fun navigateBackTo(screen: Screen): Boolean {
        val index = screenStack.lastIndexOf(screen)
        if (index < 0) return false
        while (screenStack.size > index + 1) {
            screenStack.removeAt(screenStack.lastIndex)
        }
        _currentScreen.value = screenStack.removeAt(screenStack.lastIndex)
        return true
    }

    /**
     * バックスタックを全て破棄して [screen] へ遷移する。
     */
    fun navigateClearingBackStack(screen: Screen) {
        screenStack.clear()
        _currentScreen.value = screen
    }

    /**
     * 指定した画面のスクロール位置を保存する
     * @param screenKey 画面を識別するキー
     * @param firstVisibleItemIndex 最初に表示されているアイテムのインデックス
     * @param firstVisibleItemScrollOffset 最初に表示されているアイテムのスクロールオフセット
     */
    fun saveScrollPosition(screenKey: String, firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
        scrollPositions[screenKey] = Pair(firstVisibleItemIndex, firstVisibleItemScrollOffset)
    }

    /**
     * 指定した画面のスクロール位置を取得する
     * @param screenKey 画面を識別するキー
     * @return 保存されたスクロール位置（firstVisibleItemIndex, firstVisibleItemScrollOffset）、保存されていない場合はnull
     */
    fun getScrollPosition(screenKey: String): Pair<Int, Int>? {
        return scrollPositions[screenKey]
    }

    /**
     * 指定した画面のスクロール位置をクリアする
     * @param screenKey 画面を識別するキー
     */
    fun clearScrollPosition(screenKey: String) {
        scrollPositions.remove(screenKey)
    }

    /** プロセス再生成に備えて現在画面・バックスタック・スクロール位置を Bundle へ保存する */
    fun saveState(): Bundle = Bundle().apply {
        putString(KEY_CURRENT, ScreenCodec.encode(_currentScreen.value).toString())
        putString(KEY_STACK, JSONArray().apply {
            screenStack.forEach { put(ScreenCodec.encode(it)) }
        }.toString())
        putString(KEY_SCROLL, JSONObject().apply {
            scrollPositions.forEach { (key, pos) -> put(key, JSONArray().put(pos.first).put(pos.second)) }
        }.toString())
    }

    /** [saveState] で保存した状態を復元する。壊れたデータは無視して Main のままにする */
    fun restoreState(bundle: Bundle) {
        try {
            val current = bundle.getString(KEY_CURRENT)?.let { ScreenCodec.decode(JSONObject(it)) } ?: return
            val stack = bundle.getString(KEY_STACK)?.let { json ->
                val array = JSONArray(json)
                (0 until array.length()).mapNotNull { ScreenCodec.decode(array.getJSONObject(it)) }
            } ?: emptyList()
            val scroll = bundle.getString(KEY_SCROLL)?.let { json ->
                val obj = JSONObject(json)
                obj.keys().asSequence().associateWith { key ->
                    val pos = obj.getJSONArray(key)
                    Pair(pos.getInt(0), pos.getInt(1))
                }
            } ?: emptyMap()
            screenStack.clear()
            screenStack.addAll(stack)
            scrollPositions.clear()
            scrollPositions.putAll(scroll)
            _currentScreen.value = current
        } catch (e: Exception) {
            // 復元に失敗した場合は初期状態のまま起動する
        }
    }

    private companion object {
        const val KEY_CURRENT = "nav_current"
        const val KEY_STACK = "nav_stack"
        const val KEY_SCROLL = "nav_scroll"
    }
}

/**
 * 構成変更（回転・ダークモード切替など）をまたいで [NavigationManager] を保持する ViewModel。
 */
class NavigationViewModel : ViewModel() {
    val navigationManager = NavigationManager()

    /** savedInstanceState からの復元を一度だけ行うためのフラグ */
    var restored = false
}

/** [Screen] を JSON へ相互変換する（source の入れ子も再帰的に扱う） */
internal object ScreenCodec {
    private val objects: Map<String, Screen> by lazy {
        listOf(
            Screen.Main, Screen.Settings, Screen.RecentlyReadNovels, Screen.RecentlyUpdatedNovels,
            Screen.UpdateInfo, Screen.DatabaseSync, Screen.DownloadQueue, Screen.SettingsMenu,
            Screen.SettingsDisplay, Screen.SettingsReading, Screen.SettingsNetwork,
            Screen.SettingsAutoUpdate, Screen.SettingsStorage, Screen.SettingsDeveloper
        ).associateBy { it::class.java.simpleName }
    }

    fun encode(screen: Screen): JSONObject {
        val json = JSONObject().put("type", screen::class.java.simpleName)
        when (screen) {
            is Screen.NovelList -> screen.source?.let { json.put("source", encode(it)) }
            is Screen.EpisodeList -> {
                json.put("ncode", screen.ncode)
                screen.source?.let { json.put("source", encode(it)) }
            }
            is Screen.EpisodeView -> {
                json.put("ncode", screen.ncode).put("episodeNo", screen.episodeNo)
                screen.source?.let { json.put("source", encode(it)) }
            }
            is Screen.WebView -> {
                json.put("url", screen.url)
                screen.source?.let { json.put("source", encode(it)) }
            }
            else -> Unit
        }
        return json
    }

    fun decode(json: JSONObject): Screen? {
        val source = json.optJSONObject("source")?.let { decode(it) }
        return when (val type = json.optString("type")) {
            "NovelList" -> Screen.NovelList(source)
            "EpisodeList" -> Screen.EpisodeList(json.getString("ncode"), source)
            "EpisodeView" -> Screen.EpisodeView(json.getString("ncode"), json.getString("episodeNo"), source)
            "WebView" -> Screen.WebView(json.getString("url"), source)
            else -> objects[type]
        }
    }
}

/**
 * アプリ内の各画面を表現する sealed class
 */
// navigation/Screen.kt
sealed class Screen {
    object Main : Screen()
    object Settings : Screen()
    data class NovelList(val source: Screen? = null) : Screen()
    data class EpisodeList(val ncode: String, val source: Screen? = null) : Screen()
    // sourceパラメータを追加
    data class EpisodeView(val ncode: String, val episodeNo: String, val source: Screen? = null) : Screen()
    data class WebView(val url: String, val source: Screen? = null) : Screen()
    object RecentlyReadNovels : Screen()
    object RecentlyUpdatedNovels : Screen()
    object UpdateInfo : Screen()
    object DatabaseSync : Screen()
    object DownloadQueue : Screen()
    object SettingsMenu : Screen()
    object SettingsDisplay : Screen()
    object SettingsReading : Screen()
    object SettingsNetwork : Screen()
    object SettingsAutoUpdate : Screen()
    object SettingsStorage : Screen()
    object SettingsDeveloper : Screen()
}

/**
 * NavigationManager のインスタンスを Compose で保持するための関数
 */
@Composable
fun rememberNavigationManager(): NavigationManager {
    return remember { NavigationManager() }
}
