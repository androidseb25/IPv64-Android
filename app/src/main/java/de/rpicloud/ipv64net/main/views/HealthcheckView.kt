package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.Hc2CheckListCache
import de.rpicloud.ipv64net.helper.Hc2NavKeys
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.htmlText
import de.rpicloud.ipv64net.helper.ipv64Retrying
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthcheckView(navController: NavHostController, mainPadding: PaddingValues) {
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

    var checks by remember { mutableStateOf<List<Hc2Check>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(HcFilter.ALL) }
    var query by remember { mutableStateOf("") }

    // Liste einmal laden (20 s Cache), force = Pull-to-Refresh; nie pro Zeile
    fun loadChecks(force: Boolean, pullRefresh: Boolean = false) {
        // Client zuerst holen: leert bei API-Key-Wechsel die Caches
        IPv64Client.get(ctx)
        if (!force) {
            Hc2CheckListCache.get()?.let {
                checks = it.sortedBy { c -> c.name.orEmpty().lowercase() }
                loaded = true
                return
            }
        }
        if (pullRefresh) refreshing = true else showLoadingDialog = true
        scope.launch {
            try {
                val ipv64 = IPv64Client.get(ctx)
                val result = ipv64Retrying { ipv64.hc2.checks.list() }
                Hc2CheckListCache.put(result)
                checks = result.sortedBy { c -> c.name.orEmpty().lowercase() }
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

    val counts = remember(checks) { checks.counts() }
    val visible = remember(checks, filter, query) {
        val q = query.trim().lowercase()
        checks.filter { c ->
            filter.matches(c) && (q.isEmpty() ||
                c.name.orEmpty().lowercase().contains(q) ||
                c.target.orEmpty().lowercase().contains(q) ||
                c.type.label.lowercase().contains(q))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Tabs.getLabel(Tab.healthcheck)) },
                modifier = Modifier.statusBarsPadding()
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Tabs.getRoute(Tab.healthcheck_new)) }) {
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { HealthcheckSummaryView(counts) }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HcFilter.entries.forEach { f ->
                            FilterChip(selected = f == filter, onClick = { filter = f }, label = { Text(f.label) })
                        }
                    }
                }
                items(items = visible, key = { c -> c.token.orEmpty() + c.name.orEmpty() }) { c ->
                    HealthcheckItemView(c) { selected ->
                        selected.token?.let { token ->
                            navController.currentBackStackEntry?.savedStateHandle?.set(
                                Hc2NavKeys.SELECTED_HC2_TOKEN, token
                            )
                            navController.navigate(Tabs.getRoute(Tab.healthcheck_details))
                        }
                    }
                }
                if (loaded && visible.isEmpty()) {
                    item {
                        Text(
                            if (checks.isEmpty()) "No healthchecks yet." else "No healthchecks match.",
                            textAlign = TextAlign.Center,
                            color = Color.Gray,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                item {
                    Text(
                        "Classic healthchecks are managed at ipv64.net",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
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
