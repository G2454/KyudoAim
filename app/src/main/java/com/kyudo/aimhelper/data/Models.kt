package com.kyudo.aimhelper.data

/**
 * Where the arrow landed. For a hit, [x] and [y] are positions on the mato
 * normalised to -1..1 (0,0 = centre), as seen by the archer from the shai:
 * +x = right = "front" (mae), +y = low.
 * For a miss, [miss] is one of Front / Back / High / Low.
 */
data class ShotResult(
    val hit: Boolean,
    val x: Float = 0f,
    val y: Float = 0f,
    val miss: String? = null,
)

/** One arrow. The form fields are filled when the camera detected the shot. */
data class Shot(
    val id: Long,
    val time: Long,
    val kaiMs: Long? = null,
    val arrowAngle: Float? = null,
    val shoulderDev: Float? = null,
    val hipDev: Float? = null,
    val legSpread: Float? = null,
    val bowElbow: Float? = null,
    val headMove: Float? = null,
    val flags: List<String> = emptyList(),
    val result: ShotResult? = null,
)

data class Session(
    val id: Long,
    val start: Long,
    val label: String,
    val shots: List<Shot>,
)

enum class NeraiRef(val label: String, val title: String, val desc: String) {
    HIDDEN(
        "Hidden", "Mato hidden",
        "The mato sits fully behind the bow. Used by some schools and teachers.",
    ),
    HALF(
        "Half moon", "半月 Hangetsu",
        "The bow's right edge splits the mato, so half of it is visible. The most common reference.",
    ),
    EDGE(
        "Edge", "Mato at the edge",
        "The bow just touches the left edge of the mato, which is almost fully visible.",
    ),
}

data class Settings(
    val nerai: NeraiRef = NeraiRef.HALF,
    val voice: Boolean = true,
    val frontCamera: Boolean = false,
    /** Goal for the length of kai, in seconds. */
    val minKaiSec: Float = 3f,
    /** Allowed angle between shoulder line and arrow line, in degrees. */
    val lineTolDeg: Float = 4f,
    /** Allowed tilt of the arrow from horizontal, in degrees. */
    val arrowTolDeg: Float = 4f,
)
