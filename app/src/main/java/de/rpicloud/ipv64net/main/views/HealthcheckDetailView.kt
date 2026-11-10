package de.rpicloud.ipv64net.main.views

import de.rpicloud.ipv64net.main.views.common.GroupText
import de.rpicloud.ipv64net.main.views.common.groupedRows
import de.rpicloud.ipv64net.main.views.common.spacedItem
import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle
import androidx.core.net.toUri
import de.rpicloud.ipv64net.helper.htmlText
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import de.rpicloud.ipv64net.main.views.common.ActionTile
import de.rpicloud.ipv64net.main.views.common.ConfirmDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.main.views.common.GroupDivider
import de.rpicloud.ipv64net.main.views.common.InfoRow
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.common.InfoGroup
import de.rpicloud.ipv64net.main.views.common.InfoItem
import de.rpicloud.ipv64net.helper.Hc2ChannelCache
import de.rpicloud.ipv64net.helper.Hc2CheckListCache
import de.rpicloud.ipv64net.helper.Hc2NavKeys
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.helper.realOrNull
import de.rpicloud.ipv64net.helper.formatLatency
import de.rpicloud.ipv64net.helper.formatPercent
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.helper.displayName
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.helper.toError
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.helper.views.RequestDialogs
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.main.views.network.awaitNodeCommand
import de.rpicloud.ipv64net.main.views.network.outputText
import de.rpicloud.ipv64net.models.Hc2StateUi
import de.rpicloud.ipv64net.models.RequestTyp
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.rpicloud.ipv64net.models.formatSeconds
import de.rpicloud.ipv64net.models.label
import de.rpicloud.ipv64net.models.repeatMinutesLabel
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.Hc2CheckDetail
import de.sr.ipv64.model.Hc2CheckType
import de.sr.ipv64.model.Hc2Event
import de.sr.ipv64.model.Hc2Execution
import de.sr.ipv64.model.Hc2Incident
import de.sr.ipv64.model.Hc2Measurement
import de.sr.ipv64.model.Hc2Stats
import de.sr.ipv64.model.Hc2StatsRange
import androidx.compose.material3.SnackbarDuration
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val EVENTS_PAGE_SIZE = 50
private const val INCIDENTS_LIMIT = 20
private const val MEASUREMENTS_LIMIT = 100

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HealthcheckDetailView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val snackbarHost = remember { SnackbarHostState() }

    // Token robust aus dem vorherigen Eintrag lesen
    val token = remember {
        navController.previousBackStackEntry?.savedStateHandle?.get<String>(Hc2NavKeys.SELECTED_HC2_TOKEN)
    }

    var showLoadingDialog by remember { mutableStateOf(false) }
    var nodeRunActive by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var errorDialogTitle by remember { mutableStateOf("") }
    var errorDialogText by remember { mutableStateOf("") }
    var errorDialogButtonText by remember { mutableIntStateOf(android.R.string.ok) }
    var errorRetry by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showRequestDialog by remember { mutableStateOf(false) }
    var requestType by remember { mutableStateOf(RequestTyp.UnAuthorized) }

    var detail by remember { mutableStateOf<Hc2CheckDetail?>(null) }
    var events by remember { mutableStateOf<List<Hc2Event>>(emptyList()) }
    var measurements by remember { mutableStateOf<List<Hc2Measurement>>(emptyList()) }
    var nextBefore by remember { mutableStateOf<Long?>(null) }
    var moreEventsLoading by remember { mutableStateOf(false) }
    var moreEventsJob by remember { mutableStateOf<Job?>(null) }

    var statsExpanded by remember { mutableStateOf(false) }
    var statsRange by remember { mutableStateOf(Hc2StatsRange.HOURS_24) }
    val statsCache = remember { mutableStateMapOf<Hc2StatsRange, Hc2Stats>() }
    var statsLoading by remember { mutableStateOf(false) }
    var statsError by remember { mutableStateOf<String?>(null) }

    var incidentsExpanded by remember { mutableStateOf(false) }
    var incidents by remember { mutableStateOf<List<Hc2Incident>?>(null) }
    var incidentsLoading by remember { mutableStateOf(false) }
    var incidentsError by remember { mutableStateOf<String?>(null) }

    fun showError(e: IPv64Exception, retry: (() -> Unit)?) {
        val error = e.toError()
        if (error.requestType != null) {
            requestType = error.requestType
            showRequestDialog = true
        } else {
            errorDialogTitle = error.title
            errorDialogText = htmlText(error.text)
            errorDialogButtonText = if (retry != null) R.string.retry else android.R.string.ok
            errorRetry = retry
            showDialog = true
        }
    }

    // Erst get, dann events, dann Messungen (sequentiell, Rate-Limit)
    fun load() {
        if (token == null) return
        // Laufendes "Load more" abbrechen, sonst überlappen sich die Seiten
        moreEventsJob?.cancel()
        moreEventsJob = null
        moreEventsLoading = false
        showLoadingDialog = true
        scope.launch {
            try {
                val ipv64 = IPv64Client.get(ctx)
                detail = ipv64Retrying { ipv64.hc2.checks.get(token) }
                val page = ipv64Retrying { ipv64.hc2.checks.events(token, limit = EVENTS_PAGE_SIZE) }
                events = page.data
                nextBefore = if (page.meta?.hasMore == true) page.meta?.nextBefore else null
                // Fehler hier bricht den Screen nicht ab: ohne Messungen zeigt die Leiste die Events
                measurements = try {
                    ipv64Retrying { ipv64.hc2.checks.measurements(token, limit = MEASUREMENTS_LIMIT) }
                } catch (_: IPv64Exception) {
                    emptyList()
                }
            } catch (e: IPv64Exception) {
                showError(e) { load() }
            } finally {
                showLoadingDialog = false
            }
        }
    }

    fun loadMoreEvents() {
        val before = nextBefore ?: return
        if (token == null || moreEventsLoading) return
        moreEventsLoading = true
        moreEventsJob = scope.launch {
            try {
                val ipv64 = IPv64Client.get(ctx)
                val page = ipv64Retrying { ipv64.hc2.checks.events(token, limit = EVENTS_PAGE_SIZE, before = before) }
                events = (events + page.data).distinctBy { it.id ?: Any() }
                nextBefore = if (page.meta?.hasMore == true) page.meta?.nextBefore else null
            } catch (e: IPv64Exception) {
                showError(e, null)
            } finally {
                moreEventsLoading = false
            }
        }
    }

    fun pauseResume() {
        val current = detail ?: return
        if (token == null) return
        showLoadingDialog = true
        scope.launch {
            try {
                val ipv64 = IPv64Client.get(ctx)
                val updated = if (current.paused == true) ipv64Retrying { ipv64.hc2.checks.resume(token) }
                else ipv64Retrying { ipv64.hc2.checks.pause(token) }
                detail = current.copy(paused = updated.paused, state = updated.state ?: current.state)
                Hc2CheckListCache.invalidate()
            } catch (e: IPv64Exception) {
                showError(e, null)
            } finally {
                showLoadingDialog = false
            }
        }
    }

    fun runNow() {
        if (token == null) return
        showLoadingDialog = true
        scope.launch {
            try {
                val ipv64 = IPv64Client.get(ctx)
                val result = ipv64Retrying { ipv64.hc2.checks.run(token) }
                showLoadingDialog = false
                val nodeId = result.nodeId
                val commandId = result.commandId
                if (result.queued == false || nodeId == null || commandId == null) {
                    snackbarHost.showSnackbar(if (result.queued == false) "Run not queued" else "Run queued")
                } else {
                    // Node-Check: auf das Ergebnis der Node warten (Snackbar bleibt bis dahin stehen)
                    val waiting = launch {
                        snackbarHost.showSnackbar(
                            "Waiting for node ${detail?.nodeName ?: ""}…",
                            duration = SnackbarDuration.Indefinite
                        )
                    }
                    nodeRunActive = true
                    val finished = try {
                        awaitNodeCommand(ctx, nodeId, commandId)
                    } finally {
                        waiting.cancel()
                        nodeRunActive = false
                    }
                    if (finished == null) {
                        snackbarHost.showSnackbar("Node has not reported back yet")
                    } else {
                        // Erst neu laden, dann melden: showSnackbar wartet bis zum Ausblenden
                        Hc2CheckListCache.invalidate()
                        load()
                        val output = finished.output.outputText()?.let { "\n$it" }.orEmpty()
                        snackbarHost.showSnackbar(
                            (if (finished.ok == false) "Run failed" else "Run finished: OK") + output
                        )
                    }
                }
            } catch (e: IPv64Exception) {
                // Kein finally: würde den Lade-Dialog von load() sofort wieder schließen
                showLoadingDialog = false
                showError(e, null)
            }
        }
    }

    fun deleteCheck() {
        if (token == null) return
        showLoadingDialog = true
        scope.launch {
            try {
                val ipv64 = IPv64Client.get(ctx)
                ipv64Retrying { ipv64.hc2.checks.delete(token) }
                Hc2CheckListCache.invalidate()
                navController.popBackStack()
            } catch (e: IPv64Exception) {
                showError(e, null)
            } finally {
                showLoadingDialog = false
            }
        }
    }

    // Statistik lazy beim Aufklappen, je Zeitraum nur einmal
    LaunchedEffect(statsExpanded, statsRange, detail?.token) {
        if (!statsExpanded || token == null || detail == null) return@LaunchedEffect
        if (statsCache.containsKey(statsRange)) {
            statsError = null
            return@LaunchedEffect
        }
        statsLoading = true
        statsError = null
        try {
            val ipv64 = IPv64Client.get(ctx)
            statsCache[statsRange] = ipv64Retrying { ipv64.hc2.checks.stats(token, statsRange) }
        } catch (e: IPv64Exception) {
            statsError = e.toError().text.ifBlank { "Statistics could not be loaded" }
        } finally {
            statsLoading = false
        }
    }

    // Incidents lazy beim ersten Aufklappen
    LaunchedEffect(incidentsExpanded, detail?.token) {
        if (!incidentsExpanded || token == null || detail == null || incidents != null) return@LaunchedEffect
        incidentsLoading = true
        incidentsError = null
        try {
            val ipv64 = IPv64Client.get(ctx)
            incidents = ipv64Retrying { ipv64.hc2.checks.incidents(token, limit = INCIDENTS_LIMIT) }
        } catch (e: IPv64Exception) {
            incidentsError = e.toError().text.ifBlank { "Incidents could not be loaded" }
        } finally {
            incidentsLoading = false
        }
    }

    // Beim (erneuten) Betreten laden, z. B. nach dem Bearbeiten
    LaunchedEffect(Unit) {
        load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(painter = painterResource(id = R.drawable.arrow_back_24px), contentDescription = "Back")
                    }
                },
                title = { Text(detail?.name ?: "Healthcheck", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = { load() }) {
                        Icon(painter = painterResource(id = R.drawable.refresh_24px), contentDescription = "Refresh")
                    }
                    IconButton(
                        enabled = detail != null,
                        onClick = {
                            navController.currentBackStackEntry?.savedStateHandle?.set(
                                Hc2NavKeys.EDIT_HC2_TOKEN, token
                            )
                            navController.navigate(Tabs.getRoute(Tab.healthcheck_edit))
                        }
                    ) {
                        Icon(painter = painterResource(id = R.drawable.edit_24px), contentDescription = "Edit")
                    }
                    IconButton(enabled = detail != null, onClick = { showDeleteDialog = true }) {
                        Icon(painter = painterResource(id = R.drawable.delete_24px), contentDescription = "Delete")
                    }
                },
                modifier = Modifier.statusBarsPadding()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        modifier = Modifier
            .fillMaxSize()
            .padding(mainPadding)
            .consumeWindowInsets(mainPadding)  // avoids double insets
    ) { inner ->
        val d = detail
        LazyColumn(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (d != null) {
                spacedItem { HeaderCard(d) }
                spacedItem { StatusFacts(d) }
                spacedItem {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionTile(
                            if (d.paused == true) R.drawable.play_arrow_24px else R.drawable.pause_24px,
                            if (d.paused == true) "Resume" else "Pause",
                            { pauseResume() }, Modifier.weight(1f)
                        )
                        ActionTile(R.drawable.refresh_24px, "Run now", { runNow() }, Modifier.weight(1f), enabled = !nodeRunActive)
                        if (d.type in listOf(Hc2CheckType.HTTP, Hc2CheckType.KEYWORD, Hc2CheckType.JSON) &&
                            d.target?.startsWith("http", ignoreCase = true) == true
                        ) {
                            ActionTile(R.drawable.open_in_new_24px, "Open in browser", {
                                try {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, d.target.orEmpty().toUri())
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: android.content.ActivityNotFoundException) {
                                }
                            }, Modifier.weight(1f))
                        }
                    }
                }
                if (measurements.isNotEmpty()) {
                    spacedItem {
                        ListGroup("Recent checks") {
                            Box(Modifier.padding(16.dp)) { HealthcheckMeasurementsBar(measurements) }
                        }
                    }
                } else if (events.isNotEmpty()) {
                    spacedItem {
                        ListGroup("Recent events") {
                            Box(Modifier.padding(16.dp)) { HealthcheckEventsBar(events) }
                        }
                    }
                }
                if (d.sources.isNotEmpty()) {
                    spacedItem {
                        ListGroup("Measuring points") {
                            d.sources.forEachIndexed { i, s ->
                                if (i > 0) GroupDivider()
                                val st = Hc2StateUi.from(s.state)
                                val parts = listOfNotNull(
                                    s.lastLatencyMs?.formatLatency(),
                                    s.lastCheckAt?.let { "checked ${it.formatRelative()}" },
                                    s.consecutiveFail?.takeIf { it > 0 }?.let { "$it failed in a row" },
                                    s.consecutiveOk?.takeIf { it > 0 }?.let { "$it ok in a row" }
                                )
                                val msg = s.lastMessage?.takeIf { it.isNotBlank() } ?: s.lastReason?.takeIf { it.isNotBlank() }
                                ListRow(
                                    title = s.label ?: s.source.orEmpty(),
                                    subtitle = listOfNotNull(parts.takeIf { it.isNotEmpty() }?.joinToString(" · "), msg)
                                        .joinToString("\n").ifEmpty { null },
                                    subtitleMaxLines = 4,
                                    trailing = { StatusChip(st.label, colorResource(st.color)) }
                                )
                            }
                        }
                    }
                }
                if (d.type == Hc2CheckType.PUSH && d.push != null) {
                    spacedItem { PushCard(ctx, d, onCopied = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }) }
                }
                spacedItem {
                    // Check mit Node: Ausführung führt zur Node-Detailansicht
                    ConfigCard(d, d.nodeId?.takeIf { it.isNotBlank() }?.let { nid ->
                        { navController.navigateWithArgs(Tab.node_details, NavKeys.SELECTED_NODE_ID to nid) }
                    })
                }

                spacedItem {
                    ExpandableTitle("Statistics", statsExpanded) { statsExpanded = !statsExpanded }
                }
                if (statsExpanded) {
                    spacedItem {
                        HealthcheckStatsView(
                            range = statsRange,
                            onRangeChange = { statsRange = it },
                            stats = statsCache[statsRange],
                            loading = statsLoading && !statsCache.containsKey(statsRange),
                            error = statsError
                        )
                    }
                }
                spacedItem {
                    ExpandableTitle("Incidents", incidentsExpanded) { incidentsExpanded = !incidentsExpanded }
                }
                if (incidentsExpanded) {
                    spacedItem {
                        ListGroup {
                            when {
                                incidentsLoading -> ListRow("Loading...")
                                incidentsError != null -> GroupText(incidentsError.orEmpty(), error = true)
                                incidents.isNullOrEmpty() -> ListRow("No incidents")
                                else -> incidents.orEmpty().forEachIndexed { i, inc ->
                                    if (i > 0) GroupDivider()
                                    HealthcheckIncidentItemView(inc)
                                }
                            }
                        }
                    }
                }

                if (events.isEmpty()) spacedItem { ListGroup("Events") { ListRow("No events") } }
                else groupedRows(events, key = { ev -> "ev-" + (ev.id ?: ev) }, title = "Events") { ev ->
                    HealthcheckEventItemView(ev)
                }
                if (nextBefore != null) {
                    spacedItem {
                        FilledTonalButton(
                            onClick = { loadMoreEvents() },
                            enabled = !moreEventsLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (moreEventsLoading) "Loading..." else "Load more")
                        }
                    }
                }
            }
        }
    }

    if (showLoadingDialog) {
        SpinnerDialog(spinnerText = "Loading...")
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "Delete healthcheck",
            text = "Delete \"${detail?.name.orEmpty()}\" and its history permanently?",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { showDeleteDialog = false; deleteCheck() },
            onDismiss = { showDeleteDialog = false }
        )
    }

    if (showDialog) {
        ErrorDialog(
            onDismissRequest = { showDialog = false },
            onConfirmation = { showDialog = false; errorRetry?.invoke() },
            dialogTitle = errorDialogTitle,
            dialogText = errorDialogText,
            dialogConfirmText = errorDialogButtonText,
            showDismiss = errorRetry != null
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

// Aufklappbarer Abschnitt: eine Zeile mit "Show"/"Hide"
@Composable
private fun ExpandableTitle(text: String, expanded: Boolean, onToggle: () -> Unit) {
    ListGroup {
        ListRow(title = text, badge = if (expanded) "Hide" else "Show", showChevron = false, onClick = onToggle)
    }
}

// Kopf: Zustand und Ziel
@Composable
private fun HeaderCard(d: Hc2CheckDetail) {
    val ui = Hc2StateUi.from(d.state?.state, d.paused)
    ListGroup {
        ListRow(
            title = "${d.type.label}${d.target?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}",
            trailing = { StatusChip(ui.label, colorResource(ui.color)) }
        )
    }
}

// Zustand: seit wann, letzte Messung, Verfügbarkeit
@Composable
private fun StatusFacts(d: Hc2CheckDetail) {
    val st = d.state
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
            InfoItem("Last push", st?.lastPushAt?.let { "${it.formatGermanTime()} (${it.formatRelative()})" }),
            InfoItem("Availability 24 h", d.availabilityPercent?.last24h?.formatPercent()),
            InfoItem("Availability 30 d", d.availabilityPercent?.last30d?.formatPercent()),
            InfoItem("Availability 1 y", d.availabilityPercent?.last1y?.formatPercent())
        ),
        hideEmpty = true
    )
}

// Push-URL kopieren oder teilen (enthält das Check-Token, daher nie loggen)
@Composable
private fun PushCard(ctx: Context, d: Hc2CheckDetail, onCopied: () -> Unit) {
    val push = d.push ?: return
    val entries = listOfNotNull(
        push.url?.takeIf { it.isNotBlank() }?.let { "Push URL" to it },
        push.hubUrl?.takeIf { it.isNotBlank() }?.let { "Hub URL" to it },
        push.nodePath?.takeIf { it.isNotBlank() }?.let { "Node path" to it }
    )
    if (entries.isEmpty()) return
    ListGroup("Push endpoints") {
        entries.forEachIndexed { i, (label, value) ->
            if (i > 0) GroupDivider()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(label, value)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(label, value)
                        if (Build.VERSION.SDK_INT >= 33) {
                            clip.description.extras = PersistableBundle().apply {
                                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                            }
                        }
                        clipboard.setPrimaryClip(clip)
                        onCopied()
                    }) {
                        Icon(painterResource(R.drawable.content_copy_24px), null, Modifier.size(18.dp))
                        Text(" Copy")
                    }
                    OutlinedButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, value)
                        }
                        ctx.startActivity(Intent.createChooser(send, label).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) {
                        Icon(painterResource(R.drawable.send_24px), null, Modifier.size(18.dp))
                        Text(" Share")
                    }
                }
            }
        }
    }
}

// Einstellungen des Checks; Kanalnamen nur aus dem Cache (keine zusätzliche Anfrage)
@Composable
private fun ConfigCard(d: Hc2CheckDetail, onNode: (() -> Unit)?) {
    val channelText = if (d.channels.isEmpty()) {
        "Default channels"
    } else {
        val known = Hc2ChannelCache.peek()?.associateBy { it.id }
        val names = d.channels.map { id -> known?.get(id)?.displayName }
        if (names.all { it != null }) names.joinToString(", ") else "${d.channels.size} selected"
    }
    val execution = when (d.execution) {
        Hc2Execution.LOCAL -> "Node ${d.nodeName.orEmpty()}".trim()
        Hc2Execution.BOTH -> "Online (${d.locations.joinToString(", ")}) + node ${d.nodeName.orEmpty()}".trim()
        Hc2Execution.ONLINE, null -> "Online" + d.locations.takeIf { it.isNotEmpty() }?.joinToString(", ", " (", ")").orEmpty()
    }
    fun yn(v: Boolean?) = when (v) { true -> "Yes"; false -> "No"; null -> null }
    InfoGroup(
        "Configuration",
        listOf(
            InfoItem("Type", d.type.label),
            InfoItem("Target", d.target),
            InfoItem("Interval", d.intervalSeconds?.let { formatSeconds(it) }),
            InfoItem("Retry interval", d.retryIntervalSeconds?.let { formatSeconds(it) }),
            InfoItem("Retries", d.retries?.toString()),
            InfoItem("Timeout", d.timeoutSeconds?.let { formatSeconds(it.roundToInt()) }),
            InfoItem("Connect timeout", d.connectTimeoutSeconds?.let { formatSeconds(it.roundToInt()) }),
            InfoItem("Stabilize", d.stabilize?.toString()),
            InfoItem("IP mode", d.ipMode?.value),
            InfoItem("Invert result", yn(d.invert)),
            InfoItem("Execution", execution, onClick = onNode),
            InfoItem("Policy", d.policy?.value?.takeIf { d.locations.size + d.nodes.size > 1 }),
            InfoItem("Notify down", yn(d.notifyDown)),
            InfoItem("Notify up", yn(d.notifyUp)),
            InfoItem("Notify degraded", yn(d.notifyDegraded)),
            InfoItem("Critical", yn(d.critical)),
            InfoItem("Reminder", repeatMinutesLabel(d.repeatMinutes)),
            InfoItem("Channels", channelText),
            InfoItem("Tags", d.tags.takeIf { it.isNotEmpty() }?.joinToString(", ")),
            InfoItem("Group", d.group),
            InfoItem("Description", d.description),
            InfoItem("Created", d.createdAt?.formatGermanTime()),
            InfoItem("Updated", d.updatedAt?.formatGermanTime())
        ),
        hideEmpty = true
    )
}
