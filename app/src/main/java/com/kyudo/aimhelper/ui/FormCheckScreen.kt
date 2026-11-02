package com.kyudo.aimhelper.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyudo.aimhelper.AppViewModel
import com.kyudo.aimhelper.data.Settings
import com.kyudo.aimhelper.data.Shot
import com.kyudo.aimhelper.pose.FormMetrics
import com.kyudo.aimhelper.pose.HASSETSU_JP
import com.kyudo.aimhelper.pose.Lm
import com.kyudo.aimhelper.pose.PoseEngine
import com.kyudo.aimhelper.pose.PoseFrame
import com.kyudo.aimhelper.pose.Pt
import com.kyudo.aimhelper.pose.Stage
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun FormCheckScreen(vm: AppViewModel, onBack: () -> Unit, onLog: () -> Unit) {
    val context = LocalContext.current
    val live by vm.live.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val currentId by vm.currentId.collectAsStateWithLifecycle()
    val pending = sessions.find { it.id == currentId }?.shots?.count { it.result == null } ?: 0

    var hasPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPerm = it }
    LaunchedEffect(Unit) { if (!hasPerm) launcher.launch(Manifest.permission.CAMERA) }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        vm.resetTracker()
        onDispose { view.keepScreenOn = false }
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
        ) {
            if (hasPerm) {
                CameraWithPose(vm, settings.frontCamera, Modifier.fillMaxSize())
                PoseOverlay(live.frame, live.metrics, settings, Modifier.fillMaxSize())
            } else {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("The camera is needed to track your form.", color = Paper, fontSize = 15.sp)
                    PrimaryButton("Allow camera", { launcher.launch(Manifest.permission.CAMERA) })
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onBack)
                Box(Modifier.weight(1f))
                val status = when {
                    live.error != null -> "Error"
                    live.metrics == null -> "Step into frame"
                    else -> "Tracking · ${live.fps.roundToInt()} fps"
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Ink.copy(alpha = 0.85f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (live.metrics != null) Vermilion else Muted),
                    )
                    Text(status, color = Paper, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Ink.copy(alpha = 0.88f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    "${live.stage.jp} ${live.stage.label}",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = Paper,
                )
                if (live.stage == Stage.KAI || live.stage == Stage.ZANSHIN) {
                    Text(
                        "kai %.1f s · goal ≥ %.0f s".format(live.kaiMs / 1000f, settings.minKaiSec),
                        fontSize = 13.sp,
                        color = Soft,
                    )
                }
            }

            live.error?.let {
                Text(
                    it,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(WarnBg)
                        .padding(12.dp),
                    color = WarnText,
                    fontSize = 13.sp,
                )
            }
        }

        HassetsuBar(live.stage)

        Column(
            Modifier
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val cue = live.flags.firstOrNull()
            if (cue != null) {
                CueCard("${cue.label} — ${cue.detail}")
            } else {
                live.lastShot?.let { LastShotCard(it) }
            }
            MetricRows(live.metrics, settings)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SecondaryButton(
                if (settings.voice) "Voice: on" else "Voice: off",
                { vm.updateSettings { it.copy(voice = !it.voice) } },
                Modifier.weight(1f),
            )
            SecondaryButton(
                "Flip camera",
                { vm.updateSettings { it.copy(frontCamera = !it.frontCamera) } },
                Modifier.weight(1f),
            )
            PrimaryButton(
                if (pending > 0) "Log result ($pending)" else "Shot log",
                onLog,
                Modifier.weight(1.2f),
            )
        }
    }
}

/** Binds CameraX preview + analysis and feeds frames to MediaPipe. Rebinds when the camera flips. */
@Composable
private fun CameraWithPose(vm: AppViewModel, front: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            // FIT_CENTER so the overlay can use the same letterbox maths as the preview.
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(front, lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val engine = PoseEngine(context.applicationContext, vm::onPoseFrame, vm::onPoseError)
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false

        future.addListener({
            if (disposed) return@addListener
            val p = future.get()
            provider = p
            val ratio = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            val preview = Preview.Builder()
                .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build())
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(ratio)
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            ),
                        )
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(executor, engine) }
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            try {
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } catch (e: Exception) {
                vm.onPoseError("Camera error: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            provider?.unbindAll()
            executor.execute { engine.close() }
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/** Draws the skeleton plus the arrow, shoulder and hip lines over the preview. */
@Composable
private fun PoseOverlay(frame: PoseFrame?, m: FormMetrics?, settings: Settings, modifier: Modifier) {
    Canvas(modifier) {
        val f = frame ?: return@Canvas
        val s = min(size.width / f.imgW, size.height / f.imgH)
        val ox = (size.width - f.imgW * s) / 2f
        val oy = (size.height - f.imgH * s) / 2f
        val mirror = settings.frontCamera
        fun map(p: Pt) = Offset(ox + (if (mirror) f.imgW - p.x else p.x) * s, oy + p.y * s)

        val bone = 4.dp.toPx()
        Lm.SKELETON.forEach { (a, b) ->
            val pa = f.pts[a]
            val pb = f.pts[b]
            if (pa.v > 0.5f && pb.v > 0.5f) {
                drawLine(Paper.copy(alpha = 0.9f), map(pa), map(pb), strokeWidth = bone, cap = StrokeCap.Round)
            }
        }
        Lm.JOINTS.forEach { i ->
            val p = f.pts[i]
            if (p.v > 0.5f) drawCircle(Vermilion, 5.dp.toPx(), map(p))
        }

        if (m != null) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
            val arrowOk = abs(m.arrowAngle) <= settings.arrowTolDeg
            drawLine(
                if (arrowOk) Teal else Vermilion, map(m.drawWrist), map(m.bowWrist),
                strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
            )
            val shoulderOk = abs(m.shoulderDev) <= settings.lineTolDeg
            drawLine(
                if (shoulderOk) Teal else Vermilion, map(m.drawShoulder), map(m.bowShoulder),
                strokeWidth = 2.dp.toPx(), pathEffect = dash,
            )
            val dh = m.drawHip
            val bh = m.bowHip
            if (dh != null && bh != null) {
                val hipOk = abs(m.hipDev ?: 0f) <= settings.lineTolDeg * 1.5f
                drawLine(
                    if (hipOk) Teal else Vermilion, map(dh), map(bh),
                    strokeWidth = 2.dp.toPx(), pathEffect = dash,
                )
            }
        }
    }
}

@Composable
private fun HassetsuBar(stage: Stage) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        HASSETSU_JP.forEachIndexed { i, jp ->
            val now = i in stage.first..stage.last
            val done = i < stage.first
            Column(
                Modifier.width(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .size(if (now) 12.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (now) Vermilion else if (done) Soft else Dim),
                )
                Text(
                    jp,
                    fontSize = 10.sp,
                    maxLines = 1,
                    color = if (now) Paper else Muted,
                    fontWeight = if (now) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun CueCard(text: String) {
    val shape = RoundedCornerShape(14.dp)
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(shape)
            .background(WarnBg)
            .border(1.dp, WarnLine, shape)
            .padding(12.dp),
        color = Paper,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
}

@Composable
private fun LastShotCard(shot: Shot) {
    Panel(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        SectionLabel("Last shot")
        Text(
            "Kai %.1f s".format((shot.kaiMs ?: 0L) / 1000f) +
                if (shot.flags.isEmpty()) " · clean" else " · ${shot.flags.size} flag(s)",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Paper,
        )
        if (shot.flags.isNotEmpty()) {
            Text(shot.flags.joinToString(" · "), fontSize = 13.sp, color = Soft, lineHeight = 18.sp)
        }
    }
}

private data class Row4(val name: String, val target: String, val value: String, val ok: Boolean?)

@Composable
private fun MetricRows(m: FormMetrics?, s: Settings) {
    val rows = listOf(
        Row4(
            "Ashibumi (leg spread)", "about 55–65° between the legs",
            m?.legSpread?.let { "%.0f°".format(it) } ?: "—",
            m?.legSpread?.let { it in 50f..70f },
        ),
        Row4(
            "Stance width", "info · ankles apart in shoulder widths",
            m?.stanceRatio?.let { "%.1f×".format(it) } ?: "—",
            null,
        ),
        Row4(
            "Arrow level", "within ±%.0f° (hikiwake / kai)".format(s.arrowTolDeg),
            m?.let { "%+.1f°".format(it.arrowAngle) } ?: "—",
            m?.let { abs(it.arrowAngle) <= s.arrowTolDeg },
        ),
        Row4(
            "Shoulders ∥ arrow", "within ±%.0f°".format(s.lineTolDeg),
            m?.let { "%+.1f°".format(it.shoulderDev) } ?: "—",
            m?.let { abs(it.shoulderDev) <= s.lineTolDeg },
        ),
        Row4(
            "Hips ∥ shoulders", "within ±%.0f°".format(s.lineTolDeg * 1.5f),
            m?.hipDev?.let { "%+.1f°".format(it) } ?: "—",
            m?.hipDev?.let { abs(it) <= s.lineTolDeg * 1.5f },
        ),
        Row4(
            "Bow arm", "straight, 160° or more",
            m?.let { "%.0f°".format(it.bowElbow) } ?: "—",
            m?.let { it.bowElbow >= 160f },
        ),
        Row4(
            "Head turned to mato", "estimate · chin over bow shoulder",
            m?.let { "${(it.headTurn * 100).roundToInt()}%" } ?: "—",
            m?.let { it.headTurn >= 0.5f },
        ),
    )

    rows.forEachIndexed { i, r ->
        if (i > 0) HorizontalDivider(color = Line)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(r.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Paper)
                Text(r.target, fontSize = 12.sp, color = Muted)
            }
            val label = when (r.ok) {
                true -> "${r.value} · OK"
                false -> "${r.value} · Check"
                null -> r.value
            }
            StatusBadge(label, r.ok)
        }
    }
}
