package com.chrishonson.gemmavoice.chat

import com.chrishonson.gemmavoice.llm.LlmPrompt
import com.chrishonson.gemmavoice.model.ModelSpec
import com.chrishonson.gemmavoice.model.Models
import com.chrishonson.gemmavoice.net.NetworkActivity
import com.chrishonson.gemmavoice.voice.InMemorySettingsStore
import com.chrishonson.gemmavoice.voice.SettingsStore
import com.chrishonson.gemmavoice.voice.VoiceConfig
import com.chrishonson.gemmavoice.voice.WavEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class ChatStoreTest {
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
        repo: FakeModelRepository = FakeModelRepository(downloaded = true),
        audioStream: FakeAudioStream = FakeAudioStream(),
        synthesizer: FakeSpeechSynthesizer = FakeSpeechSynthesizer(),
        micPermission: FakeMicPermission = FakeMicPermission(granted = true),
        config: VoiceConfig = VoiceConfig(),
        spec: ModelSpec = testSpec,
        settings: SettingsStore = InMemorySettingsStore(),
        wakeWordDefault: Boolean = false,
        autoListen: Boolean = false,
        networkActivity: NetworkActivity = NetworkActivity(now = { 0L })
    ): ChatStore = ChatStore(
        engine = engine,
        repo = repo,
        audioStream = audioStream,
        synthesizer = synthesizer,
        micPermission = micPermission,
        stt = FakeSpeechToText(),
        config = config,
        models = listOf(spec),
        settings = settings,
        wakeWordDefault = wakeWordDefault,
        autoListen = autoListen,
        voiceSpec = null,
        networkActivity = networkActivity
    )

    @Test
    fun constructorDefaultsSeedWakeWordAndAutoListen() {
        val store = createStore(wakeWordDefault = true, autoListen = true)

        assertTrue(store.state.value.wakeWordEnabled)
        assertTrue(store.state.value.autoListen)
    }

    @Test
    fun persistedWakeWordWinsOverConstructorDefaultInBothDirections() {
        val savedFalse = InMemorySettingsStore(wakeWord = false)
        val savedTrue = InMemorySettingsStore(wakeWord = true)

        assertFalse(createStore(settings = savedFalse, wakeWordDefault = true).state.value.wakeWordEnabled)
        assertTrue(createStore(settings = savedTrue, wakeWordDefault = false).state.value.wakeWordEnabled)
    }

    @Test
    fun persistedAutoListenWinsOverConstructorDefaultInBothDirections() {
        val savedFalse = InMemorySettingsStore(autoListen = false)
        val savedTrue = InMemorySettingsStore(autoListen = true)

        assertFalse(createStore(settings = savedFalse, autoListen = true).state.value.autoListen)
        assertTrue(createStore(settings = savedTrue, autoListen = false).state.value.autoListen)
    }

    @Test
    fun togglingWakeWordPersists() {
        val userSettings = InMemorySettingsStore()
        val userStore = createStore(settings = userSettings)

        userStore.dispatch(ChatIntent.ToggleWakeWord)

        assertEquals(true, userSettings.loadWakeWord())
    }

    @Test
    fun toggleAutoListenFlipsAndPersists() {
        val settings = InMemorySettingsStore()
        val store = createStore(settings = settings)

        store.dispatch(ChatIntent.ToggleAutoListen)

        assertTrue(store.state.value.autoListen)
        assertEquals(true, settings.loadAutoListen())
    }

    @Test
    fun autoListenPersistsAcrossStoreRecreation() {
        val settings = InMemorySettingsStore()
        val store1 = createStore(settings = settings, autoListen = false)

        store1.dispatch(ChatIntent.ToggleAutoListen)
        assertTrue(store1.state.value.autoListen)

        val store2 = createStore(settings = settings, autoListen = false)
        assertTrue(store2.state.value.autoListen)
    }

    @Test
    fun resourceMeterTogglePersistsAcrossStoreRecreation() {
        val settings = InMemorySettingsStore()
        val store1 = createStore(settings = settings)

        assertFalse(store1.state.value.showResourceMeter)
        store1.dispatch(ChatIntent.ToggleResourceMeter)

        assertTrue(store1.state.value.showResourceMeter)
        assertTrue(createStore(settings = settings).state.value.showResourceMeter)
    }

    @Test
    fun thinkingToggleReachesTheEngineAndPersistsAcrossStoreRecreation() {
        val settings = InMemorySettingsStore()
        val engine = FakeLlmEngine()
        val store1 = createStore(engine = engine, settings = settings)

        assertFalse(store1.state.value.thinking)
        assertFalse(engine.thinking)
        store1.dispatch(ChatIntent.ToggleThinking)

        assertTrue(store1.state.value.thinking)
        assertTrue(engine.thinking)
        val engine2 = FakeLlmEngine()
        assertTrue(createStore(engine = engine2, settings = settings).state.value.thinking)
        assertTrue(engine2.thinking)
        store1.dispatch(ChatIntent.ToggleThinking)
        assertFalse(engine.thinking)
        assertFalse(settings.loadThinking())
    }

    @Test
    fun autoListenEntersLiveModeWhenModelBecomesReady() = runTest {
        val micPermission = FakeMicPermission(granted = true)
        val store = createStore(micPermission = micPermission, autoListen = true)

        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        assertEquals(ModelStatus.Ready, store.state.value.modelStatus)
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertEquals(1, micPermission.ensureGrantedCount)
    }

    @Test
    fun foregroundRetriesAutoListenAfterMicPermissionIsGranted() = runTest {
        val micPermission = FakeMicPermission(granted = false)
        val store = createStore(micPermission = micPermission, autoListen = true)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()
        assertTrue(store.state.value.micDenied)
        assertEquals(LivePhase.IDLE, store.state.value.livePhase)

        micPermission.granted = true
        store.dispatch(ChatIntent.AppForegrounded)
        advanceUntilIdle()

        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertFalse(store.state.value.micDenied)
        assertEquals(2, micPermission.ensureGrantedCount)
    }

    @Test
    fun autoListenOffDoesNotEnterLiveMode() = runTest {
        val micPermission = FakeMicPermission(granted = true)
        val store = createStore(micPermission = micPermission, autoListen = false)

        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        assertEquals(ModelStatus.Ready, store.state.value.modelStatus)
        assertEquals(LivePhase.IDLE, store.state.value.livePhase)
        assertEquals(0, micPermission.ensureGrantedCount)
    }

    @Test
    fun startWithModelPresentLoadsAndBecomesReady() = runTest {
        val engine = FakeLlmEngine()
        val repo = FakeModelRepository(downloaded = true)
        val store = createStore(engine = engine, repo = repo)

        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        assertEquals(ModelStatus.Ready, store.state.value.modelStatus)
        assertEquals(listOf(repo.path), engine.loadedPaths)
    }

    @Test
    fun startWithModelMissingStaysMissing() = runTest {
        val engine = FakeLlmEngine()
        val repo = FakeModelRepository(downloaded = false)
        val store = createStore(engine = engine, repo = repo)

        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        assertEquals(ModelStatus.Missing, store.state.value.modelStatus)
        assertTrue(engine.loadedPaths.isEmpty())
    }

    @Test
    fun downloadModelEmitsDownloadingFractionsThenReady() = runTest {
        val engine = FakeLlmEngine()
        val repo = FakeModelRepository(downloaded = false).apply {
            progressEmissions = listOf(250L, 500L, 1000L)
        }
        val store = createStore(engine = engine, repo = repo)
        val statuses = mutableListOf<ModelStatus>()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            store.state.collect { statuses.add(it.modelStatus) }
        }

        store.dispatch(ChatIntent.DownloadModel)
        store.dispatch(ChatIntent.ConfirmDownload)
        advanceUntilIdle()
        collectJob.cancel()

        assertTrue(statuses.contains(ModelStatus.Downloading(0.25f)))
        assertTrue(statuses.contains(ModelStatus.Downloading(0.5f)))
        assertTrue(statuses.contains(ModelStatus.Downloading(1.0f)))
        assertTrue(statuses.contains(ModelStatus.Loading))
        assertEquals(ModelStatus.Ready, store.state.value.modelStatus)
    }

    @Test
    fun loadFailureTransitionsToError() = runTest {
        val engine = FakeLlmEngine().apply {
            loadError = IllegalStateException("Corrupt model file")
        }
        val repo = FakeModelRepository(downloaded = true)
        val store = createStore(engine = engine, repo = repo)

        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        assertEquals(ModelStatus.Error("Corrupt model file"), store.state.value.modelStatus)
    }

    @Test
    fun sendStreamsIntoOneAssistantBubbleAndFinalizes() = runTest {
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Hello", "Hello world")
        }
        val synthesizer = FakeSpeechSynthesizer()
        val store = createStore(engine = engine, synthesizer = synthesizer)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        store.dispatch(ChatIntent.InputChanged("Hi Gemma"))
        store.dispatch(ChatIntent.Send)
        advanceUntilIdle()

        val state = store.state.value
        assertEquals("", state.input)
        assertFalse(state.isGenerating)
        assertEquals(listOf<LlmPrompt>(LlmPrompt.Text("Hi Gemma")), engine.prompts)
        assertEquals(2, state.messages.size)
        assertEquals(Role.USER, state.messages[0].role)
        assertEquals("Hi Gemma", state.messages[0].text)
        assertFalse(state.messages[0].isVoice)
        assertEquals(Role.ASSISTANT, state.messages[1].role)
        assertEquals("Hello world", state.messages[1].text)
        assertFalse(state.messages[1].isStreaming)
        assertTrue(synthesizer.spokenTexts.isEmpty())
    }

    @Test
    fun engineCancellingOnItsOwnEndsTheTurnWithoutAnEmptyBubble() = runTest {
        val engine = FakeLlmEngine().apply { generateError = CancellationException("Task cancelled") }
        val store = createStore(engine = engine)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        store.dispatch(ChatIntent.InputChanged("Hi"))
        store.dispatch(ChatIntent.Send)
        advanceUntilIdle()

        val state = store.state.value
        assertFalse(state.isGenerating)
        assertEquals(listOf(Role.USER), state.messages.map { it.role })

        // The next turn still works.
        engine.generateError = null
        engine.chunks = listOf("Hello")
        store.dispatch(ChatIntent.InputChanged("Again"))
        store.dispatch(ChatIntent.Send)
        advanceUntilIdle()
        assertEquals("Hello", store.state.value.messages.last().text)
    }

    @Test
    fun engineCancellingAfterSomeTextKeepsThePartialReply() = runTest {
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Partial")
            generateError = CancellationException("Task cancelled")
        }
        val store = createStore(engine = engine)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        store.dispatch(ChatIntent.InputChanged("Hi"))
        store.dispatch(ChatIntent.Send)
        advanceUntilIdle()

        val reply = store.state.value.messages.last()
        assertEquals("Partial", reply.text)
        assertFalse(reply.isStreaming)
        assertFalse(store.state.value.isGenerating)
    }

    @Test
    fun sendIgnoredWhenNotReadyOrBlank() = runTest {
        val engine = FakeLlmEngine()
        val repo = FakeModelRepository(downloaded = false)
        val store = createStore(engine = engine, repo = repo)

        store.dispatch(ChatIntent.InputChanged("Hello"))
        store.dispatch(ChatIntent.Send)
        advanceUntilIdle()
        assertTrue(store.state.value.messages.isEmpty())

        repo.downloaded = true
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()
        store.dispatch(ChatIntent.InputChanged("   "))
        store.dispatch(ChatIntent.Send)
        advanceUntilIdle()
        assertTrue(store.state.value.messages.isEmpty())
    }

    @Test
    fun stopCallsCancelStopsTtsAndFinalizesBubble() = runTest {
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Partial response")
            holdAfterChunks = true
        }
        val synthesizer = FakeSpeechSynthesizer()
        val store = createStore(engine = engine, synthesizer = synthesizer)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        store.dispatch(ChatIntent.InputChanged("Tell me a story"))
        store.dispatch(ChatIntent.Send)
        runCurrent()

        assertTrue(store.state.value.isGenerating)
        assertTrue(store.state.value.messages.last().isStreaming)

        store.dispatch(ChatIntent.Stop)
        advanceUntilIdle()

        assertEquals(1, engine.cancelCount)
        assertEquals(1, synthesizer.stopCount)
        assertTrue(synthesizer.spokenTexts.isEmpty())
        assertFalse(store.state.value.isGenerating)
        val assistantMsg = store.state.value.messages.last()
        assertEquals("Partial response", assistantMsg.text)
        assertFalse(assistantMsg.isStreaming)
    }

    @Test
    fun newChatClearsMessagesStopsTtsExitsLiveModeAndResetsConversation() = runTest {
        val engine = FakeLlmEngine().apply {
            chunks = listOf("First reply")
        }
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val store = createStore(engine = engine, audioStream = audioStream, synthesizer = synthesizer)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        store.dispatch(ChatIntent.InputChanged("Hi"))
        store.dispatch(ChatIntent.Send)
        advanceUntilIdle()
        assertEquals(2, store.state.value.messages.size)

        store.dispatch(ChatIntent.EnterLiveMode)
        advanceUntilIdle()
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertEquals(1, audioStream.activeCollectors)

        store.dispatch(ChatIntent.NewChat)
        advanceUntilIdle()

        assertTrue(store.state.value.messages.isEmpty())
        assertFalse(store.state.value.isGenerating)
        assertEquals(LivePhase.IDLE, store.state.value.livePhase)
        assertEquals(0, audioStream.activeCollectors)
        assertTrue(synthesizer.stopCount >= 1)
        assertEquals(1, engine.resetCount)
    }

    @Test
    fun newChatUnderAutoListenKeepsListeningPhaseAndEmptyMessages() = runTest {
        val audioStream = FakeAudioStream()
        val micPermission = FakeMicPermission(granted = true)
        val engine = FakeLlmEngine()
        val store = createStore(
            engine = engine,
            micPermission = micPermission,
            audioStream = audioStream,
            autoListen = true
        )
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertEquals(1, audioStream.activeCollectors)

        store.dispatch(ChatIntent.InputChanged("Hi"))
        store.dispatch(ChatIntent.Send)
        advanceUntilIdle()
        assertTrue(store.state.value.messages.isNotEmpty())

        store.dispatch(ChatIntent.NewChat)
        advanceUntilIdle()

        assertTrue(store.state.value.messages.isEmpty())
        assertFalse(store.state.value.isGenerating)
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertEquals(1, audioStream.activeCollectors)
        assertEquals(1, engine.resetCount)
    }

    @Test
    fun generationErrorSetsErrorTextAndDoesNotSpeakTts() = runTest {
        val engine = FakeLlmEngine().apply {
            generateError = IllegalStateException("OOM during decode")
        }
        val synthesizer = FakeSpeechSynthesizer()
        val store = createStore(engine = engine, synthesizer = synthesizer)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        store.dispatch(ChatIntent.InputChanged("Trigger error"))
        store.dispatch(ChatIntent.Send)
        advanceUntilIdle()

        val state = store.state.value
        assertFalse(state.isGenerating)
        val assistantMsg = state.messages.last()
        assertEquals("Error: OOM during decode", assistantMsg.text)
        assertFalse(assistantMsg.isStreaming)
        assertTrue(synthesizer.spokenTexts.isEmpty())
        store.onCleared()
        assertEquals(1, engine.closeCount)
    }

    @Test
    fun downloadFailureTransitionsToErrorWithDefaultSpec() = runTest {
        val engine = FakeLlmEngine()
        val repo = FakeModelRepository(downloaded = false).apply {
            downloadError = IllegalStateException("Network unreachable")
        }
        val store = ChatStore(
            engine = engine,
            repo = repo,
            audioStream = FakeAudioStream(),
            synthesizer = FakeSpeechSynthesizer(),
            micPermission = FakeMicPermission(),
            stt = FakeSpeechToText(),
            networkActivity = NetworkActivity(now = { 0L })
        )

        assertEquals(Models.GEMMA_4_E2B.displayName, store.state.value.modelName)
        store.dispatch(ChatIntent.DownloadModel)
        store.dispatch(ChatIntent.ConfirmDownload)
        advanceUntilIdle()

        assertEquals(ModelStatus.Error("Network unreachable"), store.state.value.modelStatus)
    }

    @Test
    fun typedTurnNeverCallsTheSynthesizer() = runTest {
        val engine = FakeLlmEngine().apply {
            chunks = listOf("First sentence.", "First sentence. Second sentence.")
        }
        val synthesizer = FakeSpeechSynthesizer()
        val store = createStore(engine = engine, synthesizer = synthesizer)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        store.dispatch(ChatIntent.InputChanged("Quiet question"))
        store.dispatch(ChatIntent.Send)
        advanceUntilIdle()

        assertEquals("First sentence. Second sentence.", store.state.value.messages.last().text)
        assertTrue(synthesizer.spokenTexts.isEmpty())
        assertTrue(synthesizer.enqueuedTexts.isEmpty())
        assertEquals(0, synthesizer.warmUpCount)
    }

    @Test
    fun enterLiveModeTransitionsToListeningAndUpdatesLiveLevel() = runTest {
        val audioStream = FakeAudioStream()
        val micPermission = FakeMicPermission(granted = true)
        val store = createStore(audioStream = audioStream, micPermission = micPermission)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        store.dispatch(ChatIntent.EnterLiveMode)
        advanceUntilIdle()

        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertFalse(store.state.value.micDenied)
        assertEquals(1, micPermission.ensureGrantedCount)
        assertEquals(1, audioStream.activeCollectors)

        audioStream.push(audioStream.tone(ms = 20, amplitude = 0.2))
        advanceUntilIdle()
        assertTrue(store.state.value.liveLevel > 0f)
    }

    @Test
    fun toneAndSilenceSendsAudioPromptWithCorrectWavLengthAndTransitionsToThinking() = runTest {
        val config = VoiceConfig(sendAudioToModel = true)
        val engine = FakeLlmEngine().apply {
            chunks = emptyList()
            holdAfterChunks = true
        }
        val audioStream = FakeAudioStream(config)
        val store = createStore(engine = engine, audioStream = audioStream, config = config)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        store.dispatch(ChatIntent.EnterLiveMode)
        advanceUntilIdle()

        // 416ms speech + 704ms silence = 1120ms = 35840 PCM bytes
        audioStream.push(audioStream.tone(ms = 416, amplitude = 0.2))
        audioStream.push(audioStream.silence(ms = 704))
        advanceUntilIdle()

        assertEquals(LivePhase.THINKING, store.state.value.livePhase)
        assertTrue(store.state.value.isGenerating)
        assertEquals(1, engine.prompts.size)
        val audioPrompt = assertIs<LlmPrompt.Audio>(engine.prompts.single())
        assertEquals(VOICE_INSTRUCTION, audioPrompt.instruction)
        assertEquals(WavEncoder.HEADER_SIZE + 1120 * config.bytesPerMs, audioPrompt.wavBytes.size)
        assertEquals("speech", store.state.value.messages.first().text)
        assertTrue(store.state.value.messages.first().isVoice)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun liveGenerationEnqueuesSentencesAsTheyStreamAndReturnsToListeningAfterTtsDrains() = runTest {
        val engine = FakeLlmEngine().apply {
            chunks = listOf(
                "First sentence here.",
                "First sentence here. Second part"
            )
        }
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val store = createStore(
            engine = engine,
            audioStream = audioStream,
            synthesizer = synthesizer
        )
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()
        store.dispatch(ChatIntent.EnterLiveMode)
        advanceUntilIdle()

        audioStream.push(audioStream.tone(ms = 400, amplitude = 0.2))
        audioStream.push(audioStream.silence(ms = 700))
        advanceUntilIdle()

        // Sentences were enqueued and store is in SPEAKING awaiting TTS drain
        assertEquals(LivePhase.SPEAKING, store.state.value.livePhase)
        assertEquals(
            listOf("First sentence here.", "Second part"),
            synthesizer.enqueuedTexts
        )
        assertTrue(synthesizer.spokenTexts.isEmpty())

        // Once TTS finishes speaking, store returns to LISTENING
        synthesizer.finishSpeaking()
        advanceUntilIdle()

        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertEquals(1, audioStream.activeCollectors)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun bargeInDuringSpeakingStopsTtsCancelsEngineAndBuffersSpeechInListening() = runTest {
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Speaking a long sentence.")
            holdAfterChunks = true
        }
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val store = createStore(
            engine = engine,
            audioStream = audioStream,
            synthesizer = synthesizer,
            config = VoiceConfig(sendAudioToModel = true)
        )
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()
        store.dispatch(ChatIntent.EnterLiveMode)
        advanceUntilIdle()

        audioStream.push(audioStream.tone(ms = 400, amplitude = 0.2))
        audioStream.push(audioStream.silence(ms = 700))
        advanceUntilIdle()
        assertEquals(LivePhase.SPEAKING, store.state.value.livePhase)

        val stopsBeforeBargeIn = synthesizer.stopCount
        // Barge in with 256ms of speech
        audioStream.push(audioStream.tone(ms = 256, amplitude = 0.1))
        advanceUntilIdle()

        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertFalse(store.state.value.isGenerating)
        assertFalse(store.state.value.messages.last().isStreaming)
        assertEquals(1, engine.cancelCount)
        assertTrue(synthesizer.stopCount > stopsBeforeBargeIn)

        // Continue speaking 160ms more (total 416ms >= 400ms) + 704ms silence -> triggers 2nd turn
        engine.holdAfterChunks = false
        audioStream.push(audioStream.tone(ms = 160, amplitude = 0.1))
        audioStream.push(audioStream.silence(ms = 704))
        advanceUntilIdle()

        assertEquals(2, engine.prompts.size)
        val secondAudio = assertIs<LlmPrompt.Audio>(engine.prompts[1])
        assertEquals(WavEncoder.HEADER_SIZE + 1120 * VoiceConfig().bytesPerMs, secondAudio.wavBytes.size)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun liveTapInterruptsSpeakingAndForcesEndpointWhenSpeechBuffered() = runTest {
        val engine = FakeLlmEngine().apply {
            chunks = listOf("First answer.")
            holdAfterChunks = true
        }
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val store = createStore(
            engine = engine,
            audioStream = audioStream,
            synthesizer = synthesizer
        )
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()
        store.dispatch(ChatIntent.EnterLiveMode)
        advanceUntilIdle()

        // Tap while LISTENING with speech buffered forces endpoint immediately without waiting for hang
        audioStream.push(audioStream.tone(ms = 400, amplitude = 0.2))
        advanceUntilIdle()
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)

        store.dispatch(ChatIntent.LiveTap)
        advanceUntilIdle()

        assertEquals(1, engine.prompts.size)
        assertEquals(LivePhase.SPEAKING, store.state.value.livePhase)

        // Tap while SPEAKING interrupts and returns to LISTENING with empty endpointer buffer
        store.dispatch(ChatIntent.LiveTap)
        advanceUntilIdle()

        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertFalse(store.state.value.isGenerating)
        assertEquals(1, engine.cancelCount)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun exitLiveModeStopsMicStopsTtsCancelsGenerationAndReturnsToIdle() = runTest {
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Partial speech.")
            holdAfterChunks = true
        }
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val store = createStore(
            engine = engine,
            audioStream = audioStream,
            synthesizer = synthesizer
        )
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()
        store.dispatch(ChatIntent.EnterLiveMode)
        advanceUntilIdle()

        audioStream.push(audioStream.tone(ms = 400, amplitude = 0.2))
        audioStream.push(audioStream.silence(ms = 700))
        advanceUntilIdle()
        assertEquals(LivePhase.SPEAKING, store.state.value.livePhase)

        val stopsBeforeExit = synthesizer.stopCount
        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()

        assertEquals(LivePhase.IDLE, store.state.value.livePhase)
        assertFalse(store.state.value.isGenerating)
        assertEquals(0, audioStream.activeCollectors)
        assertEquals(1, engine.cancelCount)
        assertTrue(synthesizer.stopCount > stopsBeforeExit)
    }

    @Test
    fun enterLiveModePermissionDeniedAndIgnoredWhenNotReady() = runTest {
        val repo = FakeModelRepository(downloaded = false)
        val audioStream = FakeAudioStream()
        val micPermission = FakeMicPermission(granted = false)
        val store = createStore(
            repo = repo,
            audioStream = audioStream,
            micPermission = micPermission
        )

        // Ignored when model not Ready
        store.dispatch(ChatIntent.EnterLiveMode)
        advanceUntilIdle()
        assertEquals(LivePhase.IDLE, store.state.value.livePhase)
        assertEquals(0, micPermission.ensureGrantedCount)

        // Ready, but permission denied -> sets micDenied and stays IDLE
        repo.downloaded = true
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        store.dispatch(ChatIntent.EnterLiveMode)
        advanceUntilIdle()
        assertTrue(store.state.value.micDenied)
        assertEquals(LivePhase.IDLE, store.state.value.livePhase)
        assertEquals(0, audioStream.activeCollectors)
    }

    @Test
    fun liveGenerationErrorShowsErrorInBubbleAndReturnsToListening() = runTest {
        val engine = FakeLlmEngine().apply {
            generateError = IllegalStateException("Decode failed")
        }
        val audioStream = FakeAudioStream()
        val store = createStore(engine = engine, audioStream = audioStream)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()
        store.dispatch(ChatIntent.EnterLiveMode)
        advanceUntilIdle()

        audioStream.push(audioStream.tone(ms = 400, amplitude = 0.2))
        audioStream.push(audioStream.silence(ms = 700))
        advanceUntilIdle()

        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertFalse(store.state.value.isGenerating)
        assertEquals("Error: Decode failed", store.state.value.messages.last().text)

        store.onCleared()
        advanceUntilIdle()
        assertEquals(0, audioStream.activeCollectors)
        assertEquals(1, engine.closeCount)
    }

    @Test
    fun networkActiveFlipsTrueWhileRequestIsOpenAndFalseAfter() = runTest {
        val networkActivity = NetworkActivity(now = { 0L })
        val store = createStore(networkActivity = networkActivity)
        advanceUntilIdle()

        assertFalse(store.state.value.networkActive)

        val request = networkActivity.begin("example.com", "download")
        advanceUntilIdle()
        assertTrue(store.state.value.networkActive)

        request.finish()
        advanceUntilIdle()
        assertFalse(store.state.value.networkActive)
    }

    @Test
    fun offlineVoiceUnavailableKeepsTextReplyAndSkipsSpeech() = runTest {
        val engine = FakeLlmEngine().apply {
            chunks = listOf("Text answer")
        }
        val synthesizer = FakeSpeechSynthesizer().apply {
            setOfflineVoiceAvailable(false)
        }
        val store = createStore(engine = engine, synthesizer = synthesizer)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        assertTrue(store.state.value.offlineVoiceMissing)

        store.dispatch(ChatIntent.InputChanged("Question"))
        store.dispatch(ChatIntent.Send)
        advanceUntilIdle()

        val state = store.state.value
        assertTrue(state.offlineVoiceMissing)
        assertEquals(2, state.messages.size)
        assertEquals("Text answer", state.messages[1].text)
        assertTrue(synthesizer.spokenTexts.isEmpty())
        assertTrue(synthesizer.enqueuedTexts.isEmpty())
    }
}
