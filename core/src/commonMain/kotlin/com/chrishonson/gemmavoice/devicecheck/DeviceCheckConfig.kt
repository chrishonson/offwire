package com.chrishonson.gemmavoice.devicecheck

data class TestedDevice(
    val modelName: String,
    val chipset: String? = null
)

data class DeviceCheckConfig(
    val testedDevices: List<TestedDevice>,
    val minRamBytes: Long,
    val storageHeadroomBytes: Long
)

val DEFAULT_DEVICE_CHECK_CONFIG = DeviceCheckConfig(
    testedDevices = listOf(TestedDevice(modelName = "Pixel 9 Pro", chipset = "Tensor G4")),
    minRamBytes = 8_000_000_000L,
    storageHeadroomBytes = 1_000_000_000L
)
