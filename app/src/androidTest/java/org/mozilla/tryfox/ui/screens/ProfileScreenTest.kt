package org.mozilla.tryfox.ui.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.tryfox.data.FakeCacheManager
import org.mozilla.tryfox.data.FakeFenixRepository
import org.mozilla.tryfox.data.FakeUserDataRepository
import org.mozilla.tryfox.data.UserDataRepository
import org.mozilla.tryfox.data.managers.CacheManager
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val fenixRepository = FakeFenixRepository(downloadProgressDelayMillis = 100L)
    private val userDataRepository: UserDataRepository = FakeUserDataRepository()
    private val cacheManager: CacheManager = FakeCacheManager()
    private val profileViewModel =
        ProfileViewModel(
            fenixRepository = fenixRepository,
            userDataRepository = userDataRepository,
            cacheManager = cacheManager,
        )
    private val emailInputTag = "profile_email_input"
    private val emailClearButtonTag = "profile_email_clear_button"
    private val searchButtonTag = "profile_search_button"

    private val downloadButtonInitialTag = "action_button_download_initial"
    private val downloadButtonLoadingTag = "action_button_downloading"
    private val downloadButtonInstallTag = "action_button_install_ready"

    private val longTimeoutMillis = 1_000L

    @Test
    fun searchPushesAndCheckDownloadAndInstallStates() {
        var capturedApkFile: File? = null

        profileViewModel.onInstallApk = { apkFile ->
            capturedApkFile = apkFile
        }

        composeTestRule.setContent {
            ProfileScreen(
                profileViewModel = profileViewModel,
                onNavigateUp = { },
            )
        }

        val emailFieldNode = composeTestRule.onNodeWithTag(emailInputTag).fetchSemanticsNode()
        if (emailFieldNode.config[SemanticsProperties.EditableText].text.isNotEmpty()) {
            composeTestRule.onNodeWithTag(emailClearButtonTag).performClick()
        }

        composeTestRule.onNodeWithTag(emailInputTag).performTextInput("example@mozilla.com")
        composeTestRule.onNodeWithTag(searchButtonTag).performClick()

        composeTestRule.waitUntil("Wait for at least one download button", longTimeoutMillis) {
            composeTestRule
                .onAllNodesWithTag(downloadButtonInitialTag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule
            .onNodeWithTag(downloadButtonInitialTag, useUnmergedTree = true)
            .performClick()

        composeTestRule.waitUntil("Download button enters loading state", longTimeoutMillis) {
            tryOrFalse {
                composeTestRule.onNodeWithTag(downloadButtonLoadingTag, useUnmergedTree = true).assertIsDisplayed()
            }
        }

        composeTestRule.waitUntil("Download button enters install state", longTimeoutMillis) {
            tryOrFalse {
                composeTestRule
                    .onNodeWithTag(downloadButtonInstallTag, useUnmergedTree = true)
                    .assertIsDisplayed()
            }
        }

        assertNotNull("APK file should have been captured by onInstallApk callback", capturedApkFile)
    }

    private fun tryOrFalse(block: () -> Unit): Boolean =
        try {
            block()
            true
        } catch (_: AssertionError) {
            false
        }
}
