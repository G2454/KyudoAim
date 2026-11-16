package com.kyudo.aimhelper

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real app on a device or emulator: first-launch guide, every screen, and the camera
 * screen with the real camera, pose model and video recorder. Catches crashes the JVM tests
 * can't (manifest, native libraries, camera binding).
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    @get:Rule(order = 0)
    val camera: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun exists(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun skipGuideIfShown() {
        compose.waitForIdle()
        if (exists("Skip")) compose.onNodeWithText("Skip").performClick()
        compose.waitUntil(5_000) { exists("Start practice session") }
    }

    private fun back() = compose.onNodeWithContentDescription("Back").performClick()

    @Test
    fun everyScreenOpensAndCloses() {
        skipGuideIfShown()

        compose.onNodeWithText("Shot log").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("Finish session & review") }
        back()

        compose.onNodeWithText("Shot review").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("Tap a shot to watch it step by step") }
        back()

        compose.onNodeWithText("Nerai & setup").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("Show kanji") }
        compose.onNodeWithText("How to use the app").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("Skip") }
        compose.onNodeWithText("Skip").performClick()
        back()

        compose.waitUntil(5_000) { exists("Start practice session") }
    }

    @Test
    fun cameraScreen_runsForAFewSecondsWithoutCrashing() {
        skipGuideIfShown()
        compose.onNodeWithText("Start practice session").performClick()
        // The voice button only exists on Form check; the screen must stay up with the real
        // camera, MediaPipe and the recorder running (or reporting why they can't).
        compose.waitUntil(10_000) { exists("Voice: on") || exists("Voice: off") }
        Thread.sleep(4_000)
        compose.waitUntil(10_000) { exists("Voice: on") || exists("Voice: off") }
        back()
        compose.waitUntil(10_000) { exists("Start practice session") }
    }
}
