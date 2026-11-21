package com.chrishonson.gemmavoice.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AutoListenReadoutTest {

    @Test
    fun missingOffersDownload() {
        assertEquals(
            AutoListenReadoutState("STANDBY", "Not downloaded yet", "DOWNLOAD", blocked = false),
            autoListenReadout(ModelStatus.Missing, micDenied = false)
        )
    }

    @Test
    fun downloadingRoundsAndClampsProgress() {
        assertEquals("Downloading, 43%", autoListenReadout(ModelStatus.Downloading(0.425f), false)?.hint)
        assertEquals("Downloading, 0%", autoListenReadout(ModelStatus.Downloading(-0.1f), false)?.hint)
        assertEquals("Downloading, 100%", autoListenReadout(ModelStatus.Downloading(1.1f), false)?.hint)
    }

    @Test
    fun loadingExplainsPreparation() {
        assertEquals(
            AutoListenReadoutState("STANDBY", "Sit tight, this could take up to a minute.", null, blocked = false),
            autoListenReadout(ModelStatus.Loading, micDenied = false)
        )
    }

    @Test
    fun errorOffersBlockedRetry() {
        assertEquals(
            AutoListenReadoutState("STANDBY", "failed", "RETRY", blocked = true),
            autoListenReadout(ModelStatus.Error("failed"), micDenied = false)
        )
    }

    @Test
    fun microphoneDenialWinsOverStatus() {
        assertEquals(
            AutoListenReadoutState(
                "MIC BLOCKED",
                "Allow the microphone in Settings",
                null,
                blocked = true
            ),
            autoListenReadout(ModelStatus.Error("failed"), micDenied = true)
        )
    }

    @Test
    fun readyHasNoReadout() {
        assertNull(autoListenReadout(ModelStatus.Ready, micDenied = false))
    }
}
