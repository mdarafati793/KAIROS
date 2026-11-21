package com.kairos.ai
import android.app.Application
import com.kairos.ai.ai.*; import com.kairos.ai.data.local.KairosDatabase; import com.kairos.ai.data.prefs.KairosPreferences

class KairosApplication : Application() {
    lateinit var database: KairosDatabase private set; lateinit var preferences: KairosPreferences private set
    lateinit var capabilityManager: CapabilityManager private set; lateinit var mcpManager: McpManager private set
    lateinit var aiClient: GeminiOpenRouterClient private set; lateinit var memoryExtractor: MemoryExtractor private set
    override fun onCreate() {
        super.onCreate()
        database = KairosDatabase.getInstance(this); preferences = KairosPreferences(this)
        capabilityManager = CapabilityManager(preferences); mcpManager = McpManager(database.connectorDao(), preferences)
        aiClient = GeminiOpenRouterClient(mcpManager); memoryExtractor = MemoryExtractor(database.memoryDao(), aiClient)
    }
}
