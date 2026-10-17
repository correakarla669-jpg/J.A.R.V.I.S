package com.javes.app.ui

/**
 * Estados visuais do assistente.
 * Refletem a atividade real (voz, processamento, dispositivo), não só palavras.
 */
enum class JarvisState(val label: String) {
    IDLE("AGUARDANDO"),
    LISTENING("OUVINDO"),
    PROCESSING("PROCESSANDO"),
    SPEAKING("FALANDO"),
    MUSIC("MÚSICA"),
    TIME("TEMPO"),
    LOCATION("LOCAL"),
    ERROR("ERRO"),
    OFFLINE("OFFLINE")
}
