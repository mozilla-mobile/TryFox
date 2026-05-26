package org.mozilla.tryfox.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
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

    @Test
    fun scannerVisorOverlay_showsScanningStatus() {
        composeTestRule.setContent {
            TryFoxTheme {
                ScannerVisorOverlay(modifier = Modifier.fillMaxSize())
            }
        }

        composeTestRule.onNodeWithText("Scanning...").assertIsDisplayed()
    }

    @Test
    fun scannerVisorOverlay_supportsNarrowWidths() {
        composeTestRule.setContent {
            TryFoxTheme {
                ScannerVisorOverlay(
                    modifier = Modifier
                        .width(120.dp)
                        .height(240.dp),
                )
            }
        }

        composeTestRule.onNodeWithText("Scanning...").assertIsDisplayed()
    }
}
