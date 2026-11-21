package com.chrishonson.gemmavoice.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.chrishonson.gemmavoice.Lcars
import com.chrishonson.gemmavoice.limits.APACHE_LICENSE_URL
import com.chrishonson.gemmavoice.limits.MODEL_LICENSE_NOTICE

private val NoticeSpacing = 4.dp

@Composable
fun ModelLicenseNotice(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier.testTag("model-license-notice"),
        verticalArrangement = Arrangement.spacedBy(NoticeSpacing)
    ) {
        Text(
            text = MODEL_LICENSE_NOTICE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Apache License 2.0",
            style = MaterialTheme.typography.labelMedium,
            color = Color(Lcars.SUNFLOWER),
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable {
                uriHandler.openUri(APACHE_LICENSE_URL)
            }
        )
    }
}
