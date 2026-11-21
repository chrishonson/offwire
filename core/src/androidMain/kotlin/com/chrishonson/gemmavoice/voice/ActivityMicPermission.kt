package com.chrishonson.gemmavoice.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred

class ActivityMicPermission(
    private val activity: ComponentActivity
) : MicPermission {
    private var pendingDeferred: CompletableDeferred<Boolean>? = null

    private val launcher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(RequestPermission()) { granted ->
            pendingDeferred?.complete(granted)
            pendingDeferred = null
        }

    override suspend fun ensureGranted(): Boolean {
        val current = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.RECORD_AUDIO
        )
        if (current == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        val deferred = CompletableDeferred<Boolean>()
        pendingDeferred = deferred
        launcher.launch(Manifest.permission.RECORD_AUDIO)
        return deferred.await()
    }
}
