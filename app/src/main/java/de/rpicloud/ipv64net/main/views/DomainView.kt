package de.rpicloud.ipv64net.main.views

import de.rpicloud.ipv64net.main.views.common.groupedRows
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.loadDomains
import de.rpicloud.ipv64net.helper.ipv64Request
import de.rpicloud.ipv64net.helper.myIp
import de.rpicloud.ipv64net.helper.v64domains
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.FilterChipRow
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
import de.sr.ipv64.IPv64
import de.sr.ipv64.IPv64ApiException
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.Domain
import de.sr.ipv64.model.DomainDetail
import de.sr.ipv64.model.IpFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Schlüssel im savedStateHandle des Eintrags "domains"; DomainDetailView liest ihn über getBackStackEntry("domains")
private const val SELECTED_DOMAIN_KEY = "SELECTED_DOMAIN"

@Composable
fun DomainView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    var fabVisible by remember { mutableStateOf(true) }
    var showNew by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var kindFilter by rememberSaveable { mutableStateOf(DomainKindFilter.ALL) }

    var domains by remember { mutableStateOf<List<Domain>>(emptyList()) }
    // Details (Records) pro Domain, werden nur für sichtbare Einträge nachgeladen und färben den Indikator ein
    val domainDetails = remember { mutableStateMapOf<String, DomainDetail>() }
    var myV4 by remember { mutableStateOf(NO_IPV4) }
    var myV6 by remember { mutableStateOf(NO_IPV6) }
    // Serialisiert die Detail-Abrufe (höchstens einer pro Sekunde, Rate-Limit)
    val detailMutex = remember { Mutex() }
    // Zeitpunkt der letzten Anfrage (nur unter detailMutex gelesen/geschrieben); überlebt das Abbrechen eines Effekts
    var lastDetailRequest by remember { mutableLongStateOf(0L) }

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

    suspend fun ensureDetail(name: String) {
        if (name in domainDetails) return
        detailMutex.withLock {
            if (name in domainDetails) return
            val wait = lastDetailRequest + 1000 - SystemClock.elapsedRealtime()
            if (wait > 0) delay(wait)
            lastDetailRequest = SystemClock.elapsedRealtime()
            loadDomainDetail(IPv64Client.get(ctx), name)
        }
    }

    fun getDomains(pull: Boolean = false) {
        if (pull) refreshing = true
        req.launch(
            scope,
            spinner = if (pull) null else "Loading...",
            retry = { getDomains() },
            onFinally = { refreshing = false }
        ) {
            // Gemeinsame Domainliste (auch für die Pickers); Pull-to-Refresh lädt neu
            val all = loadDomains(ctx, force = pull)
            val result = all.filter { !it.name.isNullOrBlank() }
            domainDetails.clear()
            result.forEach { d ->
                DomainCache.get(d.name!!)?.let { domainDetails[d.name!!] = it }
            }
            domains = result
            loaded = true
        }
    }

    fun getMyIps() {
        // Nicht ermittelbare IPs bleiben beim Platzhalter; der Status reagiert, sobald sie eintreffen
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
                FloatingActionButton(onClick = { showNew = true }) {
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
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item(key = "kind_filter") {
                FilterChipRow(
                    options = DomainKindFilter.entries,
                    selected = kindFilter,
                    label = { it.label },
                    onSelect = { kindFilter = it },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            if (loaded && visible.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        if (domains.isEmpty()) "No domains yet." else "No domains of this kind.",
                        actionLabel = if (domains.isEmpty()) "New domain" else null,
                        onAction = { showNew = true }
                    )
                }
            }
            // Eine Gruppe, je Zeile ein Lazy-Item: Details (Indikator) werden nur für sichtbare Einträge nachgeladen
            groupedRows(
                visible.sortedBy { String().v64domains().indexOf(it.baseDomain) }, // nach Zone sortiert
                key = { it.name.orEmpty() }
            ) { d ->
                val name = d.name.orEmpty()
                LaunchedEffect(name, name in domainDetails) { ensureDetail(name) }
                DomainItemView(
                    domain = d,
                    detail = domainDetails[d.name],
                    myV4 = myV4,
                    myV6 = myV6
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

    if (showNew) DomainNewSheet(onDismiss = { showNew = false }, onCreated = { showNew = false; getDomains(pull = true) })
    RequestStateHost(req)
}
