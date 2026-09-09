package bd.asap.cowork.imagegen

/** Resolves whichever provider the admin panel's Image AI section currently has selected, or null if nothing is configured yet. */
object ImageProviders {
    fun current(): ImageProvider? {
        val credentials = ImageGenCredentialsRegistry.current() ?: return null
        return when (credentials.provider) {
            "openai" -> OpenAiImageProvider { credentials.openAiApiKey }
            "gemini" -> GeminiImageProvider { credentials.geminiApiKey }
            else -> null
        }
    }
}
