package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.main.views.FormTextField
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.InfoRow
import de.rpicloud.ipv64net.main.views.common.SectionHeader
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.sr.ipv64.model.AccessListEntry
import de.sr.ipv64.model.CdnDomain
import de.sr.ipv64.model.CdnDomainDetail
import de.sr.ipv64.model.CdnFrontend
import de.sr.ipv64.model.CdnMapping
import de.sr.ipv64.model.CdnPortmapperState
import de.sr.ipv64.model.CdnProxyState
import de.sr.ipv64.model.CdnRecordMode
import de.sr.ipv64.model.Record

// =============================================================================================
// Kopf: Status, Server, Traffic
// =============================================================================================

@Composable
fun CdnHeaderCard(cdn: CdnDomain) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    cdn.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (cdn.active == true) StatusChip("Active", colorResource(R.color.ipv64_green))
                else StatusChip("Inactive", colorResource(R.color.ipv64_gray))
            }
            if (cdn.isBusy()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                val tasks = cdn.pending.map { it.label() }.distinct()
                Text(
                    (if (tasks.isEmpty()) "Applying changes" else tasks.joinToString(", ")) +
                            ". This usually takes a few seconds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val server = cdn.server
            InfoRow(
                "CDN server",
                server?.let { listOfNotNull(it.name, it.hostname).filter { s -> s.isNotBlank() }.joinToString(" · ") }
            )
            InfoRow("Server IPv4", server?.ipv4, copyable = true, monospace = true)
            InfoRow("Server IPv6", server?.ipv6, copyable = true, monospace = true)
            InfoRow("Records via CDN", if (cdn.dnsRecordsEnabled == true) "Yes" else "No")
            InfoRow("Traffic", cdn.trafficMb.trafficText())
            InfoRow("Added", cdn.createdAt?.formatGermanTime())
        }
    }
}

// =============================================================================================
// Reverse Proxy
// =============================================================================================

@Composable
fun CdnProxyCard(
    detail: CdnDomainDetail,
    enabled: Boolean,
    lockTasks: Boolean,
    onToggleProxy: () -> Unit,
    onRenewCertificate: () -> Unit,
    onSettings: () -> Unit
) {
    val proxy = detail.proxy
    val state = proxy?.state
    val proxyOn = state == CdnProxyState.ON
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Reverse proxy", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                StatusChip(state.proxyLabel(), state.proxyColor())
            }
            InfoRow("Frontend", proxy?.frontend.frontendLabel())
            InfoRow(
                "Origin server",
                proxy?.backend?.let { "${it.scheme.schemeLabel()} · port ${it.port ?: "–"}" }
            )
            InfoRow("WebSocket", if (proxy?.websocket == true) "On" else "Off")
            InfoRow("Certificate", proxy?.certificate.certificateLabel())
            val caching = proxy?.caching
            InfoRow(
                "Cache",
                if (caching?.enabled == true) "On · ${caching.timeMinutes ?: "–"} min · ${caching.sizeMb ?: "–"} MB" else "Off"
            )
            val acl = proxy?.accessList
            InfoRow(
                "Access list",
                if (acl?.enabled == true) "On · ${acl.mode.accessModeLabel()}" else "Off"
            )
            val canEnable = detail.dnsRecordsEnabled == true
            if (!proxyOn && state != CdnProxyState.DISABLING && !canEnable) {
                Text(
                    "Enable the CDN for at least one record before you switch the proxy on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (proxyOn) {
                    OutlinedButton(onClick = onToggleProxy, enabled = enabled && !lockTasks, modifier = Modifier.weight(1f)) {
                        Text("Switch off")
                    }
                } else {
                    Button(
                        onClick = onToggleProxy,
                        enabled = enabled && !lockTasks && canEnable && state != CdnProxyState.DISABLING,
                        modifier = Modifier.weight(1f)
                    ) { Text("Switch on") }
                }
                OutlinedButton(onClick = onSettings, enabled = enabled) { Text("Settings") }
            }
            if (proxyOn && proxy?.frontend != CdnFrontend.HTTP) {
                TextButton(onClick = onRenewCertificate, enabled = enabled && !lockTasks) { Text("Renew certificate") }
            }
        }
    }
}

// =============================================================================================
// Portmapper und Mappings
// =============================================================================================

@Composable
fun CdnPortmapperCard(
    detail: CdnDomainDetail,
    enabled: Boolean,
    lockTasks: Boolean,
    onTogglePortmapper: () -> Unit,
    onAddMapping: () -> Unit,
    onDeleteMapping: (CdnMapping) -> Unit
) {
    val state = detail.portmapper?.state
    val isOn = state == CdnPortmapperState.ON
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Port mapper", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                StatusChip(state.portmapperLabel(), state.portmapperColor())
            }
            Text(
                "Forwards public ports of the CDN server (TCP/UDP) to ports on your server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val canEnable = detail.dnsRecordsEnabled == true
            if (!isOn && !canEnable) {
                Text(
                    "Enable the CDN for at least one record before you switch the port mapper on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (isOn) {
                    OutlinedButton(onClick = onTogglePortmapper, enabled = enabled && !lockTasks, modifier = Modifier.weight(1f)) {
                        Text("Switch off")
                    }
                } else {
                    Button(
                        onClick = onTogglePortmapper,
                        enabled = enabled && !lockTasks && canEnable && state != CdnPortmapperState.DISABLING &&
                                state != CdnPortmapperState.SUSPENDED_TRAFFIC_LIMIT,
                        modifier = Modifier.weight(1f)
                    ) { Text("Switch on") }
                }
                OutlinedButton(onClick = onAddMapping, enabled = enabled) {
                    Icon(painter = painterResource(R.drawable.add_24px), contentDescription = null)
                    Text("Mapping", modifier = Modifier.padding(start = 4.dp))
                }
            }
            if (detail.mappings.isEmpty()) {
                Text(
                    "No port mappings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            detail.mappings.forEach { mapping ->
                MappingRow(mapping, enabled) { onDeleteMapping(mapping) }
            }
        }
    }
}

@Composable
private fun MappingRow(mapping: CdnMapping, enabled: Boolean, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                mapping.name?.takeIf { it.isNotBlank() } ?: "Port mapping",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Public port ${mapping.listenPort ?: "–"} → port ${mapping.targetPort ?: "–"} (${mapping.transport.transportLabel()})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            mapping.fqdn?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onDelete, enabled = enabled) {
            Icon(painter = painterResource(R.drawable.delete_24px), contentDescription = "Delete port mapping")
        }
    }
}

// =============================================================================================
// Aufklappbare Abschnitte (laden erst beim Aufklappen)
// =============================================================================================

@Composable
private fun SectionToggleHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean,
    onRefresh: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionHeader(title, modifier = Modifier.weight(1f))
        if (expanded && onRefresh != null) {
            IconButton(onClick = onRefresh, enabled = enabled) {
                Icon(painter = painterResource(R.drawable.refresh_24px), contentDescription = "Refresh")
            }
        }
        Icon(
            painter = painterResource(if (expanded) R.drawable.expand_less_24px else R.drawable.expand_more_24px),
            contentDescription = if (expanded) "Collapse" else "Expand",
            modifier = Modifier.padding(end = 8.dp)
        )
    }
}

fun LazyListScope.cdnRecordsSection(
    records: List<Record>?,
    expanded: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onEnable: (Record) -> Unit,
    onDisable: (Record) -> Unit
) {
    item(key = "records_header") {
        SectionToggleHeader("DNS records", expanded, onToggle, enabled, onRefresh)
    }
    if (!expanded) return
    if (records == null) {
        item(key = "records_loading") {
            Text("Loading records…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
        }
        return
    }
    if (records.isEmpty()) {
        item(key = "records_empty") { EmptyState("No A or AAAA records on this domain.") }
        return
    }
    items(records.size, key = { "cdn_record_${records[it].id ?: it}" }) { index ->
        val record = records[index]
        CdnRecordRow(record, enabled, onEnable, onDisable)
    }
}

@Composable
private fun CdnRecordRow(record: Record, enabled: Boolean, onEnable: (Record) -> Unit, onDisable: (Record) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    record.cdnTitle(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    record.content.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(record.cdnStateLabel(), record.cdnStateColor())
                    if (record.pendingDns == true) StatusChip("DNS updating", colorResource(R.color.ipv64_orange))
                }
            }
            when {
                record.canEnableCdn() ->
                    TextButton(onClick = { onEnable(record) }, enabled = enabled) { Text("Enable") }

                record.canDisableCdn() ->
                    TextButton(onClick = { onDisable(record) }, enabled = enabled) { Text("Disable") }
            }
        }
    }
}

fun LazyListScope.cdnAccessListSection(
    entries: List<AccessListEntry>?,
    settingsText: String,
    expanded: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (AccessListEntry) -> Unit
) {
    item(key = "acl_header") {
        SectionToggleHeader("Access list", expanded, onToggle, enabled, onRefresh)
    }
    if (!expanded) return
    item(key = "acl_info") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                settingsText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onAdd, enabled = enabled && entries != null) {
                Icon(painter = painterResource(R.drawable.add_24px), contentDescription = null)
                Text("Add", modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
    if (entries == null) {
        item(key = "acl_loading") {
            Text("Loading access list…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
        }
        return
    }
    if (entries.isEmpty()) {
        item(key = "acl_empty") { EmptyState("The access list is empty.") }
        return
    }
    items(entries.size, key = { "acl_${entries[it].id ?: it}" }) { index ->
        val entry = entries[index]
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    entry.label(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onRemove(entry) }, enabled = enabled) {
                    Icon(painter = painterResource(R.drawable.close_24px), contentDescription = "Remove entry")
                }
            }
        }
    }
}

// =============================================================================================
// Dialoge
// =============================================================================================

// Modus wählen, bevor ein Record über das CDN geleitet wird
@Composable
fun CdnEnableRecordDialog(
    record: Record,
    onConfirm: (CdnRecordMode) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(CdnRecordMode.FULL) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Route through CDN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(record.cdnTitle(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                CdnRecordMode.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = mode == option, role = Role.RadioButton) { mode = option },
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(selected = mode == option, onClick = null, modifier = Modifier.padding(top = 4.dp, end = 8.dp))
                        Column {
                            Text(option.modeTitle(), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                option.modeDescription(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    "The DNS entry changes immediately. Visitors reach the CDN as soon as DNS caches expire.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(mode) }) { Text("Enable") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// IP oder Netz (CIDR) für die Zugriffsliste
@Composable
fun CdnAddAddressDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var address by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to access list") },
        text = {
            FormTextField(
                label = "IP address or network",
                value = address,
                onValue = { address = it; error = null },
                placeholder = "203.0.113.0/24",
                supporting = error ?: "IPv4 or IPv6, optionally with prefix length (CIDR)."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val problem = addressError(address)
                if (problem != null) error = problem else onConfirm(address.trim())
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
