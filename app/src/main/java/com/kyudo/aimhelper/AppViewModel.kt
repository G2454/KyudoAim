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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

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
            )
            updateCurrent { it.copy(shots = it.shots + shot) }
            lastShot = shot
            if (s.voice) voice.say("Kai %.1f seconds".format(Locale.US, rec.kaiMs / 1000f))
        }
        if (u.enteredKai && s.voice) flags.firstOrNull()?.let { voice.say(it.voice) }

        _live.value = LiveState(frame, m, u.stage, u.kaiMs, flags, fps, _live.value.error, lastShot)
    }

    fun onPoseError(message: String) {
        _live.update { it.copy(error = message) }
    }

    // ---- persistence ------------------------------------------------------

    private fun persist() {
        viewModelScope.launch(Dispatchers.IO) {
            saveLock.withLock { store.saveSessions(_sessions.value) }
        }
    }

    override fun onCleared() {
        voice.shutdown()
        super.onCleared()
    }
}
