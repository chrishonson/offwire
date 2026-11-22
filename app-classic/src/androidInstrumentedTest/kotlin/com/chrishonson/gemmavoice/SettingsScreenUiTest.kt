package com.chrishonson.gemmavoice

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chrishonson.gemmavoice.chat.ChatIntent
import com.chrishonson.gemmavoice.chat.ChatState
import com.chrishonson.gemmavoice.chat.ModelStatus
import com.chrishonson.gemmavoice.model.Models
import com.chrishonson.gemmavoice.settings.SettingsScreen
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenUiTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun resourceSwitchSendsItsAction() {
        val intents = mutableListOf<ChatIntent>()
        composeTestRule.setContent {
            GemmaVoiceTheme {
                SettingsScreen(
                    state = ChatState(showResourceMeter = false),
                    onIntent = intents::add,
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Resource meter").assertIsDisplayed()
        composeTestRule.onNodeWithTag("resourceMeterSwitch").performClick()
        assertEquals(listOf<ChatIntent>(ChatIntent.ToggleResourceMeter), intents)
    }

    @Test
    fun backArrowIsAnnouncedAsBackToChatAndGoesBack() {
        var backed = false
        composeTestRule.setContent {
            GemmaVoiceTheme {
                SettingsScreen(state = ChatState(), onIntent = {}, onBack = { backed = true })
            }
        }

        composeTestRule.onNodeWithText("Back to chat").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Back to chat").assertIsDisplayed().performClick()
        assertEquals(true, backed)
    }

    @Test
    fun choosingTheOtherModelSelectsIt() {
        val intents = mutableListOf<ChatIntent>()
        composeTestRule.setContent {
            GemmaVoiceTheme {
                SettingsScreen(state = ChatState(), onIntent = intents::add, onBack = {})
            }
        }

        composeTestRule.onNodeWithText("Gemma 4 E2B").assertIsDisplayed()
        composeTestRule.onNodeWithText("3.7 GB download").assertIsDisplayed()
        composeTestRule.onNodeWithTag("model-${Models.GEMMA_4_E4B.id}").performClick()
        assertEquals(listOf<ChatIntent>(ChatIntent.SelectModel(Models.GEMMA_4_E4B.id)), intents)
    }

    @Test
    fun onlyTheModelsThisDeviceCanRunAreOffered() {
        composeTestRule.setContent {
            GemmaVoiceTheme {
                SettingsScreen(
                    state = ChatState(models = listOf(Models.GEMMA_4_E2B)),
                    onIntent = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("model-${Models.GEMMA_4_E2B.id}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("model-${Models.GEMMA_4_E4B.id}").assertDoesNotExist()
    }

    @Test
    fun modelChoiceIsDisabledWhileDownloading() {
        composeTestRule.setContent {
            GemmaVoiceTheme {
                SettingsScreen(
                    state = ChatState(modelStatus = ModelStatus.Downloading(0.5f)),
                    onIntent = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("model-${Models.GEMMA_4_E4B.id}").assertIsNotEnabled()
    }

    @Test
    fun benchmarkIsHiddenWithoutAHost() {
        composeTestRule.setContent {
            GemmaVoiceTheme {
                SettingsScreen(state = ChatState(modelStatus = ModelStatus.Ready), onIntent = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag("openBenchmark").assertDoesNotExist()
    }

    @Test
    fun benchmarkOpensOnceTheModelIsReady() {
        var opened = false
        composeTestRule.setContent {
            GemmaVoiceTheme {
                SettingsScreen(
                    state = ChatState(modelStatus = ModelStatus.Ready),
                    onIntent = {},
                    onBack = {},
                    onOpenBenchmark = { opened = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("openBenchmark").performClick()
        assertEquals(true, opened)
    }

    @Test
    fun benchmarkWaitsForTheModel() {
        composeTestRule.setContent {
            GemmaVoiceTheme {
                SettingsScreen(
                    state = ChatState(modelStatus = ModelStatus.Missing),
                    onIntent = {},
                    onBack = {},
                    onOpenBenchmark = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("openBenchmark").assertIsNotEnabled()
    }

    @Test
    fun showsTheAppVersion() {
        composeTestRule.setContent {
            GemmaVoiceTheme {
                SettingsScreen(state = ChatState(), onIntent = {}, onBack = {}, appVersion = "9.8.7")
            }
        }

        composeTestRule.onNodeWithTag("appVersion").assertIsDisplayed().assertTextEquals("Version 9.8.7")
    }

    // In landscape the rows are taller than the screen, so the list has to scroll to reach the last one.
    @Test
    fun aShortScreenScrollsToTheVersionLine() {
        composeTestRule.setContent {
            GemmaVoiceTheme {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    SettingsScreen(state = ChatState(), onIntent = {}, onBack = {}, appVersion = "0.4.0")
                }
            }
        }

        composeTestRule.onNodeWithTag("appVersion").performScrollTo().assertIsDisplayed()
    }
}
