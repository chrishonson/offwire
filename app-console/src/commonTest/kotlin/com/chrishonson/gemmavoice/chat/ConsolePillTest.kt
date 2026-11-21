package com.chrishonson.gemmavoice.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConsolePillTest {

    @Test
    fun readyAutoListenMapsEveryPhaseAndMuteCombination() {
        val expected = mapOf(
            LivePhase.IDLE to listOf(null, null),
            LivePhase.LISTENING to listOf(
                ConsolePill("MUTE", PillKind.MUTE),
                ConsolePill("UNMUTE", PillKind.UNMUTE)
            ),
            LivePhase.THINKING to listOf(
                ConsolePill("STOP", PillKind.STOP),
                ConsolePill("STOP", PillKind.STOP)
            ),
            LivePhase.SPEAKING to listOf(
                ConsolePill("STOP", PillKind.STOP),
                ConsolePill("STOP", PillKind.STOP)
            )
        )

        for (phase in LivePhase.entries) {
            assertEquals(expected.getValue(phase)[0], consolePill(phase, false, ModelStatus.Ready, true))
            assertEquals(expected.getValue(phase)[1], consolePill(phase, true, ModelStatus.Ready, true))
        }
    }

    @Test
    fun modelPreparationMapsOnlyReadoutActions() {
        assertEquals(
            ConsolePill("DOWNLOAD", PillKind.DOWNLOAD),
            consolePill(LivePhase.IDLE, false, ModelStatus.Missing, true)
        )
        assertEquals(
            ConsolePill("RETRY", PillKind.RETRY),
            consolePill(LivePhase.IDLE, false, ModelStatus.Error("failed"), true)
        )
        assertNull(consolePill(LivePhase.IDLE, false, ModelStatus.Downloading(0.5f), true))
        assertNull(consolePill(LivePhase.IDLE, false, ModelStatus.Loading, true))
    }

    @Test
    fun idleWithoutAutoListenOffersTalk() {
        assertEquals(
            ConsolePill("TALK", PillKind.TALK),
            consolePill(LivePhase.IDLE, false, ModelStatus.Ready, false)
        )
        assertNull(consolePill(LivePhase.IDLE, false, ModelStatus.Ready, true))
    }

    @Test
    fun missingOrErrorWithoutAutoListenOffersDisabledTalkState() {
        assertEquals(
            ConsolePill("TALK", PillKind.TALK),
            consolePill(LivePhase.IDLE, false, ModelStatus.Missing, false)
        )
        assertEquals(
            ConsolePill("TALK", PillKind.TALK),
            consolePill(LivePhase.IDLE, false, ModelStatus.Error("failed"), false)
        )
    }
}
