# J.A.V.E.S. — versão 0.2.0

Assistente de voz pessoal que controla o seu Android. Todos os comandos abaixo
funcionam **sem internet**; a conversa com IA (Gemini) é opcional e precisa de
chave de API + conexão.

## Novidades da 0.2.0

- **Controle do aparelho por voz** (offline): apps, música, volume, lanterna,
  ligações, WhatsApp, SMS, alarmes, cronômetro, Wi-Fi, Bluetooth, bateria.
- **Integração com FabTune**: "toca música", "próxima música", "para a música" —
  o controle de mídia funciona com o FabTune (e com qualquer player).
- **Modo 24 horas**: o J.A.V.E.S. fica ouvindo o tempo todo. Diga **"Javes"**
  (ou "Jarvis") para chamá-lo, fale o comando e ele executa e responde por voz,
  mesmo com a tela desligada. Ative em CONFIG → Modo 24 horas.
- **Reinício automático** no boot do aparelho (se o modo 24h estiver ativo).
- **Modo offline**: sem internet, a IA avisa que está indisponível e os comandos
  de voz continuam funcionando normalmente.

## Comandos de voz (diga "Javes" antes, no modo 24h)

| Fala | O que faz |
|---|---|
| abre o WhatsApp / abre o FabTune / abre a câmera… | abre qualquer app instalado |
| toca música | abre o FabTune e dá play |
| próxima música / música anterior / para a música | controles de mídia |
| aumenta o volume / diminui o volume / mudo | volume da mídia |
| liga a lanterna / desliga a lanterna | flash |
| liga para o João / liga para 11999998888 | ligação (contato da agenda ou número) |
| manda um Whats pro João dizendo estou chegando | WhatsApp com mensagem pronta |
| manda um sms pro João dizendo te ligo depois | SMS pronto |
| me acorda às 6 / me acorda às 7 e meia da manhã | alarme |
| cronômetro de 10 minutos | timer |
| liga o Wi-Fi / desliga o Bluetooth | painéis de configuração |
| quanto de bateria | nível da bateria |
| que horas são / que dia é hoje | relógio e data |
| pesquisa como fazer bolo | Google (precisa de internet) |

Dica: no modo 24h, se ele não entender, ele pede para repetir.

## Instalação

1. Abra o projeto no Android Studio (ou compile com Gradle).
2. Rode no seu aparelho (Android 8.0+).
3. Na primeira vez, conceda as permissões pedidas (microfone, contatos,
   chamadas). Para o modo 24h, desative a otimização de bateria do app em
   Configurações → Aplicativos → J.A.V.E.S. → Bateria → "Sem restrições",
   senão o Android pode matar o serviço com o tempo.
4. (Opcional) CONFIG → cole a chave da API Gemini para conversar sobre
   qualquer assunto.

## Observações

- O reconhecimento de voz usa o serviço do Android (app Google). Em alguns
  aparelhos o reconhecimento offline em português precisa baixar o pacote de
  idioma em Configurações → Google → voz.
- O controle de mídia (play/pause/próxima) usa os botões de mídia do Android,
  então funciona com FabTune, Spotify, YouTube etc.
- "Liga para X" disca direto com a permissão de chamada; sem ela, abre o
  discador com o número pronto.
