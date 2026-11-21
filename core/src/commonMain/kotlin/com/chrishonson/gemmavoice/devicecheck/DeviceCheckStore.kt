package com.chrishonson.gemmavoice.devicecheck

import androidx.lifecycle.ViewModel
import com.chrishonson.gemmavoice.limits.AcknowledgementStore
import com.chrishonson.gemmavoice.model.ModelSpec
import com.chrishonson.gemmavoice.model.Models
import com.chrishonson.gemmavoice.model.formatDownloadSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val RAM_TENTH_BYTES = 100_000_000L
private const val RAM_HALF_TENTH_BYTES = 50_000_000L
private const val TENTHS_PER_UNIT = 10

data class DeviceCheckState(
    val requiresCheck: Boolean,
    val handoffToLimits: Boolean,
    val modelName: String,
    val chipset: String,
    val totalRamBytes: Long,
    val freeStorageBytes: Long,
    val downloadBytes: Long,
    val requiredStorageBytes: Long,
    val testedDevice: Boolean,
    val lowRam: Boolean,
    val storageBlocked: Boolean,
    val verdict: String,
    val storageMessage: String
)

sealed interface DeviceCheckIntent {
    data object Continue : DeviceCheckIntent
}

class DeviceCheckStore(
    deviceInfo: DeviceInfo,
    private val completionStore: DeviceCheckCompletionStore,
    acknowledgementStore: AcknowledgementStore,
    config: DeviceCheckConfig = DEFAULT_DEVICE_CHECK_CONFIG,
    modelSpec: ModelSpec = Models.GEMMA_4_E2B,
    voiceSpec: ModelSpec? = Models.POCKET_TTS
) : ViewModel() {
    private val previouslyCompleted = completionStore.isDeviceCheckCompleted()
    private val previouslyAcknowledged = acknowledgementStore.isLimitsAcknowledged()
    private val downloadBytes = modelSpec.sizeBytes + (voiceSpec?.sizeBytes ?: 0L)
    private val requiredStorageBytes = downloadBytes + config.storageHeadroomBytes
    private val testedDevice = config.testedDevices.any { tested ->
        tested.modelName.equals(deviceInfo.modelName, ignoreCase = true) &&
            (tested.chipset == null || tested.chipset.equals(deviceInfo.chipset, ignoreCase = true))
    }
    private val lowRam = deviceInfo.totalRamBytes < config.minRamBytes
    private val storageBlocked = deviceInfo.freeStorageBytes < requiredStorageBytes

    private val _state = MutableStateFlow(
        DeviceCheckState(
            requiresCheck = !previouslyCompleted && !previouslyAcknowledged,
            handoffToLimits = false,
            modelName = deviceInfo.modelName,
            chipset = deviceInfo.chipset,
            totalRamBytes = deviceInfo.totalRamBytes,
            freeStorageBytes = deviceInfo.freeStorageBytes,
            downloadBytes = downloadBytes,
            requiredStorageBytes = requiredStorageBytes,
            testedDevice = testedDevice,
            lowRam = lowRam,
            storageBlocked = storageBlocked,
            verdict = verdict(deviceInfo.totalRamBytes, testedDevice, lowRam),
            storageMessage = storageMessage(requiredStorageBytes, deviceInfo.freeStorageBytes, storageBlocked)
        )
    )
    val state: StateFlow<DeviceCheckState> = _state.asStateFlow()

    init {
        if (previouslyAcknowledged && !previouslyCompleted) {
            completionStore.saveDeviceCheckCompleted()
        }
    }

    fun dispatch(intent: DeviceCheckIntent) {
        when (intent) {
            DeviceCheckIntent.Continue -> {
                if (_state.value.requiresCheck && !_state.value.storageBlocked) {
                    completionStore.saveDeviceCheckCompleted()
                    _state.update { it.copy(requiresCheck = false, handoffToLimits = true) }
                }
            }
        }
    }
}

private fun verdict(totalRamBytes: Long, testedDevice: Boolean, lowRam: Boolean): String = when {
    testedDevice -> "This is a tested device."
    lowRam -> "Tested only on a Pixel 9 Pro. Your phone has ${formatRam(totalRamBytes)} RAM. " +
        "It is below the minimum and may be very slow or fail."
    else -> "Tested only on a Pixel 9 Pro. Your phone has ${formatRam(totalRamBytes)} RAM. " +
        "It may be slow or fail."
}

private fun storageMessage(requiredBytes: Long, freeBytes: Long, blocked: Boolean): String =
    if (blocked) {
        "Not enough free storage. ${formatDownloadSize(requiredBytes)} needed; " +
            "${formatDownloadSize(freeBytes)} free."
    } else {
        "Storage available: ${formatDownloadSize(freeBytes)} free; " +
            "${formatDownloadSize(requiredBytes)} needed."
    }

private fun formatRam(bytes: Long): String {
    val tenths = (bytes + RAM_HALF_TENTH_BYTES) / RAM_TENTH_BYTES
    return "${tenths / TENTHS_PER_UNIT}.${tenths % TENTHS_PER_UNIT} GB"
}
