package com.shunlight_library.novel_reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecentlyReadMediaFilterTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun deselectFromAll_usingRowAndCheckbox_appliesAndRestoresSelection() {
        val store = SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val original = runBlocking { store.getRecentlyReadFilterSettings() }
        try {
            runBlocking { store.saveRecentlyReadFilterSettings(FilterSettings()) }
            compose.setContent {
                MaterialTheme { RecentlyReadNovelsScreen(onBack = {}, onNovelClick = { _, _ -> }) }
            }
            compose.waitUntil(10_000) {
                compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
                    .fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithContentDescription("フィルター").performClick()
            val narou = compose.onNode(isToggleable() and hasAnySibling(hasText("小説家になろう")), useUnmergedTree = true)
            val kakuyomu = compose.onNode(isToggleable() and hasAnySibling(hasText("カクヨム")), useUnmergedTree = true)
            narou.performScrollTo().assertIsOn().performClick().assertIsOff()
            // 行全体のタップもチェックボックスと同じ選択状態を使う。
            compose.onNodeWithText("カクヨム", useUnmergedTree = true)
                .performScrollTo().onParent().performClick()
            kakuyomu.assertIsOff()
            compose.onNodeWithText("適用").performClick()
            compose.waitUntil(5_000) {
                runBlocking { store.getRecentlyReadFilterSettings().selectedSubSites } ==
                    setOf(SubSiteConst.NOCTURNE, SubSiteConst.MOONLIGHT, SubSiteConst.MIDNIGHT)
            }
            compose.onNodeWithContentDescription("フィルター").performClick()
            narou.performScrollTo().assertIsOff()
            kakuyomu.performScrollTo().assertIsOff()
            compose.onNodeWithText("全選択").performScrollTo().performClick()
            narou.performScrollTo().assertIsOn()
            kakuyomu.performScrollTo().assertIsOn()
            compose.onNodeWithText("適用").performClick()
            compose.waitUntil(5_000) {
                runBlocking { store.getRecentlyReadFilterSettings().selectedSubSites.isEmpty() }
            }
            assertEquals(emptySet<Int>(), runBlocking { store.getRecentlyReadFilterSettings().selectedSubSites })
        } finally {
            runBlocking { store.saveRecentlyReadFilterSettings(original) }
        }
    }
}
