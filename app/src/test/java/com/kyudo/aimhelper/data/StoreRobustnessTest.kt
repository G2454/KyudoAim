package com.kyudo.aimhelper.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** Saved data must never crash the app, even when it is damaged or from an older version. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StoreRobustnessTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file = File(context.filesDir, "sessions.json")

    @Test
    fun noFile_meansNoSessions() {
        file.delete()
        assertTrue(Store(context).loadSessions().isEmpty())
    }

    @Test
    fun damagedFile_isIgnoredInsteadOfCrashing() {
        file.writeText("{ this is not json")
        assertTrue(Store(context).loadSessions().isEmpty())
        file.writeText("")
        assertTrue(Store(context).loadSessions().isEmpty())
    }

    @Test
    fun fileFromTheFirstVersion_loadsWithDefaults() {
        // Written by version 0.1: no videoPath, no marks, no label, a shot without a result.
        file.writeText(
            """[{"id":7,"start":1700000000000,"shots":[
                {"id":70,"time":1700000001000,"kaiMs":3100,"flags":["Short kai"]},
                {"id":71,"time":1700000002000,"result":{"hit":false,"miss":"Front"}}
            ]}]""",
        )
        val s = Store(context).loadSessions().single()
        assertEquals("Practice", s.label)
        val (a, b) = s.shots
        assertEquals(3100L, a.kaiMs)
        assertNull(a.videoPath)
        assertTrue(a.marks.isEmpty())
        assertNull(a.result)
        assertEquals(ShotResult(false, miss = "Front"), b.result)
    }

    @Test
    fun notANumberValues_areDroppedNotCrashing() {
        val store = Store(context)
        store.saveSessions(
            listOf(Session(1, 0, "P", listOf(Shot(1, 0, arrowAngle = Float.NaN, shoulderDev = Float.POSITIVE_INFINITY)))),
        )
        val shot = store.loadSessions().single().shots.single()
        assertNull(shot.arrowAngle)
        assertNull(shot.shoulderDev)
    }

    @Test
    fun manySessions_roundTripInOrder() {
        val store = Store(context)
        val sessions = (1..50L).map { i ->
            Session(i, i * 1000, "S$i", (1..12L).map { j -> Shot(i * 100 + j, j, result = ShotResult(j % 2 == 0L, 0.1f, 0.2f)) })
        }
        store.saveSessions(sessions)
        assertEquals(sessions, store.loadSessions())
    }

    @Test
    fun emptySettings_giveTheDefaults() {
        context.getSharedPreferences("kyudo_settings", Context.MODE_PRIVATE).edit().clear().commit()
        assertEquals(Settings(), Store(context).loadSettings())
    }

    @Test
    fun unknownNeraiValue_fallsBackToHalfMoon() {
        context.getSharedPreferences("kyudo_settings", Context.MODE_PRIVATE).edit()
            .putString("nerai", "SOMETHING_OLD").commit()
        assertEquals(NeraiRef.HALF, Store(context).loadSettings().nerai)
    }
}
