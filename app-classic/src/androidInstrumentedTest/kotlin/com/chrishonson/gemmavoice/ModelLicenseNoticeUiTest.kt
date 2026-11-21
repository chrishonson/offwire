package com.chrishonson.gemmavoice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chrishonson.gemmavoice.chat.ModelLicenseNotice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelLicenseNoticeUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsApacheNoticeWithoutGemmaTerms() {
        composeTestRule.setContent { ModelLicenseNotice() }

        composeTestRule.onNodeWithTag("model-license-notice").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Runs Gemma 4, licensed under Apache 2.0. Offwire is not affiliated with Google.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Apache License 2.0").assertIsDisplayed()
        composeTestRule.onNodeWithText("Terms of Use", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Prohibited Use Policy").assertDoesNotExist()
    }
}
