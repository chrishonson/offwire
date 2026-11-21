package com.chrishonson.gemmavoice.limits

import platform.Foundation.NSUserDefaults

private const val KEY_LIMITS_ACKNOWLEDGED = "limits_acknowledged"

class IosAcknowledgementStore : AcknowledgementStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun isLimitsAcknowledged(): Boolean = defaults.boolForKey(KEY_LIMITS_ACKNOWLEDGED)

    override fun saveLimitsAcknowledged() {
        defaults.setBool(true, forKey = KEY_LIMITS_ACKNOWLEDGED)
    }
}
