package com.chrishonson.gemmavoice.devicecheck

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs

class AndroidDeviceInfo(context: Context) : DeviceInfo {
    private val appContext = context.applicationContext ?: context

    override val totalRamBytes: Long
        get() = memoryInfo().totalMem

    /** Memory the app can still use right now, read when the model loads to size its context. */
    val availableRamBytes: Long
        get() = memoryInfo().availMem

    private fun memoryInfo(): ActivityManager.MemoryInfo {
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
    }

    override val freeStorageBytes: Long
        get() = StatFs(appContext.filesDir.path).availableBytes

    override val modelName: String
        get() = Build.MODEL

    override val chipset: String
        get() = Build.SOC_MODEL
}
