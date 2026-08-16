package com.letstrack.app.domain.ai

/** One provider's "send this conversation, get a reply" call -- OpenAiCompatibleClient and
 * GeminiClient both implement this so AiChatViewModel doesn't need to know which provider it's
 * actually talking to. */
interface AiChatClient {
    suspend fun sendMessage(apiKey: String, systemContext: String, conversation: List<Pair<String, String>>): Result<String>
}
