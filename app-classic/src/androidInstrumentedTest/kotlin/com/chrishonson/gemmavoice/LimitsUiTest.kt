package com.chrishonson.gemmavoice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chrishonson.gemmavoice.devicecheck.InMemoryDeviceCheckCompletionStore
import com.chrishonson.gemmavoice.limits.InMemoryAcknowledgementStore
import com.chrishonson.gemmavoice.llm.LlmEngine
import com.chrishonson.gemmavoice.llm.LlmPrompt
import com.chrishonson.gemmavoice.model.ModelRepository
import com.chrishonson.gemmavoice.model.ModelSpec
import com.chrishonson.gemmavoice.voice.AudioStream
import com.chrishonson.gemmavoice.voice.InMemorySettingsStore
import com.chrishonson.gemmavoice.voice.MicPermission
import com.chrishonson.gemmavoice.voice.SpeechSynthesizer
import com.chrishonson.gemmavoice.voice.SpeechToText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LimitsUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val appConfig = AppConfig(clonedVoice = false, renameAssistant = true)

    @Test
    fun limitsButtonOpensSheetWhenAcknowledged() {
        val ackStore = InMemoryAcknowledgementStore(acknowledged = true)

        composeTestRule.setContent {
            App(
                engine = FakeLlmEngine(),
                repo = FakeModelRepository(),
                audioStream = FakeAudioStream(),
                synthesizer = FakeSpeechSynthesizer(),
                micPermission = FakeMicPermission(),
                stt = FakeSpeechToText(),
                settings = InMemorySettingsStore(),
                acknowledgementStore = ackStore,
                appConfig = appConfig
            )
        }

        // Chat screen has the Limits button
        composeTestRule.onNodeWithTag("limits-button").assertIsDisplayed()

        // Tapping it opens the sheet showing the final line
        composeTestRule.onNodeWithTag("limits-button").performClick()
        composeTestRule.onNodeWithText("Never guaranteed. Verify with a professional.")
            .performScrollTo()
            .assertIsDisplayed()

        // Close the sheet
        composeTestRule.onNodeWithTag("limits-close-button").performClick()
        composeTestRule.onNodeWithTag("limits-sheet").assertDoesNotExist()
    }

    @Test
    fun limitsSheetCanOpenLicensesSheetAndClose() {
        val ackStore = InMemoryAcknowledgementStore(acknowledged = true)

        composeTestRule.setContent {
            App(
                engine = FakeLlmEngine(),
                repo = FakeModelRepository(),
                audioStream = FakeAudioStream(),
                synthesizer = FakeSpeechSynthesizer(),
                micPermission = FakeMicPermission(),
                stt = FakeSpeechToText(),
                settings = InMemorySettingsStore(),
                acknowledgementStore = ackStore,
                appConfig = appConfig
            )
        }

        // Open Limits sheet
        composeTestRule.onNodeWithTag("limits-button").performClick()
        composeTestRule.onNodeWithTag("limits-sheet").assertIsDisplayed()

        // Open Licenses sheet
        composeTestRule.onNodeWithTag("limits-licenses-button").performClick()
        composeTestRule.onNodeWithTag("licenses-sheet").assertIsDisplayed()

        // Close Licenses sheet returns to Limits sheet
        composeTestRule.onNodeWithTag("licenses-close-button").performClick()
        composeTestRule.onNodeWithTag("licenses-sheet").assertDoesNotExist()
        composeTestRule.onNodeWithTag("limits-sheet").assertIsDisplayed()
    }

    @Test
    fun unacknowledgedStoreShowsSheetOnLaunchAndBlocksUntilUnderstood() {
        val ackStore = InMemoryAcknowledgementStore(acknowledged = false)

        composeTestRule.setContent {
            App(
                engine = FakeLlmEngine(),
                repo = FakeModelRepository(),
                audioStream = FakeAudioStream(),
                synthesizer = FakeSpeechSynthesizer(),
                micPermission = FakeMicPermission(),
                stt = FakeSpeechToText(),
                settings = InMemorySettingsStore(),
                acknowledgementStore = ackStore,
                deviceCheckCompletionStore = InMemoryDeviceCheckCompletionStore(completed = true),
                appConfig = appConfig
            )
        }

        // Sheet is shown on launch
        composeTestRule.onNodeWithTag("limits-sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Never guaranteed. Verify with a professional.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("limits-acknowledge-button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("limits-close-button").assertDoesNotExist()

        // Acknowledge by tapping "I understand"
        composeTestRule.onNodeWithTag("limits-acknowledge-button").performClick()

        // Sheet closes and chat screen is accessible
        composeTestRule.onNodeWithTag("limits-sheet").assertDoesNotExist()
        composeTestRule.onNodeWithTag("limits-button").assertIsDisplayed()
    }

    internal class FakeLlmEngine : LlmEngine {
        override suspend fun load(modelPath: String) {}
        override fun generate(prompt: LlmPrompt): Flow<String> = emptyFlow()
        override fun cancel() {}
        override suspend fun resetConversation() {}
        override fun setAssistantName(name: String) {}
        override fun close() {}
    }

    internal class FakeModelRepository : ModelRepository {
        override fun modelPath(spec: ModelSpec): String = "/fake/model"
        override fun isDownloaded(spec: ModelSpec): Boolean = true
        override fun download(spec: ModelSpec): Flow<Long> = flowOf(100L)
    }

    internal class FakeAudioStream : AudioStream {
        override fun frames(): Flow<ByteArray> = emptyFlow()
    }

    internal class FakeSpeechSynthesizer : SpeechSynthesizer {
        override val isSpeaking: StateFlow<Boolean> = MutableStateFlow(false)
        override val offlineVoiceAvailable: StateFlow<Boolean> = MutableStateFlow(true)
        override fun speak(text: String) {}
        override fun enqueue(text: String) {}
        override fun stop() {}
    }

    internal class FakeMicPermission : MicPermission {
        override suspend fun ensureGranted(): Boolean = true
    }

    internal class FakeSpeechToText : SpeechToText {
        override suspend fun transcribe(pcm16: ByteArray, sampleRate: Int): String = ""
    }
}
