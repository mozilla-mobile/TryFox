package org.mozilla.tryfox.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.mozilla.tryfox.data.repositories.UserDataRepository

/**
 * A fake implementation of [org.mozilla.tryfox.data.repositories.UserDataRepository] for testing purposes.
 */
class FakeUserDataRepository : UserDataRepository {

    private val _lastSearchedEmailFlow = MutableStateFlow("")
    override val lastSearchedEmailFlow: Flow<String> = _lastSearchedEmailFlow

    override suspend fun saveLastSearchedEmail(email: String) {
        _lastSearchedEmailFlow.value = email
    }

    // Helper method for tests to clear the stored email if needed
    fun clearLastSearchedEmail() {
        _lastSearchedEmailFlow.value = ""
    }
}
