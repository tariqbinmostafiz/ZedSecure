package dev.cluvex.zedsecure.domain.config

import kotlinx.serialization.Serializable

/** A saved, reusable SSH payload setup, selectable when adding or editing any SSH server. */
@Serializable
data class PayloadPreset(
    val id: String,
    val name: String,
    val payloadMode: String = SshProfile.PAYLOAD_NONE,
    val payload: String = "",
    val payloadDelayMs: Int = 200,
    val proxyHost: String = "",
    val proxyPort: Int = 8080,
    val sni: String = "",
    val responseMode: String = SshProfile.RESPONSE_AUTO,
)
