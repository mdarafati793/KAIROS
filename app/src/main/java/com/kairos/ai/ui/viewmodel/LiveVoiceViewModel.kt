package com.kairos.ai.ui.viewmodel

import android.annotation.SuppressLint; import android.app.Application; import android.content.Intent
import android.graphics.*; import android.media.*; import android.os.Bundle
import android.speech.*; import android.speech.tts.*; import android.util.Base64
import androidx.camera.core.ImageProxy; import androidx.lifecycle.*; import com.kairos.ai.KairosApplication
import com.kairos.ai.accessibility.AccessibilityBridge; import com.kairos.ai.ai.PromptBuilder
import com.kairos.ai.data.local.ChatMessageEntity; import com.kairos.ai.data.model.StreamChunk
import kotlinx.coroutines.*; import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream; import java.util.*; import kotlin.math.*

enum class VoicePhase { IDLE, LISTENING, THINKING, SPEAKING }

class LiveVoiceViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {
    private val app = application as KairosApplication
    private val _phase = MutableStateFlow(VoicePhase.IDLE); val phase = _phase.asStateFlow()
    private val _transcript = MutableStateFlow(""); val transcript = _transcript.asStateFlow()
    private val _aiReply = MutableStateFlow(""); val aiReply = _aiReply.asStateFlow()
    private val _currentDb = MutableStateFlow(0.0); val currentDb = _currentDb.asStateFlow()
    private val _visionEnabled = MutableStateFlow(false); val visionEnabled = _visionEnabled.asStateFlow()
    private val _statusText = MutableStateFlow("Tap Mic to start Live Voice"); val statusText = _statusText.asStateFlow()

    private var tts: TextToSpeech? = TextToSpeech(application, this); private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null; private var bargeInJob: Job? = null; private var queryJob: Job? = null
    @Volatile private var latestFrameB64: String? = null; @Volatile private var lastFrameMs = 0L
    private val voiceHistory = mutableListOf<ChatMessageEntity>()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault(); ttsReady = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { _phase.value = VoicePhase.SPEAKING; startBargeInMonitor() }
                override fun onDone(id: String?) { stopBargeInMonitor(); viewModelScope.launch(Dispatchers.Main) { if (_phase.value == VoicePhase.SPEAKING) startListeningInternal() } }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) { stopBargeInMonitor(); _phase.value = VoicePhase.IDLE }
            })
        }
    }

    fun toggleVision(on: Boolean) { _visionEnabled.value = on; if (!on) latestFrameB64 = null }

    fun onCameraFrame(proxy: ImageProxy) {
        try {
            if (!_visionEnabled.value) return; val now = System.currentTimeMillis()
            if (now - lastFrameMs < 2000L) return; lastFrameMs = now
            val p = proxy.planes.firstOrNull() ?: return; val b = ByteArray(p.buffer.remaining()); p.buffer.get(b)
            val raw = BitmapFactory.decodeByteArray(b, 0, b.size) ?: return
            val rot = proxy.imageInfo.rotationDegrees
            val bmp = if (rot == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rot.toFloat()) }, true).also { if (it !== raw) raw.recycle() }
            val scaled = Bitmap.createScaledBitmap(bmp, 512, 512, true).also { if (it !== bmp) bmp.recycle() }
            val out = ByteArrayOutputStream(); scaled.compress(Bitmap.CompressFormat.JPEG, 72, out); scaled.recycle()
            latestFrameB64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Throwable) {} finally { proxy.close() }
    }

    fun startSession() = startListeningInternal()
    fun stopSession() { stopBargeInMonitor(); queryJob?.cancel(); tts?.stop(); runCatching { speechRecognizer?.cancel() }; _phase.value = VoicePhase.IDLE; _statusText.value = "Voice session paused" }

    private fun startListeningInternal() {
        viewModelScope.launch(Dispatchers.Main) {
            stopBargeInMonitor(); tts?.stop(); _phase.value = VoicePhase.LISTENING; _statusText.value = "Listening..."
            if (!SpeechRecognizer.isRecognitionAvailable(app)) { _statusText.value = "Speech recognition unavailable."; _phase.value = VoicePhase.IDLE; return@launch }
            if (speechRecognizer == null) speechRecognizer = SpeechRecognizer.createSpeechRecognizer(app)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { _statusText.value = "Speak now..." }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rms: Float) { _currentDb.value = (rms + 50.0).coerceIn(0.0, 100.0) }
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() { _statusText.value = "Processing..." }
                override fun onError(e: Int) { if (_phase.value == VoicePhase.LISTENING) { _phase.value = VoicePhase.IDLE; _statusText.value = "Tap Mic to speak again" } }
                override fun onPartialResults(r: Bundle?) { r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { if (it.isNotBlank()) _transcript.value = it } }
                override fun onEvent(t: Int, p: Bundle?) {}
                override fun onResults(r: Bundle?) {
                    val spoken = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                    if (spoken.isNotEmpty()) { _transcript.value = spoken; sendVoiceQuery(spoken) } else { _phase.value = VoicePhase.IDLE; _statusText.value = "Tap Mic to speak" }
                }
            })
            speechRecognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true); putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        }
    }

    private fun sendVoiceQuery(spoken: String) {
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            _phase.value = VoicePhase.THINKING; _statusText.value = if (_visionEnabled.value && latestFrameB64 != null) "Thinking with Vision..." else "Thinking..."; _aiReply.value = ""
            voiceHistory.add(ChatMessageEntity(UUID.randomUUID().toString(), "live_voice", "user", spoken, timestamp = System.currentTimeMillis()))
            val caps = app.preferences.getCapabilities(); val acc = AccessibilityBridge.isConnected(app)
            val sys = PromptBuilder.buildMasterSystemPrompt(app.preferences.getCustomInstructions(), app.database.memoryDao().getAllMemories(), acc, app.mcpManager.getActiveTools(), caps) + "\nKeep answer concise for voice."
            val frames = latestFrameB64?.let { listOf("image/jpeg" to it) } ?: emptyList()
            val sb = StringBuilder()
            app.aiClient.sendMessageWithFallback(sys, voiceHistory.takeLast(12), caps, acc, frames).collect { c ->
                if (c is StreamChunk.TextDelta) { sb.append(c.text); _aiReply.value = sb.toString() }
                else if (c is StreamChunk.Error) { sb.append(c.message); _aiReply.value = sb.toString() }
            }
            val reply = sb.toString().trim()
            if (reply.isNotEmpty()) {
                voiceHistory.add(ChatMessageEntity(UUID.randomUUID().toString(), "live_voice", "model", reply, timestamp = System.currentTimeMillis()))
                val clean = reply.replace(Regex("```[\\s\\S]*?```"), "Code block omitted.").replace("**", "").replace("#", "")
                if (ttsReady && tts != null) { _statusText.value = "Speaking (speak >60dB to interrupt)..."; _phase.value = VoicePhase.SPEAKING; tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "v_${System.currentTimeMillis()}") }
                else _phase.value = VoicePhase.IDLE
            } else _phase.value = VoicePhase.IDLE
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBargeInMonitor() {
        bargeInJob?.cancel()
        bargeInJob = viewModelScope.launch(Dispatchers.IO) {
            val sr = 16000; val minBuf = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuf <= 0) return@launch
            val rec = runCatching { AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2) }.getOrNull() ?: return@launch
            if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return@launch }
            val buf = ShortArray(minBuf); var hot = 0
            try {
                rec.startRecording(); delay(350)
                while (isActive && _phase.value == VoicePhase.SPEAKING) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) {
                        var sum = 0.0; for (i in 0 until n) sum += buf[i].toDouble() * buf[i].toDouble()
                        val db = 20.0 * log10(sqrt(sum / n).coerceAtLeast(1.0)); _currentDb.value = db
                        if (db > 60.0) { if (++hot >= 2) { withContext(Dispatchers.Main) { _statusText.value = "Interrupted!"; tts?.stop(); startListeningInternal() }; break } } else hot = 0
                    }
                    delay(40)
                }
            } catch (_: Throwable) {} finally { runCatching { rec.stop(); rec.release() } }
        }
    }
    private fun stopBargeInMonitor() { bargeInJob?.cancel(); bargeInJob = null }
    override fun onCleared() { stopBargeInMonitor(); tts?.stop(); tts?.shutdown(); runCatching { speechRecognizer?.destroy() }; super.onCleared() }
}
