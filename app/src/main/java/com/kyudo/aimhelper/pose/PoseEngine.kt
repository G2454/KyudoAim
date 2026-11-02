package com.kyudo.aimhelper.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * CameraX analyzer that runs MediaPipe Pose Landmarker on every frame, on the device.
 * Results arrive on a MediaPipe thread through [onPose].
 */
class PoseEngine(
    context: Context,
    private val onPose: (PoseFrame?) -> Unit,
    private val onError: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    @Volatile
    private var closed = false
    private var lastTs = 0L

    private val landmarker: PoseLandmarker? = try {
        val base = BaseOptions.builder()
            .setModelAssetPath(MODEL)
            .setDelegate(Delegate.CPU)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result: PoseLandmarkerResult, input: MPImage -> handle(result, input) }
            .setErrorListener { e: RuntimeException -> onError(e.message ?: "Pose tracking error") }
            .build()
        PoseLandmarker.createFromOptions(context, options)
    } catch (e: Exception) {
        onError("Could not load the pose model (${e.message}). Rebuild the app so Gradle downloads it.")
        null
    }

    override fun analyze(image: ImageProxy) {
        val lm = landmarker
        if (lm == null || closed) {
            image.close()
            return
        }
        val rotation = image.imageInfo.rotationDegrees
        val raw: Bitmap? = try {
            image.toBitmap()
        } catch (e: Exception) {
            null
        } finally {
            image.close()
        }
        if (raw == null) return

        val upright = if (rotation == 0) raw else {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        }

        var ts = SystemClock.uptimeMillis()
        if (ts <= lastTs) ts = lastTs + 1
        lastTs = ts
        try {
            lm.detectAsync(BitmapImageBuilder(upright).build(), ts)
        } catch (e: Exception) {
            if (!closed) onError(e.message ?: "Pose tracking error")
        }
    }

    private fun handle(result: PoseLandmarkerResult, input: MPImage) {
        if (closed) return
        val w = input.width
        val h = input.height
        val lms = result.landmarks().firstOrNull()
        if (lms.isNullOrEmpty()) {
            onPose(null)
            return
        }
        onPose(
            PoseFrame(
                pts = lms.map { Pt(it.x() * w, it.y() * h, it.visibility().orElse(1f)) },
                imgW = w,
                imgH = h,
            ),
        )
    }

    fun close() {
        closed = true
        try {
            landmarker?.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        const val MODEL = "pose_landmarker_full.task"
    }
}
