package org.mozilla.tryfox.lan

import kotlinx.coroutines.flow.first
import org.mozilla.tryfox.data.repositories.UserDataRepository

class LanReceiveIdentityManager(
    private val userDataRepository: UserDataRepository,
) {
    suspend fun getOrCreateIdentity(): LanReceiveIdentity {
        val existingIdentity = userDataRepository.lanReceiveIdentityFlow.first()
        if (existingIdentity != null) {
            return existingIdentity
        }

        val createdIdentity = LanReceiveIdentity(
            deviceId = randomBase64Url(byteCount = 16),
            deviceName = defaultDeviceName(),
            sharedSecret = randomBase64Url(byteCount = 32),
        )
        userDataRepository.saveLanReceiveIdentity(createdIdentity)
        return createdIdentity
    }
}
