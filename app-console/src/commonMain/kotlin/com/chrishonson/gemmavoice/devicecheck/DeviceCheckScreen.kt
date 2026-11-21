package com.chrishonson.gemmavoice.devicecheck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.chrishonson.gemmavoice.Lcars
import com.chrishonson.gemmavoice.LcarsBar
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
        Row(modifier = Modifier.fillMaxSize().height(IntrinsicSize.Min)) {
            LcarsBar(Color(Lcars.TOMATO))
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
                    .testTag("device-check-screen"),
                verticalArrangement = Arrangement.Center
            ) {
                Text("DEVICE CHECK", style = MaterialTheme.typography.headlineLarge, color = Color(Lcars.TOMATO))
                Spacer(Modifier.height(16.dp))
                Text("$modelName · $chipset", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Text(verdict, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                Text(storageMessage, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                Text("MODEL DOWNLOAD $downloadSize", style = MaterialTheme.typography.titleMedium)
                Text("WI-FI ONLY BY DEFAULT", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onContinue,
                    enabled = !storageBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("device-check-continue"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(Lcars.ORANGE),
                        contentColor = Color(Lcars.BLACK)
                    )
                ) {
                    Text(if (storageBlocked) "FREE STORAGE TO CONTINUE" else "CONTINUE")
                }
            }
        }
    }
}
