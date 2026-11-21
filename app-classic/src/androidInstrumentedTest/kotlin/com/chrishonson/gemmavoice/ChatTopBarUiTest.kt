package com.chrishonson.gemmavoice

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chrishonson.gemmavoice.LimitsUiTest.FakeAudioStream
import com.chrishonson.gemmavoice.LimitsUiTest.FakeLlmEngine
import com.chrishonson.gemmavoice.LimitsUiTest.FakeMicPermission
import com.chrishonson.gemmavoice.LimitsUiTest.FakeModelRepository
import com.chrishonson.gemmavoice.LimitsUiTest.FakeSpeechSynthesizer
import com.chrishonson.gemmavoice.LimitsUiTest.FakeSpeechToText
import com.chrishonson.gemmavoice.chat.ChatScreen
import com.chrishonson.gemmavoice.chat.ChatStore
import com.chrishonson.gemmavoice.net.NetworkActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Card 310. The trash key in the top bar tells screen readers it clears the conversation. */
@RunWith(AndroidJUnit4::class)
class ChatTopBarUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun clearKeyDescribesItselfAndKeepsItsLabel() {
        val store = ChatStore(
            engine = FakeLlmEngine(),
            repo = FakeModelRepository(),
            audioStream = FakeAudioStream(),
            synthesizer = FakeSpeechSynthesizer(),
            micPermission = FakeMicPermission(),
            stt = FakeSpeechToText(),
            networkActivity = NetworkActivity(now = { 0L })
        )

        composeTestRule.setContent { ChatScreen(store = store) }

        composeTestRule.onNodeWithContentDescription("Clear conversation")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertTextEquals("New")
    }
}
