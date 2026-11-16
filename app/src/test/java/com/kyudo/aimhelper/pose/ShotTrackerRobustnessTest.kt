package com.kyudo.aimhelper.pose

import com.kyudo.aimhelper.data.Settings
import com.kyudo.aimhelper.testutil.Poses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The stage detection must survive what real tracking looks like: noise, dropouts, repeats. */
class ShotTrackerRobustnessTest {

    private val settings = Settings()
    private val tracker = ShotTracker()
    private var time = 1_000L
    private val shots = mutableListOf<ShotRecord>()
    private val stages = mutableListOf<Stage>()
    private var noise: Random? = null

    private fun feed(pts: List<Pt>?, frames: Int = 1) {
        repeat(frames) {
            val input = pts?.let { p -> noise?.let { Poses.jitter(p, it) } ?: p }
            val u = tracker.onFrame(input?.let { MetricsCalc.compute(it) }, time, settings)
            if (stages.lastOrNull() != u.stage) stages += u.stage
            u.shot?.let { shots += it }
            time += 33
        }
    }

    private fun shoot(kaiFrames: Int = 110, dropoutInKai: Int = 0) {
        feed(Poses.handsDown(), 30)
        feed(Poses.handsUp(), 30)
        for (i in 0 until 30) {
            val d = (30 - i) * 2f
            feed(Poses.kai(drawX = 185f - d, drawY = 120f - d))
        }
        feed(Poses.kai(), kaiFrames / 2)
        feed(null, dropoutInKai)
        feed(Poses.kai(), kaiFrames - kaiFrames / 2)
        feed(Poses.kai(drawX = 60f, drawY = 130f), 2)
        feed(Poses.kai(drawX = 40f, drawY = 140f), 60)
    }

    @Test
    fun trackingNoise_stillDetectsKaiAndTheShot() {
        noise = Random(42) // ±1.5 px on every point, every frame
        shoot()
        assertEquals("shots $shots, stages $stages", 1, shots.size)
        assertTrue("kai ${shots[0].kaiMs}", shots[0].kaiMs in 3_200L..4_300L)
    }

    @Test
    fun shortDropoutDuringKai_doesNotLoseTheShot() {
        shoot(kaiFrames = 110, dropoutInKai = 10) // ~0.3 s with nobody detected
        assertEquals(1, shots.size)
        assertTrue("kai ${shots[0].kaiMs}", shots[0].kaiMs in 3_400L..4_600L)
    }

    @Test
    fun twoShotsInARow_giveTwoRecordsInOrder() {
        shoot()
        shoot()
        assertEquals(2, shots.size)
        val (a, b) = shots
        assertTrue("second shot starts after the first ends", b.marks.first() >= a.marks.last())
        shots.forEach { s -> assertTrue(s.marks.zipWithNext().all { (x, y) -> x <= y }) }
    }

    @Test
    fun prepareStep_isAtMostFiveSecondsLong() {
        feed(Poses.handsDown(), 300) // 10 s standing ready
        feed(Poses.handsUp(), 30)
        for (i in 0 until 30) {
            val d = (30 - i) * 2f
            feed(Poses.kai(drawX = 185f - d, drawY = 120f - d))
        }
        feed(Poses.kai(), 110)
        feed(Poses.kai(drawX = 60f, drawY = 130f), 2)
        feed(Poses.kai(drawX = 40f, drawY = 140f), 60)
        val m = shots.single().marks
        assertTrue("prepare ${m[1] - m[0]} ms", m[1] - m[0] in 4_000L..5_000L)
    }

    @Test
    fun drawingThatNeverSettles_givesUpAfterTwentySeconds() {
        feed(Poses.handsDown(), 10)
        feed(Poses.handsUp(), 10)
        // Keep the draw hand moving back and forth for 21 s: never a kai.
        for (i in 0 until 640) feed(Poses.kai(drawX = if (i % 2 == 0) 150f else 185f, drawY = 120f))
        assertEquals(Stage.PREPARE, tracker.stage)
        assertTrue(shots.isEmpty())
        assertTrue(Stage.KAI !in stages)
    }

    @Test
    fun reset_forgetsAShotInProgress() {
        feed(Poses.handsDown(), 10)
        feed(Poses.handsUp(), 10)
        assertEquals(Stage.UCHIOKOSHI, tracker.stage)
        tracker.reset()
        assertEquals(Stage.PREPARE, tracker.stage)
        feed(Poses.kai(), 100)
        assertTrue(shots.isEmpty())
    }
}
