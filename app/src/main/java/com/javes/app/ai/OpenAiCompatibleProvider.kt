package com.javes.app.ai

import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.coroutines.cancellation.CancellationException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Provedor no formato OpenAI Chat Completions (Groq e OpenRouter).
 * - Groq: https://api.groq.com/openai/v1/chat/completions
 * - OpenRouter: https://openrouter.ai/api/v1/chat/completions
 * Chave apenas no cabeçalho Authorization: Bearer … (nunca em logs).
 */
class OpenAiCompatibleProvider(
    override val id: ProviderId,
    private val endpoint: String,
    private val extraHeaders: Map<String, String> = emptyMap()
) : AIProvider {

    override suspend fun generate(request: AIRequest): String {
        if (request.apiKey.isBlank()) {
            throw AIException(AIErrorType.MISSING_KEY, "Falta a chave de API do ${id.displayName}.")
        }
        val model = request.model.trim().ifBlank { id.defaultModel }
        if (model.isBlank()) {
            throw AIException(AIErrorType.MODEL_NOT_FOUND, "Modelo não configurado para ${id.displayName}.")
        }

        val response = try {
            postJson(
                url = endpoint,
                headers = buildMap {
                    put("Authorization", "Bearer ${request.apiKey}")
                    putAll(extraHeaders)
                },
                body = buildBody(model, request)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw AIException(AIErrorType.TIMEOUT, "O ${id.displayName} demorou demais para responder.")
        } catch (e: IOException) {
            throw AIException(AIErrorType.NETWORK, "Não consegui conectar ao ${id.displayName}. Verifique sua internet.")
        } catch (e: Exception) {
            throw AIException(AIErrorType.UNKNOWN, "Falha inesperada ao falar com o ${id.displayName}.")
        }

        return parseResponse(response.code, response.body)
    }

    private fun buildBody(model: String, request: AIRequest): String {
        val messages = JSONArray()
        if (request.systemPrompt.isNotBlank()) {
            messages.put(
                JSONObject()
                    .put("role", "system")
                    .put("content", request.systemPrompt)
            )
        }
        request.messages.forEach { message ->
            messages.put(
                JSONObject()
                    .put("role", if (message.role == ChatRole.USER) "user" else "assistant")
                    .put("content", message.text)
            )
        }
        return JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", 0.7)
            .toString()
    }

    private fun parseResponse(code: Int, body: String): String {
        if (code == 401 || code == 403) {
            throw AIException(AIErrorType.INVALID_KEY, "Chave de API do ${id.displayName} inválida ou sem permissão.")
        }
        if (code == 404) {
            throw AIException(AIErrorType.MODEL_NOT_FOUND, "Modelo não encontrado no ${id.displayName}. Confira o nome em CONFIG.")
        }
        if (code == 429) {
            throw AIException(AIErrorType.RATE_LIMIT, "Limite de uso do ${id.displayName} atingido. Tente mais tarde.")
        }
        if (code !in 200..299) {
            val detail = try {
                JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
            } catch (_: JSONException) {
                ""
            }
            val msg = if (detail.isNotBlank()) "Erro do ${id.displayName}: ${detail.take(120)}"
            else "O ${id.displayName} retornou erro HTTP $code."
            throw AIException(if (code >= 500) AIErrorType.SERVER else AIErrorType.UNKNOWN, msg)
        }

        return try {
            val root = JSONObject(body)
            val choices = root.optJSONArray("choices")
            val text = choices
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                .orEmpty()
            if (text.isBlank()) {
                throw AIException(AIErrorType.EMPTY_RESPONSE, "O ${id.displayName} respondeu em branco.")
            }
            text
        } catch (e: AIException) {
            throw e
        } catch (e: JSONException) {
            throw AIException(AIErrorType.UNKNOWN, "Resposta inválida do ${id.displayName}.")
        }
    }
}

fun createGroqProvider(): AIProvider = OpenAiCompatibleProvider(
    id = ProviderId.GROQ,
    endpoint = "https://api.groq.com/openai/v1/chat/completions"
)

fun createOpenRouterProvider(): AIProvider = OpenAiCompatibleProvider(
    id = ProviderId.OPENROUTER,
    endpoint = "https://openrouter.ai/api/v1/chat/completions",
    extraHeaders = mapOf(
        // Identificação opcional recomendada pela OpenRouter; sem dados sensíveis.
        "HTTP-Referer" to "https://javes.local",
        "X-Title" to "JAVES"
    )
)
