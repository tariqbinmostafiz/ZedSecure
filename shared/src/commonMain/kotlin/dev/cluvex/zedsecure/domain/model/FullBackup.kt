package dev.cluvex.zedsecure.domain.model

import dev.cluvex.zedsecure.domain.config.PayloadPreset
import dev.cluvex.zedsecure.domain.config.VpnProfile
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * A single file holding everything the app stores locally: settings, servers (with their payload
 * setup), saved payload presets, and the data-usage history. Meant for move-to-a-new-phone and
 * reinstall, not for syncing between devices.
 */
object FullBackup {
    const val FILE_NAME = "zedtunnel-backup.json"
    private const val MARK = "zedtunnelFullBackup"
    private const val VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        prettyPrint = true
    }

    data class Payload(
        val settings: AppSettings,
        val profiles: List<VpnProfile>,
        val presets: List<PayloadPreset>,
        val usage: String,
    )

    fun export(data: Payload): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put(MARK, VERSION)
            put("settings", json.encodeToJsonElement(AppSettings.serializer(), data.settings))
            put("profiles", json.encodeToJsonElement(ListSerializer(VpnProfile.serializer()), data.profiles))
            put("presets", json.encodeToJsonElement(ListSerializer(PayloadPreset.serializer()), data.presets))
            put("usage", JsonPrimitive(data.usage))
        },
    )

    /** Returns null if the text isn't a backup made by this app, or is unreadable. */
    fun read(text: String): Payload? = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        if (!root.containsKey(MARK)) return null
        Payload(
            settings = json.decodeFromJsonElement(AppSettings.serializer(), root.getValue("settings")),
            profiles = json.decodeFromJsonElement(ListSerializer(VpnProfile.serializer()), root.getValue("profiles")),
            presets = json.decodeFromJsonElement(ListSerializer(PayloadPreset.serializer()), root.getValue("presets")),
            usage = root["usage"]?.jsonPrimitive?.content ?: "",
        )
    }.getOrNull()
}
