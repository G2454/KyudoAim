package com.kyudo.aimhelper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {

    private fun hit(x: Float, y: Float, id: Long = 0) = Shot(id, 0, result = ShotResult(true, x, y))
    private fun miss(side: String, id: Long = 0) = Shot(id, 0, result = ShotResult(false, miss = side))

    @Test
    fun tallyAndRate_ignoreUnloggedShots() {
        val shots = listOf(hit(0.1f, 0.1f), miss("Front"), Shot(9, 0), hit(-0.2f, 0f))
        assertEquals("○×○", Stats.tally(shots))
        assertEquals(2f / 3f, Stats.hitRate(shots)!!, 1e-4f)
        assertEquals(2, Stats.hits(shots))
    }

    @Test
    fun emptyList_hasNoRateOrCenter() {
        assertNull(Stats.hitRate(emptyList()))
        assertNull(Stats.groupCenter(emptyList()))
        assertEquals("No hits yet", Stats.driftText(null))
    }

    @Test
    fun drift_isReportedInCentimetres() {
        // Mato radius is 18 cm: x = 0.5 → 9 cm front, y = 0.25 → 4.5 → 5 cm low.
        val text = Stats.driftText(Pair(0.5f, 0.25f))
        assertEquals("9 cm front (right) · 5 cm low", text)
        assertEquals("Centred", Stats.driftText(Pair(0.05f, -0.05f)))
    }

    @Test
    fun frontDrift_givesFormAdvice() {
        val shots = listOf(hit(0.5f, 0f), hit(0.45f, 0.1f), miss("Front"))
        assertTrue(Stats.advice(shots).startsWith("Front drift"))
    }

    @Test
    fun topFlags_areCounted() {
        val shots = listOf(
            Shot(1, 0, flags = listOf("Short kai", "Bow arm bent")),
            Shot(2, 0, flags = listOf("Short kai")),
        )
        assertEquals(listOf("Short kai" to 2, "Bow arm bent" to 1), Stats.topFlags(shots))
    }
}
