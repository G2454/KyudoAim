package com.kyudo.aimhelper.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Runs on Robolectric because Store uses org.json and SharedPreferences. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun sessions_roundTrip() {
        val store = Store(context)
        val sessions = listOf(
            Session(
                id = 1, start = 1_700_000_000_000, label = "Practice",
                shots = listOf(
                    Shot(
                        id = 10, time = 1_700_000_001_000, kaiMs = 3_400, arrowAngle = -1.5f,
                        shoulderDev = 4.5f, hipDev = null, legSpread = 58f, bowElbow = 172f,
                        headMove = 0.05f, flags = listOf("Shoulders off the arrow line"),
                        result = ShotResult(true, 0.4f, -0.2f),
                    ),
                    Shot(id = 11, time = 1_700_000_002_000, result = ShotResult(false, miss = "Front")),
                    Shot(id = 12, time = 1_700_000_003_000),
                ),
            ),
        )
        store.saveSessions(sessions)
        assertEquals(sessions, Store(context).loadSessions())
    }

    @Test
    fun settings_roundTrip() {
        val store = Store(context)
        val s = Settings(nerai = NeraiRef.EDGE, voice = false, frontCamera = true, minKaiSec = 4.5f, lineTolDeg = 3f, arrowTolDeg = 6f)
        store.saveSettings(s)
        assertEquals(s, Store(context).loadSettings())
    }
}
