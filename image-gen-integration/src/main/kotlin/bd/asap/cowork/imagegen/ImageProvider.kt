package bd.asap.cowork.imagegen

/** One generated/edited image's raw bytes plus the format they're in, so a caller can pick the right file extension. */
data class GeneratedImage(val bytes: ByteArray, val mimeType: String)

/** Aspect-ratio hint only — deliberately not literal pixel width/height, since OpenAI and Gemini each accept a different string format for "square"/"portrait"/"landscape" and translate it internally. */
enum class ImageAspect { SQUARE, PORTRAIT, LANDSCAPE }

/**
 * Provider-agnostic contract for the two image backends this platform
 * supports — kept intentionally minimal (generate + edit only, no
 * variations/inpainting/streaming) per V1 scope. Mirrors llm-gateway's
 * LlmProvider shape/spirit but is its own interface, not a reuse of
 * LlmProvider, since image generation isn't a chat completion.
 */
interface ImageProvider {
    val id: String

    suspend fun generate(prompt: String, aspect: ImageAspect = ImageAspect.SQUARE): GeneratedImage

    suspend fun edit(instruction: String, sourceImage: ByteArray, sourceMimeType: String): GeneratedImage
}

class ImageProviderException(message: String) : RuntimeException(message)
