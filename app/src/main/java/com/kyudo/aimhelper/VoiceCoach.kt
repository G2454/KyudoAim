package com.kyudo.aimhelper

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Short spoken cues so the archer never has to look at the phone. */
class VoiceCoach(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)

    @Volatile
    private var ready = false
    private var lastText = ""
    private var lastAt = 0L

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.ENGLISH)
            ready = true
        }
    }

    fun say(text: String) {
        if (!ready) return
        val now = System.currentTimeMillis()
        if (text == lastText && now - lastAt < 4000) return // don't nag
        lastText = text
        lastAt = now
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kyudo-$now")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
