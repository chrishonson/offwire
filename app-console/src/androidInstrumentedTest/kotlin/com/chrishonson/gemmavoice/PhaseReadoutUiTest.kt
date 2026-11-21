package com.chrishonson.gemmavoice

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chrishonson.gemmavoice.chat.LevelBlockMode
import com.chrishonson.gemmavoice.chat.PhaseReadout
import com.chrishonson.gemmavoice.chat.PhaseReadoutState
import com.chrishonson.gemmavoice.chat.RailColor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PhaseReadoutUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun listeningReadoutShowsWordHintAndContentDescriptionWithoutOrb() {
        showReadout(
            PhaseReadoutState(
                word = "LISTENING",
                hint = "Say \"Computer\" to ask",
                railColor = RailColor.ICE,
                blockMode = LevelBlockMode.LEVEL
            ),
            liveLevel = 0.5f
        )

        composeTestRule.onNodeWithTag("phaseReadout").assertIsDisplayed()
        composeTestRule.onNodeWithTag("phaseReadout")
            .assertContentDescriptionEquals("Listening. Say Computer to ask.")
        composeTestRule.onNodeWithText("LISTENING").assertIsDisplayed()
        composeTestRule.onNodeWithText("Say \"Computer\" to ask").assertIsDisplayed()
        composeTestRule.onNodeWithTag("orb").assertDoesNotExist()
    }

    @Test
    fun workingReadoutShowsWorkingWordAndHintWithoutOrb() {
        showReadout(
            PhaseReadoutState(
                word = "WORKING",
                hint = "Tap anywhere to interrupt",
                railColor = RailColor.ORANGE_PULSE,
                blockMode = LevelBlockMode.UNLIT
            ),
            liveLevel = 0f
        )

        composeTestRule.onNodeWithTag("phaseReadout").assertIsDisplayed()
        composeTestRule.onNodeWithTag("phaseReadout")
            .assertContentDescriptionEquals("Working. Tap anywhere to interrupt.")
        composeTestRule.onNodeWithText("WORKING").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap anywhere to interrupt").assertIsDisplayed()
        composeTestRule.onNodeWithTag("orb").assertDoesNotExist()
    }

    @Test
    fun speakingReadoutShowsSpeakingWordAndHintWithoutOrb() {
        showReadout(
            PhaseReadoutState(
                word = "SPEAKING",
                hint = "Start talking to interrupt",
                railColor = RailColor.SUNFLOWER,
                blockMode = LevelBlockMode.LEVEL
            ),
            liveLevel = 0f
        )

        composeTestRule.onNodeWithTag("phaseReadout").assertIsDisplayed()
        composeTestRule.onNodeWithTag("phaseReadout")
            .assertContentDescriptionEquals("Speaking. Start talking to interrupt.")
        composeTestRule.onNodeWithText("SPEAKING").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start talking to interrupt").assertIsDisplayed()
        composeTestRule.onNodeWithTag("orb").assertDoesNotExist()
    }

    @Test
    fun micBlockedReadoutShowsBlockedWordAndHintWithoutOrb() {
        showReadout(
            PhaseReadoutState(
                word = "MIC BLOCKED",
                hint = "Allow the microphone in Settings",
                railColor = RailColor.TOMATO,
                blockMode = LevelBlockMode.UNLIT
            ),
            liveLevel = 0f
        )

        composeTestRule.onNodeWithTag("phaseReadout").assertIsDisplayed()
        composeTestRule.onNodeWithTag("phaseReadout")
            .assertContentDescriptionEquals("Mic blocked. Allow the microphone in Settings.")
        composeTestRule.onNodeWithText("MIC BLOCKED").assertIsDisplayed()
        composeTestRule.onNodeWithText("Allow the microphone in Settings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("orb").assertDoesNotExist()
    }

    @Test
    fun standbyReadoutShowsStandbyWordAndHintWithoutOrb() {
        showReadout(
            PhaseReadoutState(
                word = "STANDBY",
                hint = "Not downloaded yet",
                railColor = RailColor.FRAME,
                blockMode = LevelBlockMode.UNLIT
            ),
            liveLevel = 0f
        )

        composeTestRule.onNodeWithTag("phaseReadout").assertIsDisplayed()
        composeTestRule.onNodeWithTag("phaseReadout")
            .assertContentDescriptionEquals("Standby. Not downloaded yet.")
        composeTestRule.onNodeWithText("STANDBY").assertIsDisplayed()
        composeTestRule.onNodeWithText("Not downloaded yet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("orb").assertDoesNotExist()
    }

    @Test
    fun tappingReadoutInvokesCallback() {
        var tapped = false
        showReadout(
            PhaseReadoutState(
                word = "WORKING",
                hint = "Tap anywhere to interrupt",
                railColor = RailColor.ORANGE_PULSE,
                blockMode = LevelBlockMode.UNLIT
            ),
            liveLevel = 0f,
            onTap = { tapped = true }
        )

        composeTestRule.onNodeWithTag("phaseReadout").performClick()
        assertTrue(tapped)
    }

    private fun showReadout(
        readout: PhaseReadoutState,
        liveLevel: Float,
        onTap: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            GemmaVoiceTheme {
                PhaseReadout(
                    readout = readout,
                    liveLevel = liveLevel,
                    onTap = onTap
                )
            }
        }
    }
}
