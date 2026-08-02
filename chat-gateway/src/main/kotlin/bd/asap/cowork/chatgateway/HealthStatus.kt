package bd.asap.cowork.chatgateway

import kotlinx.serialization.Serializable

@Serializable
data class HealthStatus(
    val status: String,
    val service: String,
    val timestamp: Long,
)
