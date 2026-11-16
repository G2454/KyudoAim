package com.kyudo.aimhelper.ui

import android.media.MediaPlayer
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyudo.aimhelper.AppViewModel
import com.kyudo.aimhelper.data.Settings
import com.kyudo.aimhelper.data.Shot
import com.kyudo.aimhelper.data.Stats
import com.kyudo.aimhelper.data.Terms
import com.kyudo.aimhelper.video.VideoTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Short labels for the timeline bar (romaji mode / kanji mode). */
private val SHORT = listOf("Prepare", "Raise", "Draw", "Kai", "Release")
private val SHORT_KANJI = listOf("構", "打", "引", "会", "離")

/**
 * One shot, step by step: the video (when one was recorded) with a timeline of the five steps,
 * and for each step what should happen plus what the app measured in this shot.
 */
@Composable
fun ShotDetailScreen(vm: AppViewModel, sessionId: Long, shotId: Long, onBack: () -> Unit) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val showKanji = LocalShowKanji.current
    val session = sessions.find { it.id == sessionId }
    val index = session?.shots?.indexOfFirst { it.id == shotId } ?: -1
    val shot = if (session != null && index >= 0) session.shots[index] else null

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (shot == null) {
            ScreenHeader("Shot review", null, onBack)
            Text("This shot isn't saved anymore.", color = Soft, fontSize = 14.sp)
        } else {
            val fmt = SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault())
            ScreenHeader("Shot ${index + 1}", fmt.format(Date(shot.time)), onBack) {
                val r = shot.result
                StatusBadge(if (r == null) "Not logged" else if (r.hit) "○ Hit" else "× Miss", r?.hit)
            }
            shot.result?.let { Text(Stats.positionText(it), fontSize = 14.sp, color = Paper) }
            if (shot.marks.size < 6) {
                Panel(Modifier.fillMaxWidth()) {
                    SectionLabel("No step-by-step data")
                    Text(
                        "This shot was logged by hand in Shot log. Shots taken with Form check open are " +
                            "broken into steps here, with a video when \"Record shot videos\" is on.",
                        fontSize = 14.sp, color = Paper, lineHeight = 20.sp,
                    )
                }
            } else {
                ShotSteps(shot, settings, showKanji)
            }
        }
    }
}

@Composable
private fun ShotSteps(shot: Shot, settings: Settings, showKanji: Boolean) {
    run {
        val file = remember(shot.videoPath) { shot.videoPath?.let { File(it) }?.takeIf { it.exists() } }
        var positionMs by remember { mutableLongStateOf(shot.marks[0]) }
        var seekRequest by remember { mutableLongStateOf(-1L) }

        if (file != null) {
            ShotVideo(
                file = file,
                startMs = shot.marks[0],
                seekRequest = seekRequest,
                onPosition = { positionMs = it },
                shotId = shot.id,
            )
        } else {
            Panel(Modifier.fillMaxWidth()) {
                SectionLabel("No video for this shot")
                Text(
                    if (settings.recordVideo) {
                        "The video may still be saving, or this phone couldn't record while tracking."
                    } else {
                        "Turn on \"Record shot videos\" in Nerai & setup to film your next shots."
                    },
                    fontSize = 14.sp, color = Paper, lineHeight = 20.sp,
                )
            }
        }

        val current = currentStep(shot.marks, positionMs, file != null)
        fun seek(i: Int) {
            seekRequest = shot.marks[i] + (if (seekRequest == shot.marks[i]) 1 else 0)
            positionMs = shot.marks[i]
        }

        StepTimeline(shot.marks, current, showKanji, onTap = if (file != null) ({ i: Int -> seek(i) }) else null)
        if (file != null) {
            Text("Tap a step to jump to it in the video.", fontSize = 12.sp, color = Muted)
        }

        Terms.STEPS.forEachIndexed { i, step ->
            val dur = (shot.marks[i + 1] - shot.marks[i]).coerceAtLeast(0)
            StepCard(
                title = Terms.stepName(step, showKanji),
                subtitle = Terms.stepEnglish(step) + " · %.1f s".format(dur / 1000f),
                explanation = step.explanation,
                notes = notesFor(i, shot, settings),
                highlighted = i == current,
                onClick = if (file != null) ({ seek(i) }) else null,
            )
        }
    }
}

/** Which step the video is in (or -1 when there is no video to follow). */
private fun currentStep(marks: List<Long>, positionMs: Long, hasVideo: Boolean): Int {
    if (!hasVideo) return -1
    var idx = 0
    for (i in 0 until 5) if (positionMs >= marks[i]) idx = i
    return idx
}

@Composable
private fun ShotVideo(
    file: File,
    startMs: Long,
    seekRequest: Long,
    onPosition: (Long) -> Unit,
    shotId: Long,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf<VideoView?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(0.5f) }

    fun applySpeed(mp: MediaPlayer?) {
        try {
            mp?.let { it.playbackParams = it.playbackParams.setSpeed(speed) }
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(view) {
        while (true) {
            view?.let {
                onPosition(it.currentPosition.toLong())
                playing = it.isPlaying
            }
            delay(100)
        }
    }
    LaunchedEffect(seekRequest) {
        if (seekRequest >= 0) view?.seekTo(seekRequest.toInt())
    }
    DisposableEffect(Unit) {
        onDispose { view?.stopPlayback() }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(380.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(factory = { ctx ->
            VideoView(ctx).apply {
                setVideoPath(file.absolutePath)
                setOnPreparedListener { mp ->
                    player = mp
                    seekTo(startMs.toInt())
                }
                setOnCompletionListener { playing = false }
                view = this
            }
        })
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton(
            if (playing) "Pause" else "Play",
            {
                val v = view
                if (v != null) {
                    if (v.isPlaying) {
                        v.pause()
                    } else {
                        v.start()
                        applySpeed(player)
                    }
                }
            },
            Modifier.weight(1.2f),
        )
        listOf(0.25f, 0.5f, 1f).forEach { s ->
            val label = if (s == 1f) "1×" else "$s×"
            SpeedChip(label, on = speed == s, modifier = Modifier.weight(1f)) {
                speed = s
                if (view?.isPlaying == true) applySpeed(player)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton(
            "Save to gallery",
            {
                scope.launch {
                    val uri = withContext(Dispatchers.IO) {
                        VideoTools.saveToGallery(context, file, "kyudo_shot_$shotId.mp4")
                    }
                    if (uri != null) {
                        Toast.makeText(context, "Saved to Movies/KyudoAim", Toast.LENGTH_SHORT).show()
                    } else {
                        // Android 9 and older: let the person pick where to save it.
                        context.startActivity(VideoTools.shareIntent(context, file))
                    }
                }
            },
            Modifier.weight(1f),
        )
        SecondaryButton(
            "Share",
            { context.startActivity(VideoTools.shareIntent(context, file)) },
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun SpeedChip(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (on) Vermilion else Line),
        contentPadding = PaddingValues(horizontal = 4.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (on) Vermilion else Surface1,
            contentColor = if (on) Ink else Paper,
        ),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun StepTimeline(marks: List<Long>, current: Int, showKanji: Boolean, onTap: ((Int) -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (i in 0 until 5) {
            val dur = (marks[i + 1] - marks[i]).coerceAtLeast(400L)
            val on = i == current
            val base = Modifier
                .weight(dur.toFloat())
                .fillMaxHeight()
                .background(if (on) Vermilion else if (i == 3) WarnBg else Surface1)
            Box(
                if (onTap != null) base.clickable(role = Role.Button) { onTap(i) } else base,
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (showKanji) SHORT_KANJI[i] else SHORT[i],
                    fontFamily = if (showKanji) Mincho else null,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    color = if (on) Ink else Paper,
                )
            }
        }
    }
}

@Composable
private fun StepCard(
    title: String,
    subtitle: String,
    explanation: String,
    notes: List<Pair<String, Boolean?>>,
    highlighted: Boolean,
    onClick: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(16.dp)
    val base = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(Surface1)
        .border(if (highlighted) 2.dp else 1.dp, if (highlighted) Vermilion else Line, shape)
    Column(
        (if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Paper)
        Text(subtitle, fontSize = 12.sp, color = Muted)
        Text(explanation, fontSize = 14.sp, color = Soft, lineHeight = 20.sp)
        if (notes.isNotEmpty()) {
            SectionLabel("Your shot", Modifier.padding(top = 4.dp))
            notes.forEach { (text, ok) ->
                Text(
                    "• $text",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = when (ok) {
                        true -> OkText
                        false -> WarnText
                        null -> Paper
                    },
                )
            }
        }
    }
}

/** What the app measured for step [i] of this shot. true = good, false = check, null = info. */
private fun notesFor(i: Int, shot: Shot, s: Settings): List<Pair<String, Boolean?>> {
    val out = mutableListOf<Pair<String, Boolean?>>()
    when (i) {
        0 -> shot.legSpread?.let {
            out += "Legs %.0f° apart (about 55–65° is typical)".format(it) to (it in 50f..70f)
        }
        3 -> {
            shot.kaiMs?.let {
                val sec = it / 1000f
                out += "Held kai for %.1f s (your goal: %.0f s)".format(sec, s.minKaiSec) to (sec >= s.minKaiSec)
            }
            shot.shoulderDev?.let {
                out += "Shoulders %+.0f° against the arrow (aim for ±%.0f°)".format(it, s.lineTolDeg) to
                    (abs(it) <= s.lineTolDeg)
            }
            shot.arrowAngle?.let {
                out += "Arrow tilted %+.0f° (aim for ±%.0f°)".format(it, s.arrowTolDeg) to (abs(it) <= s.arrowTolDeg)
            }
            shot.bowElbow?.let { out += "Bow arm at %.0f° (straight is 160° or more)".format(it) to (it >= 160f) }
            shot.flags.filter {
                it == "Hips twisted against shoulders" || it == "Head not turned to the mato"
            }.forEach { out += it to false }
        }
        4 -> when {
            "Head moved at hanare" in shot.flags -> out += "Your head moved at the release" to false
            shot.headMove != null -> out += "Your head stayed still at the release" to true
        }
    }
    return out
}
