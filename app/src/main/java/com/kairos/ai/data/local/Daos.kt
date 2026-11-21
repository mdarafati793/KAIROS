package com.kairos.ai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC") fun observeSessions(): Flow<List<ChatSessionEntity>>
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC") suspend fun getAllSessions(): List<ChatSessionEntity>
    @Query("SELECT * FROM chat_sessions WHERE id = :id LIMIT 1") suspend fun getSessionById(id: String): ChatSessionEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSession(session: ChatSessionEntity)
    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :id") suspend fun updateSessionTitle(id: String, title: String, updatedAt: Long)
    @Query("UPDATE chat_sessions SET updatedAt = :updatedAt, modelId = :modelId WHERE id = :id") suspend fun touchSession(id: String, updatedAt: Long, modelId: String)
    @Query("DELETE FROM chat_sessions WHERE id = :id") suspend fun deleteSession(id: String)
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sid ORDER BY timestamp ASC") fun observeMessages(sid: String): Flow<List<ChatMessageEntity>>
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sid ORDER BY timestamp ASC") suspend fun getMessagesForSession(sid: String): List<ChatMessageEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMessage(msg: ChatMessageEntity)
    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sid") suspend fun countMessages(sid: String): Int
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC") fun observeMemories(): Flow<List<MemoryEntity>>
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC") suspend fun getAllMemories(): List<MemoryEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMemory(memory: MemoryEntity)
    @Query("DELETE FROM memories WHERE id = :id") suspend fun deleteMemory(id: String)
}

@Dao
interface ConnectorDao {
    @Query("SELECT * FROM mcp_connectors ORDER BY name ASC") fun observeConnectors(): Flow<List<McpConnectorEntity>>
    @Query("SELECT * FROM mcp_connectors") suspend fun getAllConnectors(): List<McpConnectorEntity>
    @Query("SELECT * FROM mcp_connectors WHERE id = :id LIMIT 1") suspend fun getConnector(id: String): McpConnectorEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertConnector(connector: McpConnectorEntity)
}
