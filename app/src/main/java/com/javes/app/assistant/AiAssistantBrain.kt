package com.javes.app.assistant

import com.javes.app.ai.AIException
import com.javes.app.ai.AIProviderManager
import kotlin.coroutines.cancellation.CancellationException

/**
 * Fluxo do JAVES: primeiro tenta um comando local; se a fala não for um comando,
 * envia a mensagem ao provedor de IA ativo (com o contexto da conversa).
 *
 * [isOnline] permite o modo offline: sem internet, o JAVES continua controlando
 * o aparelho pelos comandos locais e avisa que a conversa com IA fica indisponível.
 */
class AiAssistantBrain(
    private val local: LocalCommandBrain,
    private val ai: AIProviderManager,
    private val isOnline: () -> Boolean = { true }
) : AssistantBrain {

    override suspend fun respond(input: String): AssistantReply {
        local.match(input)?.let { return it }

        if (!isOnline()) {
            return AssistantReply(
                text = "Estou sem internet no momento. Os comandos de voz do aparelho " +
                    "continuam funcionando: abrir apps, música, ligações, alarmes, lanterna, " +
                    "volume e mais. A conversa com inteligência artificial volta quando a " +
                    "conexão voltar.",
                isError = true
            )
        }

        return try {
            AssistantReply(text = ai.ask(input))
        } catch (e: CancellationException) {
            throw e
        } catch (e: AIException) {
            AssistantReply(
                text = e.message ?: "Não consegui falar com a inteligência artificial.",
                isError = true
            )
        } catch (e: Exception) {
            AssistantReply(
                text = "Não consegui falar com a inteligência artificial.",
                isError = true
            )
        }
    }
}
