package de.rpicloud.ipv64net.main.views

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.DomainCache
import de.rpicloud.ipv64net.helper.DomainListCache
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.ipv64Request
import de.rpicloud.ipv64net.helper.myIp
import de.rpicloud.ipv64net.helper.v64domains
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.DomainKindFilter
import de.rpicloud.ipv64net.models.NO_IPV4
import de.rpicloud.ipv64net.models.NO_IPV6
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.rpicloud.ipv64net.models.baseDomain
import de.rpicloud.ipv64net.models.matches
import de.rpicloud.ipv64net.models.tintColor
import de.sr.ipv64.IPv64
import de.sr.ipv64.IPv64ApiException
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.Domain
import de.sr.ipv64.model.DomainDetail
import de.sr.ipv64.model.IpFamily
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// Schlüssel im savedStateHandle des Eintrags "domains"; DomainDetailView liest ihn über getBackStackEntry("domains")
private const val SELECTED_DOMAIN_KEY = "SELECTED_DOMAIN"

@Composable
fun DomainView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    var fabVisible by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var kindFilter by rememberSaveable { mutableStateOf(DomainKindFilter.ALL) }

    var domains by remember { mutableStateOf<List<Domain>>(emptyList()) }
    // Details (Records) pro Domain, werden nach der Liste nachgeladen und färben den Indikator ein
    val domainDetails = remember { mutableStateMapOf<String, DomainDetail>() }
    var myV4 by remember { mutableStateOf(NO_IPV4) }
    var myV6 by remember { mutableStateOf(NO_IPV6) }
    var detailJob by remember { mutableStateOf<Job?>(null) }

    suspend fun loadDomainDetail(ipv64: IPv64, name: String) {
        repeat(2) { attempt ->
            try {
                val detail = ipv64Request { ipv64.domains.get(name) }
                DomainCache.put(name, detail)
                domainDetails[name] = detail
                return
            } catch (e: IPv64ApiException) {
                // Rate-Limit: einmal nach der Wartezeit erneut versuchen, sonst bleibt der Indikator grau
                if (!e.isRateLimited || attempt == 1) return
                delay((e.retryAfterSeconds ?: 5) * 1000L)
            } catch (e: IPv64Exception) {
                return
            }
        }
    }

    fun getDomains(pull: Boolean = false) {
        if (pull) refreshing = true
        // Laufendes Nachladen der Details abbrechen; es startet nach der neuen Liste erneut
        detailJob?.cancel()
        req.launch(
            scope,
            spinner = if (pull) null else "Loading...",
            retry = { getDomains() },
            onFinally = { refreshing = false }
        ) {
            val ipv64 = IPv64Client.get(ctx)
            val all = ipv64Request { ipv64.domains.list() }
            DomainListCache.put(all)
            val result = all.filter { !it.name.isNullOrBlank() }
            domainDetails.clear()
            result.forEach { d ->
                DomainCache.get(d.name!!)?.let { domainDetails[d.name!!] = it }
            }
            domains = result
            loaded = true
            // Nur fehlende Details nachladen, sequentiell und höchstens eines pro Sekunde (Rate-Limit).
            // Eigener Job, damit Spinner/Pull-Indikator nicht auf die Details warten.
            val missing = result.mapNotNull { it.name }.filter { it !in domainDetails }
            detailJob = scope.launch {
                missing.forEach { name ->
                    delay(1000)
                    loadDomainDetail(ipv64, name)
                }
            }
        }
    }

    fun getMyIps() {
        // Nicht ermittelbare IPs bleiben beim Platzhalter; tintColor reagiert, sobald sie eintreffen
        scope.launch { myV4 = myIp(IpFamily.IPV4) ?: NO_IPV4 }
        scope.launch { myV6 = myIp(IpFamily.IPV6) ?: NO_IPV6 }
    }

    val visible = domains.filter { it.matches(kindFilter) }

    ScreenScaffold(
        title = Tabs.getLabel(Tab.domains),
        navController = navController,
        mainPadding = mainPadding,
        showBack = false,
        fab = {
            AnimatedVisibility(
                visible = fabVisible,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                FloatingActionButton(onClick = {
                    navController.navigate(Tabs.getRoute(Tab.domain_new))
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.domain_add_24px),
                        contentDescription = "New domain"
                    )
                }
            }
        }
    ) {
        RefreshableList(
            refreshing = refreshing,
            onRefresh = { getMyIps(); getDomains(pull = true) },
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "kind_filter") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DomainKindFilter.entries.forEach { f ->
                        FilterChip(
                            selected = kindFilter == f,
                            onClick = { kindFilter = f },
                            label = { Text(f.label) }
                        )
                    }
                }
            }
            if (loaded && visible.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        if (domains.isEmpty()) "No domains yet." else "No domains of this kind.",
                        actionLabel = if (domains.isEmpty()) "New domain" else null,
                        onAction = { navController.navigate(Tabs.getRoute(Tab.domain_new)) }
                    )
                }
            }
            String().v64domains().forEach { dom ->
                val filtered = visible.filter { it.baseDomain == dom }
                if (filtered.isNotEmpty()) {
                    stickyHeader(key = "header_$dom") {
                        Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = dom,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp)
                            )
                        }
                    }
                    items(
                        items = filtered,
                        key = { d -> d.name.orEmpty() } // stabile Keys
                    ) { d ->
                        DomainItemView(
                            d,
                            domainDetails[d.name].tintColor(myV4, myV6)
                        ) { selectedDomain ->
                            navController.currentBackStackEntry?.savedStateHandle?.set(
                                SELECTED_DOMAIN_KEY,
                                selectedDomain.name
                            )
                            navController.navigate(Tabs.getRoute(Tab.domain_details))
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        getMyIps()
        getDomains()
    }

    // Sichtbarkeit abhängig von Scrollrichtung steuern
    LaunchedEffect(listState) {
        var lastPos = 0
        var lastOff = 0
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .map { (idx, off) ->
                val scrollingDown = idx > lastPos || (idx == lastPos && off > lastOff)
                lastPos = idx; lastOff = off
                scrollingDown
            }
            .distinctUntilChanged()
            .collectLatest { scrollingDown ->
                fabVisible = !scrollingDown || !listState.isScrollInProgress
            }
    }

    // Wenn kein Scrollen mehr stattfindet, wieder einblenden
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) fabVisible = true
    }

    RequestStateHost(req)
}
