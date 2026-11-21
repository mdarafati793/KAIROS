package com.kairos.ai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChatSessionEntity::class, ChatMessageEntity::class, MemoryEntity::class, McpConnectorEntity::class],
    version = 2,
    exportSchema = false
)
abstract class KairosDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun memoryDao(): MemoryDao
    abstract fun connectorDao(): ConnectorDao

    companion object {
        @Volatile private var INSTANCE: KairosDatabase? = null
        fun getInstance(context: Context): KairosDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, KairosDatabase::class.java, "kairos_v2.db")
                .fallbackToDestructiveMigration(dropAllTables = true).build().also { INSTANCE = it }
        }
    }
}
