package org.mozilla.tryfox.data.managers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.mozilla.tryfox.data.repositories.UserDataRepository
import org.mozilla.tryfox.lan.LanReceiveIdentity

/**
 * A fake implementation of [UserDataRepository] for testing purposes.
 */
class FakeUserDataRepository : UserDataRepository {

    private val _lastSearchedEmailFlow = MutableStateFlow("")
    override val lastSearchedEmailFlow: Flow<String> = _lastSearchedEmailFlow
    private val _lanReceiveIdentityFlow = MutableStateFlow<LanReceiveIdentity?>(null)
    override val lanReceiveIdentityFlow: Flow<LanReceiveIdentity?> = _lanReceiveIdentityFlow

    override suspend fun saveLastSearchedEmail(email: String) {
        _lastSearchedEmailFlow.value = email
    }

    override suspend fun saveLanReceiveIdentity(identity: LanReceiveIdentity) {
        _lanReceiveIdentityFlow.value = identity
    }

    // Helper method for tests to clear the stored email if needed
    fun clearLastSearchedEmail() {
        _lastSearchedEmailFlow.value = ""
    }
}
