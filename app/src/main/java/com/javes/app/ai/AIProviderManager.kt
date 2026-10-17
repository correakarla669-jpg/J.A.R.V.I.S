package com.javes.app.ai

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Gerencia provedores, histórico e modo ensemble.
 *
 * - [ask]: se [AISettingsStore.ensembleEnabled] e há 2+ provedores com chave,
 *   consulta todos em paralelo e combina respostas bem-sucedidas.
 * - Falhas individuais são registradas; nunca se inventa que um provedor respondeu.
 * - Com proxy de backend configurado, as chaves não saem do servidor (ver [BackendProxyProvider]).
 */
class AIProviderManager(
    private val settings: AISettingsStore,
    providers: List<AIProvider> = listOf(
        GeminiProvider(),
        createGroqProvider(),
        createOpenRouterProvider()
    )
) {
    private val providers: Map<ProviderId, AIProvider> = providers.associateBy { it.id }
    private val history = ArrayList<ChatMessage>()
    private val historyMutex = Mutex()

    val activeProviderId: ProviderId
        get() {
            val saved = settings.activeProvider
            return if (providers.containsKey(saved)) saved else providers.keys.first()
        }

    fun isAvailable(id: ProviderId): Boolean = providers.containsKey(id)

    fun isConfigured(id: ProviderId): Boolean {
        if (settings.backendProxyUrl.isNotBlank()) {
            // Com proxy, a chave fica no servidor; consideramos "configurado" se o proxy existe.
            return true
        }
        return providers.containsKey(id) && settings.hasApiKey(id)
    }

    fun selectProvider(id: ProviderId) {
        if (providers.containsKey(id)) settings.activeProvider = id
    }

    fun clearHistory() {
        history.clear()
    }

    suspend fun ask(userText: String): String {
        val userMessage = ChatMessage(ChatRole.USER, userText)
        val prompt = buildSystemPrompt()
        val snapshot = historyMutex.withLock { history.toList() }

        val reply = if (settings.ensembleEnabled) {
            askEnsemble(userMessage, prompt, snapshot)
        } else {
            askSingle(userMessage, prompt, snapshot)
        }

        historyMutex.withLock {
            history.add(userMessage)
            history.add(ChatMessage(ChatRole.ASSISTANT, reply))
            while (history.size > MAX_HISTORY_MESSAGES) {
                history.removeAt(0)
                history.removeAt(0)
            }
        }
        return reply
    }

    suspend fun test(): String {
        if (settings.backendProxyUrl.isNotBlank()) {
            val proxy = BackendProxyProvider(settings.backendProxyUrl)
            return proxy.generate(
                AIRequest(
                    model = settings.model(activeProviderId),
                    apiKey = "",
                    systemPrompt = "",
                    messages = listOf(ChatMessage(ChatRole.USER, "Responda somente com a palavra: ok"))
                )
            )
        }
        val resolved = resolve(activeProviderId)
        return resolved.provider.generate(
            AIRequest(
                model = resolved.model,
                apiKey = resolved.apiKey,
                systemPrompt = "",
                messages = listOf(ChatMessage(ChatRole.USER, "Responda somente com a palavra: ok"))
            )
        )
    }

    private suspend fun askSingle(
        userMessage: ChatMessage,
        prompt: String,
        snapshot: List<ChatMessage>
    ): String {
        if (settings.backendProxyUrl.isNotBlank()) {
            return BackendProxyProvider(settings.backendProxyUrl).generate(
                AIRequest(
                    model = settings.model(activeProviderId),
                    apiKey = "",
                    systemPrompt = prompt,
                    messages = snapshot + userMessage
                )
            )
        }
        val resolved = resolve(activeProviderId)
        return resolved.provider.generate(
            AIRequest(
                model = resolved.model,
                apiKey = resolved.apiKey,
                systemPrompt = prompt,
                messages = snapshot + userMessage
            )
        )
    }

    /**
     * Consulta todos os provedores com chave (ou o proxy uma vez).
     * Combina apenas respostas reais; lista falhas honestamente se nenhuma funcionar.
     */
    private suspend fun askEnsemble(
        userMessage: ChatMessage,
        prompt: String,
        snapshot: List<ChatMessage>
    ): String = coroutineScope {
        // Com proxy único, não há múltiplos backends locais — uma chamada basta.
        if (settings.backendProxyUrl.isNotBlank()) {
            return@coroutineScope askSingle(userMessage, prompt, snapshot)
        }

        val configured = ProviderId.entries.filter { settings.hasApiKey(it) && providers.containsKey(it) }
        if (configured.isEmpty()) {
            throw AIException(
                AIErrorType.MISSING_KEY,
                "Nenhuma chave de API configurada. Configure Gemini, Groq ou OpenRouter em CONFIG, ou defina um proxy de backend."
            )
        }
        if (configured.size == 1) {
            val id = configured.first()
            val resolved = resolve(id)
            return@coroutineScope resolved.provider.generate(
                AIRequest(
                    model = resolved.model,
                    apiKey = resolved.apiKey,
                    systemPrompt = prompt,
                    messages = snapshot + userMessage
                )
            )
        }

        val jobs = configured.map { id ->
            async {
                try {
                    val resolved = resolve(id)
                    val text = resolved.provider.generate(
                        AIRequest(
                            model = resolved.model,
                            apiKey = resolved.apiKey,
                            systemPrompt = prompt,
                            messages = snapshot + userMessage
                        )
                    )
                    EnsemblePart.Ok(id, text)
                } catch (e: AIException) {
                    EnsemblePart.Fail(id, e.message ?: e.type.name)
                } catch (e: Exception) {
                    EnsemblePart.Fail(id, "falha inesperada")
                }
            }
        }

        val parts = jobs.awaitAll()
        val oks = parts.filterIsInstance<EnsemblePart.Ok>()
        val fails = parts.filterIsInstance<EnsemblePart.Fail>()

        when {
            oks.isEmpty() -> {
                val detail = fails.joinToString("; ") { "${it.id.displayName}: ${it.reason}" }
                throw AIException(
                    AIErrorType.SERVER,
                    "Nenhum provedor respondeu. $detail"
                )
            }
            oks.size == 1 -> oks.first().text
            else -> combineAnswers(oks, fails)
        }
    }

    private fun combineAnswers(oks: List<EnsemblePart.Ok>, fails: List<EnsemblePart.Fail>): String {
        // Só usa respostas reais. Se forem muito parecidas, devolve uma; senão resume com atribuição.
        val unique = oks.map { it.text.trim() }.filter { it.isNotBlank() }
            .distinctBy { it.lowercase().take(100) }
        val body = when {
            unique.isEmpty() -> "Nenhum provedor devolveu texto útil."
            unique.size == 1 -> unique.first()
            else -> {
                val parts = oks.joinToString(" ") { part ->
                    val short = part.text.trim().take(220)
                    "${part.id.displayName}: $short"
                }
                "Consultei ${oks.size} fontes. $parts"
            }
        }
        val failNote = if (fails.isNotEmpty()) {
            " Não obtive resposta de: " + fails.joinToString(", ") { it.id.displayName } + "."
        } else ""
        return (body + failNote).take(900)
    }

    private data class Resolved(val provider: AIProvider, val model: String, val apiKey: String)

    private fun resolve(id: ProviderId): Resolved {
        val provider = providers[id]
            ?: throw AIException(AIErrorType.NOT_AVAILABLE, "${id.displayName} não está disponível neste app.")
        val key = settings.apiKey(id)
            ?: throw AIException(AIErrorType.MISSING_KEY, "Falta a chave de API do ${id.displayName}.")
        return Resolved(provider, settings.model(id), key)
    }

    private fun buildSystemPrompt(): String {
        val now = ZonedDateTime.now().format(
            DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy, HH:mm", Locale("pt", "BR"))
        )
        return """
            Você é o J.A.V.E.S., assistente de voz em português do Brasil.
            Respostas curtas e naturais, sem Markdown, sem listas com símbolos, sem emojis e sem links.
            Seja breve, com no máximo três frases, a menos que o operador peça mais detalhes.
            Se não souber algo, diga isso com honestidade.
            Data e hora atuais: $now.
        """.trimIndent()
    }

    private sealed class EnsemblePart {
        data class Ok(val id: ProviderId, val text: String) : EnsemblePart()
        data class Fail(val id: ProviderId, val reason: String) : EnsemblePart()
    }

    private companion object {
        const val MAX_HISTORY_MESSAGES = 20
    }
}
