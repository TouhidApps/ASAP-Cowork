package bd.asap.cowork.imagegen

/** [provider] is "openai" or "gemini" — which one generate_image/edit_image actually calls. */
data class ImageGenCredentials(
    val provider: String,
    val openAiApiKey: String? = null,
    val geminiApiKey: String? = null,
)

/**
 * Live, in-memory view of the currently-configured Image AI provider/keys,
 * read directly (no DI) by the imaging tools — same pattern as
 * [bd.asap.cowork.firebase.FirebaseCredentialsRegistry]. chat-gateway's
 * ImageGenCredentialsStore is the only writer.
 */
object ImageGenCredentialsRegistry {
    @Volatile private var current: ImageGenCredentials? = null

    fun current(): ImageGenCredentials? = current

    fun set(value: ImageGenCredentials?) {
        current = value
    }
}
