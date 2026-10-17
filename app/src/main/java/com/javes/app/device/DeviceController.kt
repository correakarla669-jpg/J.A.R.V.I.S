package com.javes.app.device

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat

/**
 * Executa as ações do J.A.V.E.S. no aparelho e devolve o texto de confirmação
 * (que será falado). Todas as ações funcionam SEM internet.
 */
class DeviceController(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /** Executa a ação e devolve o texto de feedback para falar na tela. */
    fun execute(action: DeviceAction): String = when (action) {
        is DeviceAction.OpenApp -> openApp(action.query)
        is DeviceAction.PlayMusic -> playMusic(action.query)
        DeviceAction.MediaToggle -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Alternando reprodução.")
        DeviceAction.MediaPlay -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY, "Iniciando a reprodução.")
        DeviceAction.MediaPause -> mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE, "Pausando a música.")
        DeviceAction.MediaStop -> mediaKey(KeyEvent.KEYCODE_MEDIA_STOP, "Parando a música.")
        DeviceAction.MediaNext -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT, "Avançando para a próxima faixa.")
        DeviceAction.MediaPrevious -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Voltando para a faixa anterior.")
        DeviceAction.MediaNowPlaying -> nowPlaying()
        DeviceAction.VolumeUp -> changeVolume(+1)
        DeviceAction.VolumeDown -> changeVolume(-1)
        DeviceAction.VolumeMute -> muteVolume()
        DeviceAction.FlashOn -> setFlash(true)
        DeviceAction.FlashOff -> setFlash(false)
        DeviceAction.FlashToggle -> toggleFlash()
        is DeviceAction.Call -> call(action.target)
        is DeviceAction.WhatsMessage -> whatsMessage(action.target, action.message)
        is DeviceAction.Sms -> sendSms(action.target, action.message)
        is DeviceAction.SetAlarm -> setAlarm(action)
        is DeviceAction.SetTimer -> setTimer(action.minutes)
        DeviceAction.WifiToggle -> openPanel(Settings.ACTION_WIFI_SETTINGS, "Abrindo configurações do Wi-Fi.")
        DeviceAction.BluetoothToggle -> openPanel(Settings.ACTION_BLUETOOTH_SETTINGS, "Abrindo configurações do Bluetooth.")
        DeviceAction.OpenSettings -> openPanel(Settings.ACTION_SETTINGS, "Abrindo as configurações.")
        DeviceAction.BatteryStatus -> batteryStatus()
        DeviceAction.LocationQuery -> locationQuery()
        is DeviceAction.WebSearch -> webSearch(action.query)
        is DeviceAction.OpenUrl -> openUrl(action.url)
    }

    // ---------- apps ----------

    /** Nomes amigáveis -> pacotes conhecidos. O FabTune tem mais de um pacote
     *  dependendo de onde foi instalado, então tentamos todos. */
    private val knownApps = mapOf(
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "fabtune" to listOf("com.fabtune.music.player", "com.music.fabtune", "com.fabtune.innertune.music"),
        "musica" to listOf("com.fabtune.music.player", "com.music.fabtune", "com.fabtune.innertune.music"),
        "youtube" to listOf("com.google.android.youtube"),
        "google" to listOf("com.google.android.googlequicksearchbox"),
        "maps" to listOf("com.google.android.apps.maps", "com.waze"),
        "mapa" to listOf("com.google.android.apps.maps"),
        "camera" to listOf("com.android.camera2", "com.google.android.GoogleCamera", "com.sec.android.app.camera"),
        "galeria" to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d", "com.android.gallery3d"),
        "fotos" to listOf("com.google.android.apps.photos"),
        "gmail" to listOf("com.google.android.gm"),
        "email" to listOf("com.google.android.gm"),
        "chrome" to listOf("com.android.chrome", "com.google.android.apps.chrome", "org.mozilla.firefox"),
        "navegador" to listOf("com.android.chrome", "org.mozilla.firefox"),
        "spotify" to listOf("com.spotify.music"),
        "instagram" to listOf("com.instagram.android"),
        "facebook" to listOf("com.facebook.katana"),
        "tiktok" to listOf("com.zhiliaoapp.musically"),
        "relogio" to listOf("com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock"),
        "calculadora" to listOf("com.google.android.calculator", "com.android.calculator2", "com.sec.android.app.popupcalculator"),
        "configuracoes" to listOf("com.android.settings"),
        "telefone" to listOf("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer"),
        "contatos" to listOf("com.google.android.contacts", "com.android.contacts", "com.samsung.android.app.contacts"),
        "play store" to listOf("com.android.vending"),
        "loja" to listOf("com.android.vending")
    )

    private fun openApp(query: String): String {
        val q = normalize(query)
        if (q.isEmpty()) return "Qual aplicativo devo abrir?"

        // 1) Pacotes conhecidos
        knownApps[q]?.firstOrNull { isInstalled(it) }?.let { return launch(it, prettyName(q)) }

        // 2) Busca entre os apps instalados pelo rótulo
        val match = findInstalledApp(q)
        if (match != null) return launch(match.first, match.second)

        return "Não encontrei o aplicativo $query instalado neste aparelho."
    }

    private fun findInstalledApp(q: String): Pair<String, String>? {
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        var best: Pair<String, String>? = null
        for (ri in apps) {
            val label = ri.loadLabel(pm)?.toString() ?: continue
            val n = normalize(label)
            if (n == q) return ri.activityInfo.packageName to label       // exato
            if (best == null && (n.contains(q) || (q.length >= 4 && q.contains(n)))) {
                best = ri.activityInfo.packageName to label
            }
        }
        return best
    }

    private fun isInstalled(pkg: String): Boolean = try {
        pm.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun launch(pkg: String, label: String): String = try {
        val intent = pm.getLaunchIntentForPackage(pkg)
            ?: return "Não consegui abrir $label."
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        "Abrindo $label."
    } catch (e: Exception) {
        "Não consegui abrir $label."
    }

    private fun prettyName(q: String) = when (q) {
        "fabtune" -> "FabTune"
        "musica" -> "FabTune"
        "whatsapp" -> "WhatsApp"
        "relogio" -> "Relógio"
        "configuracoes" -> "Configurações"
        "play store" -> "Play Store"
        else -> q
    }

    // ---------- música / FabTune ----------

    /** Controle de mídia por botões de mídia funciona com QUALQUER player,
     *  incluindo o FabTune, sem precisar saber o pacote dele. */
    /**
     * Envia tecla de mídia ao player ativo (FabTune, Spotify, etc.).
     * O Android encaminha o evento para a sessão de mídia ativa — não é necessário
     * abrir o aplicativo nem usar APIs privadas do FabTune.
     * Funciona enquanto houver uma sessão de mídia ativa (música tocando ou pausada).
     */
    private fun mediaKey(keyCode: Int, feedback: String = "Ok."): String {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            feedback
        } catch (e: Exception) {
            "Não consegui controlar a mídia. Verifique se o FabTune está reproduzindo uma música."
        }
    }

    /**
     * Tenta obter o nome da faixa atual.
     * Apps comuns não têm permissão MEDIA_CONTENT_CONTROL nem são Notification Listener
     * por padrão, então o Android normalmente não entrega metadados de outros apps.
     */
    private fun nowPlaying(): String {
        return "Não consigo ler o nome da faixa do FabTune sem permissão especial do sistema. " +
            "O controle de play, pausa, próxima e volume funciona normalmente."
    }

    private fun playMusic(query: String?): String {
        val fabtune = knownApps["fabtune"]?.firstOrNull { isInstalled(it) }
        if (fabtune != null) {
            // Abre o FabTune (ou traz para frente) e envia play.
            // Se já houver sessão ativa, o media key controla sem depender da UI.
            try {
                launch(fabtune, "FabTune")
            } catch (_: Exception) { /* continua e tenta a tecla mesmo assim */ }
            mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY, "")
            return if (query.isNullOrBlank()) "Iniciando a reprodução no FabTune."
            else "Abrindo o FabTune. Pesquise por $query dentro dele."
        }
        // Sem FabTune instalado: tenta o player de mídia ativo no sistema
        return mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY, "Iniciando a reprodução no player ativo.")
    }

    // ---------- volume ----------

    private fun changeVolume(direction: Int): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val adj = if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, adj, 0)
        val level = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return "Volume da música em $level de $max."
    }

    private fun muteVolume(): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return if (Build.VERSION.SDK_INT >= 23) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            "Mudo ativado."
        } else {
            @Suppress("DEPRECATION")
            am.setStreamMute(AudioManager.STREAM_MUSIC, true)
            "Mudo ativado."
        }
    }

    // ---------- lanterna ----------

    private var flashOn = false

    private fun setFlash(on: Boolean): String {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            return "Este aparelho não tem flash."
        }
        return try {
            val cam = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val id = cam.cameraIdList.firstOrNull { cid ->
                cam.getCameraCharacteristics(cid)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "Não encontrei o flash."
            cam.setTorchMode(id, on)
            flashOn = on
            if (on) "Lanterna ligada." else "Lanterna desligada."
        } catch (e: Exception) {
            "Não consegui controlar a lanterna."
        }
    }

    private fun toggleFlash(): String = setFlash(!flashOn)

    // ---------- chamadas e mensagens ----------

    private fun call(target: String): String {
        val number = resolveNumber(target)
            ?: return "Não encontrei o número de $target na agenda. Diga o número completo."
        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Ligando para $target."
        } catch (e: SecurityException) {
            // Sem a permissão CALL_PHONE: cai no discador com o número pronto
            dialFallback(number, target)
        } catch (e: ActivityNotFoundException) {
            dialFallback(number, target)
        }
    }

    private fun dialFallback(number: String, target: String): String = try {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        "Abrindo o discador para $target. Toque em ligar."
    } catch (e: Exception) {
        "Não consegui iniciar a ligação."
    }

    /** Procura um contato pelo nome e devolve o número (precisa de READ_CONTACTS). */
    private fun resolveNumber(target: String): String? {
        val digits = target.filter { it.isDigit() }
        if (digits.length >= 8) return digits
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        val q = normalize(target)
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            null, null, null
        ) ?: return null
        cursor.use { c ->
            var fallback: String? = null
            while (c.moveToNext()) {
                val name = normalize(c.getString(1) ?: "")
                if (name == q) return c.getString(0)
                if (fallback == null && (name.contains(q) || (q.length >= 4 && q.contains(name)))) {
                    fallback = c.getString(0)
                }
            }
            return fallback
        }
    }

    private fun whatsMessage(target: String, message: String): String {
        if (knownApps["whatsapp"]?.any { isInstalled(it) } != true) {
            return "O WhatsApp não está instalado neste aparelho."
        }
        val digits = resolveNumber(target)?.filter { it.isDigit() }
        val uri = if (digits != null) {
            Uri.parse("https://api.whatsapp.com/send?phone=$digits&text=${Uri.encode(message)}")
        } else {
            // Sem contato resolvido: abre o WhatsApp para o usuário escolher
            Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(message)}")
        }
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            if (digits != null) "Abrindo o WhatsApp para $target." else "Abrindo o WhatsApp. Escolha o contato."
        } catch (e: Exception) {
            "Não consegui abrir o WhatsApp."
        }
    }

    private fun sendSms(target: String, message: String): String = try {
        val number = resolveNumber(target) ?: target.filter { it.isDigit() }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        "Abrindo o SMS para $target com a mensagem pronta."
    } catch (e: Exception) {
        "Não consegui abrir o app de mensagens."
    }

    // ---------- relógio ----------

    private fun setAlarm(a: DeviceAction.SetAlarm): String = try {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, a.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, a.minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, a.label ?: "J.A.V.E.S.")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        "Alarme criado para as ${a.hour}h${"%02d".format(a.minute)}."
    } catch (e: Exception) {
        "Não encontrei um app de alarmes neste aparelho."
    }

    private fun setTimer(minutes: Int): String = try {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
            putExtra(AlarmClock.EXTRA_MESSAGE, "J.A.V.E.S.")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        "Cronômetro de $minutes minutos criado."
    } catch (e: Exception) {
        "Não encontrei um app de cronômetro neste aparelho."
    }

    // ---------- sistema ----------

    private fun openPanel(action: String, feedback: String): String = try {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        feedback
    } catch (e: Exception) {
        "Não consegui abrir essa configuração."
    }

    private fun batteryStatus(): String {
        val intent: Intent? = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return when {
            pct < 0 -> "Não consegui ler a bateria."
            charging -> "Bateria em $pct por cento, carregando."
            else -> "Bateria em $pct por cento."
        }
    }

    // ---------- internet ----------

    private fun webSearch(query: String): String = try {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        "Pesquisando $query no Google."
    } catch (e: Exception) {
        openUrl("https://www.google.com/search?q=${Uri.encode(query)}")
    }

    private fun openUrl(url: String): String {
        var full = url.trim()
        if (!full.startsWith("http")) full = "https://$full"
        full = full.replace(" ", "")
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(full)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Abrindo $full."
        } catch (e: Exception) {
            "Não consegui abrir o site."
        }
    }

    // ---------- utilidades ----------

    private fun normalize(s: String): String {
        var t = java.text.Normalizer.normalize(s.lowercase(java.util.Locale("pt", "BR")), java.text.Normalizer.Form.NFD)
        t = t.replace(Regex("\\p{Mn}+"), "")
        t = t.replace(Regex("[^a-z0-9 ]"), " ")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    private val locationHelper by lazy { LocationHelper(context) }

    private fun locationQuery(): String = locationHelper.describeLocation()

}
