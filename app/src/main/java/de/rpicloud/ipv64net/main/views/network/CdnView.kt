package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.FeatureAvailability
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.PlanFeature
import de.rpicloud.ipv64net.helper.availability
import de.rpicloud.ipv64net.helper.canCreate
import de.rpicloud.ipv64net.helper.loadMe
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.main.views.common.DomainPickerDialog
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.GroupDivider
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.NotAvailableView
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.CdnDomain

/**
 * Liste der CDN-Domains. Anfragen: je Öffnen/Aktualisieren höchstens `account.me` (Plan, gecacht) und `cdn.list`
 * (30 s Cache), danach eine pro "Domain hinzufügen". Keine Einzelabfragen pro Zeile.
 */
@Composable
fun CdnView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()

    var domains by remember { mutableStateOf(CdnCache.peek(ctx)) }
    var availability by remember { mutableStateOf<FeatureAvailability>(FeatureAvailability.Unknown) }
    var usage by remember { mutableStateOf<de.sr.ipv64.model.MeUsage?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    // Neues CDN64 je verbundener Proxy-Node (null = noch nicht geladen / keine Nodes)
    var nodes by remember { mutableStateOf<List<NodeOnline>>(emptyList()) }
    var choices by remember { mutableStateOf<List<CdnHostChoice>?>(null) }

    fun load(force: Boolean = false, pull: Boolean = false) {
        if (pull) refreshing = true
        req.notAvailable = null
        req.launch(
            scope,
            spinner = if (pull || domains != null) null else "Loading...",
            retry = { load(true) },
            planGate = true,
            onFinally = { refreshing = false }
        ) {
            nodes = loadNodeOnline(ctx)
            if (nodes.isNotEmpty()) {
                // Neues CDN64 läuft über die Node; das alte Cloud-CDN ist optional (Plan kann es ausschließen)
                try {
                    val me = loadMe(ctx, force)
                    usage = me.usage
                    availability = me.availability(PlanFeature.CDN)
                    if (availability !is FeatureAvailability.NotInPlan) domains = CdnCache.get(ctx, force) { it.cdn.list() }
                } catch (e: IPv64Exception) {
                }
                return@launch
            }
            // Plan-Prüfung ist nicht kritisch: bei Fehlern entscheidet der Server beim Laden der Liste
            try {
                val me = loadMe(ctx, force)
                usage = me.usage
                availability = me.availability(PlanFeature.CDN)
            } catch (e: IPv64Exception) {
                availability = FeatureAvailability.Unknown
            }
            if (availability is FeatureAvailability.NotInPlan) {
                req.notAvailable = "Your plan does not include the CDN."
                return@launch
            }
            domains = CdnCache.get(ctx, force) { it.cdn.list() }
        }
    }

    LaunchedEffect(ConnectedNodes.proxyIds) { load() }

    val canCreate = nodes.isNotEmpty() || availability.canCreate
    ScreenScaffold(
        title = Tabs.getLabel(Tab.cdn),
        navController = navController,
        mainPadding = mainPadding,
        fab = {
            if (req.notAvailable == null) {
                FloatingActionButton(
                    onClick = {
                        if (nodes.isNotEmpty()) {
                            // Hosts der Nodes, die noch nicht im CDN64 sind
                            req.launch(scope, spinner = "Loading...", onError = { req.nodeError(it) }) {
                                choices = nodes.flatMap { n ->
                                    // Ohne CDN-Status (online() fehlgeschlagen) alle Hosts anbieten; die Node lehnt Doppelte ab
                                    val used = n.status?.names?.mapNotNull { it.hostId }?.toSet().orEmpty()
                                    val hosts = try {
                                        (nodeOf(ctx, n.id) ?: return@flatMap emptyList()).proxy.hosts.list()
                                    } catch (_: IPv64Exception) {
                                        return@flatMap emptyList()
                                    }
                                    hosts.filter { it.id != null && it.id !in used }
                                        .map { CdnHostChoice(n.id, n.name, it.id!!, it.names.joinToString().ifBlank { "#${it.id}" }) }
                                }
                            }
                            return@FloatingActionButton
                        }
                        if (!canCreate) {
                            req.cdnLimitMessage(availability)
                            return@FloatingActionButton
                        }
                        // Ohne geladene Liste lässt sich nicht ausschließen, welche Domains schon im CDN sind
                        if (domains == null) load(true) else showPicker = true
                    },
                    containerColor = if (canCreate) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (canCreate) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ) {
                    Icon(painter = painterResource(R.drawable.add_24px), contentDescription = "Add domain to CDN")
                }
            }
        }
    ) {
        val unavailable = req.notAvailable
        if (unavailable != null && nodes.isEmpty()) {
            NotAvailableView(PlanFeature.CDN.label, unavailable)
        } else {
            RefreshableList(
                refreshing = refreshing,
                onRefresh = { load(true, true) },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "stats") { CdnStatsCard(usage, nodes.firstNotNullOfOrNull { it.status?.quota }) }
                nodes.forEach { n ->
                    item(key = "node_${n.id}") {
                        ListGroup(n.name) {
                            val st = n.status
                            if (st == null) ListRow(title = n.error ?: "CDN64 status not available.", subtitleMaxLines = 3)
                            else {
                                val up = st.edges.count { it.connected == true }
                                ListRow(
                                    title = if (st.edges.isEmpty()) "No CDN64 servers" else "Servers: $up of ${st.edges.size} connected",
                                    subtitle = st.syncError
                                )
                                st.names.forEach { name ->
                                    GroupDivider()
                                    ListRow(
                                        title = name.name.orEmpty().ifBlank { "–" },
                                        subtitle = name.check?.takeIf { it.isNotBlank() }?.let { "Check: $it" },
                                        trailing = { StatusChip(cdnModeShort(name.mode), colorResource(R.color.ipv64_blue)) },
                                        // Ohne Host-ID (z. B. nur im Cloud-Konto) gibt es keinen Editor
                                        onClick = name.hostId?.let { hid ->
                                            {
                                                navController.navigateWithArgs(
                                                    Tab.proxy_host_edit, NavKeys.SELECTED_NODE_ID to n.id, NavKeys.PROXY_HOST_ID to hid
                                                )
                                            }
                                        }
                                    )
                                }
                                if (st.names.isEmpty()) {
                                    GroupDivider()
                                    ListRow(title = "No names in CDN64 yet. Tap + to add one.")
                                }
                            }
                        }
                    }
                }
                val limit = (availability as? FeatureAvailability.LimitReached)?.takeIf { nodes.isEmpty() }
                if (limit != null) {
                    item(key = "limit") {
                        ListGroup {
                            ListRow(
                                title = "Plan limit reached (${limit.used} of ${limit.limit} domains)",
                                subtitle = "Remove a domain from the CDN or upgrade your plan to add more.",
                                subtitleMaxLines = 3
                            )
                        }
                    }
                }
                val list = domains.orEmpty()
                if (nodes.isNotEmpty()) {
                    if (list.isNotEmpty()) item(key = "old_cdn") {
                        ListGroup("Old CDN") {
                            list.forEachIndexed { i, cdn ->
                                if (i > 0) GroupDivider()
                                CdnRow(cdn) { navController.navigateWithArgs(Tab.cdn_details, NavKeys.SELECTED_CDN_TOKEN to it.id) }
                            }
                        }
                    }
                } else if (domains != null && list.isEmpty()) {
                    item(key = "empty_state") { EmptyState("No domains in the CDN yet. Tap + to add one.") }
                } else if (list.isNotEmpty()) {
                    item(key = "domains") {
                        ListGroup {
                            list.forEachIndexed { i, cdn ->
                                if (i > 0) GroupDivider()
                                CdnRow(cdn) {
                                    navController.navigateWithArgs(Tab.cdn_details, NavKeys.SELECTED_CDN_TOKEN to it.id)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    choices?.let { list ->
        CdnEnableFlow(
            list,
            onEnable = { c, mode, hc ->
                val node = nodeOf(ctx, c.nodeId)
                if (node != null) req.launch(scope, spinner = "Enabling CDN64...", onError = { req.nodeError(it) }) {
                    node.enableCdn64(c.hostId, mode, hc)?.let { req.message("CDN64", it, R.drawable.error_24px) }
                    load(true)
                }
            },
            onDismiss = { choices = null }
        )
    }
    if (showPicker) {
        DomainPickerDialog(
            exclude = domains.orEmpty().mapNotNull { it.domain }.toSet(),
            title = "Add domain to CDN",
            onPick = { name ->
                showPicker = false
                req.addCdnDomain(scope, ctx, navController, name) { domains = CdnCache.peek() }
            },
            onDismiss = { showPicker = false }
        )
    }
    RequestStateHost(req)
}

// Zeile der Liste (für eine ListGroup): nur Daten aus der Liste, keine Einzelabfragen
@Composable
private fun CdnRow(cdn: CdnDomain, onClick: (CdnDomain) -> Unit) {
    val server = cdn.server?.name?.takeIf { it.isNotBlank() }?.let { "Server $it · " }.orEmpty()
    val proxy = "Proxy: " + cdn.proxy?.state.proxyLabel() +
            if (cdn.portmapper?.state == de.sr.ipv64.model.CdnPortmapperState.ON) " · Port mapper" else ""
    ListRow(
        title = cdn.displayName(),
        subtitle = server + cdn.trafficMb.trafficText() + "\n" + proxy,
        trailing = {
            if (cdn.isBusy()) StatusChip("Working", colorResource(R.color.ipv64_orange))
            else if (cdn.active == true) StatusChip("Active", colorResource(R.color.ipv64_green))
            else StatusChip("Inactive", colorResource(R.color.ipv64_gray))
        },
        onClick = { onClick(cdn) }
    )
}
