package com.kyudo.aimhelper

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kyudo.aimhelper.data.Session
import com.kyudo.aimhelper.data.Settings
import com.kyudo.aimhelper.data.Shot
import com.kyudo.aimhelper.data.ShotResult
import com.kyudo.aimhelper.data.Store
import com.kyudo.aimhelper.pose.Flag
import com.kyudo.aimhelper.pose.FormMetrics
import com.kyudo.aimhelper.pose.MetricsCalc
import com.kyudo.aimhelper.pose.PoseFrame
import com.kyudo.aimhelper.pose.ShotTracker
import com.kyudo.aimhelper.pose.Stage
import com.kyudo.aimhelper.video.ShotRecorder
import com.kyudo.aimhelper.video.VideoTools
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/** Everything the camera screen shows, refreshed on every analysed frame. */
data class LiveState(
    val frame: PoseFrame? = null,
    val metrics: FormMetrics? = null,
    val stage: Stage = Stage.PREPARE,
    val kaiMs: Long = 0L,
    val flags: List<Flag> = emptyList(),
    val fps: Float = 0f,
    val error: String? = null,
    val lastShot: Shot? = null,
    /** The camera is recording the shot video right now. */
    val recording: Boolean = false,
    /** Set when video recording isn't available on this phone. */
    val videoNote: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    private val _sessions = MutableStateFlow(store.loadSessions())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _settings = MutableStateFlow(store.loadSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _currentId = MutableStateFlow<Long?>(null)
    val currentId: StateFlow<Long?> = _currentId.asStateFlow()

    private val _live = MutableStateFlow(LiveState())
    val live: StateFlow<LiveState> = _live.asStateFlow()

    private val tracker = ShotTracker()

    @Volatile
    private var resetRequested = false
    private var lastFrameAt = 0L
    private var fps = 0f
    private val voice = VoiceCoach(app)
    private val saveLock = Mutex()
    private val ids = AtomicLong(System.currentTimeMillis())

    private fun newId() = ids.incrementAndGet()

    @Volatile
    private var recorder: ShotRecorder? = null

    /** Step start times (uptime) of shots whose video is still being cut, by shot id. */
    internal val pendingMarks = ConcurrentHashMap<Long, List<Long>>()

    /** Where file work runs. Tests swap in Dispatchers.Unconfined to run it inline. */
    internal var io: CoroutineDispatcher = Dispatchers.IO

    /** Cuts a recording down to the shot. Tests swap in a fake (no real video files). */
    internal var trimmer: (File, File, Long) -> Long? = VideoTools::trim

    // ---- sessions ---------------------------------------------------------

    fun startSession(label: String = "Practice") {
        val s = Session(newId(), System.currentTimeMillis(), label, emptyList())
        _sessions.update { it + s }
        _currentId.value = s.id
        persist()
    }

    fun endSession() {
        _currentId.value = null
    }

    fun deleteSession(id: Long) {
        _sessions.value.find { it.id == id }?.shots?.forEach { shot ->
            shot.videoPath?.let { path -> viewModelScope.launch(io) { File(path).delete() } }
        }
        _sessions.update { list -> list.filterNot { it.id == id } }
        if (_currentId.value == id) _currentId.value = null
        persist()
    }

    @Synchronized
    private fun ensureSession(): Long {
        _currentId.value?.let { id -> if (_sessions.value.any { it.id == id }) return id }
        startSession()
        return _currentId.value!!
    }

    private fun updateCurrent(transform: (Session) -> Session) {
        val id = ensureSession()
        _sessions.update { list -> list.map { if (it.id == id) transform(it) else it } }
        persist()
    }

    /** Puts a result on the oldest camera-detected shot still waiting, or adds a new shot. */
    fun recordResult(r: ShotResult) = updateCurrent { s ->
        val idx = s.shots.indexOfFirst { it.result == null }
        if (idx >= 0) {
            s.copy(shots = s.shots.mapIndexed { i, shot -> if (i == idx) shot.copy(result = r) else shot })
        } else {
            s.copy(shots = s.shots + Shot(id = newId(), time = System.currentTimeMillis(), result = r))
        }
    }

    fun undoLast() {
        if (_currentId.value == null) return
        updateCurrent { s ->
            val idx = s.shots.indexOfLast { it.result != null }
            when {
                idx < 0 -> s.copy(shots = s.shots.dropLast(1))
                s.shots[idx].kaiMs != null ->
                    // Camera-detected shot: keep the form data, clear only the result.
                    s.copy(shots = s.shots.mapIndexed { i, x -> if (i == idx) x.copy(result = null) else x })
                else -> s.copy(shots = s.shots.filterIndexed { i, _ -> i != idx })
            }
        }
    }

    // ---- settings ---------------------------------------------------------

    fun updateSettings(transform: (Settings) -> Settings) {
        _settings.update(transform)
        store.saveSettings(_settings.value)
    }

    fun markGuideSeen() = updateSettings { it.copy(seenGuide = true) }

    // ---- shot videos ------------------------------------------------------

    fun attachRecorder(r: ShotRecorder) {
        recorder = r
        _live.update { it.copy(videoNote = null) }
    }

    fun detachRecorder(r: ShotRecorder) {
        if (recorder === r) recorder = null
        _live.update { it.copy(recording = false) }
    }

    fun onVideoUnavailable(message: String) {
        recorder = null
        _live.update { it.copy(videoNote = message, recording = false) }
    }

    /** A recording containing a shot has finished: trim it to the shot and attach it. */
    fun onClip(clip: ShotRecorder.Clip) {
        val marks = pendingMarks.remove(clip.shotId)
        if (marks == null || marks.size < 6) {
            clip.file.delete()
            return
        }
        viewModelScope.launch(io) {
            val wantedStart = (marks[0] - clip.startUptime).coerceAtLeast(0)
            val dst = File(clip.file.parentFile, "shot_${clip.shotId}.mp4")
            val realStart = trimmer(clip.file, dst, wantedStart)
            val (path, base) = if (realStart != null) {
                clip.file.delete()
                dst.absolutePath to realStart
            } else {
                clip.file.absolutePath to 0L
            }
            val offsets = marks.map { (it - clip.startUptime - base).coerceAtLeast(0) }
            updateShot(clip.shotId) { it.copy(videoPath = path, marks = offsets) }
        }
    }

    private fun updateShot(shotId: Long, transform: (Shot) -> Shot) {
        _sessions.update { list ->
            list.map { s ->
                if (s.shots.none { it.id == shotId }) s
                else s.copy(shots = s.shots.map { if (it.id == shotId) transform(it) else it })
            }
        }
        persist()
    }

    // ---- live pose (called on the MediaPipe thread) -----------------------

    fun resetTracker() {
        resetRequested = true
        _live.update { it.copy(error = null) }
    }

    fun onPoseFrame(frame: PoseFrame?) {
        val t = SystemClock.uptimeMillis()
        if (resetRequested) {
            tracker.reset()
            resetRequested = false
        }
        if (lastFrameAt > 0 && t > lastFrameAt) fps = fps * 0.9f + (1000f / (t - lastFrameAt)) * 0.1f
        lastFrameAt = t

        val s = _settings.value
        val m = frame?.let { MetricsCalc.compute(it.pts) }
        val u = tracker.onFrame(m, t, s)
        val flags = if (m != null && (u.stage == Stage.HIKIWAKE || u.stage == Stage.KAI)) {
            MetricsCalc.flags(m, s)
        } else emptyList()

        var lastShot = _live.value.lastShot
        u.shot?.let { rec ->
            val shot = Shot(
                id = newId(),
                time = System.currentTimeMillis(),
                kaiMs = rec.kaiMs,
                arrowAngle = rec.arrowAngle,
                shoulderDev = rec.shoulderDev,
                hipDev = rec.hipDev,
                legSpread = rec.legSpread,
                bowElbow = rec.bowElbow,
                headMove = rec.headMove,
                flags = rec.flags,
                // Relative to the start of Prepare until a video replaces them (see onClip).
                marks = rec.marks.map { it - rec.marks.first() },
            )
            updateCurrent { it.copy(shots = it.shots + shot) }
            recorder?.let { r ->
                if (r.isRecording) {
                    pendingMarks[shot.id] = rec.marks
                    r.cut(shot.id)
                }
            }
            lastShot = shot
            if (s.voice) voice.say("Kai %.1f seconds".format(Locale.US, rec.kaiMs / 1000f))
        }
        if (u.enteredKai && s.voice) flags.firstOrNull()?.let { voice.say(it.voice) }

        // Drop long stretches of waiting between shots so videos stay small.
        recorder?.let { r ->
            if (u.stage == Stage.PREPARE && !r.isCutting && r.elapsedMs > 90_000) r.cut(null)
        }

        val prev = _live.value
        val next = LiveState(
            frame, m, u.stage, u.kaiMs, flags, fps.roundToInt().toFloat(), prev.error, lastShot,
            recording = recorder?.isRecording == true,
            videoNote = prev.videoNote,
        )
        // Nobody in view and nothing changed: skip the update so the screen isn't redrawn 30×/s.
        if (next != prev) _live.value = next
    }

    fun onPoseError(message: String) {
        _live.update { it.copy(error = message) }
    }

    // ---- previews and screenshot tests ------------------------------------

    /** Shows the given data without saving it. Used by the screenshot tests only. */
    internal fun loadPreviewData(sessions: List<Session>, currentId: Long?, live: LiveState? = null) {
        _sessions.value = sessions
        _currentId.value = currentId
        if (live != null) _live.value = live
    }

    // ---- persistence ------------------------------------------------------

    private fun persist() {
        viewModelScope.launch(io) {
            saveLock.withLock { store.saveSessions(_sessions.value) }
        }
    }

    override fun onCleared() {
        voice.shutdown()
        super.onCleared()
    }
}
