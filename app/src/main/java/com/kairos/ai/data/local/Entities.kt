package com.kairos.ai.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val updatedAt: Long,
    val modelId: String
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [ForeignKey(ChatSessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["sessionId"])]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val attachmentsJson: String = "[]",
    val timestamp: Long
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val fact: String,
    val category: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "mcp_connectors")
data class McpConnectorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val enabled: Boolean,
    val isConnected: Boolean,
    val toolCount: Int,
    val toolsJson: String,
    val lastError: String? = null,
    val updatedAt: Long
)
