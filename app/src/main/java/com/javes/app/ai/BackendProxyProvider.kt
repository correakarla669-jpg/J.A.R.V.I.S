package com.javes.app.ai

import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.coroutines.cancellation.CancellationException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Cliente de um backend próprio do usuário.
 *
 * O app envia apenas o texto da conversa (sem chaves de API).
 * O servidor guarda as credenciais e devolve `{ "text": "..." }`.
 *
 * Contrato esperado (POST JSON):
 * {
 *   "model": "...",
 *   "system": "...",
 *   "messages": [ { "role": "user"|"assistant", "content": "..." } ]
 * }
 * Resposta 200: { "text": "resposta final" }
 *
 * Nenhuma chave de provedor trafega no app quando o proxy está ativo.
 */
class BackendProxyProvider(
    private val baseUrl: String
) : AIProvider {

    override val id: ProviderId = ProviderId.GEMINI // rótulo neutro; o backend escolhe o motor

    override suspend fun generate(request: AIRequest): String {
        val url = baseUrl.trim().removeSuffix("/") + "/v1/chat"
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw AIException(AIErrorType.UNKNOWN, "URL do backend inválida. Use http:// ou https://")
        }

        val messages = JSONArray()
        request.messages.forEach { m ->
            messages.put(
                JSONObject()
                    .put("role", if (m.role == ChatRole.USER) "user" else "assistant")
                    .put("content", m.text)
            )
        }
        val body = JSONObject()
            .put("model", request.model)
            .put("system", request.systemPrompt)
            .put("messages", messages)
            .toString()

        val response = try {
            postJson(url = url, headers = emptyMap(), body = body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw AIException(AIErrorType.TIMEOUT, "O backend demorou demais para responder.")
        } catch (e: IOException) {
            throw AIException(AIErrorType.NETWORK, "Não consegui conectar ao backend. Verifique a URL e a rede.")
        } catch (e: Exception) {
            throw AIException(AIErrorType.UNKNOWN, "Falha ao falar com o backend.")
        }

        if (response.code !in 200..299) {
            throw AIException(
                AIErrorType.SERVER,
                "O backend retornou HTTP ${response.code}."
            )
        }
        return try {
            val text = JSONObject(response.body).optString("text").trim()
            if (text.isBlank()) {
                throw AIException(AIErrorType.EMPTY_RESPONSE, "O backend respondeu em branco.")
            }
            text
        } catch (e: AIException) {
            throw e
        } catch (e: JSONException) {
            throw AIException(AIErrorType.UNKNOWN, "Resposta inválida do backend.")
        }
    }
}
