package org.mozilla.tryfox.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.tryfox.ui.theme.TryFoxTheme

@RunWith(AndroidJUnit4::class)
class QrCodeScannerScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun qrCodeScannerScreen_whenCameraPermissionUnavailable_showsDeniedState() {
        composeTestRule.setContent {
            TryFoxTheme {
                QrCodeScannerScreen(
                    onNavigateUp = {},
                    onQrCodeScanned = { false },
                    cameraPermissionChecker = { false },
                    requestPermissionOnStart = false,
                )
            }
        }

        composeTestRule
            .onNodeWithText("Camera permission is required to scan QR codes.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
    }
}
