package com.kyudo.aimhelper.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyudo.aimhelper.AppViewModel
import com.kyudo.aimhelper.data.NeraiRef

@Composable
fun NeraiScreen(vm: AppViewModel, onBack: () -> Unit, onStart: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader("狙い Nerai & setup", "Your sight picture at kai, right eye", onBack)

        Panel(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                SightPicture(s.nerai)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.nerai.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Paper)
                    Text(s.nerai.desc, fontSize = 13.sp, color = Soft, lineHeight = 19.sp)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeraiRef.entries.forEach { ref ->
                val on = ref == s.nerai
                Button(
                    onClick = { vm.updateSettings { it.copy(nerai = ref) } },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    border = BorderStroke(1.dp, if (on) Vermilion else Line),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (on) Vermilion else Surface1,
                        contentColor = if (on) Ink else Paper,
                    ),
                ) { Text(ref.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
            }
        }
        Text(
            "Pick the reference your sensei teaches. The app never tells you to shift your nerai to make up for a form fault.",
            fontSize = 12.sp,
            color = Muted,
            lineHeight = 17.sp,
        )

        SectionLabel("Coaching")
        Panel(Modifier.fillMaxWidth()) {
            ToggleRow("Voice cues", "Speak the main form flag at kai, and kai length after each shot", s.voice) { v ->
                vm.updateSettings { it.copy(voice = v) }
            }
            HorizontalDivider(color = Line)
            ToggleRow("Front camera", "Off: rear camera on a tripod (better tracking)", s.frontCamera) { v ->
                vm.updateSettings { it.copy(frontCamera = v) }
            }
            HorizontalDivider(color = Line)
            SliderRow("Kai goal", "%.1f s".format(s.minKaiSec), s.minKaiSec, 1f..8f, 13) { v ->
                vm.updateSettings { it.copy(minKaiSec = v) }
            }
            SliderRow("Line tolerance (shoulders, hips)", "±%.0f°".format(s.lineTolDeg), s.lineTolDeg, 1f..10f, 8) { v ->
                vm.updateSettings { it.copy(lineTolDeg = v) }
            }
            SliderRow("Arrow level tolerance", "±%.0f°".format(s.arrowTolDeg), s.arrowTolDeg, 1f..10f, 8) { v ->
                vm.updateSettings { it.copy(arrowTolDeg = v) }
            }
        }

        SectionLabel("Camera setup")
        Column {
            Step(1, "Put the phone on a tripod 3–4 m in front of your shai, at chest height, square to the shooting line. Your whole body and the bow should be in frame.")
            HorizontalDivider(color = Line)
            Step(2, "Wear clothes that contrast with the wall behind you. Good light helps the tracking a lot.")
            HorizontalDivider(color = Line)
            Step(3, "Shoot normally. After zanshin the app saves the shot; tap the mato in Shot log to record where it landed.")
            HorizontalDivider(color = Line)
            Step(4, "Never place the phone or tripod on the yamichi (the arrow path) or near the azuchi. Follow your dojo's safety rules.")
        }

        PrimaryButton("Start form check", onStart, Modifier.fillMaxWidth())
    }
}

/** The sight picture: mato on the grass, bow placed per the chosen reference. */
@Composable
private fun SightPicture(ref: NeraiRef) {
    Canvas(
        Modifier
            .size(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = "Sight picture: ${ref.title}" },
    ) {
        val u = size.width / 120f
        drawRect(Grass)
        val c = Offset(60f * u, 60f * u)
        listOf(30f, 28.2f, 22.2f, 20f, 15f, 13.2f, 7.6f).forEachIndexed { i, r ->
            drawCircle(if (i % 2 == 0) MatoBlack else Paper, r * u, c)
        }
        // The bow looks wider than the mato at 28 m. Its right edge decides the nerai.
        val rightEdge = when (ref) {
            NeraiRef.HIDDEN -> 92f
            NeraiRef.HALF -> 60f
            NeraiRef.EDGE -> 30f
        }
        drawRoundRect(
            Bamboo,
            topLeft = Offset((rightEdge - 64f) * u, 0f),
            size = Size(64f * u, size.height),
            cornerRadius = CornerRadius(4f * u),
        )
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Paper)
            Text(sub, fontSize = 12.sp, color = Muted, lineHeight = 16.sp)
        }
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Vermilion, checkedThumbColor = Ink),
        )
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(top = 6.dp)) {
        Row {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Paper, modifier = Modifier.weight(1f))
            Text(valueText, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Paper)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(thumbColor = Vermilion, activeTrackColor = Vermilion, inactiveTrackColor = Line),
        )
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Vermilion),
            contentAlignment = Alignment.Center,
        ) { Text("$n", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        Text(text, fontSize = 14.sp, color = Paper, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}
