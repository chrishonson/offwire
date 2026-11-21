package com.chrishonson.gemmavoice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chrishonson.gemmavoice.limits.LICENSE_NOTICES
import com.chrishonson.gemmavoice.limits.LicenseNotice

private val SheetPadding = 20.dp
private val ItemSpacing = 12.dp
private val NoticeSpacing = 4.dp
private val DialogMaxHeight = 560.dp
private val ButtonMinHeight = 48.dp
private const val SHEET_WIDTH_FRACTION = 0.92f

@Composable
fun LicensesSheet(
    licenses: List<LicenseNotice> = LICENSE_NOTICES,
    onClose: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(SHEET_WIDTH_FRACTION)
                .heightIn(max = DialogMaxHeight)
                .testTag("licenses-sheet")
                .semantics { contentDescription = "Licenses Sheet" },
            shape = lcarsBlockShape(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                LcarsBar(Color(Lcars.TOMATO))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SheetPadding)
                ) {
                    Text(
                        text = "Licenses",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color(Lcars.TOMATO)
                    )

                    Spacer(modifier = Modifier.height(ItemSpacing))

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(ItemSpacing)
                    ) {
                        licenses.forEach { notice ->
                            Column(verticalArrangement = Arrangement.spacedBy(NoticeSpacing)) {
                                Text(
                                    text = notice.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = notice.license,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                notice.url?.let { url ->
                                    Text(
                                        text = url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(Lcars.SUNFLOWER),
                                        textDecoration = TextDecoration.Underline,
                                        modifier = Modifier.clickable {
                                            uriHandler.openUri(url)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(ItemSpacing))

                    Button(
                        onClick = onClose,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = ButtonMinHeight)
                            .testTag("licenses-close-button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
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
