package com.kyudo.aimhelper.pose

import com.kyudo.aimhelper.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MetricsCalcTest {

    /** A synthetic archer at kai, seen from the front (bow hand on the image's right). */
    private fun kaiPose(shoulderTilt: Float = 0f): List<Pt> {
        val p = MutableList(33) { Pt(200f, 300f, 1f) }
        p[Lm.NOSE] = Pt(200f, 100f)
        p[Lm.LEFT_EAR] = Pt(180f, 100f)
        p[Lm.RIGHT_EAR] = Pt(194f, 100f)
        p[Lm.L_SHOULDER] = Pt(230f, 150f - shoulderTilt)
        p[Lm.R_SHOULDER] = Pt(170f, 150f)
        p[Lm.L_ELBOW] = Pt(290f, 136f)
        p[Lm.L_WRIST] = Pt(350f, 122f)
        p[Lm.R_ELBOW] = Pt(120f, 140f)
        p[Lm.R_WRIST] = Pt(185f, 120f)
        p[Lm.L_HIP] = Pt(215f, 270f)
        p[Lm.R_HIP] = Pt(185f, 270f)
        p[Lm.L_ANKLE] = Pt(267f, 380f)
        p[Lm.R_ANKLE] = Pt(133f, 380f)
        return p
    }

    @Test
    fun levelAngle_isPositiveWhenTargetSideIsHigher() {
        assertEquals(0f, MetricsCalc.levelAngle(Pt(0f, 0f), Pt(10f, 0f)), 0.01f)
        assertEquals(45f, MetricsCalc.levelAngle(Pt(0f, 10f), Pt(10f, 0f)), 0.01f)
        assertEquals(-45f, MetricsCalc.levelAngle(Pt(0f, 0f), Pt(10f, 10f)), 0.01f)
    }

    @Test
    fun angleAt_rightAngle() {
        assertEquals(90f, MetricsCalc.angleAt(Pt(0f, 0f), Pt(1f, 0f), Pt(0f, 1f)), 0.01f)
    }

    @Test
    fun cleanKaiPose_hasNoFlags() {
        val m = MetricsCalc.compute(kaiPose())
        assertNotNull(m)
        m!!
        assertTrue("arrow ${m.arrowAngle}", abs(m.arrowAngle) < 1f)
        assertTrue("shoulders ${m.shoulderDev}", abs(m.shoulderDev) < 1f)
        assertTrue("elbow ${m.bowElbow}", m.bowElbow > 170f)
        assertTrue("head ${m.headTurn}", m.headTurn > 0.5f)
        val legs = m.legSpread!!
        assertTrue("legs $legs", legs in 55f..70f)
        assertTrue(MetricsCalc.flags(m, Settings()).isEmpty())
    }

    @Test
    fun raisedBowShoulder_isFlagged() {
        val m = MetricsCalc.compute(kaiPose(shoulderTilt = 10f))!!
        assertTrue(m.shoulderDev > 5f)
        assertTrue(MetricsCalc.flags(m, Settings()).any { it.label.startsWith("Shoulders") })
    }
}
