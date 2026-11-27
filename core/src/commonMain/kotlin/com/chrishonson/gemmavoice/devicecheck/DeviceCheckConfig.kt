package com.chrishonson.gemmavoice.devicecheck

data class TestedDevice(
    val modelName: String,
    val chipset: String? = null
)

/**
 * [testedOn] names the tested hardware in the verdict, and [deviceNoun] is what the verdict calls the user's device.
 */
data class DeviceCheckConfig(
    val testedDevices: List<TestedDevice>,
    val minRamBytes: Long,
    val storageHeadroomBytes: Long,
    val testedOn: String = "a Pixel 9 Pro",
    val deviceNoun: String = "phone"
)

val DEFAULT_DEVICE_CHECK_CONFIG = DeviceCheckConfig(
    testedDevices = listOf(TestedDevice(modelName = "Pixel 9 Pro", chipset = "Tensor G4")),
    minRamBytes = 8_000_000_000L,
    storageHeadroomBytes = 1_000_000_000L
)

// UIDevice reports only "iPhone" or "iPad", so iOS names no tested model and judges by RAM. Gemma 4 E2B passed the
// live voice check on a 3 GB iPad (9th generation), which reports a little under 3 GB, so that is the floor.
val IOS_DEVICE_CHECK_CONFIG = DeviceCheckConfig(
    testedDevices = emptyList(),
    minRamBytes = 2_800_000_000L,
    storageHeadroomBytes = 1_000_000_000L,
    testedOn = "an iPad (9th generation)",
    deviceNoun = "device"
)
