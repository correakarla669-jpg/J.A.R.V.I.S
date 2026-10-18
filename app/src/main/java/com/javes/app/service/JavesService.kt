package com.javes.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.javes.app.MainActivity
import com.javes.app.assistant.AiAssistantBrain
import com.javes.app.assistant.AssistantReply
import com.javes.app.ai.AIProviderManager
import com.javes.app.ai.AISettingsStore
import com.javes.app.device.DeviceController
import com.javes.app.voice.HotwordListener
import com.javes.app.voice.VoiceSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Modo 24 horas: o J.A.V.E.S. fica ouvindo o tempo todo, mesmo com a tela desligada
 * ou com outro app aberto. Ele espera a frase de ativação ("JAVES, inicia") e,
 * ao ouvi-la, escuta o comando, executa no aparelho e responde por voz.
 *
 * Tudo roda em comando local (offline). Se houver internet e chave configurada,
 * perguntas gerais vão para a IA também.
 */
class JavesService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var recognizer: SpeechRecognizer? = null
    private var listeningForCommand = false
    private var running = false

    private lateinit var brain: AiAssistantBrain
    private lateinit var device: DeviceController
    private lateinit var speaker: VoiceSpeaker

    override fun onCreate() {
        super.onCreate()
        val appContext = applicationContext
        val settings = AISettingsStore(appContext)
        device = DeviceController(appContext)
        brain = AiAssistantBrain(
            local = com.javes.app.assistant.LocalCommandBrain(),
            ai = AIProviderManager(settings),
            isOnline = { isNetworkAvailable(appContext) }
        )
        speaker = VoiceSpeaker(
            context = appContext,
            onFinished = { mainHandler.postDelayed({ if (running) startHotwordLoop() }, 400) },
            onUnavailable = { }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!running) {
            running = true
            isRunning = true
            startHotwordLoop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        isRunning = false
        recognizer?.destroy()
        recognizer = null
        speaker.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- reconhecimento ----------

    /** Escuta contínua: em cada palavra parcial, procura a palavra de ativação. */
    private fun startHotwordLoop() {
        if (!running) return
        listeningForCommand = false
        startRecognizer(hotwordListener)
    }

    /** Escuta o comando de fato, depois da palavra de ativação. */
    private fun startCommandListen() {
        listeningForCommand = true
        startRecognizer(commandListener)
    }

    private fun startRecognizer(listener: RecognitionListener) {
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
        }
        val r = try {
            SpeechRecognizer.createSpeechRecognizer(applicationContext).also {
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

    private fun buildIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
    }

    private fun scheduleRestart() {
        mainHandler.postDelayed({ if (running) startHotwordLoop() }, RESTART_DELAY_MS)
    }

    private fun onHotwordHeard() {
        try {
            recognizer?.cancel()
        } catch (e: Exception) {
        }
        speaker.stop()
        speaker.speak("Javes iniciado. Pode falar.")
        startCommandListen()
    }

    private fun onCommand(text: String) {
        scope.launch {
            // 1) Interpreta o comando (local primeiro; IA se houver internet)
            val reply: AssistantReply = try {
                brain.respond(text)
            } catch (e: Exception) {
                AssistantReply("Tive um problema ao processar.", isError = true)
            }
            // 2) Executa ação no aparelho (se o comando trouxer uma)
            reply.deviceAction?.let { device.execute(it) }
            // 3) Responde por voz; quando terminar, onFinished retoma a escuta
            if (reply.speak && reply.text.isNotBlank() && speaker.speak(reply.text)) {
                // aguardando onFinished
            } else {
                startHotwordLoop()
            }
        }
    }

    // ---------- listeners ----------

    private val hotwordListener = object : RecognitionListener {
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
            if (HotwordListener.isActivationPhrase(text)) onHotwordHeard()
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (text != null && HotwordListener.isActivationPhrase(text)) {
                onHotwordHeard()
            } else {
                scheduleRestart()
            }
        }

        override fun onError(error: Int) {
            // Erros comuns (sem fala, timeout) simplesmente reiniciam a escuta.
            scheduleRestart()
        }
    }

    private val commandListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { it.isNotBlank() }
            if (text.isNullOrBlank()) {
                speakAndRelisten("Não entendi. Pode repetir?")
            } else {
                onCommand(text)
            }
        }

        override fun onError(error: Int) {
            speakAndRelisten("Não entendi. Pode repetir?")
        }
    }

    private fun speakAndRelisten(text: String) {
        if (speaker.speak(text)) {
            // onFinished retoma a escuta
        } else {
            startHotwordLoop()
        }
    }

    // ---------- notificação ----------

    private fun buildNotification(): Notification {
        createChannel()
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, JavesService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("J.A.V.E.S. ouvindo")
            .setContentText("Diga \"JAVES, inicia\" para me chamar.")
            .setContentIntent(openIntent)
            .addAction(0, "Parar", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "J.A.V.E.S. 24h",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Mantém o J.A.V.E.S. ouvindo o tempo todo" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "javes_always_on"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "com.javes.app.service.STOP"
        private const val RESTART_DELAY_MS = 1_200L

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, JavesService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, JavesService::class.java).setAction(ACTION_STOP))
        }

        fun isNetworkAvailable(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}
