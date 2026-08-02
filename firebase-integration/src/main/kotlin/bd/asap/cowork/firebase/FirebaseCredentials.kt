package bd.asap.cowork.firebase

data class FirebaseCredentials(
    val appId: String,
    val ciToken: String,
    val testerGroups: String? = null,
    val releaseNotes: String? = null,
)

/**
 * Live, in-memory view of the currently-configured Firebase credentials,
 * read directly (no DI) by [FirebaseDistributeTool] — same pattern as
 * [bd.asap.cowork.toolintegrations.ToolchainPathsRegistry] and
 * [bd.asap.cowork.toolintegrations.EmulatorSession]. chat-gateway's
 * Firebase settings store is the only writer.
 */
object FirebaseCredentialsRegistry {
    @Volatile private var current: FirebaseCredentials? = null

    fun current(): FirebaseCredentials? = current

    fun set(value: FirebaseCredentials?) {
        current = value
    }
}
