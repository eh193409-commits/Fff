package com.example.sara.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_facts")
data class MemoryFactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "command_logs")
data class CommandLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val commandText: String,
    val actionSummary: String,
    val succeeded: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "conversation_messages")
data class ConversationMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isFromSara: Boolean,
    val text: String,
    val actionBadge: String? = null,
    val isPlaceholderAi: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
