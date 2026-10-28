package com.shunlight_library.novel_reader.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * NavigationManager の画面スタック保存・復元と navigateBackTo の挙動を検証する。
 * Bundle と org.json を使うため instrumented テストで実行する。
 */
@RunWith(AndroidJUnit4::class)
class NavigationManagerTest {

    @Test
    fun saveAndRestore_keepsCurrentScreenStackAndScroll() {
        val original = NavigationManager()
        val list = Screen.NovelList(source = Screen.Main)
        val episodes = Screen.EpisodeList("n1234ab", source = list)
        original.navigateTo(list)
        original.navigateTo(episodes)
        original.navigateTo(Screen.EpisodeView("n1234ab", "12", source = episodes))
        original.saveScrollPosition("novel_list", 42, 7)

        val restored = NavigationManager()
        restored.restoreState(original.saveState())

        assertEquals(Screen.EpisodeView("n1234ab", "12", source = episodes), restored.currentScreen)
        assertEquals(Pair(42, 7), restored.getScrollPosition("novel_list"))
        assertTrue(restored.navigateBack())
        assertEquals(episodes, restored.currentScreen)
        assertTrue(restored.navigateBack())
        assertEquals(list, restored.currentScreen)
        assertTrue(restored.navigateBack())
        assertEquals(Screen.Main, restored.currentScreen)
        assertFalse(restored.navigateBack())
    }

    @Test
    fun restoreState_ignoresBrokenData() {
        val manager = NavigationManager()
        val broken = android.os.Bundle().apply { putString("nav_current", "{not json") }
        manager.restoreState(broken)
        assertEquals(Screen.Main, manager.currentScreen)
    }

    @Test
    fun navigateBackTo_missingTarget_keepsStack() {
        val manager = NavigationManager()
        manager.navigateTo(Screen.Settings)
        manager.navigateTo(Screen.UpdateInfo)

        assertFalse(manager.navigateBackTo(Screen.DownloadQueue))
        assertEquals(Screen.UpdateInfo, manager.currentScreen)
        assertTrue(manager.navigateBack())
        assertEquals(Screen.Settings, manager.currentScreen)
    }

    @Test
    fun navigateBackTo_existingTarget_popsToIt() {
        val manager = NavigationManager()
        manager.navigateTo(Screen.Settings)
        manager.navigateTo(Screen.UpdateInfo)
        manager.navigateTo(Screen.DownloadQueue)

        assertTrue(manager.navigateBackTo(Screen.Settings))
        assertEquals(Screen.Settings, manager.currentScreen)
        assertTrue(manager.navigateBack())
        assertEquals(Screen.Main, manager.currentScreen)
    }
}
