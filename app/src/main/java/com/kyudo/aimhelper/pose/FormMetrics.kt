package com.kyudo.aimhelper.pose

import com.kyudo.aimhelper.data.Settings
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Form measurements for one frame. Built for a camera that faces the archer's chest
 * (the phone stands in front of the shai, square to the shooting line).
 *
 * Angles are in degrees. "Level" angles are measured from the draw side to the bow side:
 * positive means the bow-hand side is higher.
 */
data class FormMetrics(
    val shoulderWidth: Float,
    val arrowAngle: Float,
    val shoulderAngle: Float,
    val hipAngle: Float?,
    /** Shoulder line minus arrow line: 0 = parallel (one of the five crosses). */
    val shoulderDev: Float,
    /** Hip line minus shoulder line. */
    val hipDev: Float?,
    /** Angle between the legs, hip centre to each ankle (ashibumi). */
    val legSpread: Float?,
    /** Ankle distance in shoulder widths. */
    val stanceRatio: Float?,
    /** Bow-arm elbow angle: 180 = fully straight. */
    val bowElbow: Float,
    /** Draw wrist to nose, in shoulder widths. */
    val drawToFace: Float,
    /** 0..1 estimate of how far the head is turned towards the mato. */
    val headTurn: Float,
    /** Wrist-to-wrist distance in shoulder widths. */
    val wristSpan: Float,
    val handsHigh: Boolean,
    val handsLow: Boolean,
    val nose: Pt,
    val drawWrist: Pt,
    val bowWrist: Pt,
    val drawShoulder: Pt,
    val bowShoulder: Pt,
    val drawHip: Pt?,
    val bowHip: Pt?,
)

/** A form problem. [label] is stable (used for stats), [detail] carries the numbers. */
data class Flag(val label: String, val detail: String, val voice: String)

object MetricsCalc {
    private const val VIS = 0.5f

    fun compute(p: List<Pt>): FormMetrics? {
        if (p.size < 33) return null
        val ls = p[Lm.L_SHOULDER]
        val rs = p[Lm.R_SHOULDER]
        if (ls.v < VIS || rs.v < VIS) return null
        val sw = dist(ls, rs)
        if (sw < 4f) return null

        val nose = p[Lm.NOSE]
        val lw = p[Lm.L_WRIST]
        val rw = p[Lm.R_WRIST]
        // The bow hand is the wrist that is farther from the head. This keeps working for
        // left- or right-handed setups and for a mirrored (front) camera.
        val bowIsLeft = abs(lw.x - nose.x) >= abs(rw.x - nose.x)
        val bowW = if (bowIsLeft) lw else rw
        val drawW = if (bowIsLeft) rw else lw
        val bowS = if (bowIsLeft) ls else rs
        val drawS = if (bowIsLeft) rs else ls
        val bowE = if (bowIsLeft) p[Lm.L_ELBOW] else p[Lm.R_ELBOW]
        val lh = p[Lm.L_HIP]
        val rh = p[Lm.R_HIP]
        val hipsOk = lh.v >= VIS && rh.v >= VIS
        val bowH = if (hipsOk) (if (bowIsLeft) lh else rh) else null
        val drawH = if (hipsOk) (if (bowIsLeft) rh else lh) else null
        val la = p[Lm.L_ANKLE]
        val ra = p[Lm.R_ANKLE]
        val anklesOk = la.v >= VIS && ra.v >= VIS

        val arrow = levelAngle(drawW, bowW)
        val shoulder = levelAngle(drawS, bowS)
        val hip = if (bowH != null && drawH != null) levelAngle(drawH, bowH) else null
        val legSpread = if (hipsOk && anklesOk) angleAt(mid(lh, rh), la, ra) else null
        val stance = if (anklesOk) dist(la, ra) / sw else null

        val earMid = mid(p[Lm.LEFT_EAR], p[Lm.RIGHT_EAR])
        val dirToBow = sign(bowS.x - drawS.x)
        val headTurn = ((nose.x - earMid.x) * dirToBow / (sw * 0.3f)).coerceIn(0f, 1f)

        val shoulderY = (ls.y + rs.y) / 2f
        return FormMetrics(
            shoulderWidth = sw,
            arrowAngle = arrow,
            shoulderAngle = shoulder,
            hipAngle = hip,
            shoulderDev = shoulder - arrow,
            hipDev = hip?.let { it - shoulder },
            legSpread = legSpread,
            stanceRatio = stance,
            bowElbow = angleAt(bowE, bowS, bowW),
            drawToFace = dist(drawW, nose) / sw,
            headTurn = headTurn,
            wristSpan = dist(bowW, drawW) / sw,
            handsHigh = lw.y < nose.y && rw.y < nose.y,
            handsLow = lw.y > shoulderY + 0.8f * sw && rw.y > shoulderY + 0.8f * sw,
            nose = nose,
            drawWrist = drawW,
            bowWrist = bowW,
            drawShoulder = drawS,
            bowShoulder = bowS,
            drawHip = drawH,
            bowHip = bowH,
        )
    }

    /** Tilt of the line from [from] to [to]; positive when [to] is higher. */
    fun levelAngle(from: Pt, to: Pt): Float {
        val dx = abs(to.x - from.x).coerceAtLeast(1e-3f)
        val dy = from.y - to.y
        return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }

    /** Angle a–vertex–b in degrees. */
    fun angleAt(vertex: Pt, a: Pt, b: Pt): Float {
        val ax = a.x - vertex.x
        val ay = a.y - vertex.y
        val bx = b.x - vertex.x
        val by = b.y - vertex.y
        val n = sqrt(ax * ax + ay * ay) * sqrt(bx * bx + by * by)
        if (n < 1e-6f) return 0f
        val c = ((ax * bx + ay * by) / n).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(c).toDouble()).toFloat()
    }

    /** Form problems in this frame, most important first. Meant for hikiwake and kai. */
    fun flags(m: FormMetrics, s: Settings): List<Flag> {
        val out = mutableListOf<Flag>()
        if (abs(m.shoulderDev) > s.lineTolDeg) {
            out += Flag(
                "Shoulders off the arrow line",
                "%+.0f° against the arrow".format(m.shoulderDev),
                "Shoulders",
            )
        }
        if (abs(m.arrowAngle) > s.arrowTolDeg) {
            out += Flag(
                "Arrow not level",
                if (m.arrowAngle > 0) "bow side %.0f° high".format(m.arrowAngle)
                else "bow side %.0f° low".format(-m.arrowAngle),
                "Level the arrow",
            )
        }
        m.hipDev?.let {
            if (abs(it) > s.lineTolDeg * 1.5f) {
                out += Flag("Hips twisted against shoulders", "%+.0f°".format(it), "Hips")
            }
        }
        if (m.bowElbow < 160f) {
            out += Flag("Bow arm bent", "%.0f° at the elbow".format(m.bowElbow), "Extend the bow arm")
        }
        if (m.headTurn < 0.5f) {
            out += Flag("Head not turned to the mato", "about %.0f%% turned".format(m.headTurn * 100), "Look at the target")
        }
        return out
    }
}
