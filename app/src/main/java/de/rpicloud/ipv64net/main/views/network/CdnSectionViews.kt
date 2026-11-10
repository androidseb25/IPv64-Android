package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.main.views.common.ActionSheet
import de.rpicloud.ipv64net.main.views.common.ActionTile
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.main.views.FormTextField
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.GroupDivider
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.SectionHeader
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.common.InfoGroup
import de.rpicloud.ipv64net.main.views.common.InfoItem
import de.rpicloud.ipv64net.main.views.common.InfoRows
import de.rpicloud.ipv64net.main.views.common.GroupText
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
    val server = cdn.server
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ListGroup {
            ListRow(
                title = cdn.displayName(),
                subtitle = server?.let { listOfNotNull(it.name, it.hostname).filter { s -> s.isNotBlank() }.joinToString(" · ") }
                    ?.takeIf { it.isNotEmpty() },
                trailing = {
                    if (cdn.active == true) StatusChip("Active", colorResource(R.color.ipv64_green))
                    else StatusChip("Inactive", colorResource(R.color.ipv64_gray))
                }
            )
            if (cdn.isBusy()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                val tasks = cdn.pending.map { it.label() }.distinct()
                GroupText(
                    (if (tasks.isEmpty()) "Applying changes" else tasks.joinToString(", ")) +
                            ". This usually takes a few seconds."
                )
            }
        }
        InfoGroup(
            title = "Details",
            rows = listOf(
                InfoItem("Server IPv4", server?.ipv4, copyable = true, monospace = true),
                InfoItem("Server IPv6", server?.ipv6, copyable = true, monospace = true),
                InfoItem("Records via CDN", if (cdn.dnsRecordsEnabled == true) "Yes" else "No"),
                InfoItem("Traffic", cdn.trafficMb.trafficText()),
                InfoItem("Added", cdn.createdAt?.formatGermanTime())
            )
        )
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
    val caching = proxy?.caching
    val acl = proxy?.accessList
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ListGroup(title = "Reverse proxy") {
            ListRow(title = "Status", trailing = { StatusChip(state.proxyLabel(), state.proxyColor()) })
            GroupDivider()
            InfoRows(
                listOf(
                    InfoItem("Frontend", proxy?.frontend.frontendLabel()),
                    InfoItem("Origin server", proxy?.backend?.let { "${it.scheme.schemeLabel()} · port ${it.port ?: "–"}" }),
                    InfoItem("WebSocket", if (proxy?.websocket == true) "On" else "Off"),
                    InfoItem("Certificate", proxy?.certificate.certificateLabel()),
                    InfoItem(
                        "Cache",
                        if (caching?.enabled == true) "On · ${caching.timeMinutes ?: "–"} min · ${caching.sizeMb ?: "–"} MB" else "Off"
                    ),
                    InfoItem("Access list", if (acl?.enabled == true) "On · ${acl.mode.accessModeLabel()}" else "Off")
                )
            )
        }
        val canEnable = detail.dnsRecordsEnabled == true
        if (!proxyOn && state != CdnProxyState.DISABLING && !canEnable) {
            Text(
                "Enable the CDN for at least one record before you switch the proxy on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (proxyOn) {
                ActionTile(R.drawable.cloud_off_24px, "Switch off", onToggleProxy, Modifier.weight(1f), enabled = enabled && !lockTasks)
            } else {
                ActionTile(
                    R.drawable.cloud_24px, "Switch on", onToggleProxy, Modifier.weight(1f),
                    enabled = enabled && !lockTasks && canEnable && state != CdnProxyState.DISABLING
                )
            }
            ActionTile(R.drawable.tune_24px, "Settings", onSettings, Modifier.weight(1f), enabled = enabled)
            if (proxyOn && proxy?.frontend != CdnFrontend.HTTP) {
                ActionTile(R.drawable.autorenew_24px, "Renew certificate", onRenewCertificate, Modifier.weight(1f), enabled = enabled && !lockTasks)
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
    val canEnable = detail.dnsRecordsEnabled == true
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ListGroup(title = "Port mapper") {
            ListRow(
                title = "Status",
                subtitle = "Forwards public ports of the CDN server (TCP/UDP) to ports on your server.",
                subtitleMaxLines = 3,
                trailing = { StatusChip(state.portmapperLabel(), state.portmapperColor()) }
            )
        }
        if (!isOn && !canEnable) {
            Text(
                "Enable the CDN for at least one record before you switch the port mapper on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (isOn) {
                ActionTile(R.drawable.cloud_off_24px, "Switch off", onTogglePortmapper, Modifier.weight(1f), enabled = enabled && !lockTasks)
            } else {
                ActionTile(
                    R.drawable.alt_route_24px, "Switch on", onTogglePortmapper, Modifier.weight(1f),
                    enabled = enabled && !lockTasks && canEnable && state != CdnPortmapperState.DISABLING &&
                            state != CdnPortmapperState.SUSPENDED_TRAFFIC_LIMIT
                )
            }
            ActionTile(R.drawable.add_24px, "Mapping", onAddMapping, Modifier.weight(1f), enabled = enabled)
        }
    }
}

@Composable
private fun MappingRow(mapping: CdnMapping, enabled: Boolean, onDelete: () -> Unit) {
    ListRow(
        title = mapping.name?.takeIf { it.isNotBlank() } ?: "Port mapping",
        subtitle = listOfNotNull(
            "Public port ${mapping.listenPort ?: "–"} → port ${mapping.targetPort ?: "–"} (${mapping.transport.transportLabel()})",
            mapping.fqdn?.takeIf { it.isNotBlank() }
        ).joinToString("\n"),
        showChevron = false,
        trailing = {
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(painter = painterResource(R.drawable.delete_24px), contentDescription = "Delete port mapping")
            }
        }
    )
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
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionHeader(title, modifier = Modifier.weight(1f).padding(start = 4.dp))
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
    item(key = "records_list") {
        ListGroup {
            records.forEachIndexed { index, record ->
                if (index > 0) GroupDivider()
                CdnRecordRow(record, enabled, onEnable, onDisable)
            }
        }
    }
}

@Composable
private fun CdnRecordRow(record: Record, enabled: Boolean, onEnable: (Record) -> Unit, onDisable: (Record) -> Unit) {
    ListRow(
        title = record.cdnTitle(),
        subtitle = record.content.orEmpty().takeIf { it.isNotEmpty() },
        subtitleMaxLines = 1,
        trailing = { StatusChip(record.cdnStateLabel(), record.cdnStateColor()) }
    )
    val canEnable = record.canEnableCdn()
    val canDisable = !canEnable && record.canDisableCdn()
    if (record.pendingDns == true || canEnable || canDisable) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (record.pendingDns == true) StatusChip("DNS updating", colorResource(R.color.ipv64_orange))
            Spacer(Modifier.weight(1f))
            if (canEnable) TextButton(onClick = { onEnable(record) }, enabled = enabled) { Text("Enable") }
            if (canDisable) TextButton(onClick = { onDisable(record) }, enabled = enabled) { Text("Disable") }
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(
                settingsText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(onClick = onAdd, enabled = enabled && entries != null) {
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
    item(key = "acl_list") {
        ListGroup {
            entries.forEachIndexed { index, entry ->
                if (index > 0) GroupDivider()
                ListRow(
                    title = entry.label(),
                    monospaceTitle = true,
                    showChevron = false,
                    trailing = {
                        IconButton(onClick = { onRemove(entry) }, enabled = enabled) {
                            Icon(painter = painterResource(R.drawable.close_24px), contentDescription = "Remove entry")
                        }
                    }
                )
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
    ActionSheet(
        title = "Route through CDN",
        onDismiss = onDismiss,
        confirmLabel = "Enable",
        onConfirm = { onConfirm(mode) }
    ) {
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
}

// IP oder Netz (CIDR) für die Zugriffsliste
@Composable
fun CdnAddAddressDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var address by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ActionSheet(
        title = "Add to access list",
        onDismiss = onDismiss,
        confirmLabel = "Add",
        onConfirm = {
            val problem = addressError(address)
            if (problem != null) error = problem else onConfirm(address.trim())
        }
    ) {
        FormTextField(
            label = "IP address or network",
            value = address,
            onValue = { address = it; error = null },
            placeholder = "203.0.113.0/24",
            supporting = error ?: "IPv4 or IPv6, optionally with prefix length (CIDR)."
        )
    }
}
