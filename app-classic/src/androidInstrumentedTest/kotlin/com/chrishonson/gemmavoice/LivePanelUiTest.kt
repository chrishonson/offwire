package com.chrishonson.gemmavoice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

@RunWith(AndroidJUnit4::class)
class LivePanelUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun livePanelShowsWakeWordMuteAndStopWithoutHandsFree() {
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

        composeTestRule.setContent {
            ChatScreen(store = store)
        }

        composeTestRule.onNodeWithText("Talk").performClick()

        composeTestRule
            .onNodeWithText("Only answer when I say \"Computer\"")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Mute").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Stop").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Hands-free mode").assertDoesNotExist()
        composeTestRule.onNodeWithText("Hands-free").assertDoesNotExist()
    }
}
