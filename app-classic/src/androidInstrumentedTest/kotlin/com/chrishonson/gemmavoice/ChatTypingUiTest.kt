package com.chrishonson.gemmavoice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chrishonson.gemmavoice.LimitsUiTest.FakeAudioStream
import com.chrishonson.gemmavoice.LimitsUiTest.FakeLlmEngine
import com.chrishonson.gemmavoice.LimitsUiTest.FakeMicPermission
import com.chrishonson.gemmavoice.LimitsUiTest.FakeModelRepository
import com.chrishonson.gemmavoice.LimitsUiTest.FakeSpeechSynthesizer
import com.chrishonson.gemmavoice.LimitsUiTest.FakeSpeechToText
import com.chrishonson.gemmavoice.chat.ChatIntent
import com.chrishonson.gemmavoice.chat.ChatScreen
import com.chrishonson.gemmavoice.chat.ChatStore
import com.chrishonson.gemmavoice.net.NetworkActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Card 226. On the Pixel 9 Pro the keyboard closed 400 ms after every tap on the message field:
 * the keyboard shrank the content area until it was wider than tall, the chat layout switched to
 * its landscape branch, and the rebuilt text field lost focus, which hides the keyboard. The layout
 * must follow the window, so a shorter content area keeps the field and its focus.
 */
@RunWith(AndroidJUnit4::class)
class ChatTypingUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun fieldKeepsFocusWhenTheKeyboardShrinksTheContentArea() {
        val store = ChatStore(
            engine = FakeLlmEngine(),
            repo = FakeModelRepository(),
            audioStream = FakeAudioStream(),
            synthesizer = FakeSpeechSynthesizer(),
            micPermission = FakeMicPermission(),
            stt = FakeSpeechToText(),
            networkActivity = NetworkActivity(now = { 0L })
        )
        store.dispatch(ChatIntent.Start)
        var contentHeight by mutableStateOf(TALL)

        composeTestRule.setContent {
            Box(modifier = Modifier.width(WIDTH).height(contentHeight)) {
                ChatScreen(store = store)
            }
        }

        composeTestRule.onNodeWithText("Type a message").performClick()
        composeTestRule.onNodeWithText("Type a message").performTextInput("hel")
        composeTestRule.onNodeWithText("hel").assertIsFocused()

        // The keyboard opening: the content area becomes wider than it is tall.
        contentHeight = SHORT
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("hel").assertIsDisplayed().assertIsFocused()

        composeTestRule.onNodeWithText("hel").performTextInput("lo")
        composeTestRule.onNodeWithText("hello").assertIsFocused()
    }

    private companion object {
        val WIDTH = 400.dp
        val TALL = 800.dp
        val SHORT = 300.dp
    }
}
