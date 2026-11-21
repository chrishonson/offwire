package com.chrishonson.gemmavoice

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.test.assertTrue

private val WideWidth = 800.dp
private val WideHeight = 360.dp
private val NarrowWidth = 360.dp
private val NarrowHeight = 800.dp
private const val MAX_VERTICAL_CENTER_DELTA_DP = 4f

@RunWith(AndroidJUnit4::class)
class LandscapeUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun lcarsFrameRendersSingleRowInWideAndTwoRowsInNarrow() {
        var isWide by mutableStateOf(true)

        composeTestRule.setContent {
            GemmaVoiceTheme {
                val (width, height) = if (isWide) WideWidth to WideHeight else NarrowWidth to NarrowHeight
                Box(modifier = Modifier.requiredSize(width, height)) {
                    LcarsFrame(
                        title = "COMPUTER",
                        actions = {
                            NavPill(
                                label = "SETUP",
                                color = Color(Lcars.VIOLET),
                                onClick = {}
                            )
                        }
                    ) {
                        // Empty content
                    }
                }
            }
        }

        // In wide (800x360dp), title and nav pill share the same vertical band (vertical centres within 4dp)
        val wideTitleBounds = composeTestRule.onNodeWithText("COMPUTER").getBoundsInRoot()
        val widePillBounds = composeTestRule.onNodeWithText("SETUP").getBoundsInRoot()
        val wideTitleCenterY = (wideTitleBounds.top + wideTitleBounds.bottom) / 2
        val widePillCenterY = (widePillBounds.top + widePillBounds.bottom) / 2
        val wideCenterDiff = abs(wideTitleCenterY.value - widePillCenterY.value)
        assertTrue(
            wideCenterDiff <= MAX_VERTICAL_CENTER_DELTA_DP,
            "Wide title and pill vertical centres should be within 4dp, but difference was $wideCenterDiff dp"
        )
        val wideHeaderBounds = composeTestRule.onNodeWithTag("frameHeader").getBoundsInRoot()
        val wideHeaderHeight = wideHeaderBounds.bottom - wideHeaderBounds.top

        // Switch to narrow (360x800dp)
        composeTestRule.runOnIdle {
            isWide = false
        }

        // In narrow, pill is below the title
        val narrowTitleBounds = composeTestRule.onNodeWithText("COMPUTER").getBoundsInRoot()
        val narrowPillBounds = composeTestRule.onNodeWithText("SETUP").getBoundsInRoot()
        assertTrue(
            narrowPillBounds.top >= narrowTitleBounds.bottom,
            "Narrow pill top (${narrowPillBounds.top}) should be below title bottom (${narrowTitleBounds.bottom})"
        )
        val narrowHeaderBounds = composeTestRule.onNodeWithTag("frameHeader").getBoundsInRoot()
        val narrowHeaderHeight = narrowHeaderBounds.bottom - narrowHeaderBounds.top

        // Assert header height in wide is less than in narrow
        assertTrue(
            wideHeaderHeight < narrowHeaderHeight,
            "Wide header height ($wideHeaderHeight) must be less than narrow header height ($narrowHeaderHeight)"
        )
    }
}
