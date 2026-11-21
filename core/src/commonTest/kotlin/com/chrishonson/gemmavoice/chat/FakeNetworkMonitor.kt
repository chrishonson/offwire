package com.chrishonson.gemmavoice.chat

import com.chrishonson.gemmavoice.model.NetworkMonitor

class FakeNetworkMonitor(var unmetered: Boolean = true) : NetworkMonitor {
    override fun isUnmetered(): Boolean = unmetered
}
