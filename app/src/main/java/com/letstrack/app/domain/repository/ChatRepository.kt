package com.letstrack.app.domain.repository

import com.letstrack.app.domain.model.ChatMessage
import com.letstrack.app.domain.model.ChatSession
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getAllSessions(): Flow<List<ChatSession>>
    suspend fun getSessionById(id: Long): ChatSession?
    suspend fun createSession(title: String): Long
    suspend fun renameSession(session: ChatSession, title: String)
    suspend fun touchSession(sessionId: Long)
    suspend fun deleteSession(session: ChatSession)

    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>>
    suspend fun addMessage(sessionId: Long, role: String, content: String)
}
