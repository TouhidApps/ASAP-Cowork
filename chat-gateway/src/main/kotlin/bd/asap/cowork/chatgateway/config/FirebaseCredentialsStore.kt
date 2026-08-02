package bd.asap.cowork.chatgateway.config

import bd.asap.cowork.firebase.FirebaseCredentials

/**
 * Firebase App Distribution credentials saved from the admin panel, backed
 * by the `.env` file (see [DotEnv]) rather than the SQLite `settings`
 * table. Read by [bd.asap.cowork.agents.publishing.PublishingAgent] via
 * `FirebaseCredentialsRegistry`, not this store directly.
 */
class FirebaseCredentialsStore {
    fun read(): FirebaseCredentials? {
        val appId = DotEnv.get(APP_ID_KEY) ?: return null
        val ciToken = DotEnv.get(CI_TOKEN_KEY) ?: return null
        return FirebaseCredentials(appId, ciToken, DotEnv.get(TESTER_GROUPS_KEY), DotEnv.get(RELEASE_NOTES_KEY))
    }

    fun write(credentials: FirebaseCredentials) {
        DotEnv.set(APP_ID_KEY, credentials.appId)
        DotEnv.set(CI_TOKEN_KEY, credentials.ciToken)
        DotEnv.set(TESTER_GROUPS_KEY, credentials.testerGroups.orEmpty())
        DotEnv.set(RELEASE_NOTES_KEY, credentials.releaseNotes.orEmpty())
    }

    fun clear() {
        DotEnv.set(APP_ID_KEY, "")
        DotEnv.set(CI_TOKEN_KEY, "")
        DotEnv.set(TESTER_GROUPS_KEY, "")
        DotEnv.set(RELEASE_NOTES_KEY, "")
    }

    private companion object {
        const val APP_ID_KEY = "FIREBASE_APP_ID"
        const val CI_TOKEN_KEY = "FIREBASE_CI_TOKEN"
        const val TESTER_GROUPS_KEY = "FIREBASE_TESTER_GROUPS"
        const val RELEASE_NOTES_KEY = "FIREBASE_RELEASE_NOTES"
    }
}
