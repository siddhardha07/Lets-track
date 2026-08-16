package com.letstrack.app.domain.ai

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private data class GeminiPart(val text: String)
private data class GeminiContent(val role: String? = null, val parts: List<GeminiPart>)
private data class GeminiRequest(val contents: List<GeminiContent>, val systemInstruction: GeminiContent)
private data class GeminiResponse(val candidates: List<Candidate>?, val error: ErrorDetail?) {
    data class Candidate(val content: GeminiContent?)
    data class ErrorDetail(val message: String?)
}

/**
 * Google's Generative Language API has a different request/response shape than OpenAI's (roles
 * are "user"/"model" not "user"/"assistant", the key goes in a query param not an Authorization
 * header, content is nested under "parts" arrays) -- so unlike Groq, this can't reuse
 * OpenAiCompatibleClient, it needs its own mapping.
 */
class GeminiClient(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val model: String
) : AiChatClient {

    override suspend fun sendMessage(apiKey: String, systemContext: String, conversation: List<Pair<String, String>>): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val contents = conversation.map { (role, content) ->
                    GeminiContent(role = if (role == "assistant") "model" else "user", parts = listOf(GeminiPart(content)))
                }
                val requestBody = GeminiRequest(
                    contents = contents,
                    systemInstruction = GeminiContent(parts = listOf(GeminiPart(systemContext)))
                )
                val json = gson.toJson(requestBody)
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string()
                    if (!response.isSuccessful || bodyString == null) {
                        val parsedError = bodyString?.let { runCatching { gson.fromJson(it, GeminiResponse::class.java) }.getOrNull() }
                        return@withContext Result.failure(
                            Exception(parsedError?.error?.message ?: "Request failed (${response.code})")
                        )
                    }
                    val parsed = gson.fromJson(bodyString, GeminiResponse::class.java)
                    val text = parsed.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (text != null) Result.success(text) else Result.failure(Exception("Empty response"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
