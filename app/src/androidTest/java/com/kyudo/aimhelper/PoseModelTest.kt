package com.kyudo.aimhelper

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kyudo.aimhelper.pose.PoseEngine
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The MediaPipe model is packaged and loads with its native libraries on this device. */
@RunWith(AndroidJUnit4::class)
class PoseModelTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun modelIsInTheApk() {
        val size = context.assets.openFd(PoseEngine.MODEL).use { it.length }
        assertTrue("model is $size bytes", size > 1_000_000)
    }

    @Test
    fun poseEngineLoadsWithoutErrors() {
        var error: String? = null
        val engine = PoseEngine(context, onPose = {}, onError = { error = it })
        engine.close()
        assertNull("PoseEngine reported: $error", error)
    }
}
