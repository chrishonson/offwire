package com.chrishonson.gemmavoice.limits

import android.content.Context

private const val PREFS_NAME = "limits_acknowledgement"
private const val KEY_LIMITS_ACKNOWLEDGED = "limits_acknowledged"

class AndroidAcknowledgementStore(context: Context) : AcknowledgementStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isLimitsAcknowledged(): Boolean = prefs.getBoolean(KEY_LIMITS_ACKNOWLEDGED, false)

    override fun saveLimitsAcknowledged() {
        prefs.edit().putBoolean(KEY_LIMITS_ACKNOWLEDGED, true).apply()
    }
}
