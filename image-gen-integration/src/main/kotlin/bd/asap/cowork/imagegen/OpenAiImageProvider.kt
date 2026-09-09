package bd.asap.cowork.imagegen

import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.images.ImageEditParams
import com.openai.models.images.ImageGenerateParams
import com.openai.models.images.ImageModel
import com.openai.models.images.ImagesResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

/** [apiKeyProvider] is re-resolved on every request, same reasoning as llm-gateway's OpenAI/Gemini chat providers. */
class OpenAiImageProvider(private val apiKeyProvider: () -> String?) : ImageProvider {
    override val id = "openai"

    private fun client() = OpenAIOkHttpClient.builder()
        .apiKey(apiKeyProvider() ?: throw ImageProviderException("OpenAI image API key is not set. Add it from the admin panel's Image AI section."))
        .build()

    override suspend fun generate(prompt: String, aspect: ImageAspect): GeneratedImage = withContext(Dispatchers.IO) {
        val params = ImageGenerateParams.builder()
            .model(ImageModel.GPT_IMAGE_1)
            .prompt(prompt)
            .size(aspect.toOpenAiSize())
            .responseFormat(ImageGenerateParams.ResponseFormat.B64_JSON)
            .build()
        client().images().generate(params).decodeFirstImage()
    }

    override suspend fun edit(instruction: String, sourceImage: ByteArray, sourceMimeType: String): GeneratedImage = withContext(Dispatchers.IO) {
        val params = ImageEditParams.builder()
            .model(ImageModel.GPT_IMAGE_1)
            .image(sourceImage)
            .prompt(instruction)
            .build()
        client().images().edit(params).decodeFirstImage()
    }

    private fun ImagesResponse.decodeFirstImage(): GeneratedImage {
        val b64 = data().orElse(emptyList()).firstOrNull()?.b64Json()?.orElse(null)
            ?: throw ImageProviderException("OpenAI returned no image data.")
        return GeneratedImage(Base64.getDecoder().decode(b64), "image/png")
    }

    private fun ImageAspect.toOpenAiSize() = when (this) {
        ImageAspect.SQUARE -> "1024x1024"
        ImageAspect.PORTRAIT -> "1024x1536"
        ImageAspect.LANDSCAPE -> "1536x1024"
    }
}
