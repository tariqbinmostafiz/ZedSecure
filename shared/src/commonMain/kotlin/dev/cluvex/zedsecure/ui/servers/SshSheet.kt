@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.servers

import org.jetbrains.compose.resources.StringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.weight
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.domain.config.PayloadPreset
import dev.cluvex.zedsecure.domain.config.SshProfile
import dev.cluvex.zedsecure.platform.newId
import dev.cluvex.zedsecure.ui.components.PickerField
import dev.cluvex.zedsecure.ui.platform.appVerticalScroll

@Composable
fun SshSheet(
    onDismiss: () -> Unit,
    onSave: (name: String, SshProfile) -> Unit,
    initial: SshProfile? = null,
    initialName: String = "",
    presets: List<PayloadPreset> = emptyList(),
    onSavePreset: (PayloadPreset) -> Unit = {},
    onDeletePreset: (id: String) -> Unit = {},
) {
    var showPresetMenu by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(initialName) }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf((initial?.port ?: 22).toString()) }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var authType by remember { mutableStateOf(initial?.authType ?: SshProfile.AUTH_PASSWORD) }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var privateKey by remember { mutableStateOf(initial?.privateKey ?: "") }
    var passphrase by remember { mutableStateOf(initial?.keyPassphrase ?: "") }
    var payloadMode by remember { mutableStateOf(initial?.payloadMode ?: SshProfile.PAYLOAD_NONE) }
    var payload by remember { mutableStateOf(initial?.payload ?: "") }
    var payloadDelay by remember { mutableStateOf((initial?.payloadDelayMs ?: 200).toString()) }
    var proxyHost by remember { mutableStateOf(initial?.proxyHost ?: "") }
    var proxyPort by remember { mutableStateOf((initial?.proxyPort ?: 8080).toString()) }
    var sni by remember { mutableStateOf(initial?.sni ?: "") }
    var responseMode by remember { mutableStateOf(initial?.responseMode ?: SshProfile.RESPONSE_AUTO) }
    var hostKeyPin by remember { mutableStateOf(initial?.hostKeyPin ?: "") }

    val isKey = authType == SshProfile.AUTH_KEY

    ModalBottomSheet(
        onDismissRequest = onDismiss,

        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        Column(
            Modifier.fillMaxWidth().appVerticalScroll(rememberScrollState()).imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.ssh_add_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            F(name, Res.string.manual_remark) { name = it }
            F(host, Res.string.ssh_host, "example.com") { host = it }
            F(port, Res.string.ssh_port, numeric = true) { port = it }
            F(username, Res.string.ssh_username) { username = it }
            PickerField(
                label = stringResource(Res.string.ssh_auth),
                options = listOf(
                    SshProfile.AUTH_PASSWORD to stringResource(Res.string.ssh_auth_password),
                    SshProfile.AUTH_KEY to stringResource(Res.string.ssh_auth_key),
                ),
                selected = authType,
                onSelect = { authType = it },
            )
            if (isKey) {
                F(privateKey, Res.string.ssh_private_key, minLines = 4) { privateKey = it }
                F(passphrase, Res.string.ssh_passphrase) { passphrase = it }
            } else {
                F(password, Res.string.manual_password) { password = it }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.ssh_section_payload),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    TextButton(onClick = { showPresetMenu = true }, enabled = presets.isNotEmpty()) {
                        Text(stringResource(Res.string.ssh_preset_load))
                    }
                    DropdownMenu(expanded = showPresetMenu, onDismissRequest = { showPresetMenu = false }) {
                        presets.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.name) },
                                onClick = {
                                    payloadMode = preset.payloadMode
                                    payload = preset.payload
                                    payloadDelay = preset.payloadDelayMs.toString()
                                    proxyHost = preset.proxyHost
                                    proxyPort = preset.proxyPort.toString()
                                    sni = preset.sni
                                    responseMode = preset.responseMode
                                    showPresetMenu = false
                                },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        onDeletePreset(preset.id)
                                    }) {
                                        Icon(
                                            painterResource(Res.drawable.ic_delete),
                                            contentDescription = stringResource(Res.string.action_delete),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = { presetName = ""; showSavePresetDialog = true }) {
                    Icon(
                        painterResource(Res.drawable.ic_add),
                        contentDescription = stringResource(Res.string.ssh_preset_save),
                    )
                }
            }
            PickerField(
                label = stringResource(Res.string.ssh_payload_mode),
                options = listOf(
                    SshProfile.PAYLOAD_NONE to stringResource(Res.string.ssh_payload_none),
                    SshProfile.PAYLOAD_DIRECT to stringResource(Res.string.ssh_payload_direct),
                    SshProfile.PAYLOAD_PROXY to stringResource(Res.string.ssh_payload_proxy),
                ),
                selected = payloadMode,
                onSelect = { payloadMode = it },
            )
            if (payloadMode != SshProfile.PAYLOAD_NONE) {
                if (payloadMode == SshProfile.PAYLOAD_PROXY) {
                    F(proxyHost, Res.string.ssh_proxy_host, "proxy.example.com") { proxyHost = it }
                    F(proxyPort, Res.string.ssh_proxy_port, numeric = true) { proxyPort = it }
                }
                F(payload, Res.string.ssh_payload, "CONNECT [host_port] HTTP/1.1[crlf]Host: [host][crlf][crlf]", minLines = 3) { payload = it }
                Text(
                    stringResource(Res.string.ssh_payload_tags),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                F(payloadDelay, Res.string.ssh_payload_delay, numeric = true) { payloadDelay = it }
                F(sni, Res.string.ssh_sni, "cdn.example.com") { sni = it }
                PickerField(
                    label = stringResource(Res.string.ssh_response),
                    options = listOf(
                        SshProfile.RESPONSE_AUTO to stringResource(Res.string.ssh_response_auto),
                        SshProfile.RESPONSE_NONE to stringResource(Res.string.ssh_response_none),
                        SshProfile.RESPONSE_STRICT to stringResource(Res.string.ssh_response_strict),
                    ),
                    selected = responseMode,
                    onSelect = { responseMode = it },
                )
            }
            if (showSavePresetDialog) {
                AlertDialog(
                    onDismissRequest = { showSavePresetDialog = false },
                    title = { Text(stringResource(Res.string.ssh_preset_save)) },
                    text = {
                        F(presetName, Res.string.ssh_preset_name) { presetName = it }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = presetName.isNotBlank(),
                            onClick = {
                                onSavePreset(
                                    PayloadPreset(
                                        id = newId(),
                                        name = presetName.trim(),
                                        payloadMode = payloadMode,
                                        payload = payload.trim(),
                                        payloadDelayMs = payloadDelay.toIntOrNull()?.coerceIn(0, 60_000) ?: 200,
                                        proxyHost = proxyHost.trim(),
                                        proxyPort = proxyPort.toIntOrNull() ?: 8080,
                                        sni = sni.trim(),
                                        responseMode = responseMode,
                                    ),
                                )
                                showSavePresetDialog = false
                            },
                        ) { Text(stringResource(Res.string.action_save)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSavePresetDialog = false }) { Text(stringResource(Res.string.action_cancel)) }
                    },
                )
            }
            F(hostKeyPin, Res.string.ssh_host_key_pin, "SHA256:...") { hostKeyPin = it }
            Text(
                stringResource(Res.string.ssh_host_key_pin_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    onSave(
                        name.trim(),
                        SshProfile(
                            host = host.trim(),
                            port = port.toIntOrNull() ?: 22,
                            username = username.trim(),
                            authType = authType,
                            password = password,
                            privateKey = privateKey.trim(),
                            keyPassphrase = passphrase,
                            payloadMode = payloadMode,
                            payload = payload.trim(),
                            payloadDelayMs = payloadDelay.toIntOrNull()?.coerceIn(0, 60_000) ?: 200,
                            proxyHost = proxyHost.trim(),
                            proxyPort = proxyPort.toIntOrNull() ?: 8080,
                            sni = sni.trim(),
                            responseMode = responseMode,
                            hostKeyPin = hostKeyPin.trim(),
                        ),
                    )
                },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text(stringResource(Res.string.action_save), fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun F(value: String, labelRes: StringResource, placeholder: String? = null, minLines: Int = 1, numeric: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(if (numeric) it.filter { c -> c.isDigit() } else it) },
        label = { Text(stringResource(labelRes)) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = minLines == 1,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
    )
}
