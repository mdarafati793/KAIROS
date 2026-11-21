package com.kairos.app.ai

import com.kairos.app.Mode
import com.kairos.app.data.Prefs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Tier-2 cloud brain: streaming Gemini (v1beta SSE) with automatic model fallback, then OpenRouter free models. */
object Brain {
    private const val G = "https://generativelanguage.googleapis.com/v1beta/models/"
    private val GEMINI_FALLBACKS = listOf("gemini-2.5-flash", "gemini-2.0-flash")
    private val OR_MODELS = listOf("meta-llama/llama-3.3-70b-instruct:free", "google/gemma-3-27b-it:free", "deepseek/deepseek-chat-v3-0324:free")

    fun activeModel() = Prefs.customModel.ifBlank { Prefs.model }

    fun system(mode: Mode): String {
        val base = """You are KAIROS, a Jarvis-inspired personal AI assistant and loyal, sharp-witted MALE best friend.
Speak naturally like a smart close friend, never robotic. Understand and reply in Bangla (বাংলা), English and Banglish (mixed) — mirror the language the user used.
Keep replies short and conversational unless the user asks for depth, because replies are spoken aloud. Never use long lists when talking.
${Prefs.userContext()}"""
        return when (mode) {
            Mode.CODE -> base + "\nCODING MODE: expert in Kotlin, Java, Python, JS, C++, HTML/CSS and Android. Always put code in fenced blocks with a language tag, explain briefly, debug step by step."
            Mode.CREATOR -> base + """
CREATOR MODE: you are an expert in Facebook Reels, YouTube Shorts/long-form, TikTok and Instagram Reels algorithms, 3-second hooks, retention and viral trends, and a CapCut editing coach.
When given a video/screen/image of a funny video or skit, analyse the humor and action, then give: (1) 5 catchy high-CTR titles with opening hooks (Bangla, English and Banglish mix), (2) platform-specific hashtags for Facebook, YouTube, TikTok and Instagram, (3) concrete CapCut tips: cut points, speed ramps, sound effects, caption styles, transitions."""
            else -> base
        }
    }

    fun stream(hist: List<Pair<String, String>>, prompt: String, images: List<String>, mode: Mode): Flow<String> = flow {
        var emitted = false
        val errs = mutableListOf<String>()
        if (Prefs.geminiKey.isNotBlank()) {
            for (m in (listOf(activeModel()) + GEMINI_FALLBACKS).distinct()) {
                try {
                    gemini(m, hist, prompt, images, mode) { emitted = true; emit(it) }
                    return@flow
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { if (emitted) throw e; errs += "$m: ${e.message}" }
            }
        }
        if (Prefs.orKey.isNotBlank()) {
            for (m in OR_MODELS) {
                try {
                    openRouter(m, hist, prompt, mode) { emitted = true; emit(it) }
                    return@flow
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { if (emitted) throw e; errs += "$m: ${e.message}" }
            }
        }
        throw IOException(if (errs.isEmpty()) "No API key yet — open Settings and add your Gemini key." else "All providers failed.\n" + errs.joinToString("\n"))
    }

    suspend fun test(): String = withContext(Dispatchers.IO) {
        if (Prefs.geminiKey.isBlank()) return@withContext "Add an API key first."
        val t = System.currentTimeMillis()
        try {
            val sb = StringBuilder()
            gemini(activeModel(), emptyList(), "Reply with the single word: ok", emptyList(), Mode.GENERAL) { sb.append(it) }
            "✅ Connected — ${System.currentTimeMillis() - t} ms (${activeModel()})"
        } catch (e: Exception) { "❌ ${e.message}" }
    }

    private fun open(url: String, body: String, headers: Map<String, String>): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 60000; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.outputStream.use { it.write(body.toByteArray()) }
        if (c.responseCode !in 200..299) {
            val e = c.errorStream?.bufferedReader()?.readText()?.take(200)
            throw IOException("HTTP ${c.responseCode} $e")
        }
        return c
    }

    private suspend fun gemini(model: String, hist: List<Pair<String, String>>, prompt: String, images: List<String>, mode: Mode, out: suspend (String) -> Unit) {
        val contents = JSONArray()
        hist.forEach { (r, t) ->
            contents.put(JSONObject().put("role", if (r == "user") "user" else "model")
                .put("parts", JSONArray().put(JSONObject().put("text", t))))
        }
        val parts = JSONArray().put(JSONObject().put("text", prompt))
        images.forEach { parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", it))) }
        contents.put(JSONObject().put("role", "user").put("parts", parts))
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system(mode)))))
            .put("contents", contents).toString()
        val conn = open("$G$model:streamGenerateContent?alt=sse&key=${Prefs.geminiKey}", body, emptyMap())
        conn.inputStream.bufferedReader().use { r ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = r.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val j = JSONObject(line.removePrefix("data:").trim())
                val ps = j.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: continue
                for (i in 0 until ps.length()) {
                    val t = ps.getJSONObject(i).optString("text")
                    if (t.isNotEmpty()) out(t)
                }
            }
        }
    }

    private suspend fun openRouter(model: String, hist: List<Pair<String, String>>, prompt: String, mode: Mode, out: suspend (String) -> Unit) {
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system(mode)))
        hist.forEach { (r, t) -> msgs.put(JSONObject().put("role", if (r == "user") "user" else "assistant").put("content", t)) }
        msgs.put(JSONObject().put("role", "user").put("content", prompt))
        val body = JSONObject().put("model", model).put("stream", true).put("messages", msgs).toString()
        val conn = open("https://openrouter.ai/api/v1/chat/completions", body, mapOf("Authorization" to "Bearer ${Prefs.orKey}"))
        conn.inputStream.bufferedReader().use { r ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = r.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val d = line.removePrefix("data:").trim()
                if (d == "[DONE]") break
                val t = JSONObject(d).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content") ?: ""
                if (t.isNotEmpty()) out(t)
            }
        }
    }
}
