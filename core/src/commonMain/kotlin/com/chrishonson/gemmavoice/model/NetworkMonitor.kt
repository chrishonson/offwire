package com.chrishonson.gemmavoice.model

interface NetworkMonitor {
    fun isUnmetered(): Boolean
}

object AssumeUnmetered : NetworkMonitor {
    override fun isUnmetered(): Boolean = true
}
