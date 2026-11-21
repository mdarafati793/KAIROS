package com.kairos.ai.ai

import com.kairos.ai.accessibility.AccessibilityBridge
import com.kairos.ai.data.local.ChatMessageEntity
import com.kairos.ai.data.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

class GeminiOpenRouterClient(private val mcpManager: McpManager) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(OkHttp) { install(HttpTimeout) { connectTimeoutMillis = 20_000; requestTimeoutMillis = 180_000; socketTimeoutMillis = 180_000 } }

    fun sendMessageWithFallback(sys: String, history: List<ChatMessageEntity>, caps: CapabilitiesState, accOn: Boolean, extraImgs: List<Pair<String, String>> = emptyList()): Flow<StreamChunk> = flow {
        val tools = mcpManager.getActiveTools(); var streamed = false; var fallback = false; var err: String? = null
        if (caps.geminiApiKey.isNotBlank()) {
            try {
                streamGemini(sys, history, caps, accOn, tools, extraImgs).collect { c ->
                    when (c) {
                        is StreamChunk.TextDelta -> { streamed = true; emit(c) }
                        is StreamChunk.Error -> { err = c.message; if (c.recoverableForFallback && caps.autoModelFallback && caps.openRouterApiKey.isNotBlank()) fallback = true else emit(c) }
                        else -> emit(c)
                    }
                }
            } catch (t: Throwable) {
                err = t.message ?: "Gemini failed"
                if (caps.autoModelFallback && caps.openRouterApiKey.isNotBlank()) fallback = true else emit(StreamChunk.Error(err!!))
            }
        } else if (caps.openRouterApiKey.isNotBlank()) fallback = true
        else { emit(StreamChunk.Error("Add a Gemini or OpenRouter API key in Settings.")); return@flow }

        if (fallback && !streamed) {
            emit(StreamChunk.StatusNotice("Fallback active (${err ?: "error"}) -> OpenRouter ${caps.openRouterFallbackModel}"))
            streamOpenRouter(sys, history, caps, accOn, tools, extraImgs).collect { emit(it) }
        }
    }

    private fun streamGemini(sys: String, history: List<ChatMessageEntity>, caps: CapabilitiesState, accOn: Boolean, mcp: List<McpToolDescriptor>, extra: List<Pair<String, String>>) = flow {
        val contents = buildGeminiContents(history, extra).toMutableList()
        val fns = fnDecls(accOn, mcp)
        val tools = buildJsonArray {
            if (caps.webSearchEnabled) add(buildJsonObject { put("google_search", buildJsonObject {}) })
            if (caps.codeExecutionEnabled) add(buildJsonObject { put("code_execution", buildJsonObject {}) })
            if (fns.isNotEmpty()) add(buildJsonObject { put("function_declarations", JsonArray(fns)) })
        }
        for (r in 0 until 5) {
            val body = buildJsonObject {
                put("systemInstruction", buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", sys) }) }) })
                put("contents", JsonArray(contents)); if (tools.isNotEmpty()) put("tools", tools)
                put("generationConfig", buildJsonObject { put("temperature", 0.7); put("maxOutputTokens", 8192) })
            }
            val url = "https://generativelanguage.googleapis.com/v1beta/models/${caps.primaryModel}:streamGenerateContent?alt=sse&key=${caps.geminiApiKey}"
            val calls = mutableListOf<Pair<String, JsonObject>>(); val partsOut = mutableListOf<JsonObject>(); val urls = linkedSetOf<String>()
            var blocked: String? = null; var fail: Pair<Int, String>? = null
            http.preparePost(url) { contentType(ContentType.Application.Json); setBody(body.toString()) }.execute { resp ->
                if (!resp.status.isSuccess()) { fail = resp.status.value to resp.bodyAsText(); return@execute }
                val ch = resp.bodyAsChannel()
                while (!ch.isClosedForRead) {
                    val line = ch.readUTF8Line() ?: break; if (!line.startsWith("data:")) continue
                    val raw = line.removePrefix("data:").trim(); if (raw.isEmpty() || raw == "[DONE]") continue
                    val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: continue
                    root["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull?.let { blocked = "Blocked: $it"; continue }
                    val cand = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject ?: continue
                    cand["finishReason"]?.jsonPrimitive?.contentOrNull?.takeIf { it in setOf("SAFETY","RECITATION","PROHIBITED_CONTENT","BLOCKLIST","SPII") }?.let { blocked = "Blocked ($it)" }
                    cand["groundingMetadata"]?.jsonObject?.get("groundingChunks")?.jsonArray?.forEach { gc -> gc.jsonObject["web"]?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { urls.add(it) } }
                    cand["content"]?.jsonObject?.get("parts")?.jsonArray?.forEach { pe ->
                        val p = pe.jsonObject; val th = p["thought"]?.jsonPrimitive?.contentOrNull == "true"
                        p["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() && !th }?.let { emit(StreamChunk.TextDelta(it)); partsOut.add(buildJsonObject { put("text", it) }) }
                        p["executableCode"]?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { emit(StreamChunk.TextDelta("\n```python\n$it\n```\n")) }
                        p["codeExecutionResult"]?.jsonObject?.get("output")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { emit(StreamChunk.TextDelta("\n**Output:**\n```\n$it\n```\n")) }
                        p["functionCall"]?.jsonObject?.let { fc -> val fn = fc["name"]?.jsonPrimitive?.contentOrNull.orEmpty(); val ag = fc["args"]?.jsonObject ?: buildJsonObject {}; if (fn.isNotEmpty()) { calls.add(fn to ag); partsOut.add(p) } }
                    }
                }
            }
            fail?.let { (c, b) -> emit(StreamChunk.Error("Gemini HTTP $c: ${b.take(220)}", c in setOf(401,403,404,429,500,502,503,504))); return@flow }
            blocked?.let { emit(StreamChunk.Error(it, true)); return@flow }
            if (urls.isNotEmpty()) emit(StreamChunk.GroundingSources(urls.toList()))
            if (calls.isEmpty()) { emit(StreamChunk.Completed); return@flow }
            contents.add(buildJsonObject { put("role", "model"); put("parts", JsonArray(partsOut)) })
            contents.add(buildJsonObject { put("role", "user"); put("parts", JsonArray(calls.map { (n, a) -> emit(StreamChunk.StatusNotice("Running $n...")); buildJsonObject { put("functionResponse", buildJsonObject { put("name", n); put("response", buildJsonObject { put("result", execTool(n, a)) }) }) } })) })
        }
        emit(StreamChunk.Completed)
    }

    private fun streamOpenRouter(sys: String, history: List<ChatMessageEntity>, caps: CapabilitiesState, accOn: Boolean, mcp: List<McpToolDescriptor>, extra: List<Pair<String, String>>) = flow {
        val msgs = buildOrMsgs(sys, history, extra).toMutableList(); val tools = buildJsonArray { fnDecls(accOn, mcp).forEach { add(buildJsonObject { put("type", "function"); put("function", it) }) } }
        for (r in 0 until 5) {
            val body = buildJsonObject { put("model", caps.openRouterFallbackModel); put("messages", JsonArray(msgs)); put("stream", true); if (tools.isNotEmpty()) put("tools", tools) }
            var err: String? = null; val calls = linkedMapOf<Int, Triple<String, String, StringBuilder>>(); val sb = StringBuilder()
            http.preparePost("https://openrouter.ai/api/v1/chat/completions") { header("Authorization", "Bearer ${caps.openRouterApiKey}"); header("X-Title", "KAIROS"); contentType(ContentType.Application.Json); setBody(body.toString()) }.execute { resp ->
                if (!resp.status.isSuccess()) { err = "OpenRouter HTTP ${resp.status.value}: ${resp.bodyAsText().take(220)}"; return@execute }
                val ch = resp.bodyAsChannel()
                while (!ch.isClosedForRead) {
                    val line = ch.readUTF8Line() ?: break; if (!line.startsWith("data:")) continue
                    val raw = line.removePrefix("data:").trim(); if (raw == "[DONE]") break
                    val d = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()?.get("choices")?.jsonArray?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject ?: continue
                    d["content"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { sb.append(it); emit(StreamChunk.TextDelta(it)) }
                    d["tool_calls"]?.jsonArray?.forEach { el ->
                        val tc = el.jsonObject; val idx = tc["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                        val cur = calls.getOrPut(idx) { Triple(tc["id"]?.jsonPrimitive?.contentOrNull ?: "c_$idx", "", StringBuilder()) }
                        tc["function"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() && cur.second.isEmpty() }?.let { calls[idx] = Triple(cur.first, it, cur.third) }
                        tc["function"]?.jsonObject?.get("arguments")?.jsonPrimitive?.contentOrNull?.let { calls[idx]?.third?.append(it) }
                    }
                }
            }
            err?.let { emit(StreamChunk.Error(it)); return@flow }
            if (calls.isEmpty()) { emit(StreamChunk.Completed); return@flow }
            msgs.add(buildJsonObject { put("role", "assistant"); if (sb.isNotEmpty()) put("content", sb.toString()); put("tool_calls", buildJsonArray { calls.values.forEach { (id, n, b) -> add(buildJsonObject { put("id", id); put("type", "function"); put("function", buildJsonObject { put("name", n); put("arguments", b.toString().ifBlank { "{}" }) }) }) } }) })
            for ((id, n, b) in calls.values) { emit(StreamChunk.StatusNotice("Running $n...")); val ag = runCatching { json.parseToJsonElement(b.toString().ifBlank { "{}" }).jsonObject }.getOrElse { buildJsonObject {} }; msgs.add(buildJsonObject { put("role", "tool"); put("tool_call_id", id); put("content", execTool(n, ag)) }) }
        }
        emit(StreamChunk.Completed)
    }

    private suspend fun execTool(name: String, args: JsonObject) = when (name) {
        "get_screen_context" -> AccessibilityBridge.captureScreenContext()
        "click_ui_element" -> args["text"]?.jsonPrimitive?.contentOrNull.orEmpty().let { if (AccessibilityBridge.clickElementByText(it)) "Clicked '$it'" else "No clickable '$it'" }
        "navigate_back" -> if (AccessibilityBridge.performBack()) "Back OK" else "Accessibility off"
        "navigate_home" -> if (AccessibilityBridge.performHome()) "Home OK" else "Accessibility off"
        else -> mcpManager.executeTool(name, args)
    }

    suspend fun generateConciseTitle(u: String, a: String, caps: CapabilitiesState) =
        callLightweightModel("Summarize in 3-5 words as a title ONLY.\nUser: ${u.take(300)}\nAI: ${a.take(300)}", caps)

    suspend fun callLightweightModel(prompt: String, caps: CapabilitiesState): String? {
        if (caps.geminiApiKey.isNotBlank()) {
            val ep = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=${caps.geminiApiKey}"
            val body = buildJsonObject { put("contents", buildJsonArray { add(buildJsonObject { put("role", "user"); put("parts", buildJsonArray { add(buildJsonObject { put("text", prompt) }) }) }) }) }
            runCatching {
                val r = http.post(ep) { contentType(ContentType.Application.Json); setBody(body.toString()) }
                if (r.status.isSuccess()) json.parseToJsonElement(r.bodyAsText()).jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.trim() else null
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        if (caps.openRouterApiKey.isNotBlank()) {
            val body = buildJsonObject { put("model", caps.openRouterFallbackModel); put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", prompt) }) }) }
            return runCatching {
                val r = http.post("https://openrouter.ai/api/v1/chat/completions") { header("Authorization", "Bearer ${caps.openRouterApiKey}"); contentType(ContentType.Application.Json); setBody(body.toString()) }
                if (r.status.isSuccess()) json.parseToJsonElement(r.bodyAsText()).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull?.trim() else null
            }.getOrNull()
        }
        return null
    }

    private fun buildGeminiContents(h: List<ChatMessageEntity>, extra: List<Pair<String, String>>) = h.mapIndexed { i, m ->
        val atts = runCatching { json.decodeFromString<List<ChatAttachment>>(m.attachmentsJson) }.getOrDefault(emptyList())
        buildJsonObject {
            put("role", if (m.role == "user") "user" else "model")
            put("parts", buildJsonArray {
                if (m.content.isNotBlank()) add(buildJsonObject { put("text", m.content) })
                atts.filter { !it.base64Data.isNullOrBlank() }.forEach { a -> add(buildJsonObject { put("inlineData", buildJsonObject { put("mimeType", a.mimeType); put("data", a.base64Data!!) }) }) }
                if (i == h.lastIndex && m.role == "user") extra.forEach { (mt, b64) -> add(buildJsonObject { put("inlineData", buildJsonObject { put("mimeType", mt); put("data", b64) }) }) }
            })
        }
    }
    private fun buildOrMsgs(sys: String, h: List<ChatMessageEntity>, extra: List<Pair<String, String>>) = buildList {
        add(buildJsonObject { put("role", "system"); put("content", sys) })
        h.forEachIndexed { i, m ->
            val atts = runCatching { json.decodeFromString<List<ChatAttachment>>(m.attachmentsJson) }.getOrDefault(emptyList())
            val imgs = atts.filter { !it.base64Data.isNullOrBlank() && it.mimeType.startsWith("image/") }; val hasEx = i == h.lastIndex && m.role == "user" && extra.isNotEmpty()
            if (imgs.isEmpty() && !hasEx) add(buildJsonObject { put("role", if (m.role == "user") "user" else "assistant"); put("content", m.content) })
            else add(buildJsonObject {
                put("role", if (m.role == "user") "user" else "assistant")
                put("content", buildJsonArray {
                    add(buildJsonObject { put("type", "text"); put("text", m.content.ifBlank { "Analyze image." }) })
                    imgs.forEach { a -> add(buildJsonObject { put("type", "image_url"); put("image_url", buildJsonObject { put("url", "data:${a.mimeType};base64,${a.base64Data}") }) }) }
                    if (hasEx) extra.forEach { (mt, b64) -> add(buildJsonObject { put("type", "image_url"); put("image_url", buildJsonObject { put("url", "data:$mt;base64,$b64") }) }) }
                })
            })
        }
    }
    private fun fnDecls(accOn: Boolean, mcp: List<McpToolDescriptor>) = buildList {
        if (accOn) {
            add(buildJsonObject { put("name", "get_screen_context"); put("description", "Read UI text on screen."); put("parameters", buildJsonObject { put("type", "object"); put("properties", buildJsonObject {}) }) })
            add(buildJsonObject { put("name", "click_ui_element"); put("description", "Click UI element by text."); put("parameters", buildJsonObject { put("type", "object"); put("properties", buildJsonObject { put("text", buildJsonObject { put("type", "string") }) }); put("required", buildJsonArray { add(JsonPrimitive("text")) }) }) })
            add(buildJsonObject { put("name", "navigate_back"); put("description", "System Back."); put("parameters", buildJsonObject { put("type", "object"); put("properties", buildJsonObject {}) }) })
            add(buildJsonObject { put("name", "navigate_home"); put("description", "System Home."); put("parameters", buildJsonObject { put("type", "object"); put("properties", buildJsonObject {}) }) })
        }
        mcp.forEach { t ->
            val sc = runCatching { json.parseToJsonElement(t.inputSchemaJson).jsonObject }.getOrElse { buildJsonObject { put("type", "object"); put("properties", buildJsonObject {}) } }
            add(buildJsonObject { put("name", t.name); put("description", "[${t.appId.uppercase()}] ${t.description}"); put("parameters", sc) })
        }
    }
}
