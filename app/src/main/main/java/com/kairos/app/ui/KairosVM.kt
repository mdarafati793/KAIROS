package com.kairos.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.ChatMsg
import com.kairos.app.Mode
import com.kairos.app.ai.Assistant
import com.kairos.app.data.AppDb
import com.kairos.app.data.Prefs
import com.kairos.app.voice.SentenceSpeaker
import com.kairos.app.voice.Voice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Attach(val name: String, val kind: String, val b64: String?, val text: String?, val preview: Bitmap?)

class KairosVM(app: Application) : AndroidViewModel(app) {
    val voice = Voice(app)
    private val speaker = SentenceSpeaker(voice)
    val chat = mutableStateListOf<ChatMsg>()
    val code = mutableStateListOf<ChatMsg>()
    val attachments = mutableStateListOf<Attach>()
    var busy by mutableStateOf(false)
    var thinking by mutableStateOf(false)
    var heard by mutableStateOf("")

    init {
        viewModelScope.launch {
            AppDb.get(app).dao().lastMemos(20).reversed().forEach { chat.add(ChatMsg(user = it.role == "user", text = it.text)) }
        }
    }

    fun send(text: String, mode: Mode, speak: Boolean = Prefs.speak) {
        if ((text.isBlank() && attachments.isEmpty()) || busy) return
        val list = if (mode == Mode.CODE) code else chat
        val atts = attachments.toList(); attachments.clear()
        var prompt = text
        atts.forEach { a ->
            if (a.text != null) prompt += "\n\n[File: ${a.name}]\n${a.text}"
            else if (a.kind == "video") prompt += "\n\n[Video attached: ${a.name} — its first frame is provided as an image]"
        }
        list.add(ChatMsg(user = true, text = text, attach = atts.map { it.name }))
        list.add(ChatMsg(user = false, text = ""))
        val idx = list.lastIndex
        busy = true; thinking = true
        viewModelScope.launch {
            val sb = StringBuilder(); var first = true
            Assistant.run(getApplication(), prompt, mode, atts.mapNotNull { it.b64 }).collect { chunk ->
                if (first) { thinking = false; first = false }
                sb.append(chunk)
                list[idx] = list[idx].copy(text = sb.toString())
                if (speak) speaker.feed(chunk)
            }
            if (speak) speaker.flush()
            busy = false; thinking = false
        }
    }

    fun toggleVoice() {
        when (voice.state.value) {
            com.kairos.app.OrbState.Speaking -> voice.stopSpeaking()
            com.kairos.app.OrbState.Listening -> voice.stopListening()
            else -> {
                heard = ""
                voice.listen(onPartial = { heard = it }, onFinal = { heard = it; send(it, Mode.GENERAL, speak = true) }, fail = { heard = it })
            }
        }
    }

    fun dictate(onText: (String) -> Unit) {
        if (voice.state.value == com.kairos.app.OrbState.Listening) { voice.stopListening(); return }
        voice.listen(onPartial = onText, onFinal = onText)
    }

    fun clearChat() { viewModelScope.launch { AppDb.get(getApplication()).dao().clearMemos(); chat.clear(); code.clear() } }

    fun addUri(uri: Uri, kind: String) {
        viewModelScope.launch {
            val a = withContext(Dispatchers.IO) { readAttach(uri, kind) }
            if (a != null) attachments.add(a)
        }
    }

    private fun readAttach(uri: Uri, kind: String): Attach? = try {
        val cr = getApplication<Application>().contentResolver
        var name = "file"
        cr.query(uri, null, null, null, null)?.use { c -> if (c.moveToFirst()) { val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0) name = c.getString(i) } }
        when (kind) {
            "image" -> {
                val bm = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 }) }
                bm?.let { Attach(name, kind, Assistant.toB64(it), null, Bitmap.createScaledBitmap(it, 160, (160f * it.height / it.width).toInt().coerceAtLeast(1), true)) }
            }
            "video" -> {
                val r = MediaMetadataRetriever()
                r.setDataSource(getApplication<Application>(), uri)
                val bm = r.getFrameAtTime(1_000_000)
                r.release()
                bm?.let { Attach(name, kind, Assistant.toB64(it), null, Bitmap.createScaledBitmap(it, 160, (160f * it.height / it.width).toInt().coerceAtLeast(1), true)) }
                    ?: Attach(name, kind, null, null, null)
            }
            else -> Attach(name, "file", null, cr.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(20000) } ?: "", null)
        }
    } catch (e: Exception) { null }

    override fun onCleared() { voice.release() }
}
