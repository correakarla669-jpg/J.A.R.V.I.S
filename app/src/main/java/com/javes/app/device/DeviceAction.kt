package com.javes.app.device

/**
 * Ações que o J.A.V.E.S. pode executar no aparelho.
 * O cérebro (LocalCommandBrain) devolve uma ação; quem a executa é o
 * DeviceController, que tem acesso ao Context e às permissões.
 */
sealed class DeviceAction {

    /** Abre um app instalado, procurando pelo nome ("whatsapp") ou pela atividade. */
    data class OpenApp(val query: String) : DeviceAction()

    /** Abre o FabTune e (se pedido) dá play na mídia. */
    data class PlayMusic(val query: String?) : DeviceAction()

    object MediaToggle : DeviceAction()   // play/pause no player ativo (FabTune etc.)
    object MediaPlay : DeviceAction()     // play explícito
    object MediaPause : DeviceAction()    // pause explícito
    object MediaStop : DeviceAction()     // stop
    object MediaNext : DeviceAction()
    object MediaPrevious : DeviceAction()
    object MediaNowPlaying : DeviceAction() // tenta informar a faixa atual

    object VolumeUp : DeviceAction()
    object VolumeDown : DeviceAction()
    object VolumeMute : DeviceAction()

    object FlashOn : DeviceAction()
    object FlashOff : DeviceAction()
    object FlashToggle : DeviceAction()

    /** Liga para um número ou para um contato da agenda (nome em pt-BR). */
    data class Call(val target: String) : DeviceAction()

    /** Abre o WhatsApp já com a mensagem pronta para um contato/número. */
    data class WhatsMessage(val target: String, val message: String) : DeviceAction()

    /** Abre o app de SMS com número e mensagem prontos. */
    data class Sms(val target: String, val message: String) : DeviceAction()

    /** Cria um alarme no app de relógio. */
    data class SetAlarm(val hour: Int, val minute: Int, val label: String?) : DeviceAction()

    /** Cria um cronômetro/minutero no app de relógio. */
    data class SetTimer(val minutes: Int) : DeviceAction()

    object WifiToggle : DeviceAction()
    object BluetoothToggle : DeviceAction()
    object OpenSettings : DeviceAction()  // painel de configurações rápidas

    object BatteryStatus : DeviceAction()

    /** Localização atual (requer permissão concedida). */
    object LocationQuery : DeviceAction()

    /** Pesquisa no Google (precisa de internet). */
    data class WebSearch(val query: String) : DeviceAction()

    /** Abre uma URL ou site pelo nome. */
    data class OpenUrl(val url: String) : DeviceAction()
}
