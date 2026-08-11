package org.mozilla.tryfox.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class DownloadNotificationIdTest {
    @Test
    fun `returns the same positive notification ID for the same worker`() {
        val workerId = UUID.fromString("6a9bd3fa-c00e-4c50-9647-d5d3c8d8a504")

        val firstId = DownloadNotificationId.forWorker(workerId)

        assertEquals(firstId, DownloadNotificationId.forWorker(workerId))
        assertTrue(firstId > 0)
    }

    @Test
    fun `returns distinct notification IDs for concurrent workers`() {
        val firstWorkerId = UUID.fromString("6a9bd3fa-c00e-4c50-9647-d5d3c8d8a504")
        val secondWorkerId = UUID.fromString("34e52d86-7179-41eb-8e09-c00eca920878")

        assertNotEquals(
            DownloadNotificationId.forWorker(firstWorkerId),
            DownloadNotificationId.forWorker(secondWorkerId),
        )
    }

    @Test
    fun `does not discard high worker hash bits`() {
        assertNotEquals(
            DownloadNotificationId.forWorker(UUID(0, 0)),
            DownloadNotificationId.forWorker(UUID(0, 65_536)),
        )
    }

    @Test
    fun `never returns the invalid zero notification ID`() {
        assertNotEquals(0, DownloadNotificationId.forWorker(UUID(0, 0)))
    }
}
