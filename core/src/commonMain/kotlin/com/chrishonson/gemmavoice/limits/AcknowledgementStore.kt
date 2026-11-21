package com.chrishonson.gemmavoice.limits

interface AcknowledgementStore {
    fun isLimitsAcknowledged(): Boolean
    fun saveLimitsAcknowledged()
}

class InMemoryAcknowledgementStore(private var acknowledged: Boolean = false) : AcknowledgementStore {
    override fun isLimitsAcknowledged(): Boolean = acknowledged

    override fun saveLimitsAcknowledged() {
        acknowledged = true
    }
}
