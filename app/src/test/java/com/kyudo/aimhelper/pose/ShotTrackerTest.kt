package com.kyudo.aimhelper.pose

import com.kyudo.aimhelper.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Feeds a synthetic shot (30 fps) through the hassetsu state machine. */
class ShotTrackerTest {

    private val settings = Settings()
    private val tracker = ShotTracker()
    private var time = 1_000L
    private val stages = mutableListOf<Stage>()
    private var shot: ShotRecord? = null

    private fun pose(
        drawX: Float = 185f,
        drawY: Float = 120f,
        handsUp: Boolean = false,
        handsDown: Boolean = false,
    ): List<Pt> {
        val p = MutableList(33) { Pt(200f, 300f, 1f) }
        p[Lm.NOSE] = Pt(200f, 100f)
        p[Lm.LEFT_EAR] = Pt(180f, 100f)
        p[Lm.RIGHT_EAR] = Pt(194f, 100f)
        p[Lm.L_SHOULDER] = Pt(230f, 150f)
        p[Lm.R_SHOULDER] = Pt(170f, 150f)
        p[Lm.L_ELBOW] = Pt(290f, 136f)
        p[Lm.L_WRIST] = Pt(350f, 122f)
        p[Lm.R_ELBOW] = Pt(120f, 140f)
        p[Lm.R_WRIST] = Pt(drawX, drawY)
        if (handsUp) {
            p[Lm.L_ELBOW] = Pt(250f, 90f)
            p[Lm.L_WRIST] = Pt(240f, 40f)
            p[Lm.R_WRIST] = Pt(160f, 45f)
        }
        if (handsDown) {
            p[Lm.L_WRIST] = Pt(215f, 260f)
            p[Lm.R_WRIST] = Pt(185f, 262f)
        }
        p[Lm.L_HIP] = Pt(215f, 270f)
        p[Lm.R_HIP] = Pt(185f, 270f)
        p[Lm.L_ANKLE] = Pt(267f, 380f)
        p[Lm.R_ANKLE] = Pt(133f, 380f)
        return p
    }

    private fun feed(pts: List<Pt>?, frames: Int) {
        repeat(frames) {
            val u = tracker.onFrame(pts?.let { MetricsCalc.compute(it) }, time, settings)
            if (stages.lastOrNull() != u.stage) stages += u.stage
            if (u.shot != null) shot = u.shot
            time += 33
        }
    }

    private fun shootOnce(kaiFrames: Int) {
        feed(pose(handsDown = true), 30) // ashibumi → yugamae
        feed(pose(handsUp = true), 30) // uchiokoshi
        for (i in 0 until 30) { // hikiwake: draw hand travels to the face
            val d = (30 - i) * 2f
            feed(pose(drawX = 185f - d, drawY = 120f - d), 1)
        }
        feed(pose(), kaiFrames) // kai
        feed(pose(drawX = 60f, drawY = 130f), 2) // hanare
        feed(pose(drawX = 40f, drawY = 140f), 60) // zanshin
    }

    @Test
    fun fullShot_isDetectedWithKaiLength() {
        shootOnce(kaiFrames = 110)
        assertEquals(
            listOf(Stage.PREPARE, Stage.UCHIOKOSHI, Stage.HIKIWAKE, Stage.KAI, Stage.ZANSHIN, Stage.PREPARE),
            stages,
        )
        val s = shot
        assertNotNull("no shot recorded", s)
        s!!
        assertTrue("kai was ${s.kaiMs} ms", s.kaiMs in 3_300L..4_200L)
        assertFalse(s.flags.contains("Short kai"))
        assertTrue("flags ${s.flags}", s.flags.isEmpty())
    }

    @Test
    fun shortKai_isFlagged() {
        shootOnce(kaiFrames = 45) // about 1.2 s
        val s = shot
        assertNotNull(s)
        assertTrue(s!!.flags.contains("Short kai"))
    }

    @Test
    fun lettingDown_recordsNoShot() {
        feed(pose(handsDown = true), 30)
        feed(pose(handsUp = true), 30)
        feed(pose(handsDown = true), 60)
        assertNull(shot)
        assertEquals(Stage.PREPARE, tracker.stage)
    }

    @Test
    fun losingTheArcher_resetsTheStage() {
        feed(pose(handsDown = true), 10)
        feed(pose(handsUp = true), 10)
        assertEquals(Stage.UCHIOKOSHI, tracker.stage)
        feed(null, 60)
        assertEquals(Stage.PREPARE, tracker.stage)
    }
}
