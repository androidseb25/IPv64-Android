package de.rpicloud.ipv64net.main.views

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.Hc2ChannelCache
import de.rpicloud.ipv64net.helper.Hc2CheckListCache
import de.rpicloud.ipv64net.helper.Hc2NavKeys
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.helper.formatLatency
import de.rpicloud.ipv64net.helper.formatPercent
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.helper.displayName
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.toError
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.helper.views.RequestDialogs
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
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
import de.sr.ipv64.model.Hc2Stats
import de.sr.ipv64.model.Hc2StatsRange
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val EVENTS_PAGE_SIZE = 50
private const val INCIDENTS_LIMIT = 20

@OptIn(ExperimentalMaterial3Api::class)
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

    // Erst get, dann events (sequentiell, Rate-Limit)
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
                snackbarHost.showSnackbar(
                    if (result.queued == false) "Run not queued" else "Run queued"
                )
            } catch (e: IPv64Exception) {
                showError(e, null)
            } finally {
                showLoadingDialog = false
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (d != null) {
                item { HeaderCard(d) }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { pauseResume() }, modifier = Modifier.weight(1f)) {
                            Icon(
                                painter = painterResource(
                                    if (d.paused == true) R.drawable.play_arrow_24px else R.drawable.pause_circle_24px
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(if (d.paused == true) " Resume" else " Pause")
                        }
                        OutlinedButton(onClick = { runNow() }, modifier = Modifier.weight(1f)) {
                            Icon(
                                painter = painterResource(R.drawable.refresh_24px),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(" Run now")
                        }
                    }
                }
                if (d.type in listOf(Hc2CheckType.HTTP, Hc2CheckType.KEYWORD, Hc2CheckType.JSON) &&
                    d.target?.startsWith("http", ignoreCase = true) == true
                ) {
                    item {
                        OutlinedButton(
                            onClick = {
                                try {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, d.target.orEmpty().toUri())
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: android.content.ActivityNotFoundException) {
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(painterResource(R.drawable.open_in_new_24px), null, Modifier.size(18.dp))
                            Text(" Open in browser")
                        }
                    }
                }
                if (events.isNotEmpty()) {
                    item { HealthcheckEventsBar(events) }
                }
                if (d.sources.isNotEmpty()) {
                    item { SectionTitle("Measuring points") }
                    items(d.sources, key = { "src-${it.source.orEmpty()}-${it.label.orEmpty()}" }) { s ->
                        val st = Hc2StateUi.from(s.state)
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        s.label ?: s.source.orEmpty(),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Hc2StateBadge(st)
                                }
                                val parts = listOfNotNull(
                                    s.lastLatencyMs?.formatLatency(),
                                    s.lastCheckAt?.let { "checked ${it.formatRelative()}" },
                                    s.consecutiveFail?.takeIf { it > 0 }?.let { "$it failed in a row" },
                                    s.consecutiveOk?.takeIf { it > 0 }?.let { "$it ok in a row" }
                                )
                                if (parts.isNotEmpty()) {
                                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                val msg = s.lastMessage?.takeIf { it.isNotBlank() } ?: s.lastReason?.takeIf { it.isNotBlank() }
                                if (msg != null) Text(msg, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (d.type == Hc2CheckType.PUSH && d.push != null) {
                    item { PushCard(ctx, d, onCopied = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }) }
                }
                item { SectionTitle("Configuration") }
                item { ConfigCard(d) }

                item {
                    ExpandableTitle("Statistics", statsExpanded) { statsExpanded = !statsExpanded }
                }
                if (statsExpanded) {
                    item {
                        HealthcheckStatsView(
                            range = statsRange,
                            onRangeChange = { statsRange = it },
                            stats = statsCache[statsRange],
                            loading = statsLoading && !statsCache.containsKey(statsRange),
                            error = statsError
                        )
                    }
                }
                item {
                    ExpandableTitle("Incidents", incidentsExpanded) { incidentsExpanded = !incidentsExpanded }
                }
                if (incidentsExpanded) {
                    when {
                        incidentsLoading -> item { Text("Loading...", color = Color.Gray) }
                        incidentsError != null -> item { Text(incidentsError.orEmpty(), color = MaterialTheme.colorScheme.error) }
                        incidents.isNullOrEmpty() -> item { Text("No incidents", color = Color.Gray) }
                        else -> items(incidents.orEmpty(), key = { "inc-${it.id ?: it.hashCode()}" }) { HealthcheckIncidentItemView(it) }
                    }
                }

                item { SectionTitle("Events") }
                if (events.isEmpty()) {
                    item { Text("No events", color = Color.Gray) }
                } else {
                    items(events, key = { "ev-${it.id ?: it.hashCode()}" }) { HealthcheckEventItemView(it) }
                }
                if (nextBefore != null) {
                    item {
                        Button(
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
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete healthcheck") },
            text = { Text("Delete \"${detail?.name.orEmpty()}\" and its history permanently?") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; deleteCheck() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringCancel()) }
            }
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

@Composable
private fun stringCancel(): String = androidx.compose.ui.res.stringResource(android.R.string.cancel)

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ExpandableTitle(text: String, expanded: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (expanded) "$text (hide)" else "$text (show)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String?, valueColor: Color = Color.Gray) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, fontWeight = FontWeight.Bold)
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
            color = valueColor
        )
    }
}

// Kopf: Zustand, seit wann, letzte Messung, Verfügbarkeit
@Composable
private fun HeaderCard(d: Hc2CheckDetail) {
    val st = d.state
    val ui = Hc2StateUi.from(st?.state, d.paused)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${d.type.label}${d.target?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Hc2StateBadge(ui)
            }
            InfoRow("Since", st?.since?.formatGermanTime())
            InfoRow("Last check", st?.lastCheckAt?.let { "${it.formatGermanTime()} (${it.formatRelative()})" })
            InfoRow("Last status", st?.lastStatus)
            InfoRow("Latency", st?.lastLatencyMs?.formatLatency())
            InfoRow("Reason", st?.lastReason)
            InfoRow("Message", st?.lastMessage)
            InfoRow("Down since", st?.downSince?.formatGermanTime(), Color(0xFFD32F2F))
            InfoRow("Last push", st?.lastPushAt?.let { "${it.formatGermanTime()} (${it.formatRelative()})" })
            d.availabilityPercent?.let { a ->
                InfoRow("Availability 24 h", a.last24h.formatPercent())
                InfoRow("Availability 30 d", a.last30d.formatPercent())
                InfoRow("Availability 1 y", a.last1y.formatPercent())
            }
        }
    }
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Push endpoints", style = MaterialTheme.typography.titleMedium)
            entries.forEach { (label, value) ->
                Text(label, fontWeight = FontWeight.Bold)
                Text(value, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
private fun ConfigCard(d: Hc2CheckDetail) {
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoRow("Type", d.type.label)
            InfoRow("Target", d.target)
            InfoRow("Interval", d.intervalSeconds?.let { formatSeconds(it) })
            InfoRow("Retry interval", d.retryIntervalSeconds?.let { formatSeconds(it) })
            InfoRow("Retries", d.retries?.toString())
            InfoRow("Timeout", d.timeoutSeconds?.let { formatSeconds(it.roundToInt()) })
            InfoRow("Connect timeout", d.connectTimeoutSeconds?.let { formatSeconds(it.roundToInt()) })
            InfoRow("Stabilize", d.stabilize?.toString())
            InfoRow("IP mode", d.ipMode?.value)
            InfoRow("Invert result", yn(d.invert))
            InfoRow("Execution", execution)
            InfoRow("Policy", d.policy?.value?.takeIf { d.locations.size + d.nodes.size > 1 })
            InfoRow("Notify down", yn(d.notifyDown))
            InfoRow("Notify up", yn(d.notifyUp))
            InfoRow("Notify degraded", yn(d.notifyDegraded))
            InfoRow("Critical", yn(d.critical))
            InfoRow("Reminder", repeatMinutesLabel(d.repeatMinutes))
            InfoRow("Channels", channelText)
            InfoRow("Tags", d.tags.takeIf { it.isNotEmpty() }?.joinToString(", "))
            InfoRow("Group", d.group)
            InfoRow("Description", d.description)
            InfoRow("Created", d.createdAt?.formatGermanTime())
            InfoRow("Updated", d.updatedAt?.formatGermanTime())
        }
    }
}
