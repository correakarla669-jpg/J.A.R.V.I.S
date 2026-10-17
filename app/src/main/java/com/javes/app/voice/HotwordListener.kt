package com.javes.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.text.Normalizer

/**
 * Escuta contínua em busca exclusiva da frase de ativação "JAVES, inicia".
 *
 * - Não ativa por palavras aleatórias: só dispara quando o nome do assistente
 *   é seguido de "inicia"/"iniciar" (com tolerância a erros comuns de
 *   reconhecimento no nome, como "jarvis"/"javis").
 * - Uma única instância de reconhecimento por vez; em caso de erro comum
 *   (silêncio, timeout) reinicia a escuta sozinho.
 * - Todos os callbacks acontecem na thread principal.
 * - [stop] libera o microfone imediatamente (app em segundo plano, conflito
 *   com o botão OUVIR, fala em andamento etc.).
 */
class HotwordListener(
    context: Context,
    private val onTriggered: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var started = false

    fun isStarted(): Boolean = started

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    /** Começa a escutar. Retorna false se o reconhecimento não existe no aparelho. */
    fun start(): Boolean {
        if (started) return true
        if (!isAvailable()) {
            onError(
                "O reconhecimento de voz não está disponível neste aparelho. " +
                    "Instale ou ative o app Google (ou outro serviço de voz) e tente de novo."
            )
            return false
        }
        started = true
        begin()
        return true
    }

    /** Para de escutar e libera o reconhecimento. */
    fun stop() {
        started = false
        handler.removeCallbacks(restartRunnable)
        destroyRecognizer()
    }

    private fun destroyRecognizer() {
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
        }
        recognizer = null
    }

    private fun begin() {
        if (!started) return
        destroyRecognizer()
        val r = try {
            SpeechRecognizer.createSpeechRecognizer(appContext).also {
                it.setRecognitionListener(listener)
            }
        } catch (e: Exception) {
            scheduleRestart()
            return
        }
        recognizer = r
        try {
            r.startListening(buildIntent())
        } catch (e: Exception) {
            scheduleRestart()
        }
    }

    private fun scheduleRestart() {
        if (!started) return
        handler.removeCallbacks(restartRunnable)
        handler.postDelayed(restartRunnable, RESTART_DELAY_MS)
    }

    private val restartRunnable = Runnable { begin() }

    private fun buildIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
    }

    private fun trigger() {
        if (!started) return
        stop()
        onTriggered()
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?: return
            if (isActivationPhrase(text)) trigger()
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (text != null && isActivationPhrase(text)) {
                trigger()
            } else {
                scheduleRestart()
            }
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    stop()
                    onError("Falta a permissão do microfone para a ativação por voz.")
                }
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                    stop()
                    onError("O português do Brasil não está disponível no reconhecimento de voz.")
                }
                else -> scheduleRestart()
            }
        }
    }

    companion object {
        private const val RESTART_DELAY_MS = 1_200L

        /**
         * Frase de ativação exclusiva: "JAVES, inicia".
         * Normaliza acentos, remove tudo que não é letra e exige o nome do
         * assistente imediatamente seguido de "inicia"/"iniciar" — nunca só o
         * nome, nunca palavras soltas.
         */
        fun isActivationPhrase(raw: String): Boolean {
            val normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replace(Regex("\\p{M}"), "")
                .lowercase()
                .replace(Regex("[^a-z]"), "")
            return ACTIVATION_REGEX.containsMatchIn(normalized)
        }

        // Tolerância a grafias comuns de erro de reconhecimento do nome.
        private val ACTIVATION_REGEX = Regex(
            "(javes|jarvis|jarves|javis|jawes|jawis|chaves)(inicia|iniciar)"
        )
    }
}
