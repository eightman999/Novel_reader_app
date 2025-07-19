/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Manages screen navigation stack.
 */
// NavigationManager.kt
package com.shunlight_library.novel_reader.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * 画面遷移を管理するためのクラス
 */
class NavigationManager {
    // 画面のスタック
    private val screenStack = mutableListOf<Screen>()

    // 現在表示している画面
    private var _currentScreen = mutableStateOf<Screen>(Screen.Main)
    val currentScreen: Screen
        get() = _currentScreen.value

    // 画面を開く
    fun navigateTo(screen: Screen) {
        // 現在の画面をスタックに追加
        screenStack.add(_currentScreen.value)
        _currentScreen.value = screen
    }

    // 前の画面に戻る
    fun navigateBack(): Boolean {
        return if (screenStack.isNotEmpty()) {
            _currentScreen.value = screenStack.removeAt(screenStack.size - 1)
            true
        } else {
            false
        }
    }

    // 特定の画面まで戻る
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

    // スタックをクリアして特定の画面に移動
    fun navigateClearingBackStack(screen: Screen) {
        screenStack.clear()
        _currentScreen.value = screen
    }
}

/**
 * アプリの画面を表す sealed class
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
}

/**
 * NavigationManager のインスタンスを管理する Composable 関数
 */
@Composable
fun rememberNavigationManager(): NavigationManager {
    return remember { NavigationManager() }
}