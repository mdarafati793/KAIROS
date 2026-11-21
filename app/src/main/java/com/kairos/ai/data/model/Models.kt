package com.kairos.ai.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatAttachment(
    val id: String,
    val uriString: String,
    val mimeType: String,
    val fileName: String,
    val sizeBytes: Long,
    val base64Data: String? = null
)

data class CapabilitiesState(
    val webSearchEnabled: Boolean = true,
    val codeExecutionEnabled: Boolean = true,
    val autoModelFallback: Boolean = true,
    val autoMemoryEnabled: Boolean = true,
    val primaryModel: String = "gemini-3.6-flash",
    val geminiApiKey: String = "",
    val openRouterApiKey: String = "",
    val openRouterFallbackModel: String = "google/gemini-2.5-flash"
)

enum class McpAppType(val id: String, val displayName: String, val keywordHint: String) {
    YOUTUBE("youtube", "YouTube", "youtube"),
    GMAIL("gmail", "Gmail", "gmail"),
    FACEBOOK("facebook", "Facebook", "facebook"),
    INSTAGRAM("instagram", "Instagram", "instagram")
}

@Serializable
data class McpToolDescriptor(
    val appId: String,
    val name: String,
    val description: String,
    val inputSchemaJson: String
)

sealed interface StreamChunk {
    data class TextDelta(val text: String) : StreamChunk
    data class StatusNotice(val message: String) : StreamChunk
    data class GroundingSources(val urls: List<String>) : StreamChunk
    data class Error(val message: String, val recoverableForFallback: Boolean = false) : StreamChunk
    data object Completed : StreamChunk
}
