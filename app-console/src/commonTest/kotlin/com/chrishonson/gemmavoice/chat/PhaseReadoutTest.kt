package com.chrishonson.gemmavoice.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhaseReadoutTest {

    @Test
    fun modelPreparationAndErrorsMapToExpectedStates() {
        assertEquals(
            PhaseReadoutState("STANDBY", "Not downloaded yet", RailColor.FRAME, LevelBlockMode.UNLIT),
            phaseReadout(ModelStatus.Missing, false, LivePhase.IDLE, false, true, true)
        )
        assertEquals(
            PhaseReadoutState("STANDBY", "Downloading, 43%", RailColor.FRAME, LevelBlockMode.UNLIT),
            phaseReadout(ModelStatus.Downloading(0.425f), false, LivePhase.IDLE, false, true, true)
        )
        assertEquals(
            PhaseReadoutState(
                "STANDBY",
                "Sit tight, this could take up to a minute.",
                RailColor.FRAME,
                LevelBlockMode.UNLIT
            ),
            phaseReadout(ModelStatus.Loading, false, LivePhase.IDLE, false, true, true)
        )
        assertEquals(
            PhaseReadoutState("STANDBY", "Network failure", RailColor.TOMATO, LevelBlockMode.UNLIT),
            phaseReadout(ModelStatus.Error("Network failure"), false, LivePhase.IDLE, false, true, true)
        )
    }

    @Test
    fun microphoneDenialTakesPrecedenceOverStatusAndPhase() {
        val expected = PhaseReadoutState(
            word = "MIC BLOCKED",
            hint = "Allow the microphone in Settings",
            railColor = RailColor.TOMATO,
            blockMode = LevelBlockMode.UNLIT
        )
        assertEquals(expected, phaseReadout(ModelStatus.Ready, true, LivePhase.LISTENING, false, true, true))
        assertEquals(expected, phaseReadout(ModelStatus.Error("err"), true, LivePhase.IDLE, false, true, true))
    }

    @Test
    fun listeningPhaseFollowsMuteAndWakeWord() {
        assertEquals(
            PhaseReadoutState("LISTENING", "Say \"Computer\" to ask", RailColor.ICE, LevelBlockMode.LEVEL),
            phaseReadout(ModelStatus.Ready, false, LivePhase.LISTENING, false, true, true, "Computer")
        )
        assertEquals(
            PhaseReadoutState("LISTENING", "Say \"Orion\" to ask", RailColor.ICE, LevelBlockMode.LEVEL),
            phaseReadout(ModelStatus.Ready, false, LivePhase.LISTENING, false, true, true, "Orion")
        )
        assertEquals(
            PhaseReadoutState("LISTENING", null, RailColor.ICE, LevelBlockMode.LEVEL),
            phaseReadout(ModelStatus.Ready, false, LivePhase.LISTENING, false, false, true, "Computer")
        )
        assertEquals(
            PhaseReadoutState("MIC OFF", null, RailColor.TOMATO, LevelBlockMode.UNLIT),
            phaseReadout(ModelStatus.Ready, false, LivePhase.LISTENING, true, true, true, "Computer")
        )
    }

    @Test
    fun thinkingAndSpeakingPhasesFollowMute() {
        assertEquals(
            PhaseReadoutState("WORKING", "Tap anywhere to interrupt", RailColor.ORANGE_PULSE, LevelBlockMode.UNLIT),
            phaseReadout(ModelStatus.Ready, false, LivePhase.THINKING, false, true, true)
        )
        assertEquals(
            PhaseReadoutState("WORKING", "Tap anywhere to interrupt", RailColor.TOMATO, LevelBlockMode.UNLIT),
            phaseReadout(ModelStatus.Ready, false, LivePhase.THINKING, true, true, true)
        )
        assertEquals(
            PhaseReadoutState("SPEAKING", "Start talking to interrupt", RailColor.SUNFLOWER, LevelBlockMode.LEVEL),
            phaseReadout(ModelStatus.Ready, false, LivePhase.SPEAKING, false, true, true)
        )
        assertEquals(
            PhaseReadoutState("SPEAKING", "Tap anywhere to interrupt", RailColor.TOMATO, LevelBlockMode.LEVEL),
            phaseReadout(ModelStatus.Ready, false, LivePhase.SPEAKING, true, true, true)
        )
    }

    @Test
    fun idlePhaseFollowsAutoListenFlag() {
        assertEquals(
            PhaseReadoutState("STANDBY", "Tap TALK to start", RailColor.FRAME, LevelBlockMode.UNLIT),
            phaseReadout(ModelStatus.Ready, false, LivePhase.IDLE, false, true, false)
        )
        assertEquals(
            PhaseReadoutState("STANDBY", null, RailColor.FRAME, LevelBlockMode.UNLIT),
            phaseReadout(ModelStatus.Ready, false, LivePhase.IDLE, false, true, true)
        )
    }

    @Test
    fun voiceTagShowsOnlyWhileListeningAndVoiceDetected() {
        val listening = PhaseReadoutState(LISTENING_WORD, null, RailColor.ICE, LevelBlockMode.LEVEL)
        val speaking = PhaseReadoutState("SPEAKING", null, RailColor.SUNFLOWER, LevelBlockMode.LEVEL)
        assertTrue(showsVoiceTag(listening, voiceDetected = true))
        assertFalse(showsVoiceTag(listening, voiceDetected = false))
        assertFalse(showsVoiceTag(speaking, voiceDetected = true))
    }

    @Test
    fun levelToLitBlockCountMapsAndClampsCorrectly() {
        assertEquals(0, litBlockCount(0f))
        assertEquals(0, litBlockCount(0.04f))
        assertEquals(5, litBlockCount(0.5f))
        assertEquals(10, litBlockCount(1f))
        assertEquals(0, litBlockCount(-0.5f))
        assertEquals(10, litBlockCount(1.5f))
    }

    @Test
    fun formatPhaseContentDescriptionCombinesWordAndHintInSentenceCase() {
        assertEquals(
            "Listening. Say Computer to ask.",
            formatPhaseContentDescription("LISTENING", "Say \"Computer\" to ask")
        )
        assertEquals(
            "Listening.",
            formatPhaseContentDescription("LISTENING", null)
        )
        assertEquals(
            "Mic blocked. Allow the microphone in Settings.",
            formatPhaseContentDescription("MIC BLOCKED", "Allow the microphone in Settings")
        )
        assertEquals(
            "Mic off.",
            formatPhaseContentDescription("MIC OFF", null)
        )
        assertEquals(
            "Working. Tap anywhere to interrupt.",
            formatPhaseContentDescription("WORKING", "Tap anywhere to interrupt")
        )
        assertEquals(
            "Speaking. Start talking to interrupt.",
            formatPhaseContentDescription("SPEAKING", "Start talking to interrupt")
        )
        assertEquals(
            "Standby. Not downloaded yet.",
            formatPhaseContentDescription("STANDBY", "Not downloaded yet")
        )
    }
}
