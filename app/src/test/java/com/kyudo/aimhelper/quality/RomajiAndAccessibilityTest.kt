package com.kyudo.aimhelper.quality

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.kyudo.aimhelper.AppViewModel
import com.kyudo.aimhelper.screenshots.DemoData
import com.kyudo.aimhelper.testutil.allText
import com.kyudo.aimhelper.testutil.demoViewModel
import com.kyudo.aimhelper.testutil.hasJapanese
import com.kyudo.aimhelper.testutil.show
import com.kyudo.aimhelper.testutil.smallTouchTargets
import com.kyudo.aimhelper.ui.FormCheckScreen
import com.kyudo.aimhelper.ui.GuideScreen
import com.kyudo.aimhelper.ui.HomeScreen
import com.kyudo.aimhelper.ui.NeraiScreen
import com.kyudo.aimhelper.ui.ReviewScreen
import com.kyudo.aimhelper.ui.ShotDetailScreen
import com.kyudo.aimhelper.ui.ShotLogScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Two rules every screen must follow:
 * 1. With "Show kanji" off (the default) there is no Japanese script anywhere: romaji only.
 * 2. Everything you can tap is at least 44 dp in both directions.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class RomajiAndAccessibilityTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun checkScreen(name: String, content: @Composable (AppViewModel) -> Unit) {
        val vm = demoViewModel()
        compose.show(showKanji = false) { content(vm) }
        assertRomajiOnly(name)
        assertBigEnoughTargets(name)
    }

    private fun assertRomajiOnly(name: String) {
        val japanese = compose.allText().filter { hasJapanese(it) }
        assertTrue("$name shows Japanese script in romaji mode: $japanese", japanese.isEmpty())
    }

    private fun assertBigEnoughTargets(name: String) {
        val small = compose.smallTouchTargets()
        assertTrue("$name has touch targets under 44 dp: $small", small.isEmpty())
    }

    @Test
    fun home() = checkScreen("Home") { HomeScreen(it, onStart = {}, onOpen = {}, now = DemoData.NOW) }

    @Test
    fun formCheck() = checkScreen("Form check") { FormCheckScreen(it, onBack = {}, onLog = {}, demoCamera = true) }

    @Test
    fun shotLog() = checkScreen("Shot log") { ShotLogScreen(it, onBack = {}, onFinish = {}) }

    @Test
    fun review() = checkScreen("Shot review") { ReviewScreen(it, onBack = {}) }

    @Test
    fun shotDetail() = checkScreen("Shot detail") {
        ShotDetailScreen(it, DemoData.CURRENT_ID, DemoData.DETAIL_SHOT_ID, onBack = {})
    }

    @Test
    fun nerai() = checkScreen("Nerai & setup") { NeraiScreen(it, onBack = {}, onStart = {}) }

    @Test
    fun everyGuidePage() {
        compose.show(showKanji = false) { GuideScreen(onDone = {}) }
        repeat(6) { page ->
            assertRomajiOnly("Guide page ${page + 1}")
            assertBigEnoughTargets("Guide page ${page + 1}")
            if (page < 5) {
                compose.onNodeWithText("Next").performClick()
                compose.waitForIdle()
            }
        }
    }

    @Test
    fun kanjiMode_doesShowKanji() {
        val vm = demoViewModel()
        compose.show(showKanji = true) { HomeScreen(vm, onStart = {}, onOpen = {}, now = DemoData.NOW) }
        assertTrue(compose.onAllNodesWithText("弓道 · 狙い").fetchSemanticsNodes().isNotEmpty())
    }
}
