package com.kyudo.aimhelper.pose

import com.kyudo.aimhelper.data.Settings
import com.kyudo.aimhelper.testutil.Poses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Edge cases of the form measurements and flag rules. */
class MetricsCalcEdgeTest {

    private val s = Settings()

    private fun assertSameMetrics(a: FormMetrics, b: FormMetrics) {
        assertEquals(a.arrowAngle, b.arrowAngle, 0.01f)
        assertEquals(a.shoulderDev, b.shoulderDev, 0.01f)
        assertEquals(a.hipDev!!, b.hipDev!!, 0.01f)
        assertEquals(a.bowElbow, b.bowElbow, 0.01f)
        assertEquals(a.headTurn, b.headTurn, 0.01f)
        assertEquals(a.legSpread!!, b.legSpread!!, 0.01f)
        assertEquals(a.drawToFace, b.drawToFace, 0.01f)
    }

    // ---- invariance ---------------------------------------------------------

    @Test
    fun frontCameraMirror_givesTheSameMeasurements() {
        val pose = Poses.kai(shoulderTilt = 6f)
        assertSameMetrics(MetricsCalc.compute(pose)!!, MetricsCalc.compute(Poses.mirrored(pose))!!)
    }

    @Test
    fun leftHandedSetup_findsTheBowHandOnTheOtherSide() {
        val pose = Poses.kai(shoulderTilt = 6f)
        assertSameMetrics(MetricsCalc.compute(pose)!!, MetricsCalc.compute(Poses.swappedSides(pose))!!)
    }

    // ---- missing or bad input -----------------------------------------------

    @Test
    fun tooFewLandmarks_returnsNull() {
        assertNull(MetricsCalc.compute(Poses.kai().take(20)))
        assertNull(MetricsCalc.compute(emptyList()))
    }

    @Test
    fun hiddenShoulders_returnsNull() {
        val p = Poses.kai()
        p[Lm.L_SHOULDER] = p[Lm.L_SHOULDER].copy(v = 0.2f)
        assertNull(MetricsCalc.compute(p))
    }

    @Test
    fun shouldersOnTopOfEachOther_returnsNull() {
        val p = Poses.kai()
        p[Lm.R_SHOULDER] = p[Lm.L_SHOULDER].copy(x = p[Lm.L_SHOULDER].x + 1f)
        assertNull(MetricsCalc.compute(p))
    }

    @Test
    fun hiddenHips_skipHipAndLegReadingsOnly() {
        val p = Poses.kai()
        p[Lm.L_HIP] = p[Lm.L_HIP].copy(v = 0.1f)
        val m = MetricsCalc.compute(p)
        assertNotNull(m)
        assertNull(m!!.hipAngle)
        assertNull(m.hipDev)
        assertNull(m.legSpread)
        assertNotNull(m.stanceRatio) // ankles are still visible
    }

    @Test
    fun hiddenAnkles_skipLegReadings() {
        val p = Poses.kai()
        p[Lm.R_ANKLE] = p[Lm.R_ANKLE].copy(v = 0f)
        val m = MetricsCalc.compute(p)!!
        assertNull(m.legSpread)
        assertNull(m.stanceRatio)
        assertNotNull(m.hipDev)
    }

    // ---- geometry -----------------------------------------------------------

    @Test
    fun arrowTilt_signMeansBowSideHigherOrLower() {
        assertTrue(MetricsCalc.compute(Poses.kai(bowWristY = 100f))!!.arrowAngle > 5f)
        assertTrue(MetricsCalc.compute(Poses.kai(bowWristY = 145f))!!.arrowAngle < -5f)
    }

    @Test
    fun bentBowArm_isMeasured() {
        val m = MetricsCalc.compute(Poses.kai(bowElbow = Pt(290f, 180f)))!!
        assertTrue("elbow ${m.bowElbow}", m.bowElbow < 150f)
    }

    @Test
    fun headTurn_isClampedBetweenZeroAndOne() {
        assertEquals(0f, MetricsCalc.compute(Poses.kai(noseX = 150f))!!.headTurn, 0f)
        assertEquals(1f, MetricsCalc.compute(Poses.kai(noseX = 260f))!!.headTurn, 0f)
    }

    @Test
    fun handPositions_areClassified() {
        assertTrue(MetricsCalc.compute(Poses.handsUp())!!.handsHigh)
        assertTrue(MetricsCalc.compute(Poses.handsDown())!!.handsLow)
        val kai = MetricsCalc.compute(Poses.kai())!!
        assertFalse(kai.handsHigh)
        assertFalse(kai.handsLow)
    }

    // ---- flag rules ---------------------------------------------------------

    @Test
    fun cleanForm_hasNoFlags() {
        assertTrue(MetricsCalc.flags(Poses.metrics(), s).isEmpty())
    }

    @Test
    fun tolerances_areStrictlyGreaterThan() {
        // Exactly at the tolerance is still fine; just over it is flagged.
        assertTrue(MetricsCalc.flags(Poses.metrics(shoulderDev = s.lineTolDeg), s).isEmpty())
        assertEquals(
            "Shoulders off the arrow line",
            MetricsCalc.flags(Poses.metrics(shoulderDev = s.lineTolDeg + 0.1f), s).single().label,
        )
        assertTrue(MetricsCalc.flags(Poses.metrics(arrowAngle = -s.arrowTolDeg), s).isEmpty())
    }

    @Test
    fun customTolerances_areRespected() {
        val loose = s.copy(lineTolDeg = 10f, arrowTolDeg = 10f)
        assertTrue(MetricsCalc.flags(Poses.metrics(shoulderDev = 8f, arrowAngle = 8f), loose).isEmpty())
        assertEquals(2, MetricsCalc.flags(Poses.metrics(shoulderDev = 8f, arrowAngle = 8f), s).size)
    }

    @Test
    fun arrowFlag_saysWhichSideIsHigh() {
        assertTrue(MetricsCalc.flags(Poses.metrics(arrowAngle = 7f), s).single().detail.contains("high"))
        assertTrue(MetricsCalc.flags(Poses.metrics(arrowAngle = -7f), s).single().detail.contains("low"))
    }

    @Test
    fun everyFlag_canFire_andShouldersComeFirst() {
        val f = MetricsCalc.flags(
            Poses.metrics(shoulderDev = 9f, arrowAngle = 9f, hipDev = 12f, bowElbow = 140f, headTurn = 0.1f),
            s,
        )
        assertEquals(
            listOf(
                "Shoulders off the arrow line", "Arrow not level", "Hips twisted against shoulders",
                "Bow arm bent", "Head not turned to the mato",
            ),
            f.map { it.label },
        )
        assertTrue(f.all { it.voice.isNotBlank() && it.detail.isNotBlank() })
    }

    @Test
    fun missingHips_neverRaiseAHipFlag() {
        assertTrue(MetricsCalc.flags(Poses.metrics(hipDev = null), s).isEmpty())
    }
}
