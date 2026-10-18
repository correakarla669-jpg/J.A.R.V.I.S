package com.javes.app.assistant

import com.javes.app.device.DeviceAction
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Interpretador de comandos locais. Funciona 100% SEM internet.
 * [match] devolve null quando a fala não é um comando local, para que o
 * [AiAssistantBrain] possa encaminhá-la ao provedor de IA.
 *
 * Quando a resposta carrega uma [DeviceAction], quem a executa é o
 * DeviceController (na tela ou no serviço 24h), e depois o texto é falado.
 */
class LocalCommandBrain : AssistantBrain {

    override suspend fun respond(input: String): AssistantReply =
        match(input) ?: AssistantReply(FALLBACK_TEXT)

    fun match(input: String): AssistantReply? {
        val text = normalize(input)
        val words = if (text.isEmpty()) 0 else text.split(" ").size

        // Cada regra só vale para falas curtas: "olá, explique a fotossíntese" vai para a IA.
        return when {
            has(text, words, 5, "pare de falar", "para de falar", "fique quieto", "silencio", "pare", "parar") ->
                AssistantReply(
                    text = "Ok, em silêncio.",
                    speak = false,
                    action = AssistantAction.STOP_SPEAKING
                )

            has(text, words, 6, "modo de espera", "modo espera", "pode descansar", "tchau", "ate logo") ->
                AssistantReply(
                    text = "Voltando ao modo de espera. Estarei aqui quando precisar.",
                    action = AssistantAction.STANDBY
                )

            // ---------- apps ----------
            matchOpenApp(text) != null -> matchOpenApp(text)!!

            // ---------- música / FabTune (controle por sessão de mídia Android) ----------
            // Pausar
            has(text, words, 8, "para a musica", "pausar musica", "pause a musica", "pausar a musica",
                "pausar", "pausa", "pause", "para a reproducao", "pausar reproducao") ->
                AssistantReply("Pausando a música.", deviceAction = DeviceAction.MediaPause)

            // Continuar / retomar
            has(text, words, 8, "continua tocando", "continuar tocando", "continua a musica",
                "retoma a musica", "retomar a musica", "retoma", "retomar", "continua",
                "continua a reproducao", "retomar reproducao") ->
                AssistantReply("Retomando a reprodução.", deviceAction = DeviceAction.MediaPlay)

            // Play / inicia / toca
            has(text, words, 10, "toca musica", "toque musica", "tocar musica", "toca uma musica",
                "toca minha musica", "inicia a musica", "inicia a reproducao", "inicia reproducao",
                "comeca a musica", "play", "toca", "toque") ->
                AssistantReply("Iniciando a reprodução.", deviceAction = DeviceAction.PlayMusic(null))

            // Abrir FabTune explicitamente
            has(text, words, 8, "abre o fabtune", "abre fabtune", "abrir fabtune", "abrir o fabtune",
                "abre o player", "abrir o player") ->
                AssistantReply("Abrindo o FabTune.", deviceAction = DeviceAction.PlayMusic(null))

            // Próxima
            has(text, words, 8, "proxima musica", "proxima faixa", "proxima", "pular musica",
                "pular faixa", "pular", "avanca", "avancar", "proxima track") ->
                AssistantReply("Avançando para a próxima faixa.", deviceAction = DeviceAction.MediaNext)

            // Anterior
            has(text, words, 8, "musica anterior", "faixa anterior", "voltar musica", "voltar faixa",
                "anterior", "volta", "voltar") ->
                AssistantReply("Voltando para a faixa anterior.", deviceAction = DeviceAction.MediaPrevious)

            // Parar (stop) — distinto de pausar
            has(text, words, 6, "parar a musica", "para tudo", "stop", "encerra a musica") ->
                AssistantReply("Parando a música.", deviceAction = DeviceAction.MediaStop)

            // Qual música está tocando
            has(text, words, 10, "qual musica esta tocando", "que musica esta tocando",
                "qual musica toca", "o que esta tocando", "qual e a musica", "nome da musica") ->
                AssistantReply("Consultando a faixa atual.", deviceAction = DeviceAction.MediaNowPlaying)

            // ---------- volume ----------
            has(text, words, 6, "aumenta o volume", "aumentar volume", "volume mais alto", "volume pra cima", "mais volume") ->
                AssistantReply("Ok.", deviceAction = DeviceAction.VolumeUp)

            has(text, words, 6, "diminui o volume", "diminuir volume", "volume mais baixo", "volume pra baixo", "menos volume") ->
                AssistantReply("Ok.", deviceAction = DeviceAction.VolumeDown)

            has(text, words, 5, "mudo", "silenciar", "deixa no mudo") ->
                AssistantReply("Ok.", deviceAction = DeviceAction.VolumeMute)

            // ---------- lanterna ----------
            has(text, words, 5, "liga a lanterna", "ligar lanterna", "acende a lanterna", "lanterna") ->
                AssistantReply("Ok.", deviceAction = DeviceAction.FlashOn)

            has(text, words, 6, "desliga a lanterna", "desligar lanterna", "apaga a lanterna") ->
                AssistantReply("Ok.", deviceAction = DeviceAction.FlashOff)

            // ---------- chamadas / mensagens / alarme ----------
            matchCall(text) != null -> matchCall(text)!!
            matchAlarm(text) != null -> matchAlarm(text)!!
            matchTimer(text) != null -> matchTimer(text)!!

            // ---------- sistema ----------
            has(text, words, 5, "liga o wifi", "ligar wifi", "wifi ligar", "ativar wifi", "liga wifi",
                "desliga o wifi", "desligar wifi", "desativar wifi") ->
                AssistantReply("Ok.", deviceAction = DeviceAction.WifiToggle)

            has(text, words, 7, "liga o bluetooth", "ligar bluetooth", "ativar bluetooth",
                "desliga o bluetooth", "desligar bluetooth") ->
                AssistantReply("Ok.", deviceAction = DeviceAction.BluetoothToggle)

            has(text, words, 6, "abre as configuracoes", "abrir configuracoes", "configuracoes") ->
                AssistantReply("Ok.", deviceAction = DeviceAction.OpenSettings)

            has(text, words, 6, "nivel da bateria", "quanto de bateria", "bateria", "carga da bateria", "bateria quanto") ->
                AssistantReply("Ok.", deviceAction = DeviceAction.BatteryStatus)

            // ---------- data / hora ----------
            has(text, words, 7, "que horas", "que hora e", "hora atual", "me diga as horas", "horario") ->
                AssistantReply(timeText())

            has(text, words, 7, "que dia e hoje", "qual e a data", "data de hoje", "que data") ->
                AssistantReply(dateText())

            has(text, words, 10, "onde estou", "minha localizacao", "qual minha localizacao",
                "onde eu estou", "minha posicao", "localizacao atual") ->
                AssistantReply("Consultando a localização.", deviceAction = DeviceAction.LocationQuery)

            // ---------- identidade ----------
            has(text, words, 7, "seu nome", "voce se chama", "quem e voce") ->
                AssistantReply(
                    "Meu nome é J.A.V.E.S., seu assistente pessoal. Controlo seus aplicativos, " +
                        "músicas, ligações, alarmes e configurações deste aparelho — sem precisar de internet."
                )

            has(text, words, 6, "como voce esta", "como vai voce", "tudo bem com voce") ->
                AssistantReply("Estou operando normalmente. E você, como está?")

            has(text, words, 6, "o que voce pode fazer", "o que voce faz", "ajuda", "comandos") ->
                AssistantReply(
                    "Eu controlo seu celular por voz, tudo offline: abrir aplicativos, tocar música " +
                        "no FabTune, volume, lanterna, ligações, WhatsApp, SMS, alarmes, cronômetro, " +
                        "Wi-Fi, Bluetooth e bateria. Também sei as horas e a data. " +
                        "Para conversar sobre qualquer assunto, uso a inteligência artificial configurada."
                )

            isGreeting(text, words) ->
                AssistantReply("Olá, operador. J.A.V.E.S. está online. Como você está hoje?")

            has(text, words, 5, "estou bem", "tudo bem", "tudo certo", "estou otimo", "estou ok") ->
                AssistantReply("Fico feliz em saber. Em que posso ajudar?")

            else -> matchLongCommand(text, words)
        }
    }

    // ---------- comandos com parâmetros ----------

    private fun matchOpenApp(text: String): AssistantReply? {
        val m = Regex("\\b(abre|abrir|abra)\\b (?:o |a )?(.+)")
            .find(text) ?: return null
        val query = m.groupValues[2].trim()
        if (query.isEmpty() || query.split(" ").size > 4) return null
        // "abre o site X" tem regra própria
        if (query.startsWith("site ")) return null
        // Não é "abre o alarme" etc. (esses têm regra própria)
        if (Regex("\\b(alarme|cronometro|timer|configuracoes|lanterna)\\b").containsMatchIn(query)) return null
        return AssistantReply("Ok.", deviceAction = DeviceAction.OpenApp(query))
    }

    private fun matchCall(text: String): AssistantReply? {
        val m = Regex("\\b(ligar para|liga para|ligar pro|liga pro|ligar pra|liga pra|discar|disca|ligar)\\b(?: o| a)? (.+)")
            .find(text) ?: return null
        val target = m.groupValues[2].trim()
        if (target.isEmpty() || target.split(" ").size > 5) return null
        return AssistantReply("Ok.", deviceAction = DeviceAction.Call(target))
    }

    private fun matchAlarm(text: String): AssistantReply? {
        val m = Regex("\\b(me acorda|me acorde|despertador|alarme|alarme para|acorda me)\\b(?: as| para as| pra| para)? (\\d{1,2})(?:[h:](\\d{2}))?(?:\\s*(da manha|da tarde|da noite))?")
            .find(text) ?: return null
        var hour = m.groupValues[2].toIntOrNull() ?: return null
        val minute = m.groupValues[3].toIntOrNull() ?: 0
        val period = m.groupValues[4]
        if (hour in 1..11 && (period == "da tarde" || period == "da noite")) hour += 12
        if (hour == 12 && period == "da noite") hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return AssistantReply(
            "Ok.",
            deviceAction = DeviceAction.SetAlarm(hour, minute, "Acordar")
        )
    }

    private fun matchTimer(text: String): AssistantReply? {
        val m = Regex("\\b(cronometro|timer|minutero|conta)\\b(?: de)? (\\d{1,3}) minutos?")
            .find(text) ?: return null
        val minutes = m.groupValues[2].toIntOrNull() ?: return null
        if (minutes < 1) return null
        return AssistantReply("Ok.", deviceAction = DeviceAction.SetTimer(minutes))
    }

    /** Falas mais longas caem aqui: pesquisa na web, sites e mensagens com destinatário. */
    private fun matchLongCommand(text: String, words: Int): AssistantReply? {
        // "manda um whats pro joão dizendo estou chegando"
        Regex("\\bmanda (?:um )?(?:whatsapp|whats|zap|mensagem)(?: no whatsapp)? (?:pro|pra|para) ([a-z0-9 ]+?) (?:dizendo|falando) (.+)")
            .find(text)?.let { m ->
                return AssistantReply(
                    "Ok.",
                    deviceAction = DeviceAction.WhatsMessage(m.groupValues[1].trim(), m.groupValues[2].trim())
                )
            }

        // "manda um sms pro joão dizendo ..."
        Regex("\\bmanda (?:um )?sms (?:pro|pra|para) ([a-z0-9 ]+?) (?:dizendo|falando) (.+)")
            .find(text)?.let { m ->
                return AssistantReply(
                    "Ok.",
                    deviceAction = DeviceAction.Sms(m.groupValues[1].trim(), m.groupValues[2].trim())
                )
            }

        // "pesquisa no google como fazer bolo" / "pesquisa clima de hoje"
        if (words in 2..12) {
            Regex("\\b(?:pesquisa|pesquisar|busca|buscar|procura|google)\\b(?: no google)? (.+)")
                .find(text)?.let { m ->
                    return AssistantReply(
                        "Ok.",
                        deviceAction = DeviceAction.WebSearch(m.groupValues[1].trim())
                    )
                }
        }

        // "abre o site globo.com"
        Regex("\\b(?:abre|abrir|acesse|acessa) (?:o |a )?site ([a-z0-9.]+\\.[a-z]{2,})\\b")
            .find(text)?.let { m ->
                return AssistantReply("Ok.", deviceAction = DeviceAction.OpenUrl(m.groupValues[1].trim()))
            }

        return null
    }

    // ----- utilidades -----

    private fun normalize(input: String): String {
        var s = Normalizer.normalize(input.lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
        s = s.replace(Regex("\\p{Mn}+"), "")
        s = s.replace(Regex("[^a-z0-9 ]"), " ")
        s = s.replace(Regex("\\s+"), " ").trim()
        // "j.a.v.e.s." vira "j a v e s" depois da limpeza
        s = s.replace("j a v e s", "javes")
        // Remove o nome do assistente (e variações comuns do reconhecedor) da frase
        s = s.replace(Regex("\\b(javes|jarvis|jarves|javis)\\b"), " ")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    private fun has(text: String, words: Int, maxWords: Int, vararg phrases: String): Boolean =
        words <= maxWords && phrases.any { phrase ->
            Regex("\\b" + Regex.escape(phrase) + "\\b").containsMatchIn(text)
        }

    private fun isGreeting(text: String, words: Int): Boolean {
        // Só o nome do assistente ("Javes!") ou "inicia" após a ativação
        if (text.isEmpty()) return true
        return has(text, words, 4, "ola", "oi", "bom dia", "boa tarde", "boa noite", "e ai", "hey", "alo", "inicia", "iniciar")
    }

    private fun timeText(): String {
        // Fuso horário atual do dispositivo (não UTC fixo).
        val now = LocalTime.now(ZoneId.systemDefault())
        val h = now.hour
        val m = now.minute
        val hourPart = when (h) {
            0 -> "É meia-noite"
            1 -> "É 1 hora"
            12 -> "É meio-dia"
            else -> "São $h horas"
        }
        return when (m) {
            0 -> "$hourPart em ponto."
            1 -> "$hourPart e 1 minuto."
            else -> "$hourPart e $m minutos."
        }
    }

    private fun dateText(): String {
        val zone = ZoneId.systemDefault()
        val formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale("pt", "BR"))
        return "Hoje é ${LocalDate.now(zone).format(formatter)}."
    }

    private companion object {
        const val FALLBACK_TEXT =
            "Não reconheci esse comando local. Eu controlo o aparelho: apps, música, ligações, " +
                "alarmes, lanterna, volume e mais. Ou configure a IA para conversar."
    }
}
