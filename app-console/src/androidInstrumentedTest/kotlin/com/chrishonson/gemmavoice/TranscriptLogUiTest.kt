package com.chrishonson.gemmavoice

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chrishonson.gemmavoice.chat.ChatMessage
import com.chrishonson.gemmavoice.chat.MessageList
import com.chrishonson.gemmavoice.chat.Role
import com.chrishonson.gemmavoice.chat.TranscriptEntry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranscriptLogUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun userEntryShowsYouAndTextWithoutVoiceIcon() {
        composeTestRule.setContent {
            GemmaVoiceTheme {
                TranscriptEntry(
                    role = Role.USER,
                    text = "Computer, status report."
                )
            }
        }

        composeTestRule.onNodeWithTag("logEntry-user").assertIsDisplayed()
        composeTestRule.onNodeWithText("YOU").assertIsDisplayed()
        composeTestRule.onNodeWithText("Computer, status report.").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Voice message").assertDoesNotExist()
    }

    @Test
    fun assistantEntryShowsComputer() {
        composeTestRule.setContent {
            GemmaVoiceTheme {
                TranscriptEntry(
                    role = Role.ASSISTANT,
                    text = "All systems functioning normally."
                )
            }
        }

        composeTestRule.onNodeWithTag("logEntry-assistant").assertIsDisplayed()
        composeTestRule.onNodeWithText("COMPUTER").assertIsDisplayed()
        composeTestRule.onNodeWithText("All systems functioning normally.").assertIsDisplayed()
        composeTestRule.onNodeWithText("YOU").assertDoesNotExist()
    }

    @Test
    fun overheardShowsTextWithoutYouOrComputerTag() {
        composeTestRule.setContent {
            GemmaVoiceTheme {
                TranscriptEntry(
                    role = Role.OVERHEARD,
                    text = "the furnace filter looked dirty"
                )
            }
        }

        composeTestRule.onNodeWithTag("logEntry-overheard").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Heard: the furnace filter looked dirty").assertIsDisplayed()
        composeTestRule.onNodeWithText("YOU").assertDoesNotExist()
        composeTestRule.onNodeWithText("COMPUTER").assertDoesNotExist()
    }

    @Test
    fun messageListWithLongListEndsWithLastMessageDisplayed() {
        val count = 25
        val messages = (1..count).map { index ->
            ChatMessage(
                id = index.toLong(),
                role = if (index % 2 == 1) Role.USER else Role.ASSISTANT,
                text = "Message $index in a long scrollable transcript."
            )
        }

        composeTestRule.setContent {
            GemmaVoiceTheme {
                MessageList(messages = messages)
            }
        }

        composeTestRule.onNodeWithText("Message $count in a long scrollable transcript.").assertIsDisplayed()
    }
}
