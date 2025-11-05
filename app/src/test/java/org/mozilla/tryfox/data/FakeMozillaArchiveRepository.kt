package org.mozilla.tryfox.data

import kotlinx.datetime.LocalDate
import org.mozilla.tryfox.model.ParsedNightlyApk

/**
 * A fake implementation of [MozillaArchiveRepository] for use in unit tests.
 */
class FakeMozillaArchiveRepository(
    private val fenixBuilds: NetworkResult<List<ParsedNightlyApk>> = NetworkResult.Success(emptyList()),
    private val focusBuilds: NetworkResult<List<ParsedNightlyApk>> = NetworkResult.Success(emptyList()),
) : MozillaArchiveRepository {

    override suspend fun getFenixNightlyBuilds(date: LocalDate?): NetworkResult<List<ParsedNightlyApk>> {
        return fenixBuilds
    }

    override suspend fun getFocusNightlyBuilds(date: LocalDate?): NetworkResult<List<ParsedNightlyApk>> {
        return focusBuilds
    }
}
