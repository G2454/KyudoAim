package com.kyudo.aimhelper.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyudo.aimhelper.AppViewModel
import com.kyudo.aimhelper.data.Session
import com.kyudo.aimhelper.data.Shot
import com.kyudo.aimhelper.data.Stats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ReviewScreen(vm: AppViewModel, onBack: () -> Unit) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(sessions.lastOrNull()?.id) }
    var toDelete by remember { mutableStateOf<Session?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader("Shot review", "${sessions.size} session(s) saved on this phone", onBack)
        }
        if (sessions.isEmpty()) {
            item {
                Text(
                    "No sessions yet. Start a practice session from the home screen.",
                    color = Soft,
                    fontSize = 14.sp,
                )
            }
        }
        items(sessions.reversed(), key = { it.id }) { s ->
            SessionCard(
                s = s,
                open = expanded == s.id,
                onToggle = { expanded = if (expanded == s.id) null else s.id },
                onDelete = { toDelete = s },
            )
        }
    }

    toDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete this session?") },
            text = { Text("Its ${s.shots.size} shots will be removed from this phone.") },
            confirmButton = {
                TextButton({
                    vm.deleteSession(s.id)
                    toDelete = null
                }) { Text("Delete", color = Vermilion) }
            },
            dismissButton = { TextButton({ toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SessionCard(s: Session, open: Boolean, onToggle: () -> Unit, onDelete: () -> Unit) {
    val fmt = SimpleDateFormat("EEE d MMM yyyy · HH:mm", Locale.getDefault())
    val rate = Stats.hitRate(s.shots)
    val kai = Stats.avgKaiSec(s.shots)

    Panel(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(fmt.format(Date(s.start)), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Paper)
                Text(
                    listOfNotNull(
                        "${s.shots.size} shots",
                        rate?.let { "${(it * 100).roundToInt()}% hits" },
                        kai?.let { "avg kai %.1f s".format(it) },
                    ).joinToString(" · "),
                    fontSize = 12.sp,
                    color = Muted,
                )
            }
            Text(if (open) "▲" else "▼", color = Muted, fontSize = 12.sp)
        }
        Text(
            Stats.tally(s.shots).ifEmpty { "No results logged" },
            fontFamily = FontFamily.Serif,
            fontSize = 17.sp,
            letterSpacing = 3.sp,
            color = Paper,
        )

        if (open) {
            if (Stats.results(s.shots).any { it.hit }) {
                Mato(
                    s.shots,
                    Modifier
                        .fillMaxWidth(0.6f)
                        .aspectRatio(1f)
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 6.dp),
                    onTap = null,
                )
                Text("Group: " + Stats.driftText(Stats.groupCenter(s.shots)), fontSize = 14.sp, color = Paper)
            }
            val top = Stats.topFlags(s.shots)
            if (top.isNotEmpty()) {
                SectionLabel("Most common flags", Modifier.padding(top = 6.dp))
                top.forEach { (label, n) -> Text("$label  ×$n", fontSize = 14.sp, color = Soft) }
            }
            SectionLabel("Shots", Modifier.padding(top = 6.dp))
            s.shots.forEachIndexed { i, shot ->
                if (i > 0) HorizontalDivider(color = Line)
                ShotRowView(i + 1, shot)
            }
            TextButton(onClick = onDelete) { Text("Delete session", color = WarnText) }
        }
    }
}

@Composable
private fun ShotRowView(n: Int, shot: Shot) {
    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("#$n", fontSize = 12.sp, color = Muted, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                shot.result?.let { Stats.positionText(it) } ?: "Result not logged",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Paper,
            )
            val details = listOfNotNull(
                shot.kaiMs?.let { "kai %.1f s".format(it / 1000f) },
                shot.shoulderDev?.let { "shoulders %+.0f°".format(it) },
                shot.arrowAngle?.let { "arrow %+.0f°".format(it) },
                shot.bowElbow?.let { "bow arm %.0f°".format(it) },
            )
            if (details.isNotEmpty()) Text(details.joinToString(" · "), fontSize = 12.sp, color = Muted)
            if (shot.flags.isNotEmpty()) {
                Text(shot.flags.joinToString(" · "), fontSize = 12.sp, color = WarnText, lineHeight = 17.sp)
            }
        }
    }
}
