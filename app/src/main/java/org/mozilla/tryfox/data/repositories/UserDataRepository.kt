package org.mozilla.tryfox.data.repositories

import kotlinx.coroutines.flow.Flow
import org.mozilla.tryfox.lan.LanReceiveIdentity

/**
 * A repository that stores the last searched email.
 */
interface UserDataRepository {

    /**
     * A flow that emits the last searched email.
     */
    val lastSearchedEmailFlow: Flow<String>
    val lanReceiveIdentityFlow: Flow<LanReceiveIdentity?>

    /**
     * Saves the last searched email.
     * @param email The email to save.
     */
    suspend fun saveLastSearchedEmail(email: String)
    suspend fun saveLanReceiveIdentity(identity: LanReceiveIdentity)
}
