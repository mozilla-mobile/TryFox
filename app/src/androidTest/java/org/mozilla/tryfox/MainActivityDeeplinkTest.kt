package org.mozilla.tryfox

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
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
        val deeplinkUri =
            Uri.parse("https://treeherder.mozilla.org/#/jobs?repo=$project&revision=$revision")
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = deeplinkUri
            }

        ActivityScenario.launch<MainActivity>(intent).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Revision").assert(hasText(revision))
            composeTestRule.onNodeWithText("Project").assert(hasText(project))
        }
    }

    @Test
    fun testDeeplink_withoutHash_populatesRevision() {
        val project = "mozilla-central"
        val revision = "fedcba654321"
        val deeplinkUri =
            Uri.parse("https://treeherder.mozilla.org/jobs?repo=$project&revision=$revision")
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = deeplinkUri
            }

        ActivityScenario.launch<MainActivity>(intent).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(revision).assertExists()
        }
    }

    @Test
    fun testDeeplink_whenAppIsAlreadyOpen_populatesRevision() {
        // Launch the app first
        ActivityScenario.launch(MainActivity::class.java)

        val project = "autoland"
        val revision = "abcdef123456"
        val deeplinkUri =
            Uri.parse("https://treeherder.mozilla.org/#/jobs?repo=$project&revision=$revision")
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = deeplinkUri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        // Now send the deeplink intent
        ApplicationProvider.getApplicationContext<android.content.Context>().startActivity(intent)

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(revision).assertExists()
    }

    @Test
    fun testDeeplink_withAuthorEmail_populatesProfileScreen() {
        val email = "tthibaud@mozilla.com"
        val encodedEmail = "tthibaud%40mozilla.com"
        val deeplinkUri =
            Uri.parse("https://treeherder.mozilla.org/jobs?repo=try&author=$encodedEmail")
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = deeplinkUri
            }

        ActivityScenario.launch<MainActivity>(intent).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(email).assertExists()
        }
    }
}
