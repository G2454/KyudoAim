package com.kyudo.aimhelper

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyudo.aimhelper.data.Session
import com.kyudo.aimhelper.data.Shot
import com.kyudo.aimhelper.data.ShotResult
import com.kyudo.aimhelper.pose.Pt
import com.kyudo.aimhelper.pose.Stage
import com.kyudo.aimhelper.testutil.Poses
import com.kyudo.aimhelper.video.ShotRecorder
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppViewModelTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var vm: AppViewModel

    private fun newVm() = AppViewModel(app).also { it.io = Dispatchers.Unconfined }

    @Before
    fun setUp() {
        vm = newVm()
        vm.updateSettings { it.copy(voice = false) }
    }

    private fun current(): Session = vm.sessions.value.first { it.id == vm.currentId.value }

    // ---- sessions and results ------------------------------------------------

    @Test
    fun startSession_makesItCurrent() {
        vm.startSession()
        assertEquals(1, vm.sessions.value.size)
        assertEquals(vm.sessions.value.single().id, vm.currentId.value)
        vm.endSession()
        assertNull(vm.currentId.value)
    }

    @Test
    fun loggingWithoutASession_startsOne() {
        vm.recordResult(ShotResult(true, 0.1f, 0.1f))
        val shot = current().shots.single()
        assertEquals(ShotResult(true, 0.1f, 0.1f), shot.result)
        assertTrue("logged by hand: no step data", shot.marks.isEmpty())
    }

    @Test
    fun result_goesToTheOldestShotTheCameraDetected() {
        val a = Shot(1, 0, kaiMs = 3000)
        val b = Shot(2, 0, kaiMs = 3200)
        vm.loadPreviewData(listOf(Session(9, 0, "P", listOf(a, b))), 9)
        vm.recordResult(ShotResult(false, miss = "Front"))
        assertEquals("Front", current().shots[0].result?.miss)
        assertNull(current().shots[1].result)
        vm.recordResult(ShotResult(true))
        assertEquals(true, current().shots[1].result?.hit)
        vm.recordResult(ShotResult(true)) // nothing pending: a new hand-logged shot
        assertEquals(3, current().shots.size)
    }

    @Test
    fun undo_clearsCameraResultsButRemovesHandLoggedShots() {
        vm.loadPreviewData(listOf(Session(9, 0, "P", listOf(Shot(1, 0, kaiMs = 3000)))), 9)
        vm.recordResult(ShotResult(true))
        vm.recordResult(ShotResult(true)) // hand-logged
        assertEquals(2, current().shots.size)
        vm.undoLast()
        assertEquals(1, current().shots.size)
        vm.undoLast()
        assertNull(current().shots.single().result) // the camera shot itself is kept
    }

    @Test
    fun undo_withoutASession_doesNothing() {
        vm.undoLast()
        assertTrue(vm.sessions.value.isEmpty())
    }

    @Test
    fun deleteSession_alsoDeletesItsVideos() {
        val dir = File(app.filesDir, "videos").apply { mkdirs() }
        val video = File(dir, "shot_1.mp4").apply { writeText("x") }
        vm.loadPreviewData(listOf(Session(9, 0, "P", listOf(Shot(1, 0, videoPath = video.path)))), 9)
        vm.deleteSession(9)
        assertTrue(vm.sessions.value.isEmpty())
        assertNull(vm.currentId.value)
        assertFalse(video.exists())
    }

    @Test
    fun sessionsAndSettings_surviveARestart() {
        vm.recordResult(ShotResult(true, 0.2f, 0.3f))
        vm.updateSettings { it.copy(showKanji = true, minKaiSec = 4f) }
        vm.markGuideSeen()
        val again = newVm()
        assertEquals(vm.sessions.value, again.sessions.value)
        assertTrue(again.settings.value.showKanji)
        assertTrue(again.settings.value.seenGuide)
        assertEquals(4f, again.settings.value.minKaiSec, 0f)
    }

    // ---- shot videos ----------------------------------------------------------

    private val marks = listOf(10_000L, 14_000L, 16_000L, 18_500L, 21_900L, 23_400L)

    private fun rawClip(): File =
        File(app.filesDir, "videos").apply { mkdirs() }.let { File(it, "raw_test.mp4").apply { writeText("raw") } }

    @Test
    fun clip_isTrimmedAndStepTimesBecomeVideoTimes() {
        vm.loadPreviewData(listOf(Session(9, 0, "P", listOf(Shot(500, 0)))), 9)
        vm.pendingMarks[500L] = marks
        var asked = -1L
        vm.trimmer = { _, dst, start ->
            asked = start
            dst.writeText("trimmed")
            900L // the key frame before the requested start, relative to the raw file
        }
        val raw = rawClip()
        vm.onClip(ShotRecorder.Clip(500, raw, startUptime = 4_000))

        assertEquals("trim from Prepare (10 000 − 4 000)", 6_000L, asked)
        val shot = current().shots.single()
        assertTrue(shot.videoPath!!.endsWith("shot_500.mp4"))
        assertTrue(File(shot.videoPath!!).exists())
        assertFalse("raw recording deleted", raw.exists())
        assertEquals(marks.map { it - 4_000 - 900 }, shot.marks)
        assertTrue(vm.pendingMarks.isEmpty())
    }

    @Test
    fun clip_keepsTheRawFileWhenTrimmingFails() {
        vm.loadPreviewData(listOf(Session(9, 0, "P", listOf(Shot(500, 0)))), 9)
        vm.pendingMarks[500L] = marks
        vm.trimmer = { _, _, _ -> null }
        val raw = rawClip()
        vm.onClip(ShotRecorder.Clip(500, raw, startUptime = 4_000))
        val shot = current().shots.single()
        assertEquals(raw.absolutePath, shot.videoPath)
        assertEquals(marks.map { it - 4_000 }, shot.marks)
    }

    @Test
    fun clip_forAnUnknownShot_isDeleted() {
        val raw = rawClip()
        vm.onClip(ShotRecorder.Clip(12345, raw, startUptime = 0))
        assertFalse(raw.exists())
    }

    @Test
    fun clip_startingAfterPrepare_isClampedToZero() {
        vm.loadPreviewData(listOf(Session(9, 0, "P", listOf(Shot(500, 0)))), 9)
        vm.pendingMarks[500L] = marks
        var asked = -1L
        vm.trimmer = { _, dst, start -> asked = start; dst.writeText("t"); 0L }
        vm.onClip(ShotRecorder.Clip(500, rawClip(), startUptime = 12_000)) // started 2 s into Prepare
        assertEquals(0L, asked)
        assertEquals(0L, current().shots.single().marks.first())
    }

    // ---- live pose → shot, end to end ----------------------------------------

    private fun feed(pts: List<Pt>?, frames: Int = 1) {
        repeat(frames) {
            vm.onPoseFrame(pts?.let { Poses.frame(it) })
            ShadowSystemClock.advanceBy(Duration.ofMillis(33))
        }
    }

    @Test
    fun aFullShotInFrontOfTheCamera_isSavedWithStepTimes() {
        vm.startSession()
        val seen = mutableSetOf<Stage>()
        fun step(pts: List<Pt>, frames: Int) = repeat(frames) { feed(pts); seen += vm.live.value.stage }

        step(Poses.handsDown(), 30)
        step(Poses.handsUp(), 30)
        for (i in 0 until 30) {
            val d = (30 - i) * 2f
            step(Poses.kai(drawX = 185f - d, drawY = 120f - d), 1)
        }
        step(Poses.kai(), 110)
        assertEquals(Stage.KAI, vm.live.value.stage)
        assertTrue("kai timer runs", vm.live.value.kaiMs > 3_000)
        step(Poses.kai(drawX = 60f, drawY = 130f), 2)
        step(Poses.kai(drawX = 40f, drawY = 140f), 60)

        assertEquals(Stage.entries.toSet(), seen)
        val shot = current().shots.single()
        assertNotNull(shot.kaiMs)
        assertTrue("kai ${shot.kaiMs}", shot.kaiMs!! in 3_300L..4_300L)
        assertEquals(6, shot.marks.size)
        assertEquals(0L, shot.marks.first())
        assertNull("waits for the result", shot.result)
        assertEquals(shot, vm.live.value.lastShot)
        assertNotNull(vm.live.value.metrics)
    }

    @Test
    fun nobodyInView_keepsTheLiveStateQuiet() {
        feed(null, 60) // let the frame-rate reading settle
        val before = vm.live.value
        feed(null, 30)
        assertTrue("no new state while nothing changes", before === vm.live.value)
        assertNull(vm.live.value.metrics)
    }
}
