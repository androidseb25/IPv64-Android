package de.rpicloud.ipv64net.main.views.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.main.views.network.NodeListCache
import de.sr.ipv64.IPv64ApiException
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.Hc2Node
import de.sr.ipv64.model.ThreatFeedAction
import de.sr.ipv64.model.ThreatFeedChange
import de.sr.ipv64.model.ThreatStats
import kotlinx.coroutines.delay
import androidx.compose.ui.res.colorResource
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.main.views.common.ConfirmDialog
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.GroupDivider
import de.rpicloud.ipv64net.main.views.common.InfoRow
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.SecureWindowEffect
import de.rpicloud.ipv64net.main.views.common.SectionHeader
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.main.views.common.FormSection
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.rpicloud.ipv64net.ui.theme.chartPalette
import de.sr.ipv64.model.HoneypotListToken
import de.sr.ipv64.model.HoneypotSensor
import de.sr.ipv64.model.HoneypotStats

private const val LIST_FILE = "ipv64_blocklist_v64.txt"

/**
 * Honeypot: oben Kennzahlen von ipv64.net (threat.stats() 10 min gecacht), dann der Anteil der Nodes mit Honeypot-Modul
 * (Node-Liste 30 s gecacht), dann eigener Beitrag und Sensoren (stats() und sensors() je 2 min gecacht) sowie der
 * persönliche Listen-Token. Der Token wird erst nach Bestätigung geladen (der Abruf legt ihn an, falls er fehlt),
 * nur inline angezeigt (maskiert, sensibel kopierbar, Screenshots gesperrt) und nie gecacht.
 */
@Composable
fun HoneypotView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()

    var stats by remember { mutableStateOf<HoneypotStats?>(null) }
    var sensors by remember { mutableStateOf<List<HoneypotSensor>>(emptyList()) }
    var cloud by remember { mutableStateOf<ThreatStats?>(null) }
    var nodes by remember { mutableStateOf<List<Hc2Node>?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    var confirmToken by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf<HoneypotListToken?>(null) }
    // Wird beim Verbergen/Stoppen erhöht; verspätete Token-Antworten werden verworfen
    val tokenGen = remember { intArrayOf(0) }

    fun load(force: Boolean = false, pull: Boolean = false) {
        if (pull) refreshing = true
        val quiet = pull || (!force && HoneypotStatsCache.peek(ctx) != null)
        req.launch(
            scope,
            spinner = if (quiet) null else "Loading...",
            retry = { load(true) },
            onFinally = { refreshing = false }
        ) {
            stats = HoneypotStatsCache.get(ctx, force) { it.honeypot.stats() }
            sensors = HoneypotSensorsCache.get(ctx, force) { it.honeypot.sensors() }
            // Live-Daten und Node-Liste sind Zusatz: ein Fehler dort blockiert den eigenen Beitrag nicht
            try { cloud = ThreatStatsCache.get(ctx, force) { it.threat.stats() } } catch (_: IPv64Exception) { }
            try { nodes = NodeListCache.get(ctx, force) { it.nodes.list() } } catch (_: IPv64Exception) { }
            loaded = true
        }
    }

    fun loadToken() {
        // Kein retry-Lambda und keine Ablage im Cache: der Token bleibt nur im Screen-State
        val gen = tokenGen[0]
        req.launch(scope, spinner = "Loading token...") {
            val client = IPv64Client.get(ctx)
            val t = ipv64Retrying { client.honeypot.listToken() }
            if (tokenGen[0] == gen) token = t
        }
    }

    LaunchedEffect(Unit) { load() }

    // Token beim Wechsel in den Hintergrund wieder verbergen
    ClearOnStop { tokenGen[0]++; token = null }

    ScreenScaffold(Tabs.getLabel(Tab.honeypot), navController, mainPadding) {
        RefreshableList(
            refreshing = refreshing,
            onRefresh = { load(force = true, pull = true) },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            networkContent(cloud)
            cloud?.let { statsContent(it, withOverview = false) }
            nodes?.let { nodeShareContent(it) }
            if (!loaded) {
                item(key = "loading") { EmptyState(if (req.isBusy) "Loading..." else "No data.") }
            } else {
                stats?.let { honeypotContent(it, sensors) }
            }

            item(key = "token") {
                TokenCard(
                    token = token,
                    busy = req.isBusy,
                    onShow = { confirmToken = true },
                    onHide = { tokenGen[0]++; token = null }
                )
            }
        }
    }

    if (confirmToken) {
        ConfirmDialog(
            title = "Show list token?",
            text = "This creates your personal token if you do not have one yet. Keep it secret: anyone with the " +
                    "token can fetch your live blocklist.",
            confirmLabel = "Show token",
            onConfirm = { confirmToken = false; loadToken() },
            onDismiss = { confirmToken = false }
        )
    }
    RequestStateHost(req)
}

@Composable
private fun TokenCard(token: HoneypotListToken?, busy: Boolean, onShow: () -> Unit, onHide: () -> Unit) {
    FormSection(title = "Blocklist token") {
        if (token == null) {
            Text(
                "Your personal token gives access to the live v64 blocklist.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onShow, enabled = !busy) { Text("Show list token") }
        } else {
            // Screenshots sperren, solange der Token sichtbar ist
            SecureWindowEffect()
            val url = token.base?.takeIf { it.isNotBlank() }?.let { it.trimEnd('/') + "/" + LIST_FILE }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("List token", style = MaterialTheme.typography.titleSmall)
                if (token.live == true) StatusChip("Live", colorResource(R.color.ipv64_green))
                else StatusChip("Daily list", colorResource(R.color.ipv64_orange))
            }
            InfoRow("Token", token.token, copyable = true, sensitive = true, monospace = true)
            InfoRow("List URL", url, copyable = true, sensitive = true, monospace = true)
            if (token.live != true) {
                Text(
                    "Live access is not available for your account right now. The list URL serves the daily state.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onHide) { Text("Hide") }
        }
    }
}

private fun LazyListScope.honeypotContent(s: HoneypotStats, sensors: List<HoneypotSensor>) {
    item(key = "overview") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader("Your contribution")
                if (s.contributor == true) StatusChip("Contributor", colorResource(R.color.ipv64_green))
                else StatusChip("Not contributing", colorResource(R.color.ipv64_gray))
            }
            StatTiles(
                listOf(
                    "Sensors online" to "${(s.sensorsOnline ?: 0L).formatCount()} / ${(s.sensors ?: 0L).formatCount()}",
                    "Events (24 h)" to (s.events24h ?: 0L).formatCount(),
                    "Events (7 d)" to (s.events7d ?: 0L).formatCount(),
                    "Sources (24 h)" to (s.sources24h ?: 0L).formatCount(),
                    "Sources (7 d)" to (s.sources7d ?: 0L).formatCount()
                )
            )
            s.lastReport?.let {
                Text(
                    "Last report ${it.formatRelative()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }

    if (s.timeline.isNotEmpty()) {
        item(key = "hp_timeline") {
            FormSection(title = "Events per day (7 days)") {
                SimpleBarChart(
                    values = s.timeline.map { it.events ?: 0L },
                    firstLabel = shortDay(s.timeline.first().day),
                    lastLabel = shortDay(s.timeline.last().day),
                    color = chartPalette().categorical(1),
                    labels = s.timeline.map { shortDay(it.day) }
                )
            }
        }
    }
    item(key = "sensors") {
        ListGroup("Sensors") {
            if (sensors.isEmpty()) {
                EmptyState("You have no sensors yet. Sensors are provided by the Node64 honeypot module.")
            } else {
                sensors.forEachIndexed { i, sensor ->
                    if (i > 0) GroupDivider()
                    SensorRow(sensor)
                }
            }
        }
    }
}

@Composable
private fun SensorRow(sensor: HoneypotSensor) {
    val meta = listOfNotNull(sensor.profile, sensor.mode, sensor.version?.let { "v$it" }).joinToString(" · ")
    val subtitle = listOfNotNull(
        meta.takeIf { it.isNotEmpty() }?.let { "Profile: $it" },
        sensor.lastSeen?.let { "Last report ${it.formatRelative()}" },
        sensor.reports?.let { "Reports: ${it.formatCount()}" },
        "Events (24 h / 7 d): ${(sensor.events24h ?: 0L).formatCount()} / ${(sensor.events7d ?: 0L).formatCount()}",
        "Sources (24 h / 7 d): ${(sensor.sources24h ?: 0L).formatCount()} / ${(sensor.sources7d ?: 0L).formatCount()}"
    ).joinToString("\n")
    ListRow(
        // Nur der Anfang der UUID, die Zeile bleibt übersichtlich
        title = (sensor.id ?: "–").take(8) + if ((sensor.id?.length ?: 0) > 8) "…" else "",
        subtitle = subtitle,
        subtitleMaxLines = 5,
        trailing = {
            when {
                sensor.disabled == true -> StatusChip("Disabled", colorResource(R.color.ipv64_gray))
                sensor.online == true -> StatusChip("Online", colorResource(R.color.ipv64_green))
                else -> StatusChip("Offline", colorResource(R.color.ipv64_red))
            }
        }
    )
}

private fun LazyListScope.networkContent(c: ThreatStats?) {
    item(key = "network") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("ipv64.net network", Modifier.padding(start = 4.dp))
            c?.let {
                StatTiles(
                    listOf(
                        "Sensors online" to (it.sensors?.online ?: 0L).formatCount(),
                        "Attacks (24 h)" to (it.attacks24h?.events ?: 0L).formatCount(),
                        "Attacker IPs (24 h)" to (it.attacks24h?.sources ?: 0L).formatCount(),
                        "Blocklist size" to (it.lists["v64"] ?: 0L).formatCount()
                    )
                )
            }
        }
    }
}

private fun LazyListScope.nodeShareContent(nodes: List<Hc2Node>) {
    item(key = "nodeshare") {
        val active = nodes.count { n -> n.modules.any { it.equals("honeypot", ignoreCase = true) } }
        FormSection(title = "Your nodes") {
            if (nodes.isEmpty()) {
                Text("No nodes linked yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "$active of ${nodes.size} nodes run the honeypot",
                    style = MaterialTheme.typography.titleSmall
                )
                LinearProgressIndicator(progress = { active / nodes.size.toFloat() }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
