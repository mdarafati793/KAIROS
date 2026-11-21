package com.kairos.app.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.kairos.app.OrbState
import com.kairos.app.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

open class SimpleListener : RecognitionListener {
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onError(error: Int) {}
    override fun onResults(results: Bundle?) {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}

fun speechIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (Prefs.lang == "en") "en-US" else "bn-BD")
    if (Prefs.lang == "auto" && Build.VERSION.SDK_INT >= 33)
        putExtra("android.speech.extra.ADDITIONAL_LANGUAGES", arrayOf("en-US"))
}

/** Bilingual STT (bn-BD / en-US) + male-leaning TTS. Call listen() on the main thread. */
class Voice(private val ctx: Context) {
    val state = MutableStateFlow(OrbState.Idle)
    val level = MutableStateFlow(0f)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var sr: SpeechRecognizer? = null
    private var pending = 0

    init {
        tts = TextToSpeech(ctx.applicationContext) { st ->
            ttsReady = st == TextToSpeech.SUCCESS
            if (ttsReady) tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { state.value = OrbState.Speaking }
                override fun onDone(id: String?) = finish()
                @Deprecated("Deprecated in Java") override fun onError(id: String?) = finish()
            })
        }
    }

    private fun finish() {
        synchronized(this) {
            pending = (pending - 1).coerceAtLeast(0)
            if (pending == 0 && state.value == OrbState.Speaking) state.value = OrbState.Idle
        }
    }

    private fun clean(s: String) = s
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("https?://\\S+"), " ")
        .replace(Regex("[*_#`>|~]+"), " ")
        .replace(Regex("[\\p{So}\\p{Sk}]"), " ")

    fun speak(raw: String) {
        val t = clean(raw).trim()
        val e = tts ?: return
        if (!ttsReady || t.length < 2) return
        val bn = t.any { it in '\u0980'..'\u09FF' }
        val loc = if (bn) Locale("bn", "BD") else Locale.US
        try {
            e.setLanguage(loc)
            e.voices?.filter { it.locale.language == loc.language }?.sortedBy { it.isNetworkConnectionRequired }
                ?.firstOrNull { val n = it.name.lowercase(); n.contains("male") && !n.contains("female") }
                ?.let { e.setVoice(it) }
        } catch (ex: Exception) { }
        e.setPitch(Prefs.pitch); e.setSpeechRate(Prefs.rate)
        synchronized(this) { pending++ }
        e.speak(t, TextToSpeech.QUEUE_ADD, null, "k${System.nanoTime()}")
    }

    fun stopSpeaking() {
        tts?.stop(); synchronized(this) { pending = 0 }
        if (state.value == OrbState.Speaking) state.value = OrbState.Idle
    }

    fun listen(onPartial: (String) -> Unit = {}, onFinal: (String) -> Unit, fail: (String) -> Unit = {}) {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { fail("Speech recognition isn't available on this phone."); return }
        stopSpeaking()
        sr?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        sr = r
        r.setRecognitionListener(object : SimpleListener() {
            override fun onRmsChanged(rmsdB: Float) { level.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onEndOfSpeech() { level.value = 0f }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
            }
            override fun onResults(results: Bundle?) {
                level.value = 0f
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t.isNullOrBlank()) { state.value = OrbState.Idle; fail("I didn't catch that.") } else onFinal(t)
            }
            override fun onError(error: Int) {
                level.value = 0f; state.value = OrbState.Idle
                fail(if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) "I didn't catch that." else "Mic error ($error)")
            }
        })
        state.value = OrbState.Listening
        r.startListening(speechIntent())
    }

    fun stopListening() { sr?.stopListening(); level.value = 0f; if (state.value == OrbState.Listening) state.value = OrbState.Idle }
    fun release() { sr?.destroy(); tts?.shutdown() }
}

/** Speaks a streaming reply sentence-by-sentence so KAIROS talks while the rest is still generating. */
class SentenceSpeaker(private val v: Voice) {
    private val b = StringBuilder()
    private var inCode = false

    fun feed(chunk: String) {
        b.append(chunk)
        val idx = b.indexOfLast { it == '.' || it == '!' || it == '?' || it == '।' || it == '\n' }
        if (idx >= 15) { val seg = b.substring(0, idx + 1); b.delete(0, idx + 1); emit(seg) }
    }

    fun flush() { if (b.isNotBlank()) emit(b.toString()); b.clear() }

    private fun emit(seg: String) {
        val n = Regex("```").findAll(seg).count()
        val was = inCode
        if (n % 2 == 1) inCode = !inCode
        if (!was && n == 0) v.speak(seg)
    }
}
