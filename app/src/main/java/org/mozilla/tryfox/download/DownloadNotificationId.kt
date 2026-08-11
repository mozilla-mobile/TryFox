package org.mozilla.tryfox.download

import java.util.UUID

/** Creates stable notification IDs for individual download workers. */
object DownloadNotificationId {
    private const val ID_MASK = Int.MAX_VALUE

    fun forWorker(workerId: UUID): Int =
        (workerId.hashCode() and ID_MASK).coerceAtLeast(1)
}
