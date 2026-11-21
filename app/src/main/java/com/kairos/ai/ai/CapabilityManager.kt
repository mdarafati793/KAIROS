package com.kairos.ai.ai

import com.kairos.ai.data.model.CapabilitiesState
import com.kairos.ai.data.prefs.KairosPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class CapabilityManager(private val prefs: KairosPreferences) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val state: StateFlow<CapabilitiesState> = prefs.capabilitiesFlow.stateIn(scope, SharingStarted.Eagerly, CapabilitiesState())
    fun setWebSearch(v: Boolean) { scope.launch { prefs.setWebSearchEnabled(v) } }
    fun setCodeExecution(v: Boolean) { scope.launch { prefs.setCodeExecutionEnabled(v) } }
    fun setAutoModelFallback(v: Boolean) { scope.launch { prefs.setAutoModelFallback(v) } }
    fun setAutoMemory(v: Boolean) { scope.launch { prefs.setAutoMemoryEnabled(v) } }
    fun setPrimaryModel(m: String) { scope.launch { prefs.setPrimaryModel(m) } }
    fun setGeminiApiKey(k: String) { scope.launch { prefs.setGeminiApiKey(k) } }
    fun setOpenRouterConfig(k: String, m: String) { scope.launch { prefs.setOpenRouterConfig(k, m) } }
}
