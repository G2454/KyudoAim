package com.kyudo.aimhelper.video

import android.content.Context
import android.os.SystemClock
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Keeps the camera recording while Form check is open and cuts the video into one clip per
 * shot. When a shot ends, [cut] stops the current file and a new one starts right away.
 * Files without a shot (the archer was just waiting) are deleted.
 *
 * No audio is recorded, so no microphone permission is needed.
 */
class ShotRecorder(
    context: Context,
    private val onClip: (Clip) -> Unit,
) {
    /** A finished recording that contains the shot [shotId]. */
    data class Clip(val shotId: Long, val file: File, val startUptime: Long)

    private val appContext = context.applicationContext
    private val main = ContextCompat.getMainExecutor(appContext)

    private val recorder: Recorder = Recorder.Builder()
        .setQualitySelector(
            QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)),
        )
        .build()

    /** Bind this use case together with the preview and the analysis. */
    val videoCapture: VideoCapture<Recorder> = VideoCapture.withOutput(recorder)

    private var active: Recording? = null

    @Volatile
    private var stopped = false
    private var pendingShot: Long? = null

    @Volatile
    var isRecording = false
        private set

    @Volatile
    var isCutting = false
        private set

    @Volatile
    private var startUptime = 0L

    /** How long the current file has been recording. */
    val elapsedMs: Long
        get() = if (isRecording) SystemClock.uptimeMillis() - startUptime else 0L

    /** Starts recording. Call after the camera is bound. */
    fun start() = main.execute { startNow() }

    private fun startNow() {
        if (stopped || active != null) return
        val dir = File(appContext.filesDir, VIDEO_DIR).apply { mkdirs() }
        val file = File(dir, "raw_${System.currentTimeMillis()}.mp4")
        active = try {
            recorder.prepareRecording(appContext, FileOutputOptions.Builder(file).build())
                .start(main) { event -> onEvent(event, file) }
        } catch (e: Exception) {
            null
        }
    }

    private fun onEvent(event: VideoRecordEvent, file: File) {
        when (event) {
            is VideoRecordEvent.Start -> {
                startUptime = SystemClock.uptimeMillis()
                isRecording = true
            }
            is VideoRecordEvent.Finalize -> {
                isRecording = false
                isCutting = false
                active = null
                val shot = pendingShot
                pendingShot = null
                val usable = file.exists() && file.length() > 0 &&
                    (!event.hasError() || event.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE)
                if (shot != null && usable) {
                    onClip(Clip(shot, file, startUptime))
                } else {
                    file.delete()
                }
                startNow()
            }
            else -> Unit
        }
    }

    /**
     * Ends the current file. If [shotId] is set the file is handed to [onClip] for that shot;
     * with null it is thrown away (used to drop long stretches of waiting).
     */
    fun cut(shotId: Long?) = main.execute {
        val rec = active ?: return@execute
        isCutting = true
        pendingShot = shotId
        rec.stop()
    }

    /** Stops for good (Form check closed). A clip already being cut for a shot is kept. */
    fun release() {
        stopped = true // set right away so a recording that ends meanwhile doesn't restart
        main.execute { active?.stop() }
    }

    companion object {
        const val VIDEO_DIR = "videos"
    }
}
