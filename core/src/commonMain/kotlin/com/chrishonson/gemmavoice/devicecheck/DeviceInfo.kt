package com.chrishonson.gemmavoice.devicecheck

interface DeviceInfo {
    val totalRamBytes: Long
    val freeStorageBytes: Long
    val modelName: String
    val chipset: String
}

data class FakeDeviceInfo(
    override val totalRamBytes: Long,
    override val freeStorageBytes: Long,
    override val modelName: String,
    override val chipset: String
) : DeviceInfo

object UnknownDeviceInfo : DeviceInfo {
    override val totalRamBytes: Long = 8_000_000_000L
    override val freeStorageBytes: Long = 10_000_000_000L
    override val modelName: String = "Unknown device"
    override val chipset: String = "Unknown chipset"
}
