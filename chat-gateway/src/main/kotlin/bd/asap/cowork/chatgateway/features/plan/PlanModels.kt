package bd.asap.cowork.chatgateway.features.plan

import kotlinx.serialization.Serializable

@Serializable
data class MarkdownFileEntry(
    val path: String,
    val name: String,
    val updatedAt: Long,
)
