package com.kyudo.aimhelper.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyudo.aimhelper.AppViewModel
import com.kyudo.aimhelper.data.Shot
import com.kyudo.aimhelper.data.ShotResult
import com.kyudo.aimhelper.data.Stats

/** Ring radii of a kasumi-mato as fractions of its radius, outside in (black, white, ...). */
private val MATO_RINGS = listOf(1f, 0.94f, 0.74f, 0.667f, 0.5f, 0.44f, 0.253f)

@Composable
fun ShotLogScreen(vm: AppViewModel, onBack: () -> Unit, onFinish: () -> Unit) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val currentId by vm.currentId.collectAsStateWithLifecycle()
    val shots = sessions.find { it.id == currentId }?.shots.orEmpty()
    val pending = shots.count { it.result == null }
    val logged = Stats.results(shots).size

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(
            title = "Shot log",
            subtitle = if (pending > 0) "$pending detected shot(s) waiting · tap where it landed"
            else "Kinteki 28 m · tap where the arrow landed",
            onBack = onBack,
        ) {
            SecondaryButton("Undo", vm::undoLast)
        }

        Mato(
            shots = shots,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(1f)
                .align(Alignment.CenterHorizontally),
            onTap = { x, y -> vm.recordResult(ShotResult(hit = true, x = x, y = y)) },
        )

        Row(Modifier.fillMaxWidth()) {
            Text("◀ Back (ushiro)", fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1f))
            Text("Front (mae) ▶", fontSize = 12.sp, color = Muted)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Front", "Back", "High", "Low").forEach { side ->
                SecondaryButton(
                    "× $side",
                    { vm.recordResult(ShotResult(hit = false, miss = side)) },
                    Modifier.weight(1f),
                )
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (logged == 0) "—" else Stats.tally(shots),
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    letterSpacing = 3.sp,
                    color = Paper,
                    modifier = Modifier.weight(1f),
                )
                Text("${Stats.hits(shots)} / $logged hits", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Paper)
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            SectionLabel("Group drift")
            Text(
                Stats.driftText(Stats.groupCenter(shots)),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Paper,
            )
            Text(Stats.advice(shots), fontSize = 13.sp, color = Soft, lineHeight = 19.sp)
        }

        PrimaryButton("Finish session & review", onFinish, Modifier.fillMaxWidth())
    }
}

@Composable
fun Mato(shots: List<Shot>, modifier: Modifier, onTap: ((Float, Float) -> Unit)?) {
    val hits = shots.mapNotNull { it.result }.filter { it.hit }
    val group = Stats.groupCenter(shots)
    val tapModifier = if (onTap != null) {
        Modifier.pointerInput(Unit) {
            detectTapGestures { o ->
                val r = size.width / 2f
                val x = (o.x - r) / r
                val y = (o.y - size.height / 2f) / r
                if (x * x + y * y <= 1f) onTap(x, y)
            }
        }
    } else Modifier

    Canvas(
        modifier
            .then(tapModifier)
            .semantics {
                contentDescription = "Target face with ${hits.size} hits. Tap where the arrow landed."
            },
    ) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        MATO_RINGS.forEachIndexed { i, f ->
            drawCircle(if (i % 2 == 0) MatoBlack else Paper, radius = r * f, center = c)
        }
        hits.forEach { h ->
            val p = Offset(c.x + h.x * r, c.y + h.y * r)
            drawCircle(Paper, 8.dp.toPx(), p)
            drawCircle(Vermilion, 6.dp.toPx(), p)
        }
        if (hits.size > 1 && group != null) {
            drawCircle(
                Teal,
                radius = 13.dp.toPx(),
                center = Offset(c.x + group.first * r, c.y + group.second * r),
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
            )
        }
    }
}
