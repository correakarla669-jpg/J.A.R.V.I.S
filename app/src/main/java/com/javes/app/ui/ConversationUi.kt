package com.javes.app.ui

import com.javes.app.ai.ProviderId

/** O que a tela mostra da conversa. */
data class ConversationUi(
    /** Última frase reconhecida ou digitada. */
    val heard: String = "",
    /** Texto parcial enquanto o usuário ainda fala. */
    val partial: String = "",
    /** Última resposta do JAVES. */
    val reply: String = "",
    /** Aviso ou erro para o usuário (null = nenhum). */
    val notice: String? = null,
    /** Se true, o JAVES volta a ouvir sozinho depois de cada resposta (enquanto o app está aberto). */
    val autoConversation: Boolean = false
)

/** Um provedor de IA na lista de configuração. */
data class ProviderOption(
    val id: ProviderId,
    /** False = ainda não implementado ("em breve"). */
    val available: Boolean,
    /** True = há uma chave salva para este provedor. */
    val configured: Boolean
)

/** Estado da tela de configuração da IA. */
data class AiSettingsUi(
    val activeProvider: ProviderId,
    val options: List<ProviderOption>,
    /** Modelo do provedor ativo. */
    val model: String,
    /** Se o provedor ativo tem chave salva. */
    val hasKey: Boolean,
    val initiativeEnabled: Boolean,
    /** Consulta vários provedores e combina respostas reais. */
    val ensembleEnabled: Boolean = true,
    /** URL do backend próprio (vazio = chaves no aparelho). */
    val backendProxyUrl: String = "",
    /** Modo 24h: serviço sempre ouvindo, mesmo com o app fechado. */
    val alwaysOn: Boolean = false,
    /** Ativação por voz "JAVES, inicia" com o app aberto. */
    val voiceActivationEnabled: Boolean = false,
    val testStatus: String? = null,
    val testing: Boolean = false
)
