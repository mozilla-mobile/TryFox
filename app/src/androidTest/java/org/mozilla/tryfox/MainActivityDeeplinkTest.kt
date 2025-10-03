package org.mozilla.tryfox

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityDeeplinkTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testDeeplink_withHash_populatesRevision() {
        val project = "try"
        val revision = "abcdef123456"
        val deeplinkUri = Uri.parse("https://treeherder.mozilla.org/#/jobs?repo=$project&revision=$revision")
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = deeplinkUri
        }

        ActivityScenario.launch<MainActivity>(intent).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(revision).assertExists()
        }
    }

    @Test
    fun testDeeplink_withoutHash_populatesRevision() {
        val project = "mozilla-central"
        val revision = "fedcba654321"
        val deeplinkUri = Uri.parse("https://treeherder.mozilla.org/jobs?repo=$project&revision=$revision")
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = deeplinkUri
        }

        ActivityScenario.launch<MainActivity>(intent).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(revision).assertExists()
        }
    }
}
