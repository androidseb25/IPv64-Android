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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.AccountCache
import de.rpicloud.ipv64net.helper.FeatureAvailability
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.PlanFeature
import de.rpicloud.ipv64net.helper.availability
import de.rpicloud.ipv64net.helper.canCreate
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.isLimitReached
import de.rpicloud.ipv64net.helper.isPlanRestricted
import de.rpicloud.ipv64net.helper.loadMe
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.helper.planRestrictionText
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.GroupDivider
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.NotAvailableView
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64Exception

/**
 * DNS64-Client-Liste. Anfragen: loadMe (60 s Cache, nur für das Plan-Gate) und die Client-Liste (30 s Cache).
 * Beim Anlegen (202) wird der neue Client in den Cache geschrieben und direkt die Detailansicht geöffnet;
 * diese fragt den Client ab und wartet, bis er "ready" ist.
 */
@Composable
fun Dns64View(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()

    var clients by remember { mutableStateOf(Dns64Cache.peek(ctx)) }
    var availability by remember { mutableStateOf<FeatureAvailability>(FeatureAvailability.Unknown) }
    var refreshing by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }

    fun load(force: Boolean = false, pull: Boolean = false) {
        if (pull) refreshing = true
        req.notAvailable = null
        req.launch(
            scope,
            spinner = if (pull || clients != null) null else "Loading...",
            retry = { load(true) },
            planGate = true,
            onFinally = { refreshing = false }
        ) {
            // Plan-Prüfung ist nicht kritisch: bei Fehlern entscheidet der Server beim Laden der Liste
            try {
                availability = loadMe(ctx, force).availability(PlanFeature.DNS64)
            } catch (e: IPv64Exception) {
                availability = FeatureAvailability.Unknown
            }
            if (availability is FeatureAvailability.NotInPlan) {
                req.notAvailable = "Your plan does not include DNS64."
                return@launch
            }
            clients = Dns64Cache.get(ctx, force) { it.dns64.listClients() }
        }
    }

    fun create(name: String) {
        req.launch(
            scope,
            spinner = "Creating client...",
            onError = { e ->
                if (e.isLimitReached() || e.isPlanRestricted()) {
                    req.message("Plan limit reached", e.planRestrictionText())
                    true
                } else false
            }
        ) {
            val created = ipv64Retrying { IPv64Client.get(ctx).dns64.createClient(name.ifBlank { null }) }
            // Liste mit dem neuen Client fortschreiben (ohne weitere Anfrage)
            dns64CacheUpsert(created)
            AccountCache.invalidate()
            clients = clients?.let { list -> list.filterNot { it.id == created.id } + created }
            val token = created.id
            if (token != null) navController.navigateWithArgs(Tab.dns64_details, NavKeys.SELECTED_DNS64_TOKEN to token)
        }
    }

    LaunchedEffect(Unit) { load() }

    val canCreate = availability.canCreate
    ScreenScaffold(
        title = Tabs.getLabel(Tab.dns64),
        navController = navController,
        mainPadding = mainPadding,
        fab = {
            if (req.notAvailable == null) {
                FloatingActionButton(
                    onClick = {
                        if (canCreate) showCreate = true
                        else (availability as? FeatureAvailability.LimitReached)?.let {
                            req.message("Plan limit reached", "Your plan allows ${it.limit} DNS64 clients. Delete one or upgrade your plan.")
                        }
                    },
                    containerColor = if (canCreate) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (canCreate) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ) {
                    Icon(painter = painterResource(R.drawable.add_24px), contentDescription = "New DNS64 client")
                }
            }
        }
    ) {
        val unavailable = req.notAvailable
        val nodeIds = ConnectedNodes.ids
        if (unavailable != null && nodeIds.isEmpty()) {
            NotAvailableView(PlanFeature.DNS64.label, unavailable)
        } else {
            RefreshableList(
                refreshing = refreshing,
                onRefresh = { load(true, true) },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Lokales DNS der direkt verbundenen Nodes (keine Anfrage hier; Details im Modul-Screen)
                if (nodeIds.isNotEmpty()) {
                    item(key = "local_dns") {
                        ListGroup(title = "Local DNS") {
                            nodeIds.forEachIndexed { i, id ->
                                if (i > 0) GroupDivider()
                                ListRow(
                                    title = LocalNodeStore.name(ctx, id) ?: id,
                                    subtitle = "Status, queries and top lists",
                                    icon = R.drawable.dns_24px,
                                    onClick = {
                                        navController.navigateWithArgs(Tab.node_dns, NavKeys.SELECTED_NODE_ID to id)
                                    }
                                )
                            }
                        }
                    }
                }
                if (unavailable != null) {
                    item(key = "not_in_plan") {
                        ListGroup(title = "DNS64 clients") { ListRow(title = "Not available on your plan", subtitle = unavailable, showChevron = false) }
                    }
                    return@RefreshableList
                }
                val limit = availability as? FeatureAvailability.LimitReached
                if (limit != null) {
                    item(key = "limit") {
                        ListGroup {
                            ListRow(
                                title = "Plan limit reached (${limit.used} of ${limit.limit} clients)",
                                subtitle = "Delete a client or upgrade your plan to create more.",
                                subtitleMaxLines = 3
                            )
                        }
                    }
                }
                val list = clients.orEmpty()
                if (clients != null && list.isEmpty()) {
                    item(key = "empty_state") {
                        EmptyState("No DNS64 clients yet. A client gives you a personal DNS resolver with optional filtering.")
                    }
                } else if (list.isNotEmpty()) {
                    item(key = "clients") {
                        ListGroup {
                            list.forEachIndexed { i, client ->
                                if (i > 0) GroupDivider()
                                Dns64Row(client) {
                                    val token = it.id ?: return@Dns64Row
                                    navController.navigateWithArgs(Tab.dns64_details, NavKeys.SELECTED_DNS64_TOKEN to token)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        Dns64NameDialog(
            title = "New DNS64 client",
            initial = "",
            confirmLabel = "Create",
            required = false,
            onConfirm = { name ->
                showCreate = false
                create(name)
            },
            onDismiss = { showCreate = false }
        )
    }
    RequestStateHost(req)
}
