package bd.asap.cowork.imagegen

import com.google.genai.Client
import com.google.genai.types.EditImageConfig
import com.google.genai.types.GenerateImagesConfig
import com.google.genai.types.Image
import com.google.genai.types.RawReferenceImage
import com.google.genai.types.ReferenceImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [apiKeyProvider] is re-resolved on every request, same reasoning as llm-gateway's OpenAI/Gemini chat providers. */
class GeminiImageProvider(private val apiKeyProvider: () -> String?) : ImageProvider {
    override val id = "gemini"

    // Pin against Google's current Gemini image-model catalog at deploy time — model ids on this API change over time.
    private val model = "imagen-4.0-generate-001"

    private fun client() = Client.builder()
        .apiKey(apiKeyProvider() ?: throw ImageProviderException("Gemini image API key is not set. Add it from the admin panel's Image AI section."))
        .build()

    override suspend fun generate(prompt: String, aspect: ImageAspect): GeneratedImage = withContext(Dispatchers.IO) {
        val config = GenerateImagesConfig.builder()
            .numberOfImages(1)
            .aspectRatio(aspect.toGeminiAspectRatio())
            .outputMimeType("image/png")
            .build()
        val response = client().models.generateImages(model, prompt, config)
        val image = response.images()?.firstOrNull() ?: throw ImageProviderException("Gemini returned no image data.")
        image.toGeneratedImage()
    }

    override suspend fun edit(instruction: String, sourceImage: ByteArray, sourceMimeType: String): GeneratedImage = withContext(Dispatchers.IO) {
        val reference = RawReferenceImage.builder()
            .referenceImage(Image.builder().imageBytes(sourceImage).mimeType(sourceMimeType).build())
            .build()
        val config = EditImageConfig.builder().numberOfImages(1).outputMimeType("image/png").build()
        val response = client().models.editImage(model, instruction, listOf<ReferenceImage>(reference), config)
        val image = response.generatedImages().orElse(emptyList()).firstOrNull()?.image()?.orElse(null)
            ?: throw ImageProviderException("Gemini returned no edited image.")
        image.toGeneratedImage()
    }

    private fun Image.toGeneratedImage() = GeneratedImage(
        bytes = imageBytes().orElseThrow { ImageProviderException("Gemini image had no bytes.") },
        mimeType = mimeType().orElse("image/png"),
    )

    private fun ImageAspect.toGeminiAspectRatio() = when (this) {
        ImageAspect.SQUARE -> "1:1"
        ImageAspect.PORTRAIT -> "9:16"
        ImageAspect.LANDSCAPE -> "16:9"
    }
}
