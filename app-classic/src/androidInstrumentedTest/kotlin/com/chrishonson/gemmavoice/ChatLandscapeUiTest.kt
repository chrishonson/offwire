package com.chrishonson.gemmavoice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntSize
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
 * Card 229. A phone in landscape with the keyboard up leaves about 200 dp of height. The two-row
 * header took most of it and the input row was handed the rest, which squashed the Talk button.
 */
@RunWith(AndroidJUnit4::class)
class ChatLandscapeUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun inputRowKeepsItsHeightInAShortLandscapeWindow() {
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
            CompositionLocalProvider(LocalWindowInfo provides LandscapeWindow) {
                Box(modifier = Modifier.width(WIDTH).height(HEIGHT)) {
                    ChatScreen(store = store)
                }
            }
        }

        composeTestRule.onNodeWithText("Type a message").performClick()

        composeTestRule.onNodeWithText("Talk").assertIsDisplayed().assertHeightIsAtLeast(TALK_MIN_HEIGHT)
        composeTestRule.onNodeWithText("Type a message").assertIsDisplayed().assertHeightIsAtLeast(FIELD_MIN_HEIGHT)
    }

    private object LandscapeWindow : WindowInfo {
        override val isWindowFocused: Boolean = true
        override val containerSize: IntSize = IntSize(width = 2000, height = 450)
    }

    private companion object {
        val WIDTH = 800.dp
        val HEIGHT = 180.dp
        val TALK_MIN_HEIGHT = 72.dp
        val FIELD_MIN_HEIGHT = 64.dp
    }
}
