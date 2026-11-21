package com.chrishonson.gemmavoice.chat

import com.chrishonson.gemmavoice.llm.ReplySpeed
import com.chrishonson.gemmavoice.net.NetworkActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ReplySpeedChatTest {
    private val testDispatcher = StandardTestDispatcher()
    private var ticks = 0L

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createStore(engine: FakeLlmEngine) = ChatStore(
        engine = engine,
        repo = FakeModelRepository(downloaded = true),
        audioStream = FakeAudioStream(),
        synthesizer = FakeSpeechSynthesizer(),
        micPermission = FakeMicPermission(granted = true),
        stt = FakeSpeechToText(),
        voiceSpec = null,
        networkActivity = NetworkActivity(now = { 0L }),
        // Each reading is 100 ms after the last: send, then one per streamed message.
        clockMs = {
            ticks += 100L
            ticks
        }
    )

    @Test
    fun typedReplyRecordsTimeToFirstTokenAndRate() = runTest {
        val engine = FakeLlmEngine().apply { chunks = listOf("Hello", "Hello there", "Hello there friend") }
        val store = createStore(engine)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()
        assertNull(store.state.value.replySpeed)

        store.dispatch(ChatIntent.InputChanged("Hi"))
        store.dispatch(ChatIntent.Send)
        advanceUntilIdle()

        // First text 100 ms after the send, then two intervals over 200 ms.
        assertEquals(ReplySpeed(ttftMs = 100L, tokensPerSecond = 10.0), store.state.value.replySpeed)
    }

    @Test
    fun aReplyWithNoTextKeepsThePreviousSpeed() = runTest {
        val engine = FakeLlmEngine().apply { chunks = listOf("One", "One two") }
        val store = createStore(engine)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()
        store.dispatch(ChatIntent.InputChanged("First"))
        store.dispatch(ChatIntent.Send)
        advanceUntilIdle()
        val first = store.state.value.replySpeed

        engine.chunks = emptyList()
        store.dispatch(ChatIntent.InputChanged("Second"))
        store.dispatch(ChatIntent.Send)
        advanceUntilIdle()

        assertEquals(first, store.state.value.replySpeed)
    }
}
