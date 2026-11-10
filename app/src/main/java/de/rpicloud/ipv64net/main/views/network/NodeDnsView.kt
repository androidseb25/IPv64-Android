package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.helper.rememberNavArg
import de.rpicloud.ipv64net.main.views.common.ConfirmDialog
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.FilterChipRow
import androidx.compose.runtime.LaunchedEffect
import de.rpicloud.ipv64net.models.Tabs
import de.rpicloud.ipv64net.main.views.common.FormSection
import de.rpicloud.ipv64net.main.views.common.GroupDivider
import de.rpicloud.ipv64net.main.views.common.GroupText
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.main.views.security.RankRow
import de.rpicloud.ipv64net.main.views.security.RankingCard
import de.rpicloud.ipv64net.main.views.security.StackedBarChart
import de.rpicloud.ipv64net.main.views.security.StatTiles
import de.rpicloud.ipv64net.main.views.security.formatCount
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.ui.theme.chartPalette
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.Node64ApiException
import de.sr.ipv64.node64.model.DNS64Blocking
import de.sr.ipv64.node64.model.DNS64Cache
import de.sr.ipv64.node64.model.DNS64Range
import de.sr.ipv64.node64.model.DNS64Status
import de.sr.ipv64.node64.model.DNS64Summary
import de.sr.ipv64.node64.model.DNS64Timeline
import de.sr.ipv64.node64.model.DNS64TopItem
import de.sr.ipv64.node64.model.DNS64TopKind
import de.sr.ipv64.node64.model.DNS64Upstreams
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Ergebnis eines Abschnitts: Wert oder Fehlertext (ein Fehler lässt die anderen Abschnitte stehen)
private class Sec<T>(val value: T? = null, val error: String? = null)

private suspend fun <T> sec(block: suspend () -> T): Sec<T> =
    try {
        Sec(block())
    } catch (e: IPv64Exception) {
        Sec(error = if (e is Node64ApiException && e.status == 404) "Not available on this node." else localNodeError(e).let { "${it.first}: ${it.second}" })
    }

private class DnsData(
    val status: Sec<DNS64Status>,
    val summary: Sec<DNS64Summary>,
    val timeline: Sec<DNS64Timeline>,
    val upstreams: Sec<DNS64Upstreams>,
    val cache: Sec<DNS64Cache>,
    val blocked: Sec<List<DNS64TopItem>>,
    val allowed: Sec<List<DNS64TopItem>>,
    val clients: Sec<List<DNS64TopItem>>
)

private val RANGES = listOf(Triple("1 h", DNS64Range.HOUR_1, 1), Triple("24 h", DNS64Range.HOURS_24, 24), Triple("7 d", DNS64Range.DAYS_7, 168))

private suspend fun loadDns(url: String, key: String, range: DNS64Range): DnsData = coroutineScope {
    val dns = localNodeClient(url, key).dns64
    val status = async { sec { dns.status() } }
    val summary = async { sec { dns.summary(range) } }
    val timeline = async { sec { dns.timeline(range) } }
    val upstreams = async { sec { dns.upstreams() } }
    val cache = async { sec { dns.cache() } }
    val blocked = async { sec { dns.top(DNS64TopKind.BLOCKED, range, 10) } }
    val allowed = async { sec { dns.top(DNS64TopKind.ALLOWED, range, 10) } }
    val clients = async { sec { dns.top(DNS64TopKind.CLIENTS, range, 10) } }
    DnsData(status.await(), summary.await(), timeline.await(), upstreams.await(), cache.await(), blocked.await(), allowed.await(), clients.await())
}

private val PAUSE_CHOICES: List<Pair<String, Int>?> = listOf("5 min" to 5, "15 min" to 15, "30 min" to 30, "1 h" to 60, "Until resumed" to 0)

private fun pct(part: Long, whole: Long) = if (whole > 0) "%.1f".format(Locale.US, part * 100.0 / whole) else "0"

/** Dashboard of one directly connected node's local DNS (node id via nav arg). */
@Composable
fun NodeDnsView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    // Als Tab-Wurzel (ohne Nav-Argument): Node aus den Nodes mit aktivem DNS64 wählen
    val argId = navController.rememberNavArg<String>(NavKeys.SELECTED_NODE_ID)
    val dnsIds = ConnectedNodes.dnsIds
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val id = argId ?: picked?.takeIf { it in dnsIds } ?: dnsIds.firstOrNull()
    var resumedOnce by remember { mutableStateOf(false) }
    var config by remember { mutableStateOf<Pair<String, String>?>(null) }
    var data by remember { mutableStateOf<DnsData?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var rangeIdx by rememberSaveable { mutableIntStateOf(1) }
    var showFlush by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun load(pull: Boolean = false) {
        val (url, key) = config ?: return
        job?.cancel()
        if (pull) refreshing = true
        job = req.launch(scope, spinner = if (pull || data != null) null else "Loading...", onFinally = { refreshing = false }) {
            data = loadDns(url, key, RANGES[rangeIdx].second)
        }
    }

    // Andere Node gewählt: alten Stand verwerfen und neu laden
    LaunchedEffect(id) {
        config = id?.let { LocalNodeStore.get(ctx, it) }
        data = null
        load()
    }
    // Zugang beim Zurückkehren neu lesen (z. B. nach Änderung unter Nodes); der erste Aufruf läuft über LaunchedEffect
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (resumedOnce) {
            config = id?.let { LocalNodeStore.get(ctx, it) }
            load()
        }
        resumedOnce = true
    }

    fun setBlocking(call: suspend (de.sr.ipv64.node64.api.Dns64Api) -> DNS64Blocking) {
        val (url, key) = config ?: return
        req.launch(
            scope,
            spinner = "Updating...",
            onError = { e -> localNodeError(e).let { req.message(it.first, it.second) }; true }
        ) {
            val b = call(localNodeClient(url, key).dns64)
            data = data?.let { d -> d.status.value?.let { s -> DnsData(Sec(s.copy(blocking = b)), d.summary, d.timeline, d.upstreams, d.cache, d.blocked, d.allowed, d.clients) } ?: d }
        }
    }

    fun flush() {
        val (url, key) = config ?: return
        req.launch(
            scope,
            spinner = "Flushing cache...",
            onError = { e -> localNodeError(e).let { req.message(it.first, it.second) }; true }
        ) {
            val dns = localNodeClient(url, key).dns64
            dns.flushCache()
            val c = sec { dns.cache() }
            data = data?.let { d -> DnsData(d.status, d.summary, d.timeline, d.upstreams, c, d.blocked, d.allowed, d.clients) }
        }
    }

    val title = if (argId == null) Tabs.getLabel(Tab.dns64) else id?.let { LocalNodeStore.name(ctx, it) } ?: "Local DNS"
    ScreenScaffold(title = title, navController = navController, mainPadding = mainPadding) {
        val d = data
        when {
            argId == null && id == null -> EmptyState("No connected node has DNS64 enabled.")
            config == null -> EmptyState("No direct connection to this node.")
            d == null -> Unit
            else -> RefreshableList(refreshing = refreshing, onRefresh = { load(pull = true) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Mehrere DNS64-Nodes: Auswahl oben (nur als Tab-Wurzel)
                if (argId == null && dnsIds.size > 1 && id != null) {
                    item(key = "node_picker") {
                        FilterChipRow(
                            options = dnsIds,
                            selected = id,
                            label = { LocalNodeStore.name(ctx, it) ?: it },
                            onSelect = { picked = it }
                        )
                    }
                }
                item(key = "blocking") {
                    BlockingCard(d.status, req.isBusy, onPause = { m -> setBlocking { it.pause(m) } }, onResume = { setBlocking { it.resume() } }, onExpired = { load() })
                }
                // Node nicht erreichbar: nur die Fehlermeldung (Verbindung wird unter More → Nodes bearbeitet), keine weiteren Karten
                if (d.status.value == null) return@RefreshableList
                item(key = "links") {
                    ListGroup {
                        ListRow(
                            title = "Query log", subtitle = "Live", icon = R.drawable.dns_24px,
                            onClick = { id?.let { navController.navigateWithArgs(Tab.node_dns_log, NavKeys.SELECTED_NODE_ID to it) } }
                        )
                        GroupDivider(56.dp)
                        ListRow(
                            title = "Local DNS records", icon = R.drawable.dns_24px,
                            onClick = { id?.let { navController.navigateWithArgs(Tab.node_dns_records, NavKeys.SELECTED_NODE_ID to it) } }
                        )
                    }
                }
                item(key = "range") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        RANGES.forEachIndexed { i, (label, _, _) ->
                            SegmentedButton(
                                selected = rangeIdx == i,
                                onClick = { if (rangeIdx != i) { rangeIdx = i; load() } },
                                shape = SegmentedButtonDefaults.itemShape(i, RANGES.size)
                            ) { Text(label) }
                        }
                    }
                }
                item(key = "kpi") { Kpis(d.summary, RANGES[rangeIdx].third) }
                item(key = "timeline") { Timeline(d.timeline, RANGES[rangeIdx].second) }
                item(key = "upstreams") { Upstreams(d.upstreams) }
                item(key = "cache") { CacheCard(d.cache, req.isBusy) { showFlush = true } }
                item(key = "top_blocked") { Top("Top blocked domains", d.blocked, false) }
                item(key = "top_allowed") { Top("Top queried domains", d.allowed, false) }
                item(key = "top_clients") { Top("Top clients", d.clients, true) }
            }
        }
    }

    if (showFlush) {
        ConfirmDialog(
            title = "Flush cache",
            text = "Remove all cached DNS answers on this node? The next queries are answered by the upstream servers.",
            confirmLabel = "Flush",
            destructive = true,
            onConfirm = { showFlush = false; flush() },
            onDismiss = { showFlush = false }
        )
    }
    RequestStateHost(req)
}

@Composable
private fun SecError(title: String, msg: String) = ListGroup(title = title) { GroupText(msg, error = true) }

@Composable
private fun BlockingCard(status: Sec<DNS64Status>, busy: Boolean, onPause: (Int) -> Unit, onResume: () -> Unit, onExpired: () -> Unit) {
    val s = status.value
    if (s == null) {
        ListGroup(title = "Blocking") {
            GroupText(status.error ?: "Status unavailable", error = true)
            GroupText("Check the connection under More → Nodes.")
        }
        return
    }
    val b = s.blocking
    val paused = b?.paused == true
    var pending by remember { mutableStateOf<Int?>(null) }
    // Lokal berechnetes Pausenende, falls die Antwort kein pausedUntil enthält (sofortige Anzeige)
    var localUntil by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(paused) { if (!paused) localUntil = null }
    val untilMs = if (b?.untilResumed == true) null else b?.pausedUntil?.toEpochMilliseconds() ?: localUntil
    val timeFmt = SimpleDateFormat("HH:mm", Locale.GERMANY)
    // Restzeit läuft mit (30 s, unter 2 min sekündlich); bei Ablauf einmal neu laden
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (paused && untilMs != null) LaunchedEffect(untilMs) {
        while (true) {
            now = System.currentTimeMillis()
            if (untilMs <= now) { onExpired(); break }
            delay(if (untilMs - now < 120_000) 1_000 else 30_000)
        }
    }
    pending?.let { m ->
        val end = timeFmt.format(Date(System.currentTimeMillis() + m * 60_000L))
        ConfirmDialog(
            title = "Pause blocking?",
            text = if (m == 0) "Ads and trackers will not be blocked until you resume it."
            else "Ads and trackers will not be blocked for ${PAUSE_CHOICES.firstOrNull { it?.second == m }?.first ?: "$m min"} (until $end).",
            confirmLabel = "Pause",
            onConfirm = { pending = null; localUntil = if (m > 0) System.currentTimeMillis() + m * 60_000L else null; onPause(m) },
            onDismiss = { pending = null }
        )
    }
    ListGroup(title = "Blocking") {
        ListRow(
            title = when {
                !paused -> "Blocking active"
                untilMs == null -> "Paused until resumed"
                else -> {
                    val ms = (untilMs - now).coerceAtLeast(0)
                    val end = timeFmt.format(Date(untilMs))
                    if (ms < 120_000) "Paused · ${(ms / 1000).toInt()} s left (until $end)"
                    else "Paused · ${(ms / 60_000).toInt() + 1} min left (until $end)"
                }
            },
            showChevron = false,
            trailing = { if (paused) StatusChip("Paused", colorResource(R.color.ipv64_orange)) else StatusChip("Active", colorResource(R.color.ipv64_green)) }
        )
        val mod = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        if (paused) FilledTonalButton(onClick = onResume, enabled = !busy, modifier = mod) { Text("Resume") }
        else {
            // Dauer direkt in der Karte wählen (0 = bis zur Wiederaufnahme), danach Rückfrage
            GroupText("Pause blocking for")
            FilterChipRow(
                options = PAUSE_CHOICES,
                selected = null,
                label = { it?.first.orEmpty() },
                onSelect = { if (!busy) pending = it?.second },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun Kpis(summary: Sec<DNS64Summary>, hours: Int) {
    val s = summary.value
    if (s == null) return SecError("Overview", summary.error ?: "No data")
    val st = s.summary
    val total = st?.total ?: 0L
    val blocked = st?.blocked ?: 0L
    val cached = st?.cached ?: 0L
    StatTiles(
        items = listOf(
            "Queries" to total.formatCount(),
            "Blocked" to blocked.formatCount(),
            "From cache" to cached.formatCount(),
            "Clients" to (st?.clients ?: 0L).formatCount(),
            "Ø Upstream" to (st?.avgUpstreamMs?.let { "%.0f ms".format(Locale.US, it) } ?: "–"),
            "Blocklists" to (s.blocklistSize ?: 0L).formatCount()
        ),
        captions = listOf("≈ ${(total / hours).formatCount()} per hour", "${pct(blocked, total)} % of queries", "${pct(cached, total)} % answered instantly", null, null, "domains")
    )
}

@Composable
private fun Timeline(timeline: Sec<DNS64Timeline>, range: DNS64Range) {
    val t = timeline.value
    if (t == null) return SecError("Queries over time", timeline.error ?: "No data")
    val fmt = remember(range) { SimpleDateFormat(if (range == DNS64Range.DAYS_7) "dd.MM." else "HH:mm", Locale.GERMANY) }
    val full = remember { SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY) }
    val buckets = t.buckets
    val palette = chartPalette()
    FormSection(title = "Queries over time") {
        if (buckets.isEmpty()) Text("No data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else StackedBarChart(
            bottom = buckets.map { ((it.total ?: 0L) - (it.blocked ?: 0L)).coerceAtLeast(0L) },
            top = buckets.map { it.blocked ?: 0L },
            bottomName = "Allowed",
            topName = "Blocked",
            bottomColor = palette.categorical(5),
            topColor = palette.categorical(7),
            tooltips = buckets.map { full.format(Date(it.t ?: 0L)) },
            axisLabels = buckets.map { fmt.format(Date(it.t ?: 0L)) }
        )
    }
}

@Composable
private fun Upstreams(up: Sec<DNS64Upstreams>) {
    val u = up.value
    if (u == null) return SecError("Upstream servers", up.error ?: "No data")
    ListGroup(title = "Upstream servers") {
        u.mode?.let { GroupText("Mode: $it") }
        if (u.servers.isEmpty()) GroupText("No upstream servers.")
        val sumWins = u.servers.sumOf { it.wins ?: 0L }
        u.servers.forEachIndexed { i, sv ->
            if (i > 0 || u.mode != null) GroupDivider()
            val req = sv.requests ?: 0L
            ListRow(
                title = sv.name ?: sv.url ?: "–",
                subtitle = "Ø ${sv.avgMs?.let { "%.0f".format(Locale.US, it) } ?: "–"} ms · fastest at ${pct(sv.wins ?: 0L, req)} % · ${req.formatCount()} queries · ${sv.errors ?: 0L} errors",
                subtitleMaxLines = 3,
                showChevron = false,
                trailing = {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        sv.proto?.let { StatusChip(it.uppercase(), colorResource(R.color.ipv64_gray)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (sv.healthy == true) StatusChip("Healthy", colorResource(R.color.ipv64_green)) else StatusChip("Unhealthy", colorResource(R.color.ipv64_red))
                            if (sv.active == true) StatusChip("Active", colorResource(R.color.ipv64_green))
                        }
                    }
                }
            )
            LinearProgressIndicator(
                progress = { if (sumWins > 0) (sv.wins ?: 0L).toFloat() / sumWins else 0f },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
            sv.lastError?.takeIf { it.isNotBlank() }?.let { GroupText(it, error = true) }
        }
    }
}

@Composable
private fun CacheCard(cache: Sec<DNS64Cache>, busy: Boolean, onFlush: () -> Unit) {
    val c = cache.value
    if (c == null) return SecError("DNS cache", cache.error ?: "No data")
    val hits = c.hits ?: 0L
    val misses = c.misses ?: 0L
    ListGroup(title = "DNS cache") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Hit rate ${pct(hits, hits + misses)} %", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { if (hits + misses > 0) hits.toFloat() / (hits + misses) else 0f }, modifier = Modifier.fillMaxWidth())
        }
        GroupDivider()
        ListRow(title = "Entries", subtitle = "${(c.entries ?: 0L).formatCount()} of ${(c.max ?: 0L).formatCount()}", showChevron = false)
        GroupDivider()
        ListRow(title = "Stale served", subtitle = (c.staleServed ?: 0L).formatCount(), showChevron = false)
        GroupDivider()
        ListRow(title = "Refreshed in background", subtitle = (c.refreshed ?: 0L).formatCount(), showChevron = false)
        OutlinedButton(
            onClick = onFlush,
            enabled = !busy,
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) { Text("Flush cache") }
    }
}

@Composable
private fun Top(title: String, items: Sec<List<DNS64TopItem>>, client: Boolean) {
    val list = items.value
    if (list == null) return SecError(title, items.error ?: "No data")
    RankingCard(title, list.map { RankRow((if (client) it.hostname?.takeIf { h -> h.isNotBlank() } else null) ?: it.name ?: "–", it.count ?: 0L) })
}
