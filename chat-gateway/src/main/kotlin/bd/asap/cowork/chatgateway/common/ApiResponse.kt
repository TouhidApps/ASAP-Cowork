package bd.asap.cowork.chatgateway.common

import kotlinx.serialization.Serializable

/**
 * Standard envelope for every JSON response so the frontend can rely on one
 * shape regardless of which route produced it.
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
) {
    companion object {
        fun <T> ok(data: T): ApiResponse<T> = ApiResponse(success = true, data = data)

        fun error(code: String, message: String): ApiResponse<Nothing> =
            ApiResponse(success = false, error = ApiError(code, message))
    }
}

@Serializable
data class ApiError(
    val code: String,
    val message: String,
)
