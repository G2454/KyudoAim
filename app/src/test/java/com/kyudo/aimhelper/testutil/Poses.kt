package com.kyudo.aimhelper.testutil

import com.kyudo.aimhelper.pose.FormMetrics
import com.kyudo.aimhelper.pose.Lm
import com.kyudo.aimhelper.pose.PoseFrame
import com.kyudo.aimhelper.pose.Pt
import kotlin.random.Random

/**
 * Synthetic archers seen from the front (the camera faces the chest), in a 400×600 frame.
 * The bow hand is on the image's right. Shoulder width is 60 px.
 */
object Poses {

    fun kai(
        shoulderTilt: Float = 0f,
        drawX: Float = 185f,
        drawY: Float = 120f,
        bowWristY: Float = 122f,
        bowElbow: Pt = Pt(290f, 136f),
        noseX: Float = 200f,
    ): MutableList<Pt> {
        val p = MutableList(33) { Pt(200f, 300f, 1f) }
        p[Lm.NOSE] = Pt(noseX, 100f)
        p[Lm.LEFT_EAR] = Pt(180f, 100f)
        p[Lm.RIGHT_EAR] = Pt(194f, 100f)
        p[Lm.L_SHOULDER] = Pt(230f, 150f - shoulderTilt)
        p[Lm.R_SHOULDER] = Pt(170f, 150f)
        p[Lm.L_ELBOW] = bowElbow
        p[Lm.L_WRIST] = Pt(350f, bowWristY)
        p[Lm.R_ELBOW] = Pt(120f, 140f)
        p[Lm.R_WRIST] = Pt(drawX, drawY)
        p[Lm.L_HIP] = Pt(215f, 270f)
        p[Lm.R_HIP] = Pt(185f, 270f)
        p[Lm.L_ANKLE] = Pt(267f, 380f)
        p[Lm.R_ANKLE] = Pt(133f, 380f)
        return p
    }

    fun handsUp(): MutableList<Pt> = kai().also { p ->
        p[Lm.L_ELBOW] = Pt(250f, 90f)
        p[Lm.L_WRIST] = Pt(240f, 40f)
        p[Lm.R_WRIST] = Pt(160f, 45f)
    }

    fun handsDown(): MutableList<Pt> = kai().also { p ->
        p[Lm.L_WRIST] = Pt(215f, 260f)
        p[Lm.R_WRIST] = Pt(185f, 262f)
    }

    /** The same pose seen in a mirror (front camera): x flipped, labels kept. */
    fun mirrored(p: List<Pt>, width: Float = 400f): List<Pt> = p.map { Pt(width - it.x, it.y, it.v) }

    /** A left-handed setup: the archer's left/right landmark labels are swapped. */
    fun swappedSides(p: List<Pt>): List<Pt> {
        val q = p.toMutableList()
        listOf(
            Lm.LEFT_EAR to Lm.RIGHT_EAR, Lm.L_SHOULDER to Lm.R_SHOULDER, Lm.L_ELBOW to Lm.R_ELBOW,
            Lm.L_WRIST to Lm.R_WRIST, Lm.L_HIP to Lm.R_HIP, Lm.L_KNEE to Lm.R_KNEE,
            Lm.L_ANKLE to Lm.R_ANKLE, Lm.L_FOOT to Lm.R_FOOT,
        ).forEach { (a, b) ->
            val t = q[a]
            q[a] = q[b]
            q[b] = t
        }
        return q
    }

    /** Adds up to ±[amount] px of tracking noise to every point (repeatable with [random]). */
    fun jitter(p: List<Pt>, random: Random, amount: Float = 1.5f): List<Pt> =
        p.map { Pt(it.x + (random.nextFloat() * 2 - 1) * amount, it.y + (random.nextFloat() * 2 - 1) * amount, it.v) }

    fun frame(p: List<Pt>) = PoseFrame(p, 400, 600)

    /** FormMetrics with chosen values, for testing the flag rules directly. */
    fun metrics(
        shoulderDev: Float = 0f,
        arrowAngle: Float = 0f,
        hipDev: Float? = 0f,
        bowElbow: Float = 175f,
        headTurn: Float = 0.8f,
    ): FormMetrics {
        val o = Pt(0f, 0f)
        return FormMetrics(
            shoulderWidth = 60f, arrowAngle = arrowAngle, shoulderAngle = shoulderDev + arrowAngle,
            hipAngle = hipDev?.let { it + shoulderDev + arrowAngle }, shoulderDev = shoulderDev, hipDev = hipDev,
            legSpread = 60f, stanceRatio = 2f, bowElbow = bowElbow, drawToFace = 0.4f, headTurn = headTurn,
            wristSpan = 2.7f, handsHigh = false, handsLow = false,
            nose = o, drawWrist = o, bowWrist = o, drawShoulder = o, bowShoulder = o, drawHip = o, bowHip = o,
        )
    }
}
