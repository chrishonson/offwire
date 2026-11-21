package com.chrishonson.gemmavoice.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class LiveTextTest {

    @Test
    fun blankAssistantNameFallsBack() {
        assertEquals("Assistant", displayName(""))
        assertEquals("Orion", displayName("Orion"))
    }

    @Test
    fun modelCardTitleNamesAssistantWhilePreparing() {
        assertEquals("Getting Orion ready", formatModelCardTitle(ModelStatus.Downloading(0.5f), "Orion", "Gemma"))
        assertEquals("Getting Orion ready", formatModelCardTitle(ModelStatus.Loading, "Orion", "Gemma"))
        assertEquals("Gemma", formatModelCardTitle(ModelStatus.Missing, "Orion", "Gemma"))
    }

    @Test
    fun modelStatusTextDescribesEveryDownloadState() {
        assertEquals("Not downloaded yet", formatStatusText(ModelStatus.Missing))
        assertEquals("Ready to download", formatStatusText(ModelStatus.ConfirmDownload(100L)))
        assertEquals("Waiting for Wi-Fi", formatStatusText(ModelStatus.WifiRequired(100L)))
        assertEquals("Downloading, 42%", formatStatusText(ModelStatus.Downloading(0.42f)))
        assertEquals("Sit tight, this could take up to a minute.", formatStatusText(ModelStatus.Loading))
        assertEquals("Ready", formatStatusText(ModelStatus.Ready))
        assertEquals("Something went wrong: failed", formatStatusText(ModelStatus.Error("failed")))
    }
}
