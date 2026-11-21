package com.kairos.ai.ai

import com.kairos.ai.data.local.ChatMessageEntity
import kotlin.math.ceil

class ContextWindowManager(private val maxContextTokens: Int = 24_000, private val reservedOutputTokens: Int = 4_096) {
    fun estimateTokens(text: String): Int = if (text.isEmpty()) 0 else ceil(text.length / 4.0).toInt().coerceAtLeast(1)
    fun trimMessages(systemPrompt: String, history: List<ChatMessageEntity>): List<ChatMessageEntity> {
        val budget = (maxContextTokens - reservedOutputTokens - estimateTokens(systemPrompt)).coerceAtLeast(2_048)
        val out = mutableListOf<ChatMessageEntity>()
        var used = 0
        for (msg in history.asReversed()) {
            val cost = estimateTokens(msg.content) + (if (msg.attachmentsJson.length > 4) 300 else 0) + 12
            if (out.isNotEmpty() && used + cost > budget) break
            out.add(msg); used += cost
        }
        return out.asReversed()
    }
}
