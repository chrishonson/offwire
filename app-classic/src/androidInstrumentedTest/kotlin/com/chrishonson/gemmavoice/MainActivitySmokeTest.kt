package com.chrishonson.gemmavoice

import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
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
}
