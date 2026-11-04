package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.Serializable

@Serializable
data class SshProfile(
    val host: String,
    val port: Int = 22,
    val username: String = "",

    val authType: String = AUTH_PASSWORD,
    val password: String = "",

    val privateKey: String = "",
    val keyPassphrase: String = "",

    val payloadMode: String = PAYLOAD_NONE,
    val payload: String = "",
    val payloadDelayMs: Int = 200,
    val proxyHost: String = "",
    val proxyPort: Int = 8080,
    val sni: String = "",
    val responseMode: String = RESPONSE_AUTO,

    val hostKeyPin: String = "",
) {
    companion object {
        const val AUTH_PASSWORD = "password"
        const val AUTH_KEY = "key"

        const val PAYLOAD_NONE = "none"
        const val PAYLOAD_DIRECT = "direct"
        const val PAYLOAD_PROXY = "proxy"

        const val RESPONSE_AUTO = "auto"
        const val RESPONSE_NONE = "none"
        const val RESPONSE_STRICT = "strict"
    }
}
