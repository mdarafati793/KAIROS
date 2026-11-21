package com.kairos.ai.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.kairos.ai.data.model.CapabilitiesState
import kotlinx.coroutines.flow.*

private val Context.kairosDataStore by preferencesDataStore(name = "kairos_v2_prefs")

class KairosPreferences(private val context: Context) {
    private object K {
        val SID = stringPreferencesKey("active_session_id"); val DRAFT = stringPreferencesKey("draft_input")
        val GEMINI_KEY = stringPreferencesKey("gemini_api_key"); val OR_KEY = stringPreferencesKey("openrouter_api_key")
        val OR_MODEL = stringPreferencesKey("openrouter_model"); val MODEL = stringPreferencesKey("primary_model")
        val INSTR = stringPreferencesKey("custom_instructions"); val WEB = booleanPreferencesKey("web_search_enabled")
        val CODE = booleanPreferencesKey("code_execution_enabled"); val FALLBACK = booleanPreferencesKey("auto_model_fallback")
        val MEMORY = booleanPreferencesKey("auto_memory_enabled"); val MCP_URL = stringPreferencesKey("composio_mcp_url")
        val MCP_KEY = stringPreferencesKey("composio_api_key"); val APPS = stringSetPreferencesKey("connected_apps")
    }
    val customInstructionsFlow: Flow<String> = context.kairosDataStore.data.map { it[K.INSTR] ?: "" }
    val composioUrlFlow: Flow<String> = context.kairosDataStore.data.map { it[K.MCP_URL] ?: "" }
    val composioApiKeyFlow: Flow<String> = context.kairosDataStore.data.map { it[K.MCP_KEY] ?: "" }
    val capabilitiesFlow: Flow<CapabilitiesState> = context.kairosDataStore.data.map { p ->
        CapabilitiesState(
            p[K.WEB] ?: true, p[K.CODE] ?: true, p[K.FALLBACK] ?: true, p[K.MEMORY] ?: true,
            p[K.MODEL] ?: "gemini-3.6-flash", p[K.GEMINI_KEY] ?: "", p[K.OR_KEY] ?: "", p[K.OR_MODEL] ?: "google/gemini-2.5-flash"
        )
    }
    suspend fun getActiveSessionId(): String? = context.kairosDataStore.data.map { it[K.SID] }.first()
    suspend fun getDraftInput(): String = context.kairosDataStore.data.map { it[K.DRAFT] ?: "" }.first()
    suspend fun getCustomInstructions(): String = customInstructionsFlow.first()
    suspend fun getComposioUrl(): String = composioUrlFlow.first()
    suspend fun getComposioApiKey(): String = composioApiKeyFlow.first()
    suspend fun getCapabilities(): CapabilitiesState = capabilitiesFlow.first()
    suspend fun setActiveSessionId(id: String) { context.kairosDataStore.edit { it[K.SID] = id } }
    suspend fun setDraftInput(v: String) { context.kairosDataStore.edit { it[K.DRAFT] = v } }
    suspend fun setGeminiApiKey(v: String) { context.kairosDataStore.edit { it[K.GEMINI_KEY] = v.trim() } }
    suspend fun setOpenRouterConfig(k: String, m: String) { context.kairosDataStore.edit { it[K.OR_KEY] = k.trim(); if (m.isNotBlank()) it[K.OR_MODEL] = m.trim() } }
    suspend fun setPrimaryModel(v: String) { context.kairosDataStore.edit { it[K.MODEL] = v.trim() } }
    suspend fun setCustomInstructions(v: String) { context.kairosDataStore.edit { it[K.INSTR] = v } }
    suspend fun setWebSearchEnabled(v: Boolean) { context.kairosDataStore.edit { it[K.WEB] = v } }
    suspend fun setCodeExecutionEnabled(v: Boolean) { context.kairosDataStore.edit { it[K.CODE] = v } }
    suspend fun setAutoModelFallback(v: Boolean) { context.kairosDataStore.edit { it[K.FALLBACK] = v } }
    suspend fun setAutoMemoryEnabled(v: Boolean) { context.kairosDataStore.edit { it[K.MEMORY] = v } }
    suspend fun setComposioConfig(u: String, k: String) { context.kairosDataStore.edit { it[K.MCP_URL] = u.trim(); it[K.MCP_KEY] = k.trim() } }
    suspend fun setAppConnected(id: String, on: Boolean) {
        context.kairosDataStore.edit { p -> val s = p[K.APPS]?.toMutableSet() ?: mutableSetOf(); if (on) s.add(id) else s.remove(id); p[K.APPS] = s }
    }
}
