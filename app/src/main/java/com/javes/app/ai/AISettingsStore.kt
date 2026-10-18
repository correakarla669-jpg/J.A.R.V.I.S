package com.javes.app.ai

import android.content.Context

/**
 * Configurações da IA no aparelho.
 * Chaves locais são criptografadas pelo [SecureKeyStore].
 * [backendProxyUrl]: se preenchida, o app não envia chaves aos provedores —
 * apenas o texto da conversa ao seu servidor.
 */
class AISettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("javes_ai_settings", Context.MODE_PRIVATE)
    private val secure = SecureKeyStore()

    var activeProvider: ProviderId
        get() {
            val saved = prefs.getString(KEY_ACTIVE, null)
            return ProviderId.entries.firstOrNull { it.name == saved } ?: ProviderId.GEMINI
        }
        set(value) {
            prefs.edit().putString(KEY_ACTIVE, value.name).apply()
        }

    var initiativeEnabled: Boolean
        get() = prefs.getBoolean(KEY_INITIATIVE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_INITIATIVE, value).apply()
        }

    /** Consulta Gemini + Groq + OpenRouter (com chave) e combina respostas reais. */
    var ensembleEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENSEMBLE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ENSEMBLE, value).apply()
        }

    /**
     * URL base do backend próprio (ex.: https://meu-servidor.com).
     * Vazio = chaves ficam no aparelho (criptografadas).
     */
    var backendProxyUrl: String
        get() = prefs.getString(KEY_PROXY, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_PROXY, value.trim()).apply()
        }

    fun model(id: ProviderId): String =
        prefs.getString(modelKey(id), null)?.takeIf { it.isNotBlank() } ?: id.defaultModel

    fun setModel(id: ProviderId, model: String) {
        val clean = model.trim()
        if (clean.isEmpty()) {
            prefs.edit().remove(modelKey(id)).apply()
        } else {
            prefs.edit().putString(modelKey(id), clean).apply()
        }
    }

    fun apiKey(id: ProviderId): String? =
        prefs.getString(apiKeyKey(id), null)?.let { secure.decrypt(it) }

    fun hasApiKey(id: ProviderId): Boolean = apiKey(id) != null

    fun setApiKey(id: ProviderId, key: String): Boolean {
        val clean = key.filterNot { it.isWhitespace() }
        if (clean.isEmpty()) return false
        val encrypted = secure.encrypt(clean) ?: return false
        prefs.edit().putString(apiKeyKey(id), encrypted).apply()
        return true
    }

    fun clearApiKey(id: ProviderId) {
        prefs.edit().remove(apiKeyKey(id)).apply()
    }

    private fun modelKey(id: ProviderId) = "model_${id.name}"
    private fun apiKeyKey(id: ProviderId) = "key_${id.name}"

    private companion object {
        const val KEY_ACTIVE = "active_provider"
        const val KEY_INITIATIVE = "initiative_enabled"
        const val KEY_ENSEMBLE = "ensemble_enabled"
        const val KEY_PROXY = "backend_proxy_url"
    }
}
