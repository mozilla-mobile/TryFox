package org.mozilla.tryfox.data

import org.mozilla.tryfox.model.ParsedNightlyApk
import org.mozilla.tryfox.util.REFERENCE_BROWSER

/**
 * A fake implementation of [ReferenceBrowserReleaseRepository] for use in unit tests.
 */
class FakeReferenceBrowserReleaseRepository(
    private val releases: NetworkResult<List<ParsedNightlyApk>> = NetworkResult.Success(emptyList()),
) : ReleaseRepository {

    override val appName: String = REFERENCE_BROWSER

    override suspend fun getLatestReleases(): NetworkResult<List<ParsedNightlyApk>> {
        return releases
    }
}
