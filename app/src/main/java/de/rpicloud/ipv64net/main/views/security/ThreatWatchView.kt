package de.rpicloud.ipv64net.main.views.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab as M3Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.main.views.common.ConfirmDialog
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.NotAvailableView
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.SectionHeader
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64ApiException
import de.sr.ipv64.model.ThreatWatch
import de.sr.ipv64.model.ThreatWatchAlert
import de.sr.ipv64.model.ThreatWatchNetwork
import kotlin.time.Instant

private const val MAX_ALERT_PAGES = 3
private const val ALERT_PAGE_SIZE = 100

// Auswahl des Zeitraums für Alarme (Tage). Die API liefert die ältesten zuerst; ein Zeitpunkt als Startwert
// begrenzt die Menge, damit die höchstens 3 Seiten die neuesten Alarme des Zeitraums abdecken
private val ALERT_PERIODS = listOf(1 to "24 h", 7 to "7 days", 30 to "30 days")

/**
 * Frühwarnung. Tab "Networks": eine Anfrage (watch()). Tab "Alerts": lazy beim ersten Öffnen, höchstens
 * 3 sequentielle Seiten, neueste zuerst.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThreatWatchView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var periodDays by rememberSaveable { mutableIntStateOf(7) }
    var watch by remember { mutableStateOf<ThreatWatch?>(null) }
    var alerts by remember { mutableStateOf<List<ThreatWatchAlert>>(emptyList()) }
    var alertsLoaded by remember { mutableStateOf(false) }
    var alertsNote by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    var cidr by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var cidrError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ThreatWatchNetwork?>(null) }

    fun loadWatch(pull: Boolean = false) {
        if (pull) refreshing = true
        req.launch(
            scope,
            spinner = if (pull) null else "Loading...",
            retry = { loadWatch(true) },
            planGate = true,
            onFinally = { refreshing = false }
        ) {
            val client = IPv64Client.get(ctx)
            watch = ipv64Retrying { client.threat.watch() }
        }
    }

    fun loadAlerts(pull: Boolean = false) {
        if (pull) refreshing = true
        val days = periodDays
        req.launch(
            scope,
            spinner = if (pull) null else "Loading alerts...",
            retry = { loadAlerts(true) },
            onFinally = { refreshing = false }
        ) {
            val client = IPv64Client.get(ctx)
            val nowSec = System.currentTimeMillis() / 1000
            val periodStart = nowSec - days * 86_400L
            var windowStart = periodStart
            var page: List<ThreatWatchAlert> = emptyList()
            var full = false
            // Die API liefert die ältesten zuerst (100 je Abruf). Ist die Seite voll, wird der Startpunkt mit den
            // verbleibenden Anfragen (insgesamt höchstens 3) Richtung jetzt verschoben, damit die neuesten Alarme
            // erscheinen: Das Fenster wird auf die Hälfte der Zeitspanne geschätzt, die die ersten 100 Alarme belegten.
            for (request in 1..MAX_ALERT_PAGES) {
                val result = ipv64Retrying { client.threat.watchAlerts(Instant.fromEpochSeconds(windowStart).toString()) }
                page = result.alerts
                full = page.size >= ALERT_PAGE_SIZE
                if (!full || request == MAX_ALERT_PAGES) break
                val lastSec = page.maxOf { (it.createdAt?.toEpochMilliseconds() ?: 0L) / 1000 }
                val span = if (lastSec > windowStart) lastSec - windowStart else nowSec - windowStart
                val next = nowSec - span / 2
                if (next <= windowStart || next >= nowSec) break
                windowStart = next
            }
            val seen = HashSet<Long>()
            alerts = page.filter { a -> a.id == null || seen.add(a.id!!) }.sortedWith(
                compareByDescending<ThreatWatchAlert> { it.createdAt?.toEpochMilliseconds() ?: 0L }
                    .thenByDescending { it.id ?: 0L }
            )
            val sinceText = Instant.fromEpochSeconds(windowStart).formatGermanTime()
            alertsNote = when {
                full -> "Too many alerts: only the oldest 100 alerts since $sinceText are shown. Choose a shorter period to see the newest ones."
                windowStart > periodStart -> "Too many alerts in this period: showing the newest ones since $sinceText."
                else -> null
            }
            alertsLoaded = true
        }
    }

    fun addNetwork() {
        if (req.isBusy) return
        val current = watch
        val parsed = parseNetwork(cidr)
        if (parsed == null) {
            cidrError = "Enter an IP address or a network in CIDR notation, e.g. 203.0.113.0/24."
            return
        }
        // Zu große Netze lokal ablehnen (der Server prüft zusätzlich)
        val minPrefix = if (parsed.isV6) current?.limits?.minPrefixV6 else current?.limits?.minPrefixV4
        if (minPrefix != null && parsed.prefix != null && parsed.prefix < minPrefix) {
            cidrError = "This network is too large. Use /$minPrefix or a longer prefix."
            return
        }
        cidrError = null
        val newCidr = cidr.trim()
        val newLabel = label.trim().ifEmpty { null }
        req.launch(
            scope,
            spinner = "Adding...",
            onError = { e ->
                if (e is IPv64ApiException) {
                    val text = when (e.code) {
                        "invalid_cidr" -> "Not a valid IP address or network."
                        "prefix_too_large" -> "This network is too large for your account."
                        "not_public" -> "Only public networks can be watched."
                        "duplicate" -> "This network is already watched."
                        else -> if (e.isConflict) "This network is already watched." else null
                    }
                    if (text != null) {
                        cidrError = text
                        true
                    } else if (e.code == "limit_reached") {
                        req.message("Limit reached", e.detail ?: "You cannot watch more networks with your plan.")
                        true
                    } else false
                } else false
            }
        ) {
            val client = IPv64Client.get(ctx)
            val created = ipv64Retrying { client.threat.addWatch(newCidr, newLabel) }
            // Rückgabe übernehmen statt neu zu laden
            watch = watch?.let { it.copy(networks = it.networks.filterNot { n -> n.id != null && n.id == created.id } + created) }
            cidr = ""
            label = ""
        }
    }

    fun deleteNetwork(n: ThreatWatchNetwork) {
        val id = n.id ?: return
        req.launch(
            scope,
            spinner = "Deleting...",
            onError = { e ->
                // Bereits gelöscht: aus der Liste entfernen
                if (e is IPv64ApiException && e.isNotFound) {
                    watch = watch?.let { w -> w.copy(networks = w.networks.filter { it.id != id }) }
                    true
                } else false
            }
        ) {
            val client = IPv64Client.get(ctx)
            ipv64Retrying { client.threat.deleteWatch(id) }
            watch = watch?.let { w -> w.copy(networks = w.networks.filter { it.id != id }) }
        }
    }

    LaunchedEffect(Unit) { loadWatch() }
    // Alarme erst beim ersten Öffnen des Tabs bzw. nach einem Zeitraumwechsel (alertsLoaded = false) laden
    LaunchedEffect(tab, watch != null, periodDays) {
        if (tab == 1 && !alertsLoaded && watch != null && req.notAvailable == null) loadAlerts()
    }

    ScreenScaffold(Tabs.getLabel(Tab.threat_watch), navController, mainPadding) {
        val notAvailable = req.notAvailable
        val limits = watch?.limits
        if (notAvailable != null) {
            NotAvailableView("Early warning", notAvailable)
        } else if (limits?.max == 0) {
            NotAvailableView("Early warning", "Your plan does not include watched networks.")
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                PrimaryTabRow(selectedTabIndex = tab) {
                    M3Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Networks") })
                    M3Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Alerts") })
                }
                if (tab == 0) {
                    RefreshableList(refreshing = refreshing, onRefresh = { loadWatch(true) }) {
                        val w = watch
                        if (w == null) {
                            item(key = "loading") { EmptyState(if (req.isBusy) "Loading..." else "No data.") }
                        } else {
                            val max = w.limits?.max
                            val full = max != null && w.networks.size >= max
                            item(key = "limits") {
                                SecurityCard {
                                    Text("Early warning", style = MaterialTheme.typography.titleMedium)
                                    KeyValueLine(
                                        "Watched networks",
                                        if (max != null) "${w.networks.size} / $max" else "${w.networks.size}"
                                    )
                                    w.limits?.minPrefixV4?.let { KeyValueLine("Shortest IPv4 prefix", "/$it") }
                                    w.limits?.minPrefixV6?.let { KeyValueLine("Shortest IPv6 prefix", "/$it") }
                                    Text(
                                        "Unverified networks only report IPs that are on the blocklist. " +
                                                "Verified networks report every sighting.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            item(key = "add") {
                                SecurityCard {
                                    Text("Add network", style = MaterialTheme.typography.titleSmall)
                                    OutlinedTextField(
                                        value = cidr,
                                        onValueChange = { cidr = it; cidrError = null },
                                        label = { Text("IP or network (CIDR)") },
                                        singleLine = true,
                                        isError = cidrError != null,
                                        supportingText = cidrError?.let { { Text(it) } },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = label,
                                        onValueChange = { label = it },
                                        label = { Text("Label (optional)") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { if (!full && cidr.isNotBlank() && !req.isBusy) addNetwork() }),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(onClick = { addNetwork() }, enabled = cidr.isNotBlank() && !full && !req.isBusy) {
                                        Text("Add")
                                    }
                                    if (full) {
                                        Text(
                                            "You reached the limit of watched networks. Delete one to add another.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            item(key = "networks_header") { SectionHeader("Watched networks") }
                            if (w.networks.isEmpty()) {
                                item(key = "networks_empty") { EmptyState("No networks yet.") }
                            } else {
                                itemsIndexed(w.networks, key = { i, n -> n.id ?: -(i + 1L) }) { _, n ->
                                    WatchNetworkItem(n, onDelete = { deleteTarget = n })
                                }
                            }
                            item(key = "dyndns_header") { SectionHeader("Automatically watched DynDNS IPs") }
                            if (w.dyndns.isEmpty()) {
                                item(key = "dyndns_empty") { EmptyState("None. The current A/AAAA records of your domains are watched automatically.") }
                            } else {
                                itemsIndexed(w.dyndns, key = { i, d -> "dyndns_${i}_${d.ip}" }) { _, d ->
                                    SecurityCard {
                                        Text(
                                            d.ip ?: "–",
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            d.domain ?: "–",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    RefreshableList(refreshing = refreshing, onRefresh = { loadAlerts(true) }) {
                        item(key = "period") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ALERT_PERIODS.forEach { (days, name) ->
                                    FilterChip(
                                        selected = periodDays == days,
                                        onClick = {
                                            if (periodDays != days) {
                                                periodDays = days
                                                alertsLoaded = false
                                                alerts = emptyList()
                                            }
                                        },
                                        label = { Text(name) },
                                        enabled = !req.isBusy
                                    )
                                }
                            }
                        }
                        if (!alertsLoaded) {
                            item(key = "alerts_loading") { EmptyState(if (req.isBusy) "Loading..." else "No data.") }
                        } else if (alerts.isEmpty()) {
                            item(key = "alerts_empty") { EmptyState("No alerts in this period.") }
                        } else {
                            alertsNote?.let { note ->
                                item(key = "alerts_truncated") {
                                    Text(
                                        note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            itemsIndexed(alerts, key = { i, a -> a.id ?: -(i + 1L) }) { _, a ->
                                ThreatAlertItemView(a, onClick = {
                                    val ip = a.ip
                                    if (ip != null) navController.navigateWithArgs(Tab.threat_lookup, NavKeys.THREAT_LOOKUP_IP to ip)
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { n ->
        ConfirmDialog(
            title = "Stop watching network?",
            text = "${n.cidr ?: "This network"} will no longer be monitored. Earlier alerts are kept.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { deleteTarget = null; deleteNetwork(n) },
            onDismiss = { deleteTarget = null }
        )
    }
    RequestStateHost(req)
}

@Composable
private fun WatchNetworkItem(n: ThreatWatchNetwork, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(n.cidr ?: "–", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                n.label?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                n.createdAt?.let {
                    Text(
                        "Added ${it.formatRelative()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (n.verified == true) StatusChip("Verified", colorResource(R.color.ipv64_green))
                else StatusChip("Unverified", colorResource(R.color.ipv64_gray))
            }
            IconButton(onClick = onDelete) {
                Icon(painter = painterResource(id = R.drawable.delete_24px), contentDescription = "Delete network")
            }
        }
    }
}
