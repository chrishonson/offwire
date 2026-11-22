package com.chrishonson.gemmavoice.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.chrishonson.gemmavoice.LabelTape
import com.chrishonson.gemmavoice.RockerButton
import com.chrishonson.gemmavoice.SteelHeader
import com.chrishonson.gemmavoice.chat.ChatIntent
import com.chrishonson.gemmavoice.chat.ChatState
import com.chrishonson.gemmavoice.chat.ModelStatus
import com.chrishonson.gemmavoice.chat.allowsModelSwitch
import com.chrishonson.gemmavoice.model.ModelSpec
import com.chrishonson.gemmavoice.model.formatDownloadSize

private val ScreenPadding = 16.dp
private val RowPadding = 18.dp
private val RowSpacing = 12.dp
private val TouchTargetHeight = 60.dp
private val ButtonPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)

@Composable
fun SettingsScreen(
    state: ChatState,
    onIntent: (ChatIntent) -> Unit,
    onBack: () -> Unit,
    appVersion: String = "",
    /** Null hides the benchmark, as in release builds. */
    onOpenBenchmark: (() -> Unit)? = null
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            SteelHeader(
                title = "Settings",
                navigation = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to chat")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(RowSpacing)
        ) {
            LabelTape("Display")
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .clickable { onIntent(ChatIntent.ToggleResourceMeter) }
                        .heightIn(min = TouchTargetHeight)
                        .padding(horizontal = RowPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RowSpacing)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Resource meter", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Show CPU and memory while responding",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.showResourceMeter,
                        onCheckedChange = { onIntent(ChatIntent.ToggleResourceMeter) },
                        modifier = Modifier.testTag("resourceMeterSwitch")
                    )
                }
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .clickable { onIntent(ChatIntent.ToggleThinking) }
                        .heightIn(min = TouchTargetHeight)
                        .padding(horizontal = RowPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RowSpacing)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Thinking", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Reason before answering. Replies start later.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.thinking,
                        onCheckedChange = { onIntent(ChatIntent.ToggleThinking) },
                        modifier = Modifier.testTag("thinkingSwitch")
                    )
                }
            }
            LabelTape("Model")
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.selectableGroup()) {
                    state.models.forEach { model ->
                        ModelRow(
                            model = model,
                            selected = model.id == state.modelId,
                            enabled = state.modelStatus.allowsModelSwitch(),
                            onSelect = { onIntent(ChatIntent.SelectModel(model.id)) }
                        )
                    }
                }
            }
            if (onOpenBenchmark != null) {
                LabelTape("Tools")
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = TouchTargetHeight)
                            .padding(horizontal = RowPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(RowSpacing)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Benchmark", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Time ${state.modelName} on this device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RockerButton(
                            onClick = onOpenBenchmark,
                            enabled = state.modelStatus == ModelStatus.Ready,
                            modifier = Modifier.testTag("openBenchmark"),
                            contentPadding = ButtonPadding
                        ) {
                            Text("Open")
                        }
                    }
                }
            }
            if (appVersion.isNotBlank()) {
                Text(
                    "Version $appVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag("appVersion")
                )
            }
        }
    }
}

@Composable
private fun ModelRow(model: ModelSpec, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .heightIn(min = TouchTargetHeight)
            .padding(horizontal = RowPadding)
            .testTag("model-${model.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowSpacing)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(model.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                "${formatDownloadSize(model.sizeBytes)} download",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(selected = selected, onClick = null, enabled = enabled)
    }
}
