package com.javes.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Fala usando o TextToSpeech nativo do Android, em português do Brasil.
 * Todos os callbacks voltam na thread principal.
 */
class VoiceSpeaker(
    context: Context,
    /** Chamado quando uma fala termina por conta própria (ou dá erro). Não é chamado após [stop]. */
    private val onFinished: () -> Unit,
    /** Chamado se o TextToSpeech não puder ser usado em português. */
    private val onUnavailable: (String) -> Unit,
    /**
     * Chamado uma vez se não houver voz masculina em português no dispositivo.
     * O app continua falando com a voz mais próxima possível.
     */
    private val onVoiceInfo: (String) -> Unit = {}
) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var failed = false
    private var pendingText: String? = null
    private var activeId: String? = null
    private var counter = 0

    init {
        tts = try {
            TextToSpeech(context.applicationContext) { status -> onInit(status) }
        } catch (e: Exception) {
            failed = true
            main.post { onUnavailable("Não foi possível iniciar a voz do Android (TextToSpeech).") }
            null
        }
    }

    private fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            failed = true
            onUnavailable("Não foi possível iniciar a voz do Android (TextToSpeech).")
            return
        }
        val result = engine.setLanguage(Locale("pt", "BR"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            failed = true
            pendingText = null
            onUnavailable(
                "A voz em português do Brasil não está disponível neste aparelho. " +
                    "Instale-a em Configurações > Idioma > Saída de texto para fala."
            )
            return
        }
        // Escolhe a voz mais próxima do perfil "masculina, grave, pt-BR".
        val maleVoice = selectMalePtVoice(engine)
        maleVoice?.let { v ->
            runCatching { engine.voice = v }
        }
        // Com voz masculina real, quase nenhum ajuste; sem ela, desce o tom
        // para aproximar o timbre de algo mais grave e tecnológico.
        engine.setPitch(if (maleVoice != null) 0.92f else 0.78f)
        engine.setSpeechRate(0.95f)
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finish(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)
        })
        ready = true
        pendingText?.let {
            pendingText = null
            speak(it)
        }
    }

    /**
     * Procura uma voz masculina em português (pt-BR preferido) entre as vozes
     * instaladas no dispositivo. Retorna null se não encontrar nenhuma voz
     * masculina (o chamador ajusta o tom e avisa o usuário).
     */
    private fun selectMalePtVoice(engine: TextToSpeech): android.speech.tts.Voice? {
        val voices = runCatching { engine.voices }.getOrNull() ?: return null
        val ptVoices = voices.filter { v ->
            v.locale.language.equals("pt", ignoreCase = true)
        }
        val target = ptVoices.filter {
            it.locale.country.equals("BR", ignoreCase = true)
        }.ifEmpty { ptVoices }
        val male = target.firstOrNull { v ->
            val n = v.name.lowercase()
            n.contains("male") || n.contains("masculin") || n.contains("#male")
        }
        if (male == null) {
            onVoiceInfo(
                "Não encontrei uma voz masculina em português neste aparelho. " +
                    "Vou falar com a voz mais próxima e mais grave possível. " +
                    "Para o efeito completo, instale uma voz masculina em " +
                    "Configurações > Acessibilidade > Saída de texto para fala " +
                    "(ex.: pacote de voz do Google em português do Brasil)."
            )
        }
        return male
    }

    private fun finish(utteranceId: String?) {
        main.post {
            // Ignora falas antigas ou interrompidas por stop()
            if (utteranceId != null && utteranceId == activeId) {
                activeId = null
                onFinished()
            }
        }
    }

    /** Retorna false se a voz estiver indisponível (a resposta deve ficar só na tela). */
    fun speak(text: String): Boolean {
        val engine = tts ?: return false
        if (failed) return false
        val spoken = prepareForSpeech(text)
        if (spoken.isBlank()) return false
        if (!ready) {
            pendingText = spoken // motor ainda iniciando
            return true
        }
        val id = "javes-${++counter}"
        activeId = id
        val result = engine.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, id)
        if (result != TextToSpeech.SUCCESS) {
            activeId = null
            return false
        }
        return true
    }

    /** Remove símbolos de Markdown e links (respostas de IA) e respeita o limite do motor de voz. */
    private fun prepareForSpeech(text: String): String {
        var s = text
        s = s.replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "\$1")
        s = s.replace(Regex("https?://\\S+"), "")
        s = s.replace(Regex("[*`#>~|]"), "")
        s = s.replace(Regex("\\s+"), " ").trim()
        val max = TextToSpeech.getMaxSpeechInputLength() - 1
        return if (s.length > max) s.take(max) else s
    }

    /** Interrompe a fala atual sem disparar [onFinished]. */
    fun stop() {
        pendingText = null
        activeId = null
        tts?.stop()
    }

    /** Libera o motor de voz. */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
