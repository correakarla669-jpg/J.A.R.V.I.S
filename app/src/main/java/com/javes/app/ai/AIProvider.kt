package com.javes.app.ai

/**
 * Provedores de IA conhecidos pelo JAVES.
 * [implemented] = existe uma implementação real de [AIProvider] registrada no app.
 * Os três provedores estão implementados: Gemini (nativo), Groq e OpenRouter
 * (ambos via formato OpenAI Chat Completions).
 */
enum class ProviderId(
    val displayName: String,
    val implemented: Boolean,
    val defaultModel: String
) {
    GEMINI("Google Gemini", true, "gemini-2.0-flash"),
    GROQ("Groq", true, "llama-3.3-70b-versatile"),
    OPENROUTER("OpenRouter", true, "openai/gpt-4o-mini")
}

enum class ChatRole { USER, ASSISTANT }

data class ChatMessage(val role: ChatRole, val text: String)

/** Tudo que um provedor precisa para uma chamada. Provedores não guardam estado. */
data class AIRequest(
    val model: String,
    val apiKey: String,
    val systemPrompt: String,
    val messages: List<ChatMessage>
)

enum class AIErrorType {
    MISSING_KEY,
    INVALID_KEY,
    MODEL_NOT_FOUND,
    RATE_LIMIT,
    NETWORK,
    TIMEOUT,
    BLOCKED,
    EMPTY_RESPONSE,
    SERVER,
    NOT_AVAILABLE,
    UNKNOWN
}

/** Erro de IA com mensagem já pronta para o usuário (em português, sem dados sensíveis). */
class AIException(val type: AIErrorType, message: String) : Exception(message)

/** Contrato comum a todos os provedores (Gemini, Groq e OpenRouter). */
interface AIProvider {
    val id: ProviderId

    /**
     * Envia a conversa e devolve o texto da resposta.
     * Lança [AIException] em falhas previstas e respeita o cancelamento da corrotina.
     */
    suspend fun generate(request: AIRequest): String
}
