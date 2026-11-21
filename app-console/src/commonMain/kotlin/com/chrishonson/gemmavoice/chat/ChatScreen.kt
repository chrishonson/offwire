@file:Suppress("TooManyFunctions")

package com.chrishonson.gemmavoice.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.chrishonson.gemmavoice.Lcars
import com.chrishonson.gemmavoice.LcarsBar
import com.chrishonson.gemmavoice.LcarsFrame
import com.chrishonson.gemmavoice.NavPill
import com.chrishonson.gemmavoice.isLandscape
import com.chrishonson.gemmavoice.lcarsBlockShape
import com.chrishonson.gemmavoice.model.formatDownloadSize
import kotlin.math.roundToInt

private const val PERCENT_MULTIPLIER = 100
private const val THINKING_ALPHA_MIN = 0.15f
private const val THINKING_ALPHA_MAX = 0.45f
private const val THINKING_ANIMATION_DURATION_MS = 600
private const val FALLBACK_ASSISTANT_NAME = "Assistant"
private const val LARGE_SCROLL_OFFSET = 10_000
private val OuterPadding = 16.dp
private val InnerPadding = 20.dp
private val ItemSpacing = 12.dp
private val LogEntryStartSpacing = 12.dp
private val LogEntryTextSpacing = 2.dp
private val LogEntryTagSpacing = 8.dp
private val SidePanelWidth = 300.dp
private val TouchTargetHeight = 60.dp
private val ConsolePillHeight = 72.dp
private val ConsolePillPadding = 12.dp
private val ButtonPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
private val ButtonIconSize = 26.dp
private val ButtonIconSpacing = 8.dp
private val ProgressHeight = 16.dp
private val OverheardRuleWidth = 3.dp
private val OverheardPadding = 10.dp
private val ProgressCornerRadius = 8.dp

internal fun displayName(assistantName: String): String = assistantName.ifBlank { FALLBACK_ASSISTANT_NAME }

internal data class AutoListenReadoutState(
    val phase: String,
    val hint: String,
    val action: String?,
    val blocked: Boolean
)

internal enum class PillKind { MUTE, UNMUTE, STOP, TALK, DOWNLOAD, RETRY }

internal data class ConsolePill(val label: String, val kind: PillKind)

internal fun consolePill(
    phase: LivePhase,
    micMuted: Boolean,
    modelStatus: ModelStatus,
    autoListen: Boolean
): ConsolePill? = when (modelStatus) {
    ModelStatus.Missing, is ModelStatus.Error -> if (autoListen) {
        readoutActionPill(modelStatus)
    } else {
        ConsolePill("TALK", PillKind.TALK)
    }
    ModelStatus.Ready -> liveConsolePill(phase, micMuted, autoListen)
    else -> null
}

private fun readoutActionPill(modelStatus: ModelStatus): ConsolePill? =
    when (autoListenReadout(modelStatus, false)?.action) {
        "DOWNLOAD" -> ConsolePill("DOWNLOAD", PillKind.DOWNLOAD)
        "RETRY" -> ConsolePill("RETRY", PillKind.RETRY)
        else -> null
    }

private fun liveConsolePill(phase: LivePhase, micMuted: Boolean, autoListen: Boolean): ConsolePill? = when (phase) {
    LivePhase.LISTENING -> if (micMuted) {
        ConsolePill("UNMUTE", PillKind.UNMUTE)
    } else {
        ConsolePill("MUTE", PillKind.MUTE)
    }
    LivePhase.THINKING, LivePhase.SPEAKING -> ConsolePill("STOP", PillKind.STOP)
    LivePhase.IDLE -> if (autoListen) null else ConsolePill("TALK", PillKind.TALK)
}

internal fun autoListenReadout(status: ModelStatus, micDenied: Boolean): AutoListenReadoutState? = when {
    micDenied -> AutoListenReadoutState(
        phase = "MIC BLOCKED",
        hint = "Allow the microphone in Settings",
        action = null,
        blocked = true
    )
    status is ModelStatus.Missing -> AutoListenReadoutState(
        phase = "STANDBY",
        hint = "Not downloaded yet",
        action = "DOWNLOAD",
        blocked = false
    )
    status is ModelStatus.Downloading -> AutoListenReadoutState(
        phase = "STANDBY",
        hint = "Downloading, ${(status.fraction.coerceIn(0f, 1f) * PERCENT_MULTIPLIER).roundToInt()}%",
        action = null,
        blocked = false
    )
    status is ModelStatus.Loading -> AutoListenReadoutState(
        phase = "STANDBY",
        hint = "Sit tight, this could take up to a minute.",
        action = null,
        blocked = false
    )
    status is ModelStatus.Error -> AutoListenReadoutState(
        phase = "STANDBY",
        hint = status.message,
        action = "RETRY",
        blocked = true
    )
    else -> null
}

@Composable
fun ChatScreen(
    store: ChatStore,
    onOpenSetup: () -> Unit = {},
    onOpenLimits: () -> Unit = {},
    sheetHost: @Composable () -> Unit = {}
) {
    val state by store.state.collectAsState()
    val name = displayName(state.assistantName)
    val headerTitle = name
    val isReady = state.modelStatus is ModelStatus.Ready

    val readout = phaseReadout(
        modelStatus = state.modelStatus,
        micDenied = state.micDenied,
        livePhase = state.livePhase,
        micMuted = state.micMuted,
        wakeWordEnabled = state.wakeWordEnabled,
        autoListen = state.autoListen,
        assistantName = name
    )

    val infiniteTransition = rememberInfiniteTransition(label = "railAnimation")
    val thinkingAlpha by infiniteTransition.animateFloat(
        initialValue = THINKING_ALPHA_MIN,
        targetValue = THINKING_ALPHA_MAX,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = THINKING_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinkingAlpha"
    )

    val railTopColor = when (readout.railColor) {
        RailColor.FRAME -> Color(Lcars.FRAME)
        RailColor.TOMATO -> Color(Lcars.TOMATO)
        RailColor.ICE -> Color(Lcars.ICE)
        RailColor.SUNFLOWER -> Color(Lcars.SUNFLOWER)
        RailColor.ORANGE_PULSE -> Color(Lcars.ORANGE).copy(alpha = thinkingAlpha)
    }

    LcarsFrame(
        title = headerTitle,
        railTopColor = railTopColor,
        onOpenLimits = onOpenLimits,
        actions = {
            NetworkIndicator(active = state.networkActive)
            NavPill(label = "Setup", color = Color(Lcars.VIOLET), onClick = onOpenSetup)
        }
    ) { innerPadding ->
        ChatBody(
            store = store,
            state = state,
            isReady = isReady,
            readout = readout,
            innerPadding = innerPadding
        )
        sheetHost()
    }
}

@Composable
private fun ChatBody(
    store: ChatStore,
    state: ChatState,
    isReady: Boolean,
    readout: PhaseReadoutState,
    innerPadding: PaddingValues
) {
    val name = displayName(state.assistantName)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        // Landscape on a phone leaves too little height to stack the conversation over the controls.
        val wide = isLandscape(maxWidth, maxHeight)
        val conversation: @Composable ColumnScope.() -> Unit = {
            if (!isReady && !state.autoListen) {
                ModelCard(
                    assistantName = name,
                    modelName = state.modelName,
                    status = state.modelStatus,
                    onDownload = { store.dispatch(ChatIntent.DownloadModel) },
                    onConfirmDownload = { store.dispatch(ChatIntent.ConfirmDownload) },
                    onCancelDownload = { store.dispatch(ChatIntent.CancelDownload) },
                    onToggleCellular = { store.dispatch(ChatIntent.ToggleCellularDownload) },
                    modifier = Modifier.padding(horizontal = OuterPadding, vertical = ItemSpacing)
                )
            }
            MessageList(
                messages = state.messages,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            OfflineVoiceNotice(
                missing = state.offlineVoiceMissing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OuterPadding, vertical = ItemSpacing)
            )
            if (state.micDenied && !state.autoListen) {
                Text(
                    text = "Microphone permission is needed for voice.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OuterPadding, vertical = ItemSpacing)
                )
            }
        }
        val phaseReadout: @Composable (Modifier) -> Unit = { readoutModifier ->
            Column(modifier = readoutModifier) {
                PhaseReadout(
                    readout = readout,
                    liveLevel = state.liveLevel,
                    voiceDetected = state.voiceDetected,
                    onTap = { store.dispatch(ChatIntent.LiveTap) },
                    modifier = Modifier.fillMaxWidth()
                )
                ResourceMeter(
                    sample = state.resources,
                    modifier = Modifier.padding(horizontal = OuterPadding),
                    speed = state.replySpeed
                )
            }
        }
        val pill = consolePill(state.livePhase, state.micMuted, state.modelStatus, state.autoListen)
        val consolePill: @Composable () -> Unit = {
            ConsolePillButton(
                pill = pill,
                enabled = isReady || pill?.kind != PillKind.TALK,
                onToggleMute = { store.dispatch(ChatIntent.ToggleMute) },
                onLiveTap = { store.dispatch(ChatIntent.LiveTap) },
                onEnterLiveMode = { store.dispatch(ChatIntent.EnterLiveMode) },
                onDownload = { store.dispatch(ChatIntent.DownloadModel) },
                modifier = Modifier.fillMaxWidth().padding(ConsolePillPadding)
            )
        }

        ChatLayout(
            wide = wide,
            hasPill = pill != null,
            conversation = conversation,
            phaseReadout = phaseReadout,
            consolePill = consolePill
        )
    }
}

// Landscape puts the readout beside the conversation; otherwise it sits at the top of the body.
@Composable
private fun ChatLayout(
    wide: Boolean,
    hasPill: Boolean,
    conversation: @Composable ColumnScope.() -> Unit,
    phaseReadout: @Composable (Modifier) -> Unit,
    consolePill: @Composable () -> Unit
) {
    if (wide) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), content = conversation)
            Column(modifier = Modifier.width(SidePanelWidth).fillMaxHeight()) {
                phaseReadout(Modifier.fillMaxWidth().weight(1f))
                if (hasPill) consolePill()
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            phaseReadout(Modifier.fillMaxWidth())
            conversation()
            if (hasPill) consolePill()
        }
    }
}

@Composable
private fun RowScope.ButtonContent(icon: ImageVector, label: String) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(ButtonIconSize)
    )
    Spacer(modifier = Modifier.width(ButtonIconSpacing))
    Text(text = label, style = MaterialTheme.typography.labelLarge)
}

private val TopBarSpacing = 8.dp

@Composable
private fun NetworkIndicator(active: Boolean, modifier: Modifier = Modifier) {
    if (!active) return
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.secondary,
                shape = RoundedCornerShape(percent = 50)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "NET",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondary
        )
    }
    Spacer(modifier = Modifier.width(TopBarSpacing))
}

@Composable
private fun OfflineVoiceNotice(missing: Boolean, modifier: Modifier = Modifier) {
    if (!missing) return
    Text(
        text = "No offline voice installed. Answers are text only.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

@Composable
fun ModelCard(
    assistantName: String,
    modelName: String,
    status: ModelStatus,
    onDownload: () -> Unit,
    onConfirmDownload: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onToggleCellular: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = lcarsBlockShape(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            LcarsBar(MaterialTheme.colorScheme.secondary)
            ModelCardContent(
                assistantName = assistantName,
                modelName = modelName,
                status = status,
                onDownload = onDownload,
                onConfirmDownload = onConfirmDownload,
                onCancelDownload = onCancelDownload,
                onToggleCellular = onToggleCellular
            )
        }
    }
}

@Composable
private fun ModelCardContent(
    assistantName: String,
    modelName: String,
    status: ModelStatus,
    onDownload: () -> Unit,
    onConfirmDownload: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onToggleCellular: () -> Unit = {}
) {
    Column(
        modifier = Modifier.padding(InnerPadding),
        verticalArrangement = Arrangement.spacedBy(ItemSpacing)
    ) {
        Text(
            text = formatModelCardTitle(status, assistantName, modelName),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = formatStatusText(status),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when (status) {
            is ModelStatus.ConfirmDownload -> {
                Text(
                    text = "Download ${formatDownloadSize(status.totalBytes)}?",
                    style = MaterialTheme.typography.bodyMedium
                )
                ModelLicenseNotice()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ItemSpacing)
                ) {
                    Button(
                        onClick = onConfirmDownload,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = TouchTargetHeight),
                        contentPadding = ButtonPadding
                    ) {
                        Text(text = "Download", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = onCancelDownload,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = TouchTargetHeight),
                        contentPadding = ButtonPadding
                    ) {
                        Text(text = "Cancel", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            is ModelStatus.WifiRequired -> {
                Text(
                    text = "Connect to Wi-Fi to download ${formatDownloadSize(status.totalBytes)}, or allow cellular.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ItemSpacing)
                ) {
                    Button(
                        onClick = onToggleCellular,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = TouchTargetHeight),
                        contentPadding = ButtonPadding
                    ) {
                        Text(text = "Allow cellular", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = onCancelDownload,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = TouchTargetHeight),
                        contentPadding = ButtonPadding
                    ) {
                        Text(text = "Cancel", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            is ModelStatus.Downloading -> LinearProgressIndicator(
                progress = { status.fraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ProgressHeight)
                    .clip(RoundedCornerShape(ProgressCornerRadius))
            )
            ModelStatus.Loading -> LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ProgressHeight)
                    .clip(RoundedCornerShape(ProgressCornerRadius))
            )
            ModelStatus.Missing, is ModelStatus.Error -> Button(
                onClick = onDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TouchTargetHeight),
                contentPadding = ButtonPadding
            ) {
                if (status is ModelStatus.Error) {
                    ButtonContent(icon = Icons.Default.Refresh, label = "Try again")
                } else {
                    ButtonContent(icon = Icons.Default.Download, label = "Download")
                }
            }
            ModelStatus.Ready -> Unit
        }
    }
}

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val lastMessageText = messages.lastOrNull()?.text

    LaunchedEffect(messages.size, lastMessageText) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex, scrollOffset = LARGE_SCROLL_OFFSET)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = OuterPadding, vertical = ItemSpacing),
        verticalArrangement = Arrangement.spacedBy(ItemSpacing)
    ) {
        items(items = messages, key = { it.id }) { message ->
            TranscriptEntry(message = message)
        }
    }
}

// Heard but not asked: a dim quote with no bubble, so it never reads as part of the conversation.
@Composable
internal fun OverheardEntry(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .testTag(transcriptTestTag(Role.OVERHEARD))
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) { contentDescription = "Heard: $text" }
    ) {
        Box(
            modifier = Modifier
                .width(OverheardRuleWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Text(
            text = "\u201C$text\u201D",
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = OverheardPadding, vertical = OverheardRuleWidth)
        )
    }
}

@Composable
internal fun TranscriptEntry(
    role: Role,
    text: String,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (role == Role.OVERHEARD) {
        OverheardEntry(text = text, modifier = modifier)
        return
    }

    val rootTag = transcriptTestTag(role)
    val barColor = transcriptBarColor(
        role = role,
        userColor = MaterialTheme.colorScheme.secondary,
        assistantColor = MaterialTheme.colorScheme.tertiary
    ) ?: MaterialTheme.colorScheme.secondary
    val tagText = transcriptTag(role).orEmpty()
    val displayText = transcriptDisplayText(text, isStreaming)

    Row(
        modifier = modifier
            .testTag(rootTag)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        LcarsBar(barColor)
        Spacer(modifier = Modifier.width(LogEntryStartSpacing))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(LogEntryTextSpacing)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LogEntryTagSpacing)
            ) {
                Text(
                    text = tagText,
                    style = MaterialTheme.typography.labelMedium,
                    color = barColor
                )
            }
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun TranscriptEntry(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    TranscriptEntry(
        role = message.role,
        text = message.text,
        isStreaming = message.isStreaming,
        modifier = modifier
    )
}

internal fun transcriptTag(role: Role): String? = when (role) {
    Role.USER -> "YOU"
    Role.ASSISTANT -> "COMPUTER"
    Role.OVERHEARD -> null
}

internal fun transcriptTestTag(role: Role): String = when (role) {
    Role.USER -> "logEntry-user"
    Role.ASSISTANT -> "logEntry-assistant"
    Role.OVERHEARD -> "logEntry-overheard"
}

internal fun transcriptBarColor(
    role: Role,
    userColor: Color,
    assistantColor: Color
): Color? = when (role) {
    Role.USER -> userColor
    Role.ASSISTANT -> assistantColor
    Role.OVERHEARD -> null
}

internal fun transcriptDisplayText(text: String, isStreaming: Boolean): String =
    if (isStreaming) "$text▍" else text

@Composable
internal fun ConsolePillButton(
    pill: ConsolePill?,
    enabled: Boolean = true,
    onToggleMute: () -> Unit = {},
    onLiveTap: () -> Unit = {},
    onEnterLiveMode: () -> Unit = {},
    onDownload: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (pill == null) return
    val color = when (pill.kind) {
        PillKind.MUTE, PillKind.TALK, PillKind.DOWNLOAD -> Color(Lcars.ICE)
        PillKind.UNMUTE -> Color(Lcars.ORANGE)
        PillKind.STOP, PillKind.RETRY -> Color(Lcars.TOMATO)
    }
    val onClick = when (pill.kind) {
        PillKind.MUTE, PillKind.UNMUTE -> onToggleMute
        PillKind.STOP -> onLiveTap
        PillKind.TALK -> onEnterLiveMode
        PillKind.DOWNLOAD, PillKind.RETRY -> onDownload
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(ConsolePillHeight)
            .testTag("consolePill"),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color(Lcars.BLACK),
            disabledContainerColor = color.copy(alpha = 0.38f),
            disabledContentColor = Color(Lcars.BLACK).copy(alpha = 0.38f)
        ),
        contentPadding = ButtonPadding
    ) {
        Text(text = pill.label.uppercase(), style = MaterialTheme.typography.labelLarge)
    }
}

internal fun formatModelCardTitle(status: ModelStatus, assistantName: String, modelName: String): String =
    when (status) {
        is ModelStatus.Downloading, ModelStatus.Loading -> "Getting ${displayName(assistantName)} ready"
        else -> modelName
    }

internal fun formatStatusText(status: ModelStatus): String = when (status) {
    ModelStatus.Missing -> "Not downloaded yet"
    is ModelStatus.ConfirmDownload -> "Ready to download"
    is ModelStatus.WifiRequired -> "Waiting for Wi-Fi"
    is ModelStatus.Downloading -> {
        val percent = (status.fraction.coerceIn(0f, 1f) * PERCENT_MULTIPLIER).roundToInt()
        "Downloading, $percent%"
    }
    ModelStatus.Loading -> "Sit tight, this could take up to a minute."
    ModelStatus.Ready -> "Ready"
    is ModelStatus.Error -> "Something went wrong: ${status.message}"
}
