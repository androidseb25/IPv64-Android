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
import de.rpicloud.ipv64net.helper.AccountCache
import de.rpicloud.ipv64net.helper.FeatureAvailability
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.PlanFeature
import de.rpicloud.ipv64net.helper.canCreate
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.loadMe
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.helper.openUrl
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
import kotlinx.coroutines.async

@Composable
fun CdnView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()

    var domains by remember { mutableStateOf(CdnCache.peek(ctx)) }
    var cloud by remember { mutableStateOf(CloudCdnCache.peek(ctx)) }
    var availability by remember { mutableStateOf<FeatureAvailability>(FeatureAvailability.Unknown) }
    var usage by remember { mutableStateOf<de.sr.ipv64.model.MeUsage?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var nodes by remember { mutableStateOf<List<NodeOnline>>(emptyList()) }
    var choices by remember { mutableStateOf<List<CdnHostChoice>?>(null) }

    fun load(force: Boolean = false, pull: Boolean = false) {
        if (pull) refreshing = true
        req.notAvailable = null
        req.launch(
            scope,
            spinner = if (pull || cloud != null) null else "Loading...",
            retry = { load(true) },
            planGate = true,
            onFinally = { refreshing = false }
        ) {
            val nodeTask = async { loadNodeOnline(ctx) }
            val oldCdnTask = async {
                try {
                    CdnCache.get(ctx, force) { it.cdn.list() }
                } catch (e: IPv64Exception) {
                    // The old CDN is separate and can be unavailable during its shutdown.
                    null
                }
            }
            val cloudError = try {
                cloud = CloudCdnCache.get(ctx, force) { it.loadCloudCdn() }
                null
            } catch (e: IPv64Exception) {
                e
            }
            nodes = nodeTask.await()
            domains = oldCdnTask.await()
            val quota = cloud?.quota?.domains
            val limit = quota?.limit
            val used = quota?.used ?: 0
            availability = when {
                limit == null -> FeatureAvailability.Unknown
                limit == 0 -> FeatureAvailability.NotInPlan
                used >= limit && limit > 0 -> FeatureAvailability.LimitReached(used, limit)
                else -> FeatureAvailability.Available
            }
            if (cloudError != null && nodes.isEmpty()) throw cloudError
            if (cloud?.quota == null) {
                try { usage = loadMe(ctx, force).usage } catch (_: IPv64Exception) { }
            }
            if (availability is FeatureAvailability.NotInPlan && nodes.isEmpty() && domains.isNullOrEmpty() && cloud?.hosts.isNullOrEmpty()) {
                req.notAvailable = "Your plan does not include CDN64."
            }
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
                        if (req.isBusy) return@FloatingActionButton
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
                                        .map { CdnHostChoice(n.id, n.name, it.id!!, it.sortedNames()) }
                                }
                            }
                            return@FloatingActionButton
                        }
                        if (!canCreate) {
                            req.cdnLimitMessage(availability)
                            return@FloatingActionButton
                        }
                        showPicker = true
                    },
                    containerColor = if (canCreate) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (canCreate) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ) {
                    Icon(painter = painterResource(R.drawable.add_24px), contentDescription = "Add domain to CDN64")
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
                item(key = "stats") { CdnStatsCard(usage, cloud?.quota ?: nodes.firstNotNullOfOrNull { it.status?.quota }) }
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
                                st.names.sortedBy { it.name.orEmpty().lowercase() }.forEach { name ->
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
                val nodeNames = nodes.flatMap { it.status?.names.orEmpty() }.mapNotNull { it.name?.lowercase() }.toSet()
                val hosts = cloud?.hosts.orEmpty().filterNot { it.fqdn.lowercase() in nodeNames }.sortedBy { it.fqdn.lowercase() }
                if (hosts.isNotEmpty()) {
                    item(key = "cloud_cdn") {
                        ListGroup("Cloud domains") {
                            hosts.forEachIndexed { index, host ->
                                if (index > 0) GroupDivider()
                                val status = if (host.paused) "Paused" else host.status?.replaceFirstChar { it.uppercase() }
                                ListRow(
                                    title = host.fqdn,
                                    subtitle = status,
                                    trailing = { StatusChip(cdnModeShort(host.mode), colorResource(R.color.ipv64_blue)) },
                                    onClick = { openUrl(ctx, "https://ipv64.net/cdn64") }
                                )
                            }
                        }
                    }
                } else if (cloud != null && nodes.isEmpty()) {
                    item(key = "empty_state") { EmptyState("No domains in CDN64 yet. Tap + to add one.") }
                }
                val list = domains.orEmpty().sortedBy { it.displayName().lowercase() }
                if (list.isNotEmpty()) {
                    item(key = "old_cdn") {
                        ListGroup("Old CDN") {
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
        CloudCdnEnableFlow(
            exclude = (cloud?.hosts.orEmpty().map { it.fqdn } + domains.orEmpty().mapNotNull { it.domain }).map { it.lowercase() }.toSet(),
            onEnable = { input ->
                showPicker = false
                req.launch(scope, spinner = "Enabling CDN64...") {
                    val created = ipv64Retrying { IPv64Client.get(ctx).enableCloudCdn(input) }
                    cloud = CloudCdnOverview(cloud?.quota, cloud?.hosts.orEmpty().filterNot { it.fqdn.equals(created.fqdn, true) } + created)
                    CloudCdnCache.invalidate()
                    AccountCache.invalidate()
                    load(true)
                }
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
