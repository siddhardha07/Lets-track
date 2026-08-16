package com.letstrack.app.domain.ai

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private data class OpenAiRequest(val model: String, val messages: List<OpenAiMessage>)
private data class OpenAiMessage(val role: String, val content: String)
private data class OpenAiResponse(val choices: List<Choice>?, val error: ApiError?) {
    data class Choice(val message: OpenAiMessage?)
    data class ApiError(val message: String?)
}

/**
 * BYOK client for any provider that mirrors OpenAI's chat/completions request/response shape --
 * OpenAI itself, and Groq (whose API is deliberately OpenAI-compatible, per their own docs), just
 * with a different [baseUrl]/[model]. Not a single hardcoded Singleton since each provider needs
 * its own instance -- see AiClientProvider, which builds one of these per provider on demand.
 */
class OpenAiCompatibleClient(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val baseUrl: String,
    private val model: String
) : AiChatClient {

    override suspend fun sendMessage(apiKey: String, systemContext: String, conversation: List<Pair<String, String>>): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val messages = mutableListOf(OpenAiMessage("system", systemContext))
                conversation.forEach { (role, content) -> messages.add(OpenAiMessage(role, content)) }

                val requestJson = gson.toJson(OpenAiRequest(model = model, messages = messages))
                val request = Request.Builder()
                    .url(baseUrl)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestJson.toRequestBody("application/json".toMediaType()))
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string()
                    if (!response.isSuccessful || bodyString == null) {
                        val parsedError = bodyString?.let { runCatching { gson.fromJson(it, OpenAiResponse::class.java) }.getOrNull() }
                        return@withContext Result.failure(
                            Exception(parsedError?.error?.message ?: "Request failed (${response.code})")
                        )
                    }
                    val parsed = gson.fromJson(bodyString, OpenAiResponse::class.java)
                    val content = parsed.choices?.firstOrNull()?.message?.content
                    if (content != null) Result.success(content) else Result.failure(Exception("Empty response"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
