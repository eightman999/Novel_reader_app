/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * 画面遷移用のスタックを管理するクラス。
 */
package com.shunlight_library.novel_reader.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

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
     */
    fun navigateBackTo(screen: Screen): Boolean {
        // 目的の画面が見つかるまでスタックを巻き戻す
        while (screenStack.isNotEmpty() && screenStack.last() != screen) {
            screenStack.removeAt(screenStack.lastIndex)
        }

        return if (screenStack.isNotEmpty() && screenStack.last() == screen) {
            _currentScreen.value = screenStack.removeAt(screenStack.lastIndex)
            true
        } else {
            false
        }
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
}

/**
 * NavigationManager のインスタンスを Compose で保持するための関数
 */
@Composable
fun rememberNavigationManager(): NavigationManager {
    return remember { NavigationManager() }
}
