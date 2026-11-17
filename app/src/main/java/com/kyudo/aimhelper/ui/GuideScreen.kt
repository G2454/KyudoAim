package com.kyudo.aimhelper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyudo.aimhelper.data.Terms
import kotlinx.coroutines.launch

private data class GuidePage(val title: String, val body: String, val points: List<String>)

private fun pages(showKanji: Boolean): List<GuidePage> {
    fun t(i: Int) = Terms.name(Terms.HASSETSU[i], showKanji)
    return listOf(
        GuidePage(
            "Welcome",
            "Kyudo Aim Helper watches your form through the phone camera, records each shot, " +
                "and keeps a log of where your arrows land. Everything stays on your phone.",
            listOf(
                "Form check: live feedback while you shoot",
                "Shot log: where each arrow landed",
                "Shot review: each shot's video, step by step",
            ),
        ),
        GuidePage(
            "1. Set up the phone",
            "Put the phone on a tripod 3–4 m in front of your shooting position, at chest " +
                "height, facing you.",
            listOf(
                "Your whole body and the bow must be in the picture",
                "Good light and a plain wall behind you help",
                "Never put the phone on the arrow's path or near the target",
            ),
        ),
        GuidePage(
            "2. Start a session",
            "On the home screen tap Start practice session. Form check opens and the camera " +
                "starts. When a skeleton appears over your body, the app can see you.",
            listOf(
                "A red REC badge means the shot video is being recorded",
                "Voice cues can be turned on or off at the bottom",
            ),
        ),
        GuidePage(
            "3. Shoot normally",
            "The app follows the eight steps of the shot and lights them up as you go:",
            listOf(
                "Prepare: ${t(0)}, ${t(1)}, ${t(2)} (footing, posture, readying the bow)",
                "${t(3)}: raising the bow",
                "${t(4)}: drawing",
                "${t(5)}: full draw, timed on screen",
                "${t(6)} and ${t(7)}: release and follow-through",
            ),
        ),
        GuidePage(
            "4. Log where it landed",
            "After the shot, tap Log result, then tap the target where the arrow hit, or press " +
                "a miss button.",
            listOf(
                "Front (mae) is the right side of the target as you see it from the shooting line",
                "The app shows which way your group drifts and what usually causes it",
            ),
        ),
        GuidePage(
            "5. Review your shots",
            "Open Shot review and tap a shot. You can watch its video in slow motion, jump to " +
                "each step, and read what to look for and what the app measured.",
            listOf(
                "Save to gallery keeps the video in Movies/KyudoAim",
                "Share sends it to your teacher or another app",
                "Nerai & setup has the kanji option, tolerances and this guide",
            ),
        ),
    )
}

/**
 * A short swipeable guide. Shown on first launch and from the info buttons.
 * [onDone] runs on "Start" or "Skip".
 */
@Composable
fun GuideScreen(onDone: () -> Unit, startPage: Int = 0) {
    val list = pages(LocalShowKanji.current)
    val pager = rememberPagerState(initialPage = startPage, pageCount = { list.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == list.size - 1

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "How to use the app",
                fontFamily = Mincho,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                color = Paper,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) { Text("Skip", color = Muted) }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            val page = list[index]
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 24.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Vermilion),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (index == 0) "Hi" else "$index",
                        color = Ink,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(page.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Paper)
                Text(page.body, fontSize = 16.sp, color = Soft, lineHeight = 24.sp)
                Panel(Modifier.fillMaxWidth()) {
                    page.points.forEach { p ->
                        Text("• $p", fontSize = 15.sp, color = Paper, lineHeight = 22.sp)
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .semantics { contentDescription = "Page ${pager.currentPage + 1} of ${list.size}" },
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(list.size) { i ->
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (i == pager.currentPage) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (i == pager.currentPage) Vermilion else Dim),
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pager.currentPage > 0) {
                SecondaryButton(
                    "Back",
                    { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                    Modifier.weight(1f).padding(top = 2.dp),
                )
            }
            PrimaryButton(
                if (last) "Start" else "Next",
                {
                    if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
                Modifier.weight(1f),
            )
        }
    }
}
