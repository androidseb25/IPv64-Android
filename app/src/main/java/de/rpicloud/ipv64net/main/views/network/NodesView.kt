package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.Hc2AccountCache
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.helper.pollUntil
import de.rpicloud.ipv64net.helper.toError
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.GroupDivider
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.NotAvailableView
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.main.views.inlineText
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.rpicloud.ipv64net.models.User
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.sr.ipv64.model.Hc2Node
import de.sr.ipv64.model.Hc2RemoteMgmt
import de.sr.ipv64.model.LinkNodeRequest
import kotlinx.coroutines.Job

/**
 * Node64-Liste. Anfragen: eine (Nodes-Liste, 30 s Cache), keine pro Zeile.
 * Verknüpfen: Code, Standort, Fernverwaltung; danach wird die Liste alle 5 s (höchstens 60 s) abgefragt,
 * bis eine Node mehr erscheint (die Node holt sich ihren Token selbst).
 */
@Composable
fun NodesView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()

    var nodes by remember { mutableStateOf(NodeListCache.peek(ctx)) }
    var refreshing by remember { mutableStateOf(false) }
    var showLink by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showDirect by remember { mutableStateOf(false) }
    // Direkt verbundene Nodes (LocalNodeStore) dieses Accounts; bei jeder Rückkehr neu gelesen
    var directIds by remember { mutableStateOf(emptyList<String>()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        directIds = LocalNodeStore.list(ctx, User.current?.id?.toString().orEmpty())
    }
    var linkBusy by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }
    var waiting by remember { mutableStateOf(false) }
    var pollJob by remember { mutableStateOf<Job?>(null) }

    fun load(force: Boolean = false, pull: Boolean = false) {
        if (pull) refreshing = true
        req.notAvailable = null
        req.launch(
            scope,
            spinner = if (pull || nodes != null) null else "Loading...",
            retry = { load(true) },
            planGate = true,
            onFinally = { refreshing = false }
        ) {
            nodes = NodeListCache.get(ctx, force) { it.nodes.list() }
        }
    }

    // Wartet (5 s Takt, höchstens 60 s), bis eine neue (oder neu verknüpfte) Node in der Liste auftaucht
    fun waitForNode(before: Map<String?, Any?>) {
        if (pollJob?.isActive == true) return
        waiting = true
        pollJob = req.launch(scope, spinner = null, onFinally = { waiting = false }) {
            val found = pollUntil(
                intervalMs = 5_000,
                maxMs = 60_000,
                onEach = { list: List<Hc2Node> ->
                    nodes = list
                    NodeListCache.put(list)
                },
                fetch = { IPv64Client.get(ctx).nodes.list() },
                done = { l -> l.any { n -> n.nodeId !in before || before[n.nodeId] != n.linkedAt } }
            )
            Hc2AccountCache.invalidate()
            if (found == null) {
                req.message(
                    "Node not visible yet",
                    "The node has not fetched its token yet. Make sure it is online and has internet access, then pull down to refresh."
                )
            }
        }
    }

    fun link(code: String, location: String?, mgmt: Hc2RemoteMgmt) {
        linkBusy = true
        linkError = null
        req.launch(
            scope,
            spinner = null,
            onError = { e ->
                linkError = e.toError().inlineText()
                true
            },
            onFinally = { linkBusy = false }
        ) {
            // Stand vor dem Verknüpfen (frisch geladen): ID -> Verknüpfungszeit
            val current = NodeListCache.get(ctx, force = true) { it.nodes.list() }
            nodes = current
            val before = current.associate { it.nodeId to (it.linkedAt as Any?) }
            val result = ipv64Retrying {
                IPv64Client.get(ctx).nodes.link(LinkNodeRequest(code = code, location = location, remoteMgmt = mgmt))
            }
            if (result.accepted == false) {
                linkError = result.detail?.takeIf { it.isNotBlank() } ?: "The code was not accepted."
                return@launch
            }
            showLink = false
            NodeListCache.invalidate()
            Hc2AccountCache.invalidate()
            waitForNode(before)
        }
    }

    LaunchedEffect(Unit) { load() }

    ScreenScaffold(
        title = Tabs.getLabel(Tab.nodes),
        navController = navController,
        mainPadding = mainPadding,
        fab = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(painter = painterResource(R.drawable.add_24px), contentDescription = "Add node")
            }
        }
    ) {
        val unavailable = req.notAvailable
        val list = nodes.orEmpty()
        // Nur Nodes, die nicht (mehr) in der Cloud-Liste stehen; ohne Cloud-Liste (Fehler/Plan) alle
        val cloudIds = list.mapNotNull { it.nodeId }.toSet()
        val direct = directIds.filter { it !in cloudIds }
        if (unavailable != null && direct.isEmpty()) {
            NotAvailableView("Nodes", unavailable)
        } else {
            RefreshableList(
                refreshing = refreshing,
                onRefresh = { load(true, true) },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (waiting) {
                    item(key = "waiting") {
                        ListGroup {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Waiting for the node", style = MaterialTheme.typography.titleMedium)
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    "The node fetches its token itself. This usually takes less than a minute.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (nodes != null && list.isEmpty() && direct.isEmpty()) {
                    item(key = "empty_state") {
                        EmptyState("No nodes yet. Tap + to link a node with the code your Node64 shows under \"ipv64 link\", or connect to one directly.")
                    }
                } else if (list.isNotEmpty() || direct.isNotEmpty()) {
                    item(key = "nodes") {
                        ListGroup {
                            list.forEachIndexed { i, node ->
                                if (i > 0) GroupDivider()
                                NodeRow(node) {
                                    navController.navigateWithArgs(Tab.node_details, NavKeys.SELECTED_NODE_ID to it.nodeId)
                                }
                            }
                            direct.forEachIndexed { i, nid ->
                                if (i > 0 || list.isNotEmpty()) GroupDivider()
                                DirectNodeRow(nid) {
                                    navController.navigateWithArgs(Tab.node_details, NavKeys.SELECTED_NODE_ID to nid)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddNodeDialog(
            canLink = req.notAvailable == null,
            onLink = {
                showAdd = false
                linkError = null
                showLink = true
            },
            onDirect = {
                showAdd = false
                showDirect = true
            },
            onDismiss = { showAdd = false }
        )
    }

    if (showLink) {
        LinkNodeDialog(
            busy = linkBusy,
            error = linkError,
            onConfirm = { code, location, mgmt -> link(code, location, mgmt) },
            onDismiss = { showLink = false }
        )
    }

    if (showDirect) NodeLocalSheet(null, onDismiss = { showDirect = false }, onSaved = { newId ->
        showDirect = false
        navController.navigateWithArgs(Tab.node_details, NavKeys.SELECTED_NODE_ID to newId)
    })

    RequestStateHost(req)
}
