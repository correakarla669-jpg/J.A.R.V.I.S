package com.javes.app.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javes.app.ai.AIException
import com.javes.app.ai.AIProviderManager
import com.javes.app.ai.AISettingsStore
import com.javes.app.ai.ProviderId
import com.javes.app.assistant.AiAssistantBrain
import com.javes.app.assistant.AssistantAction
import com.javes.app.assistant.AssistantBrain
import com.javes.app.assistant.AssistantReply
import com.javes.app.assistant.LocalCommandBrain
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.javes.app.voice.HotwordListener
import com.javes.app.voice.ListenError
import com.javes.app.voice.VoiceListener
import com.javes.app.voice.VoiceSpeaker
import com.javes.app.device.DeviceController
import com.javes.app.service.JavesPrefs
import com.javes.app.service.JavesService
import java.util.Calendar
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Coordena escuta, comandos locais, IA e fala, e expõe o estado visual do J.A.V.E.S.
 * Todas as funções devem ser chamadas na thread principal (a UI já faz isso).
 *
 * Fluxo: fala (VoiceListener) ou texto -> AiAssistantBrain (comando local primeiro,
 * senão provedor de IA ativo) -> resposta na tela -> VoiceSpeaker.
 */
class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private enum class InputSource { VOICE, TEXT }

    private val settingsStore = AISettingsStore(application)
    private val aiManager = AIProviderManager(settingsStore)
    private val device = DeviceController(application)
    private val brain: AssistantBrain = AiAssistantBrain(
        local = LocalCommandBrain(),
        ai = aiManager,
        isOnline = { JavesService.isNetworkAvailable(application) }
    )

    private val _state = MutableStateFlow(JarvisState.IDLE)
    val state: StateFlow<JarvisState> = _state.asStateFlow()

    private val _conversation = MutableStateFlow(ConversationUi())
    val conversation: StateFlow<ConversationUi> = _conversation.asStateFlow()

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _aiSettings = MutableStateFlow(buildAiSettings())
    val aiSettings: StateFlow<AiSettingsUi> = _aiSettings.asStateFlow()

    private var processingJob: Job? = null
    private var errorResetJob: Job? = null
    private var testJob: Job? = null
    private var initiativeJob: Job? = null

    /** Depois da fala atual, não volta a ouvir sozinho (modo de espera, erro, cumprimento). */
    private var skipRelistenAfterSpeech = false

    // Controle do modo de iniciativa (só vale com o app aberto)
    private var foreground = false
    private var lastInteractionAt = SystemClock.elapsedRealtime()
    private var lastGreetingAt: Long? = null
    private var lastOfferAt: Long? = null
    private var offersThisSession = 0
    private var offerIndex = 0

    private val speaker = VoiceSpeaker(
        context = application,
        onFinished = { onSpeechFinished() },
        onUnavailable = { message -> showNotice(message) },
        onVoiceInfo = { message -> showNotice(message) }
    )

    /**
     * Ativação por voz com o app aberto: escuta contínua por "JAVES, inicia".
     * Só roda em primeiro plano e com o estado em IDLE — nunca compete com o
     * botão OUVIR, com a conversa contínua ou com a fala.
     */
    private val hotword = HotwordListener(
        context = application,
        onTriggered = {
            if (!foreground) return@HotwordListener
            val granted = ContextCompat.checkSelfPermission(
                getApplication(), Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                setVoiceActivation(false)
                showNotice("Ativei a escuta, mas falta a permissão do microfone.")
                return@HotwordListener
            }
            if (_state.value == JarvisState.IDLE) startListening()
        },
        onError = { message -> showNotice(message) }
    )

    private val listener = VoiceListener(
        context = application,
        onPartial = { text -> _conversation.update { it.copy(partial = text) } },
        onResult = { text -> onHeard(text) },
        onFailure = { error -> onListenError(error) },
        onLevel = { level -> _micLevel.value = level }
    )

    // ---------- ações da interface: conversa ----------

    /** Apenas para o "modo de teste" visual. */
    fun setState(newState: JarvisState) {
        _state.value = newState
    }

    fun startListening() {
        // Falar e ouvir ao mesmo tempo faria o JAVES ouvir a si mesmo.
        hotword.stop()
        speaker.stop()
        processingJob?.cancel()
        errorResetJob?.cancel()
        skipRelistenAfterSpeech = false
        touch()

        if (!listener.isAvailable()) {
            _state.value = JarvisState.IDLE
            showNotice(
                "O reconhecimento de voz não está disponível neste aparelho. " +
                    "Instale ou ative o app Google (ou outro serviço de voz) e tente de novo."
            )
            return
        }
        _conversation.update { it.copy(partial = "", notice = null) }
        if (listener.start()) {
            _state.value = JarvisState.LISTENING
        } else {
            _state.value = JarvisState.IDLE
            showNotice("Não consegui iniciar a escuta. Tente novamente.")
        }
    }

    fun cancelListening() {
        listener.cancel()
        _state.value = JarvisState.IDLE
        _conversation.update { it.copy(partial = "", notice = "Escuta cancelada.") }
        maybeStartHotword()
    }

    /** Envia uma mensagem digitada (mesmo fluxo da voz). */
    fun sendText(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        speaker.stop()
        listener.cancel()
        touch()
        process(clean, InputSource.TEXT)
    }

    /** Cancela um pedido em andamento (inclusive a requisição de rede à IA). */
    fun cancelProcessing() {
        processingJob?.cancel()
        if (_state.value == JarvisState.PROCESSING) _state.value = JarvisState.IDLE
        _conversation.update { it.copy(notice = "Pedido cancelado.") }
        maybeStartHotword()
    }

    fun stopSpeaking() {
        speaker.stop()
        skipRelistenAfterSpeech = false
        if (_state.value == JarvisState.SPEAKING) _state.value = JarvisState.IDLE
        maybeStartHotword()
    }

    fun setAutoConversation(enabled: Boolean) {
        _conversation.update { it.copy(autoConversation = enabled) }
    }

    fun onMicPermissionDenied() {
        _state.value = JarvisState.IDLE
        showNotice("Sem a permissão do microfone eu não consigo ouvir você.")
    }

    // ---------- ações da interface: configuração da IA ----------

    fun selectProvider(id: ProviderId) {
        aiManager.selectProvider(id)
        refreshAiSettings()
    }

    fun saveModel(model: String) {
        settingsStore.setModel(aiManager.activeProviderId, model)
        refreshAiSettings()
    }

    fun saveApiKey(key: String) {
        val id = aiManager.activeProviderId
        _aiSettings.value = when {
            key.isBlank() -> buildAiSettings(testStatus = "Cole a chave antes de salvar.")
            !settingsStore.setApiKey(id, key) ->
                buildAiSettings(testStatus = "Não consegui guardar a chave com segurança neste aparelho.")
            else -> buildAiSettings(testStatus = "Chave salva neste aparelho.")
        }
    }

    fun clearApiKey() {
        settingsStore.clearApiKey(aiManager.activeProviderId)
        _aiSettings.value = buildAiSettings(testStatus = "Chave apagada.")
    }

    fun clearContext() {
        aiManager.clearHistory()
        _aiSettings.value = buildAiSettings(testStatus = "Contexto da conversa apagado.")
    }

    fun testConnection() {
        testJob?.cancel()
        _aiSettings.value = buildAiSettings(testStatus = "Testando conexão…", testing = true)
        testJob = viewModelScope.launch {
            val status = try {
                aiManager.test()
                "Conexão com ${aiManager.activeProviderId.displayName} funcionando."
            } catch (e: CancellationException) {
                throw e
            } catch (e: AIException) {
                e.message ?: "Falha no teste."
            } catch (e: Exception) {
                "Falha inesperada no teste."
            }
            _aiSettings.value = buildAiSettings(testStatus = status)
        }
    }

    /** Liga/desliga o modo 24h (serviço sempre ouvindo, mesmo com o app fechado). */
    fun setAlwaysOn(enabled: Boolean) {
        JavesPrefs.setAlwaysOn(getApplication(), enabled)
        if (enabled) JavesService.start(getApplication()) else JavesService.stop(getApplication())
        refreshAiSettings()
    }

    /** Liga/desliga a ativação por voz ("JAVES, inicia") com o app aberto. */
    fun setVoiceActivation(enabled: Boolean) {
        JavesPrefs.setVoiceActivation(getApplication(), enabled)
        refreshAiSettings()
        if (enabled) {
            showNotice("Ativação por voz ativa. Diga \"JAVES, inicia\" para me chamar.")
            maybeStartHotword()
        } else {
            hotword.stop()
        }
    }

    /** Retoma a escuta da frase de ativação quando o app está ocioso em primeiro plano. */
    private fun maybeStartHotword() {
        val enabled = JavesPrefs.voiceActivation(getApplication())
        if (!enabled || !foreground || _state.value != JarvisState.IDLE) {
            hotword.stop()
            return
        }
        hotword.start()
    }

    fun setEnsemble(enabled: Boolean) {
        settingsStore.ensembleEnabled = enabled
        refreshAiSettings()
    }

    fun saveBackendProxyUrl(url: String) {
        settingsStore.backendProxyUrl = url.trim()
        refreshAiSettings()
    }

    fun setInitiative(enabled: Boolean) {
        settingsStore.initiativeEnabled = enabled
        refreshAiSettings()
        if (enabled && foreground) startInitiativeLoop() else initiativeJob?.cancel()
    }

    // ---------- ciclo de vida do app ----------

    fun onAppForegrounded() {
        foreground = true
        maybeStartHotword()
        if (!settingsStore.initiativeEnabled) return
        maybeGreet()
        startInitiativeLoop()
    }

    /** O app saiu da tela: encerra microfone, fala e iniciativa (privacidade e bateria). */
    fun onAppBackgrounded() {
        foreground = false
        hotword.stop()
        initiativeJob?.cancel()
        listener.cancel()
        speaker.stop()
        processingJob?.cancel()
        errorResetJob?.cancel()
        skipRelistenAfterSpeech = false
        _micLevel.value = 0f
        _state.value = JarvisState.IDLE
    }

    // ---------- fluxo interno ----------

    private fun onHeard(text: String) {
        touch()
        process(text, InputSource.VOICE)
    }

    private fun process(text: String, source: InputSource) {
        hotword.stop()
        errorResetJob?.cancel()
        skipRelistenAfterSpeech = false
        _micLevel.value = 0f
        _state.value = JarvisState.PROCESSING
        _conversation.update { it.copy(heard = text, partial = "", reply = "", notice = null) }

        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            val reply = try {
                brain.respond(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val offline = !JavesService.isNetworkAvailable(getApplication())
                AssistantReply(
                    if (offline) "Sem conexão de rede. Comandos locais continuam disponíveis."
                    else "Tive um problema ao processar esse pedido.",
                    isError = true
                )
            }
            handleReply(reply, source, SystemClock.elapsedRealtime() - startedAt)
        }
    }

    private fun handleReply(reply: AssistantReply, source: InputSource, tookMs: Long) {
        if (reply.isError) {
            _conversation.update { it.copy(notice = reply.text) }
            val offlineHint = reply.text.contains("rede", ignoreCase = true) ||
                reply.text.contains("conexão", ignoreCase = true) ||
                reply.text.contains("internet", ignoreCase = true) ||
                reply.text.contains("offline", ignoreCase = true)
            if (offlineHint) {
                _state.value = JarvisState.OFFLINE
                scheduleReturnToIdle(5000)
            }
            if (source == InputSource.VOICE) {
                skipRelistenAfterSpeech = true
                startSpeaking(reply.text)
            } else {
                if (!offlineHint) showErrorState()
            }
            return
        }

        var text = reply.text
        // Não prefixar "Pronto/Concluído": só o texto da ação real (DeviceController)
        // ou da IA é falado. O modo iniciativa não inventa confirmação de sucesso.
        _conversation.update { it.copy(reply = text) }

        // Modo TEMPO se a resposta local for horário/data (sem DeviceAction)
        if (reply.deviceAction == null && !reply.isError) {
            val lower = text.lowercase()
            if (lower.startsWith("são ") || lower.startsWith("é ") || lower.startsWith("hoje é") ||
                "horas" in lower && ("minuto" in lower || "em ponto" in lower)) {
                _state.value = JarvisState.TIME
                scheduleReturnToIdle(5000)
            }
        }

        when (reply.action) {
            AssistantAction.STOP_SPEAKING -> speaker.stop()
            AssistantAction.STANDBY -> {
                skipRelistenAfterSpeech = true
                _conversation.update { it.copy(autoConversation = false) }
            }
            AssistantAction.NONE -> Unit
        }

        // Executa a ação no aparelho (abrir app, ligar, alarme, lanterna…)
        val actionFeedback = reply.deviceAction?.let { device.execute(it) }
        if (actionFeedback != null && actionFeedback.isNotBlank()) {
            // Prefer feedback real do DeviceController quando existir
            if (text == "Ok." || text.startsWith("Abrindo") || text.startsWith("Iniciando") ||
                text.startsWith("Pausando") || text.startsWith("Retomando") ||
                text.startsWith("Avançando") || text.startsWith("Voltando") ||
                text.startsWith("Parando") || text.startsWith("Consultando")) {
                text = actionFeedback
                _conversation.update { it.copy(reply = text) }
            }
        }

        // Modo visual alinhado à ação real
        applyVisualMode(reply.deviceAction, reply.isError)

        if (!reply.speak) {
            // Mantém MUSIC/TIME por alguns segundos se aplicável; senão IDLE
            if (_state.value !in setOf(JarvisState.MUSIC, JarvisState.TIME, JarvisState.LOCATION, JarvisState.ERROR, JarvisState.OFFLINE)) {
                _state.value = JarvisState.IDLE
            }
            return
        }
        startSpeaking(text)
    }

    private fun startSpeaking(text: String) {
        _state.value = JarvisState.SPEAKING
        if (!speaker.speak(text)) {
            _state.value = JarvisState.IDLE
            _conversation.update {
                it.copy(notice = it.notice ?: "Não consegui falar em voz alta. A resposta está na tela.")
            }
        }
    }

    /**
     * Ajusta o estado visual conforme a ação real do dispositivo ou falha.
     * Não inventa modo: só muda quando há evidência (DeviceAction / erro / rede).
     */
    private fun applyVisualMode(action: com.javes.app.device.DeviceAction?, isError: Boolean) {
        if (isError) {
            showErrorState()
            return
        }
        if (action == com.javes.app.device.DeviceAction.LocationQuery) {
            _state.value = JarvisState.LOCATION
            scheduleReturnToIdle(6000)
            return
        }
        val isMusic = action is com.javes.app.device.DeviceAction.PlayMusic ||
            action == com.javes.app.device.DeviceAction.MediaToggle ||
            action == com.javes.app.device.DeviceAction.MediaPlay ||
            action == com.javes.app.device.DeviceAction.MediaPause ||
            action == com.javes.app.device.DeviceAction.MediaStop ||
            action == com.javes.app.device.DeviceAction.MediaNext ||
            action == com.javes.app.device.DeviceAction.MediaPrevious ||
            action == com.javes.app.device.DeviceAction.MediaNowPlaying
        if (isMusic) {
            _state.value = JarvisState.MUSIC
            scheduleReturnToIdle(4500)
        }
    }

    private var visualResetJob: kotlinx.coroutines.Job? = null
    private fun scheduleReturnToIdle(ms: Long) {
        visualResetJob?.cancel()
        visualResetJob = viewModelScope.launch {
            kotlinx.coroutines.delay(ms)
            if (_state.value in setOf(JarvisState.MUSIC, JarvisState.TIME, JarvisState.LOCATION, JarvisState.OFFLINE)) {
                if (_state.value != JarvisState.SPEAKING && _state.value != JarvisState.LISTENING && _state.value != JarvisState.PROCESSING) {
                    _state.value = JarvisState.IDLE
                }
            }
        }
    }

    private fun showErrorState() {

        _state.value = JarvisState.ERROR
        errorResetJob?.cancel()
        errorResetJob = viewModelScope.launch {
            delay(ERROR_STATE_MS)
            if (_state.value == JarvisState.ERROR) _state.value = JarvisState.IDLE
        }
    }

    private fun onSpeechFinished() {
        touch()
        _state.value = JarvisState.IDLE
        if (skipRelistenAfterSpeech) {
            skipRelistenAfterSpeech = false
            maybeStartHotword()
            return
        }
        if (_conversation.value.autoConversation) startListening() else maybeStartHotword()
    }

    private fun onListenError(error: ListenError) {
        _state.value = JarvisState.IDLE
        val auto = _conversation.value.autoConversation
        val message = when (error) {
            ListenError.NO_SPEECH ->
                if (auto) "Escuta encerrada por inatividade. Toque em OUVIR para continuar."
                else "Não ouvi nenhuma fala. Toque em OUVIR e tente de novo."
            ListenError.NETWORK ->
                "O serviço de reconhecimento de voz precisou de internet e não conseguiu conexão."
            ListenError.AUDIO -> "Houve um problema com o microfone."
            ListenError.BUSY -> "O reconhecimento de voz está ocupado. Tente de novo em instantes."
            ListenError.PERMISSION -> "Falta a permissão do microfone."
            ListenError.LANGUAGE -> "O português do Brasil não está disponível no reconhecimento de voz."
            ListenError.SERVICE -> "O serviço de reconhecimento de voz falhou. Tente novamente."
            ListenError.OTHER -> "Erro desconhecido no reconhecimento de voz."
        }
        _conversation.update { it.copy(partial = "", notice = message) }
        maybeStartHotword()
    }

    private fun showNotice(message: String) {
        _conversation.update { it.copy(notice = message) }
    }

    // ---------- modo de iniciativa ----------

    private fun touch() {
        lastInteractionAt = SystemClock.elapsedRealtime()
    }

    private fun maybeGreet() {
        val now = SystemClock.elapsedRealtime()
        val last = lastGreetingAt
        if (last != null && now - last < GREETING_COOLDOWN_MS) return
        if (_state.value != JarvisState.IDLE) return
        lastGreetingAt = now
        speakProactive(greetingText())
    }

    private fun greetingText(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val salute = when (hour) {
            in 5..11 -> "Bom dia"
            in 12..17 -> "Boa tarde"
            else -> "Boa noite"
        }
        return "$salute, operador. J.A.V.E.S. online. Como posso ajudar?"
    }

    private fun startInitiativeLoop() {
        initiativeJob?.cancel()
        initiativeJob = viewModelScope.launch {
            while (true) {
                delay(INITIATIVE_TICK_MS)
                maybeOfferHelp()
            }
        }
    }

    /** No máximo [MAX_OFFERS_PER_SESSION] ofertas, com intervalo mínimo e frases que se alternam. */
    private fun maybeOfferHelp() {
        if (!foreground || !settingsStore.initiativeEnabled) return
        if (_state.value != JarvisState.IDLE) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastInteractionAt < IDLE_BEFORE_OFFER_MS) return
        if (offersThisSession >= MAX_OFFERS_PER_SESSION) return
        val last = lastOfferAt
        if (last != null && now - last < OFFER_COOLDOWN_MS) return

        lastOfferAt = now
        offersThisSession++
        speakProactive(OFFER_PHRASES[offerIndex++ % OFFER_PHRASES.size])
    }

    /** Fala por iniciativa própria. Nunca abre o microfone. */
    private fun speakProactive(text: String) {
        skipRelistenAfterSpeech = true
        _conversation.update { it.copy(reply = text, notice = null) }
        startSpeaking(text)
    }

    // ---------- configuração ----------

    private fun buildAiSettings(testStatus: String? = null, testing: Boolean = false): AiSettingsUi {
        val active = aiManager.activeProviderId
        return AiSettingsUi(
            activeProvider = active,
            options = ProviderId.entries.map {
                ProviderOption(it, aiManager.isAvailable(it), aiManager.isConfigured(it))
            },
            model = settingsStore.model(active),
            hasKey = settingsStore.hasApiKey(active) || settingsStore.backendProxyUrl.isNotBlank(),
            initiativeEnabled = settingsStore.initiativeEnabled,
            ensembleEnabled = settingsStore.ensembleEnabled,
            backendProxyUrl = settingsStore.backendProxyUrl,
            alwaysOn = JavesPrefs.alwaysOn(getApplication()),
            voiceActivationEnabled = JavesPrefs.voiceActivation(getApplication()),
            testStatus = testStatus,
            testing = testing
        )
    }

    private fun refreshAiSettings() {
        _aiSettings.value = buildAiSettings()
    }

    override fun onCleared() {
        initiativeJob?.cancel()
        hotword.stop()
        listener.destroy()
        speaker.shutdown()
        super.onCleared()
    }

    private companion object {
        const val LONG_TASK_MS = 5_000L
        const val ERROR_STATE_MS = 2_500L
        const val INITIATIVE_TICK_MS = 60_000L
        const val IDLE_BEFORE_OFFER_MS = 5 * 60_000L
        const val OFFER_COOLDOWN_MS = 15 * 60_000L
        const val GREETING_COOLDOWN_MS = 30 * 60_000L
        const val MAX_OFFERS_PER_SESSION = 3

        val OFFER_PHRASES = listOf(
            "Posso ajudar com alguma coisa?",
            "Se precisar de algo, é só me chamar.",
            "Estou por aqui. Quer perguntar alguma coisa?"
        )
    }
}
