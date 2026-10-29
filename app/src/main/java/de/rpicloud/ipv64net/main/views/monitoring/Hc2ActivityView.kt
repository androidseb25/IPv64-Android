package de.rpicloud.ipv64net.main.views.monitoring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab as TabItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.Job
import de.rpicloud.ipv64net.helper.Hc2NavKeys
import de.rpicloud.ipv64net.helper.loadHc2Checks
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.main.views.HealthcheckEventItemView
import de.rpicloud.ipv64net.main.views.HealthcheckIncidentItemView
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.itemsOrEmpty
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.main.views.common.unknownCheckLabel
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.model.Hc2Event
import de.sr.ipv64.model.Hc2Incident

private const val INCIDENT_LIMIT = 100
private const val EVENT_PAGE = 50

// Aktivität: Incidents (sofort) und Events (erst beim ersten Öffnen des Tabs, seitenweise)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Hc2ActivityView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()

    // Flag einmal lesen und entfernen, damit ein späterer Besuch über die normale Zeile ungefiltert öffnet
    val startOpenOnly = remember {
        val handle = navController.previousBackStackEntry?.savedStateHandle
        (handle?.get<Boolean>(MonitoringNavKeys.ACTIVITY_OPEN_ONLY) == true).also {
            handle?.remove<Boolean>(MonitoringNavKeys.ACTIVITY_OPEN_ONLY)
        }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openOnly by rememberSaveable { mutableStateOf(startOpenOnly) }
    var eventsJob by remember { mutableStateOf<Job?>(null) }

    var names by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var incidents by remember { mutableStateOf<List<Hc2Incident>>(emptyList()) }
    var incidentsLoaded by remember { mutableStateOf(false) }
    var events by remember { mutableStateOf<List<Hc2Event>>(emptyList()) }
    var eventsLoaded by remember { mutableStateOf(false) }
    var nextBefore by remember { mutableStateOf<Long?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    fun loadIncidents(force: Boolean = false, pull: Boolean = false) {
        if (pull) refreshing = true
        req.launch(
            scope,
            spinner = if (pull) null else "Loading...",
            retry = { loadIncidents(true) },
            onFinally = { refreshing = false }
        ) {
            // Namen zuerst (gecachte Checkliste), dann die Incidents: sequentiell
            if (names.isEmpty() || force) {
                names = loadHc2Checks(ctx, force).mapNotNull { c -> c.token?.let { it to c.name.orEmpty() } }.toMap()
            }
            incidents = monitoringCall(ctx) { it.hc2.incidents(limit = INCIDENT_LIMIT, open = if (openOnly) true else null) }
            incidentsLoaded = true
        }
    }

    fun loadEvents(more: Boolean = false, pull: Boolean = false) {
        // Laden weiterer Seiten nicht parallel starten; ein Neuladen bricht eine laufende Anfrage ab
        if (more && eventsJob?.isActive == true) return
        if (!more) eventsJob?.cancel()
        if (pull) refreshing = true
        val before = if (more) nextBefore else null
        eventsJob = req.launch(
            scope,
            spinner = if (pull) null else "Loading...",
            retry = { loadEvents(more) },
            onFinally = { refreshing = false }
        ) {
            val page = monitoringCall(ctx) { it.hc2.events(limit = EVENT_PAGE, before = before) }
            events = if (more) (events + page.data).distinctBy { it.id ?: it.hashCode().toLong() } else page.data
            nextBefore = if (page.meta?.hasMore == true) page.meta?.nextBefore else null
            eventsLoaded = true
        }
    }

    LaunchedEffect(Unit) { loadIncidents() }
    LaunchedEffect(tab) {
        if (tab == 1 && !eventsLoaded) loadEvents()
    }

    fun nameOf(token: String?, fallback: String? = null): String =
        token?.let { names[it]?.takeIf { n -> n.isNotBlank() } } ?: fallback?.takeIf { it.isNotBlank() }
            ?: token?.let { unknownCheckLabel(it) } ?: "Unknown check"

    fun openCheck(token: String?) {
        if (token == null) return
        // Gelöschte Checks haben keine Detailseite; ist die Namensliste leer (nicht geladen), trotzdem versuchen
        if (names.isNotEmpty() && token !in names) return
        navController.navigateWithArgs(Tab.healthcheck_details, Hc2NavKeys.SELECTED_HC2_TOKEN to token)
    }

    ScreenScaffold(Tabs.getLabel(Tab.hc2_activity), navController, mainPadding) {
        Column(Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                TabItem(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Incidents") })
                TabItem(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Events") })
            }
            if (tab == 0) {
                RefreshableList(refreshing, { loadIncidents(force = true, pull = true) }) {
                    item(key = "filter") {
                        FilterChip(
                            selected = openOnly,
                            onClick = {
                                openOnly = !openOnly
                                loadIncidents()
                            },
                            label = { Text("Open only") }
                        )
                    }
                    itemsOrEmpty(
                        incidents, incidentsLoaded,
                        if (openOnly) "No open incidents." else "No incidents yet.",
                        key = { "inc_" + (it.id ?: it.hashCode()) }
                    ) { inc ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = inc.token != null) { openCheck(inc.token) },
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                nameOf(inc.token, inc.name),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            HealthcheckIncidentItemView(inc)
                        }
                    }
                }
            } else {
                RefreshableList(refreshing, { loadEvents(more = false, pull = true) }) {
                    itemsOrEmpty(
                        events, eventsLoaded, "No events yet.",
                        key = { "ev_" + (it.id ?: it.hashCode()) }
                    ) { ev ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = ev.token != null) { openCheck(ev.token) },
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                nameOf(ev.token),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            HealthcheckEventItemView(ev)
                        }
                    }
                    if (nextBefore != null) {
                        item(key = "more") {
                            TextButton(onClick = { loadEvents(more = true) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Load more")
                            }
                        }
                    }
                }
            }
        }
    }
    RequestStateHost(req)
}
