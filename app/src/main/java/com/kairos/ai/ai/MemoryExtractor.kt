package com.kairos.ai.ai

import com.kairos.ai.data.local.*
import com.kairos.ai.data.model.CapabilitiesState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

class MemoryExtractor(private val memoryDao: MemoryDao, private val aiClient: GeminiOpenRouterClient) {
    private val json = Json { ignoreUnknownKeys = true }
    @Serializable private data class Payload(val memories: List<Fact> = emptyList())
    @Serializable private data class Fact(val fact: String, val category: String = "Context")

    suspend fun extractAndStore(userMessage: String, aiResponse: String, capabilities: CapabilitiesState) {
        if (!capabilities.autoMemoryEnabled || userMessage.isBlank() || aiResponse.isBlank()) return
        val existing = memoryDao.getAllMemories()
        val prompt = """Extract durable long-term facts, preferences, or goals about the user. Do not repeat existing facts.
Return ONLY JSON: {"memories":[{"fact":"...","category":"Preference|Profile|Work|Goal|Context"}]}
Existing: ${existing.take(30).joinToString("; ") { it.fact }}
User: ${userMessage.take(700)}
Assistant: ${aiResponse.take(700)}"""
        val raw = aiClient.callLightweightModel(prompt, capabilities) ?: return
        val cleaned = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val parsed = runCatching { json.decodeFromString<Payload>(cleaned) }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        parsed.memories.forEach { item ->
            val f = item.fact.trim()
            if (f.length > 5 && existing.none { it.fact.equals(f, true) }) {
                memoryDao.upsertMemory(MemoryEntity(UUID.randomUUID().toString(), f, item.category.ifBlank { "Context" }, now, now))
            }
        }
    }
}
