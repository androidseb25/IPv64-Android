package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.ipv64Request
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.GroupDivider
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.LogEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive

// Einträge pro Gruppe in der Liste
private const val LOG_CHUNK = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val req = rememberRequestState()

    // Geladene Einträge (neueste zuerst), nextBefore ist der Cursor für die nächste Seite
    val logList = remember { mutableStateListOf<LogEntry>() }
    var nextBefore by remember { mutableStateOf<Long?>(null) }
    var retentionDays by remember { mutableStateOf<Int?>(null) }
    var isLoadingMore by remember { mutableStateOf(false) }
    // Nach einem Fehler beim Nachladen nicht automatisch weiterladen, sondern per Retry
    var pagingFailed by remember { mutableStateOf(false) }
    var loadJob by remember { mutableStateOf<Job?>(null) }

    // Erste Seite erfolgreich geladen bzw. fehlgeschlagen (für Leer- und Fehlerzustand)
    var loaded by remember { mutableStateOf(false) }
    var firstPageFailed by remember { mutableStateOf(false) }
    // Zählt die Abrufe; nur der aktuelle setzt die Anzeige zurück
    var loadGen by remember { mutableIntStateOf(0) }

    // reset = true lädt die erste Seite neu, sonst wird die nächste Seite angehängt
    fun loadPage(reset: Boolean) {
        // Keine parallelen Seitenabrufe und kein Nachladen am Ende der Liste
        if (!reset && (pagingFailed || loadJob?.isActive == true || nextBefore == null)) return
        loadJob?.cancel()
        val before = if (reset) null else nextBefore
        val gen = ++loadGen
        if (reset) {
            pagingFailed = false
            firstPageFailed = false
        } else {
            isLoadingMore = true
        }
        loadJob = req.launch(
            scope,
            spinner = if (reset) "Loading..." else null,
            retry = { if (reset) loadPage(true) else { pagingFailed = false; loadPage(false) } },
            // Nur der aktuelle Abruf setzt die Anzeige zurück
            onFinally = { if (gen == loadGen) isLoadingMore = false }
        ) {
            try {
                val page = ipv64Request { IPv64Client.get(ctx).account.logs(before = before) }
                if (reset) logList.clear()
                val known = logList.mapNotNull { it.id }.toHashSet()
                // Einträge ohne ID bleiben erhalten, doppelte IDs werden verworfen
                logList.addAll(page.data.filter { it.id == null || known.add(it.id!!) })
                nextBefore = page.meta?.nextBefore
                retentionDays = page.meta?.retentionDays ?: retentionDays
                pagingFailed = false
                loaded = true
            } catch (e: IPv64Exception) {
                // Abgelöste Anfrage: keinen Fehler anzeigen
                if (!isActive) return@launch
                if (reset) firstPageFailed = true else pagingFailed = true
                throw e
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(Tabs.getLabel(Tab.logs))
                }, modifier = Modifier.statusBarsPadding(), navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_back_24px),
                            contentDescription = "Close"
                        )
                    }
                }
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(mainPadding)
            .consumeWindowInsets(mainPadding)  // avoids double insets
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .fillMaxHeight()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (loaded && logList.isEmpty()) {
                    item(key = "empty") { EmptyState("No logs yet.") }
                } else if (firstPageFailed && logList.isEmpty()) {
                    item(key = "failed") {
                        EmptyState("Logs could not be loaded.", actionLabel = "Retry", onAction = { loadPage(reset = true) })
                    }
                }
                // Je 10 Einträge eine Gruppe: die Liste bleibt lazy, wirkt aber wie eine Fläche
                val chunks = logList.toList().chunked(LOG_CHUNK)
                itemsIndexed(items = chunks, key = { i, c -> c.first().id ?: "chunk-$i" }) { _, chunk ->
                    ListGroup {
                        chunk.forEachIndexed { i, log ->
                            if (i > 0) GroupDivider()
                            LogItemView(log)
                        }
                    }
                }
                if (pagingFailed) {
                    item(key = "retry") {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextButton(onClick = { pagingFailed = false; loadPage(reset = false) }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                } else if (isLoadingMore) {
                    item(key = "loading-more") {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                } else if (nextBefore == null && logList.isNotEmpty()) {
                    retentionDays?.let { days ->
                        item(key = "retention") {
                            Text(
                                "Logs are kept for $days days",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadPage(reset = true)
    }

    // Nächste Seite laden, sobald das Listenende fast erreicht ist
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }.collect { (last, total) ->
            if (total > 0 && last >= total - 2) loadPage(reset = false)
        }
    }

    RequestStateHost(req)
}
