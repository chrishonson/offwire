package com.chrishonson.gemmavoice

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeepAwakePolicyTest {
    private val defaultConfig = AppConfig(
        clonedVoice = false,
        renameAssistant = false,
        keepScreenOnInForeground = true
    )
    private val disabledConfig = AppConfig(
        clonedVoice = false,
        renameAssistant = false,
        keepScreenOnInForeground = false
    )

    @Test
    fun listeningInForegroundWithConfigFlagOffIsOn() {
        assertTrue(KeepAwakePolicy.keepScreenOn(disabledConfig, foreground = true, listening = true))
    }

    @Test
    fun idleInForegroundWithConfigFlagOffIsOff() {
        assertFalse(KeepAwakePolicy.keepScreenOn(disabledConfig, foreground = true, listening = false))
    }

    @Test
    fun idleInForegroundWithConfigFlagOnIsOn() {
        assertTrue(KeepAwakePolicy.keepScreenOn(defaultConfig, foreground = true, listening = false))
    }

    @Test
    fun listeningInBackgroundIsOff() {
        assertFalse(KeepAwakePolicy.keepScreenOn(defaultConfig, foreground = false, listening = true))
        assertFalse(KeepAwakePolicy.keepScreenOn(disabledConfig, foreground = false, listening = true))
    }

    @Test
    fun idleInBackgroundIsOff() {
        assertFalse(KeepAwakePolicy.keepScreenOn(defaultConfig, foreground = false, listening = false))
        assertFalse(KeepAwakePolicy.keepScreenOn(disabledConfig, foreground = false, listening = false))
    }
}
