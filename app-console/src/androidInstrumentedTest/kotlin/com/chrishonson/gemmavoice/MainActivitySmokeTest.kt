package com.chrishonson.gemmavoice

import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chrishonson.gemmavoice.devicecheck.AndroidDeviceCheckCompletionStore
import com.chrishonson.gemmavoice.limits.AndroidAcknowledgementStore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Test
    fun launchMainActivityReachesResumed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun foregroundHoldsKeepScreenOnAndPauseClearsIt() {
        val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.window.attributes.flags and flag != 0)
            }
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { activity ->
                assertFalse(activity.window.attributes.flags and flag != 0)
            }
        }
    }

    @Test
    fun setupSurvivesActivityRecreate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AndroidDeviceCheckCompletionStore(context).saveDeviceCheckCompleted()
        AndroidAcknowledgementStore(context).saveLimitsAcknowledged()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeTestRule.onNodeWithTag("phaseReadout").assertIsDisplayed()
            composeTestRule.onNodeWithText("STANDBY").assertIsDisplayed()

            composeTestRule.onNodeWithText("SETUP").performClick()
            composeTestRule.onNodeWithText("WAKE WORD").assertIsDisplayed()
            composeTestRule.onNodeWithTag("micStatus").performScrollTo().assertIsDisplayed()

            scenario.recreate()

            composeTestRule.onNodeWithText("WAKE WORD").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("micStatus").performScrollTo().assertIsDisplayed()

            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }

            composeTestRule.onNodeWithTag("phaseReadout").assertIsDisplayed()
            composeTestRule.onNodeWithText("STANDBY").assertIsDisplayed()
        }
    }
}
