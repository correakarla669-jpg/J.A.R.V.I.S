package com.javes.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javes.app.ui.theme.JarvisBlack
import com.javes.app.ui.theme.JarvisBlue
import com.javes.app.ui.theme.JarvisCyan
import com.javes.app.ui.theme.JarvisDeepBlue
import com.javes.app.ui.theme.JarvisError
import com.javes.app.ui.theme.JarvisGlow
import com.javes.app.ui.theme.JarvisNavy
import com.javes.app.ui.theme.JarvisWarning

@Composable
fun JarvisScreen(viewModel: JarvisViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val micLevel by viewModel.micLevel.collectAsStateWithLifecycle()
    val aiSettings by viewModel.aiSettings.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current

    var showPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var permissionBlocked by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionBlocked = false
            viewModel.startListening()
        } else {
            val activity = context.findActivity()
            // Se o Android não oferece mais o pedido, só as configurações resolvem.
            permissionBlocked = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.RECORD_AUDIO
                )
            viewModel.onMicPermissionDenied()
        }
    }

    // Permissões do modo 24h: microfone (fundo), contatos (ligações) e chamadas.
    val locationPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* resultado tratado na próxima consulta de localização */ }

    val alwaysOnPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] == true
        if (micGranted) {
            viewModel.setAlwaysOn(true)
        } else {
            viewModel.onMicPermissionDenied()
        }
    }

    // Privacidade: ao sair do app, o microfone, a fala e a iniciativa são encerrados.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val onMicClick: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (state == JarvisState.LISTENING) {
            viewModel.cancelListening()
        } else if (state == JarvisState.PROCESSING) {
            viewModel.cancelProcessing()
        } else if (granted) {
            viewModel.startListening()
        } else {
            showPermissionDialog = true
        }
    }

    val onSend: () -> Unit = {
        if (input.isNotBlank()) {
            viewModel.sendText(input)
            input = ""
            focusManager.clearFocus()
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            containerColor = JarvisDeepBlue,
            title = { Text("Permitir o microfone?", color = JarvisCyan) },
            text = {
                Text(
                    text = if (permissionBlocked) {
                        "A permissão do microfone foi negada. Para o J.A.V.E.S. ouvir você, " +
                            "ative o microfone nas configurações do app."
                    } else {
                        "O J.A.V.E.S. usa o microfone somente quando você toca em OUVIR, " +
                            "e a tela mostra \"OUVINDO\" enquanto ele está ativo. " +
                            "O áudio é processado pelo serviço de reconhecimento de voz do Android, " +
                            "que pode usar a internet dependendo do aparelho."
                    },
                    color = Color.White.copy(alpha = 0.85f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    if (permissionBlocked) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                        )
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }) {
                    Text(
                        if (permissionBlocked) "ABRIR CONFIGURAÇÕES" else "CONTINUAR",
                        color = JarvisCyan
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("AGORA NÃO", color = JarvisBlue)
                }
            }
        )
    }

    if (showSettings) {
        SettingsDialog(
            settings = aiSettings,
            conversation = conversation,
            currentState = state,
            onDismiss = { showSettings = false },
            onSelectProvider = viewModel::selectProvider,
            onSaveModel = viewModel::saveModel,
            onSaveKey = viewModel::saveApiKey,
            onClearKey = viewModel::clearApiKey,
            onTest = viewModel::testConnection,
            onAutoConversation = viewModel::setAutoConversation,
            onInitiative = viewModel::setInitiative,
            onEnsemble = viewModel::setEnsemble,
            onSaveProxyUrl = viewModel::saveBackendProxyUrl,
            onVoiceActivation = viewModel::setVoiceActivation,
            onAlwaysOn = { enabled ->
                if (!enabled) {
                    viewModel.setAlwaysOn(false)
                } else {
                    val needsAsk = Build.VERSION.SDK_INT >= 23 && listOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.CALL_PHONE
                    ).any {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (needsAsk) {
                        alwaysOnPermissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.CALL_PHONE,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        )
                    } else {
                        viewModel.setAlwaysOn(true)
                    }
                }
            },
            onClearContext = viewModel::clearContext,
            onVisualState = viewModel::setState
        )
    }

    // Pede permissão de localização sob demanda (comando "onde estou").
    LaunchedEffect(state) {
        if (state == JarvisState.LOCATION) {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
                locationPermissionsLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    val listening = state == JarvisState.LISTENING
    val processing = state == JarvisState.PROCESSING

    val online = rememberOnline()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(JarvisBlack, JarvisNavy)
                )
            )
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "J.A.V.E.S.",
                color = JarvisCyan,
                fontSize = 26.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 8.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            TextButton(
                onClick = { showSettings = true },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Text("CONFIG", color = JarvisCyan.copy(alpha = 0.8f), fontSize = 11.sp, letterSpacing = 1.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        SystemIndicators(
            state = state,
            online = online,
            alwaysOn = aiSettings.alwaysOn
        )
        Text(
            text = providerLabel(aiSettings),
            color = JarvisCyan.copy(alpha = 0.45f),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 2.dp)
        )

        // Indicação de que a ativação por voz está armada (só em IDLE, app aberto)
        if (aiSettings.voiceActivationEnabled && state == JarvisState.IDLE) {
            val pulse by rememberInfinitePulse()
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(7.dp)
                        .height(7.dp)
                        .background(JarvisGlow.copy(alpha = pulse), RoundedCornerShape(4.dp))
                )
                Text(
                    text = "Diga \"JAVES, inicia\"",
                    color = JarvisGlow.copy(alpha = 0.55f + 0.35f * pulse),
                    fontSize = 10.sp,
                    letterSpacing = 2.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            JarvisCore(
                state = state,
                micLevel = micLevel,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
        }

        // Painéis contextuais conforme o estado real
        TimeHoloPanel(visible = state == JarvisState.TIME)
        MusicHoloPanel(
            visible = state == JarvisState.MUSIC,
            statusLine = conversation.reply.takeIf { it.isNotBlank() }
        )
        StatusHoloPanel(
            visible = state == JarvisState.OFFLINE,
            title = "REDE · OFFLINE",
            message = conversation.notice ?: "Serviços externos indisponíveis. Comandos locais ativos.",
            borderColor = JarvisWarning
        )
        StatusHoloPanel(
            visible = state == JarvisState.ERROR,
            title = "SISTEMA · ERRO",
            message = conversation.notice ?: "Falha temporária.",
            borderColor = JarvisError
        )
        StatusHoloPanel(
            visible = state == JarvisState.LOCATION,
            title = "LOCALIZAÇÃO",
            message = conversation.reply.ifBlank { "Aguardando dados de localização autorizados." },
            borderColor = JarvisCyan
        )

        JarvisWaveform(
            state = state,
            level = micLevel,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        )

        Spacer(Modifier.height(6.dp))
        ConversationPanel(conversation)
        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp),
                placeholder = { Text("Digite uma mensagem…", fontSize = 14.sp) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = javesFieldColors()
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSend,
                enabled = input.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisBlue,
                    contentColor = Color.White,
                    disabledContainerColor = JarvisBlue.copy(alpha = 0.25f),
                    disabledContentColor = Color.White.copy(alpha = 0.4f)
                )
            ) {
                Text("ENVIAR", fontSize = 12.sp, maxLines = 1)
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onMicClick,
                modifier = Modifier
                    .weight(1.5f)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (listening || processing) JarvisBlue else JarvisCyan,
                    contentColor = if (listening || processing) Color.White else JarvisBlack
                )
            ) {
                Text(
                    text = when {
                        listening -> "ENCERRAR ESCUTA"
                        processing -> "CANCELAR"
                        else -> "OUVIR"
                    },
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
            OutlinedButton(
                onClick = viewModel::stopSpeaking,
                enabled = state == JarvisState.SPEAKING,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = JarvisCyan,
                    disabledContentColor = JarvisCyan.copy(alpha = 0.3f)
                ),
                border = BorderStroke(
                    1.dp,
                    JarvisCyan.copy(alpha = if (state == JarvisState.SPEAKING) 1f else 0.25f)
                )
            ) {
                Text("PARAR FALA", fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

private fun providerLabel(settings: AiSettingsUi): String {
    val option = settings.options.firstOrNull { it.id == settings.activeProvider }
    val status = if (option?.configured == true) "pronto" else "sem chave"
    return "IA: ${settings.activeProvider.displayName} · $status"
}

/** Ponto luminoso que pulsa suavemente (indicador da ativação por voz). */
@Composable
private fun rememberInfinitePulse(): androidx.compose.runtime.State<Float> {
    val transition = rememberInfiniteTransition(label = "pulse")
    return transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
}

@Composable
private fun ConversationPanel(conversation: ConversationUi) {
    val heard = conversation.partial.ifBlank { conversation.heard }
    val empty = heard.isBlank() && conversation.reply.isBlank() && conversation.notice == null
    val scrollState = rememberScrollState()

    // Sempre mostra o conteúdo mais recente (rola para o fim a cada novo texto)
    LaunchedEffect(conversation.heard, conversation.reply, conversation.partial, conversation.notice) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .border(1.dp, JarvisCyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(12.dp)
            .verticalScroll(scrollState)
    ) {
        if (empty) {
            Text(
                text = "Toque em OUVIR e fale, ou digite. Ex.: \"abre o WhatsApp\", " +
                    "\"toca música\", \"liga a lanterna\", \"me acorda às 6\".",
                color = JarvisCyan.copy(alpha = 0.5f),
                fontSize = 13.sp
            )
        }
        if (heard.isNotBlank()) {
            Text("VOCÊ", color = JarvisBlue, fontSize = 10.sp, letterSpacing = 2.sp)
            Text(heard, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
        }
        if (conversation.reply.isNotBlank()) {
            Text("J.A.V.E.S.", color = JarvisCyan, fontSize = 10.sp, letterSpacing = 2.sp)
            Text(conversation.reply, color = JarvisCyan, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
        }
        conversation.notice?.let {
            Text(it, color = JarvisError.copy(alpha = 0.9f), fontSize = 12.sp)
        }
    }
}

@Composable
internal fun javesFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = JarvisCyan,
    unfocusedBorderColor = JarvisCyan.copy(alpha = 0.4f),
    cursorColor = JarvisCyan,
    focusedLabelColor = JarvisCyan,
    unfocusedLabelColor = JarvisCyan.copy(alpha = 0.7f),
    focusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.4f)
)

@Composable
internal fun RowScope.StateButton(
    target: JarvisState,
    current: JarvisState,
    onClick: (JarvisState) -> Unit
) {
    val selected = target == current
    OutlinedButton(
        onClick = { onClick(target) },
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) JarvisBlue.copy(alpha = 0.35f) else Color.Transparent,
            contentColor = JarvisCyan
        ),
        border = BorderStroke(1.dp, JarvisCyan.copy(alpha = if (selected) 1f else 0.4f))
    ) {
        Text(target.label, fontSize = 12.sp, maxLines = 1)
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}


@Composable
private fun rememberOnline(): Boolean {
    val context = LocalContext.current
    return com.javes.app.service.JavesService.isNetworkAvailable(context)
}
