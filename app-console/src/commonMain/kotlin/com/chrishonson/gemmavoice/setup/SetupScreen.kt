package com.chrishonson.gemmavoice.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.chrishonson.gemmavoice.Lcars
import com.chrishonson.gemmavoice.LcarsBar
import com.chrishonson.gemmavoice.LcarsFrame
import com.chrishonson.gemmavoice.NavPill
import com.chrishonson.gemmavoice.chat.ChatIntent
import com.chrishonson.gemmavoice.chat.ChatState
import com.chrishonson.gemmavoice.chat.ModelCard
import com.chrishonson.gemmavoice.chat.allowsModelSwitch
import com.chrishonson.gemmavoice.chat.displayName
import com.chrishonson.gemmavoice.lcarsBlockShape
import com.chrishonson.gemmavoice.model.ModelSpec
import com.chrishonson.gemmavoice.model.formatDownloadSize

private const val WAKE_SWITCH_SCALE = 1.25f
private val ContentPaddingVertical = 16.dp
private val RowSpacing = 12.dp
private val InnerSpacing = 4.dp
private val InnerPadding = 16.dp
private val MinRowHeight = 60.dp
private val ConsolePillHeight = 72.dp
private val ButtonPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)

internal fun microphoneStatusText(micDenied: Boolean): String = if (micDenied) "Blocked" else "Allowed"

internal fun wakeWordSubtext(assistantName: String): String =
    "Only answer after \"${displayName(assistantName)}\""

@Composable
fun SetupScreen(
    state: ChatState,
    onIntent: (ChatIntent) -> Unit,
    onBack: () -> Unit,
    onOpenLimits: () -> Unit = {},
    appVersion: String = "",
    sheetHost: @Composable () -> Unit = {}
) {
    LcarsFrame(
        title = "Setup",
        onOpenLimits = onOpenLimits,
        actions = {
            NavPill(label = "Console", color = Color(Lcars.ORANGE), onClick = onBack)
        }
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(vertical = ContentPaddingVertical),
            verticalArrangement = Arrangement.spacedBy(RowSpacing)
        ) {
            SetupSwitchRow(
                title = "WAKE WORD",
                subtitle = wakeWordSubtext(state.assistantName),
                checked = state.wakeWordEnabled,
                onCheckedChange = { onIntent(ChatIntent.ToggleWakeWord) },
                switchTestTag = "wakeWordSwitch"
            )
            SetupSwitchRow(
                title = "LISTEN AT START",
                subtitle = "Open the console already listening",
                checked = state.autoListen,
                onCheckedChange = { onIntent(ChatIntent.ToggleAutoListen) },
                switchTestTag = "listenAtStartSwitch"
            )
            SetupSwitchRow(
                title = "RESOURCE METER",
                subtitle = "Show CPU and memory while responding",
                checked = state.showResourceMeter,
                onCheckedChange = { onIntent(ChatIntent.ToggleResourceMeter) },
                switchTestTag = "resourceMeterSwitch"
            )
            SetupSwitchRow(
                title = "THINKING",
                subtitle = "Reason before answering. Replies start later.",
                checked = state.thinking,
                onCheckedChange = { onIntent(ChatIntent.ToggleThinking) },
                switchTestTag = "thinkingSwitch"
            )
            state.models.forEach { model ->
                ModelChoiceRow(
                    model = model,
                    selected = model.id == state.modelId,
                    enabled = state.modelStatus.allowsModelSwitch(),
                    onSelect = { onIntent(ChatIntent.SelectModel(model.id)) }
                )
            }
            ModelCard(
                assistantName = displayName(state.assistantName),
                modelName = state.modelName,
                status = state.modelStatus,
                onDownload = { onIntent(ChatIntent.DownloadModel) },
                onConfirmDownload = { onIntent(ChatIntent.ConfirmDownload) },
                onCancelDownload = { onIntent(ChatIntent.CancelDownload) },
                onToggleCellular = { onIntent(ChatIntent.ToggleCellularDownload) },
                modifier = Modifier.fillMaxWidth()
            )
            MicrophoneRow(
                micDenied = state.micDenied
            )
            Button(
                onClick = { onIntent(ChatIntent.NewChat) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ConsolePillHeight)
                    .testTag("clearLogPill"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(Lcars.TOMATO),
                    contentColor = Color(Lcars.BLACK)
                ),
                contentPadding = ButtonPadding
            ) {
                Text(
                    text = "CLEAR LOG",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            if (appVersion.isNotBlank()) {
                Text(
                    text = "VERSION $appVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag("appVersion")
                )
            }
        }
        sheetHost()
    }
}

@Composable
private fun SetupSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            LcarsBar(MaterialTheme.colorScheme.secondary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinRowHeight)
                    .padding(InnerPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RowSpacing)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(InnerSpacing)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier
                        .scale(WAKE_SWITCH_SCALE)
                        .testTag(switchTestTag)
                )
            }
        }
    }
}

@Composable
private fun ModelChoiceRow(
    model: ModelSpec,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .testTag("model-${model.id}"),
        shape = lcarsBlockShape(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            LcarsBar(MaterialTheme.colorScheme.secondary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinRowHeight)
                    .padding(InnerPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RowSpacing)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(InnerSpacing)
                ) {
                    Text(
                        text = model.displayName.uppercase(),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${formatDownloadSize(model.sizeBytes)} download",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RadioButton(selected = selected, onClick = null, enabled = enabled)
            }
        }
    }
}

@Composable
private fun MicrophoneRow(
    micDenied: Boolean,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            LcarsBar(MaterialTheme.colorScheme.secondary)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinRowHeight)
                    .padding(InnerPadding),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "MICROPHONE",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(InnerSpacing))
                Text(
                    text = microphoneStatusText(micDenied),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("micStatus")
                )
            }
        }
    }
}
