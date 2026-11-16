package com.kyudo.aimhelper.testutil

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.kyudo.aimhelper.AppViewModel
import com.kyudo.aimhelper.screenshots.DemoData
import com.kyudo.aimhelper.ui.Ink
import com.kyudo.aimhelper.ui.KyudoTheme
import com.kyudo.aimhelper.ui.LocalShowKanji
import kotlinx.coroutines.Dispatchers

typealias ComposeRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

/** A ViewModel filled with the demo sessions; file work runs inline; voice off. */
fun demoViewModel(): AppViewModel =
    AppViewModel(ApplicationProvider.getApplicationContext<Application>()).also {
        it.io = Dispatchers.Unconfined
        it.updateSettings { s -> s.copy(voice = false) }
        it.loadPreviewData(DemoData.sessions, DemoData.CURRENT_ID, DemoData.liveAtKai())
    }

/** Shows one screen inside the app theme, with the kanji setting as given. */
fun ComposeRule.show(showKanji: Boolean = false, content: @Composable () -> Unit) {
    setContent {
        KyudoTheme {
            CompositionLocalProvider(LocalShowKanji provides showKanji) {
                Box(Modifier.fillMaxSize().background(Ink)) { content() }
            }
        }
    }
    waitForIdle()
}

/** Every text and content description on screen (unmerged, so nothing is hidden). */
fun ComposeRule.allText(): List<String> {
    val out = mutableListOf<String>()
    fun walk(n: SemanticsNode) {
        n.config.getOrNull(SemanticsProperties.Text)?.forEach { out += it.text }
        n.config.getOrNull(SemanticsProperties.ContentDescription)?.let { out += it }
        n.config.getOrNull(SemanticsProperties.EditableText)?.let { out += it.text }
        n.children.forEach { walk(it) }
    }
    walk(onRoot(useUnmergedTree = true).fetchSemanticsNode())
    return out
}

/** Clickable elements smaller than [min] dp in either direction, described for the failure message. */
fun ComposeRule.smallTouchTargets(min: Int = 44): List<String> {
    val minPx = with(density) { min.dp.toPx() } - 0.5f
    val out = mutableListOf<String>()
    fun walk(n: SemanticsNode) {
        if (n.config.contains(SemanticsActions.OnClick) &&
            (n.size.width < minPx || n.size.height < minPx)
        ) {
            val label = (n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
                ?: n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString())
            val w = with(density) { n.size.width.toDp() }
            val h = with(density) { n.size.height.toDp() }
            out += "'$label' is $w × $h"
        }
        n.children.forEach { walk(it) }
    }
    walk(onRoot().fetchSemanticsNode())
    return out
}

fun hasJapanese(s: String): Boolean = s.codePoints().anyMatch {
    Character.UnicodeScript.of(it) in setOf(
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA,
    )
}
