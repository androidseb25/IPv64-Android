package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.Hc2AccountCache
import de.rpicloud.ipv64net.helper.Hc2CheckListCache
import de.rpicloud.ipv64net.helper.Hc2NavKeys
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.helper.pollUntil
import de.rpicloud.ipv64net.helper.rememberNavArg
import de.rpicloud.ipv64net.main.views.HealthcheckItemView
import de.rpicloud.ipv64net.main.views.common.ConfirmDialog
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.InfoRow
import de.rpicloud.ipv64net.main.views.common.NotAvailableView
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.SectionHeader
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.model.Hc2Command
import de.sr.ipv64.model.Hc2NodeCommandName
import de.sr.ipv64.model.Hc2NodeDetail
import de.sr.ipv64.model.UpdateNodeRequest
import de.sr.ipv64.IPv64Exception
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Details einer Node. Anfragen: ein GET beim Öffnen (Node samt Checks), danach eine pro Aktion.
 * Der Befehlsverlauf (Limit 20) wird erst beim Aufklappen geladen. Nach einem Befehl (nur bei Fernverwaltung
 * "full") wird der Verlauf alle 3 s (höchstens 60 s) abgefragt, bis der Befehl abgeschlossen ist (doneAt gesetzt).
 */
@Composable
fun NodeDetailView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    val id = navController.rememberNavArg<String>(NavKeys.SELECTED_NODE_ID)

    var node by remember { mutableStateOf<Hc2NodeDetail?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<NodeCommand?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var showUnlink by remember { mutableStateOf(false) }

    var historyOpen by remember { mutableStateOf(false) }
    var commands by remember { mutableStateOf<List<Hc2Command>>(emptyList()) }
    var commandsLoaded by remember { mutableStateOf(false) }
    var commandsLoading by remember { mutableStateOf(false) }
    var tracking by remember { mutableStateOf(false) }
    var trackJob by remember { mutableStateOf<Job?>(null) }

    fun load(pull: Boolean = false) {
        val nid = id ?: return
        if (pull) refreshing = true
        req.launch(
            scope,
            spinner = if (pull || node != null) null else "Loading...",
            retry = { load() },
            planGate = true,
            onFinally = { refreshing = false }
        ) {
            val loaded = ipv64Retrying { IPv64Client.get(ctx).nodes.get(nid) }
            node = loaded
            upsertCachedNode(loaded.toNode())
        }
    }

    fun loadCommands() {
        val nid = id ?: return
        commandsLoading = true
        req.launch(scope, spinner = null, onFinally = { commandsLoading = false }) {
            commands = ipv64Retrying { IPv64Client.get(ctx).nodes.commands(nid, 20) }
            commandsLoaded = true
        }
    }

    // Wartet (3 s Takt, höchstens 60 s), bis die Node den Befehl abgeschlossen hat
    fun track(sent: Hc2Command) {
        val nid = id ?: return
        val cid = sent.commandId
        if (cid == null) {
            loadCommands()
            return
        }
        if (trackJob?.isActive == true) return
        tracking = true
        // Eigener Job statt req.launch: das Warten soll Bearbeiten/Trennen nicht sperren (kein isBusy)
        trackJob = scope.launch {
          try {
            val list = pollUntil(
                intervalMs = 3_000,
                maxMs = 60_000,
                onEach = { l: List<Hc2Command> ->
                    commands = l
                    commandsLoaded = true
                },
                fetch = { IPv64Client.get(ctx).nodes.commands(nid, 20) },
                done = { l -> l.any { it.commandId == cid && it.doneAt != null } }
            )
            val finished = list?.firstOrNull { it.commandId == cid }
            if (finished == null) {
                req.message(
                    "Command still pending",
                    "The node has not reported back yet. It runs the command as soon as it syncs. Open the command history later to see the result."
                )
            } else {
                if (finished.ok == false) {
                    req.message(
                        "Command failed",
                        finished.output.outputText() ?: "The node reported an error."
                    )
                }
                // Check-Zustände können sich geändert haben
                Hc2CheckListCache.invalidate()
                load()
            }
          } catch (e: IPv64Exception) {
            req.error(e)
          } finally {
            tracking = false
          }
        }
    }

    fun send(cmd: NodeCommand) {
        val nid = id ?: return
        if (!node?.remoteMgmt.allowsCommands()) return
        req.launch(scope, spinner = "Sending command...") {
            val sent = ipv64Retrying { IPv64Client.get(ctx).nodes.sendCommand(nid, cmd.name.value, cmd.args) }
            historyOpen = true
            commands = listOf(sent) + commands.filter { sent.commandId == null || it.commandId != sent.commandId }
            track(sent)
        }
    }

    fun save(name: String?, location: String?, mgmt: de.sr.ipv64.model.Hc2RemoteMgmt?) {
        val nid = id ?: return
        req.launch(scope, spinner = "Saving...") {
            val result = ipv64Retrying {
                IPv64Client.get(ctx).nodes.update(nid, UpdateNodeRequest(name = name, location = location, remoteMgmt = mgmt))
            }
            val updated = node?.withUpdate(result, name, location, mgmt) ?: return@launch
            node = updated
            upsertCachedNode(updated.toNode())
            if (mgmt != null) Hc2CheckListCache.invalidate()
        }
    }

    fun unlink(keepChecks: Boolean) {
        val nid = id ?: return
        req.launch(scope, spinner = "Unlinking...") {
            val result = ipv64Retrying { IPv64Client.get(ctx).nodes.unlink(nid, keepChecks) }
            if (result.unlinked == false) {
                req.message("Node not unlinked", "The server did not unlink the node. Please try again.")
                return@launch
            }
            trackJob?.cancel()
            removeCachedNode(nid)
            Hc2AccountCache.invalidate()
            Hc2CheckListCache.invalidate()
            val counts = listOfNotNull(
                result.checksKeptOnline?.let { "$it check" + (if (it == 1) "" else "s") + " continued as online check" + (if (it == 1) "" else "s") },
                result.checksDeleted?.let { "$it check" + (if (it == 1) "" else "s") + " deleted" }
            ).joinToString(", ")
            req.message(
                "Node unlinked",
                (if (counts.isNotEmpty()) "$counts. " else "") + "The node keeps running standalone.",
                onClose = { navController.popBackStack() }
            )
        }
    }

    LaunchedEffect(Unit) { load() }

    val n = node
    val busy = req.isBusy

    ScreenScaffold(
        title = n?.displayName() ?: Tabs.getLabel(Tab.node_details),
        navController = navController,
        mainPadding = mainPadding,
        actions = {
            IconButton(enabled = n != null && !busy, onClick = { showEdit = true }) {
                Icon(painter = painterResource(R.drawable.edit_24px), contentDescription = "Edit")
            }
            IconButton(enabled = n != null && !busy, onClick = { showUnlink = true }) {
                Icon(painter = painterResource(R.drawable.link_off_24px), contentDescription = "Unlink node")
            }
        }
    ) {
        val unavailable = req.notAvailable
        when {
            id == null -> EmptyState("Node not found.")
            unavailable != null -> NotAvailableView("Nodes", unavailable)
            n == null -> Box(modifier = Modifier)
            else -> RefreshableList(
                refreshing = refreshing,
                onRefresh = {
                    load(pull = true)
                    if (historyOpen && !tracking) loadCommands()
                }
            ) {
                item(key = "header") { NodeHeader(n) }

                item(key = "commands") {
                    NodeCommandActions(
                        node = n,
                        enabled = !busy && !tracking,
                        onPick = { confirm = it }
                    )
                }
                if (tracking) {
                    item(key = "tracking") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                "Waiting for the node to run the command…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item(key = "checks_header") {
                    SectionHeader("Checks (${n.checks.size})")
                }
                if (n.checks.isEmpty()) {
                    item(key = "checks_empty") { EmptyState("This node has no checks yet.") }
                }
                items(n.checks.size, key = { "check_${n.checks[it].token ?: it}" }) { index ->
                    HealthcheckItemView(n.checks[index]) { check ->
                        check.token?.let { token ->
                            navController.navigateWithArgs(Tab.healthcheck_details, Hc2NavKeys.SELECTED_HC2_TOKEN to token)
                        }
                    }
                }

                nodeCommandHistory(
                    open = historyOpen,
                    loading = commandsLoading,
                    loaded = commandsLoaded,
                    commands = commands,
                    checks = n.checks,
                    onToggle = {
                        historyOpen = !historyOpen
                        if (historyOpen && !commandsLoaded && !commandsLoading && !tracking) loadCommands()
                    },
                    onRefresh = { if (!tracking) loadCommands() }
                )
            }
        }
    }

    // ---- Befehl bestätigen (nur bei Fernverwaltung "full") ----
    confirm?.let { cmd ->
        ConfirmDialog(
            title = cmd.name.label() + "?",
            text = cmd.confirmText(n?.displayName() ?: "The node"),
            confirmLabel = "Send",
            destructive = cmd.name == Hc2NodeCommandName.RESOLVE_CONFLICT,
            onConfirm = {
                confirm = null
                send(cmd)
            },
            onDismiss = { confirm = null }
        )
    }

    if (showEdit && n != null) {
        NodeEditDialog(
            name = n.name.orEmpty(),
            location = n.location.orEmpty(),
            remoteMgmt = n.remoteMgmt ?: de.sr.ipv64.model.Hc2RemoteMgmt.OFF,
            onSave = { name, location, mgmt ->
                showEdit = false
                save(name, location, mgmt)
            },
            onDismiss = { showEdit = false }
        )
    }

    if (showUnlink && n != null) {
        NodeUnlinkDialog(
            nodeName = n.name.orEmpty(),
            onConfirm = { keep ->
                showUnlink = false
                unlink(keep)
            },
            onDismiss = { showUnlink = false }
        )
    }

    RequestStateHost(req)
}

@Composable
private fun NodeHeader(node: Hc2NodeDetail) {
    val online = node.online == true
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    node.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(
                    if (online) "Online" else "Offline",
                    colorResource(if (online) R.color.ipv64_green else R.color.ipv64_gray)
                )
            }
            InfoRow("Location", node.location)
            InfoRow("Remote management", node.remoteMgmt.label())
            InfoRow("Version", node.version)
            InfoRow("Time zone", node.timezone)
            node.hub?.takeIf { it.isNotBlank() }?.let { InfoRow("Hub", it) }
            InfoRow("Modules", node.modules.joinToString(", ").takeIf { it.isNotEmpty() })
            InfoRow("Last seen", node.lastSeen?.formatRelative())
            InfoRow("Last sync", node.lastSync?.formatRelative())
            InfoRow("Outbox", node.outboxSize?.toString())
            InfoRow("Conflicts", node.conflicts?.toString())
            InfoRow("Linked", node.linkedAt?.formatGermanTime())
            InfoRow("Node ID", node.nodeId, copyable = true, monospace = true)
        }
    }
}
