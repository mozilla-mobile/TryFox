package org.mozilla.tryfox.data

import org.mozilla.tryfox.model.ParsedNightlyApk

interface MozillaArchiveRepository {
    /**
     * Fetches and parses the list of Fenix nightly builds for the current month from the archive.
     */
    suspend fun getFenixNightlyBuilds(): NetworkResult<List<ParsedNightlyApk>>

    /**
     * Fetches and parses the list of Focus nightly builds for the current month from the archive.
     */
    suspend fun getFocusNightlyBuilds(): NetworkResult<List<ParsedNightlyApk>>

    /**
     * Fetches and parses the list of Reference Browser nightly builds from TaskCluster storage.
     */
    suspend fun getReferenceBrowserNightlyBuilds(): NetworkResult<List<ParsedNightlyApk>>
}
