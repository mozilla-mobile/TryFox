package org.mozilla.tryfox.data

import org.mozilla.tryfox.model.MozillaArchiveApk
import org.mozilla.tryfox.util.TRYFOX

/**
 * A fake implementation of [TryFoxReleaseRepository] for use in unit tests.
 */
class FakeTryFoxReleaseRepository(
    private val releases: NetworkResult<List<MozillaArchiveApk>> = NetworkResult.Success(emptyList()),
) : ReleaseRepository {

    override val appName: String = TRYFOX

    override suspend fun getLatestReleases(): NetworkResult<List<MozillaArchiveApk>> {
        return releases
    }
}
