@file:Suppress("TooManyFunctions")

package com.chrishonson.gemmavoice.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrishonson.gemmavoice.HardwareColors
import com.chrishonson.gemmavoice.KeyShape
import com.chrishonson.gemmavoice.LabelTape
import com.chrishonson.gemmavoice.LcdPanel
import com.chrishonson.gemmavoice.LocalHardwareColors
import com.chrishonson.gemmavoice.LocalHardwareFonts
import com.chrishonson.gemmavoice.OffwireMark
import com.chrishonson.gemmavoice.RockerButton
import com.chrishonson.gemmavoice.brushedSteel
import com.chrishonson.gemmavoice.ledRedKeyColors
import com.chrishonson.gemmavoice.steelKeyColors
import com.chrishonson.gemmavoice.model.formatDownloadSize
import kotlin.math.roundToInt

private const val PERCENT_MULTIPLIER = 100
private const val MAX_INPUT_LINES = 4
private const val LIVE_LEVEL_SCALE_DELTA = 0.35f
private const val RING_ALPHA_BASE = 0.25f
private const val RING_ALPHA_LEVEL_SCALE = 0.25f
private const val WAKE_SWITCH_SCALE = 1.25f
private const val THINKING_ALPHA_MIN = 0.15f
private const val THINKING_ALPHA_MAX = 0.45f
private const val THINKING_RING_SCALE = 1.15f
private const val DEFAULT_SCALE = 1f
private const val ZERO_ALPHA = 0f
private const val THINKING_ANIMATION_DURATION_MS = 600
private const val FALLBACK_ASSISTANT_NAME = "Assistant"
private val OuterPadding = 16.dp
private val InnerPadding = 20.dp
private val ItemSpacing = 12.dp
private val TargetSpacing = 12.dp
private val BubbleCornerRadius = 4.dp
private val BubbleHorizontalPadding = 18.dp
private val BubbleVerticalPadding = 14.dp
private val BubbleMaxWidth = 320.dp
private val VoiceIconSize = 22.dp
private val OrbContainerSize = 104.dp
private val OrbSize = 84.dp
private val OrbIconSize = 38.dp
private val PanelTopCornerRadius = 10.dp
private val SidePanelWidth = 400.dp
private val TopBarButtonHeight = 48.dp
private val TopBarButtonPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
private val TopBarSpacing = 8.dp
private val WideTitleMaxWidth = 240.dp
private val TouchTargetHeight = 60.dp
private val TalkButtonHeight = 72.dp
private val InputMinHeight = 64.dp
private val ButtonPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
private val PanelPaddingHorizontal = 20.dp
private val PanelPaddingVertical = 20.dp
private val WakeRowCornerRadius = 6.dp
private val WakeRowPaddingHorizontal = 18.dp
private val ButtonIconSize = 26.dp
private val SendIconSize = 30.dp
private val ButtonIconSpacing = 8.dp
private val HintSpacing = 6.dp
private val ProgressHeight = 16.dp
private val OverheardRuleWidth = 3.dp
private val OverheardPadding = 10.dp
private val ProgressCornerRadius = 8.dp
private val RenameFieldTextSize = 20.sp
private val LcdMargin = 8.dp
private val LcdBubbleBorder = 2.dp
private val OrbBezelWidth = 3.dp
private val HeaderEdge = 2.dp
// VT323 draws small for its size, so LCD text is set larger to read like the 20 sp body text.
private val LcdTextSize = 26.sp
private val LcdLineHeight = 30.sp
private const val LCD_GLOW_ALPHA = 0.6f
private const val LCD_GLOW_BLUR = 10f
private const val LED_HIGHLIGHT = 0.5f
private const val LED_SHADE = 0.3f

internal fun displayName(assistantName: String): String = assistantName.ifBlank { FALLBACK_ASSISTANT_NAME }

@Composable
fun LimitsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = TopBarButtonHeight)
            .testTag("limits-button")
            .semantics { contentDescription = "Limits" },
        shape = KeyShape,
        colors = ledRedKeyColors(),
        contentPadding = TopBarButtonPadding
    ) {
        Text(
            text = "Limits",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    store: ChatStore,
    onOpenSettings: () -> Unit = {},
    onOpenNotes: () -> Unit = {},
    onOpenLimits: () -> Unit = {},
    sheetHost: @Composable () -> Unit = {}
) {
    val state by store.state.collectAsState()
    val name = displayName(state.assistantName)
    val isReady = state.modelStatus is ModelStatus.Ready
    // Landscape on a phone leaves too little height to stack the conversation over the controls. The
    // window decides, not the content area: the keyboard shrinks the content area below its width in
    // portrait, and a layout that followed it rebuilt the text field, dropped focus and closed the
    // keyboard on every tap (card 226).
    val windowSize = LocalWindowInfo.current.containerSize
    val wide = windowSize.width > windowSize.height

    HardwareFrame(
        title = name,
        wide = wide,
        onTitleClick = { store.dispatch(ChatIntent.OpenRename) },
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
            }
            LimitsButton(onClick = onOpenLimits)
            NetworkIndicator(active = state.networkActive)
            OutlinedButton(
                onClick = { store.dispatch(ChatIntent.NewChat) },
                modifier = Modifier
                    .heightIn(min = TopBarButtonHeight)
                    .semantics { contentDescription = "Clear conversation" },
                shape = KeyShape,
                colors = steelKeyColors(),
                contentPadding = TopBarButtonPadding
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonIconSize)
                )
            }
            OutlinedButton(
                onClick = onOpenNotes,
                modifier = Modifier.heightIn(min = TopBarButtonHeight),
                shape = KeyShape,
                colors = steelKeyColors(),
                contentPadding = TopBarButtonPadding
            ) {
                ButtonContent(icon = Icons.AutoMirrored.Filled.Notes, label = "Notes")
            }
        }
    ) { innerPadding ->
        ChatBody(store = store, state = state, isReady = isReady, wide = wide, innerPadding = innerPadding)
        sheetHost()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HardwareFrame(
    title: String,
    wide: Boolean,
    onTitleClick: () -> Unit,
    actions: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val titleBox: @Composable (Modifier) -> Unit = { titleModifier ->
        Row(
            modifier = titleModifier
                .heightIn(min = TopBarButtonHeight)
                .clickable(
                    onClickLabel = "Rename",
                    role = SemanticsRole.Button,
                    onClick = onTitleClick
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TopBarSpacing)
        ) {
            OffwireMark()
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    val actionsRow: @Composable (Modifier) -> Unit = { rowModifier ->
        FlowRow(
            modifier = rowModifier.padding(horizontal = OuterPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(TopBarSpacing),
            verticalArrangement = Arrangement.spacedBy(TopBarSpacing)
        ) {
            actions()
        }
    }
    val hardware = LocalHardwareColors.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                CompositionLocalProvider(LocalContentColor provides hardware.onSteel) {
                    if (wide) {
                        // One row in landscape: with the keyboard up the window is about 200 dp tall, and
                        // a title row above the actions left the input row no height (card 229).
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .brushedSteel(hardware)
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                                )
                                .padding(start = OuterPadding),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            titleBox(Modifier.widthIn(max = WideTitleMaxWidth))
                            actionsRow(Modifier.weight(1f))
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .brushedSteel(hardware)
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                                )
                                .padding(top = HeaderEdge, bottom = HeaderEdge)
                        ) {
                            titleBox(Modifier.padding(horizontal = OuterPadding))
                            actionsRow(Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            content = content
        )
    }
}

@Composable
private fun ChatBody(
    store: ChatStore,
    state: ChatState,
    isReady: Boolean,
    wide: Boolean,
    innerPadding: PaddingValues
) {
    val name = displayName(state.assistantName)
    state.renameDraft?.let { draft ->
        RenameDialog(
            draft = draft,
            error = state.renameError,
            onDraftChange = { store.dispatch(ChatIntent.RenameInputChanged(it)) },
            onSave = { store.dispatch(ChatIntent.SaveRename) },
            onCancel = { store.dispatch(ChatIntent.CancelRename) }
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        val isLive = state.livePhase != LivePhase.IDLE
        val conversation: @Composable ColumnScope.() -> Unit = {
            if (!isReady) {
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
            LcdPanel(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = OuterPadding, vertical = LcdMargin)
            ) {
                MessageList(messages = state.messages, modifier = Modifier.fillMaxSize())
            }
            OfflineVoiceNotice(
                missing = state.offlineVoiceMissing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OuterPadding, vertical = ItemSpacing)
            )
            if (state.micDenied) {
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
        val livePanel: @Composable (Modifier) -> Unit = { panelModifier ->
            LiveVoicePanel(
                assistantName = name,
                phase = state.livePhase,
                micMuted = state.micMuted,
                liveLevel = state.liveLevel,
                voiceDetected = state.voiceDetected,
                wakeWordEnabled = state.wakeWordEnabled,
                onToggleMute = { store.dispatch(ChatIntent.ToggleMute) },
                onToggleWakeWord = { store.dispatch(ChatIntent.ToggleWakeWord) },
                onTap = { store.dispatch(ChatIntent.LiveTap) },
                onEnd = { store.dispatch(ChatIntent.ExitLiveMode) },
                sideBySide = wide,
                modifier = panelModifier
            )
        }
        val inputBar: @Composable (Modifier) -> Unit = { barModifier ->
            InputBar(
                text = state.input,
                enabled = isReady,
                isGenerating = state.isGenerating,
                onTextChange = { store.dispatch(ChatIntent.InputChanged(it)) },
                onSend = { store.dispatch(ChatIntent.Send) },
                onStop = { store.dispatch(ChatIntent.Stop) },
                onMicClick = { store.dispatch(ChatIntent.EnterLiveMode) },
                modifier = barModifier
            )
        }
        val resourceMeter: @Composable (Modifier) -> Unit = { meterModifier ->
            ResourceMeter(sample = state.resources, modifier = meterModifier, speed = state.replySpeed)
        }

        ChatLayout(
            wide = wide,
            isLive = isLive,
            conversation = conversation,
            livePanel = livePanel,
            resourceMeter = resourceMeter,
            inputBar = inputBar
        )
    }
}

// Landscape puts the live panel beside the conversation; otherwise the controls stack under it.
@Composable
private fun ChatLayout(
    wide: Boolean,
    isLive: Boolean,
    conversation: @Composable ColumnScope.() -> Unit,
    livePanel: @Composable (Modifier) -> Unit,
    resourceMeter: @Composable (Modifier) -> Unit,
    inputBar: @Composable (Modifier) -> Unit
) {
    when {
        wide && isLive -> Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), content = conversation)
            Column(modifier = Modifier.width(SidePanelWidth).fillMaxHeight()) {
                resourceMeter(Modifier.padding(horizontal = OuterPadding))
                livePanel(Modifier.fillMaxWidth().weight(1f))
            }
        }
        wide -> Column(modifier = Modifier.fillMaxSize()) {
            conversation()
            resourceMeter(Modifier.padding(horizontal = OuterPadding))
            // The row keeps its own height however little the keyboard leaves; the conversation
            // above it is what gives way (card 229).
            Row(
                modifier = Modifier.wrapContentHeight(unbounded = true),
                verticalAlignment = Alignment.CenterVertically
            ) {
                inputBar(Modifier.weight(1f))
            }
        }
        else -> Column(modifier = Modifier.fillMaxSize()) {
            conversation()
            resourceMeter(Modifier.padding(horizontal = OuterPadding))
            if (isLive) {
                livePanel(Modifier.fillMaxWidth())
            } else {
                inputBar(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun RenameDialog(
    draft: String,
    error: String?,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = "What should I be called?", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HintSpacing)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    singleLine = true,
                    isError = error != null,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = RenameFieldTextSize),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (error == null) onSave() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Short, familiar words are heard best.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            RockerButton(
                onClick = onSave,
                enabled = error == null,
                modifier = Modifier.heightIn(min = TouchTargetHeight),
                contentPadding = ButtonPadding
            ) {
                Text(text = "Save", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.heightIn(min = TouchTargetHeight),
                shape = KeyShape,
                contentPadding = ButtonPadding
            ) {
                Text(text = "Cancel", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
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

@Composable
private fun NetworkIndicator(active: Boolean) {
    if (!active) return
    CircularProgressIndicator(
        modifier = Modifier
            .size(20.dp)
            .semantics { contentDescription = "Network active" },
        strokeWidth = 2.dp
    )
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
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
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
        LabelTape("Model")
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
                    RockerButton(
                        onClick = onConfirmDownload,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = TouchTargetHeight),
                        contentPadding = ButtonPadding
                    ) {
                        Text("Download")
                    }
                    OutlinedButton(
                        onClick = onCancelDownload,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = TouchTargetHeight),
                        shape = KeyShape,
                        contentPadding = ButtonPadding
                    ) {
                        Text("Cancel")
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
                    RockerButton(
                        onClick = onToggleCellular,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = TouchTargetHeight),
                        contentPadding = ButtonPadding
                    ) {
                        Text("Allow cellular")
                    }
                    OutlinedButton(
                        onClick = onCancelDownload,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = TouchTargetHeight),
                        shape = KeyShape,
                        contentPadding = ButtonPadding
                    ) {
                        Text("Cancel")
                    }
                }
            }
            is ModelStatus.Downloading -> LinearProgressIndicator(
                progress = { status.fraction.coerceIn(0f, 1f) },
                color = LocalHardwareColors.current.ledGreen,
                trackColor = LocalHardwareColors.current.ledPanel,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ProgressHeight)
                    .clip(RoundedCornerShape(ProgressCornerRadius))
            )
            ModelStatus.Loading -> LinearProgressIndicator(
                color = LocalHardwareColors.current.ledGreen,
                trackColor = LocalHardwareColors.current.ledPanel,
                gapSize = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ProgressHeight)
                    .clip(RoundedCornerShape(ProgressCornerRadius))
            )
            ModelStatus.Missing, is ModelStatus.Error -> RockerButton(
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
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    // Follow the newest text until the reader scrolls away, and again once they return to the end.
    var followEnd by remember { mutableStateOf(true) }

    LaunchedEffect(isDragged, listState.isScrollInProgress) {
        if (isDragged) {
            followEnd = false
        } else if (!listState.isScrollInProgress) {
            followEnd = !listState.canScrollForward
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            followEnd = true
            listState.scrollToItem(messages.lastIndex, scrollOffset = Int.MAX_VALUE)
        }
    }
    LaunchedEffect(lastMessageText) {
        if (followEnd && messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex, scrollOffset = Int.MAX_VALUE)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = OuterPadding, vertical = ItemSpacing),
        verticalArrangement = Arrangement.spacedBy(ItemSpacing)
    ) {
        items(items = messages, key = { it.id }) { message ->
            MessageBubble(
                role = message.role,
                text = message.text,
                isStreaming = message.isStreaming,
                isVoice = message.isVoice
            )
        }
    }
}

// Pixel text for the LCD. The dark theme's VFD glass gives it a cyan glow.
@Composable
private fun lcdTextStyle(): TextStyle {
    val hardware = LocalHardwareColors.current
    return TextStyle(
        fontFamily = LocalHardwareFonts.current.lcd,
        fontSize = LcdTextSize,
        lineHeight = LcdLineHeight,
        shadow = if (hardware.lcdGlow) {
            Shadow(color = hardware.lcdInk.copy(alpha = LCD_GLOW_ALPHA), blurRadius = LCD_GLOW_BLUR)
        } else {
            null
        }
    )
}

// Heard but not asked: a dim quote with no bubble, so it never reads as part of the conversation.
@Composable
private fun OverheardLine(text: String) {
    val hardware = LocalHardwareColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) { contentDescription = "Heard: $text" }
    ) {
        Box(
            modifier = Modifier
                .width(OverheardRuleWidth)
                .fillMaxHeight()
                .background(hardware.lcdInkDim)
        )
        Text(
            text = "\u201C$text\u201D",
            style = lcdTextStyle(),
            fontStyle = FontStyle.Italic,
            color = hardware.lcdInkDim,
            modifier = Modifier.padding(horizontal = OverheardPadding, vertical = OverheardRuleWidth)
        )
    }
}

@Composable
private fun MessageBubble(
    role: Role,
    text: String,
    isStreaming: Boolean,
    isVoice: Boolean) {
    if (role == Role.OVERHEARD) {
        OverheardLine(text)
        return
    }
    val hardware = LocalHardwareColors.current
    val isUser = role == Role.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    // On the LCD the user's lines are inverted segments and the assistant's are outlined.
    val containerColor = if (isUser) hardware.lcdInk else Color.Transparent
    val contentColor = if (isUser) hardware.lcd else hardware.lcdInk
    val displayText = if (isStreaming) "$text▍" else text

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(BubbleCornerRadius),
            color = containerColor,
            contentColor = contentColor,
            modifier = Modifier
                .widthIn(max = BubbleMaxWidth)
                .border(LcdBubbleBorder, hardware.lcdInk, RoundedCornerShape(BubbleCornerRadius))
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = BubbleHorizontalPadding,
                    vertical = BubbleVerticalPadding
                )
            ) {
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isUser && isVoice) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice message",
                            modifier = Modifier.size(VoiceIconSize)
                        )
                        Spacer(modifier = Modifier.width(ButtonIconSpacing))
                    }
                    Text(
                        text = displayText,
                        style = if (isUser) lcdTextStyle().copy(shadow = null) else lcdTextStyle(),
                    )
                }
            }
        }
    }
}

private data class OrbVisuals(
    val orbBrush: Brush,
    val iconTint: Color,
    val orbIcon: ImageVector,
    val contentDescription: String,
    val ringScale: Float,
    val ringAlpha: Float,
    val ringColor: Color
)

// A lit LED: a bright hot spot fading to a darker rim.
private fun led(color: Color): Brush =
    Brush.radialGradient(listOf(lerp(color, Color.White, LED_HIGHLIGHT), color, lerp(color, Color.Black, LED_SHADE)))

private fun unlit(hardware: HardwareColors): Brush = SolidColor(hardware.ledPanel)

private fun mutedVisuals(hardware: HardwareColors): OrbVisuals = OrbVisuals(
    orbBrush = unlit(hardware),
    iconTint = hardware.readoutDim,
    orbIcon = Icons.Default.MicOff,
    contentDescription = "Microphone off",
    ringScale = DEFAULT_SCALE,
    ringAlpha = ZERO_ALPHA,
    ringColor = Color.Transparent
)

private fun listeningVisuals(
    hardware: HardwareColors,
    liveLevel: Float,
    voiceDetected: Boolean
): OrbVisuals {
    val clampedLevel = liveLevel.coerceIn(0f, 1f)
    return OrbVisuals(
        orbBrush = led(hardware.ledGreen),
        iconTint = hardware.onLed,
        orbIcon = Icons.Default.Mic,
        contentDescription = if (voiceDetected) "Microphone listening, voice detected" else "Microphone listening",
        ringScale = DEFAULT_SCALE + clampedLevel * LIVE_LEVEL_SCALE_DELTA,
        ringAlpha = (RING_ALPHA_BASE + clampedLevel * RING_ALPHA_LEVEL_SCALE).coerceIn(0f, 1f),
        ringColor = if (voiceDetected) hardware.readout else hardware.ledGreen
    )
}

private fun thinkingVisuals(
    hardware: HardwareColors,
    micMuted: Boolean,
    pulseAlpha: Float
): OrbVisuals = OrbVisuals(
    orbBrush = if (micMuted) unlit(hardware) else led(hardware.ledAmber),
    iconTint = if (micMuted) hardware.readoutDim else hardware.onLed,
    orbIcon = if (micMuted) Icons.Default.MicOff else Icons.Default.Mic,
    contentDescription = if (micMuted) "Assistant thinking, microphone off" else "Assistant thinking",
    ringScale = THINKING_RING_SCALE,
    ringAlpha = pulseAlpha,
    ringColor = hardware.ledAmber
)

private fun speakingVisuals(
    hardware: HardwareColors,
    micMuted: Boolean,
    liveLevel: Float
): OrbVisuals {
    val clampedLevel = liveLevel.coerceIn(0f, 1f)
    return OrbVisuals(
        orbBrush = if (micMuted) unlit(hardware) else led(hardware.readout),
        iconTint = if (micMuted) hardware.readoutDim else hardware.onLed,
        orbIcon = if (micMuted) Icons.Default.MicOff else Icons.AutoMirrored.Filled.VolumeUp,
        contentDescription = if (micMuted) "Assistant speaking, microphone off" else "Assistant speaking",
        ringScale = DEFAULT_SCALE + clampedLevel * LIVE_LEVEL_SCALE_DELTA,
        ringAlpha = (RING_ALPHA_BASE + clampedLevel * RING_ALPHA_LEVEL_SCALE).coerceIn(0f, 1f),
        ringColor = hardware.readout
    )
}

private fun resolveOrbVisuals(
    phase: LivePhase,
    micMuted: Boolean,
    liveLevel: Float,
    voiceDetected: Boolean,
    thinkingAlpha: Float,
    hardware: HardwareColors
): OrbVisuals = when {
    micMuted && phase == LivePhase.LISTENING -> mutedVisuals(hardware)
    phase == LivePhase.LISTENING -> listeningVisuals(hardware, liveLevel, voiceDetected)
    phase == LivePhase.THINKING -> thinkingVisuals(hardware, micMuted, thinkingAlpha)
    phase == LivePhase.SPEAKING -> speakingVisuals(hardware, micMuted, liveLevel)
    else -> mutedVisuals(hardware)
}

@Composable
private fun LiveOrb(visuals: OrbVisuals) {
    Box(
        modifier = Modifier.size(OrbContainerSize),
        contentAlignment = Alignment.Center
    ) {
        if (visuals.ringAlpha > ZERO_ALPHA) {
            Box(
                modifier = Modifier
                    .size(OrbSize)
                    .scale(visuals.ringScale)
                    .background(
                        color = visuals.ringColor.copy(alpha = visuals.ringAlpha),
                        shape = CircleShape
                    )
            )
        }
        Box(
            modifier = Modifier
                .size(OrbSize)
                .border(OrbBezelWidth, LocalHardwareColors.current.bezel, CircleShape)
                .background(
                    brush = visuals.orbBrush,
                    shape = CircleShape
                )
                .semantics { this.contentDescription = visuals.contentDescription },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = visuals.orbIcon,
                contentDescription = null,
                tint = visuals.iconTint,
                modifier = Modifier.size(OrbIconSize)
            )
        }
    }
}

@Composable
private fun WakeWordControl(
    assistantName: String,
    wakeWordEnabled: Boolean,
    onToggleWakeWord: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(WakeRowCornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .toggleable(
                    value = wakeWordEnabled,
                    role = SemanticsRole.Switch,
                    onValueChange = { onToggleWakeWord() }
                )
                .heightIn(min = TouchTargetHeight)
                .padding(horizontal = WakeRowPaddingHorizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItemSpacing)
        ) {
            Text(
                text = "Only answer when I say \"$assistantName\"",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = wakeWordEnabled,
                onCheckedChange = null,
                modifier = Modifier.scale(WAKE_SWITCH_SCALE)
            )
        }
    }
}

@Composable
private fun LivePanelControls(
    micMuted: Boolean,
    onToggleMute: () -> Unit,
    onEnd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TargetSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onToggleMute,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = TouchTargetHeight),
            shape = KeyShape,
            contentPadding = ButtonPadding
        ) {
            ButtonContent(
                icon = if (micMuted) Icons.Default.Mic else Icons.Default.MicOff,
                label = if (micMuted) "Unmute" else "Mute"
            )
        }
        Button(
            onClick = onEnd,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = TouchTargetHeight),
            shape = KeyShape,
            colors = ledRedKeyColors(),
            contentPadding = ButtonPadding
        ) {
            ButtonContent(icon = Icons.Default.Stop, label = "Stop")
        }
    }
}

@Composable
fun LiveVoicePanel(
    assistantName: String,
    phase: LivePhase,
    micMuted: Boolean,
    liveLevel: Float,
    voiceDetected: Boolean = false,
    wakeWordEnabled: Boolean = false,
    onToggleMute: () -> Unit = {},
    onToggleWakeWord: () -> Unit = {},
    onTap: () -> Unit,
    onEnd: () -> Unit,
    sideBySide: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbAnimations")
    val thinkingAlpha by infiniteTransition.animateFloat(
        initialValue = THINKING_ALPHA_MIN,
        targetValue = THINKING_ALPHA_MAX,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = THINKING_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinkingAlpha"
    )

    val visuals = resolveOrbVisuals(
        phase = phase,
        micMuted = micMuted,
        liveLevel = liveLevel,
        voiceDetected = voiceDetected,
        thinkingAlpha = thinkingAlpha,
        hardware = LocalHardwareColors.current
    )

    Surface(
        shape = if (sideBySide) {
            RoundedCornerShape(topStart = PanelTopCornerRadius, bottomStart = PanelTopCornerRadius)
        } else {
            RoundedCornerShape(topStart = PanelTopCornerRadius, topEnd = PanelTopCornerRadius)
        },
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.clickable(onClick = onTap)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PanelPaddingHorizontal, vertical = PanelPaddingVertical),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ItemSpacing, Alignment.CenterVertically)
        ) {
            LabelTape("Live")
            LiveOrb(visuals)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HintSpacing)
            ) {
                val hearing = voiceDetected && !micMuted && phase == LivePhase.LISTENING
                val phaseLabel = formatLivePhaseLabel(phase, micMuted, assistantName, voiceDetected)
                Text(
                    text = phaseLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (hearing) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                val hintText = formatLiveHint(phase, micMuted, wakeWordEnabled, assistantName)
                if (hintText != null) {
                    Text(
                        text = hintText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            WakeWordControl(
                assistantName = assistantName,
                wakeWordEnabled = wakeWordEnabled,
                onToggleWakeWord = onToggleWakeWord
            )

            LivePanelControls(
                micMuted = micMuted,
                onToggleMute = onToggleMute,
                onEnd = onEnd
            )
        }
    }
}

@Composable
fun TalkButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    RockerButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = TalkButtonHeight),
        contentPadding = ButtonPadding
    ) {
        ButtonContent(icon = Icons.Default.Mic, label = "Talk")
    }
}

@Composable
fun InputBar(
    text: String,
    enabled: Boolean,
    isGenerating: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = OuterPadding, vertical = ItemSpacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TargetSpacing)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text(text = "Type a message", style = MaterialTheme.typography.bodyLarge) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = InputMinHeight),
            shape = MaterialTheme.shapes.medium,
            singleLine = false,
            maxLines = MAX_INPUT_LINES,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() })
        )
        // One action at a time keeps the text field wide: Stop while generating, Send once there is text, else Talk.
        when {
            isGenerating -> FilledIconButton(
                onClick = onStop,
                modifier = Modifier.size(InputMinHeight),
                shape = KeyShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = LocalHardwareColors.current.ledRed,
                    contentColor = LocalHardwareColors.current.onLed
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    modifier = Modifier.size(SendIconSize)
                )
            }
            text.isNotBlank() -> RockerButton(
                onClick = onSend,
                enabled = enabled,
                modifier = Modifier.heightIn(min = InputMinHeight),
                contentPadding = ButtonPadding
            ) {
                ButtonContent(icon = Icons.AutoMirrored.Filled.Send, label = "Send")
            }
            else -> TalkButton(
                enabled = enabled,
                onClick = onMicClick
            )
        }
    }
}

internal fun formatLivePhaseLabel(
    phase: LivePhase,
    micMuted: Boolean,
    assistantName: String,
    voiceDetected: Boolean = false
): String = when {
    micMuted && phase == LivePhase.LISTENING -> "Microphone is off"
    phase == LivePhase.LISTENING && voiceDetected -> "Hearing speech"
    phase == LivePhase.LISTENING -> "I'm listening"
    phase == LivePhase.THINKING -> "Thinking…"
    phase == LivePhase.SPEAKING -> "${displayName(assistantName)} is speaking"
    else -> ""
}

internal fun formatLiveHint(
    phase: LivePhase,
    micMuted: Boolean,
    wakeWordEnabled: Boolean,
    assistantName: String
): String? = when {
    micMuted && phase == LivePhase.LISTENING -> "Tap Unmute to talk again"
    phase == LivePhase.SPEAKING && !micMuted -> "Just start talking to interrupt"
    phase == LivePhase.THINKING || phase == LivePhase.SPEAKING -> "Tap anywhere to interrupt"
    wakeWordEnabled && phase == LivePhase.LISTENING -> "Say \"${displayName(assistantName)}\" to ask something"
    else -> null
}

internal fun formatModelCardTitle(status: ModelStatus, assistantName: String, modelName: String): String =
    when (status) {
        is ModelStatus.Downloading, ModelStatus.Loading -> "Getting ${displayName(assistantName)} ready"
        else -> modelName
    }

private fun formatStatusText(status: ModelStatus): String = when (status) {
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
