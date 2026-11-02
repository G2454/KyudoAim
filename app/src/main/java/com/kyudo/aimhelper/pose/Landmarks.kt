package com.kyudo.aimhelper.pose

import kotlin.math.sqrt

/** A landmark in pixels of the upright camera frame (y grows downwards). [v] = visibility 0..1. */
data class Pt(val x: Float, val y: Float, val v: Float = 1f)

/** One detected pose: 33 MediaPipe landmarks plus the size of the frame they live in. */
data class PoseFrame(val pts: List<Pt>, val imgW: Int, val imgH: Int)

/** MediaPipe Pose landmark indices (left/right are the archer's own sides). */
object Lm {
    const val NOSE = 0
    const val LEFT_EAR = 7
    const val RIGHT_EAR = 8
    const val L_SHOULDER = 11
    const val R_SHOULDER = 12
    const val L_ELBOW = 13
    const val R_ELBOW = 14
    const val L_WRIST = 15
    const val R_WRIST = 16
    const val L_HIP = 23
    const val R_HIP = 24
    const val L_KNEE = 25
    const val R_KNEE = 26
    const val L_ANKLE = 27
    const val R_ANKLE = 28
    const val L_FOOT = 31
    const val R_FOOT = 32

    val SKELETON = listOf(
        L_SHOULDER to R_SHOULDER,
        L_SHOULDER to L_ELBOW, L_ELBOW to L_WRIST,
        R_SHOULDER to R_ELBOW, R_ELBOW to R_WRIST,
        L_SHOULDER to L_HIP, R_SHOULDER to R_HIP, L_HIP to R_HIP,
        L_HIP to L_KNEE, L_KNEE to L_ANKLE, L_ANKLE to L_FOOT,
        R_HIP to R_KNEE, R_KNEE to R_ANKLE, R_ANKLE to R_FOOT,
    )

    val JOINTS = listOf(
        NOSE, L_SHOULDER, R_SHOULDER, L_ELBOW, R_ELBOW, L_WRIST, R_WRIST,
        L_HIP, R_HIP, L_KNEE, R_KNEE, L_ANKLE, R_ANKLE,
    )
}

fun dist(a: Pt, b: Pt): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

fun mid(a: Pt, b: Pt) = Pt((a.x + b.x) / 2f, (a.y + b.y) / 2f, minOf(a.v, b.v))
