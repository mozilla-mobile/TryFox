package org.mozilla.tryfox.data

import kotlinx.datetime.LocalDate
import org.mozilla.tryfox.model.ParsedNightlyApk

interface MozillaArchiveRepository {
    /**
     * Fetches and parses the list of Fenix nightly builds for the current month from the archive.
     */
    suspend fun getFenixNightlyBuilds(date: LocalDate? = null): NetworkResult<List<ParsedNightlyApk>>

    /**
     * Fetches and parses the list of Focus nightly builds for the current month from the archive.
     */
    suspend fun getFocusNightlyBuilds(date: LocalDate? = null): NetworkResult<List<ParsedNightlyApk>>
}
