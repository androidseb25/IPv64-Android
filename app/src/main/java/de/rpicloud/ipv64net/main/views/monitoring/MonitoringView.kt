package de.rpicloud.ipv64net.main.views.monitoring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.Hc2CheckListCache
import de.rpicloud.ipv64net.helper.Hc2NavKeys
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.isPlanRestricted
import de.rpicloud.ipv64net.helper.loadHc2Account
import de.rpicloud.ipv64net.helper.loadHc2Checks
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.helper.planRestrictionText
import de.rpicloud.ipv64net.main.views.HealthcheckItemView
import de.rpicloud.ipv64net.main.views.HealthcheckSummaryView
import de.rpicloud.ipv64net.main.views.common.HubScreen
import de.rpicloud.ipv64net.main.views.common.HubSection
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.SectionHeader
import de.rpicloud.ipv64net.main.views.counts
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Hc2StateUi
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.model.Hc2Account
import de.sr.ipv64.model.Hc2Check
import de.sr.ipv64.model.Hc2Incident
import de.sr.ipv64.model.Hc2UsageLimit

private const val MAX_FAILING = 5

// Zustände, die im Hub als "Problem" aufgelistet werden (Down zuerst)
private fun Hc2Check.failingRank(): Int? = when (Hc2StateUi.from(state?.state, paused)) {
    Hc2StateUi.DOWN -> 0
    Hc2StateUi.FLAPPING -> 1
    Hc2StateUi.DEGRADED -> 2
    else -> null
}

private fun Hc2UsageLimit?.text(): String {
    if (this == null) return "–"
    return "${used ?: 0} / ${limit?.toString() ?: "∞"}"
}

// Monitoring-Hub: höchstens 3 sequentielle, gecachte Anfragen (Checks, offene Incidents, hc2-Account)
@Composable
fun MonitoringView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()

    // Client zuerst holen: bei Account-Wechsel leert IPv64Client.get() die Caches, damit keine fremden Daten aufblitzen
    var checks by remember { IPv64Client.get(ctx); mutableStateOf(Hc2CheckListCache.get()) }
    var incidents by remember { mutableStateOf<List<Hc2Incident>?>(OpenIncidentsCache.peek(ctx)) }
    var account by remember { mutableStateOf<Hc2Account?>(null) }
    var planNote by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    fun load(force: Boolean = false, pull: Boolean = false) {
        if (pull) refreshing = true
        planNote = null
        req.launch(
            scope,
            // Spinner nur, wenn noch nichts angezeigt werden kann
            spinner = if (pull || checks != null) null else "Loading...",
            retry = { load(true) },
            onError = {
                // Plan ohne Healthchecks 2.0: Hub bleibt nutzbar, nur ein Hinweis statt Fehlerdialog
                if (it.isPlanRestricted()) {
                    planNote = it.planRestrictionText()
                    true
                } else false
            },
            onFinally = { refreshing = false }
        ) {
            checks = loadHc2Checks(ctx, force)
            incidents = OpenIncidentsCache.get(ctx, force) { it.hc2.incidents(limit = 20, open = true) }
            account = loadHc2Account(ctx, force)
        }
    }

    LaunchedEffect(Unit) { load() }

    val list = checks
    val counts = remember(list) { list.orEmpty().counts() }
    val failing = remember(list) {
        list.orEmpty().mapNotNull { c -> c.failingRank()?.let { it to c } }
            .sortedWith(compareBy({ it.first }, { it.second.name.orEmpty().lowercase() }))
            .map { it.second }
    }
    val openIncidents = incidents.orEmpty().size

    HubScreen(
        title = Tabs.getLabel(Tab.monitoring),
        navController = navController,
        mainPadding = mainPadding,
        refreshing = refreshing,
        onRefresh = { load(force = true, pull = true) },
        sections = listOf(
            HubSection(Tab.healthcheck, subtitle = "Checks, states and details", badge = list?.size?.toString(), group = "Monitoring"),
            HubSection(
                Tab.hc2_activity, subtitle = "Incidents and events",
                badge = openIncidents.takeIf { it > 0 }?.let { "$it open" }, group = "Monitoring"
            ),
            HubSection(Tab.maintenance, subtitle = "Pause alerts during planned work", group = "Monitoring"),
            HubSection(Tab.channels, subtitle = "Where notifications are sent", group = "Notifications"),
            HubSection(Tab.hc2_settings, subtitle = "Quiet hours and batching", group = "Notifications"),
            HubSection(Tab.status_pages, subtitle = "Public pages for your checks", group = "Publishing"),
            HubSection(Tab.failover, subtitle = "Switch DNS records when a check fails", group = "Resilience"),
            HubSection(Tab.offline_pages, subtitle = "Fallback pages for your domains", group = "Resilience")
        ),
        header = {
            planNote?.let { note ->
                item(key = "plan_note") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    }
                }
            }
            if (openIncidents > 0) {
                item(key = "incident_banner") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        onClick = {
                            navController.navigateWithArgs(Tab.hc2_activity, MonitoringNavKeys.ACTIVITY_OPEN_ONLY to true)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(painterResource(R.drawable.warning_24px), contentDescription = null)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (openIncidents == 1) "1 open incident" else "$openIncidents open incidents",
                                    fontWeight = FontWeight.Bold
                                )
                                incidents?.firstOrNull()?.let { first ->
                                    first.name?.takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            if (openIncidents > 1) "$it and ${openIncidents - 1} more" else it,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                            Icon(painterResource(R.drawable.chevron_right_24px), contentDescription = null)
                        }
                    }
                }
            }
            if (list != null) {
                item(key = "summary") {
                    // Zusammenfassung antippen = Check-Liste
                    HealthcheckSummaryView(
                        counts,
                        onTileClick = { state ->
                            navController.navigateWithArgs(Tab.healthcheck, MonitoringNavKeys.CHECK_FILTER to state.name)
                        }
                    )
                }
            }
            if (failing.isNotEmpty()) {
                item(key = "failing_header") { SectionHeader("Needs attention") }
                items(failing.take(MAX_FAILING), key = { "failing_" + it.token.orEmpty() + it.name.orEmpty() }) { check ->
                    HealthcheckItemView(check) { selected ->
                        selected.token?.let { token ->
                            navController.navigateWithArgs(Tab.healthcheck_details, Hc2NavKeys.SELECTED_HC2_TOKEN to token)
                        }
                    }
                }
                if (failing.size > MAX_FAILING) {
                    item(key = "failing_more") {
                        TextButton(
                            onClick = { navController.navigate(Tabs.getRoute(Tab.healthcheck)) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Show all ${failing.size} problems") }
                    }
                }
            }
            account?.let { acc ->
                item(key = "plan_card") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Plan: " + acc.plan.orEmpty().ifBlank { "–" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                listOfNotNull(
                                    acc.checks?.let { "$it check(s)" },
                                    "Online points ${acc.onlineMeasuringPoints.text()}",
                                    acc.nodes?.let { "Nodes ${it.online ?: 0} / ${it.total ?: 0} online" }
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            acc.checksPerDay?.let {
                                Text(
                                    "Daily measurements ${it.text()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    )
    RequestStateHost(req)
}
