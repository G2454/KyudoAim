package com.kyudo.aimhelper.pose

import com.kyudo.aimhelper.data.Settings
import kotlin.math.max

/**
 * The stages of hassetsu the camera can tell apart. [first]..[last] are the indexes of the
 * eight classical stages each one covers (0 = ashibumi ... 7 = zanshin).
 */
enum class Stage(val label: String, val jp: String, val first: Int, val last: Int) {
    PREPARE("Prepare", "構え", 0, 2),
    UCHIOKOSHI("Uchiokoshi", "打起し", 3, 3),
    HIKIWAKE("Hikiwake", "引分け", 4, 4),
    KAI("Kai", "会", 5, 5),
    ZANSHIN("Hanare · Zanshin", "残心", 6, 7),
}

val HASSETSU_JP = listOf("足踏み", "胴造り", "弓構え", "打起し", "引分け", "会", "離れ", "残心")

/** What the tracker measured for one completed shot (averaged over kai). */
data class ShotRecord(
    val kaiMs: Long,
    val arrowAngle: Float?,
    val shoulderDev: Float?,
    val hipDev: Float?,
    val legSpread: Float?,
    val bowElbow: Float?,
    val headMove: Float,
    val flags: List<String>,
)

data class TrackerUpdate(
    val stage: Stage,
    val kaiMs: Long,
    val enteredKai: Boolean,
    val shot: ShotRecord?,
)

/**
 * A small state machine that follows the archer through the shot from wrist and head
 * positions. Heuristic by design: tune the thresholds with real footage.
 * Call [onFrame] from one thread only.
 */
class ShotTracker {
    var stage = Stage.PREPARE
        private set

    private var stageSince = 0L
    private var kaiStart = 0L
    private var lastKaiMs = 0L
    private var lastSeen = 0L
    private var prevDraw: Pt? = null
    private var prevT = 0L
    /** Draw-hand speed in shoulder widths per second (smoothed). */
    private var drawSpeed = 0f
    private var highFrames = 0
    private var stillSince = -1L
    private val kaiSamples = ArrayList<FormMetrics>()
    private var noseAtRelease: Pt? = null
    private var releaseSw = 1f
    private var headMove = 0f

    fun reset() {
        stage = Stage.PREPARE
        stageSince = 0L
        kaiStart = 0L
        lastKaiMs = 0L
        lastSeen = 0L
        prevDraw = null
        prevT = 0L
        drawSpeed = 0f
        highFrames = 0
        stillSince = -1L
        kaiSamples.clear()
        noseAtRelease = null
        headMove = 0f
    }

    private fun go(s: Stage, t: Long) {
        stage = s
        stageSince = t
        stillSince = -1L
    }

    fun onFrame(m: FormMetrics?, t: Long, settings: Settings): TrackerUpdate {
        if (stageSince == 0L) stageSince = t

        // Zanshin ends on time even if the archer steps out of frame.
        if (stage == Stage.ZANSHIN && t - stageSince > 1500) {
            val shot = buildRecord(settings)
            go(Stage.PREPARE, t)
            highFrames = 0
            return TrackerUpdate(stage, 0L, false, shot)
        }

        if (m == null) {
            if (stage != Stage.PREPARE && lastSeen > 0 && t - lastSeen > 1500) {
                reset()
                stageSince = t
            }
            prevDraw = null
            return TrackerUpdate(stage, liveKai(t), false, null)
        }
        lastSeen = t

        val p = prevDraw
        drawSpeed = if (p != null && prevT > 0 && t > prevT) {
            val inst = dist(p, m.drawWrist) / m.shoulderWidth / ((t - prevT) / 1000f)
            drawSpeed * 0.5f + inst * 0.5f
        } else 0f
        prevDraw = m.drawWrist
        prevT = t

        var entered = false
        when (stage) {
            Stage.PREPARE -> {
                highFrames = if (m.handsHigh) highFrames + 1 else 0
                if (highFrames >= 3) go(Stage.UCHIOKOSHI, t)
            }

            Stage.UCHIOKOSHI -> when {
                !m.handsHigh && m.wristSpan > 1.3f -> go(Stage.HIKIWAKE, t)
                m.handsLow && t - stageSince > 800 -> go(Stage.PREPARE, t)
            }

            Stage.HIKIWAKE -> {
                if (isKaiPose(m) && drawSpeed < 0.6f) {
                    if (stillSince < 0) stillSince = t
                    if (t - stillSince >= 300) {
                        val start = stillSince
                        go(Stage.KAI, t)
                        kaiStart = start
                        kaiSamples.clear()
                        entered = true
                    }
                } else {
                    stillSince = -1L
                }
                if (stage == Stage.HIKIWAKE &&
                    ((m.handsLow && t - stageSince > 800) || t - stageSince > 20_000)
                ) {
                    go(Stage.PREPARE, t)
                }
            }

            Stage.KAI -> {
                val released = drawSpeed > 3f || m.drawToFace > 1.8f
                when {
                    released -> {
                        lastKaiMs = t - kaiStart
                        noseAtRelease = kaiSamples.lastOrNull()?.nose ?: m.nose
                        releaseSw = m.shoulderWidth
                        headMove = 0f
                        go(Stage.ZANSHIN, t)
                    }
                    m.handsLow -> go(Stage.PREPARE, t) // let down without shooting
                    else -> {
                        kaiSamples += m
                        if (kaiSamples.size > 900) kaiSamples.removeAt(0)
                    }
                }
            }

            Stage.ZANSHIN -> {
                val n0 = noseAtRelease
                if (n0 != null && t - stageSince < 500) {
                    headMove = max(headMove, dist(n0, m.nose) / releaseSw)
                }
            }
        }
        return TrackerUpdate(stage, liveKai(t), entered, null)
    }

    private fun isKaiPose(m: FormMetrics): Boolean =
        m.bowElbow > 150f &&
            m.drawToFace < 1.3f &&
            m.wristSpan > 1.6f &&
            kotlin.math.abs(m.arrowAngle) < 25f

    private fun liveKai(t: Long): Long = when (stage) {
        Stage.KAI -> t - kaiStart
        Stage.ZANSHIN -> lastKaiMs
        else -> 0L
    }

    private fun buildRecord(s: Settings): ShotRecord {
        fun avg(sel: (FormMetrics) -> Float?): Float? {
            val v = kaiSamples.mapNotNull(sel)
            return if (v.isEmpty()) null else v.average().toFloat()
        }

        val flags = mutableListOf<String>()
        if (lastKaiMs < s.minKaiSec * 1000) flags += "Short kai"
        if (headMove > 0.15f) flags += "Head moved at hanare"
        val n = kaiSamples.size
        if (n > 0) {
            // Keep a form flag only if it showed up in at least 40 % of the kai frames.
            val counts = HashMap<String, Int>()
            kaiSamples.forEach { m ->
                MetricsCalc.flags(m, s).forEach { f -> counts[f.label] = (counts[f.label] ?: 0) + 1 }
            }
            counts.filter { it.value >= n * 0.4 }.keys.forEach { flags += it }
        }

        val record = ShotRecord(
            kaiMs = lastKaiMs,
            arrowAngle = avg { it.arrowAngle },
            shoulderDev = avg { it.shoulderDev },
            hipDev = avg { it.hipDev },
            legSpread = avg { it.legSpread },
            bowElbow = avg { it.bowElbow },
            headMove = headMove,
            flags = flags,
        )
        kaiSamples.clear()
        return record
    }
}
