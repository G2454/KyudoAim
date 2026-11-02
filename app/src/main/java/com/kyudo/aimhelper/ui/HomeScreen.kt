package com.kyudo.aimhelper.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyudo.aimhelper.AppViewModel
import com.kyudo.aimhelper.data.Session
import com.kyudo.aimhelper.data.Stats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(vm: AppViewModel, onStart: () -> Unit, onOpen: (String) -> Unit) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
    val weekShots = sessions.filter { it.start >= weekAgo }.flatMap { it.shots }
    val logged = Stats.results(weekShots).size
    val hits = Stats.hits(weekShots)
    val rate = Stats.hitRate(weekShots)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("弓道 · 狙い", fontFamily = FontFamily.Serif, fontSize = 13.sp, letterSpacing = 4.sp, color = Muted)
            Text(
                "Kyudo Aim Helper",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                color = Paper,
            )
        }

        Panel(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                RateRing(rate)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("Last 7 days")
                    Text(
                        if (logged == 0) "No shots logged yet" else "$hits / $logged hits",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Paper,
                    )
                    Text(
                        if (logged == 0) "Start a session to track your form and hits."
                        else "Group: " + Stats.driftText(Stats.groupCenter(weekShots)),
                        fontSize = 13.sp,
                        color = Soft,
                        lineHeight = 18.sp,
                    )
                }
            }
        }

        PrimaryButton("Start practice session", onStart, Modifier.fillMaxWidth().height(60.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("形", "Form check", "Live pose · hassetsu", Modifier.weight(1f)) { onOpen("form") }
                Tile("的", "Shot log", "Hit map · drift", Modifier.weight(1f)) { onOpen("log") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("録", "Shot review", "Sessions · flags", Modifier.weight(1f)) { onOpen("review") }
                Tile("狙", "Nerai & setup", "Sight · camera · tolerances", Modifier.weight(1f)) { onOpen("nerai") }
            }
        }

        if (sessions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("Recent sessions")
                sessions.takeLast(3).reversed().forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(color = Line)
                    SessionRow(s) { onOpen("review") }
                }
            }
        }
    }
}

@Composable
private fun RateRing(rate: Float?) {
    val pct = rate?.let { (it * 100).roundToInt() }
    Box(
        Modifier
            .size(104.dp)
            .semantics { contentDescription = if (pct == null) "No hit rate yet" else "Hit rate $pct percent" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(104.dp)) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Line, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (rate != null && rate > 0f) {
                drawArc(
                    Vermilion, -90f, 360f * rate, false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (pct == null) "—" else "$pct%", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Paper)
            Text("tekichū", fontSize = 11.sp, color = Muted)
        }
    }
}

@Composable
private fun Tile(glyph: String, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(Surface1)
            .border(1.dp, Line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(glyph, fontFamily = FontFamily.Serif, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Vermilion)
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Paper)
        Text(subtitle, fontSize = 12.sp, color = Muted, lineHeight = 16.sp)
    }
}

@Composable
fun SessionRow(s: Session, onClick: () -> Unit) {
    val fmt = SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault())
    val logged = Stats.results(s.shots).size
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(fmt.format(Date(s.start)), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Paper)
            val kai = Stats.avgKaiSec(s.shots)
            Text(
                "${s.shots.size} shots" + (kai?.let { " · avg kai %.1f s".format(it) } ?: ""),
                fontSize = 12.sp,
                color = Muted,
            )
        }
        Text(
            if (logged == 0) "—" else Stats.tally(s.shots).take(12),
            fontFamily = FontFamily.Serif,
            fontSize = 16.sp,
            letterSpacing = 2.sp,
            color = Paper,
        )
    }
}
