package com.chrishonson.gemmavoice.devicecheck

interface DeviceCheckCompletionStore {
    fun isDeviceCheckCompleted(): Boolean
    fun saveDeviceCheckCompleted()
}

class InMemoryDeviceCheckCompletionStore(
    private var completed: Boolean = false
) : DeviceCheckCompletionStore {
    override fun isDeviceCheckCompleted(): Boolean = completed

    override fun saveDeviceCheckCompleted() {
        completed = true
    }
}
