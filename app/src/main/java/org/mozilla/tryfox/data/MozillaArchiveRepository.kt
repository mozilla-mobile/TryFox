package org.mozilla.tryfox.data

import kotlinx.datetime.LocalDate
import org.mozilla.tryfox.model.MozillaArchiveApk

interface MozillaArchiveRepository {
    /**
     * Fetches and parses the list of Fenix nightly builds for the current month from the archive.
     */
    suspend fun getFenixNightlyBuilds(date: LocalDate? = null): NetworkResult<List<MozillaArchiveApk>>

    /**
     * Fetches and parses the list of Focus nightly builds for the current month from the archive.
     */
    suspend fun getFocusNightlyBuilds(date: LocalDate? = null): NetworkResult<List<MozillaArchiveApk>>
}
