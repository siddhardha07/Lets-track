package com.letstrack.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letstrack.app.domain.ai.AiClientProvider
import com.letstrack.app.domain.ai.AiSettingsRepository
import com.letstrack.app.domain.ai.buildBudgetContext
import com.letstrack.app.domain.budget.BudgetStatusProvider
import com.letstrack.app.domain.model.ChatMessage
import com.letstrack.app.domain.model.ChatSession
import com.letstrack.app.domain.repository.CategoryRepository
import com.letstrack.app.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val aiSettingsRepository: AiSettingsRepository,
    private val budgetStatusProvider: BudgetStatusProvider,
    private val categoryRepository: CategoryRepository,
    private val aiClientProvider: AiClientProvider
) : ViewModel() {

    // One-shot, not a StateFlow -- a StateFlow driving this redirect/send decision would be
    // seeded with null/no-provider before the real DataStore value loads, which is exactly the
    // bug that made both the redirect-to-setup guard and sendMessage silently fail earlier (see
    // AiSettingsRepository.currentActiveProviderAndKey's doc comment).
    suspend fun currentActiveProviderAndKey() = aiSettingsRepository.currentActiveProviderAndKey()

    val sessions: StateFlow<List<ChatSession>> = chatRepository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Null = a fresh, not-yet-saved chat -- no session row exists until the first message is
    // actually sent, so opening the chat screen never litters the sessions list with empties.
    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

    val messages: StateFlow<List<ChatMessage>> = _currentSessionId.flatMapLatest { sessionId ->
        if (sessionId == null) flowOf(emptyList()) else chatRepository.getMessagesForSession(sessionId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    fun startNewChat() {
        _currentSessionId.value = null
    }

    fun selectSession(sessionId: Long) {
        _currentSessionId.value = sessionId
    }

    fun deleteSession(session: ChatSession) {
        viewModelScope.launch {
            chatRepository.deleteSession(session)
            if (_currentSessionId.value == session.id) _currentSessionId.value = null
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _isSending.value) return

        viewModelScope.launch {
            _isSending.value = true
            try {
                val providerAndKey = currentActiveProviderAndKey() ?: return@launch
                val (provider, key) = providerAndKey

                var sessionId = _currentSessionId.value
                if (sessionId == null) {
                    // First message of a new chat -- title it from the message itself (like
                    // ChatGPT/Claude do), truncated so it stays a short label in the sessions list.
                    sessionId = chatRepository.createSession(title = trimmed.take(48))
                    _currentSessionId.value = sessionId
                }

                chatRepository.addMessage(sessionId, "user", trimmed)

                val budgetSummary = budgetStatusProvider.summary.first()
                val categories = categoryRepository.getAllCategories().first()
                val context = buildBudgetContext(budgetSummary, categories)

                val history = chatRepository.getMessagesForSession(sessionId).first()
                    .map { it.role to it.content }

                val client = aiClientProvider.clientFor(provider)
                val result = client.sendMessage(key, context, history)
                result.fold(
                    onSuccess = { reply -> chatRepository.addMessage(sessionId, "assistant", reply) },
                    onFailure = { error ->
                        chatRepository.addMessage(
                            sessionId,
                            "assistant",
                            "Sorry, I couldn't reach ${provider.displayName}: ${error.message ?: "unknown error"}. Check your API key in Settings and try again."
                        )
                    }
                )
            } finally {
                _isSending.value = false
            }
        }
    }
}
