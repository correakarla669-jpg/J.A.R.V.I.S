package com.javes.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Reinicia o modo 24h quando o aparelho liga, se ele estava ativo.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!JavesPrefs.alwaysOn(context)) return
        try {
            JavesService.start(context.applicationContext)
        } catch (e: Exception) {
            // Alguns Androids (12+) restringem iniciar serviço com microfone no boot;
            // o usuário abre o app uma vez e o serviço sobe normalmente.
        }
    }
}

/** Preferência do modo 24h, compartilhada entre o app, o serviço e o boot. */
object JavesPrefs {
    private const val PREFS = "javes_prefs"
    private const val KEY_ALWAYS_ON = "always_on"
    private const val KEY_VOICE_ACTIVATION = "voice_activation"

    fun alwaysOn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALWAYS_ON, false)

    fun setAlwaysOn(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ALWAYS_ON, enabled).apply()
    }

    /** Ativação por voz com o app aberto: "JAVES, inicia". */
    fun voiceActivation(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_VOICE_ACTIVATION, false)

    fun setVoiceActivation(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_VOICE_ACTIVATION, enabled).apply()
    }
}
