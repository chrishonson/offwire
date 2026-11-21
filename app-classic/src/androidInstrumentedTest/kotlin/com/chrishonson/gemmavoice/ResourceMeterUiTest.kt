package com.chrishonson.gemmavoice

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Switch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chrishonson.gemmavoice.chat.LivePhase
import com.chrishonson.gemmavoice.chat.ResourceMeter
import com.chrishonson.gemmavoice.resource.ResourceSample
import com.chrishonson.gemmavoice.resource.cpuFraction
import com.chrishonson.gemmavoice.resource.memoryFraction
import com.chrishonson.gemmavoice.resource.resourceBlockCount
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResourceMeterUiTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun enabledThinkingSampleShowsBlockRowsAndSwitchingOffRemovesIt() {
        var show by mutableStateOf(true)
        val phase = LivePhase.THINKING
        val cores = 4
        val sample = ResourceSample(cpuPercent = 63.0, pssMb = 1945.6, availMb = 4096.0, at = 1L)
        val expectedCpuBlocks = resourceBlockCount(cpuFraction(sample.cpuPercent, cores))
        val expectedMemBlocks = resourceBlockCount(memoryFraction(sample.pssMb, sample.availMb))

        composeTestRule.setContent {
            GemmaVoiceTheme {
                Column {
                    Switch(
                        checked = show,
                        onCheckedChange = { show = it },
                        modifier = Modifier.testTag("testResourceSwitch")
                    )
                    ResourceMeter(
                        sample = sample.takeIf { show && phase == LivePhase.THINKING },
                        cores = cores
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("resourceMeter").assertIsDisplayed().assertHeightIsEqualTo(32.dp)
        composeTestRule.onNodeWithTag("resourceRowCpu")
            .assertIsDisplayed()
            .assertHeightIsEqualTo(16.dp)
            .assert(hasStateDescription("$expectedCpuBlocks of 10"))
        composeTestRule.onNodeWithTag("resourceRowMem")
            .assertIsDisplayed()
            .assertHeightIsEqualTo(16.dp)
            .assert(hasStateDescription("$expectedMemBlocks of 10"))
        composeTestRule.onNodeWithText("CPU").assertIsDisplayed()
        composeTestRule.onNodeWithText("MEM").assertIsDisplayed()
        composeTestRule.onNodeWithText("16%").assertIsDisplayed()
        composeTestRule.onNodeWithText("1.9G").assertIsDisplayed()

        composeTestRule.onNodeWithTag("testResourceSwitch").performClick()
        composeTestRule.onNodeWithTag("resourceMeter").assertDoesNotExist()
    }
}
