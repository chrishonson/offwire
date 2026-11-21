package com.chrishonson.gemmavoice.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsStoreTest {
    @Test
    fun wakeWordAndAutoListenDistinguishNeverSavedFromSavedValues() {
        val settings = InMemorySettingsStore()

        assertNull(settings.loadWakeWord())
        assertNull(settings.loadAutoListen())

        settings.saveWakeWord(false)
        settings.saveAutoListen(true)

        assertEquals(false, settings.loadWakeWord())
        assertEquals(true, settings.loadAutoListen())
    }

    @Test
    fun resourceMeterDefaultsOffAndPersistsAcrossStoreUsers() {
        val settings = InMemorySettingsStore()

        assertFalse(settings.loadShowResourceMeter())
        settings.saveShowResourceMeter(true)

        assertTrue(settings.loadShowResourceMeter())
    }

    @Test
    fun thinkingDefaultsOffAndPersistsAcrossStoreUsers() {
        val settings = InMemorySettingsStore()

        assertFalse(settings.loadThinking())
        settings.saveThinking(true)

        assertTrue(settings.loadThinking())
    }
}
