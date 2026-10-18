package com.javes.app.assistant

import com.javes.app.device.DeviceAction

/** Ação extra que a resposta pede ao app, além de falar o texto. */
enum class AssistantAction {
    NONE,
    /** Interromper qualquer fala em andamento. */
    STOP_SPEAKING,
    /** Voltar ao modo de espera (encerra a conversa contínua). */
    STANDBY
}

data class AssistantReply(
    val text: String,
    /** Se false, o texto aparece só na tela (ex.: confirmação de "pare de falar"). */
    val speak: Boolean = true,
    val action: AssistantAction = AssistantAction.NONE,
    /** Ação no aparelho a executar antes de falar (abrir app, ligar, alarme…). */
    val deviceAction: DeviceAction? = null,
    /** True quando o texto descreve uma falha (ex.: IA indisponível), não uma resposta. */
    val isError: Boolean = false
)

/**
 * Ponto de extensão da inteligência do JAVES.
 * Implementações: [LocalCommandBrain] (comandos locais) e [AiAssistantBrain]
 * (comandos locais primeiro; o restante vai ao provedor de IA ativo).
 * A função é suspend porque a chamada de rede é assíncrona.
 */
interface AssistantBrain {
    suspend fun respond(input: String): AssistantReply
}
