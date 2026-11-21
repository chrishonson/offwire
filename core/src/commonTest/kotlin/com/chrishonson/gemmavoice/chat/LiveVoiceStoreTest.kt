package com.chrishonson.gemmavoice.chat

import com.chrishonson.gemmavoice.llm.LlmPrompt
import com.chrishonson.gemmavoice.model.ModelSpec
import com.chrishonson.gemmavoice.net.NetworkActivity
import com.chrishonson.gemmavoice.resource.ResourceSample
import com.chrishonson.gemmavoice.resource.ResourceSampler
import com.chrishonson.gemmavoice.voice.InMemorySettingsStore
import com.chrishonson.gemmavoice.voice.FakeVad
import com.chrishonson.gemmavoice.voice.SpeechSynthesizer
import com.chrishonson.gemmavoice.voice.VoiceActivityDetector
import com.chrishonson.gemmavoice.voice.VoiceConfig
import com.chrishonson.gemmavoice.voice.WavEncoder
import com.chrishonson.gemmavoice.voice.withOverheard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Suppress("TooManyFunctions", "LargeClass")
@OptIn(ExperimentalCoroutinesApi::class)
class LiveVoiceStoreTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testSpec = ModelSpec(
        id = "test-model",
        displayName = "Test Gemma",
        url = "https://example.com/model.litertlm",
        fileName = "model.litertlm",
        sizeBytes = 1000L
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createStore(
        engine: FakeLlmEngine = FakeLlmEngine(),
        audioStream: FakeAudioStream = FakeAudioStream(),
        synthesizer: SpeechSynthesizer = FakeSpeechSynthesizer(),
        stt: FakeSpeechToText = FakeSpeechToText(),
        config: VoiceConfig = VoiceConfig(),
        vad: VoiceActivityDetector = FakeVad(config),
        settings: InMemorySettingsStore = InMemorySettingsStore(),
        resourceMonitor: ResourceSampler = FakeResourceSampler(),
        resourceDispatcher: CoroutineDispatcher = testDispatcher,
        autoListen: Boolean = false
    ): ChatStore = ChatStore(
        engine = engine,
        repo = FakeModelRepository(downloaded = true),
        audioStream = audioStream,
        synthesizer = synthesizer,
        micPermission = FakeMicPermission(granted = true),
        stt = stt,
        config = config,
        vad = vad,
        models = listOf(testSpec),
        settings = settings,
        resourceMonitor = resourceMonitor,
        resourceDispatcher = resourceDispatcher,
        autoListen = autoListen,
        networkActivity = NetworkActivity(now = { 0L })
    )

    private fun FakeAudioStream.pushUtterance(speechMs: Int = 400, silenceMs: Int = 700) {
        push(tone(ms = speechMs, amplitude = 0.2))
        push(silence(ms = silenceMs))
    }

    // The label the console shows for a phase, kept here so the store test does not reach into the UI module.
    private fun ChatStore.phaseLabel(): String = state.value.let {
        when {
            it.micMuted && it.livePhase == LivePhase.LISTENING -> "Microphone is off"
            it.livePhase == LivePhase.LISTENING -> "I'm listening"
            it.livePhase == LivePhase.THINKING -> "Thinking…"
            it.livePhase == LivePhase.SPEAKING -> "${it.assistantName} is speaking"
            else -> ""
        }
    }

    private suspend fun startLive(store: ChatStore, testScope: TestScope) {
        store.dispatch(ChatIntent.Start)
        testScope.runCurrent()
        store.dispatch(ChatIntent.EnterLiveMode)
        testScope.runCurrent()
    }

    @Test
    fun resourceSamplerStaysOffWhileIdleAndWhileSwitchIsOff() = runTest {
        val monitor = FakeResourceSampler()
        val audioStream = FakeAudioStream()
        val store = createStore(
            audioStream = audioStream,
            settings = InMemorySettingsStore(showResourceMeter = true),
            resourceMonitor = monitor
        )
        try {

            runCurrent()
            assertTrue(monitor.sampleCount > 0)
            assertTrue(store.state.value.resources != null)

            store.dispatch(ChatIntent.ToggleResourceMeter)
            runCurrent()
            assertEquals(null, store.state.value.resources)
            val stoppedCount = monitor.sampleCount

            startLive(store, this)
            audioStream.pushUtterance()
            advanceTimeBy(1_000L)
            runCurrent()
            assertEquals(stoppedCount, monitor.sampleCount)
            assertEquals(null, store.state.value.resources)

            store.dispatch(ChatIntent.ToggleResourceMeter)
            runCurrent()
            assertTrue(monitor.sampleCount > stoppedCount)
            assertTrue(store.state.value.resources != null)
        } finally {
            store.onCleared()
        }
    }

    @Test
    fun resourceSamplerStartsOnThinkingKeepsLatestAndStopsAfterTail() = runTest {
        val monitor = FakeResourceSampler()
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val engine = FakeLlmEngine().apply { chunks = listOf("Done.") }
        val store = createStore(
            engine = engine,
            audioStream = audioStream,
            synthesizer = synthesizer,
            settings = InMemorySettingsStore(showResourceMeter = true),
            resourceMonitor = monitor
        )
        try {
            startLive(store, this)

            audioStream.pushUtterance()
            runCurrent()
            advanceTimeBy(1_000L)
            runCurrent()

            assertTrue(monitor.sampleCount > 0)
            assertEquals(monitor.sampleCount.toDouble(), store.state.value.resources?.cpuPercent)

            synthesizer.finishSpeaking()
            runCurrent()
            val samplesAtEnd = monitor.sampleCount
            advanceTimeBy(1_999L)
            runCurrent()
            assertTrue(monitor.sampleCount > samplesAtEnd)
            assertTrue(store.state.value.resources != null)

            advanceTimeBy(1L)
            runCurrent()
            assertTrue(store.state.value.resources != null)
            assertEquals(1, monitor.loggedPeaks.size)
            assertTrue((monitor.loggedPeaks.single().first ?: 0.0) >= samplesAtEnd)
            val countAfterTail = monitor.sampleCount
            advanceTimeBy(2_000L)
            runCurrent()
            assertTrue(monitor.sampleCount > countAfterTail)
            assertTrue(store.state.value.resources != null)
        } finally {
            store.onCleared()
        }
    }

    @Test
    fun resourceSamplerUsesTwoSecondIdleIntervalAndHalfSecondActiveInterval() = runTest {
        val monitor = FakeResourceSampler()
        val gate = CompletableDeferred<Unit>()
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Done.")
            this.gate = gate
        }
        val store = createStore(
            engine = engine,
            settings = InMemorySettingsStore(showResourceMeter = true),
            resourceMonitor = monitor
        )
        try {
            store.dispatch(ChatIntent.Start)
            runCurrent()
            val initialIdleSamples = monitor.sampleCount
            assertEquals(1, initialIdleSamples)

            advanceTimeBy(1_999L)
            runCurrent()
            assertEquals(initialIdleSamples, monitor.sampleCount)

            advanceTimeBy(1L)
            runCurrent()
            assertEquals(initialIdleSamples + 1, monitor.sampleCount)

            store.dispatch(ChatIntent.InputChanged("Hello"))
            store.dispatch(ChatIntent.Send)
            runCurrent()
            val activeStartSamples = monitor.sampleCount
            assertTrue(activeStartSamples > initialIdleSamples + 1)

            advanceTimeBy(499L)
            runCurrent()
            assertEquals(activeStartSamples, monitor.sampleCount)

            advanceTimeBy(1L)
            runCurrent()
            assertEquals(activeStartSamples + 1, monitor.sampleCount)

            gate.complete(Unit)
        } finally {
            store.onCleared()
        }
    }

    @Test
    fun idleSamplesDoNotChangeLoggedPeaksOfNextTurn() = runTest {
        val monitor = FakeResourceSampler().apply {
            nextCpuPercent = 750.0
            nextPssMb = 2048.0
        }
        val synthesizer = FakeSpeechSynthesizer()
        val engine = FakeLlmEngine().apply { chunks = listOf("Done.") }
        val store = createStore(
            engine = engine,
            synthesizer = synthesizer,
            settings = InMemorySettingsStore(showResourceMeter = true),
            resourceMonitor = monitor
        )
        try {
            store.dispatch(ChatIntent.Start)
            runCurrent()

            advanceTimeBy(2_000L)
            runCurrent()
            assertEquals(750.0, store.state.value.resources?.cpuPercent)
            assertTrue(monitor.loggedPeaks.isEmpty())

            monitor.nextCpuPercent = null
            monitor.nextPssMb = null
            store.dispatch(ChatIntent.InputChanged("Hello"))
            store.dispatch(ChatIntent.Send)
            advanceTimeBy(2_000L)
            runCurrent()

            assertEquals(1, monitor.loggedPeaks.size)
            // The 750 percent idle sample before the turn is not the turn's peak, and the
            // sampler keeps running after the tail, so the logged peak is not tied to the count.
            val turnPeak = monitor.loggedPeaks.single()
            assertTrue((turnPeak.first ?: 0.0) in 1.0..<750.0)
            assertTrue((turnPeak.second ?: 0.0) in 101.0..<2048.0)
        } finally {
            store.onCleared()
        }
    }

    @Test
    fun resourceSamplerStopsAfterGenerationTailForTypedTurn() = runTest {
        val monitor = FakeResourceSampler()
        val synthesizer = FakeSpeechSynthesizer()
        val gate = CompletableDeferred<Unit>()
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Done.")
            this.gate = gate
        }
        val store = createStore(
            engine = engine,
            synthesizer = synthesizer,
            settings = InMemorySettingsStore(showResourceMeter = true),
            resourceMonitor = monitor
        )
        try {
            store.dispatch(ChatIntent.Start)
            runCurrent()

            store.dispatch(ChatIntent.InputChanged("Hello"))
            store.dispatch(ChatIntent.Send)
            runCurrent()
            advanceTimeBy(1_000L)
            runCurrent()
            assertTrue(monitor.sampleCount > 0)
            assertTrue(store.state.value.resources != null)

            gate.complete(Unit)
            runCurrent()
            assertFalse(store.state.value.isGenerating)
            assertFalse(synthesizer.isSpeaking.value)

            val samplesAtEnd = monitor.sampleCount
            advanceTimeBy(1_999L)
            runCurrent()
            assertTrue(monitor.sampleCount > samplesAtEnd)
            assertTrue(store.state.value.resources != null)

            advanceTimeBy(1L)
            runCurrent()
            assertTrue(store.state.value.resources != null)
            assertEquals(1, monitor.loggedPeaks.size)
            val countAfterTail = monitor.sampleCount
            advanceTimeBy(2_000L)
            runCurrent()
            assertTrue(monitor.sampleCount > countAfterTail)
        } finally {
            store.onCleared()
        }
    }

    @Test
    fun secondTypedTurnInsideTheTailLogsThePreviousTurnOnItsOwn() = runTest {
        val monitor = FakeResourceSampler()
        val synthesizer = FakeSpeechSynthesizer()
        val engine = FakeLlmEngine().apply { chunks = listOf("Done.") }
        val store = createStore(
            engine = engine,
            synthesizer = synthesizer,
            settings = InMemorySettingsStore(showResourceMeter = true),
            resourceMonitor = monitor
        )
        try {
            store.dispatch(ChatIntent.Start)
            runCurrent()

            store.dispatch(ChatIntent.InputChanged("First"))
            store.dispatch(ChatIntent.Send)
            advanceTimeBy(1_000L)
            runCurrent()
            assertFalse(store.state.value.isGenerating)
            val firstTurnSamples = monitor.sampleCount
            assertTrue(firstTurnSamples > 0)
            assertTrue(monitor.loggedPeaks.isEmpty())

            // Still inside the first turn's two-second tail.
            store.dispatch(ChatIntent.InputChanged("Second"))
            store.dispatch(ChatIntent.Send)
            runCurrent()
            assertEquals(1, monitor.loggedPeaks.size)
            assertEquals(firstTurnSamples.toDouble(), monitor.loggedPeaks[0].first)

            advanceTimeBy(3_000L)
            runCurrent()
            assertEquals(2, monitor.loggedPeaks.size)
            assertTrue((monitor.loggedPeaks[1].first ?: 0.0) > firstTurnSamples.toDouble())
            assertTrue(store.state.value.resources != null)
        } finally {
            store.onCleared()
        }
    }

    @Test
    fun speechStartingAfterGenerationEndsStaysInTheSameTurn() = runTest {
        // On the Pixel the speaker starts a moment after generation completes; that gap is not a
        // turn boundary, so the turn logs its peaks once, after speech.
        val monitor = FakeResourceSampler()
        val synthesizer = FakeSpeechSynthesizer()
        val gate = CompletableDeferred<Unit>()
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Done.")
            this.gate = gate
        }
        val store = createStore(
            engine = engine,
            synthesizer = synthesizer,
            settings = InMemorySettingsStore(showResourceMeter = true),
            resourceMonitor = monitor
        )
        try {
            store.dispatch(ChatIntent.Start)
            runCurrent()

            store.dispatch(ChatIntent.InputChanged("Hello"))
            store.dispatch(ChatIntent.Send)
            runCurrent()
            advanceTimeBy(1_000L)
            runCurrent()
            synthesizer.setSpeaking(false)
            gate.complete(Unit)
            runCurrent()
            assertFalse(store.state.value.isGenerating)
            val samplesAtGenerationEnd = monitor.sampleCount

            advanceTimeBy(500L)
            runCurrent()
            synthesizer.setSpeaking(true)
            runCurrent()
            assertTrue(monitor.loggedPeaks.isEmpty())

            advanceTimeBy(1_000L)
            runCurrent()
            synthesizer.finishSpeaking()
            runCurrent()
            advanceTimeBy(2_000L)
            runCurrent()
            assertEquals(1, monitor.loggedPeaks.size)
            assertTrue((monitor.loggedPeaks[0].first ?: 0.0) > samplesAtGenerationEnd.toDouble())
            assertTrue(store.state.value.resources != null)
        } finally {
            store.onCleared()
        }
    }

    @Test
    fun resourceSamplerStaysOffForTypedTurnWhenSwitchIsOff() = runTest {
        val monitor = FakeResourceSampler()
        val synthesizer = FakeSpeechSynthesizer()
        val engine = FakeLlmEngine().apply { chunks = listOf("Done.") }
        val store = createStore(
            engine = engine,
            synthesizer = synthesizer,
            settings = InMemorySettingsStore(showResourceMeter = false),
            resourceMonitor = monitor
        )
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        store.dispatch(ChatIntent.InputChanged("Hello"))
        store.dispatch(ChatIntent.Send)
        advanceTimeBy(1_000L)
        runCurrent()
        synthesizer.finishSpeaking()
        advanceTimeBy(3_000L)
        runCurrent()

        assertEquals(0, monitor.sampleCount)
        assertEquals(null, store.state.value.resources)
        assertTrue(monitor.loggedPeaks.isEmpty())
    }

    @Test
    fun assistantNameComesFromConfigAndIsTheDefaultWakeWord() = runTest {
        assertEquals("Computer", createStore().state.value.assistantName)
        assertEquals(listOf("computer"), VoiceConfig().wakeWords)
        val named = VoiceConfig(assistantName = "Orion")
        assertEquals("Orion", createStore(config = named).state.value.assistantName)
        assertEquals(listOf("orion"), named.wakeWords)
    }

    @Test
    fun toggleWakeWordFlipsState() = runTest {
        val store = createStore()
        assertFalse(store.state.value.wakeWordEnabled)
        store.dispatch(ChatIntent.ToggleWakeWord)
        assertTrue(store.state.value.wakeWordEnabled)
        store.dispatch(ChatIntent.ToggleWakeWord)
        assertFalse(store.state.value.wakeWordEnabled)
    }

    @Test
    fun wakeWordDisabledAlwaysUsesVoiceInstruction() = runTest {
        val engine = FakeLlmEngine().apply { holdAfterChunks = true }
        val audioStream = FakeAudioStream()
        val stt = FakeSpeechToText()
        val store = createStore(
            engine = engine,
            audioStream = audioStream,
            stt = stt,
            config = VoiceConfig(sendAudioToModel = true)
        )
        startLive(store, this)

        audioStream.pushUtterance()
        advanceUntilIdle()

        assertEquals(VOICE_INSTRUCTION, assertIs<LlmPrompt.Audio>(engine.prompts.single()).instruction)
        assertEquals(1, stt.callCount)
        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun engineCancellingOnItsOwnReturnsLiveModeToListening() = runTest {
        val engine = FakeLlmEngine().apply { generateError = CancellationException("Task cancelled") }
        val audioStream = FakeAudioStream()
        val stt = FakeSpeechToText().apply { defaultTranscript = "what time is it" }
        val store = createStore(engine = engine, audioStream = audioStream, stt = stt)
        startLive(store, this)

        audioStream.pushUtterance()
        advanceUntilIdle()

        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertFalse(store.state.value.isGenerating)
        assertTrue(store.state.value.messages.none { it.role == Role.ASSISTANT })

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    private fun textOf(prompt: LlmPrompt): String = assertIs<LlmPrompt.Text>(prompt).text

    @Test
    fun wakeOnUnaddressedSpeechIsShownAsOverheardWithoutEngineCall() = runTest {
        val engine = FakeLlmEngine()
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val stt = FakeSpeechToText().apply { defaultTranscript = "what should we have for dinner" }
        val store = createStore(engine = engine, audioStream = audioStream, synthesizer = synthesizer, stt = stt)
        startLive(store, this)
        store.dispatch(ChatIntent.ToggleWakeWord)

        audioStream.pushUtterance()
        advanceUntilIdle()

        assertEquals(1, stt.callCount)
        assertTrue(engine.prompts.isEmpty())
        val line = store.state.value.messages.single()
        assertEquals(Role.OVERHEARD, line.role)
        assertEquals("what should we have for dinner", line.text)
        assertTrue(synthesizer.enqueuedTexts.isEmpty())
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertFalse(store.state.value.isGenerating)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun wakeWordAloneAcknowledgesThenNextUtteranceIsSentWithoutWakeWord() = runTest {
        val engine = FakeLlmEngine().apply { chunks = listOf("It is noon.") }
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val stt = FakeSpeechToText(listOf("Hey Computer", "what time is it"))
        val store = createStore(engine = engine, audioStream = audioStream, synthesizer = synthesizer, stt = stt)
        startLive(store, this)
        store.dispatch(ChatIntent.ToggleWakeWord)

        audioStream.pushUtterance()
        advanceUntilIdle()

        assertTrue(engine.prompts.isEmpty())
        assertTrue(synthesizer.enqueuedTexts.isEmpty())
        val bubble = store.state.value.messages.single()
        assertEquals(Role.USER, bubble.role)
        assertEquals("Hey Computer", bubble.text)
        assertTrue(bubble.isVoice)
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)

        synthesizer.finishSpeaking()
        audioStream.pushUtterance()
        advanceUntilIdle()

        assertEquals("what time is it", textOf(engine.prompts.single()))
        assertEquals(
            listOf("Hey Computer", "what time is it", "It is noon."),
            store.state.value.messages.map { it.text }
        )

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun speechDuringTheWakeAcknowledgementReachesSpeechToText() = runTest {
        // Card 220: the mic stays open while "Yes?" plays, so a question spoken over it is transcribed.
        val engine = FakeLlmEngine().apply { chunks = listOf("It is Monday.") }
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val stt = FakeSpeechToText(listOf("Computer", "what day is it"))
        val store = createStore(engine = engine, audioStream = audioStream, synthesizer = synthesizer, stt = stt)
        startLive(store, this)
        store.dispatch(ChatIntent.ToggleWakeWord)

        audioStream.pushUtterance()
        advanceUntilIdle()
        assertTrue(synthesizer.enqueuedTexts.isEmpty())
        // The acknowledgement is still playing when the question arrives.
        audioStream.pushUtterance()
        advanceUntilIdle()

        assertEquals(2, stt.callCount)
        assertEquals(1, engine.prompts.size)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun addressedUtteranceSendsOverheardContextAndClearsIt() = runTest {
        val engine = FakeLlmEngine().apply { chunks = listOf("Sure.") }
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val stt = FakeSpeechToText(
            listOf("I think the meeting is at three", "no it was at four", "Computer, when is the meeting?")
        )
        val store = createStore(engine = engine, audioStream = audioStream, synthesizer = synthesizer, stt = stt)
        startLive(store, this)
        store.dispatch(ChatIntent.ToggleWakeWord)

        repeat(3) {
            audioStream.pushUtterance()
            advanceUntilIdle()
        }

        assertEquals(
            withOverheard(
                listOf("I think the meeting is at three", "no it was at four"),
                "Computer, when is the meeting?"
            ),
            textOf(engine.prompts.single())
        )
        val messages = store.state.value.messages
        assertEquals(listOf(Role.OVERHEARD, Role.OVERHEARD, Role.USER), messages.take(3).map { it.role })
        assertEquals("Computer, when is the meeting?", messages[2].text)
        assertTrue(messages[2].isVoice)

        // The context is cleared: the next addressed utterance carries no overheard lines.
        synthesizer.finishSpeaking()
        audioStream.push(audioStream.silence(ms = VoiceConfig().followUpMs + 500))
        stt.script("Computer, thanks")
        audioStream.pushUtterance()
        advanceUntilIdle()

        assertEquals("Computer, thanks", textOf(engine.prompts[1]))

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun overheardKeepsOnlyTheMostRecentLines() = runTest {
        val config = VoiceConfig(overheardLines = 2)
        val engine = FakeLlmEngine()
        val audioStream = FakeAudioStream(config)
        val stt = FakeSpeechToText(listOf("one", "two", "three", "Computer go"))
        val store = createStore(engine = engine, audioStream = audioStream, stt = stt, config = config)
        startLive(store, this)
        store.dispatch(ChatIntent.ToggleWakeWord)

        repeat(4) {
            audioStream.pushUtterance()
            advanceUntilIdle()
        }

        val text = textOf(engine.prompts.single())
        assertEquals(withOverheard(listOf("two", "three"), "Computer go"), text)
        assertFalse("one" in text.lines())

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun followUpWindowBypassesWakeWordUntilItExpires() = runTest {
        val config = VoiceConfig()
        val engine = FakeLlmEngine().apply { chunks = listOf("Sure thing, friend.") }
        val audioStream = FakeAudioStream(config)
        val synthesizer = FakeSpeechSynthesizer()
        val stt = FakeSpeechToText(listOf("Computer hello there", "and another thing", "unrelated chatter"))
        val store = createStore(engine = engine, audioStream = audioStream, synthesizer = synthesizer, stt = stt)
        startLive(store, this)
        store.dispatch(ChatIntent.ToggleWakeWord)

        audioStream.pushUtterance()
        advanceUntilIdle()
        synthesizer.finishSpeaking()
        advanceUntilIdle()
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)

        // Follow-up speech starts right after the spoken reply ended, with no wake word.
        audioStream.pushUtterance()
        advanceUntilIdle()
        synthesizer.finishSpeaking()
        advanceUntilIdle()
        assertEquals(2, engine.prompts.size)

        // Quiet for longer than followUpMs: speech without the wake word is only overheard.
        audioStream.push(audioStream.silence(ms = config.followUpMs + 500))
        audioStream.pushUtterance()
        advanceUntilIdle()

        assertEquals(2, engine.prompts.size)
        assertEquals(3, stt.callCount)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun replyThatAsksAQuestionLetsExactlyTheNextUtteranceBypassTheWakeWord() = runTest {
        val config = VoiceConfig()
        val engine = FakeLlmEngine().apply { chunks = listOf("What do you mean by that?") }
        val audioStream = FakeAudioStream(config)
        val synthesizer = FakeSpeechSynthesizer()
        val stt = FakeSpeechToText(listOf("Computer, play something", "the blue one", "more unrelated chatter"))
        val store = createStore(engine = engine, audioStream = audioStream, synthesizer = synthesizer, stt = stt)
        startLive(store, this)
        store.dispatch(ChatIntent.ToggleWakeWord)

        audioStream.pushUtterance()
        advanceUntilIdle()
        synthesizer.finishSpeaking()
        advanceUntilIdle()

        // Outside the follow-up window, but the reply asked a question.
        engine.chunks = listOf("Okay.")
        audioStream.push(audioStream.silence(ms = config.followUpMs + 500))
        audioStream.pushUtterance()
        advanceUntilIdle()
        synthesizer.finishSpeaking()
        advanceUntilIdle()
        assertEquals(2, engine.prompts.size)

        // "Okay." asked nothing, so a later unaddressed utterance is only overheard.
        audioStream.push(audioStream.silence(ms = config.followUpMs + 500))
        audioStream.pushUtterance()
        advanceUntilIdle()
        assertEquals(2, engine.prompts.size)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun wakeWordIsMatchedAsWholeWordAcrossSpellingVariants() = runTest {
        val engine = FakeLlmEngine()
        val audioStream = FakeAudioStream()
        val stt = FakeSpeechToText(listOf("my computers are lovely", "Computer what is up"))
        val store = createStore(engine = engine, audioStream = audioStream, stt = stt)
        startLive(store, this)
        store.dispatch(ChatIntent.ToggleWakeWord)

        audioStream.pushUtterance()
        advanceUntilIdle()
        assertTrue(engine.prompts.isEmpty())

        audioStream.pushUtterance()
        advanceUntilIdle()
        assertEquals(1, engine.prompts.size)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun sttFailureIsTreatedAsUnaddressedSpeech() = runTest {
        val engine = FakeLlmEngine()
        val audioStream = FakeAudioStream()
        val stt = FakeSpeechToText().apply { error = IllegalStateException("model missing") }
        val store = createStore(engine = engine, audioStream = audioStream, stt = stt)
        startLive(store, this)
        store.dispatch(ChatIntent.ToggleWakeWord)

        audioStream.pushUtterance()
        advanceUntilIdle()

        assertTrue(engine.prompts.isEmpty())
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun togglingWakeWordClearsOverheardAndWoken() = runTest {
        val engine = FakeLlmEngine()
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val stt = FakeSpeechToText(listOf("some chatter", "Computer", "Computer"))
        val store = createStore(engine = engine, audioStream = audioStream, synthesizer = synthesizer, stt = stt)
        startLive(store, this)
        store.dispatch(ChatIntent.ToggleWakeWord)

        audioStream.pushUtterance()
        advanceUntilIdle()
        store.dispatch(ChatIntent.ToggleWakeWord)
        store.dispatch(ChatIntent.ToggleWakeWord)

        // With overheard cleared, the bare wake word is an acknowledgement rather than a prompt.
        audioStream.pushUtterance()
        advanceUntilIdle()
        assertTrue(engine.prompts.isEmpty())
        assertTrue(synthesizer.enqueuedTexts.isEmpty())

        // Toggling clears the woken state too, so the next bare wake word acknowledges again.
        store.dispatch(ChatIntent.ToggleWakeWord)
        store.dispatch(ChatIntent.ToggleWakeWord)
        synthesizer.finishSpeaking()
        audioStream.pushUtterance()
        advanceUntilIdle()
        assertTrue(engine.prompts.isEmpty())
        assertTrue(synthesizer.enqueuedTexts.isEmpty())

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun exitingLiveModeClearsOverheard() = runTest {
        val engine = FakeLlmEngine()
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val stt = FakeSpeechToText(listOf("some chatter", "Computer"))
        val store = createStore(engine = engine, audioStream = audioStream, synthesizer = synthesizer, stt = stt)
        startLive(store, this)
        store.dispatch(ChatIntent.ToggleWakeWord)

        audioStream.pushUtterance()
        advanceUntilIdle()
        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
        store.dispatch(ChatIntent.EnterLiveMode)
        advanceUntilIdle()

        audioStream.pushUtterance()
        advanceUntilIdle()

        assertTrue(engine.prompts.isEmpty())
        assertTrue(synthesizer.enqueuedTexts.isEmpty())

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun bargeInIsAllowedWhenWakeWordIsOnAndTheNameIsInTheSameUtterance() = runTest {
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Speaking a long sentence.")
            holdAfterChunks = true
        }
        val audioStream = FakeAudioStream()
        val stt = FakeSpeechToText(listOf("Computer tell me a story"))
        val store = createStore(engine = engine, audioStream = audioStream, stt = stt)
        startLive(store, this)
        store.dispatch(ChatIntent.ToggleWakeWord)

        audioStream.pushUtterance()
        advanceUntilIdle()
        assertEquals(LivePhase.SPEAKING, store.state.value.livePhase)

        audioStream.push(audioStream.tone(ms = 640, amplitude = 0.2))
        advanceUntilIdle()

        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertEquals(1, engine.cancelCount)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun bargeInIsAllowedWhenWakeWordIsOnAndAssistantWasWoken() = runTest {
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Speaking a long sentence.")
            holdAfterChunks = true
        }
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val stt = FakeSpeechToText(listOf("Computer", "tell me a story"))
        val store = createStore(engine = engine, audioStream = audioStream, synthesizer = synthesizer, stt = stt)
        startLive(store, this)
        store.dispatch(ChatIntent.ToggleWakeWord)

        audioStream.pushUtterance()
        advanceUntilIdle()
        synthesizer.finishSpeaking()
        audioStream.pushUtterance()
        advanceUntilIdle()
        assertEquals(LivePhase.SPEAKING, store.state.value.livePhase)

        audioStream.push(audioStream.tone(ms = 640, amplitude = 0.2))
        advanceUntilIdle()

        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertEquals(1, engine.cancelCount)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun halfDuplexIgnoresFramesWhileSpeaking() = runTest {
        val config = VoiceConfig(halfDuplex = true)
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Speaking a long sentence.")
            holdAfterChunks = true
        }
        val audioStream = FakeAudioStream(config)
        val store = createStore(engine = engine, audioStream = audioStream, config = config)
        startLive(store, this)

        audioStream.pushUtterance()
        advanceUntilIdle()
        assertEquals(LivePhase.SPEAKING, store.state.value.livePhase)

        audioStream.push(audioStream.tone(ms = 800, amplitude = 0.3))
        advanceUntilIdle()

        assertEquals(LivePhase.SPEAKING, store.state.value.livePhase)
        assertEquals(0, engine.cancelCount)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun ignoreMicWhileSpeakingDropsSpeechWhileTheReplyPlays() = runTest {
        val config = VoiceConfig(ignoreMicWhileSpeaking = true)
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Speaking a long sentence.")
            holdAfterChunks = true
        }
        val audioStream = FakeAudioStream(config)
        val store = createStore(engine = engine, audioStream = audioStream, config = config)
        startLive(store, this)

        audioStream.pushUtterance()
        advanceUntilIdle()
        assertEquals(LivePhase.SPEAKING, store.state.value.livePhase)

        audioStream.push(audioStream.tone(ms = 800, amplitude = 0.3))
        advanceUntilIdle()

        assertEquals(LivePhase.SPEAKING, store.state.value.livePhase)
        assertEquals(0, engine.cancelCount)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun ignoreMicWhileSpeakingStillLetsSpeechBargeInBeforeTheReplyPlays() = runTest {
        val config = VoiceConfig(ignoreMicWhileSpeaking = true)
        val engine = FakeLlmEngine().apply { gate = CompletableDeferred() }
        val audioStream = FakeAudioStream(config)
        val store = createStore(engine = engine, audioStream = audioStream, config = config)
        startLive(store, this)

        audioStream.pushUtterance()
        advanceUntilIdle()
        assertEquals(LivePhase.THINKING, store.state.value.livePhase)

        audioStream.push(audioStream.tone(ms = 640, amplitude = 0.2))
        advanceUntilIdle()

        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun ignoreMicWhileSpeakingDropsTheEchoTailThenListensAgain() = runTest {
        val config = VoiceConfig(ignoreMicWhileSpeaking = true, echoTailMs = 500)
        val engine = FakeLlmEngine().apply { chunks = listOf("Short reply.") }
        val audioStream = FakeAudioStream(config)
        val synthesizer = FakeSpeechSynthesizer()
        val store = createStore(engine = engine, audioStream = audioStream, synthesizer = synthesizer, config = config)
        startLive(store, this)

        audioStream.pushUtterance()
        advanceUntilIdle()
        audioStream.push(audioStream.silence(ms = 100))
        advanceUntilIdle()
        synthesizer.finishSpeaking()
        advanceUntilIdle()
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)

        // The tail of the reply, still inside echoTailMs, is not a turn.
        audioStream.push(audioStream.tone(ms = 480, amplitude = 0.2))
        audioStream.push(audioStream.silence(ms = 700))
        advanceUntilIdle()
        assertEquals(1, engine.prompts.size)

        audioStream.pushUtterance()
        advanceUntilIdle()
        assertEquals(2, engine.prompts.size)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun speechThatBargesInIsAnsweredWithoutTheWakeWord() = runTest {
        // Card 176 (Nick, 2026-11-02): words that cut a reply short are addressed to the assistant.
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Speaking a long sentence.")
            holdAfterChunks = true
        }
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val stt = FakeSpeechToText(listOf("Computer", "tell me about the Pixel", "actually tell me about the eight"))
        val store = createStore(engine = engine, audioStream = audioStream, synthesizer = synthesizer, stt = stt)
        startLive(store, this)
        store.dispatch(ChatIntent.ToggleWakeWord)

        audioStream.pushUtterance()
        advanceUntilIdle()
        synthesizer.finishSpeaking()
        audioStream.pushUtterance()
        advanceUntilIdle()
        assertEquals(LivePhase.SPEAKING, store.state.value.livePhase)

        // The reply has started speaking, so this barge-in is not merged with the earlier question.
        audioStream.push(audioStream.tone(ms = 640, amplitude = 0.2))
        advanceUntilIdle()
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertEquals(1, engine.cancelCount)

        engine.chunks = listOf("The Pixel 8 is older.")
        engine.holdAfterChunks = false
        audioStream.push(audioStream.silence(ms = 704))
        advanceUntilIdle()

        assertEquals(2, engine.prompts.size)
        val messages = store.state.value.messages
        assertTrue(messages.none { it.role == Role.OVERHEARD }, "barge-in speech must not be filed as overheard")
        assertEquals(Role.USER, messages[messages.size - 2].role)
        assertEquals("actually tell me about the eight", messages[messages.size - 2].text)
        assertEquals("The Pixel 8 is older.", messages.last().text)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun generationWarmsTheSpeakerBeforeSpeech() = runTest {
        // Card 213: the output path is opened when the reply starts generating, not when the first clause arrives.
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Hello there.")
            holdAfterChunks = true
        }
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val store = createStore(engine = engine, audioStream = audioStream, synthesizer = synthesizer)
        startLive(store, this)
        assertEquals(0, synthesizer.warmUpCount)

        audioStream.pushUtterance()
        advanceUntilIdle()

        assertEquals(1, synthesizer.warmUpCount)
        assertEquals(listOf("Hello there."), synthesizer.enqueuedTexts)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun liveTurnSpeaksEveryClause() = runTest {
        val engine = FakeLlmEngine().apply {
            chunks = listOf("First one.", "First one. Second one.", "First one. Second one. Third one.")
        }
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val store = createStore(engine = engine, audioStream = audioStream, synthesizer = synthesizer)
        startLive(store, this)

        audioStream.pushUtterance()
        advanceUntilIdle()

        assertEquals("First one. Second one. Third one.", synthesizer.enqueuedTexts.joinToString(" "))
        assertTrue(synthesizer.spokenTexts.isEmpty())

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun questionSpokenWhileTheWakeWordIsTranscribedIsAnswered() = runTest {
        // Card 220: "Computer", a short pause, "what time is it" must not lose the question
        // to the wake word's transcription window.
        val engine = FakeLlmEngine().apply { chunks = listOf("It is noon.") }
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val stt = FakeSpeechToText(listOf("Computer", "what time is it")).apply { delayMs = 1_500L }
        val store = createStore(engine = engine, audioStream = audioStream, synthesizer = synthesizer, stt = stt)
        startLive(store, this)
        store.dispatch(ChatIntent.ToggleWakeWord)

        audioStream.pushUtterance()
        // Still transcribing "Computer": the question arrives now.
        audioStream.pushUtterance()
        advanceTimeBy(1_600L)
        runCurrent()
        synthesizer.finishSpeaking()
        advanceUntilIdle()

        assertEquals(2, stt.callCount)
        assertEquals(1, engine.prompts.size)
        // No spoken "Yes?" over a user who kept talking; only the reply is enqueued.
        assertEquals(listOf("It is noon."), synthesizer.enqueuedTexts)
        val users = store.state.value.messages.filter { it.role == Role.USER }.map { it.text }
        assertEquals(listOf("Computer", "what time is it"), users)
        assertEquals("It is noon.", store.state.value.messages.last().text)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun silenceTranscribedAsNothingIsNotSentToTheModel() = runTest {
        // Card 220: a 1.3 s "Voice message" of nothing was answered with "Please let me know what you would like".
        val engine = FakeLlmEngine().apply { chunks = listOf("Reply.") }
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val stt = FakeSpeechToText(listOf("Computer", ""))
        val store = createStore(engine = engine, audioStream = audioStream, synthesizer = synthesizer, stt = stt)
        startLive(store, this)
        store.dispatch(ChatIntent.ToggleWakeWord)

        audioStream.pushUtterance()
        advanceUntilIdle()
        synthesizer.finishSpeaking()
        val before = store.state.value.messages.size
        audioStream.pushUtterance()
        advanceUntilIdle()

        assertTrue(engine.prompts.isEmpty())
        assertEquals(before, store.state.value.messages.size)
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun listeningStartsWhileTheModelLoadsAndTheFirstQuestionWaitsForIt() = runTest {
        // Card 216: the console reads LISTENING as soon as the mic is up, not after the 2.6 GB model loads.
        val engine = FakeLlmEngine().apply {
            chunks = listOf("It is noon.")
            loadGate = kotlinx.coroutines.CompletableDeferred()
        }
        val audioStream = FakeAudioStream()
        val store = createStore(engine = engine, audioStream = audioStream, autoListen = true)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        assertEquals(ModelStatus.Loading, store.state.value.modelStatus)
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)

        audioStream.pushUtterance()
        advanceUntilIdle()
        assertEquals(LivePhase.THINKING, store.state.value.livePhase)
        assertTrue(engine.prompts.isEmpty())

        engine.loadGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(ModelStatus.Ready, store.state.value.modelStatus)
        assertEquals(1, engine.prompts.size)
        assertEquals("It is noon.", store.state.value.messages.last().text)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun listeningClosesWhenTheModelFailsToLoad() = runTest {
        val engine = FakeLlmEngine().apply {
            loadGate = kotlinx.coroutines.CompletableDeferred()
            loadError = IllegalStateException("corrupt model")
        }
        val audioStream = FakeAudioStream()
        val store = createStore(engine = engine, audioStream = audioStream, autoListen = true)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)

        engine.loadGate!!.complete(Unit)
        advanceUntilIdle()

        assertTrue(store.state.value.modelStatus is ModelStatus.Error)
        assertEquals(LivePhase.IDLE, store.state.value.livePhase)
    }

    @Test
    fun voiceDetectedFollowsTheVadAndClearsAtEndpointAndOnExit() = runTest {
        val config = VoiceConfig()
        val vad = FakeVad(config)
        val audioStream = FakeAudioStream(config)
        val store = createStore(audioStream = audioStream, config = config, vad = vad)
        startLive(store, this)
        assertFalse(store.state.value.voiceDetected)

        vad.fallbackProbability = 0f
        audioStream.push(audioStream.tone(ms = 320, amplitude = 0.2))
        advanceUntilIdle()
        assertFalse(store.state.value.voiceDetected, "loud audio the VAD rejects is not voice")

        vad.fallbackProbability = 0.9f
        audioStream.push(audioStream.tone(ms = 320, amplitude = 0.2))
        advanceUntilIdle()
        assertTrue(store.state.value.voiceDetected)

        vad.fallbackProbability = 0f
        audioStream.push(audioStream.silence(ms = 704))
        advanceUntilIdle()
        assertFalse(store.state.value.voiceDetected, "cleared once the utterance endpoints")

        vad.fallbackProbability = 0.9f
        audioStream.push(audioStream.tone(ms = 320, amplitude = 0.2))
        advanceUntilIdle()
        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
        assertFalse(store.state.value.voiceDetected)
    }

    @Test
    fun voiceDetectedClearsWhenSpeechBufferedDuringTranscriptionIsDiscardedByTheTurn() = runTest {
        val config = VoiceConfig()
        val vad = FakeVad(config)
        val audioStream = FakeAudioStream(config)
        val stt = FakeSpeechToText().apply { delayMs = 5_000L }
        val store = createStore(audioStream = audioStream, stt = stt, config = config, vad = vad)
        startLive(store, this)

        vad.fallbackProbability = 0.9f
        audioStream.push(audioStream.tone(ms = 400, amplitude = 0.2))
        advanceTimeBy(1_000L)
        vad.fallbackProbability = 0f
        audioStream.push(audioStream.silence(ms = 704))
        advanceTimeBy(1_000L)
        assertEquals(1, stt.callCount)

        vad.fallbackProbability = 0.9f
        audioStream.push(audioStream.tone(ms = 320, amplitude = 0.2))
        advanceTimeBy(1_000L)
        assertTrue(store.state.value.voiceDetected, "voice heard while the first utterance is transcribed")

        advanceUntilIdle()
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertFalse(store.state.value.voiceDetected, "the turn reset the endpointer, so the flag must clear")

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun bargeInRequiresProbabilityAtOrAboveBargeInThreshold() = runTest {
        val config = VoiceConfig()
        val vad = FakeVad(config)
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Speaking a long sentence.")
            holdAfterChunks = true
        }
        val audioStream = FakeAudioStream(config)
        val store = createStore(engine = engine, audioStream = audioStream, config = config, vad = vad)
        startLive(store, this)

        audioStream.pushUtterance()
        advanceUntilIdle()
        assertEquals(LivePhase.SPEAKING, store.state.value.livePhase)

        // Above vadThreshold (0.6) but below bargeInThreshold (0.8): no barge-in.
        vad.fallbackProbability = 0.7f
        audioStream.push(audioStream.tone(ms = 640, amplitude = 0.2))
        advanceUntilIdle()
        assertEquals(LivePhase.SPEAKING, store.state.value.livePhase)
        assertEquals(0, engine.cancelCount)

        vad.fallbackProbability = 0.9f
        audioStream.push(audioStream.tone(ms = 320, amplitude = 0.2))
        advanceUntilIdle()
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertEquals(1, engine.cancelCount)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun bargeInBeforeAnySpeechMergesPreviousUtteranceAndDropsBothEarlierBubbles() = runTest {
        val config = VoiceConfig(sendAudioToModel = true)
        val engine = FakeLlmEngine().apply { holdAfterChunks = true }
        val audioStream = FakeAudioStream(config)
        val store = createStore(engine = engine, audioStream = audioStream, config = config)
        startLive(store, this)

        // 400ms speech + 704ms silence = 1120ms.
        audioStream.pushUtterance()
        advanceUntilIdle()
        assertEquals(LivePhase.THINKING, store.state.value.livePhase)
        val firstWavSize = assertIs<LlmPrompt.Audio>(engine.prompts[0]).wavBytes.size

        // Barge in before any sentence was spoken, then finish the follow-on utterance.
        audioStream.push(audioStream.tone(ms = 256, amplitude = 0.2))
        advanceUntilIdle()
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertEquals(1, store.state.value.messages.size)

        engine.chunks = listOf("Merged reply here.")
        engine.holdAfterChunks = false
        audioStream.push(audioStream.tone(ms = 160, amplitude = 0.2))
        audioStream.push(audioStream.silence(ms = 704))
        advanceUntilIdle()

        assertEquals(2, engine.prompts.size)
        val merged = assertIs<LlmPrompt.Audio>(engine.prompts[1]).wavBytes.size
        val newPcmBytes = 1120 * config.bytesPerMs
        assertEquals(firstWavSize + newPcmBytes, merged)
        // Only the combined user bubble and its reply remain; the interrupted pair is gone.
        assertEquals(
            listOf(Role.USER, Role.ASSISTANT),
            store.state.value.messages.map { it.role }
        )
        assertEquals("Merged reply here.", store.state.value.messages.last().text)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun bargeInBeforeAnySpeechDoesNotMergeWhenThePreviousUtteranceIsTooLong() = runTest {
        val config = VoiceConfig(maxMergeMs = 500, sendAudioToModel = true)
        val engine = FakeLlmEngine().apply { holdAfterChunks = true }
        val audioStream = FakeAudioStream(config)
        val store = createStore(engine = engine, audioStream = audioStream, config = config)
        startLive(store, this)

        // The first utterance is about 1.1 s, over the 500 ms merge cap.
        audioStream.pushUtterance()
        advanceUntilIdle()
        assertEquals(LivePhase.THINKING, store.state.value.livePhase)

        audioStream.push(audioStream.tone(ms = 256, amplitude = 0.2))
        advanceUntilIdle()
        engine.chunks = emptyList()
        engine.holdAfterChunks = false
        audioStream.push(audioStream.tone(ms = 160, amplitude = 0.2))
        audioStream.push(audioStream.silence(ms = 704))
        advanceUntilIdle()

        val second = assertIs<LlmPrompt.Audio>(engine.prompts[1])
        assertEquals(WavEncoder.HEADER_SIZE + 1120 * config.bytesPerMs, second.wavBytes.size)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun bargeInAfterSentenceSpokenDoesNotMergeAndKeepsAssistantBubble() = runTest {
        val config = VoiceConfig(sendAudioToModel = true)
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Speaking a long sentence.")
            holdAfterChunks = true
        }
        val audioStream = FakeAudioStream(config)
        val store = createStore(engine = engine, audioStream = audioStream, config = config)
        startLive(store, this)

        audioStream.pushUtterance()
        advanceUntilIdle()
        assertEquals(LivePhase.SPEAKING, store.state.value.livePhase)

        audioStream.push(audioStream.tone(ms = 256, amplitude = 0.2))
        advanceUntilIdle()
        assertEquals(2, store.state.value.messages.size)
        assertEquals("Speaking a long sentence.", store.state.value.messages.last().text)

        engine.chunks = emptyList()
        audioStream.push(audioStream.tone(ms = 160, amplitude = 0.2))
        audioStream.push(audioStream.silence(ms = 704))
        advanceUntilIdle()

        val second = assertIs<LlmPrompt.Audio>(engine.prompts[1])
        assertEquals(WavEncoder.HEADER_SIZE + 1120 * config.bytesPerMs, second.wavBytes.size)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun toggleMuteWhileListeningCancelsCollectorAndIgnoresFrames() = runTest {
        val engine = FakeLlmEngine()
        val audioStream = FakeAudioStream()
        val store = createStore(engine = engine, audioStream = audioStream)
        startLive(store, this)

        assertEquals(1, audioStream.activeCollectors)
        assertFalse(store.state.value.micMuted)
        assertEquals("I'm listening", store.phaseLabel())

        store.dispatch(ChatIntent.ToggleMute)
        advanceUntilIdle()

        assertEquals(0, audioStream.activeCollectors)
        assertTrue(store.state.value.micMuted)
        assertEquals("Microphone is off", store.phaseLabel())

        audioStream.pushUtterance()
        advanceUntilIdle()
        assertTrue(engine.prompts.isEmpty())

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun unmuteRestartsCollectorAndUtteranceIsProcessed() = runTest {
        val engine = FakeLlmEngine()
        val audioStream = FakeAudioStream()
        val store = createStore(engine = engine, audioStream = audioStream)
        startLive(store, this)

        store.dispatch(ChatIntent.ToggleMute)
        advanceUntilIdle()
        assertEquals(0, audioStream.activeCollectors)
        assertTrue(store.state.value.micMuted)

        store.dispatch(ChatIntent.ToggleMute)
        advanceUntilIdle()
        assertEquals(1, audioStream.activeCollectors)
        assertFalse(store.state.value.micMuted)
        assertEquals("I'm listening", store.phaseLabel())

        audioStream.pushUtterance()
        advanceUntilIdle()
        assertEquals(1, engine.prompts.size)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun muteDuringThinkingAllowsReplyToCompleteAndStaysMutedWithoutCollector() = runTest {
        val gate = CompletableDeferred<Unit>()
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Thinking reply.")
            this.gate = gate
        }
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val store = createStore(engine = engine, audioStream = audioStream, synthesizer = synthesizer)
        startLive(store, this)

        audioStream.pushUtterance()
        advanceUntilIdle()
        assertEquals(LivePhase.THINKING, store.state.value.livePhase)
        assertEquals("Thinking…", store.phaseLabel())

        store.dispatch(ChatIntent.ToggleMute)
        advanceUntilIdle()
        assertTrue(store.state.value.micMuted)
        assertEquals(0, audioStream.activeCollectors)
        assertEquals(0, engine.cancelCount)
        assertEquals("Thinking…", store.phaseLabel())

        gate.complete(Unit)
        advanceUntilIdle()
        synthesizer.finishSpeaking()
        advanceUntilIdle()

        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertTrue(store.state.value.micMuted)
        assertEquals(0, audioStream.activeCollectors)
        assertEquals("Thinking reply.", store.state.value.messages.last().text)
        assertEquals("Microphone is off", store.phaseLabel())

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun muteDuringSpeakingAllowsReplyToCompleteAndStaysMutedWithoutCollector() = runTest {
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Speaking reply.")
        }
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val store = createStore(engine = engine, audioStream = audioStream, synthesizer = synthesizer)
        startLive(store, this)

        audioStream.pushUtterance()
        advanceUntilIdle()
        assertEquals(LivePhase.SPEAKING, store.state.value.livePhase)
        assertEquals("Computer is speaking", store.phaseLabel())

        val stopCountBeforeMute = synthesizer.stopCount
        store.dispatch(ChatIntent.ToggleMute)
        advanceUntilIdle()
        assertTrue(store.state.value.micMuted)
        assertEquals(0, audioStream.activeCollectors)
        assertEquals(stopCountBeforeMute, synthesizer.stopCount)
        assertEquals("Computer is speaking", store.phaseLabel())

        synthesizer.finishSpeaking()
        advanceUntilIdle()

        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertTrue(store.state.value.micMuted)
        assertEquals(0, audioStream.activeCollectors)
        assertEquals("Speaking reply.", store.state.value.messages.last().text)
        assertEquals("Microphone is off", store.phaseLabel())

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun exitLiveModeAndNewChatWhileMutedResetMicMuted() = runTest {
        val engine = FakeLlmEngine()
        val audioStream = FakeAudioStream()
        val store = createStore(engine = engine, audioStream = audioStream)
        startLive(store, this)

        store.dispatch(ChatIntent.ToggleMute)
        advanceUntilIdle()
        assertTrue(store.state.value.micMuted)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
        assertFalse(store.state.value.micMuted)
        assertEquals(LivePhase.IDLE, store.state.value.livePhase)

        store.dispatch(ChatIntent.EnterLiveMode)
        advanceUntilIdle()
        assertFalse(store.state.value.micMuted)

        store.dispatch(ChatIntent.ToggleMute)
        advanceUntilIdle()
        assertTrue(store.state.value.micMuted)

        store.dispatch(ChatIntent.NewChat)
        advanceUntilIdle()
        assertFalse(store.state.value.micMuted)
        assertEquals(LivePhase.IDLE, store.state.value.livePhase)
    }

    @Test
    fun enterLiveModeStartsUnmuted() = runTest {
        val engine = FakeLlmEngine()
        val audioStream = FakeAudioStream()
        val store = createStore(engine = engine, audioStream = audioStream)

        assertFalse(store.state.value.micMuted)
        startLive(store, this)
        assertFalse(store.state.value.micMuted)
        assertEquals(1, audioStream.activeCollectors)
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun liveTapWhileMutedDoesNotTriggerEndpointInListening() = runTest {
        val engine = FakeLlmEngine()
        val audioStream = FakeAudioStream()
        val store = createStore(engine = engine, audioStream = audioStream)
        startLive(store, this)

        store.dispatch(ChatIntent.ToggleMute)
        advanceUntilIdle()
        assertTrue(store.state.value.micMuted)

        store.dispatch(ChatIntent.LiveTap)
        advanceUntilIdle()
        assertTrue(engine.prompts.isEmpty())
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun liveTapWhileMutedInterruptsSpeakingReply() = runTest {
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Speaking something long.")
        }
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val store = createStore(engine = engine, audioStream = audioStream, synthesizer = synthesizer)
        startLive(store, this)

        audioStream.pushUtterance()
        advanceUntilIdle()
        assertEquals(LivePhase.SPEAKING, store.state.value.livePhase)

        val stopCountBeforeMute = synthesizer.stopCount
        store.dispatch(ChatIntent.ToggleMute)
        advanceUntilIdle()
        assertTrue(store.state.value.micMuted)
        assertEquals(stopCountBeforeMute, synthesizer.stopCount)
        assertEquals(LivePhase.SPEAKING, store.state.value.livePhase)

        store.dispatch(ChatIntent.LiveTap)
        advanceUntilIdle()
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertTrue(store.state.value.micMuted)
        assertEquals(stopCountBeforeMute + 1, synthesizer.stopCount)
        assertEquals(0, audioStream.activeCollectors)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun wakeWordOffSttReturnsEmptyDropsUtteranceAndStaysListening() = runTest {
        val engine = FakeLlmEngine()
        val audioStream = FakeAudioStream()
        val stt = FakeSpeechToText().apply { defaultTranscript = "" }
        val store = createStore(engine = engine, audioStream = audioStream, stt = stt)
        startLive(store, this)

        audioStream.pushUtterance()
        advanceUntilIdle()

        assertEquals(1, stt.callCount)
        assertTrue(engine.prompts.isEmpty())
        assertTrue(store.state.value.messages.isEmpty())
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertFalse(store.state.value.isGenerating)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun wakeWordOffSttReturnsSpeechCallsEngineAndSetsUserBubble() = runTest {
        val engine = FakeLlmEngine().apply { holdAfterChunks = true }
        val audioStream = FakeAudioStream()
        val stt = FakeSpeechToText(listOf("what time is it"))
        val store = createStore(engine = engine, audioStream = audioStream, stt = stt)
        startLive(store, this)

        audioStream.pushUtterance()
        advanceUntilIdle()

        assertEquals(1, stt.callCount)
        assertEquals(1, engine.prompts.size)
        assertEquals(LlmPrompt.Text("what time is it"), engine.prompts.single())
        val userBubble = store.state.value.messages.first()
        assertEquals("what time is it", userBubble.text)
        assertTrue(userBubble.isVoice)
        assertEquals(LivePhase.THINKING, store.state.value.livePhase)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun wakeWordOffWhenSttNotAvailableSendsAudioWithoutTranscript() = runTest {
        val engine = FakeLlmEngine().apply { holdAfterChunks = true }
        val audioStream = FakeAudioStream()
        val stt = FakeSpeechToText().apply { isAvailable = false }
        val store = createStore(engine = engine, audioStream = audioStream, stt = stt)
        startLive(store, this)

        audioStream.pushUtterance()
        advanceUntilIdle()

        assertEquals(0, stt.callCount)
        assertEquals(1, engine.prompts.size)
        val prompt = assertIs<LlmPrompt.Audio>(engine.prompts.single())
        assertEquals(VOICE_INSTRUCTION, prompt.instruction)
        val userBubble = store.state.value.messages.first()
        assertTrue(userBubble.text.startsWith("Voice message"))
        assertTrue(userBubble.isVoice)
        assertEquals(LivePhase.THINKING, store.state.value.livePhase)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun wakeWordOffWhenSttThrowsFallsBackToSendingAudio() = runTest {
        val engine = FakeLlmEngine().apply { holdAfterChunks = true }
        val audioStream = FakeAudioStream()
        val stt = FakeSpeechToText().apply { error = RuntimeException("STT failed") }
        val store = createStore(engine = engine, audioStream = audioStream, stt = stt)
        startLive(store, this)

        audioStream.pushUtterance()
        advanceUntilIdle()

        assertEquals(1, stt.callCount)
        assertEquals(1, engine.prompts.size)
        val prompt = assertIs<LlmPrompt.Audio>(engine.prompts.single())
        assertEquals(VOICE_INSTRUCTION, prompt.instruction)
        val userBubble = store.state.value.messages.first()
        assertTrue(userBubble.text.startsWith("Voice message"))
        assertTrue(userBubble.isVoice)
        assertEquals(LivePhase.THINKING, store.state.value.livePhase)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun wakeWordOffBargeInMergeCombinesEarlierAndNewTranscripts() = runTest {
        val config = VoiceConfig()
        val engine = FakeLlmEngine().apply { holdAfterChunks = true }
        val audioStream = FakeAudioStream(config)
        val stt = FakeSpeechToText(listOf("what time is", "it"))
        val store = createStore(engine = engine, audioStream = audioStream, stt = stt, config = config)
        startLive(store, this)

        audioStream.pushUtterance()
        advanceUntilIdle()
        assertEquals(LivePhase.THINKING, store.state.value.livePhase)
        assertEquals("what time is", store.state.value.messages.first().text)

        // Barge in before speech
        audioStream.push(audioStream.tone(ms = 256, amplitude = 0.2))
        advanceUntilIdle()
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)

        // Finish follow-on utterance
        engine.chunks = listOf("Merged answer.")
        engine.holdAfterChunks = false
        audioStream.push(audioStream.tone(ms = 160, amplitude = 0.2))
        audioStream.push(audioStream.silence(ms = 704))
        advanceUntilIdle()

        assertEquals(2, engine.prompts.size)
        assertEquals(LlmPrompt.Text("what time is it"), engine.prompts[1])
        assertEquals(2, stt.callCount)
        val userBubble = store.state.value.messages.first()
        assertEquals("what time is it", userBubble.text)
        assertEquals("Merged answer.", store.state.value.messages.last().text)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun wakeWordOnAddressedUtteranceBubbleOmitsOverheardPrefix() = runTest {
        val engine = FakeLlmEngine().apply { holdAfterChunks = true }
        val audioStream = FakeAudioStream()
        val stt = FakeSpeechToText(listOf("we should leave at five", "Computer, what time is it?"))
        val store = createStore(engine = engine, audioStream = audioStream, stt = stt)
        startLive(store, this)
        store.dispatch(ChatIntent.ToggleWakeWord)

        repeat(2) {
            audioStream.pushUtterance()
            advanceUntilIdle()
        }

        assertEquals(
            LlmPrompt.Text(withOverheard(listOf("we should leave at five"), "Computer, what time is it?")),
            engine.prompts.single()
        )
        val userBubble = store.state.value.messages.first { it.role == Role.USER }
        assertEquals("Computer, what time is it?", userBubble.text)
        assertTrue(userBubble.isVoice)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun sendAudioToModelSendsAudioEvenWhenSttIsAvailable() = runTest {
        val engine = FakeLlmEngine().apply { holdAfterChunks = true }
        val audioStream = FakeAudioStream()
        val stt = FakeSpeechToText(listOf("what time is it"))
        val store = createStore(
            engine = engine,
            audioStream = audioStream,
            stt = stt,
            config = VoiceConfig(sendAudioToModel = true)
        )
        startLive(store, this)

        audioStream.pushUtterance()
        advanceUntilIdle()

        assertEquals(VOICE_INSTRUCTION, assertIs<LlmPrompt.Audio>(engine.prompts.single()).instruction)
        assertEquals("what time is it", store.state.value.messages.first().text)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun offlineVoiceUnavailableInLiveModeKeepsTextReplyAndSkipsSpeech() = runTest {
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Live text answer")
        }
        val synthesizer = FakeSpeechSynthesizer().apply {
            setOfflineVoiceAvailable(false)
        }
        val audioStream = FakeAudioStream()
        val store = createStore(
            engine = engine,
            synthesizer = synthesizer,
            audioStream = audioStream
        )
        startLive(store, this)

        assertTrue(store.state.value.offlineVoiceMissing)

        audioStream.pushUtterance()
        advanceUntilIdle()

        val state = store.state.value
        assertTrue(state.offlineVoiceMissing)
        val assistantMessages = state.messages.filter { it.role == Role.ASSISTANT }
        assertEquals(1, assistantMessages.size)
        assertEquals("Live text answer", assistantMessages.first().text)
        assertTrue(synthesizer.spokenTexts.isEmpty())
        assertTrue(synthesizer.enqueuedTexts.isEmpty())
    }

    @Test
    fun outputLevelUpdatesLiveLevelDuringSpeakingAndIsIgnoredAfterwards() = runTest {
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Speaking a long sentence.")
        }
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val stt = FakeSpeechToText(listOf("Computer tell me a story"))
        val store = createStore(engine = engine, audioStream = audioStream, synthesizer = synthesizer, stt = stt)
        startLive(store, this)
        store.dispatch(ChatIntent.ToggleWakeWord)

        audioStream.pushUtterance()
        advanceUntilIdle()
        assertEquals(LivePhase.SPEAKING, store.state.value.livePhase)

        synthesizer.setOutputLevel(0.75f)
        advanceUntilIdle()
        assertEquals(0.75f, store.state.value.liveLevel)

        synthesizer.setOutputLevel(0.4f)
        advanceUntilIdle()
        assertEquals(0.4f, store.state.value.liveLevel)

        synthesizer.finishSpeaking()
        advanceUntilIdle()
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertEquals(0f, store.state.value.liveLevel)

        synthesizer.setOutputLevel(0.9f)
        advanceUntilIdle()
        assertEquals(0f, store.state.value.liveLevel)
    }

    @Test
    fun flatSynthesizerLeavesLiveLevelAtZeroWhileSpeaking() = runTest {
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Speaking a long sentence.")
        }
        val audioStream = FakeAudioStream()
        val flatSynthesizer = object : SpeechSynthesizer {
            private val _isSpeaking = kotlinx.coroutines.flow.MutableStateFlow(false)
            override val isSpeaking: kotlinx.coroutines.flow.StateFlow<Boolean> = _isSpeaking
            override val offlineVoiceAvailable: kotlinx.coroutines.flow.StateFlow<Boolean> =
                kotlinx.coroutines.flow.MutableStateFlow(true)
            override fun speak(text: String) { _isSpeaking.value = true }
            override fun enqueue(text: String) { _isSpeaking.value = true }
            override fun stop() { _isSpeaking.value = false }
        }
        val stt = FakeSpeechToText(listOf("Computer tell me a story"))
        val store = createStore(engine = engine, audioStream = audioStream, synthesizer = flatSynthesizer, stt = stt)
        startLive(store, this)
        store.dispatch(ChatIntent.ToggleWakeWord)

        audioStream.pushUtterance()
        advanceUntilIdle()
        assertEquals(LivePhase.SPEAKING, store.state.value.livePhase)
        assertEquals(0f, store.state.value.liveLevel)
    }
}

private class FakeResourceSampler : ResourceSampler {
    var sampleCount = 0
    var nextCpuPercent: Double? = null
    var nextPssMb: Double? = null
    val loggedPeaks = mutableListOf<Pair<Double?, Double?>>()

    override fun sample(): ResourceSample {
        sampleCount++
        return ResourceSample(
            cpuPercent = nextCpuPercent ?: sampleCount.toDouble(),
            pssMb = nextPssMb ?: (100.0 + sampleCount),
            availMb = 900.0,
            at = sampleCount.toLong()
        )
    }

    override fun logTurnPeaks(cpuPercent: Double?, pssMb: Double?) {
        loggedPeaks += cpuPercent to pssMb
    }
}
