package com.chrishonson.gemmavoice

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AppNavigationTest {

    @Test
    fun consolePillOpensSetup() {
        assertEquals(AppScreen.SETUP, navigate(AppScreen.CHAT, NavigationEvent.OPEN_SETUP))
    }

    @Test
    fun backReturnsFromSetupToConsole() {
        assertEquals(AppScreen.CHAT, navigate(AppScreen.SETUP, NavigationEvent.BACK_TO_CHAT))
    }

    @Test
    fun appScreenSaverRoundTripsEveryScreen() {
        val scope = SaverScope { true }
        for (screen in AppScreen.entries) {
            val saved = with(AppScreenSaver) { scope.save(screen) }
            assertNotNull(saved)
            assertEquals(screen.name, saved)
            assertEquals(screen, AppScreenSaver.restore(saved))
        }
    }
}
