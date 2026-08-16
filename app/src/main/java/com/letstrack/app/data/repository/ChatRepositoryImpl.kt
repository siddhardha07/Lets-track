package com.letstrack.app.data.repository

import com.letstrack.app.data.local.dao.ChatMessageDao
import com.letstrack.app.data.local.dao.ChatSessionDao
import com.letstrack.app.data.local.entity.ChatMessageEntity
import com.letstrack.app.data.local.entity.ChatSessionEntity
import com.letstrack.app.domain.model.ChatMessage
import com.letstrack.app.domain.model.ChatSession
import com.letstrack.app.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val sessionDao: ChatSessionDao,
    private val messageDao: ChatMessageDao
) : ChatRepository {

    override fun getAllSessions(): Flow<List<ChatSession>> =
        sessionDao.getAllSessions().map { entities -> entities.map { it.toDomainModel() } }

    override suspend fun getSessionById(id: Long): ChatSession? = sessionDao.getSessionById(id)?.toDomainModel()

    override suspend fun createSession(title: String): Long =
        sessionDao.insertSession(ChatSessionEntity(title = title))

    override suspend fun renameSession(session: ChatSession, title: String) {
        sessionDao.updateSession(session.toEntity().copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun touchSession(sessionId: Long) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        sessionDao.updateSession(session.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteSession(session: ChatSession) {
        messageDao.deleteMessagesForSession(session.id)
        sessionDao.deleteSession(session.toEntity())
    }

    override fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>> =
        messageDao.getMessagesForSession(sessionId).map { entities -> entities.map { it.toDomainModel() } }

    override suspend fun addMessage(sessionId: Long, role: String, content: String) {
        messageDao.insertMessage(ChatMessageEntity(sessionId = sessionId, role = role, content = content))
        touchSession(sessionId)
    }

    private fun ChatSessionEntity.toDomainModel() = ChatSession(id = id, title = title, createdAt = createdAt, updatedAt = updatedAt)
    private fun ChatSession.toEntity() = ChatSessionEntity(id = id, title = title, createdAt = createdAt, updatedAt = updatedAt)
    private fun ChatMessageEntity.toDomainModel() = ChatMessage(id = id, sessionId = sessionId, role = role, content = content, timestamp = timestamp)
}
