@file:OptIn(ExperimentalForeignApi::class)

package com.chrishonson.gemmavoice.devicecheck

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIDevice

class IosDeviceInfo : DeviceInfo {
    override val totalRamBytes: Long
        get() = NSProcessInfo.processInfo.physicalMemory.toLong()

    override val freeStorageBytes: Long
        get() {
            val attributes = NSFileManager.defaultManager.attributesOfFileSystemForPath(
                NSHomeDirectory(),
                error = null
            )
            return (attributes?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue ?: 0L
        }

    override val modelName: String
        get() = UIDevice.currentDevice.model

    // Foundation does not expose a stable marketing chipset name.
    override val chipset: String
        get() = "Apple silicon"
}
