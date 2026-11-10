package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import de.rpicloud.ipv64net.main.views.common.ActionTile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.formatDuration
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.helper.realOrNull
import de.rpicloud.ipv64net.helper.formatLatency
import de.rpicloud.ipv64net.helper.formatPercent
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.helper.rememberNavArg
import de.rpicloud.ipv64net.main.views.common.ConfirmDialog
import de.rpicloud.ipv64net.main.views.common.FilterChipRow
import de.rpicloud.ipv64net.main.views.common.GroupDivider
import de.rpicloud.ipv64net.main.views.common.GroupText
import de.rpicloud.ipv64net.main.views.common.InfoGroup
import de.rpicloud.ipv64net.main.views.common.InfoItem
import de.rpicloud.ipv64net.main.views.common.InfoRow
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.common.groupedRows
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.main.views.common.spacedItem
import de.rpicloud.ipv64net.main.views.network.LocalNodeStore
import de.rpicloud.ipv64net.main.views.network.localNodeClient
import de.rpicloud.ipv64net.main.views.network.localNodeError
import de.rpicloud.ipv64net.models.Hc2StateUi
import de.rpicloud.ipv64net.models.Tab
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.node64.model.Check
import de.sr.ipv64.node64.model.Event
import de.sr.ipv64.node64.model.Incident
import de.sr.ipv64.node64.model.Measurement
import de.sr.ipv64.node64.model.MeasurementStatus
import de.sr.ipv64.node64.model.Stats
import de.sr.ipv64.node64.model.StatsRange

private const val HINT = "Check the connection under More → Nodes."

// Detailansicht eines Checks, der auf einer direkt verbundenen Node läuft (lokale Node-API)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NodeCheckDetailView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    val nodeId = navController.rememberNavArg<String>(NavKeys.SELECTED_NODE_ID)
    val token = navController.rememberNavArg<String>(NavKeys.NODE_CHECK_TOKEN)
    val client = remember(nodeId) { nodeId?.let { id -> LocalNodeStore.get(ctx, id)?.let { (u, k) -> localNodeClient(u, k) } } }

    var chk by remember { mutableStateOf<Check?>(null) }
    var nodeError by remember { mutableStateOf<String?>(null) }
    var events by remember { mutableStateOf<List<Event>>(emptyList()) }
    var eventsError by remember { mutableStateOf<String?>(null) }
    var measurements by remember { mutableStateOf<List<Measurement>>(emptyList()) }
    var incidents by remember { mutableStateOf<List<Incident>>(emptyList()) }
    var incidentsError by remember { mutableStateOf<String?>(null) }
    var range by remember { mutableStateOf(StatsRange.HOURS_24) }
    val statsCache = remember { mutableStateMapOf<StatsRange, Stats>() }
    // Zählt jedes Neuladen hoch, damit die geleerte Statistik erneut geholt wird
    var statsGen by remember { mutableIntStateOf(0) }
    var statsError by remember { mutableStateOf<String?>(null) }
    var statsLoading by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Ein fehlschlagender Abschnitt zeigt seinen Fehler inline und bricht die anderen nicht ab
    suspend fun <T> section(onError: (String?) -> Unit, block: suspend () -> T): T? = try {
        block().also { onError(null) }
    } catch (e: IPv64Exception) {
        onError(localNodeError(e).second); null
    }

    fun load() {
        val tk = token
        if (client == null || tk == null) {
            nodeError = "No local access stored for this node."
            return
        }
        statsCache.clear()
        statsGen++
        req.launch(scope, onError = { e -> chk = null; nodeError = localNodeError(e).second; true }) {
            chk = client.checks.get(tk)
            nodeError = null
            events = section({ eventsError = it }) { client.checks.events(tk, limit = 50) } ?: events
            incidents = section({ incidentsError = it }) { client.checks.incidents(tk, limit = 20) } ?: incidents
            measurements = runCatching { client.checks.measurements(tk, limit = 100) }.getOrDefault(emptyList())
        }
    }

    // Aktion mit Fehlerdialog (lokale Fehlertexte) und anschließendem Neuladen
    fun act(spinner: String, block: suspend () -> Unit) {
        req.launch(scope, spinner = spinner, onError = { e ->
            localNodeError(e).let { req.message(it.first, it.second, R.drawable.warning_24px) }; true
        }) {
            block()
            load()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { load() }

    // Statistik je Zeitraum nur einmal laden
    LaunchedEffect(range, chk?.token, statsGen) {
        val tk = chk?.token ?: return@LaunchedEffect
        if (client == null || statsCache.containsKey(range)) { statsError = null; return@LaunchedEffect }
        statsLoading = true
        section({ statsError = it }) { client.checks.stats(tk, range) }?.let { statsCache[range] = it }
        statsLoading = false
    }

    ScreenScaffold(
        title = chk?.name ?: "Healthcheck", navController = navController, mainPadding = mainPadding,
        actions = {
            IconButton(onClick = { load() }) {
                Icon(painterResource(R.drawable.refresh_24px), contentDescription = "Refresh")
            }
            // Typen, die das Package nicht kennt (type == null), ließen sich nicht speichern
            IconButton(enabled = chk?.type != null, onClick = {
                navController.navigateWithArgs(Tab.node_check_edit, NavKeys.SELECTED_NODE_ID to nodeId, NavKeys.NODE_CHECK_TOKEN to token)
            }) { Icon(painterResource(R.drawable.edit_24px), contentDescription = "Edit") }
            IconButton(enabled = chk != null, onClick = { confirmDelete = true }) {
                Icon(painterResource(R.drawable.delete_24px), contentDescription = "Delete")
            }
        }
    ) {
        val d = chk
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (nodeError != null) {
                spacedItem {
                    ListGroup {
                        GroupText(nodeError.orEmpty(), error = true)
                        GroupText(HINT)
                    }
                }
            }
            if (d != null) {
                val tk = d.token.orEmpty()
                val st = d.state
                val ui = Hc2StateUi.from(st?.state?.value, d.paused)
                spacedItem {
                    ListGroup {
                        ListRow(
                            title = listOfNotNull(d.type?.value?.uppercase(), d.target?.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { "–" },
                            trailing = { StatusChip(ui.label, colorResource(ui.color)) }
                        )
                    }
                }
                spacedItem {
                    InfoGroup(
                        "Status",
                        listOf(
                            InfoItem("Since", st?.since?.realOrNull()?.formatGermanTime()),
                            InfoItem("Last check", st?.lastCheckAt?.let { "${it.formatGermanTime()} (${it.formatRelative()})" }),
                            InfoItem("Last status", st?.lastStatus),
                            InfoItem("Latency", st?.lastLatencyMs?.formatLatency()),
                            InfoItem("Reason", st?.lastReason),
                            InfoItem("Message", st?.lastMessage),
                            InfoItem("Down since", st?.downSince?.realOrNull()?.formatGermanTime(), valueColor = MaterialTheme.colorScheme.error),
                            InfoItem("Interval", d.intervalSeconds?.let { "$it s" }),
                            InfoItem("Description", d.description)
                        ),
                        hideEmpty = true
                    )
                }
                spacedItem {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionTile(
                            if (d.paused == true) R.drawable.play_arrow_24px else R.drawable.pause_24px,
                            if (d.paused == true) "Resume" else "Pause",
                            {
                                act(if (d.paused == true) "Resuming..." else "Pausing...") {
                                    if (d.paused == true) client?.checks?.resume(tk) else client?.checks?.pause(tk)
                                }
                            }, Modifier.weight(1f)
                        )
                        ActionTile(R.drawable.refresh_24px, "Run now", { act("Running...") { client?.checks?.run(tk) } }, Modifier.weight(1f))
                    }
                }
                if (measurements.isNotEmpty()) {
                    spacedItem {
                        ListGroup("Recent checks") {
                            Box(Modifier.padding(16.dp)) {
                                StatusPillsBar(measurements.map { it.stateUi() }, "checks")
                            }
                        }
                    }
                }
                spacedItem {
                    ListGroup("Statistics") {
                        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilterChipRow(options = StatsRange.entries, selected = range, label = { it.chipLabel() }, onSelect = { range = it })
                            val s = statsCache[range]
                            when {
                                statsError != null -> Text(statsError.orEmpty(), color = MaterialTheme.colorScheme.error)
                                statsLoading && s == null -> Text("Loading...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                s == null -> Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                else -> {
                                    InfoRow("Uptime", s.upPercent.formatPercent())
                                    InfoRow("Measurements", s.count?.toString() ?: "–")
                                    InfoRow("Response time", "min ${s.minMs.formatLatency()} · avg ${s.avgMs.formatLatency()} · max ${s.maxMs.formatLatency()}")
                                }
                            }
                        }
                    }
                }
                spacedItem {
                    ListGroup("Incidents") {
                        when {
                            incidentsError != null -> GroupText(incidentsError.orEmpty(), error = true)
                            incidents.isEmpty() -> ListRow("No incidents")
                            else -> incidents.forEachIndexed { i, inc ->
                                if (i > 0) GroupDivider()
                                NodeIncidentRow(inc)
                            }
                        }
                    }
                }
                when {
                    eventsError != null -> spacedItem { ListGroup("Events") { GroupText(eventsError.orEmpty(), error = true) } }
                    events.isEmpty() -> spacedItem { ListGroup("Events") { ListRow("No events") } }
                    else -> groupedRows(events, title = "Events") { NodeEventRow(it) }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete healthcheck?", text = "\"${chk?.name.orEmpty()}\" will be removed from the node.",
            confirmLabel = "Delete", destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                val tk = token ?: return@ConfirmDialog
                req.launch(scope, spinner = "Deleting...", onError = { e ->
                    localNodeError(e).let { req.message(it.first, it.second, R.drawable.warning_24px) }; true
                }) {
                    client?.checks?.delete(tk)
                    navController.popBackStack()
                }
            }
        )
    }

    RequestStateHost(req)
}

private fun StatsRange.chipLabel() = when (this) {
    StatsRange.HOUR_1 -> "1 h"
    StatsRange.HOURS_24 -> "24 h"
    StatsRange.DAYS_7 -> "7 d"
    StatsRange.DAYS_30 -> "30 d"
    StatsRange.YEAR_1 -> "1 y"
}

private fun Measurement.stateUi() = when (status) {
    MeasurementStatus.UP -> Hc2StateUi.UP
    MeasurementStatus.DOWN -> Hc2StateUi.DOWN
    else -> Hc2StateUi.UNKNOWN
}

// Zustand eines Events: Art zuerst, sonst to-Zustand (wie bei den Account-Checks)
private fun Event.stateUi() = when (kind?.lowercase()) {
    "down", "reminder" -> Hc2StateUi.DOWN
    "up", "degraded_end", "flapping_end" -> Hc2StateUi.UP
    "degraded" -> Hc2StateUi.DEGRADED
    "flapping_start" -> Hc2StateUi.FLAPPING
    else -> Hc2StateUi.from(to)
}

@Composable
private fun NodeEventRow(ev: Event) {
    val state = ev.stateUi()
    val kind = ev.kind.orEmpty().replace('_', ' ').replaceFirstChar { it.uppercase() }.ifBlank { state.label }
    val lines = listOfNotNull(
        if (!ev.from.isNullOrBlank() || !ev.to.isNullOrBlank()) "${ev.from.orEmpty().ifBlank { "–" }} → ${ev.to.orEmpty().ifBlank { "–" }}" else null,
        listOfNotNull(
            ev.durationSeconds?.let { "after ${it.formatDuration()}" },
            ev.flaps?.takeIf { it > 0 }?.let { "$it flaps" },
            "silent".takeIf { ev.silent == true }
        ).takeIf { it.isNotEmpty() }?.joinToString(" · "),
        ev.message?.takeIf { it.isNotBlank() } ?: ev.reason?.takeIf { it.isNotBlank() },
        ev.time?.formatGermanTime()
    )
    ListRow(
        title = kind, subtitle = lines.joinToString("\n").ifEmpty { null }, subtitleMaxLines = 6,
        trailing = { StatusChip(state.label, colorResource(state.color)) }, showChevron = false
    )
}

@Composable
private fun NodeIncidentRow(inc: Incident) {
    val open = inc.open == true
    val color = if (open) Hc2StateUi.DOWN.color else Hc2StateUi.UP.color
    val lines = listOfNotNull(
        inc.startedAt?.let { "Started ${it.formatGermanTime()}" },
        inc.endedAt?.let { "Ended ${it.formatGermanTime()}" },
        inc.message?.takeIf { it.isNotBlank() } ?: inc.reason?.takeIf { it.isNotBlank() }
    )
    ListRow(
        title = if (open) "Open incident" else "Resolved incident",
        subtitle = lines.joinToString("\n").ifEmpty { null }, subtitleMaxLines = 6,
        trailing = { StatusChip(if (open) "Open" else "Resolved", colorResource(color)) }, showChevron = false
    )
}
