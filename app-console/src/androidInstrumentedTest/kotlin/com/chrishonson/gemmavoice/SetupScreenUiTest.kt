package com.chrishonson.gemmavoice

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chrishonson.gemmavoice.chat.ChatIntent
import com.chrishonson.gemmavoice.chat.ChatState
import com.chrishonson.gemmavoice.chat.ModelStatus
import com.chrishonson.gemmavoice.model.Models
import com.chrishonson.gemmavoice.setup.SetupScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SetupScreenUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Suppress("LongMethod")
    fun allRowsDisplayedAndClickingDispatchesIntents() {
        val intents = mutableListOf<ChatIntent>()
        val state = ChatState(
            modelName = "Gemma 4 E2B",
            modelStatus = ModelStatus.Ready,
            assistantName = "Computer",
            wakeWordEnabled = true,
            autoListen = true,
            micDenied = false
        )

        composeTestRule.setContent {
            GemmaVoiceTheme {
                SetupScreen(
                    state = state,
                    onIntent = { intents.add(it) },
                    onBack = {}
                )
            }
        }

        // Assert all rows are displayed
        composeTestRule.onNodeWithText("WAKE WORD").assertIsDisplayed()
        composeTestRule.onNodeWithText("Only answer after \"Computer\"").assertIsDisplayed()
        composeTestRule.onNodeWithText("LISTEN AT START").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open the console already listening").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gemma 4 E2B").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("MICROPHONE").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("micStatus").performScrollTo().assertIsDisplayed().assertTextEquals("Allowed")
        composeTestRule.onNodeWithText("RESOURCE METER").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("clearLogPill")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals("CLEAR LOG")
        composeTestRule.onNodeWithText("HANDS-FREE").assertDoesNotExist()

        // Clicking each switch/pill dispatches the right intent
        composeTestRule.onNodeWithTag("wakeWordSwitch").performScrollTo().performClick()
        assertEquals(listOf<ChatIntent>(ChatIntent.ToggleWakeWord), intents)

        composeTestRule.onNodeWithTag("listenAtStartSwitch").performScrollTo().performClick()
        assertEquals(listOf<ChatIntent>(ChatIntent.ToggleWakeWord, ChatIntent.ToggleAutoListen), intents)

        composeTestRule.onNodeWithTag("resourceMeterSwitch").performScrollTo().performClick()
        assertEquals(
            listOf<ChatIntent>(
                ChatIntent.ToggleWakeWord,
                ChatIntent.ToggleAutoListen,
                ChatIntent.ToggleResourceMeter
            ),
            intents
        )

        composeTestRule.onNodeWithTag("clearLogPill").performScrollTo().performClick()
        assertEquals(
            listOf<ChatIntent>(
                ChatIntent.ToggleWakeWord,
                ChatIntent.ToggleAutoListen,
                ChatIntent.ToggleResourceMeter,
                ChatIntent.NewChat
            ),
            intents
        )

        // No node with text "VOICE" or "Voice on" exists
        composeTestRule.onNodeWithText("VOICE").assertDoesNotExist()
        composeTestRule.onNodeWithText("Voice on").assertDoesNotExist()
        composeTestRule.onNodeWithText("Voice off").assertDoesNotExist()
    }

    @Test
    fun micDeniedTrueShowsBlocked() {
        val state = ChatState(
            modelName = "Gemma 4 E2B",
            modelStatus = ModelStatus.Ready,
            micDenied = true
        )

        composeTestRule.setContent {
            GemmaVoiceTheme {
                SetupScreen(
                    state = state,
                    onIntent = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("micStatus").performScrollTo().assertIsDisplayed().assertTextEquals("Blocked")
    }

    @Test
    fun modelMissingShowsDownloadButtonAndDispatchesDownloadModel() {
        val intents = mutableListOf<ChatIntent>()
        val state = ChatState(
            modelName = "Gemma 4 E2B",
            modelStatus = ModelStatus.Missing
        )

        composeTestRule.setContent {
            GemmaVoiceTheme {
                SetupScreen(
                    state = state,
                    onIntent = { intents.add(it) },
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Download").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf<ChatIntent>(ChatIntent.DownloadModel), intents)
    }

    @Test
    fun choosingTheOtherModelSelectsIt() {
        val intents = mutableListOf<ChatIntent>()
        composeTestRule.setContent {
            GemmaVoiceTheme {
                SetupScreen(
                    state = ChatState(modelStatus = ModelStatus.Ready),
                    onIntent = { intents.add(it) },
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("GEMMA 4 E4B").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("model-${Models.GEMMA_4_E4B.id}").performScrollTo().performClick()
        assertEquals(listOf<ChatIntent>(ChatIntent.SelectModel(Models.GEMMA_4_E4B.id)), intents)
    }

    @Test
    fun onlyTheModelsThisDeviceCanRunAreOffered() {
        composeTestRule.setContent {
            GemmaVoiceTheme {
                SetupScreen(
                    state = ChatState(models = listOf(Models.GEMMA_4_E2B), modelStatus = ModelStatus.Ready),
                    onIntent = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("model-${Models.GEMMA_4_E2B.id}").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("model-${Models.GEMMA_4_E4B.id}").assertDoesNotExist()
    }

    @Test
    fun showsTheAppVersion() {
        composeTestRule.setContent {
            GemmaVoiceTheme {
                SetupScreen(state = ChatState(), onIntent = {}, onBack = {}, appVersion = "9.8.7")
            }
        }

        composeTestRule.onNodeWithTag("appVersion")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals("VERSION 9.8.7")
    }
}
