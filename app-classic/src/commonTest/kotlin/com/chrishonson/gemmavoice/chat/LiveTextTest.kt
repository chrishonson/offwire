package com.chrishonson.gemmavoice.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiveTextTest {

    @Test
    fun blankAssistantNameFallsBack() {
        assertEquals("Assistant", displayName(""))
        assertEquals("Orion", displayName("Orion"))
        assertEquals("Assistant is speaking", formatLivePhaseLabel(LivePhase.SPEAKING, false, " "))
    }

    @Test
    fun listeningLabelSaysWhenTheDetectorHearsSpeech() {
        assertEquals("I'm listening", formatLivePhaseLabel(LivePhase.LISTENING, false, "Orion"))
        assertEquals("Hearing speech", formatLivePhaseLabel(LivePhase.LISTENING, false, "Orion", true))
        assertEquals("Microphone is off", formatLivePhaseLabel(LivePhase.LISTENING, true, "Orion", true))
        assertEquals("Orion is speaking", formatLivePhaseLabel(LivePhase.SPEAKING, false, "Orion", true))
    }

    @Test
    fun hintsFollowPhaseMuteAndWakeWord() {
        assertEquals("Tap Unmute to talk again", formatLiveHint(LivePhase.LISTENING, true, true, "Orion"))
        assertEquals("Just start talking to interrupt", formatLiveHint(LivePhase.SPEAKING, false, false, "Orion"))
        assertEquals("Tap anywhere to interrupt", formatLiveHint(LivePhase.SPEAKING, true, false, "Orion"))
        assertEquals("Tap anywhere to interrupt", formatLiveHint(LivePhase.THINKING, false, false, "Orion"))
        assertEquals("Say \"Orion\" to ask something", formatLiveHint(LivePhase.LISTENING, false, true, "Orion"))
        assertNull(formatLiveHint(LivePhase.LISTENING, false, false, "Orion"))
    }

    @Test
    fun modelCardTitleNamesAssistantWhilePreparing() {
        assertEquals("Getting Orion ready", formatModelCardTitle(ModelStatus.Downloading(0.5f), "Orion", "Gemma"))
        assertEquals("Getting Orion ready", formatModelCardTitle(ModelStatus.Loading, "Orion", "Gemma"))
        assertEquals("Gemma", formatModelCardTitle(ModelStatus.Missing, "Orion", "Gemma"))
    }
}
