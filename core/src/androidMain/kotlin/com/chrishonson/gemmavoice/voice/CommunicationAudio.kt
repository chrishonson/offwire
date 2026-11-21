package com.chrishonson.gemmavoice.voice

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

private const val LOG_TAG = "GemmaVoice"

// Puts the phone in call audio mode while the mic is live so the platform echo canceller uses the
// speech output as its reference. Without it the assistant hears its own voice and interrupts itself.
class CommunicationAudio(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var previousMode = AudioManager.MODE_NORMAL

    private val routeListeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    var isActive: Boolean = false
        private set

    // Speech already playing keeps the stream it started on, so players move it themselves when the route changes.
    fun addRouteListener(listener: () -> Unit) {
        routeListeners += listener
    }

    fun enter() {
        previousMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        // Call mode defaults to the earpiece. Prefer a headset when one is connected, else the loudspeaker.
        val devices = audioManager.availableCommunicationDevices
        val device = devices.firstOrNull { it.type in HEADSET_TYPES }
            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val routed = device != null && audioManager.setCommunicationDevice(device)
        isActive = true
        // Read the mode back: without MODIFY_AUDIO_SETTINGS the platform refuses setMode silently
        // (logcat "Audio Settings Permission Denial"), and only the route call above succeeds.
        val mode = audioManager.mode
        if (mode != AudioManager.MODE_IN_COMMUNICATION) {
            Log.w(LOG_TAG, "Call audio mode refused, mode=$mode; speech output and echo cancellation will suffer")
        }
        Log.i(LOG_TAG, "Call audio on, mode=$mode, routed to ${device?.type} ok=$routed")
        routeListeners.forEach { it() }
    }

    fun exit() {
        audioManager.clearCommunicationDevice()
        audioManager.mode = previousMode
        isActive = false
        Log.i(LOG_TAG, "Call audio off")
        routeListeners.forEach { it() }
    }

    private companion object {
        val HEADSET_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        )
    }
}
