package com.chrishonson.gemmavoice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chrishonson.gemmavoice.limits.Safeguard
import com.chrishonson.gemmavoice.limits.limitsSheetLines

private val SheetPadding = 24.dp
private val ItemSpacing = 12.dp
private val DialogMaxHeight = 560.dp
private val ButtonMinHeight = 48.dp
private const val SHEET_WIDTH_FRACTION = 0.92f
private const val HEADING_INDEX = 0
private const val MEASURES_HEADER_INDEX = 3

@Composable
fun LimitsSheet(
    requiresAcknowledgement: Boolean,
    safeguards: List<Safeguard> = emptyList(),
    onOpenLicenses: () -> Unit = {},
    onAcknowledge: () -> Unit = {},
    onDismiss: () -> Unit = {},
    lines: List<String> = limitsSheetLines(safeguards)
) {
    Dialog(
        onDismissRequest = {
            if (!requiresAcknowledgement) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !requiresAcknowledgement,
            dismissOnClickOutside = !requiresAcknowledgement,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(SHEET_WIDTH_FRACTION)
                .heightIn(max = DialogMaxHeight)
                .testTag("limits-sheet")
                .semantics { contentDescription = "Limits Sheet" },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SheetPadding)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ItemSpacing)
                ) {
                    lines.forEachIndexed { index, line ->
                        when (index) {
                            HEADING_INDEX -> Text(
                                text = line,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            MEASURES_HEADER_INDEX -> Text(
                                text = line,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            else -> Text(
                                text = line,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (index == lines.lastIndex) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(ItemSpacing))

                Button(
                    onClick = onOpenLicenses,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ButtonMinHeight)
                        .testTag("limits-licenses-button"),
                    shape = KeyShape,
                    colors = panelKeyColors()
                ) {
                    Text(
                        text = "Licenses",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(ItemSpacing))

                if (requiresAcknowledgement) {
                    RockerButton(
                        onClick = onAcknowledge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = ButtonMinHeight)
                            .testTag("limits-acknowledge-button")
                    ) {
                        Text(
                            text = "I understand",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = ButtonMinHeight)
                            .testTag("limits-close-button"),
                        shape = KeyShape,
                        colors = panelKeyColors()
                    ) {
                        Text(
                            text = "Close",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
