package com.kyudo.aimhelper.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.kyudo.aimhelper.AppViewModel
import com.kyudo.aimhelper.data.NeraiRef
import com.kyudo.aimhelper.data.Session
import com.kyudo.aimhelper.data.Shot
import com.kyudo.aimhelper.data.ShotResult
import com.kyudo.aimhelper.screenshots.DemoData
import com.kyudo.aimhelper.testutil.demoViewModel
import com.kyudo.aimhelper.testutil.show
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What happens when people tap things. Runs on the JVM with Robolectric. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreensBehaviourTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun waitingShot(vm: AppViewModel): Shot =
        vm.sessions.value.first { it.id == DemoData.CURRENT_ID }.shots.first { it.id == 306L }

    // Case-insensitive: section labels are drawn in capitals.
    private fun shown(text: String, substring: Boolean = false) =
        compose.onAllNodesWithText(text, substring = substring, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()

    // ---- Shot log ---------------------------------------------------------------

    @Test
    fun shotLog_tapOnTheTarget_logsAHitOnTheShotTheCameraSaw() {
        val vm = demoViewModel()
        compose.show { ShotLogScreen(vm, onBack = {}, onFinish = {}) }
        compose.onNodeWithContentDescription("Target face", substring = true).performTouchInput { click(center) }
        val r = waitingShot(vm).result!!
        assertTrue(r.hit)
        assertEquals(0f, r.x, 0.1f)
        assertEquals(0f, r.y, 0.1f)
    }

    @Test
    fun shotLog_tapOutsideTheTarget_logsNothing() {
        val vm = demoViewModel()
        compose.show { ShotLogScreen(vm, onBack = {}, onFinish = {}) }
        compose.onNodeWithContentDescription("Target face", substring = true)
            .performTouchInput { click(Offset(4f, 4f)) } // corner of the square, outside the circle
        assertNull(waitingShot(vm).result)
    }

    @Test
    fun shotLog_missButtonThenUndo() {
        val vm = demoViewModel()
        compose.show { ShotLogScreen(vm, onBack = {}, onFinish = {}) }
        compose.onNodeWithText("× Back").performClick()
        assertEquals("Back", waitingShot(vm).result?.miss)
        compose.onNodeWithText("Undo").performClick()
        assertNull(waitingShot(vm).result)
    }

    @Test
    fun shotLog_finishButtonEndsTheSession() {
        val vm = demoViewModel()
        var finished = false
        compose.show { ShotLogScreen(vm, onBack = {}, onFinish = { finished = true }) }
        compose.onNodeWithText("Finish session & review").performScrollTo().performClick()
        assertTrue(finished)
    }

    // ---- Guide ------------------------------------------------------------------

    @Test
    fun guide_nextThroughEveryPage_thenStart() {
        var done = false
        compose.show { GuideScreen(onDone = { done = true }) }
        assertFalse(shown("Back"))
        repeat(5) {
            compose.onNodeWithText("Next").performClick()
            compose.waitForIdle()
        }
        assertTrue("Back appears after the first page", shown("Back"))
        assertTrue(shown("5. Review your shots"))
        compose.onNodeWithText("Start").performClick()
        assertTrue(done)
    }

    @Test
    fun guide_skipEndsItRightAway() {
        var done = false
        compose.show { GuideScreen(onDone = { done = true }) }
        compose.onNodeWithText("Skip").performClick()
        assertTrue(done)
    }

    // ---- Home -------------------------------------------------------------------

    @Test
    fun home_buttonsGoToTheRightPlaces() {
        val vm = demoViewModel()
        var started = false
        val opened = mutableListOf<String>()
        compose.show { HomeScreen(vm, onStart = { started = true }, onOpen = { opened += it }, now = DemoData.NOW) }
        compose.onNodeWithText("Start practice session").performClick()
        compose.onNodeWithContentDescription("How to use the app").performClick()
        listOf("Form check", "Shot log", "Shot review", "Nerai & setup").forEach {
            compose.onNodeWithText(it).performScrollTo().performClick()
        }
        assertTrue(started)
        assertEquals(listOf("guide", "form", "log", "review", "nerai"), opened)
    }

    @Test
    fun home_showsTheWeekSummary() {
        val vm = demoViewModel()
        compose.show { HomeScreen(vm, onStart = {}, onOpen = {}, now = DemoData.NOW) }
        assertTrue(shown("9 / 13 hits"))
    }

    // ---- Nerai & setup ----------------------------------------------------------

    @Test
    fun nerai_rowsChangeTheSettings() {
        val vm = demoViewModel()
        var guide = false
        compose.show { NeraiScreen(vm, onBack = {}, onStart = {}, onGuide = { guide = true }) }
        compose.onNodeWithText("Edge").performScrollTo().performClick()
        assertEquals(NeraiRef.EDGE, vm.settings.value.nerai)
        compose.onNodeWithText("Show kanji").performScrollTo().performClick()
        assertTrue(vm.settings.value.showKanji)
        compose.onNodeWithText("Record shot videos").performScrollTo().performClick()
        assertFalse(vm.settings.value.recordVideo)
        compose.onNodeWithText("How to use the app").performScrollTo().performClick()
        assertTrue(guide)
    }

    // ---- Shot review --------------------------------------------------------------

    @Test
    fun review_tappingAShotOpensIt() {
        val vm = demoViewModel()
        var opened: Pair<Long, Long>? = null
        compose.show { ReviewScreen(vm, onBack = {}, onOpenShot = { s, shot -> opened = s to shot }) }
        compose.onNodeWithText("Miss · front").performScrollTo().performClick()
        assertEquals(DemoData.CURRENT_ID to DemoData.DETAIL_SHOT_ID, opened)
    }

    @Test
    fun shotDetail_explainsEveryStepAndThisShot() {
        val vm = demoViewModel()
        compose.show { ShotDetailScreen(vm, DemoData.CURRENT_ID, DemoData.DETAIL_SHOT_ID, onBack = {}) }
        listOf("Ashibumi · Dozukuri · Yugamae", "Uchiokoshi", "Hikiwake", "Kai", "Hanare · Zanshin").forEach {
            assertTrue("step '$it' shown", shown(it))
        }
        assertTrue(shown("Held kai for 2.7 s (your goal: 3 s)", substring = true))
        assertTrue(shown("No video for this shot"))
        assertTrue(shown("Miss · front"))
    }

    @Test
    fun shotDetail_handLoggedShot_explainsWhyThereAreNoSteps() {
        val vm = demoViewModel()
        vm.loadPreviewData(listOf(Session(1, 0, "P", listOf(Shot(5, 0, result = ShotResult(true))))), 1)
        compose.show { ShotDetailScreen(vm, 1, 5, onBack = {}) }
        assertTrue(shown("No step-by-step data"))
    }

    @Test
    fun shotDetail_missingShot_doesNotCrash() {
        val vm = demoViewModel()
        compose.show { ShotDetailScreen(vm, 999, 999, onBack = {}) }
        assertTrue(shown("This shot isn't saved anymore."))
    }

    // ---- Form check -------------------------------------------------------------

    @Test
    fun formCheck_showsTheStageAndTheMainCue() {
        val vm = demoViewModel()
        var logged = false
        compose.show { FormCheckScreen(vm, onBack = {}, onLog = { logged = true }, demoCamera = true) }
        assertTrue(shown("Kai"))
        assertTrue(shown("Shoulders off the arrow line", substring = true))
        compose.onNodeWithText("Log result (1)").performClick()
        assertTrue(logged)
    }

    @Test
    fun formCheck_withKanji_showsKanjiNextToRomaji() {
        val vm = demoViewModel()
        compose.show(showKanji = true) { FormCheckScreen(vm, onBack = {}, onLog = {}, demoCamera = true) }
        assertTrue(shown("会 Kai"))
    }

    @Test
    fun formCheck_voiceButtonTogglesTheSetting() {
        val vm = demoViewModel() // voice is off in demoViewModel()
        compose.show { FormCheckScreen(vm, onBack = {}, onLog = {}, demoCamera = true) }
        compose.onNodeWithText("Voice: off").performClick()
        assertTrue(vm.settings.value.voice)
    }
}
