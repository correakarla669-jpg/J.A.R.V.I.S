package com.javes.app.ai

import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.coroutines.cancellation.CancellationException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Google Gemini via REST (generateContent), sem bibliotecas externas.
 * Documentação: https://ai.google.dev/api/generate-content
 *  - POST https://generativelanguage.googleapis.com/v1beta/models/{modelo}:generateContent
 *  - chave no cabeçalho x-goog-api-key (nunca na URL, para não vazar em logs)
 *  - histórico em "contents" com os papéis "user" e "model"; instruções em "systemInstruction"
 */
class GeminiProvider : AIProvider {

    override val id = ProviderId.GEMINI

    override suspend fun generate(request: AIRequest): String {
        if (request.apiKey.isBlank()) {
            throw AIException(AIErrorType.MISSING_KEY, "Falta a chave de API do Gemini.")
        }
        val model = request.model.trim().removePrefix("models/").ifBlank { id.defaultModel }
        val url = "$BASE_URL/$model:generateContent"

        val response = try {
            postJson(
                url = url,
                headers = mapOf("x-goog-api-key" to request.apiKey),
                body = buildBody(request)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw AIException(AIErrorType.TIMEOUT, "O Gemini demorou demais para responder. Tente de novo.")
        } catch (e: IOException) {
            throw AIException(AIErrorType.NETWORK, "Não consegui conectar ao Gemini. Verifique sua internet.")
        } catch (e: Exception) {
            throw AIException(AIErrorType.UNKNOWN, "Falha inesperada ao falar com o Gemini.")
        }

        return parseResponse(response.code, response.body)
    }

    private fun buildBody(request: AIRequest): String {
        val contents = JSONArray()
        request.messages.forEach { message ->
            contents.put(
                JSONObject()
                    .put("role", if (message.role == ChatRole.USER) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", message.text)))
            )
        }
        val root = JSONObject().put("contents", contents)
        if (request.systemPrompt.isNotBlank()) {
            root.put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", request.systemPrompt))
                )
            )
        }
        return root.toString()
    }

    private fun parseResponse(code: Int, body: String): String {
        if (code !in 200..299) throw errorFor(code, body)

        val root = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw AIException(AIErrorType.UNKNOWN, "Resposta inválida do Gemini.")
        }

        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val blockReason = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            if (blockReason.isNotEmpty()) {
                throw AIException(AIErrorType.BLOCKED, "O Gemini não aceitou essa mensagem ($blockReason).")
            }
            throw AIException(AIErrorType.EMPTY_RESPONSE, "O Gemini não retornou nenhuma resposta.")
        }

        val candidate = candidates.getJSONObject(0)
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
        val text = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                if (part.optBoolean("thought", false)) continue
                text.append(part.optString("text"))
            }
        }

        val result = text.toString().trim()
        if (result.isNotEmpty()) return result

        when (candidate.optString("finishReason")) {
            "SAFETY", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "RECITATION" ->
                throw AIException(AIErrorType.BLOCKED, "O Gemini bloqueou essa resposta por segurança.")
            "MAX_TOKENS" ->
                throw AIException(AIErrorType.EMPTY_RESPONSE, "A resposta do Gemini foi cortada antes de começar.")
            else ->
                throw AIException(AIErrorType.EMPTY_RESPONSE, "O Gemini retornou uma resposta vazia.")
        }
    }

    private fun errorFor(code: Int, body: String): AIException {
        val error = try {
            JSONObject(body).optJSONObject("error")
        } catch (e: JSONException) {
            null
        }
        val apiMessage = error?.optString("message").orEmpty().take(200)

        return when {
            code == 400 && apiMessage.contains("API key", ignoreCase = true) ->
                AIException(AIErrorType.INVALID_KEY, "A chave de API do Gemini parece inválida. Confira em CONFIG.")
            code == 401 || code == 403 ->
                AIException(AIErrorType.INVALID_KEY, "O Gemini recusou a chave de API (inválida ou sem permissão).")
            code == 404 ->
                AIException(AIErrorType.MODEL_NOT_FOUND, "O modelo configurado não foi encontrado no Gemini. Confira o nome em CONFIG.")
            code == 429 ->
                AIException(AIErrorType.RATE_LIMIT, "Limite de uso do Gemini atingido. Aguarde um pouco e tente de novo.")
            code in 500..599 ->
                AIException(AIErrorType.SERVER, "O serviço do Gemini está instável no momento. Tente novamente.")
            code == 400 ->
                AIException(AIErrorType.UNKNOWN, "O Gemini recusou o pedido: $apiMessage".trim())
            else ->
                AIException(AIErrorType.UNKNOWN, "Erro $code do Gemini.")
        }
    }

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }
}
