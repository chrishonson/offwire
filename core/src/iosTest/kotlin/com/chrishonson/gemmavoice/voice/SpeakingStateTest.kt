package com.chrishonson.gemmavoice.voice

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeakingStateTest {
    @Test
    fun enqueueTwiceStaysSpeakingUntilSecondFinish() {
        val state = SpeakingState()
        state.enqueue()
        state.enqueue()
        state.finished()
        assertTrue(state.isSpeaking.value)
        state.finished()
        assertFalse(state.isSpeaking.value)
    }

    @Test
    fun speakResetsCountToOne() {
        val state = SpeakingState()
        state.enqueue()
        state.enqueue()
        state.speak()
        state.finished()
        assertFalse(state.isSpeaking.value)
    }

    @Test
    fun stopZeroesCountAndFlag() {
        val state = SpeakingState()
        state.enqueue()
        state.enqueue()
        state.stop()
        assertFalse(state.isSpeaking.value)
        state.enqueue()
        state.finished()
        assertFalse(state.isSpeaking.value)
    }

    @Test
    fun finishWithNothingPendingStaysFalse() {
        val state = SpeakingState()
        state.finished()
        assertFalse(state.isSpeaking.value)
    }
}
