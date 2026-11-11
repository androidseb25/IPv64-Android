package de.rpicloud.ipv64net.main.views.monitoring

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.helper.realOrNull
import de.rpicloud.ipv64net.helper.rememberNavArg
import de.rpicloud.ipv64net.main.views.common.ActionTile
import de.rpicloud.ipv64net.main.views.common.ConfirmDialog
import de.rpicloud.ipv64net.main.views.common.FilterChipRow
import de.rpicloud.ipv64net.main.views.common.GroupDivider
import de.rpicloud.ipv64net.main.views.common.GroupText
import de.rpicloud.ipv64net.main.views.common.InfoGroup
import de.rpicloud.ipv64net.main.views.common.InfoItem
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.common.groupedRows
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.main.views.common.spacedItem
import de.rpicloud.ipv64net.main.views.network.LocalNodeStore
import de.rpicloud.ipv64net.main.views.network.localNodeClient
import de.rpicloud.ipv64net.main.views.network.localNodeError
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.ui.theme.chartPalette
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.Node64ApiException
import de.sr.ipv64.node64.model.Monitor64AccessState
import de.sr.ipv64.node64.model.Monitor64HostChart
import de.sr.ipv64.node64.model.Monitor64HostDetail
import de.sr.ipv64.node64.model.Monitor64HostInput
import de.sr.ipv64.node64.model.Monitor64HostService
import de.sr.ipv64.node64.model.Monitor64HostView
import de.sr.ipv64.node64.model.Monitor64HostsServiceKeys
import de.sr.ipv64.node64.model.Monitor64Problem
import de.sr.ipv64.node64.model.Monitor64ProblemAction
import de.sr.ipv64.node64.model.Monitor64ProblemActionAction
import de.sr.ipv64.node64.model.Monitor64ProblemStatus
import de.sr.ipv64.node64.model.Monitor64Range
import de.sr.ipv64.node64.model.Monitor64ServiceState
import de.rpicloud.ipv64net.main.views.nearestIndex
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val HINT = "Check the connection under More → Nodes."

/** Anzeigezustand eines Hosts (Text, Farbe); pausiert hat Vorrang. Die Liste filtert über den Text. */
internal fun monitor64HostState(view: Monitor64HostView?, paused: Boolean?): Pair<String, Int> = when {
    paused == true -> "Paused" to R.color.ipv64_gray
    view == Monitor64HostView.OK -> "OK" to R.color.ipv64_green
    view == Monitor64HostView.WARN -> "Warning" to R.color.ipv64_orange
    view == Monitor64HostView.CRIT -> "Critical" to R.color.ipv64_red
    view == Monitor64HostView.UNREACHABLE -> "Unreachable" to R.color.ipv64_red
    view == Monitor64HostView.MAINTENANCE -> "Maintenance" to R.color.ipv64_gray
    else -> "Unknown" to R.color.ipv64_gray
}

private fun monitor64ServiceState(s: Monitor64ServiceState?): Pair<String, Int> = when (s) {
    Monitor64ServiceState.OK -> "OK" to R.color.ipv64_green
    Monitor64ServiceState.WARN -> "Warning" to R.color.ipv64_orange
    Monitor64ServiceState.CRIT -> "Critical" to R.color.ipv64_red
    else -> "Unknown" to R.color.ipv64_gray
}

private fun Double.fmt(): String = if (this == Math.rint(this) && kotlin.math.abs(this) < 1e12) toLong().toString() else String.format(Locale.ROOT, "%.1f", this)

private val RANGES = listOf(Monitor64Range.HOURS_6, Monitor64Range.HOURS_24, Monitor64Range.DAYS_7, Monitor64Range.DAYS_30)

private fun Monitor64Range.chipLabel() = when (this) {
    Monitor64Range.HOURS_6 -> "6 h"
    Monitor64Range.HOURS_24 -> "24 h"
    Monitor64Range.DAYS_7 -> "7 d"
    Monitor64Range.DAYS_30 -> "30 d"
    Monitor64Range.YEAR_1 -> "1 y"
}

// Detailansicht eines Monitor64-Hosts einer direkt verbundenen Node (lokale Node-API)
@Composable
fun Monitor64HostDetailView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    val nodeId = navController.rememberNavArg<String>(NavKeys.SELECTED_NODE_ID)
    val uid = navController.rememberNavArg<String>(NavKeys.MONITOR64_HOST_UID)
    val client = remember(nodeId) { nodeId?.let { id -> LocalNodeStore.get(ctx, id)?.let { (u, k) -> localNodeClient(u, k) } } }

    var detail by remember { mutableStateOf<Monitor64HostDetail?>(null) }
    var nodeError by remember { mutableStateOf<String?>(null) }
    var problems by remember { mutableStateOf<List<Monitor64Problem>>(emptyList()) }
    var problemsError by remember { mutableStateOf<String?>(null) }
    var range by remember { mutableStateOf(Monitor64Range.HOURS_24) }
    // Chart-ID -> Linien (Beschriftung, avg-Werte); fehlender Eintrag = Chart ausgeblendet
    val chartLines = remember { mutableStateMapOf<String, List<Pair<String, List<Pair<Long, Double>>>>>() }
    // Zählt jedes Neuladen hoch, damit die Charts erneut geholt werden
    var chartGen by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Ein fehlschlagender Abschnitt zeigt seinen Fehler inline und bricht die anderen nicht ab
    suspend fun <T> section(onError: (String?) -> Unit, block: suspend () -> T): T? = try {
        block().also { onError(null) }
    } catch (e: IPv64Exception) {
        onError(localNodeError(e).second); null
    }

    fun load() {
        val id = uid
        if (client == null || id == null) {
            nodeError = "No local access stored for this node."
            return
        }
        req.launch(scope, onError = { e -> detail = null; nodeError = monitor64Error(e); true }) {
            detail = client.monitor64.hosts.get(id)
            chartGen++
            nodeError = null
            problems = section({ problemsError = it }) {
                client.monitor64.problems(status = Monitor64ProblemStatus.OPEN, host = id).problems
            } ?: problems
        }
    }

    // Aktion mit Fehlerdialog (lokale Fehlertexte) und anschließendem Neuladen
    fun act(spinner: String, block: suspend () -> Unit) {
        req.launch(scope, spinner = spinner, onError = { e ->
            localNodeError(e).let { req.message(it.first, it.second, R.drawable.warning_24px) }; true
        }) {
            block()
            load()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { load() }

    // Ein Aufruf für alle Charts pro Zeitraum/Neuladen (Server erlaubt max. 16 Serien); Fehler blendet nur die Charts aus
    LaunchedEffect(range, chartGen) {
        val d = detail ?: return@LaunchedEffect
        val id = d.host?.uid ?: return@LaunchedEffect
        if (client == null) return@LaunchedEffect
        val keys = d.charts.flatMap { c -> c.series.map { "${it.svc}|${it.metric}" } }.distinct().take(16)
        val res = if (keys.isEmpty()) null else try { client.monitor64.series(host = id, s = keys, range = range) } catch (e: IPv64Exception) { null }
        chartLines.clear()
        d.charts.forEach { chart ->
            val lines = res?.series?.mapNotNull { e ->
                val s = chart.series.firstOrNull { "${it.svc}|${it.metric}" == e.s } ?: return@mapNotNull null
                // (Zeit in ms, avg)
                val v = e.points.mapNotNull { p -> p.avg?.let { a -> p.t?.let { it * 1000 to a } } }.sortedBy { it.first }
                if (v.size >= 2) (s.label ?: e.s.orEmpty()) to v else null
            }.orEmpty()
            if (lines.isNotEmpty()) chartLines[chart.id.orEmpty()] = lines
        }
    }

    // Problem-Aktion (Bestätigen/Lösen); Kommentar ist dafür nicht nötig
    fun problemAction(p: Monitor64Problem, action: Monitor64ProblemActionAction) {
        val id = uid ?: return
        act("Saving...") { client?.monitor64?.problemAction(Monitor64ProblemAction(host = id, action = action, key = p.key.orEmpty())) }
    }

    fun adopt(keys: List<String>, state: Int) {
        val id = uid ?: return
        act("Saving...") { client?.monitor64?.hosts?.adoptServices(id, Monitor64HostsServiceKeys(keys, state = state)) }
    }

    ScreenScaffold(
        title = detail?.host?.name ?: "Monitor64 host", navController = navController, mainPadding = mainPadding,
        actions = {
            IconButton(onClick = { load() }) {
                Icon(painterResource(R.drawable.refresh_24px), contentDescription = "Refresh")
            }
            IconButton(enabled = detail != null, onClick = {
                navController.navigateWithArgs(Tab.monitor64_host_edit, NavKeys.SELECTED_NODE_ID to nodeId, NavKeys.MONITOR64_HOST_UID to uid)
            }) { Icon(painterResource(R.drawable.edit_24px), contentDescription = "Edit") }
            IconButton(enabled = detail != null, onClick = { confirmDelete = true }) {
                Icon(painterResource(R.drawable.delete_24px), contentDescription = "Delete")
            }
        }
    ) {
        val d = detail
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (nodeError != null) {
                spacedItem {
                    ListGroup {
                        GroupText(nodeError.orEmpty(), error = true)
                        GroupText(HINT)
                    }
                }
            }
            if (d != null) {
                val h = d.host
                val hostId = h?.uid.orEmpty()
                val (stateText, stateColor) = monitor64HostState(d.view, h?.paused)
                spacedItem {
                    ListGroup {
                        ListRow(
                            title = h?.name.orEmpty().ifBlank { "(unnamed)" },
                            subtitle = h?.address,
                            trailing = { StatusChip(stateText, colorResource(stateColor)) }
                        )
                    }
                }
                spacedItem {
                    InfoGroup(
                        "Host",
                        listOf(
                            InfoItem("Kind", h?.kind?.value?.takeIf { it.isNotBlank() }),
                            InfoItem("Group", h?.group),
                            InfoItem("Tags", h?.tags?.joinToString(", ")),
                            InfoItem("Last check", d.checked?.realOrNull()?.let { "${it.formatGermanTime()} (${it.formatRelative()})" }),
                            InfoItem("Unreachable", d.unreach?.m64Text(), valueColor = MaterialTheme.colorScheme.error),
                            InfoItem("Maintenance", d.maint?.let { m -> listOfNotNull(m.reason?.takeIf { it.isNotBlank() }, m.end?.realOrNull()?.let { "until ${it.formatGermanTime()}" }).joinToString(" · ").ifEmpty { "Running" } }),
                            InfoItem("Interval", h?.intervalSec?.takeIf { it > 0 }?.let { "$it s" }),
                            InfoItem("Notes", h?.notes)
                        ),
                        hideEmpty = true
                    )
                }
                spacedItem {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val paused = h?.paused == true
                        ActionTile(
                            if (paused) R.drawable.play_arrow_24px else R.drawable.pause_24px,
                            if (paused) "Resume" else "Pause",
                            {
                                // PUT ersetzt alle Felder: Werte des geladenen Hosts übernehmen
                                val input = Monitor64HostInput(
                                    name = h?.name.orEmpty(), address = h?.address.orEmpty(), kind = h?.kind, group = h?.group,
                                    tags = h?.tags, intervalSec = h?.intervalSec, notes = h?.notes, paused = !paused
                                )
                                act(if (paused) "Resuming..." else "Pausing...") { client?.monitor64?.hosts?.update(hostId, input) }
                            },
                            Modifier.weight(1f), enabled = h != null
                        )
                        ActionTile(
                            R.drawable.refresh_24px, "Discover services",
                            { uid?.let { id -> act("Discovering...") { client?.monitor64?.hosts?.discover(id) } } }, Modifier.weight(1f)
                        )
                    }
                }
                if (d.charts.isNotEmpty()) {
                    spacedItem {
                        ListGroup("Charts") {
                            Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                FilterChipRow(options = RANGES, selected = range, label = { it.chipLabel() }, onSelect = { range = it })
                                d.charts.forEach { chart -> chartLines[chart.id.orEmpty()]?.let { Monitor64Chart(chart, it) } }
                                if (chartLines.isEmpty()) Text("No data in this range.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                spacedItem {
                    ListGroup("Problems") {
                        when {
                            problemsError != null -> GroupText(problemsError.orEmpty(), error = true)
                            problems.isEmpty() -> ListRow("No open problems")
                            else -> problems.forEachIndexed { i, p ->
                                if (i > 0) GroupDivider()
                                val acked = p.ack != null
                                ListRow(
                                    title = p.name?.takeIf { it.isNotBlank() }?.m64Text() ?: "Host unreachable",
                                    subtitle = listOfNotNull(p.opened?.realOrNull()?.let { "Since ${it.formatGermanTime()}" }, "Acknowledged".takeIf { acked }).joinToString(" · ").ifEmpty { null },
                                    trailing = { monitor64HostState(Monitor64HostView.entries.firstOrNull { it.value == p.state?.value }, false).let { (t, c) -> StatusChip(t, colorResource(c)) } },
                                    showChevron = false
                                )
                                Row(Modifier.padding(horizontal = 8.dp)) {
                                    if (!acked) TextButton(onClick = { problemAction(p, Monitor64ProblemActionAction.ACK) }) { Text("Acknowledge") }
                                    TextButton(onClick = { problemAction(p, Monitor64ProblemActionAction.RESOLVE) }) { Text("Resolve") }
                                }
                            }
                        }
                    }
                }
                val keys = d.new.mapNotNull { it.key }
                if (keys.isNotEmpty()) {
                    spacedItem {
                        ListGroup("New services") {
                            ListRow(
                                title = "${keys.size} newly discovered",
                                subtitle = d.new.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() }?.m64Text() ?: it.key }.take(5).joinToString(", ") + if (keys.size > 5) " …" else "",
                                subtitleMaxLines = 3, showChevron = false
                            )
                            Row(Modifier.padding(horizontal = 8.dp)) {
                                TextButton(onClick = { adopt(keys, 1) }) { Text("Adopt all") }
                                TextButton(onClick = { adopt(keys, -1) }) { Text("Ignore all") }
                            }
                        }
                    }
                }
                // Dienste nach Kategorie (docker, guests, system, fs, ...)
                d.services.filter { it.key != null }.distinctBy { it.key }.groupBy { it.category?.value ?: "other" }.forEach { (cat, list) ->
                    groupedRows(list, key = { "svc_${cat}_${it.key}" }, title = cat.replaceFirstChar { it.uppercase() }, headerKey = "cat_$cat") { ServiceRow(it) }
                }
                spacedItem {
                    ListGroup("Accesses") {
                        if (d.accesses.isEmpty()) ListRow("No accesses")
                        d.accesses.forEachIndexed { i, a ->
                            if (i > 0) GroupDivider()
                            AccessRow(a)
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete host?", text = "\"${detail?.host?.name.orEmpty()}\" will be removed from Monitor64. Its history is kept.",
            confirmLabel = "Delete", destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                val id = uid ?: return@ConfirmDialog
                req.launch(scope, spinner = "Deleting...", onError = { e ->
                    localNodeError(e).let { req.message(it.first, it.second, R.drawable.warning_24px) }; true
                }) {
                    client?.monitor64?.hosts?.delete(id)
                    navController.popBackStack()
                }
            }
        )
    }

    RequestStateHost(req)
}

@Composable
private fun ServiceRow(s: Monitor64HostService) {
    val (text, color) = monitor64ServiceState(s.state)
    val metrics = s.metrics.mapNotNull { m ->
        m.value?.let { "${m.name.orEmpty()} ${it.fmt()}${m.unit?.takeIf { u -> u.isNotBlank() }?.let { u -> " $u" }.orEmpty()}".trim() }
    }.joinToString(" · ")
    ListRow(
        title = (s.customName?.takeIf { it.isNotBlank() } ?: s.name?.takeIf { it.isNotBlank() } ?: s.key.orEmpty()).m64Text(),
        subtitle = listOf(s.text.orEmpty().m64Text(), metrics).filter { it.isNotBlank() }.joinToString("\n").ifEmpty { null },
        subtitleMaxLines = 4,
        trailing = { StatusChip(text, colorResource(color)) }, showChevron = false
    )
}

@Composable
private fun AccessRow(a: Monitor64AccessState) {
    val (text, color) = when {
        a.enabled == false -> "Off" to R.color.ipv64_gray
        a.known != true -> "Unknown" to R.color.ipv64_gray
        a.ok == true -> "OK" to R.color.ipv64_green
        else -> "Error" to R.color.ipv64_red
    }
    ListRow(
        title = (a.name?.takeIf { it.isNotBlank() } ?: a.type?.value.orEmpty()).m64Text(),
        subtitle = listOfNotNull(a.type?.value, a.error?.takeIf { it.isNotBlank() }?.m64Text()).joinToString("\n"),
        subtitleMaxLines = 3,
        trailing = { StatusChip(text, colorResource(color)) }, showChevron = false
    )
}

// Liniendiagramm (avg-Werte, eine Linie je Serie); y von 0 bis max des Charts bzw. größtem Wert.
// Tippen oder Wischen zeigt Uhrzeit und Werte an dieser Stelle.
@Composable
private fun Monitor64Chart(chart: Monitor64HostChart, lines: List<Pair<String, List<Pair<Long, Double>>>>) {
    val palette = chartPalette()
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val unit = chart.unit?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
    val top = (chart.max ?: lines.maxOf { l -> l.second.maxOf { it.second } }).takeIf { it > 0 } ?: 1.0
    val times = remember(lines) { lines.flatMap { l -> l.second.map { it.first } }.distinct().sorted() }
    val tMin = times.first()
    val tMax = times.last()
    var selected by remember(lines) { mutableStateOf<Int?>(null) }
    val sel = selected?.takeIf { it in times.indices }
    val fmt = remember { SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${chart.id.orEmpty().uppercase()} (max ${top.fmt()}$unit)", style = MaterialTheme.typography.titleSmall)
        // Auslesezeile mit fester Höhe, damit das Diagramm beim Berühren nicht springt
        val readout = if (sel == null) "Touch the chart for values" else {
            val t = times[sel]
            fmt.format(Date(t)) + " · " + lines.mapNotNull { (label, pts) ->
                // nächster Punkt dieser Serie (Serien können leicht versetzte Zeitstempel haben)
                pts.minByOrNull { kotlin.math.abs(it.first - t) }?.let { (if (lines.size > 1) "$label " else "") + it.second.fmt() + unit }
            }.joinToString(" · ")
        }
        Text(readout, style = MaterialTheme.typography.labelSmall, color = if (sel == null) muted else MaterialTheme.colorScheme.onSurface, minLines = 1, maxLines = 2)
        Canvas(
            Modifier.fillMaxWidth().height(110.dp)
                .pointerInput(times) {
                    detectTapGestures { pos -> nearestIndex(times, tMin, tMax, pos.x / size.width).let { selected = if (selected == it) null else it } }
                }
                .pointerInput(times) {
                    detectHorizontalDragGestures { change, _ -> selected = nearestIndex(times, tMin, tMax, change.position.x / size.width) }
                }
        ) {
            val span = (tMax - tMin).coerceAtLeast(1L).toFloat()
            fun xOf(t: Long) = size.width * (t - tMin) / span
            fun yOf(v: Double) = size.height * (1f - (v / top).toFloat().coerceIn(0f, 1f))
            drawLine(palette.baseline, Offset(0f, size.height), Offset(size.width, size.height))
            sel?.let { val x = xOf(times[it]); drawLine(palette.baseline, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx()) }
            lines.forEachIndexed { i, (_, pts) ->
                val path = Path()
                pts.forEachIndexed { j, (t, v) -> if (j == 0) path.moveTo(xOf(t), yOf(v)) else path.lineTo(xOf(t), yOf(v)) }
                drawPath(path, palette.categorical(i), style = Stroke(width = 2.dp.toPx()))
                sel?.let { si ->
                    pts.minByOrNull { kotlin.math.abs(it.first - times[si]) }?.let { (t, v) ->
                        drawCircle(surface, radius = 6.dp.toPx(), center = Offset(xOf(t), yOf(v)))
                        drawCircle(palette.categorical(i), radius = 4.dp.toPx(), center = Offset(xOf(t), yOf(v)))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt.format(Date(tMin)), style = MaterialTheme.typography.labelSmall, color = muted)
            Text(fmt.format(Date(tMax)), style = MaterialTheme.typography.labelSmall, color = muted)
        }
        if (lines.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            lines.forEachIndexed { i, (label, _) -> Text(label, style = MaterialTheme.typography.labelSmall, color = palette.categorical(i)) }
        }
    }
}

// 404 = Host gelöscht oder Modul aus; sonst der allgemeine Node-Fehlertext
internal fun monitor64Error(e: IPv64Exception): String =
    if (e is Node64ApiException && e.status == 404) "Host not found or Monitor64 is off on this node." else localNodeError(e).second

// Die Node liefert teils unübersetzte Schlüssel ("i18n:monitor64.svc.fs?name=%2F"): letzter Schlüsselteil + Parameterwerte ("fs /")
private val I18N = Regex("""i18n:([\w.\-]+)(?:\?([^\s]*))?""")

internal fun String.m64Text(): String = I18N.replace(this) { m ->
    val word = m.groupValues[1].substringAfterLast('.').replace('_', ' ')
    val values = m.groupValues[2].split('&').mapNotNull { p ->
        p.substringAfter('=', "").takeIf { it.isNotEmpty() }?.let { v -> runCatching { java.net.URLDecoder.decode(v, "UTF-8") }.getOrDefault(v) }
    }
    if (values.isEmpty()) word else "$word ${values.joinToString(" / ")}"
}
