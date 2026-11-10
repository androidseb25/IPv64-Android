package de.rpicloud.ipv64net.main.views

import de.rpicloud.ipv64net.main.views.common.groupedRows
import de.rpicloud.ipv64net.main.views.common.spacedItem
import androidx.compose.runtime.saveable.rememberSaveable
import de.rpicloud.ipv64net.main.views.monitoring.MonitoringNavKeys
import de.rpicloud.ipv64net.main.views.common.FilterChipRow
import de.rpicloud.ipv64net.main.views.common.GroupDivider
import de.rpicloud.ipv64net.main.views.common.GroupText
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.SearchField
import de.rpicloud.ipv64net.main.views.common.rememberCanGoBack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.res.colorResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.network.ConnectedNodes
import de.rpicloud.ipv64net.main.views.network.LocalNodeStore
import de.rpicloud.ipv64net.main.views.network.localNodeClient
import de.rpicloud.ipv64net.main.views.network.localNodeError
import de.sr.ipv64.node64.model.Check
import de.sr.ipv64.node64.model.CheckOwner
import de.sr.ipv64.node64.model.CheckStateValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.Hc2CheckListCache
import de.rpicloud.ipv64net.helper.Hc2NavKeys
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.htmlText
import de.rpicloud.ipv64net.helper.loadHc2Checks
import de.rpicloud.ipv64net.helper.toError
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.helper.views.RequestDialogs
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.models.Hc2StateUi
import de.rpicloud.ipv64net.models.RequestTyp
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.rpicloud.ipv64net.models.label
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.Hc2Check
import de.sr.ipv64.model.Hc2Execution
import kotlinx.coroutines.launch

// Filter der Liste (lokal, ohne Netzwerk)
private enum class HcFilter(val label: String) {
    ALL("All"), UP("Up"), DEGRADED("Degraded"), DOWN("Down"), PAUSED("Paused");

    fun matches(check: Hc2Check): Boolean {
        val state = Hc2StateUi.from(check.state?.state, check.paused)
        return when (this) {
            ALL -> true
            UP -> state == Hc2StateUi.UP
            DEGRADED -> state == Hc2StateUi.DEGRADED || state == Hc2StateUi.FLAPPING
            DOWN -> state == Hc2StateUi.DOWN
            PAUSED -> state == Hc2StateUi.PAUSED
        }
    }
}

private fun HcFilter.matchesNode(c: Check): Boolean {
    val st = if (c.paused == true) CheckStateValue.PAUSED else c.state?.state
    return when (this) {
        HcFilter.ALL -> true
        HcFilter.UP -> st == CheckStateValue.UP
        HcFilter.DEGRADED -> st == CheckStateValue.DEGRADED || st == CheckStateValue.FLAPPING
        HcFilter.DOWN -> st == CheckStateValue.DOWN
        HcFilter.PAUSED -> st == CheckStateValue.PAUSED
    }
}

@Composable
private fun NodeCheckRow(c: Check, onClick: () -> Unit) {
    val st = if (c.paused == true) CheckStateValue.PAUSED else c.state?.state
    val color = when (st) {
        CheckStateValue.UP -> R.color.ipv64_green
        CheckStateValue.DOWN -> R.color.ipv64_red
        CheckStateValue.DEGRADED, CheckStateValue.FLAPPING -> R.color.ipv64_orange
        else -> R.color.ipv64_gray
    }
    val type = c.type?.value.orEmpty().uppercase()
    ListRow(
        title = c.name.orEmpty().ifBlank { "(unnamed)" },
        subtitle = if (c.target.isNullOrBlank()) type else "$type · ${c.target}",
        trailing = { StatusChip(st?.value?.replaceFirstChar { it.uppercase() } ?: "–", colorResource(color)) },
        showChevron = false,
        onClick = onClick
    )
}

// Zweiter Filter nach Ausführung (lokal); fehlendes execution gilt als Online
private enum class ExecFilter(val label: String) {
    ALL("All"), ONLINE("Online"), NODE("Node"), BOTH("Both");

    fun matches(check: Hc2Check): Boolean = when (this) {
        ALL -> true
        ONLINE -> (check.execution ?: Hc2Execution.ONLINE) == Hc2Execution.ONLINE
        NODE -> check.execution == Hc2Execution.LOCAL
        BOTH -> check.execution == Hc2Execution.BOTH
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthcheckView(navController: NavHostController, mainPadding: PaddingValues) {
    val canGoBack = rememberCanGoBack(navController)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLoadingDialog by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var errorDialogTitle by remember { mutableStateOf("") }
    var errorDialogText by remember { mutableStateOf("") }
    var errorDialogButtonText by remember { mutableIntStateOf(android.R.string.ok) }
    var showRequestDialog by remember { mutableStateOf(false) }
    var requestType by remember { mutableStateOf(RequestTyp.UnAuthorized) }

    var allChecks by remember { mutableStateOf<List<Hc2Check>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    // Startfilter aus der Monitoring-Übersicht (Kachel), einmal lesen und entfernen
    val startFilter = remember {
        val handle = navController.previousBackStackEntry?.savedStateHandle
        val name = handle?.get<String>(MonitoringNavKeys.CHECK_FILTER)
        handle?.remove<String>(MonitoringNavKeys.CHECK_FILTER)
        when (name) {
            Hc2StateUi.UP.name -> HcFilter.UP
            Hc2StateUi.DEGRADED.name, Hc2StateUi.FLAPPING.name -> HcFilter.DEGRADED
            Hc2StateUi.DOWN.name -> HcFilter.DOWN
            Hc2StateUi.PAUSED.name -> HcFilter.PAUSED
            else -> HcFilter.ALL
        }
    }
    // Checks der verbundenen Nodes: je Node-ID Liste oder Fehlertext (ein Fehler betrifft nur diese Node)
    val nodeChecks = remember { mutableStateMapOf<String, List<Check>>() }
    val nodeErrors = remember { mutableStateMapOf<String, String>() }
    // Zustand des Healthchecks-Moduls je Node; fehlender Eintrag = unbekannt (noch nicht geladen)
    val nodeModules = remember { mutableStateMapOf<String, NodeHcModule>() }
    var showNewDialog by remember { mutableStateOf(false) }
    var firstResume by remember { mutableStateOf(true) }
    var filter by rememberSaveable { mutableStateOf(startFilter) }
    var execFilter by rememberSaveable { mutableStateOf(ExecFilter.ALL) }
    var query by remember { mutableStateOf("") }

    suspend fun loadNodes() {
        val ids = ConnectedNodes.ids
        nodeChecks.keys.retainAll(ids.toSet()); nodeErrors.keys.retainAll(ids.toSet()); nodeModules.keys.retainAll(ids.toSet())
        coroutineScope {
            ids.map { id ->
                async {
                    val (url, key) = LocalNodeStore.get(ctx, id) ?: return@async
                    try {
                        val node = localNodeClient(url, key)
                        // Erst status(): ohne aktives Modul ist die Node erreichbar, hat aber keine Checks
                        val modules = node.node.status().modules
                        val mod = modules.firstOrNull { it.name?.lowercase() in HC_MODULES }
                        val state = when {
                            modules.isEmpty() -> NodeHcModule.ON // Node meldet keine Modulliste: einfach versuchen
                            mod == null -> NodeHcModule.MISSING
                            mod.enabled == false -> NodeHcModule.OFF
                            else -> NodeHcModule.ON
                        }
                        nodeModules[id] = state
                        if (state == NodeHcModule.ON) nodeChecks[id] = node.checks.list() else nodeChecks.remove(id)
                        nodeErrors.remove(id)
                    } catch (e: IPv64Exception) {
                        // Alte Liste verwerfen: sonst verschwänden ihre Checks auch aus der Account-Liste
                        nodeChecks.remove(id)
                        nodeErrors[id] = localNodeError(e).let { "${it.first}: ${it.second}" }
                    }
                }
            }.awaitAll()
        }
    }

    // Liste einmal laden (20 s Cache), force = Pull-to-Refresh; nie pro Zeile
    fun loadChecks(force: Boolean, pullRefresh: Boolean = false) {
        // Client zuerst holen: leert bei API-Key-Wechsel die Caches
        IPv64Client.get(ctx)
        if (!force) {
            Hc2CheckListCache.get()?.let {
                allChecks = it.sortedBy { c -> c.name.orEmpty().lowercase() }
                loaded = true
                return
            }
        }
        if (pullRefresh) refreshing = true else showLoadingDialog = true
        if (pullRefresh) scope.launch { loadNodes() }
        scope.launch {
            try {
                // Gemeinsamer Loader: Mutex, Cache und Schutz vor Account-Wechsel während des Abrufs
                val result = loadHc2Checks(ctx, force)
                allChecks = result.sortedBy { c -> c.name.orEmpty().lowercase() }
                loaded = true
            } catch (e: IPv64Exception) {
                val error = e.toError()
                if (error.requestType != null) {
                    requestType = error.requestType
                    showRequestDialog = true
                } else {
                    errorDialogTitle = error.title
                    errorDialogText = htmlText(error.text)
                    errorDialogButtonText = R.string.retry
                    showDialog = true
                }
            } finally {
                showLoadingDialog = false
                refreshing = false
            }
        }
    }

    // Auf einer verbundenen Node angelegte Checks erscheinen nur in deren Gruppe, nicht in der Account-Liste
    val nodeOwned = nodeChecks.values.flatten().filter { it.isNodeOwned() }.mapNotNull { it.token }.toSet()
    val checks = remember(allChecks, nodeOwned) { allChecks.filter { it.token !in nodeOwned } }
    val counts = remember(checks) { checks.counts() }
    // Ausführungsfilter nur anzeigen, wenn es Node-Checks gibt (oder er noch aktiv ist)
    val showExecFilter = execFilter != ExecFilter.ALL || checks.any { !ExecFilter.ONLINE.matches(it) }
    val visible = remember(checks, filter, execFilter, query) {
        val q = query.trim().lowercase()
        checks.filter { c ->
            filter.matches(c) && execFilter.matches(c) && (q.isEmpty() ||
                c.name.orEmpty().lowercase().contains(q) ||
                c.target.orEmpty().lowercase().contains(q) ||
                c.type.label.lowercase().contains(q))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    // Kein Tab mehr: Zurück-Pfeil nur, wenn es einen vorherigen Screen gibt (z. B. Monitoring-Hub)
                    if (canGoBack) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(painter = painterResource(id = R.drawable.arrow_back_24px), contentDescription = "Back")
                        }
                    }
                },
                title = { Text(Tabs.getLabel(Tab.healthcheck)) },
                modifier = Modifier.statusBarsPadding()
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (ConnectedNodes.ids.none { nodeModules[it].offersChecks() }) navController.navigate(Tabs.getRoute(Tab.healthcheck_new)) else showNewDialog = true
            }) {
                Icon(painter = painterResource(id = R.drawable.heart_plus_24px), contentDescription = "New healthcheck")
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(mainPadding)
            .consumeWindowInsets(mainPadding)  // avoids double insets
    ) { inner ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { loadChecks(force = true, pullRefresh = true) },
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                spacedItem {
                    SearchField(value = query, onValueChange = { query = it }, placeholder = "Search")
                }
                spacedItem {
                    FilterChipRow(
                        options = HcFilter.entries,
                        selected = filter,
                        label = { f ->
                            val n = when (f) {
                                HcFilter.ALL -> checks.size
                                HcFilter.UP -> counts.up
                                HcFilter.DEGRADED -> counts.degraded
                                HcFilter.DOWN -> counts.down
                                HcFilter.PAUSED -> counts.paused
                            }
                            "${f.label} $n"
                        },
                        onSelect = { filter = it },
                        trailingContent = if (!showExecFilter) null else {
                            {
                                ExecFilter.entries.forEach { f ->
                                    FilterChip(
                                        selected = f == execFilter, onClick = { execFilter = f },
                                        label = { Text("${f.label} ${checks.count { f.matches(it) }}") }
                                    )
                                }
                            }
                        }
                    )
                }
                groupedRows(visible, key = { "hc_" + it.token.orEmpty() + it.name.orEmpty() }) { c ->
                    HealthcheckRow(c) { selected ->
                        selected.token?.let { token ->
                            navController.currentBackStackEntry?.savedStateHandle?.set(
                                Hc2NavKeys.SELECTED_HC2_TOKEN, token
                            )
                            navController.navigate(Tabs.getRoute(Tab.healthcheck_details))
                        }
                    }
                }
                // Node-Gruppen: Suche (Name/Ziel) und Statusfilter gelten auch hier; Ausführungsfilter nicht
                ConnectedNodes.ids.forEach { id ->
                    val name = LocalNodeStore.name(ctx, id)?.takeIf { it.isNotBlank() } ?: id
                    val q = query.trim().lowercase()
                    // Bei Fehler keine veralteten Zeilen neben der Fehlermeldung
                    val rows = (nodeChecks[id].takeIf { nodeErrors[id] == null } ?: emptyList()).filter { c ->
                        c.isNodeOwned() && filter.matchesNode(c) && (q.isEmpty() || c.name.orEmpty().lowercase().contains(q) || c.target.orEmpty().lowercase().contains(q))
                    }.sortedBy { it.name.orEmpty().lowercase() }
                    if (nodeErrors[id] == null && nodeModules[id] == NodeHcModule.OFF) {
                        spacedItem(key = "node_off_$id") {
                            ListGroup(title = name) { GroupText("Healthchecks module is off on this node") }
                        }
                    }
                    nodeErrors[id]?.let { err ->
                        spacedItem(key = "node_err_$id") {
                            ListGroup(title = name) {
                                ListRow(title = "Node not reachable", subtitle = err, subtitleMaxLines = 4)
                                GroupText("Check the connection under More → Nodes.")
                            }
                        }
                    }
                    groupedRows(rows, key = { "node_${id}_" + it.token.orEmpty() }, title = name, headerKey = "node_$id") { c ->
                        NodeCheckRow(c) { c.token?.let { navController.navigateWithArgs(Tab.node_check_details, NavKeys.SELECTED_NODE_ID to id, NavKeys.NODE_CHECK_TOKEN to it) } }
                    }
                }
                if (loaded && visible.isEmpty() && nodeChecks.values.none { l -> l.any { it.isNodeOwned() } }) {
                    spacedItem {
                        Text(
                            if (checks.isEmpty()) "No healthchecks yet." else "No healthchecks match.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                item {
                    Text(
                        "Classic healthchecks are managed at ipv64.net",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadChecks(force = false)
    }
    LaunchedEffect(ConnectedNodes.ids) { loadNodes() }
    // Zurück vom Node-Check-Editor: neu laden (erster Resume ist schon durch LaunchedEffect abgedeckt)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (firstResume) firstResume = false else scope.launch { loadNodes() }
    }

    if (showNewDialog) {
        ModalBottomSheet(onDismissRequest = { showNewDialog = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                ListGroup(title = "New healthcheck") {
                    ListRow(
                        title = "Account healthcheck", subtitle = "Runs from ipv64.net locations", icon = R.drawable.cloud_24px,
                        onClick = {
                            showNewDialog = false
                            navController.navigate(Tabs.getRoute(Tab.healthcheck_new))
                        }
                    )
                    ConnectedNodes.ids.filter { nodeModules[it].offersChecks() && nodeErrors[it] == null }.forEach { id ->
                        val nodeName = LocalNodeStore.name(ctx, id)?.takeIf { it.isNotBlank() } ?: id
                        GroupDivider(inset = 56.dp)
                        ListRow(
                            title = "On node $nodeName", subtitle = "Runs locally on $nodeName", icon = R.drawable.lan_24px,
                            onClick = {
                                showNewDialog = false
                                navController.navigateWithArgs(Tab.node_check_edit, NavKeys.SELECTED_NODE_ID to id, NavKeys.NODE_CHECK_TOKEN to null)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showLoadingDialog) {
        SpinnerDialog(spinnerText = "Loading...")
    }

    if (showDialog) {
        ErrorDialog(
            onDismissRequest = { showDialog = false },
            onConfirmation = { showDialog = false; loadChecks(force = true) },
            dialogTitle = errorDialogTitle,
            dialogText = errorDialogText,
            dialogConfirmText = errorDialogButtonText,
            showDismiss = true
        )
    }

    if (showRequestDialog) {
        RequestDialogs(
            onDismissRequest = { showRequestDialog = false },
            onConfirmation = { showRequestDialog = false },
            dialogConfirmText = errorDialogButtonText,
            request = requestType
        )
    }
}

// Von dieser Node angelegt (Owner "self"); Web-Checks ("online") und Checks anderer Nodes ("other") gehören nicht in
// die Node-Gruppe. Ohne Owner-Angabe (ältere Node64-Version) gilt der Check als Node-Check.
private fun Check.isNodeOwned(): Boolean = owner != CheckOwner.ONLINE && owner != CheckOwner.OTHER

// Modul "Healthchecks" der Node: ON = aktiv, OFF = vorhanden aber aus, MISSING = Node hat kein solches Modul
private enum class NodeHcModule { ON, OFF, MISSING }

private val HC_MODULES = setOf("checks", "healthchecks")

// Unbekannt (null) darf angeboten werden; die Node ist ja verbunden
private fun NodeHcModule?.offersChecks() = this == null || this == NodeHcModule.ON
