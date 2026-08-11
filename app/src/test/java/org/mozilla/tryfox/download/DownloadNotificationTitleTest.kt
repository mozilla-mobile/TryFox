package org.mozilla.tryfox.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FENIX_DEBUG
import org.mozilla.tryfox.util.FOCUS_DEBUG

class DownloadNotificationTitleTest {
    @Test
    fun `uses a friendly Home app name and version`() {
        assertEquals("Fenix Nightly 155.0a1", homeDownloadNotificationTitle(FENIX, "155.0a1"))
        assertEquals("Fenix debug 155.0a1", homeDownloadNotificationTitle(FENIX_DEBUG, "155.0a1"))
        assertEquals("Focus debug 155.0a1", homeDownloadNotificationTitle(FOCUS_DEBUG, "155.0a1"))
    }
}
