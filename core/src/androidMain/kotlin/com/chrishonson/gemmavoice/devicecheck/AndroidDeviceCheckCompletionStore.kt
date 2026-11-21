package com.chrishonson.gemmavoice.devicecheck

import android.content.Context

private const val PREFS_NAME = "device_check"
private const val KEY_DEVICE_CHECK_COMPLETED = "device_check_completed"

class AndroidDeviceCheckCompletionStore(context: Context) : DeviceCheckCompletionStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isDeviceCheckCompleted(): Boolean = prefs.getBoolean(KEY_DEVICE_CHECK_COMPLETED, false)

    override fun saveDeviceCheckCompleted() {
        prefs.edit().putBoolean(KEY_DEVICE_CHECK_COMPLETED, true).apply()
    }
}
