package com.kyudo.aimhelper

import android.content.Intent
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kyudo.aimhelper.video.ShotRecorder
import com.kyudo.aimhelper.video.VideoTools
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** Trimming, saving and sharing with real video files on a real Android media stack. */
@RunWith(AndroidJUnit4::class)
class VideoToolsDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.filesDir, ShotRecorder.VIDEO_DIR).apply { mkdirs() }
    private val created = mutableListOf<Uri>()

    @After
    fun cleanUp() {
        dir.listFiles()?.filter { it.name.startsWith("test_") }?.forEach { it.delete() }
        created.forEach { context.contentResolver.delete(it, null, null) }
    }

    /** Encodes a [seconds]-long grey H.264 video with a key frame every second. */
    private fun makeVideo(file: File, seconds: Int = 3, fps: Int = 10) {
        val w = 320
        val h = 240
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 400_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        val total = seconds * fps
        var fed = 0
        var inputDone = false
        var outputDone = false
        val info = MediaCodec.BufferInfo()
        while (!outputDone) {
            if (!inputDone) {
                val inIdx = codec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val pts = fed * 1_000_000L / fps
                    if (fed == total) {
                        codec.queueInputBuffer(inIdx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        fillGrey(codec.getInputImage(inIdx)!!, w, h, 40 + (fed * 5) % 180)
                        codec.queueInputBuffer(inIdx, 0, w * h * 3 / 2, pts, 0)
                        fed++
                    }
                }
            }
            val outIdx = codec.dequeueOutputBuffer(info, 10_000)
            if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                track = muxer.addTrack(codec.outputFormat)
                muxer.start()
            } else if (outIdx >= 0) {
                val buf = codec.getOutputBuffer(outIdx)!!
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                if (info.size > 0 && track >= 0) {
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    muxer.writeSampleData(track, buf, info)
                }
                codec.releaseOutputBuffer(outIdx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
        codec.stop()
        codec.release()
        muxer.stop()
        muxer.release()
    }

    private fun fillGrey(image: Image, w: Int, h: Int, luma: Int) {
        image.planes.forEachIndexed { i, plane ->
            val pw = if (i == 0) w else w / 2
            val ph = if (i == 0) h else h / 2
            val value = (if (i == 0) luma else 128).toByte()
            val buf = plane.buffer
            for (y in 0 until ph) for (x in 0 until pw) buf.put(y * plane.rowStride + x * plane.pixelStride, value)
        }
    }

    private fun durationMs(file: File): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        } finally {
            r.release()
        }
    }

    @Test
    fun trim_startsAtTheKeyFrameBeforeTheRequestedTime() {
        val src = File(dir, "test_src.mp4")
        makeVideo(src)
        assertTrue("source is about 3 s", abs(durationMs(src) - 3_000) < 400)

        val dst = File(dir, "test_dst.mp4")
        val start = VideoTools.trim(src, dst, startMs = 1_500)
        assertNotNull("trim failed", start)
        assertTrue("starts at a key frame ≤ 1.5 s, got $start", start!! in 0L..1_500L)
        assertTrue(dst.length() > 0)
        val expected = 3_000 - start
        assertTrue("trimmed length ${durationMs(dst)} vs $expected", abs(durationMs(dst) - expected) < 400)
    }

    @Test
    fun trim_fromZero_keepsEverything() {
        val src = File(dir, "test_src0.mp4")
        makeVideo(src)
        val dst = File(dir, "test_dst0.mp4")
        assertEquals(0L, VideoTools.trim(src, dst, 0))
        assertTrue(abs(durationMs(dst) - durationMs(src)) < 300)
    }

    @Test
    fun trim_ofABrokenFile_returnsNullAndLeavesNothing() {
        val src = File(dir, "test_broken.mp4").apply { writeText("not a video") }
        val dst = File(dir, "test_broken_out.mp4")
        assertNull(VideoTools.trim(src, dst, 0))
        assertFalse(dst.exists())
    }

    @Test
    fun saveToGallery_writesAPlayableVideo() {
        assumeTrue("needs Android 10+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val src = File(dir, "test_gallery.mp4")
        makeVideo(src, seconds = 1)
        val uri = VideoTools.saveToGallery(context, src, "kyudo_test_${System.currentTimeMillis()}.mp4")
        assertNotNull(uri)
        created += uri!!
        val size = context.contentResolver.openFileDescriptor(uri, "r")!!.use { it.statSize }
        assertEquals(src.length(), size)
    }

    @Test
    fun shareIntent_givesOtherAppsReadAccess() {
        val src = File(dir, "test_share.mp4").apply { writeText("video") }
        val chooser = VideoTools.shareIntent(context, src)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("${context.packageName}.files", uri.authority)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        assertEquals("video", text)
    }
}
