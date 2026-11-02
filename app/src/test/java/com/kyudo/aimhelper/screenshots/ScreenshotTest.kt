package com.kyudo.aimhelper.screenshots

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.kyudo.aimhelper.AppViewModel
import com.kyudo.aimhelper.ui.FormCheckScreen
import com.kyudo.aimhelper.ui.HomeScreen
import com.kyudo.aimhelper.ui.Ink
import com.kyudo.aimhelper.ui.KyudoTheme
import com.kyudo.aimhelper.ui.NeraiScreen
import com.kyudo.aimhelper.ui.ReviewScreen
import com.kyudo.aimhelper.ui.ShotLogScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every screen with fixed demo data.
 *
 * - `./gradlew testDebugUnitTest` runs these as smoke tests (each screen must compose).
 * - `./gradlew recordRoborazziDebug` also writes the PNGs to docs/screenshots/ for the README.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun viewModel(): AppViewModel =
        AppViewModel(ApplicationProvider.getApplicationContext<Application>()).also {
            it.loadPreviewData(DemoData.sessions, DemoData.CURRENT_ID, DemoData.liveAtKai())
        }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            KyudoTheme {
                Box(Modifier.fillMaxSize().background(Ink)) { content() }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(
            filePath = "../docs/screenshots/$name.png",
            roborazziOptions = RoborazziOptions(
                recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
            ),
        )
    }

    @Test
    fun home() {
        val vm = viewModel()
        shoot("01-home") { HomeScreen(vm, onStart = {}, onOpen = {}, now = DemoData.NOW) }
    }

    @Test
    fun formCheck() {
        val vm = viewModel()
        shoot("02-form-check") { FormCheckScreen(vm, onBack = {}, onLog = {}, demoCamera = true) }
    }

    @Test
    fun shotLog() {
        val vm = viewModel()
        shoot("03-shot-log") { ShotLogScreen(vm, onBack = {}, onFinish = {}) }
    }

    @Test
    fun review() {
        val vm = viewModel()
        shoot("04-review") { ReviewScreen(vm, onBack = {}) }
    }

    @Test
    fun nerai() {
        val vm = viewModel()
        shoot("05-nerai") { NeraiScreen(vm, onBack = {}, onStart = {}) }
    }
}
