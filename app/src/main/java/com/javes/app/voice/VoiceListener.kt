package com.javes.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

enum class ListenError {
    NO_SPEECH,      // nada foi dito / não entendeu
    NETWORK,        // serviço de reconhecimento precisou de internet
    AUDIO,          // problema no microfone
    BUSY,           // reconhecedor ocupado
    PERMISSION,     // sem permissão de microfone
    LANGUAGE,       // português do Brasil indisponível
    SERVICE,        // serviço de reconhecimento falhou ou desconectou
    OTHER
}

/**
 * Escuta usando o SpeechRecognizer nativo, em português do Brasil.
 * Deve ser usado somente na thread principal.
 */
class VoiceListener(
    context: Context,
    private val onPartial: (String) -> Unit,
    private val onResult: (String) -> Unit,
    private val onFailure: (ListenError) -> Unit,
    /** Nível do microfone de 0 a 1 (para animar as ondas). */
    private val onLevel: (Float) -> Unit
) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var active = false

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    /** Retorna false se não foi possível iniciar a escuta. */
    fun start(): Boolean {
        if (!isAvailable()) return false
        val r = recognizer ?: createRecognizer() ?: return false
        recognizer = r
        active = true
        return try {
            r.startListening(buildIntent())
            true
        } catch (e: Exception) {
            active = false
            false
        }
    }

    /** Cancela a escuta atual sem entregar resultado. */
    fun cancel() {
        active = false
        recognizer?.cancel()
        onLevel(0f)
    }

    fun destroy() {
        active = false
        recognizer?.destroy()
        recognizer = null
    }

    private fun createRecognizer(): SpeechRecognizer? = try {
        SpeechRecognizer.createSpeechRecognizer(appContext).also {
            it.setRecognitionListener(listener)
        }
    } catch (e: Exception) {
        null
    }

    private fun buildIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onEndOfSpeech() {
            if (active) onLevel(0f)
        }

        override fun onRmsChanged(rmsdB: Float) {
            if (active) onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!active) return
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) onPartial(text)
        }

        override fun onResults(results: Bundle?) {
            if (!active) return
            active = false
            onLevel(0f)
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { it.isNotBlank() }
            if (text == null) onFailure(ListenError.NO_SPEECH) else onResult(text)
        }

        override fun onError(error: Int) {
            if (!active) return
            active = false
            onLevel(0f)
            onFailure(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> ListenError.NO_SPEECH

                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> ListenError.NETWORK

                    SpeechRecognizer.ERROR_AUDIO -> ListenError.AUDIO
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> ListenError.BUSY
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> ListenError.PERMISSION

                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> ListenError.LANGUAGE

                    SpeechRecognizer.ERROR_SERVER,
                    SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
                    SpeechRecognizer.ERROR_CLIENT,
                    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> ListenError.SERVICE

                    else -> ListenError.OTHER
                }
            )
        }
    }
}
