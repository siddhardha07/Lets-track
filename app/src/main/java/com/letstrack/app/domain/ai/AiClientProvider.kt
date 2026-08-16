package com.letstrack.app.domain.ai

import com.google.gson.Gson
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/** Builds the right [AiChatClient] for whichever [AiProvider] is currently active -- a factory
 * rather than one client per provider wired through Hilt directly, since all three share the
 * same underlying OkHttpClient/Gson and only differ in URL/model (or, for Gemini, request shape). */
@Singleton
class AiClientProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    fun clientFor(provider: AiProvider): AiChatClient = when (provider) {
        AiProvider.OPENAI -> OpenAiCompatibleClient(
            okHttpClient, gson,
            baseUrl = "https://api.openai.com/v1/chat/completions",
            model = provider.model
        )
        AiProvider.GROQ -> OpenAiCompatibleClient(
            okHttpClient, gson,
            baseUrl = "https://api.groq.com/openai/v1/chat/completions",
            model = provider.model
        )
        AiProvider.GEMINI -> GeminiClient(okHttpClient, gson, model = provider.model)
    }
}
