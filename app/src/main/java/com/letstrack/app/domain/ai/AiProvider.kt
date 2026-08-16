package com.letstrack.app.domain.ai

/**
 * [isFree] reflects the provider's own published pricing at the time this was written, not a
 * guarantee -- providers change pricing/free tiers over time, this is just what's shown as a
 * "Free" hint in the picker.
 */
enum class AiProvider(
    val id: String,
    val displayName: String,
    val isFree: Boolean,
    val apiKeyUrl: String,
    val model: String
) {
    OPENAI(
        id = "openai",
        displayName = "OpenAI (GPT)",
        isFree = false,
        apiKeyUrl = "https://platform.openai.com/api-keys",
        model = "gpt-4o-mini"
    ),
    GROQ(
        id = "groq",
        displayName = "Groq",
        isFree = true,
        apiKeyUrl = "https://console.groq.com/keys",
        model = "llama-3.3-70b-versatile"
    ),
    GEMINI(
        id = "gemini",
        displayName = "Google Gemini",
        isFree = true,
        apiKeyUrl = "https://aistudio.google.com/app/apikey",
        model = "gemini-2.0-flash"
    )
}
