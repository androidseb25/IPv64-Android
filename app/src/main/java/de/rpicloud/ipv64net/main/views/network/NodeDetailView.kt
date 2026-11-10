package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.padding
import de.sr.ipv64.Node64ApiException
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.Hc2AccountCache
import de.rpicloud.ipv64net.helper.Hc2CheckListCache
import de.rpicloud.ipv64net.helper.Hc2NavKeys
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.formatBytes
import de.rpicloud.ipv64net.helper.formatDuration
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.helper.rememberNavArg
import de.rpicloud.ipv64net.main.views.HealthcheckRow
import de.rpicloud.ipv64net.main.views.common.ConfirmDialog
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
import de.rpicloud.ipv64net.main.views.common.InfoItem
import de.rpicloud.ipv64net.main.views.common.InfoRows
import de.rpicloud.ipv64net.main.views.common.GroupText
import de.rpicloud.ipv64net.main.views.common.StatTile
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.model.Hc2Command
import de.sr.ipv64.model.Hc2CommandArgs
import de.sr.ipv64.model.Hc2NodeCommandName
import de.sr.ipv64.model.Hc2NodeDetail
import de.sr.ipv64.model.UpdateNodeRequest
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.IPv64ApiException
import de.rpicloud.ipv64net.helper.isPlanRestricted
import de.sr.ipv64.node64.Node64
import de.sr.ipv64.node64.api.Dns64Api
import de.sr.ipv64.node64.model.DNS64Blocking
import de.sr.ipv64.node64.model.DNS64Status
import de.sr.ipv64.node64.model.NodeStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Details einer Node (Cloud + optional lokaler Zugang). Anfragen: ein GET beim Öffnen (Node samt Checks), danach eine pro Aktion.
 * Der Befehlsverlauf (Limit 20) wird erst beim Aufklappen geladen. Nach einem Befehl (nur bei Fernverwaltung
 * "full") wird der Verlauf alle 3 s (höchstens 60 s) abgefragt, bis der Befehl abgeschlossen ist (doneAt gesetzt).
 * Mit gespeichertem lokalem Zugang (LocalNodeStore) kommen Live-Daten der Node64-API dazu: status() + logs(100), solange der
 * Screen sichtbar ist (RESUMED) alle [REFRESH_MS] still nachgeladen.
 * Direkte Node (nur lokaler Zugang): kennt die Cloud die ID nicht (404) oder sperrt der Plan Nodes, zeigt der Screen nur
 * die lokalen Daten (ohne Checks, Befehle, Bearbeiten, Trennen).
 */
@Composable
fun NodeDetailView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    val id = navController.rememberNavArg<String>(NavKeys.SELECTED_NODE_ID)

    var node by remember { mutableStateOf<Hc2NodeDetail?>(null) }
    var directOnly by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<NodeCommand?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var showDirect by remember { mutableStateOf(false) }
    var showUnlink by remember { mutableStateOf(false) }
    var showResolve by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }

    var historyOpen by remember { mutableStateOf(false) }
    var commands by remember { mutableStateOf<List<Hc2Command>>(emptyList()) }
    var commandsLoaded by remember { mutableStateOf(false) }
    var commandsLoading by remember { mutableStateOf(false) }
    var tracking by remember { mutableStateOf(false) }
    var trackJob by remember { mutableStateOf<Job?>(null) }

    // Lokaler Zugang (null = nur Cloud) und dessen Live-Daten
    var config by remember(id) { mutableStateOf(id?.let { LocalNodeStore.get(ctx, it) }) }
    var status by remember { mutableStateOf<NodeStatus?>(null) }
    // false, wenn das erste Laden scheiterte: dann kein Spinner mehr, der Fehlerdialog bietet Retry
    var loadFailed by remember { mutableStateOf(false) }
    var disableModule by remember { mutableStateOf<String?>(null) }
    var showForget by remember { mutableStateOf(false) }
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }

    // Live-Aktualisierung: Zeitpunkte (ms), Fehlerzustand des letzten stillen Ladens, Lifecycle, Uhr für "Updated …"
    var lastOk by remember { mutableLongStateOf(0L) }
    var lastAttempt by remember { mutableLongStateOf(0L) }
    var failing by remember { mutableStateOf(false) }
    var tickError by remember { mutableStateOf<IPv64Exception?>(null) }
    // ID einer anderen Node unter der gespeicherten Adresse (null = passt)
    var otherNodeId by remember { mutableStateOf<String?>(null) }
    var resumed by remember { mutableStateOf(false) }
    val clock = remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Nie zwei Aktualisierungen gleichzeitig; Nutzeraktionen warten, der stille Tick wird übersprungen
    val refreshLock = remember { Mutex() }

    fun load(pull: Boolean = false) {
        val nid = id ?: return
        if (pull) refreshing = true
        loadFailed = false
        req.launch(
            scope,
            // Erstes Laden zeigt den Spinner im Screen (bis auch der lokale Status da ist), kein Dialog
            spinner = null,
            retry = { load() },
            planGate = true,
            // Cloud kennt die Node nicht (oder kein Nodes-Plan), aber ein lokaler Zugang existiert: direkte Node
            onError = { e ->
                val direct = config != null && (e.isPlanRestricted() || (e as? IPv64ApiException)?.isNotFound == true)
                if (direct) {
                    directOnly = true
                    if (node == null) node = directNodeDetail(nid, LocalNodeStore.name(ctx, nid))
                }
                if (!direct && node == null) loadFailed = true
                direct
            },
            onFinally = { refreshing = false }
        ) {
            val loaded = ipv64Retrying { IPv64Client.get(ctx).nodes.get(nid) }
            directOnly = false
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
            val finished = awaitNodeCommand(ctx, nid, cid) { l ->
                commands = l
                commandsLoaded = true
            }
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
            config = null // stoppt die Live-Ticks; der lokale Zugang bleibt (Node läuft standalone weiter)
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

    // ---- Lokaler Zugang ----

    // Lokale Fehler als Dialog (Cloud-Fehlertexte passen hier nicht)
    fun handleLocal(e: IPv64Exception): Boolean {
        val (title, text) = localNodeError(e)
        req.message(title, text, R.drawable.error_24px)
        return true
    }

    // Alles holen, erst dann übernehmen; verworfen, wenn der Zugang inzwischen vergessen/gewechselt wurde
    suspend fun refreshFrom(c: Pair<String, String>) {
        val client = localNodeClient(c.first, c.second)
        val st = client.node.status()
        // Neueste zuerst (die Node liefert chronologisch)
        val logs = client.node.logs(lines = 100).lines.map { it.logText() }.reversed()
        if (config != c) return
        st.node?.name?.takeIf { it.isNotBlank() && it != LocalNodeStore.name(ctx, id.orEmpty()) }
            ?.let { LocalNodeStore.setName(ctx, id.orEmpty(), it) }
        // Modul-Schalter steuern die Tabs (DNS64, CDN64 ↔ Proxy64)
        id?.let { LocalNodeStore.saveModules(ctx, it, st.modules) }
        status = st
        logLines = logs
        lastOk = System.currentTimeMillis()
        failing = false
        tickError = null
        otherNodeId = st.node?.id?.takeIf { it != id }
    }

    // Stiller Tick: kein Spinner, kein Dialog; Fehler nur als Inline-Zeile
    suspend fun tick() {
        val c = config ?: return
        if (req.isBusy || !refreshLock.tryLock()) return
        try {
            refreshFrom(c)
        } catch (e: IPv64Exception) {
            if (config == c) { failing = true; tickError = e }
        } finally {
            refreshLock.unlock()
        }
    }

    // Pull-to-Refresh: wie der Tick, aber wartet auf die Sperre
    fun refreshLocal() {
        val c = config ?: return
        lastAttempt = System.currentTimeMillis()
        scope.launch {
            refreshLock.withLock {
                try {
                    refreshFrom(c)
                } catch (e: IPv64Exception) {
                    if (config == c) { failing = true; tickError = e }
                }
            }
        }
    }

    fun setModule(name: String, enable: Boolean) {
        val (url, key) = config ?: return
        req.launch(scope, onError = { handleLocal(it) }) {
            refreshLock.withLock {
                val client = localNodeClient(url, key)
                if (enable) client.node.enableModule(name) else client.node.disableModule(name)
                val st = client.node.status()
                id?.let { LocalNodeStore.saveModules(ctx, it, st.modules) }
                status = st
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    // Zurückkehren (z. B. aus den Einstellungen): gespeicherten Zugang neu lesen
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { config = id?.let { LocalNodeStore.get(ctx, it) } }

    // Auto-Refresh nur bei vorhandenem Zugang und solange RESUMED; der Tick läuft eigenständig, damit die Uhr weiterläuft
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, config) {
        if (config == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            resumed = true
            try {
                while (true) {
                    val now = System.currentTimeMillis()
                    clock.longValue = now
                    if (now - lastAttempt >= REFRESH_MS) {
                        lastAttempt = now
                        launch { tick() }
                    }
                    delay(1_000)
                }
            } finally {
                resumed = false
            }
        }
    }

    // Direkte Node: Name/Ort/Version aus dem lokalen Status
    val n = node?.let { cloud ->
        val sn = status?.node
        if (!directOnly || sn == null) cloud
        else cloud.copy(name = sn.name ?: cloud.name, location = sn.location, timezone = sn.timezone, version = sn.version)
    }
    val busy = req.isBusy
    val canCommand = n?.remoteMgmt.allowsCommands()

    ScreenScaffold(
        title = n?.displayName() ?: Tabs.getLabel(Tab.node_details),
        navController = navController,
        mainPadding = mainPadding,
        actions = {
            // Cloud-Node: Name/Ort bearbeiten; direkte Node: Verbindung (Adresse, API-Key) bearbeiten
            IconButton(
                enabled = n != null && !busy,
                onClick = {
                    if (directOnly) showDirect = true
                    else showEdit = true
                }
            ) {
                Icon(painter = painterResource(R.drawable.edit_24px), contentDescription = if (directOnly) "Edit connection" else "Edit")
            }
            Box {
                IconButton(enabled = n != null, onClick = { menuOpen = true }) {
                    Icon(painter = painterResource(R.drawable.more_vert_24px), contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (canCommand) {
                        DropdownMenuItem(
                            text = { Text(Hc2NodeCommandName.REPAIR.label()) },
                            enabled = !busy && !tracking,
                            onClick = { menuOpen = false; confirm = NodeCommand(Hc2NodeCommandName.REPAIR) }
                        )
                        if (((status?.link?.conflicts ?: n?.conflicts)?.toLong() ?: 0L) > 0L) {
                            DropdownMenuItem(
                                text = { Text(Hc2NodeCommandName.RESOLVE_CONFLICT.label()) },
                                enabled = !busy && !tracking,
                                onClick = { menuOpen = false; showResolve = true }
                            )
                        }
                    }
                    if (config != null && !directOnly) {
                        DropdownMenuItem(
                            text = { Text("Edit direct connection") },
                            enabled = !busy,
                            onClick = { menuOpen = false; showDirect = true }
                        )
                    }
                    if (config != null) {
                        DropdownMenuItem(
                            text = { Text("Forget direct connection") },
                            enabled = !busy,
                            onClick = { menuOpen = false; showForget = true }
                        )
                    }
                    if (!directOnly) {
                        DropdownMenuItem(
                            text = { Text("Unlink node", color = MaterialTheme.colorScheme.error) },
                            enabled = !busy,
                            onClick = { menuOpen = false; showUnlink = true }
                        )
                    }
                }
            }
        }
    ) {
        val unavailable = req.notAvailable
        val c = config
        val st = status
        when {
            id == null -> EmptyState("Node not found.")
            unavailable != null -> NotAvailableView("Nodes", unavailable)
            // Erst zeigen, wenn Cloud-Daten und (bei Direktzugang) der erste lokale Status da sind: kein Nachspringen
            n == null && loadFailed -> Box(modifier = Modifier)
            n == null || (c != null && st == null && !failing) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> RefreshableList(
                refreshing = refreshing,
                onRefresh = {
                    load(pull = true)
                    refreshLocal()
                    if (historyOpen && !tracking) loadCommands()
                },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "header") {
                    NodeHeader(
                        node = n,
                        status = st,
                        host = c?.first,
                        live = LiveInfo(resumed, failing, lastOk),
                        clock = clock,
                        otherNodeId = otherNodeId,
                        // Key abgelehnt / PROXY64-Anmeldeseite: statt "Connection lost" den Grund zeigen
                        rejected = (tickError as? Node64ApiException)?.takeIf { it.status == 401 || it.status == 403 }
                            ?.let { localNodeError(it).first },
                        onSetupLocal = { showDirect = true }
                    )
                }
                item(key = "stats") { NodeStatTiles(n, st, local = c != null, cloud = !directOnly) }

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

                item(key = "modules") {
                    if (c != null && st != null) {
                        ListGroup(title = "Modules (${st.modules.size})") {
                            st.modules.forEachIndexed { i, m ->
                                if (i > 0) GroupDivider()
                                val open = m.name?.takeIf { hasModuleScreen(it) && id != null && otherNodeId == null && m.enabled == true }
                                    ?.let { name -> { navController.navigateWithArgs(Tab.node_module, NavKeys.SELECTED_NODE_ID to id, NavKeys.NODE_MODULE to name) } }
                                LocalModuleRow(m, busy = busy || otherNodeId != null, onOpen = open) { on ->
                                    val name = m.name ?: return@LocalModuleRow
                                    if (on) setModule(name, true) else disableModule = name
                                }
                            }
                        }
                    } else {
                        ListGroup(title = "Modules (${n.modules.size})") {
                            n.modules.forEachIndexed { i, name ->
                                if (i > 0) GroupDivider()
                                ListRow(title = name, showChevron = false)
                            }
                            if (c == null) GroupText("Set up direct access to switch modules.")
                        }
                    }
                }

                if (!directOnly) item(key = "checks") {
                    ListGroup(title = "Checks (${n.checks.size})") {
                        if (n.checks.isEmpty()) GroupText("This node has no checks yet.")
                        else if (canCommand && n.online != true) GroupText("The node is offline. Commands are picked up when it reconnects.")
                        n.checks.forEachIndexed { index, check ->
                            if (index > 0) GroupDivider()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    HealthcheckRow(check) { ch ->
                                        ch.token?.let { token ->
                                            navController.navigateWithArgs(Tab.healthcheck_details, Hc2NavKeys.SELECTED_HC2_TOKEN to token)
                                        }
                                    }
                                }
                                if (canCommand) CheckCommandMenu(check, enabled = !busy && !tracking) { confirm = it }
                            }
                        }
                    }
                }

                if (c != null && st != null) {
                    item(key = "logs") { NodeLogsGroup(logLines) }
                }

                if (!directOnly) nodeCommandHistory(
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

                item(key = "details") {
                    ListGroup {
                        ListRow(
                            title = "Details",
                            showChevron = false,
                            trailing = {
                                Icon(
                                    painter = painterResource(if (detailsOpen) R.drawable.expand_less_24px else R.drawable.expand_more_24px),
                                    contentDescription = if (detailsOpen) "Collapse" else "Expand"
                                )
                            },
                            onClick = { detailsOpen = !detailsOpen }
                        )
                        if (detailsOpen) {
                            GroupDivider()
                            InfoRows(nodeDetailItems(n, st, c?.first, cloud = !directOnly).filter { !it.value.isNullOrBlank() })
                        }
                    }
                }
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

    if (showResolve) {
        ResolveConflictDialog(
            onConfirm = { cid, keep ->
                showResolve = false
                confirm = NodeCommand(Hc2NodeCommandName.RESOLVE_CONFLICT, Hc2CommandArgs(id = cid, keep = keep.value))
            },
            onDismiss = { showResolve = false }
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

    if (showForget) {
        ConfirmDialog(
            title = "Forget direct connection?",
            text = "The address and API key are removed from this device. The node itself is not changed.",
            confirmLabel = "Forget",
            destructive = true,
            onConfirm = {
                showForget = false
                id?.let { LocalNodeStore.remove(ctx, it) }
                // Direkte Node: ohne Zugang bleibt nichts zu zeigen
                if (directOnly) {
                    navController.popBackStack()
                    return@ConfirmDialog
                }
                config = null
                status = null
                logLines = emptyList()
                lastOk = 0L
                failing = false
                tickError = null
                otherNodeId = null
            },
            onDismiss = { showForget = false }
        )
    }

    disableModule?.let { name ->
        ConfirmDialog(
            title = "Disable $name?",
            text = "Disable $name? Services of this module stop on the node, e.g. DNS for your network.",
            confirmLabel = "Disable",
            destructive = true,
            onConfirm = { disableModule = null; setModule(name, false) },
            onDismiss = { disableModule = null }
        )
    }

    if (showDirect) NodeLocalSheet(id, onDismiss = { showDirect = false }, onSaved = {
        showDirect = false
        config = id?.let { nid -> LocalNodeStore.get(ctx, nid) }
        load()
    })

    RequestStateHost(req)
}

// Kopf: Name, Status (lokal: Live/Paused/Offline, sonst Cloud Online/Offline), Quelle und Aktualität; ohne lokalen Zugang der Einstieg dazu
@Composable
private fun NodeHeader(
    node: Hc2NodeDetail,
    status: NodeStatus?,
    host: String?,
    live: LiveInfo,
    clock: State<Long>,
    otherNodeId: String?,
    rejected: String?,
    onSetupLocal: () -> Unit
) {
    val local = host != null
    val version = (status?.node?.version ?: node.version)?.takeIf { it.isNotBlank() }?.let { "v$it" }
    val (chipText, chipColor) = if (local) live.chip() else {
        val online = node.online == true
        (if (online) "Online" else "Offline") to colorResource(if (online) R.color.ipv64_green else R.color.ipv64_gray)
    }
    ListGroup {
        if (local && live.failing) {
            GroupText(rejected ?: LOST_TEXT, error = true)
            // Auch bei nicht erreichbarer Node (z. B. neue IP), nicht nur bei abgelehntem Key
            TextButton(onClick = onSetupLocal, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Edit connection") }
        }
        if (local && otherNodeId != null) {
            GroupText("This address belongs to another node ($otherNodeId). Forget the direct connection and connect again.", error = true)
        }
        ListRow(
            title = node.displayName(),
            subtitle = listOfNotNull(
                version,
                if (local) "Direct " + (host?.toHttpUrlOrNull()?.host ?: host)
                else listOfNotNull("Cloud", node.lastSeen?.let { "seen ${it.formatRelative()}" }).joinToString(" · ")
            ).joinToString(" · "),
            trailing = { StatusChip(chipText, chipColor) }
        )
        if (local) {
            if (live.lastOk > 0) UpdatedText(live.lastOk, clock)
            status?.link?.lastError?.takeIf { it.isNotBlank() }?.let { GroupText("Last error: $it", error = true) }
        } else {
            GroupDivider()
            ListRow(title = "Set up direct access", icon = R.drawable.lan_24px, onClick = onSetupLocal)
        }
    }
}

// Vier Kacheln; lokal mit Uptime, nur Cloud mit letztem Sync. Anzahl Konflikte > 0 zusätzlich in Fehlerfarbe.
@Composable
private fun NodeStatTiles(node: Hc2NodeDetail, status: NodeStatus?, local: Boolean, cloud: Boolean) {
    val link = status?.link
    val conflicts: Number? = link?.conflicts ?: node.conflicts
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (local) StatTile(status?.node?.uptimeSeconds?.formatDuration() ?: "–", "Uptime", Modifier.weight(1f))
        else StatTile(node.lastSync?.formatRelative() ?: "–", "Last sync", Modifier.weight(1f))
        if (cloud) StatTile(node.checks.size.toString(), "Checks", Modifier.weight(1f))
        StatTile((link?.outbox ?: node.outboxSize)?.toString() ?: "–", "Outbox", Modifier.weight(1f))
        StatTile(
            conflicts?.toString() ?: "–", "Conflicts", Modifier.weight(1f),
            valueColor = if ((conflicts?.toLong() ?: 0L) > 0) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified
        )
    }
}

// Aufklappbare Fakten; lokale Werte nur mit Zugang
private fun nodeDetailItems(node: Hc2NodeDetail, status: NodeStatus?, localUrl: String?, cloud: Boolean): List<InfoItem> {
    val st = status?.node
    return listOfNotNull(
        InfoItem("Node ID", node.nodeId, copyable = true, monospace = true),
        InfoItem("Location", node.location),
        InfoItem("Time zone", node.timezone),
        node.hub?.takeIf { it.isNotBlank() }?.let { InfoItem("Hub", it) },
        InfoItem("Linked", node.linkedAt?.formatGermanTime()),
        InfoItem("Remote management", node.remoteMgmt.label()).takeIf { cloud },
        localUrl?.let { InfoItem("Started", st?.startedAt?.formatGermanTime()) },
        localUrl?.let { InfoItem("Database", st?.databaseBytes?.formatBytes()) },
        localUrl?.let { InfoItem("Direct address", it, copyable = true, monospace = true) }
    )
}

// Platzhalter für eine Node ohne Cloud-Eintrag; die Werte kommen aus dem lokalen Status
private fun directNodeDetail(id: String, name: String?) = Hc2NodeDetail(
    nodeId = id, name = name, location = null, version = null, timezone = null, modules = emptyList(),
    remoteMgmt = null, online = null, lastSeen = null, lastSync = null, outboxSize = null, conflicts = null,
    hub = null, linkedAt = null, checks = emptyList()
)
