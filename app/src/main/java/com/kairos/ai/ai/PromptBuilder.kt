package com.kairos.ai.ai

import com.kairos.ai.data.local.MemoryEntity
import com.kairos.ai.data.model.*

object PromptBuilder {
    fun buildMasterSystemPrompt(
        customInstructions: String,
        memories: List<MemoryEntity>,
        accessibilityConnected: Boolean,
        mcpTools: List<McpToolDescriptor>,
        capabilities: CapabilitiesState
    ): String = buildString {
        appendLine("You are KAIROS v2.0, an intelligent, fast, privacy-conscious Android AI assistant.")
        appendLine("Format responses clearly using Markdown headers, lists, bold text, and fenced ```lang code blocks when helpful.")
        if (customInstructions.isNotBlank()) { appendLine("\n### User Custom Instructions"); appendLine(customInstructions.trim()) }
        if (memories.isNotEmpty()) {
            appendLine("\n### Long-Term Personal Memory")
            memories.take(40).forEach { appendLine("- [${it.category}] ${it.fact}") }
        }
        appendLine("\n### Active Capabilities")
        appendLine("- Web Search: ${capabilities.webSearchEnabled} | Code Execution: ${capabilities.codeExecutionEnabled}")
        appendLine("- Accessibility Bridge: ${if (accessibilityConnected) "CONNECTED (tools: get_screen_context, click_ui_element, navigate_back, navigate_home)" else "DISCONNECTED"}")
        if (mcpTools.isNotEmpty()) appendLine("- Connected MCP Apps (${mcpTools.map { it.appId }.distinct().joinToString(", ")}): ${mcpTools.size} tools active.")
    }.trim()
}
