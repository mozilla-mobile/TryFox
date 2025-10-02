package org.mozilla.tryfox.data

import kotlinx.coroutines.flow.Flow

/**
 * A repository that stores the last searched email.
 */
interface UserDataRepository {
    /**
     * A flow that emits the last searched email.
     */
    val lastSearchedEmailFlow: Flow<String>

    /**
     * Saves the last searched email.
     * @param email The email to save.
     */
    suspend fun saveLastSearchedEmail(email: String)
}
