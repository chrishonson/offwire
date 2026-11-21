package com.chrishonson.gemmavoice

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chrishonson.gemmavoice.chat.ConsolePillButton
import com.chrishonson.gemmavoice.chat.LivePhase
import com.chrishonson.gemmavoice.chat.ModelStatus
import com.chrishonson.gemmavoice.chat.consolePill
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ConsolePillUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun listeningShowsExactlyOneMutePill() {
        showPill(LivePhase.LISTENING, micMuted = false)

        assertOnePill("MUTE")
    }

    @Test
    fun mutedListeningShowsExactlyOneUnmutePill() {
        showPill(LivePhase.LISTENING, micMuted = true)

        assertOnePill("UNMUTE")
    }

    @Test
    fun thinkingShowsOneStopPillAndClickInterrupts() {
        var interrupted = false
        showPill(LivePhase.THINKING, micMuted = true) { interrupted = true }

        assertOnePill("STOP")
        composeTestRule.onNodeWithTag("consolePill").performClick()
        assertTrue(interrupted)
    }

    @Test
    fun speakingShowsExactlyOneStopPill() {
        showPill(LivePhase.SPEAKING, micMuted = false)

        assertOnePill("STOP")
    }

    private fun showPill(
        phase: LivePhase,
        micMuted: Boolean,
        onLiveTap: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            GemmaVoiceTheme {
                ConsolePillButton(
                    pill = consolePill(phase, micMuted, ModelStatus.Ready, autoListen = true),
                    onLiveTap = onLiveTap
                )
            }
        }
    }

    private fun assertOnePill(label: String) {
        composeTestRule.onAllNodesWithTag("consolePill").assertCountEquals(1)
        composeTestRule.onNodeWithTag("consolePill").assertTextEquals(label)
    }
}
