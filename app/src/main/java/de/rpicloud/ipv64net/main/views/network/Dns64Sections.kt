package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.helper.myIp
import de.rpicloud.ipv64net.main.views.FormSwitchRow
import de.rpicloud.ipv64net.main.views.FormTextField
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.InfoRow
import de.rpicloud.ipv64net.main.views.common.SectionHeader
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.sr.ipv64.model.Dns64Blocklist
import de.sr.ipv64.model.Dns64Client
import de.sr.ipv64.model.Dns64Rule
import de.sr.ipv64.model.Dns64RuleAction
import de.sr.ipv64.model.Dns64Server
import de.sr.ipv64.model.Dns64Source
import de.sr.ipv64.model.Dns64SourceKind
import de.sr.ipv64.model.IpFamily
import kotlinx.coroutines.launch

// =============================================================================================
// Listenzeile
// =============================================================================================

// Zeile der Client-Liste: nur Daten aus der Liste, keine Einzelabfragen
@Composable
fun Dns64ItemView(client: Dns64Client, onClick: (Dns64Client) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = { onClick(client) }) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = client.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(client.state.dns64Label(), client.state.dns64Color())
            }
            val filter = if (client.blockingEnabled == true) {
                if (client.blocklists.isEmpty()) "Filtering on" else "Filtering: " + client.blocklists.joinToString(", ") { it.value }
            } else "Filtering off"
            Text(filter, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val total = client.queriesTotal
            if (total != null) {
                Text(
                    "$total queries · ${client.queriesBlocked ?: 0} blocked",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// =============================================================================================
// Detail-Abschnitte (LazyListScope-Erweiterungen, Schlüssel sind eindeutig)
// =============================================================================================

// Kopf: Name, Zustand, Fortschritt und Statistik
fun LazyListScope.dns64HeaderItem(client: Dns64Client) {
    item(key = "dns64_header") {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        client.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    StatusChip(client.state.dns64Label(), client.state.dns64Color())
                }
                if (client.isApplying()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        "Changes are being applied. This usually takes a few seconds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                InfoRow("Client ID", client.clientId, copyable = true, monospace = true)
                val total = client.queriesTotal
                val blocked = client.queriesBlocked
                InfoRow("Queries", total?.toString())
                if (blocked != null) {
                    val share = if (total != null && total > 0) " (${blocked * 100 / total} %)" else ""
                    InfoRow("Blocked queries", "$blocked$share")
                }
                InfoRow("Created", client.createdAt?.formatGermanTime())
            }
        }
    }
}

// Resolver-Endpunkte; [onUsePrivateDns] bekommt den gewählten Server (kopiert DoT-Host und öffnet Einstellungen)
fun LazyListScope.dns64ResolverItems(client: Dns64Client, onUsePrivateDns: (Dns64Server) -> Unit) {
    item(key = "resolver_header") { SectionHeader("Resolver endpoints") }
    if (client.servers.isEmpty()) {
        item(key = "resolver_empty") {
            EmptyState(
                if (client.isApplying()) "The resolver addresses appear as soon as the client is ready."
                else "No resolver servers available."
            )
        }
        return
    }
    if (client.servers.any { !it.dot.isNullOrBlank() }) {
        item(key = "resolver_hint") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Use as Android Private DNS", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Copy the DNS over TLS host below, open Settings > Network & internet > Private DNS, " +
                                "choose \"Private DNS provider hostname\" and paste it. Needs Android 9 or newer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    items(client.servers.size, key = { "server_$it" }) { index ->
        ServerCard(client.servers[index], index, onUsePrivateDns)
    }
}

@Composable
private fun ServerCard(server: Dns64Server, index: Int, onUsePrivateDns: (Dns64Server) -> Unit) {
    var stamps by rememberSaveable(index) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(server.displayName(index), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            server.dot?.takeIf { it.isNotBlank() }?.let { InfoRow("DNS over TLS (Private DNS host)", it, copyable = true, monospace = true) }
            server.doh?.takeIf { it.isNotBlank() }?.let { InfoRow("DNS over HTTPS", it, copyable = true, monospace = true) }
            server.doq?.takeIf { it.isNotBlank() }?.let { InfoRow("DNS over QUIC", it, copyable = true, monospace = true) }
            server.ipv6?.takeIf { it.isNotBlank() }?.let { InfoRow("Personal IPv6", it, copyable = true, monospace = true) }
            server.ipv4Shared?.takeIf { it.isNotBlank() }?.let { InfoRow("Shared IPv4 (needs source address)", it, copyable = true, monospace = true) }
            server.ipv6Shared?.takeIf { it.isNotBlank() }?.let { InfoRow("Shared IPv6 (needs source address)", it, copyable = true, monospace = true) }
            if (!server.dot.isNullOrBlank()) {
                OutlinedButton(onClick = { onUsePrivateDns(server) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Use as Android Private DNS")
                }
            }
            val s = server.stamps
            if (s != null && (!s.doh.isNullOrBlank() || !s.dot.isNullOrBlank() || !s.doq.isNullOrBlank())) {
                TextButton(onClick = { stamps = !stamps }) {
                    Icon(
                        painter = painterResource(if (stamps) R.drawable.expand_less_24px else R.drawable.expand_more_24px),
                        contentDescription = null
                    )
                    Text(if (stamps) "Hide DNS stamps" else "Show DNS stamps", modifier = Modifier.padding(start = 4.dp))
                }
                if (stamps) {
                    s.doh?.takeIf { it.isNotBlank() }?.let { InfoRow("DoH stamp", it, copyable = true, monospace = true) }
                    s.dot?.takeIf { it.isNotBlank() }?.let { InfoRow("DoT stamp", it, copyable = true, monospace = true) }
                    s.doq?.takeIf { it.isNotBlank() }?.let { InfoRow("DoQ stamp", it, copyable = true, monospace = true) }
                }
            }
        }
    }
}

// Filter und Blocklisten
fun LazyListScope.dns64FilteringItems(
    client: Dns64Client,
    unavailable: Set<Dns64Blocklist>,
    enabled: Boolean,
    onBlocking: (Boolean) -> Unit,
    onBlocklist: (Dns64Blocklist, Boolean) -> Unit
) {
    item(key = "filter_header") { SectionHeader("Filtering") }
    item(key = "filter_card") {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FormSwitchRow(
                    label = "Filtering",
                    checked = client.blockingEnabled == true,
                    onChange = onBlocking,
                    supporting = "Blocks domains from the selected blocklists.",
                    enabled = enabled
                )
                HorizontalDivider()
                Dns64Blocklist.entries.forEach { list ->
                    val notInPlan = list in unavailable
                    FormSwitchRow(
                        label = list.dns64Title(),
                        checked = list in client.blocklists,
                        onChange = { onBlocklist(list, it) },
                        supporting = list.dns64Description() + when {
                            notInPlan -> " Not in your plan."
                            list.isPaidOnly() -> " Not available on the free plan."
                            else -> ""
                        },
                        enabled = enabled && !notInPlan
                    )
                }
            }
        }
    }
}

// Eigene Erlauben-/Sperren-Regeln
fun LazyListScope.dns64RuleItems(
    client: Dns64Client,
    enabled: Boolean,
    onAdd: () -> Unit,
    onDelete: (Dns64Rule) -> Unit
) {
    item(key = "rules_header") { Dns64SectionRow("Rules", "Add rule", enabled, onAdd) }
    if (client.rules.isEmpty()) {
        item(key = "rules_empty") { EmptyState("No rules. Allow or block single domains.") }
    }
    items(client.rules.size, key = { "rule_${client.rules[it].id ?: it}" }) { index ->
        val rule = client.rules[index]
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        rule.domain.orEmpty().ifBlank { "Unknown domain" },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip(rule.action.dns64ActionLabel(), rule.action.dns64ActionColor())
                        if (rule.state == de.sr.ipv64.model.Dns64State.APPLYING) {
                            StatusChip("Applying", rule.state.dns64Color())
                        }
                    }
                }
                IconButton(onClick = { onDelete(rule) }, enabled = enabled && rule.id != null) {
                    Icon(painter = painterResource(R.drawable.delete_24px), contentDescription = "Delete rule")
                }
            }
        }
    }
}

// Quell-Adressen: feste IPs und DynDNS-Records (je höchstens 5)
fun LazyListScope.dns64SourceItems(
    client: Dns64Client,
    enabled: Boolean,
    onAddIp: () -> Unit,
    onAddRecord: () -> Unit,
    onDelete: (Dns64Source) -> Unit
) {
    val ips = client.sources.ofKind(Dns64SourceKind.IP)
    val records = client.sources.ofKind(Dns64SourceKind.RECORD)

    item(key = "sources_header") { SectionHeader("Source addresses") }
    item(key = "sources_hint") {
        Text(
            "Source addresses let the shared resolver IPs recognize your queries and assign them to this client. " +
                    "Not needed for DNS over TLS/HTTPS/QUIC with your personal address.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    item(key = "sources_ip_header") {
        Dns64SectionRow(
            "Fixed IPs (${ips.size}/$DNS64_MAX_SOURCES_PER_KIND)", "Add IP",
            enabled && ips.size < DNS64_MAX_SOURCES_PER_KIND, onAddIp
        )
    }
    if (ips.isEmpty()) item(key = "sources_ip_empty") { EmptyState("No fixed IP addresses.") }
    items(ips.size, key = { "source_ip_${ips[it].id ?: it}" }) { Dns64SourceRow(ips[it], enabled, onDelete) }

    item(key = "sources_record_header") {
        Dns64SectionRow(
            "DynDNS records (${records.size}/$DNS64_MAX_SOURCES_PER_KIND)", "Add record",
            enabled && records.size < DNS64_MAX_SOURCES_PER_KIND, onAddRecord
        )
    }
    if (records.isEmpty()) item(key = "sources_record_empty") { EmptyState("No DynDNS records.") }
    items(records.size, key = { "source_record_${records[it].id ?: it}" }) { Dns64SourceRow(records[it], enabled, onDelete) }
}

@Composable
private fun Dns64SourceRow(source: Dns64Source, enabled: Boolean, onDelete: (Dns64Source) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(source.displayText(), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!source.fqdn.isNullOrBlank() && !source.address.isNullOrBlank()) {
                    Text(
                        source.address.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = { onDelete(source) }, enabled = enabled && source.id != null) {
                Icon(painter = painterResource(R.drawable.delete_24px), contentDescription = "Delete source")
            }
        }
    }
}

@Composable
private fun Dns64SectionRow(title: String, addLabel: String, addEnabled: Boolean, onAdd: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionHeader(title, modifier = Modifier.weight(1f))
        TextButton(onClick = onAdd, enabled = addEnabled) {
            Icon(painter = painterResource(R.drawable.add_24px), contentDescription = null)
            Text(addLabel, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

// =============================================================================================
// Dialoge
// =============================================================================================

// Name eingeben (Anlegen: optional, Umbenennen: Pflicht)
@Composable
fun Dns64NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    required: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    val trimmed = name.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FormTextField(
                label = if (required) "Name" else "Name (optional)",
                value = name,
                onValue = { name = it },
                placeholder = "e.g. Family"
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(trimmed) }, enabled = !required || trimmed.isNotEmpty()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// Regel hinzufügen: Domain und Aktion
@Composable
fun Dns64RuleDialog(
    existingDomains: Set<String>,
    onConfirm: (domain: String, action: Dns64RuleAction) -> Unit,
    onDismiss: () -> Unit
) {
    var domain by remember { mutableStateOf("") }
    var action by remember { mutableStateOf(Dns64RuleAction.BLOCK) }
    val normalized = normalizeDns64Domain(domain)
    val duplicate = normalized != null && normalized in existingDomains
    val error = when {
        domain.isNotBlank() && normalized == null -> "Enter a valid domain, e.g. example.com"
        duplicate -> "A rule for this domain already exists."
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FormTextField(
                    label = "Domain",
                    value = domain,
                    onValue = { domain = it },
                    placeholder = "example.com",
                    supporting = error
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dns64RuleAction.entries.forEach { a ->
                        FilterChip(
                            selected = action == a,
                            onClick = { action = a },
                            label = { Text(a.dns64ActionLabel()) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { normalized?.let { onConfirm(it, action) } },
                enabled = normalized != null && !duplicate
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// Feste IP als Quelle hinzufügen, mit "Meine IP"-Schnellauswahl
@Composable
fun Dns64IpDialog(
    existingIps: Set<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var ip by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var lookupFailed by remember { mutableStateOf(false) }
    val trimmed = ip.trim()
    val valid = isIpLiteral(trimmed)
    val duplicate = valid && trimmed in existingIps
    val error = when {
        trimmed.isNotEmpty() && !valid -> "Enter a valid IPv4 or IPv6 address."
        duplicate -> "This address is already a source."
        lookupFailed -> "Could not determine your IP address."
        else -> null
    }

    fun fill(family: IpFamily) {
        if (loading) return
        loading = true
        lookupFailed = false
        scope.launch {
            try {
                val found = myIp(family)
                if (found != null) ip = found else lookupFailed = true
            } finally {
                loading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add IP address") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FormTextField(
                    label = "IP address",
                    value = ip,
                    onValue = { ip = it; lookupFailed = false },
                    placeholder = "203.0.113.10",
                    supporting = error
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { fill(IpFamily.IPV4) }, enabled = !loading) { Text("My IPv4") }
                    OutlinedButton(onClick = { fill(IpFamily.IPV6) }, enabled = !loading) { Text("My IPv6") }
                }
                if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(trimmed) }, enabled = valid && !duplicate && !loading) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
