package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.main.views.FormDropdown
import de.rpicloud.ipv64net.main.views.FormNumberField
import de.rpicloud.ipv64net.main.views.FormTextField
import de.sr.ipv64.model.CreateCdnMappingRequest
import de.sr.ipv64.model.CdnTransport
import de.sr.ipv64.model.Record

private val LISTEN_PORTS = 50000..62000

/**
 * Dialog zum Anlegen eines Port-Mappings. [records] sind nur Records, die über das CDN laufen.
 * Ohne öffentlichen Port vergibt der Server einen freien zwischen 50000 und 62000.
 */
@Composable
fun CdnMappingDialog(
    records: List<Record>,
    onConfirm: (CreateCdnMappingRequest) -> Unit,
    onDismiss: () -> Unit
) {
    if (records.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add port mapping") },
            text = { Text("No record runs through the CDN yet. Enable the CDN for an A or AAAA record first.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
        )
        return
    }

    var record by remember { mutableStateOf(records.first()) }
    var name by remember { mutableStateOf("") }
    var targetPort by remember { mutableStateOf("") }
    var listenPort by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf(CdnTransport.TCP) }
    var targetError by remember { mutableStateOf<String?>(null) }
    var listenError by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val target = parsePort(targetPort)
        if (target == null) {
            targetError = "Enter a port between 1 and 65535."
            return
        }
        var listen: Int? = null
        if (listenPort.isNotBlank()) {
            listen = parsePort(listenPort, LISTEN_PORTS)
            if (listen == null) {
                listenError = "Enter a port between ${LISTEN_PORTS.first} and ${LISTEN_PORTS.last} or leave it empty."
                return
            }
        }
        val ref = record.cdnRef() ?: return
        onConfirm(
            CreateCdnMappingRequest(
                record = ref,
                targetPort = target,
                transport = transport,
                name = name.trim().takeIf { it.isNotEmpty() },
                listenPort = listen
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add port mapping") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Forwards a public port of the CDN server to a port on your server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FormDropdown(
                    label = "Target record",
                    value = record,
                    options = records,
                    labelOf = { it.cdnTitle() },
                    onSelect = { record = it }
                )
                FormNumberField(
                    label = "Port on your server",
                    value = targetPort,
                    onValue = { targetPort = it; targetError = null },
                    supporting = targetError
                )
                FormDropdown(
                    label = "Protocol",
                    value = transport,
                    options = CdnTransport.entries.toList(),
                    labelOf = { it.transportLabel() },
                    onSelect = { transport = it }
                )
                FormNumberField(
                    label = "Public port (optional)",
                    value = listenPort,
                    onValue = { listenPort = it; listenError = null },
                    supporting = listenError
                        ?: "${LISTEN_PORTS.first}-${LISTEN_PORTS.last}. A free port is assigned if empty."
                )
                FormTextField(
                    label = "Name (optional)",
                    value = name,
                    onValue = { name = it }
                )
            }
        },
        confirmButton = { TextButton(onClick = { submit() }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
