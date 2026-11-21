package com.kairos.ai.ui.viewmodel

import android.app.Application; import android.net.Uri; import android.provider.OpenableColumns; import android.util.Base64
import androidx.lifecycle.*; import androidx.lifecycle.viewmodel.*
import com.kairos.ai.KairosApplication; import com.kairos.ai.accessibility.AccessibilityBridge
import com.kairos.ai.ai.*; import com.kairos.ai.data.local.*; import com.kairos.ai.data.model.*
import kotlinx.coroutines.*; import kotlinx.coroutines.flow.*; import kotlinx.serialization.encodeToString; import kotlinx.serialization.json.Json
import java.util.UUID

class ChatViewModel(application: Application, private val ssh: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as KairosApplication
    private val chatDao = app.database.chatDao(); private val memoryDao = app.database.memoryDao()
    private val prefs = app.preferences; private val ctxMgr = ContextWindowManager(); private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val K_SID = "active_session_id"; private const val K_DRAFT = "draft_input_text"
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KairosApplication, createSavedStateHandle()) }
        }
    }

    private val _activeSessionId = MutableStateFlow(ssh.get<String>(K_SID)); val activeSessionId = _activeSessionId.asStateFlow()
    private val _draftInput = MutableStateFlow(ssh.get<String>(K_DRAFT) ?: ""); val draftInput = _draftInput.asStateFlow()
    private val _pendingAttachments = MutableStateFlow<List<ChatAttachment>>(emptyList()); val pendingAttachments = _pendingAttachments.asStateFlow()
    private val _streamingText = MutableStateFlow(""); val streamingText = _streamingText.asStateFlow()
    private val _isStreaming = MutableStateFlow(false); val isStreaming = _isStreaming.asStateFlow()
    private val _statusNotice = MutableStateFlow<String?>(null); val statusNotice = _statusNotice.asStateFlow()
    private val _scrollToBottomEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 4); val scrollToBottomEvent = _scrollToBottomEvent.asSharedFlow()

    val sessions = chatDao.observeSessions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    @OptIn(ExperimentalCoroutinesApi::class)
    val messages = _activeSessionId.flatMapLatest { sid -> if (sid.isNullOrBlank()) flowOf(emptyList()) else chatDao.observeMessages(sid) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val memories = memoryDao.observeMemories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val connectors = app.mcpManager.connectorsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val capabilities = app.capabilityManager.state
    val customInstructions = prefs.customInstructionsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val composioUrl = prefs.composioUrlFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val composioApiKey = prefs.composioApiKeyFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val accessibilityLive = AccessibilityBridge.liveConnected
    private var genJob: Job? = null

    init { viewModelScope.launch { restoreSession() } }

    private suspend fun restoreSession() {
        val saved = ssh.get<String>(K_SID) ?: prefs.getActiveSessionId(); val all = chatDao.getAllSessions()
        val target = when { !saved.isNullOrBlank() && all.any { it.id == saved } -> saved; all.isNotEmpty() -> all.first().id; else -> newSessionInternal() }
        setSessionInternal(target)
        val draft = ssh.get<String>(K_DRAFT) ?: prefs.getDraftInput()
        if (_draftInput.value.isBlank() && draft.isNotBlank()) { _draftInput.value = draft; ssh[K_DRAFT] = draft }
        _scrollToBottomEvent.tryEmit(Unit)
    }

    fun onAppResumed() {
        AccessibilityBridge.refreshState(app)
        viewModelScope.launch {
            val saved = ssh.get<String>(K_SID) ?: prefs.getActiveSessionId()
            if (!saved.isNullOrBlank() && saved != _activeSessionId.value && chatDao.getSessionById(saved) != null) setSessionInternal(saved)
            _scrollToBottomEvent.tryEmit(Unit)
        }
    }

    private suspend fun setSessionInternal(id: String) { _activeSessionId.value = id; ssh[K_SID] = id; prefs.setActiveSessionId(id) }
    fun updateDraftInput(t: String) { _draftInput.value = t; ssh[K_DRAFT] = t; viewModelScope.launch(Dispatchers.IO) { prefs.setDraftInput(t) } }
    fun selectSession(id: String) { viewModelScope.launch { setSessionInternal(id); _scrollToBottomEvent.tryEmit(Unit) } }
    fun createNewSession() { viewModelScope.launch { setSessionInternal(newSessionInternal()); _scrollToBottomEvent.tryEmit(Unit) } }
    private suspend fun newSessionInternal(): String = UUID.randomUUID().toString().also {
        chatDao.upsertSession(ChatSessionEntity(it, "New Chat", System.currentTimeMillis(), capabilities.value.primaryModel))
    }
    fun renameSession(id: String, title: String) { if (title.isNotBlank()) viewModelScope.launch { chatDao.updateSessionTitle(id, title.trim(), System.currentTimeMillis()) } }
    fun deleteSession(id: String) {
        viewModelScope.launch { chatDao.deleteSession(id); if (_activeSessionId.value == id) setSessionInternal(chatDao.getAllSessions().firstOrNull()?.id ?: newSessionInternal()) }
    }

    fun addAttachmentFromUri(uri: Uri, fallbackMime: String? = null) = viewModelScope.launch(Dispatchers.IO) {
        val r = app.contentResolver; val mime = r.getType(uri) ?: fallbackMime ?: "application/octet-stream"
        var name = "attachment"; var size = 0L
        r.query(uri, null, null, null, null)?.use { c ->
            val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); val si = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) { if (ni >= 0) name = c.getString(ni) ?: name; if (si >= 0) size = c.getLong(si) }
        }
        val bytes = r.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
        if (bytes.size > 8 * 1024 * 1024) { _statusNotice.value = "Attachment '$name' > 8MB limit."; return@launch }
        _pendingAttachments.value += ChatAttachment(UUID.randomUUID().toString(), uri.toString(), mime, name, if (size > 0) size else bytes.size.toLong(), Base64.encodeToString(bytes, Base64.NO_WRAP))
    }
    fun removePendingAttachment(id: String) { _pendingAttachments.value = _pendingAttachments.value.filterNot { it.id == id } }
    fun clearStatusNotice() { _statusNotice.value = null }

    fun stopGeneration() {
        genJob?.cancel(); genJob = null
        val p = _streamingText.value; val sid = _activeSessionId.value
        if (_isStreaming.value && p.isNotBlank() && !sid.isNullOrBlank()) viewModelScope.launch {
            chatDao.insertMessage(ChatMessageEntity(UUID.randomUUID().toString(), sid, "model", p, timestamp = System.currentTimeMillis()))
        }
        _isStreaming.value = false; _streamingText.value = ""
    }

    fun sendMessage() {
        val text = _draftInput.value.trim(); val atts = _pendingAttachments.value
        if ((text.isEmpty() && atts.isEmpty()) || _isStreaming.value) return
        genJob = viewModelScope.launch {
            val sid = _activeSessionId.value ?: newSessionInternal().also { setSessionInternal(it) }
            val caps = capabilities.value; val now = System.currentTimeMillis()
            chatDao.insertMessage(ChatMessageEntity(UUID.randomUUID().toString(), sid, "user", text, json.encodeToString(atts), now))
            chatDao.touchSession(sid, now, caps.primaryModel)
            updateDraftInput(""); _pendingAttachments.value = emptyList(); _scrollToBottomEvent.tryEmit(Unit)
            _isStreaming.value = true; _streamingText.value = ""; _statusNotice.value = null

            val accReady = AccessibilityBridge.isConnected(app)
            val sysPrompt = PromptBuilder.buildMasterSystemPrompt(prefs.getCustomInstructions(), memoryDao.getAllMemories(), accReady, app.mcpManager.getActiveTools(), caps)
            val trimmed = ctxMgr.trimMessages(sysPrompt, chatDao.getMessagesForSession(sid))
            val sb = StringBuilder(); val urls = linkedSetOf<String>()
            app.aiClient.sendMessageWithFallback(sysPrompt, trimmed, caps, accReady).collect { c ->
                when (c) {
                    is StreamChunk.TextDelta -> { sb.append(c.text); _streamingText.value = sb.toString() }
                    is StreamChunk.StatusNotice -> _statusNotice.value = c.message
                    is StreamChunk.GroundingSources -> urls.addAll(c.urls)
                    is StreamChunk.Error -> { _statusNotice.value = c.message; if (sb.isEmpty()) { sb.append("⚠️ ${c.message}"); _streamingText.value = sb.toString() } }
                    StreamChunk.Completed -> Unit
                }
            }
            if (urls.isNotEmpty()) { sb.append("\n\n**Sources:**\n"); urls.take(5).forEach { sb.append("- $it\n") } }
            val finalAi = sb.toString().trim()
            if (finalAi.isNotEmpty()) {
                chatDao.insertMessage(ChatMessageEntity(UUID.randomUUID().toString(), sid, "model", finalAi, timestamp = System.currentTimeMillis()))
                chatDao.touchSession(sid, System.currentTimeMillis(), caps.primaryModel)
                val sess = chatDao.getSessionById(sid); val cnt = chatDao.countMessages(sid)
                if (sess != null && (sess.title == "New Chat" || cnt <= 2)) launch(Dispatchers.IO) {
                    app.aiClient.generateConciseTitle(text, finalAi, caps)?.takeIf { it.isNotBlank() }?.let { chatDao.updateSessionTitle(sid, it.take(48), System.currentTimeMillis()) }
                }
                launch(Dispatchers.IO) { app.memoryExtractor.extractAndStore(text, finalAi, caps) }
            }
            _isStreaming.value = false; _streamingText.value = ""
        }
    }

    fun setCustomInstructions(t: String) { viewModelScope.launch { prefs.setCustomInstructions(t) } }
    fun saveMemory(id: String?, fact: String, cat: String) {
        if (fact.isBlank()) return; val now = System.currentTimeMillis()
        viewModelScope.launch { memoryDao.upsertMemory(MemoryEntity(id ?: UUID.randomUUID().toString(), fact.trim(), cat.ifBlank { "Context" }, now, now)) }
    }
    fun deleteMemory(id: String) { viewModelScope.launch { memoryDao.deleteMemory(id) } }
    fun saveComposioConfig(u: String, k: String) { viewModelScope.launch { prefs.setComposioConfig(u, k) } }
    fun toggleMcpApp(appType: McpAppType, on: Boolean, u: String, k: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = app.mcpManager.toggleConnector(appType, on, u, k)
            withContext(Dispatchers.Main) {
                res.onSuccess { n -> _statusNotice.value = if (on) "${appType.displayName} connected ($n tools)" else "${appType.displayName} disconnected" }
                    .onFailure { e -> _statusNotice.value = "${appType.displayName}: ${e.message}" }
            }
        }
    }
}
