package com.chrishonson.gemmavoice.devicecheck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.chrishonson.gemmavoice.LabelTape
import com.chrishonson.gemmavoice.OffwireMark
import com.chrishonson.gemmavoice.RockerButton
import com.chrishonson.gemmavoice.model.formatDownloadSize

@Composable
fun DeviceCheckScreen(store: DeviceCheckStore) {
    val state by store.state.collectAsState()
    DeviceCheckContent(
        modelName = state.modelName,
        chipset = state.chipset,
        verdict = state.verdict,
        storageMessage = state.storageMessage,
        downloadSize = formatDownloadSize(state.downloadBytes),
        storageBlocked = state.storageBlocked,
        onContinue = { store.dispatch(DeviceCheckIntent.Continue) }
    )
}

@Composable
fun DeviceCheckContent(
    modelName: String,
    chipset: String,
    verdict: String,
    storageMessage: String,
    downloadSize: String,
    storageBlocked: Boolean,
    onContinue: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .testTag("device-check-screen"),
            verticalArrangement = Arrangement.Center
        ) {
            OffwireMark(size = 72.dp)
            Spacer(Modifier.height(16.dp))
            LabelTape("System check")
            Spacer(Modifier.height(8.dp))
            Text("Device check", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(16.dp))
            Text("$modelName · $chipset", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Text(verdict, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Text(storageMessage, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Text("Model download: $downloadSize", style = MaterialTheme.typography.bodyLarge)
            Text("Downloads use Wi-Fi only by default.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            RockerButton(
                onClick = onContinue,
                enabled = !storageBlocked,
                modifier = Modifier.fillMaxWidth().testTag("device-check-continue")
            ) {
                Text(if (storageBlocked) "Free storage to continue" else "Continue")
            }
        }
    }
}
