package bd.asap.cowork.chatgateway.features.admin

import bd.asap.cowork.chatgateway.common.exceptions.AppException
import bd.asap.cowork.chatgateway.config.FirebaseCredentialsStore
import bd.asap.cowork.firebase.FirebaseCredentials
import bd.asap.cowork.firebase.FirebaseCredentialsRegistry

/**
 * Business logic behind the admin panel's Firebase section: view whether
 * credentials are configured, save them, or clear them. Every write goes
 * through both [store] (persists across restarts) and
 * [FirebaseCredentialsRegistry] (what `distribute_apk` actually reads),
 * so a save takes effect on the very next chat message with no restart.
 */
class FirebaseService(private val store: FirebaseCredentialsStore) {
    fun status(): FirebaseStatus {
        val current = FirebaseCredentialsRegistry.current()
        return FirebaseStatus(
            configured = current != null,
            appId = current?.appId,
            ciToken = current?.ciToken,
            testerGroups = current?.testerGroups,
            releaseNotes = current?.releaseNotes,
        )
    }

    suspend fun setCredentials(appId: String, ciToken: String, testerGroups: String?, releaseNotes: String?): FirebaseStatus {
        if (appId.isBlank() || ciToken.isBlank()) {
            throw AppException.BadRequest("appId and ciToken must not be blank")
        }
        val credentials = FirebaseCredentials(
            appId = appId.trim(),
            ciToken = ciToken.trim(),
            testerGroups = testerGroups?.trim()?.takeIf { it.isNotBlank() },
            releaseNotes = releaseNotes?.trim()?.takeIf { it.isNotBlank() },
        )
        store.write(credentials)
        FirebaseCredentialsRegistry.set(credentials)
        return status()
    }

    suspend fun clearCredentials(): FirebaseStatus {
        store.clear()
        FirebaseCredentialsRegistry.set(null)
        return status()
    }
}
