package com.javes.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.javes.app.ai.ProviderId
import com.javes.app.ui.theme.JarvisBlack
import com.javes.app.ui.theme.JarvisBlue
import com.javes.app.ui.theme.JarvisCyan
import com.javes.app.ui.theme.JarvisDeepBlue

/** Configuração da IA e dos modos do JAVES. A chave digitada nunca é guardada em estado salvo da tela. */
@Composable
fun SettingsDialog(
    settings: AiSettingsUi,
    conversation: ConversationUi,
    currentState: JarvisState,
    onDismiss: () -> Unit,
    onSelectProvider: (ProviderId) -> Unit,
    onSaveModel: (String) -> Unit,
    onSaveKey: (String) -> Unit,
    onClearKey: () -> Unit,
    onTest: () -> Unit,
    onAutoConversation: (Boolean) -> Unit,
    onInitiative: (Boolean) -> Unit,
    onEnsemble: (Boolean) -> Unit = {},
    onSaveProxyUrl: (String) -> Unit = {},
    onAlwaysOn: (Boolean) -> Unit,
    onVoiceActivation: (Boolean) -> Unit = {},
    onClearContext: () -> Unit,
    onVisualState: (JarvisState) -> Unit
) {
    var model by remember(settings.activeProvider, settings.model) { mutableStateOf(settings.model) }
    var key by remember { mutableStateOf("") }
    var showVisualTest by remember { mutableStateOf(false) }
    val active = settings.activeProvider

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisDeepBlue,
        title = { Text("CONFIGURAÇÃO", color = JarvisCyan, letterSpacing = 3.sp, fontSize = 16.sp) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

                SectionLabel("PROVEDOR DE IA")
                settings.options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = option.available) { onSelectProvider(option.id) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option.id == active,
                            onClick = null,
                            enabled = option.available,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = JarvisCyan,
                                unselectedColor = JarvisCyan.copy(alpha = 0.5f),
                                disabledSelectedColor = JarvisCyan.copy(alpha = 0.3f),
                                disabledUnselectedColor = JarvisCyan.copy(alpha = 0.25f)
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                option.id.displayName,
                                color = Color.White.copy(alpha = if (option.available) 0.95f else 0.4f),
                                fontSize = 14.sp
                            )
                            Text(
                                text = when {
                                    !option.available -> "em breve"
                                    option.configured -> "chave salva"
                                    else -> "sem chave"
                                },
                                color = JarvisCyan.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                SectionLabel("MODELO (${active.displayName})")
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Nome do modelo") },
                    colors = javesFieldColors()
                )
                Text(
                    "Padrão: ${active.defaultModel}. Deixe em branco para voltar ao padrão.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                TextButton(onClick = { onSaveModel(model) }) {
                    Text("SALVAR MODELO", color = JarvisCyan, fontSize = 12.sp)
                }

                SectionLabel("CHAVE DE API")
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Cole a chave aqui") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = javesFieldColors()
                )
                Text(
                    text = if (settings.hasKey) "Há uma chave salva neste aparelho." else "Nenhuma chave salva.",
                    color = JarvisCyan.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            onSaveKey(key)
                            key = ""
                        },
                        enabled = key.isNotBlank()
                    ) { Text("SALVAR CHAVE", color = JarvisCyan, fontSize = 12.sp) }
                    TextButton(onClick = onClearKey, enabled = settings.hasKey) {
                        Text("APAGAR", color = JarvisBlue, fontSize = 12.sp)
                    }
                }
                Text(
                    "A chave fica criptografada neste aparelho e é enviada só ao provedor escolhido. " +
                        "Use apenas para testes pessoais: uma chave dentro de um aplicativo pode ser " +
                        "extraída. Em um app distribuído, guarde-a em um servidor seu.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onTest,
                    enabled = settings.hasKey && !settings.testing,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = JarvisCyan,
                        disabledContentColor = JarvisCyan.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("TESTAR CONEXÃO", fontSize = 12.sp) }
                settings.testStatus?.let {
                    Text(
                        it,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                SectionLabel("COMPORTAMENTO")
                SwitchRow(
                    label = "Modo 24 horas",
                    description = "O J.A.V.E.S. fica ouvindo o tempo todo, mesmo com a tela " +
                        "desligada. Diga \"JAVES, inicia\" para chamá-lo. Gasta mais bateria.",
                    checked = settings.alwaysOn,
                    onChange = onAlwaysOn
                )
                SwitchRow(
                    label = "Ativação por voz",
                    description = "Com o app aberto, diga \"JAVES, inicia\" para começar a falar " +
                        "comigo, sem tocar em nada. Não ativa por palavras aleatórias.",
                    checked = settings.voiceActivationEnabled,
                    onChange = onVoiceActivation
                )
                SwitchRow(
                    label = "Conversa contínua",
                    description = "Depois de cada resposta, volta a ouvir sozinho. Só com o app aberto.",
                    checked = conversation.autoConversation,
                    onChange = onAutoConversation
                )
                SwitchRow(
                    label = "Modo iniciativa",
                    description = "Cumprimenta ao abrir e oferece ajuda ocasionalmente, sem repetir " +
                        "a mesma frase em sequência. Só com o app aberto; nunca liga o microfone sozinho.",
                    checked = settings.initiativeEnabled,
                    onChange = onInitiative
                )
                SwitchRow(
                    label = "Ensemble de IA",
                    description = "Consulta todos os provedores com chave configurada e combina " +
                        "apenas respostas reais. Falhas individuais são informadas.",
                    checked = settings.ensembleEnabled,
                    onChange = onEnsemble
                )
                SectionLabel("BACKEND (OPCIONAL)")
                var proxyDraft by remember(settings.backendProxyUrl) { mutableStateOf(settings.backendProxyUrl) }
                Text(
                    "Se preenchido, as chaves ficam no seu servidor. O app só envia o texto da conversa.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = proxyDraft,
                    onValueChange = { proxyDraft = it },
                    singleLine = true,
                    label = { Text("URL do proxy", color = JarvisCyan.copy(alpha = 0.7f)) },
                    placeholder = { Text("https://seu-servidor.com", color = Color.White.copy(alpha = 0.3f)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { onSaveProxyUrl(proxyDraft) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyan),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("SALVAR URL DO BACKEND", fontSize = 12.sp) }
                TextButton(onClick = onClearContext) {
                    Text("LIMPAR CONTEXTO DA CONVERSA", color = JarvisCyan, fontSize = 12.sp)
                }

                TextButton(onClick = { showVisualTest = !showVisualTest }) {
                    Text(
                        if (showVisualTest) "MODO DE TESTE (SÓ VISUAL) ▲" else "MODO DE TESTE (SÓ VISUAL) ▼",
                        color = JarvisCyan.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
                if (showVisualTest) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StateButton(JarvisState.IDLE, currentState, onVisualState)
                        StateButton(JarvisState.LISTENING, currentState, onVisualState)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StateButton(JarvisState.PROCESSING, currentState, onVisualState)
                        StateButton(JarvisState.SPEAKING, currentState, onVisualState)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StateButton(JarvisState.MUSIC, currentState, onVisualState)
                        StateButton(JarvisState.TIME, currentState, onVisualState)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StateButton(JarvisState.LOCATION, currentState, onVisualState)
                        StateButton(JarvisState.OFFLINE, currentState, onVisualState)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StateButton(JarvisState.ERROR, currentState, onVisualState)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("FECHAR", color = JarvisCyan) }
        }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(text, color = JarvisBlue, fontSize = 11.sp, letterSpacing = 2.sp)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White.copy(alpha = 0.95f), fontSize = 14.sp)
            Text(description, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = JarvisBlack,
                checkedTrackColor = JarvisCyan,
                uncheckedThumbColor = JarvisCyan,
                uncheckedTrackColor = JarvisBlack,
                uncheckedBorderColor = JarvisCyan.copy(alpha = 0.5f)
            )
        )
    }
}
