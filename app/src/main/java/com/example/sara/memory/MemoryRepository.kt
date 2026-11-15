package com.example.sara.memory

import kotlinx.coroutines.flow.Flow

class MemoryRepository(private val dao: MemoryDao) {
    val memories: Flow<List<MemoryFactEntity>> = dao.observeAllMemories()
    val recentLogs: Flow<List<CommandLogEntity>> = dao.observeRecentLogs()
    val messages: Flow<List<ConversationMessageEntity>> = dao.observeMessages()

    suspend fun getMemoriesSnapshot(): List<MemoryFactEntity> = dao.getAllMemoriesOnce()

    suspend fun rememberFact(category: String, content: String) {
        val cleaned = content.trim()
        if (cleaned.isNotEmpty()) {
            dao.insertMemory(MemoryFactEntity(category = category, content = cleaned))
        }
    }

    suspend fun forgetFact(id: Long) {
        dao.deleteMemoryById(id)
    }

    suspend fun logCommand(commandText: String, actionSummary: String, succeeded: Boolean) {
        dao.insertLog(
            CommandLogEntity(
                commandText = commandText,
                actionSummary = actionSummary,
                succeeded = succeeded
            )
        )
    }

    suspend fun clearCommandLogs() = dao.clearLogs()

    suspend fun addMessage(
        isFromSara: Boolean,
        text: String,
        actionBadge: String? = null,
        isPlaceholderAi: Boolean = false
    ) {
        dao.insertMessage(
            ConversationMessageEntity(
                isFromSara = isFromSara,
                text = text,
                actionBadge = actionBadge,
                isPlaceholderAi = isPlaceholderAi
            )
        )
    }

    suspend fun clearConversation() = dao.clearMessages()
}
