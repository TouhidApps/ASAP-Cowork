package bd.asap.cowork.chatgateway.features.email

import kotlinx.serialization.Serializable

/** [clientSecret] is returned in full (not masked) — this admin panel already returns other saved secrets (e.g. FirebaseStatus.ciToken) as-is, consistent with its single-trusted-operator threat model. */
@Serializable
data class GmailOAuthStatus(
    val configured: Boolean,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val redirectUri: String,
)

@Serializable
data class SetGmailOAuthCredentialsRequest(val clientId: String, val clientSecret: String)

@Serializable
data class GmailOAuthAuthorizeUrl(val url: String)
