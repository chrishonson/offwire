package com.chrishonson.gemmavoice

/** Pure policy deciding whether an activity should hold FLAG_KEEP_SCREEN_ON. */
object KeepAwakePolicy {
    fun keepScreenOn(config: AppConfig, foreground: Boolean, listening: Boolean): Boolean =
        foreground && (config.keepScreenOnInForeground || listening)
}
