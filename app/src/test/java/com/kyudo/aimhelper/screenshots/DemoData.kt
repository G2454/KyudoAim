package com.kyudo.aimhelper.screenshots

import com.kyudo.aimhelper.LiveState
import com.kyudo.aimhelper.data.Session
import com.kyudo.aimhelper.data.Settings
import com.kyudo.aimhelper.data.Shot
import com.kyudo.aimhelper.data.ShotResult
import com.kyudo.aimhelper.pose.Lm
import com.kyudo.aimhelper.pose.MetricsCalc
import com.kyudo.aimhelper.pose.PoseFrame
import com.kyudo.aimhelper.pose.Pt
import com.kyudo.aimhelper.pose.Stage

/** Fixed, made-up data so the README screenshots look the same on every run. */
object DemoData {
    /** 2026-11-01 10:00 UTC. The home screen counts "last 7 days" from here. */
    const val NOW = 1_793_527_200_000L
    private const val DAY = 24 * 3600 * 1000L

    const val CURRENT_ID = 3L

    private fun hit(id: Long, x: Float, y: Float, kai: Long? = null, flags: List<String> = emptyList()) =
        Shot(
            id = id, time = NOW - id * 60_000, kaiMs = kai,
            arrowAngle = kai?.let { -1.2f }, shoulderDev = kai?.let { 2.5f }, bowElbow = kai?.let { 171f },
            flags = flags, result = ShotResult(true, x, y),
        )

    private fun miss(id: Long, side: String, kai: Long? = null, flags: List<String> = emptyList()) =
        Shot(
            id = id, time = NOW - id * 60_000, kaiMs = kai,
            arrowAngle = kai?.let { -3.1f }, shoulderDev = kai?.let { 6.2f }, bowElbow = kai?.let { 164f },
            flags = flags, result = ShotResult(false, miss = side),
        )

    val sessions: List<Session> = listOf(
        Session(
            id = 1, start = NOW - 5 * DAY, label = "Makiwara",
            shots = listOf(
                Shot(101, NOW - 5 * DAY, kaiMs = 2_600, flags = listOf("Short kai")),
                Shot(102, NOW - 5 * DAY, kaiMs = 3_100),
                Shot(103, NOW - 5 * DAY, kaiMs = 3_300, flags = listOf("Shoulders off the arrow line")),
            ),
        ),
        Session(
            id = 2, start = NOW - 2 * DAY, label = "Practice",
            shots = listOf(
                hit(201, 0.32f, 0.28f, 3_400),
                hit(202, 0.18f, 0.41f, 3_100),
                miss(203, "Front", 2_400, listOf("Short kai", "Shoulders off the arrow line")),
                hit(204, 0.44f, 0.12f, 3_600),
                miss(205, "Low", 2_800, listOf("Short kai")),
                hit(206, 0.05f, 0.36f, 3_300),
                miss(207, "Front", 3_200, listOf("Head moved at hanare")),
                hit(208, 0.26f, 0.20f, 3_500),
            ),
        ),
        Session(
            id = CURRENT_ID, start = NOW - 40 * 60_000, label = "Practice",
            shots = listOf(
                hit(301, 0.30f, 0.22f, 3_500),
                hit(302, 0.12f, 0.35f, 3_200),
                miss(303, "Front", 2_700, listOf("Short kai")),
                hit(304, 0.41f, 0.08f, 3_400, listOf("Shoulders off the arrow line")),
                hit(305, -0.08f, 0.30f, 3_800),
                Shot(306, NOW - 60_000, kaiMs = 3_300, flags = listOf("Shoulders off the arrow line")),
            ),
        ),
    )

    /** A synthetic archer at kai, facing the camera, in a 480×640 frame. */
    private val kaiPose: List<Pt> = MutableList(33) { Pt(235f, 400f, 0f) }.also { p ->
        p[Lm.NOSE] = Pt(232f, 158f, 1f)
        p[Lm.LEFT_EAR] = Pt(226f, 152f, 1f)
        p[Lm.RIGHT_EAR] = Pt(212f, 154f, 1f)
        p[Lm.L_SHOULDER] = Pt(270f, 203f, 1f)
        p[Lm.R_SHOULDER] = Pt(200f, 207f, 1f)
        p[Lm.L_ELBOW] = Pt(345f, 196f, 1f)
        p[Lm.L_WRIST] = Pt(420f, 192f, 1f)
        p[Lm.R_ELBOW] = Pt(150f, 196f, 1f)
        p[Lm.R_WRIST] = Pt(214f, 182f, 1f)
        p[Lm.L_HIP] = Pt(253f, 305f, 1f)
        p[Lm.R_HIP] = Pt(217f, 305f, 1f)
        p[Lm.L_KNEE] = Pt(281f, 378f, 1f)
        p[Lm.R_KNEE] = Pt(185f, 378f, 1f)
        p[Lm.L_ANKLE] = Pt(310f, 452f, 1f)
        p[Lm.R_ANKLE] = Pt(152f, 452f, 1f)
        p[Lm.L_FOOT] = Pt(322f, 462f, 1f)
        p[Lm.R_FOOT] = Pt(140f, 462f, 1f)
    }

    fun liveAtKai(settings: Settings = Settings()): LiveState {
        val metrics = MetricsCalc.compute(kaiPose)
        return LiveState(
            frame = PoseFrame(kaiPose, 480, 640),
            metrics = metrics,
            stage = Stage.KAI,
            kaiMs = 3_400,
            flags = metrics?.let { MetricsCalc.flags(it, settings) } ?: emptyList(),
            fps = 28f,
            lastShot = sessions.last().shots.last(),
        )
    }
}
