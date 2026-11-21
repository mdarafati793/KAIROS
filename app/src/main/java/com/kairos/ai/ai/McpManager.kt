package com.kairos.ai.ai

import com.kairos.ai.data.local.*
import com.kairos.ai.data.model.*
import com.kairos.ai.data.prefs.KairosPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.*
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

class McpManager(private val connectorDao: ConnectorDao, private val prefs: KairosPreferences) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val httpClient = HttpClient(OkHttp) {
        install(SSE)
        install(HttpTimeout) { connectTimeoutMillis = 15_000; requestTimeoutMillis = 60_000; socketTimeoutMillis = 60_000 }
    }
    private val activeClients = ConcurrentHashMap<String, Client>()
    private val toolCache = ConcurrentHashMap<String, List<McpToolDescriptor>>()
    private val toolOwnerMap = ConcurrentHashMap<String, String>()
    val connectorsFlow: Flow<List<McpConnectorEntity>> = connectorDao.observeConnectors()

    init {
        scope.launch {
            McpAppType.entries.forEach { app ->
                if (connectorDao.getConnector(app.id) == null) {
                    connectorDao.upsertConnector(McpConnectorEntity(app.id, app.displayName, false, false, 0, "[]", null, System.currentTimeMillis()))
                }
            }
            val url = prefs.getComposioUrl(); val key = prefs.getComposioApiKey()
            connectorDao.getAllConnectors().forEach { e ->
                if (e.isConnected && e.toolsJson.isNotBlank()) {
                    runCatching { json.decodeFromString<List<McpToolDescriptor>>(e.toolsJson) }.getOrNull()?.let { list ->
                        toolCache[e.id] = list; list.forEach { toolOwnerMap[it.name] = e.id }
                    }
                }
                if (e.enabled && url.isNotBlank()) McpAppType.entries.firstOrNull { it.id == e.id }?.let { connectApp(it, url, key) }
            }
        }
    }

    suspend fun toggleConnector(app: McpAppType, enabled: Boolean, url: String, apiKey: String): Result<Int> = mutex.withLock {
        prefs.setComposioConfig(url, apiKey)
        if (!enabled) { disconnectInternal(app); return Result.success(0) }
        connectInternal(app, url, apiKey)
    }
    suspend fun connectApp(app: McpAppType, url: String, apiKey: String): Result<Int> = mutex.withLock { connectInternal(app, url, apiKey) }

    private suspend fun connectInternal(app: McpAppType, rawUrl: String, apiKey: String): Result<Int> {
        val url = rawUrl.trim()
        if (url.isBlank()) {
            val err = "Configure your Composio MCP Server URL first."
            connectorDao.upsertConnector(McpConnectorEntity(app.id, app.displayName, false, false, 0, "[]", err, System.currentTimeMillis()))
            prefs.setAppConnected(app.id, false); return Result.failure(IllegalArgumentException(err))
        }
        return try {
            runCatching { activeClients.remove(app.id)?.close() }
            val endpoint = if (url.contains("?")) "$url&app=${app.id}" else url
            val client = Client(Implementation("kairos-android-v2", "2.0.0"))
            val transport = StreamableHttpClientTransport(httpClient, endpoint) {
                if (apiKey.isNotBlank()) { headers.append("Authorization", "Bearer $apiKey"); headers.append("x-api-key", apiKey) }
                headers.append("X-Kairos-App", app.id)
            }
            client.connect(transport)
            val listed = client.listTools()
            val filtered = listed.tools.filter { it.name.contains(app.keywordHint, true) || (it.description?.contains(app.keywordHint, true) == true) }.ifEmpty { listed.tools }
            val descriptors = filtered.map { t ->
                val schemaJson = buildJsonObject {
                    put("type", "object"); put("properties", t.inputSchema.properties?.let { sanitizeSchema(it) } ?: buildJsonObject { })
                    t.inputSchema.required?.takeIf { it.isNotEmpty() }?.let { req -> put("required", buildJsonArray { req.forEach { add(JsonPrimitive(it)) } }) }
                }.toString()
                McpToolDescriptor(app.id, t.name, t.description ?: "MCP tool for ${app.displayName}", schemaJson)
            }
            activeClients[app.id] = client; toolCache[app.id] = descriptors; descriptors.forEach { toolOwnerMap[it.name] = app.id }
            connectorDao.upsertConnector(McpConnectorEntity(app.id, app.displayName, true, true, descriptors.size, json.encodeToString(descriptors), null, System.currentTimeMillis()))
            prefs.setAppConnected(app.id, true); Result.success(descriptors.size)
        } catch (t: Throwable) {
            connectorDao.upsertConnector(McpConnectorEntity(app.id, app.displayName, false, false, 0, "[]", t.message ?: "MCP connection failed", System.currentTimeMillis()))
            prefs.setAppConnected(app.id, false); Result.failure(t)
        }
    }

    private suspend fun disconnectInternal(app: McpAppType) {
        runCatching { activeClients.remove(app.id)?.close() }
        toolCache.remove(app.id)?.forEach { toolOwnerMap.remove(it.name) }
        connectorDao.upsertConnector(McpConnectorEntity(app.id, app.displayName, false, false, 0, "[]", null, System.currentTimeMillis()))
        prefs.setAppConnected(app.id, false)
    }

    fun getActiveTools(): List<McpToolDescriptor> = toolCache.values.flatten().distinctBy { it.name }

    suspend fun executeTool(toolName: String, argsJson: JsonObject): String {
        val appId = toolOwnerMap[toolName] ?: return "Error: No active MCP connector owns '$toolName'."
        var client = activeClients[appId]
        if (client == null) {
            val url = prefs.getComposioUrl(); val key = prefs.getComposioApiKey()
            McpAppType.entries.firstOrNull { it.id == appId }?.takeIf { url.isNotBlank() }?.let { connectApp(it, url, key); client = activeClients[appId] }
        }
        if (client == null) return "Error: MCP client for '$appId' is disconnected."
        return try {
            val res = client.callTool(name = toolName, arguments = argsJson.mapValues { (_, v) -> toAny(v) })
            res?.content?.filterIsInstance<TextContent>()?.joinToString("\n") { it.text ?: "" }?.trim()?.ifEmpty { "Tool '$toolName' completed." } ?: "Tool '$toolName' completed."
        } catch (t: Throwable) { "Tool error for '$toolName': ${t.message ?: t.javaClass.simpleName}" }
    }

    private fun toAny(el: JsonElement): Any? = when (el) {
        is JsonNull -> null; is JsonPrimitive -> if (el.isString) el.content else el.booleanOrNull ?: el.longOrNull ?: el.doubleOrNull ?: el.content
        is JsonObject -> el.mapValues { (_, v) -> toAny(v) }; is JsonArray -> el.map { toAny(it) }
    }
    private fun sanitizeSchema(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> buildJsonObject {
            el.forEach { (k, v) -> if (k !in setOf("\$schema","additionalProperties","default","\$defs","definitions") && !(k == "format" && v is JsonPrimitive && v.isString && v.content !in setOf("enum","date-time"))) put(k, sanitizeSchema(v)) }
        }
        is JsonArray -> buildJsonArray { el.forEach { add(sanitizeSchema(it)) } }; else -> el
    }
}
