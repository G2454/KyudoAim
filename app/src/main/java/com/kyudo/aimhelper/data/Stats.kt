package com.kyudo.aimhelper.data

import kotlin.math.abs
import kotlin.math.roundToInt

/** Summaries over a list of shots, shared by the screens. */
object Stats {
    /** Standard kasumi-mato: 36 cm across. */
    const val MATO_RADIUS_CM = 18f

    fun results(shots: List<Shot>): List<ShotResult> = shots.mapNotNull { it.result }

    fun hits(shots: List<Shot>): Int = results(shots).count { it.hit }

    fun hitRate(shots: List<Shot>): Float? {
        val r = results(shots)
        return if (r.isEmpty()) null else r.count { it.hit }.toFloat() / r.size
    }

    /** Average landing point of the hits, or null with no hits. */
    fun groupCenter(shots: List<Shot>): Pair<Float, Float>? {
        val h = results(shots).filter { it.hit }
        if (h.isEmpty()) return null
        return Pair(h.map { it.x }.average().toFloat(), h.map { it.y }.average().toFloat())
    }

    fun tally(shots: List<Shot>): String =
        results(shots).joinToString("") { if (it.hit) "○" else "×" }

    fun driftText(center: Pair<Float, Float>?): String {
        if (center == null) return "No hits yet"
        val (x, y) = center
        val parts = mutableListOf<String>()
        val cmX = (abs(x) * MATO_RADIUS_CM).roundToInt()
        val cmY = (abs(y) * MATO_RADIUS_CM).roundToInt()
        if (cmX >= 2) parts += "$cmX cm " + if (x > 0) "front (right)" else "back (left)"
        if (cmY >= 2) parts += "$cmY cm " + if (y > 0) "low" else "high"
        return if (parts.isEmpty()) "Centred" else parts.joinToString(" · ")
    }

    fun positionText(r: ShotResult): String {
        if (!r.hit) return "Miss · ${r.miss?.lowercase() ?: "unknown"}"
        val c = driftText(Pair(r.x, r.y))
        return if (c == "Centred") "Hit · centre" else "Hit · $c"
    }

    fun advice(shots: List<Shot>): String {
        val r = results(shots)
        if (r.isEmpty()) return "Log a few shots to see your drift."
        val c = groupCenter(shots)
        val front = r.count { it.miss == "Front" } + r.count { it.hit && it.x > 0.3f }
        val back = r.count { it.miss == "Back" } + r.count { it.hit && it.x < -0.3f }
        val low = r.count { it.miss == "Low" } + r.count { it.hit && it.y > 0.3f }
        val high = r.count { it.miss == "High" } + r.count { it.hit && it.y < -0.3f }
        val tips = mutableListOf<String>()
        if (front > back && (front >= 2 || (c?.first ?: 0f) > 0.15f)) {
            tips += "Front drift usually comes from tenouchi or the release (yugaeri not completing), not the sight. Fix form before moving your nerai."
        } else if (back > front && (back >= 2 || (c?.first ?: 0f) < -0.15f)) {
            tips += "Back drift often comes from the bow hand pushing late or the head moving at hanare."
        }
        if (low > high && low >= 2) tips += "Low arrows: check kai length and keep the arrow level."
        if (high > low && high >= 2) tips += "High arrows: check that the bow hand isn't lifting at hanare."
        return if (tips.isEmpty()) "Group looks even. Keep the same nerai and rhythm." else tips.joinToString(" ")
    }

    fun avgKaiSec(shots: List<Shot>): Float? {
        val k = shots.mapNotNull { it.kaiMs }
        return if (k.isEmpty()) null else k.average().toFloat() / 1000f
    }

    fun topFlags(shots: List<Shot>, n: Int = 3): List<Pair<String, Int>> =
        shots.flatMap { it.flags }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(n).map { it.key to it.value }
}
