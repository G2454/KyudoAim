package com.kyudo.aimhelper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsEdgeTest {

    private fun hit(x: Float, y: Float) = Shot(0, 0, result = ShotResult(true, x, y))
    private fun miss(side: String) = Shot(0, 0, result = ShotResult(false, miss = side))

    @Test
    fun allMisses_giveZeroRateAndNoCenter() {
        val shots = listOf(miss("Front"), miss("Low"))
        assertEquals(0f, Stats.hitRate(shots)!!, 0f)
        assertNull(Stats.groupCenter(shots))
        assertEquals("××", Stats.tally(shots))
    }

    @Test
    fun unloggedShots_areIgnoredEverywhere() {
        val shots = listOf(Shot(1, 0), Shot(2, 0, kaiMs = 3000))
        assertNull(Stats.hitRate(shots))
        assertEquals("", Stats.tally(shots))
        assertEquals("Log a few shots to see your drift.", Stats.advice(shots))
    }

    @Test
    fun positionText_describesHitsAndMisses() {
        assertEquals("Hit · centre", Stats.positionText(ShotResult(true, 0.02f, -0.03f)))
        assertEquals("Hit · 9 cm back (left) · 9 cm high", Stats.positionText(ShotResult(true, -0.5f, -0.5f)))
        assertEquals("Miss · low", Stats.positionText(ShotResult(false, miss = "Low")))
        assertEquals("Miss · unknown", Stats.positionText(ShotResult(false)))
    }

    @Test
    fun drift_ignoresLessThanTwoCentimetres() {
        // 0.1 × 18 cm = 1.8 → rounds to 2 cm (reported); 0.08 × 18 = 1.44 → 1 cm (ignored).
        assertEquals("2 cm front (right)", Stats.driftText(Pair(0.1f, 0.08f)))
    }

    @Test
    fun advice_coversEachDirection() {
        assertTrue(Stats.advice(listOf(hit(-0.5f, 0f), hit(-0.4f, 0f), miss("Back"))).startsWith("Back drift"))
        assertTrue(Stats.advice(listOf(hit(0f, 0.5f), miss("Low"), miss("Low"))).contains("Low arrows"))
        assertTrue(Stats.advice(listOf(hit(0f, -0.5f), miss("High"), miss("High"))).contains("High arrows"))
        assertTrue(Stats.advice(listOf(hit(0.05f, 0.02f), hit(-0.04f, -0.03f))).startsWith("Group looks even"))
    }

    @Test
    fun averageKai_usesOnlyCameraShots() {
        val shots = listOf(Shot(1, 0, kaiMs = 3000), Shot(2, 0, kaiMs = 4000), Shot(3, 0))
        assertEquals(3.5f, Stats.avgKaiSec(shots)!!, 0.001f)
        assertNull(Stats.avgKaiSec(listOf(Shot(1, 0))))
    }

    @Test
    fun topFlags_isLimitedAndSorted() {
        val shots = listOf(
            Shot(1, 0, flags = listOf("A", "B", "C", "D")),
            Shot(2, 0, flags = listOf("B", "C", "D")),
            Shot(3, 0, flags = listOf("C", "D")),
            Shot(4, 0, flags = listOf("D")),
        )
        assertEquals(listOf("D" to 4, "C" to 3, "B" to 2), Stats.topFlags(shots))
        assertEquals(listOf("D" to 4), Stats.topFlags(shots, n = 1))
    }
}
