package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.rememberNavArg
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.FilterChipRow
import de.rpicloud.ipv64net.main.views.common.GroupText
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.SearchField
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.node64.model.DNS64LogRow
import de.sr.ipv64.node64.model.DNS64LogStatus
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val LOG_REFRESH_MS = 3_000L
private const val LOG_LIMIT = 100
private val LOG_FILTERS = listOf("All" to null, "Blocked" to DNS64LogStatus.BLOCKED, "Allowed" to DNS64LogStatus.ALLOWED)

/** Live-Abfrageprotokoll einer direkt verbundenen Node (Node-ID per Nav-Argument). */
@Composable
fun NodeDnsLogView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    val id = navController.rememberNavArg<String>(NavKeys.SELECTED_NODE_ID)
    var config by remember { mutableStateOf<Pair<String, String>?>(null) }
    var rows by remember { mutableStateOf<List<DNS64LogRow>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var live by rememberSaveable { mutableStateOf(true) }
    var filter by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var allowRow by remember { mutableStateOf<DNS64LogRow?>(null) }
    val time = remember { SimpleDateFormat("HH:mm:ss", Locale.GERMANY) }

    LaunchedEffect(id) { config = id?.let { LocalNodeStore.get(ctx, it) } }

    // Abfrage nur solange RESUMED; Filter-/Suchänderung startet die Schleife neu (Suche mit kurzer Verzögerung)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, config, live, filter, query) {
        val (url, key) = config ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (query.isNotEmpty()) delay(300)
            do {
                try {
                    rows = localNodeClient(url, key).dns64.log(status = LOG_FILTERS[filter].second, q = query.trim().ifEmpty { null }, limit = LOG_LIMIT).rows
                    error = null
                } catch (e: IPv64Exception) {
                    error = localNodeError(e).let { "${it.first}: ${it.second}" }
                }
                if (live) delay(LOG_REFRESH_MS)
            } while (live)
        }
    }

    ScreenScaffold(
        title = Tabs.getLabel(Tab.node_dns_log),
        navController = navController,
        mainPadding = mainPadding,
        actions = {
            IconButton(onClick = { live = !live }) {
                Icon(painterResource(if (live) R.drawable.pause_24px else R.drawable.play_arrow_24px), contentDescription = if (live) "Pause live update" else "Resume live update")
            }
        }
    ) {
        if (config == null) return@ScreenScaffold EmptyState("No direct connection to this node.")
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchField(query, { query = it }, "Domain or client")
                FilterChipRow(LOG_FILTERS.indices.toList(), filter, { LOG_FILTERS[it].first }, { filter = it })
            }
            error?.let { GroupText(it, Modifier.padding(horizontal = 16.dp), error = true) }
            if (rows.isEmpty() && error == null) GroupText("No queries.", Modifier.padding(horizontal = 16.dp))
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Ohne Key: identische Anfragen in derselben Millisekunde ergäben doppelte Keys (Absturz)
                items(rows) { r ->
                    val (color, label) = when (r.status) {
                        "blocked" -> R.color.ipv64_red to "Blocked"
                        "error" -> R.color.ipv64_orange to "Error"
                        "allowed", "cached" -> R.color.ipv64_green to r.status!!.replaceFirstChar { it.uppercase() }
                        else -> R.color.ipv64_gray to (r.status ?: "–").replaceFirstChar { it.uppercase() }
                    }
                    val blocked = r.status == "blocked" && r.domain != null
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                        ListRow(
                            title = r.domain ?: "–",
                            subtitle = buildString {
                                append(r.t?.let { time.format(Date(it)) } ?: "–")
                                append(" · ").append(r.clientName?.takeIf { it.isNotBlank() } ?: r.client ?: "–")
                                r.qtype?.let { append(" · ").append(it) }
                                if (r.upstream != null || r.ms != null) append("\n").append(r.upstream ?: "").append(r.ms?.let { " ${"%.0f".format(Locale.US, it)} ms" } ?: "")
                                r.reason?.takeIf { it.isNotBlank() && blocked }?.let { append("\n").append(it) }
                            },
                            subtitleMaxLines = 4,
                            showChevron = false,
                            trailing = { StatusChip(label, colorResource(color)) },
                            onClick = if (blocked) ({ allowRow = r }) else null
                        )
                    }
                }
            }
        }
    }

    allowRow?.let { r ->
        AlertDialog(
            onDismissRequest = { allowRow = null },
            title = { Text(r.domain ?: "") },
            text = { Text("Blocked${r.reason?.takeIf { it.isNotBlank() }?.let { " by $it" } ?: ""}. Allow this domain and its subdomains for all clients?") },
            confirmButton = {
                TextButton(onClick = {
                    val (url, key) = config ?: return@TextButton
                    val d = r.domain ?: return@TextButton
                    allowRow = null
                    req.launch(
                        scope,
                        spinner = "Updating...",
                        onError = { e -> localNodeError(e).let { req.message(it.first, it.second) }; true }
                    ) { localNodeClient(url, key).dns64.allow(d) }
                }) { Text("Allow this domain") }
            },
            dismissButton = { TextButton(onClick = { allowRow = null }) { Text("Cancel") } }
        )
    }
    RequestStateHost(req)
}
