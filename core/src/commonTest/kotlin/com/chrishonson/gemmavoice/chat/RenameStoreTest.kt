package com.chrishonson.gemmavoice.chat

import com.chrishonson.gemmavoice.llm.LlmPrompt
import com.chrishonson.gemmavoice.model.ModelSpec
import com.chrishonson.gemmavoice.net.NetworkActivity
import com.chrishonson.gemmavoice.voice.FakeVad
import com.chrishonson.gemmavoice.voice.InMemorySettingsStore
import com.chrishonson.gemmavoice.voice.VoiceConfig
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RenameStoreTest {
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
        synthesizer: FakeSpeechSynthesizer = FakeSpeechSynthesizer(),
        stt: FakeSpeechToText = FakeSpeechToText(),
        settings: InMemorySettingsStore = InMemorySettingsStore(),
        config: VoiceConfig = VoiceConfig()
    ): ChatStore = ChatStore(
        engine = engine,
        repo = FakeModelRepository(downloaded = true),
        audioStream = audioStream,
        synthesizer = synthesizer,
        micPermission = FakeMicPermission(granted = true),
        stt = stt,
        config = config,
        vad = FakeVad(config),
        models = listOf(testSpec),
        settings = settings,
        networkActivity = NetworkActivity(now = { 0L })
    )

    private fun ChatStore.rename(text: String) {
        dispatch(ChatIntent.OpenRename)
        dispatch(ChatIntent.RenameInputChanged(text))
        dispatch(ChatIntent.SaveRename)
    }

    @Test
    fun openRenamePrefillsTheCurrentName() {
        val store = createStore()

        store.dispatch(ChatIntent.OpenRename)

        assertEquals("Computer", store.state.value.renameDraft)
        assertNull(store.state.value.renameError)
    }

    @Test
    fun savingAValidNameRenamesPersistsAndStartsANewChat() = runTest {
        val engine = FakeLlmEngine().apply { chunks = listOf("Hi.") }
        val settings = InMemorySettingsStore()
        val store = createStore(engine = engine, settings = settings)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()
        store.dispatch(ChatIntent.InputChanged("hello"))
        store.dispatch(ChatIntent.Send)
        advanceUntilIdle()
        assertTrue(store.state.value.messages.isNotEmpty())

        store.rename("  jarvis ")
        advanceUntilIdle()

        val state = store.state.value
        assertEquals("Jarvis", state.assistantName)
        assertNull(state.renameDraft)
        assertNull(state.renameError)
        assertEquals("Jarvis", settings.loadAssistantName())
        assertEquals(listOf("Jarvis"), engine.assistantNames)
        assertTrue(state.messages.isEmpty())
        assertEquals(1, engine.resetCount)
    }

    @Test
    fun savingAnInvalidNameDoesNothingAndKeepsTheDialogOpen() = runTest {
        val engine = FakeLlmEngine()
        val settings = InMemorySettingsStore()
        val store = createStore(engine = engine, settings = settings)

        store.rename("R2D2")
        advanceUntilIdle()

        val state = store.state.value
        assertEquals("Computer", state.assistantName)
        assertEquals("R2D2", state.renameDraft)
        assertEquals(RENAME_ERROR_TEXT, state.renameError)
        assertNull(settings.loadAssistantName())
        assertTrue(engine.assistantNames.isEmpty())
        assertEquals(0, engine.resetCount)
    }

    @Test
    fun cancelClosesTheDialogWithoutChanges() = runTest {
        val engine = FakeLlmEngine()
        val settings = InMemorySettingsStore()
        val store = createStore(engine = engine, settings = settings)
        store.dispatch(ChatIntent.OpenRename)
        store.dispatch(ChatIntent.RenameInputChanged("x"))

        store.dispatch(ChatIntent.CancelRename)
        advanceUntilIdle()

        val state = store.state.value
        assertEquals("Computer", state.assistantName)
        assertNull(state.renameDraft)
        assertNull(state.renameError)
        assertNull(settings.loadAssistantName())
        assertEquals(0, engine.resetCount)
    }

    @Test
    fun wakeWordFollowsTheRenameAndLiveModeKeepsRunning() = runTest {
        val engine = FakeLlmEngine().apply { chunks = listOf("It is noon.") }
        val audioStream = FakeAudioStream()
        val synthesizer = FakeSpeechSynthesizer()
        val stt = FakeSpeechToText(listOf("Jarvis what time is it"))
        val store = createStore(engine = engine, audioStream = audioStream, synthesizer = synthesizer, stt = stt)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()
        store.dispatch(ChatIntent.EnterLiveMode)
        advanceUntilIdle()
        store.dispatch(ChatIntent.ToggleWakeWord)

        store.rename("Jarvis")
        advanceUntilIdle()
        assertEquals(LivePhase.LISTENING, store.state.value.livePhase)
        assertEquals(1, audioStream.activeCollectors)

        audioStream.push(audioStream.tone(ms = 400, amplitude = 0.2))
        audioStream.push(audioStream.silence(ms = 700))
        advanceUntilIdle()
        assertEquals("Jarvis what time is it", assertIs<LlmPrompt.Text>(engine.prompts.single()).text)

        synthesizer.finishSpeaking()
        audioStream.push(audioStream.silence(ms = VoiceConfig().followUpMs + 500))
        stt.script("Computer what time is it")
        audioStream.push(audioStream.tone(ms = 400, amplitude = 0.2))
        audioStream.push(audioStream.silence(ms = 700))
        advanceUntilIdle()

        assertEquals(1, engine.prompts.size)
        val last = store.state.value.messages.last()
        assertEquals(Role.OVERHEARD, last.role)
        assertEquals("Computer what time is it", last.text)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }

    @Test
    fun startupUsesASavedValidName() {
        val store = createStore(settings = InMemorySettingsStore("Jarvis"))

        assertEquals("Jarvis", store.state.value.assistantName)
    }

    @Test
    fun startupFallsBackToConfigWhenTheSavedNameIsInvalid() {
        val store = createStore(
            settings = InMemorySettingsStore("R2D2"),
            config = VoiceConfig(assistantName = "Orion")
        )

        assertEquals("Orion", store.state.value.assistantName)
    }

    @Test
    fun savedNameIsTheStartupWakeWord() = runTest {
        val engine = FakeLlmEngine()
        val audioStream = FakeAudioStream()
        val stt = FakeSpeechToText(listOf("Jarvis tell me a story"))
        val store = createStore(
            engine = engine,
            audioStream = audioStream,
            stt = stt,
            settings = InMemorySettingsStore("Jarvis")
        )
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()
        store.dispatch(ChatIntent.EnterLiveMode)
        advanceUntilIdle()
        store.dispatch(ChatIntent.ToggleWakeWord)

        audioStream.push(audioStream.tone(ms = 400, amplitude = 0.2))
        audioStream.push(audioStream.silence(ms = 700))
        advanceUntilIdle()

        assertEquals("Jarvis tell me a story", assertIs<LlmPrompt.Text>(engine.prompts.single()).text)

        store.dispatch(ChatIntent.ExitLiveMode)
        advanceUntilIdle()
    }
}
