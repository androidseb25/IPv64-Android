package de.rpicloud.ipv64net.main.views

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.DomainCache
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.NetworkService
import de.rpicloud.ipv64net.helper.ipv64Request
import de.rpicloud.ipv64net.helper.toError
import de.rpicloud.ipv64net.helper.v64domains
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.helper.views.RequestDialogs
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.models.IPResult
import de.rpicloud.ipv64net.models.NO_IPV4
import de.rpicloud.ipv64net.models.NO_IPV6
import de.rpicloud.ipv64net.models.RequestTyp
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.rpicloud.ipv64net.models.baseDomain
import de.rpicloud.ipv64net.models.tintColor
import de.sr.ipv64.IPv64
import de.sr.ipv64.IPv64ApiException
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.Domain
import de.sr.ipv64.model.DomainDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("ContextCastToActivity")
@Composable
fun DomainView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showLoadingDialog by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var fabVisible by remember { mutableStateOf(true) }
    var errorDialogTitle by remember { mutableStateOf("") }
    var errorDialogText by remember { mutableStateOf("") }
    var errorDialogButtonText by remember { mutableIntStateOf(android.R.string.ok) }

    var showRequestDialog by remember { mutableStateOf(false) }
    var requestType by remember { mutableStateOf(RequestTyp.UnAuthorized) }

    var domains by remember { mutableStateOf<List<Domain>>(emptyList()) }
    // Details (Records) pro Domain, werden nach der Liste nachgeladen und färben den Indikator ein
    val domainDetails = remember { mutableStateMapOf<String, DomainDetail>() }
    var myV4 by remember { mutableStateOf(IPResult.empty) }
    var myV6 by remember { mutableStateOf(IPResult.empty) }

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

    fun getDomains() {
        showLoadingDialog = true
        scope.launch {
            val ipv64 = IPv64Client.get(ctx)
            try {
                val result = ipv64Request { ipv64.domains.list() }.filter { !it.name.isNullOrBlank() }
                showLoadingDialog = false
                domainDetails.clear()
                result.forEach { d ->
                    DomainCache.get(d.name!!)?.let { domainDetails[d.name!!] = it }
                }
                domains = result
                // Nur fehlende Details nachladen, sequentiell und höchstens eines pro Sekunde (Rate-Limit)
                result.mapNotNull { it.name }.filter { it !in domainDetails }.forEach { name ->
                    delay(1000)
                    loadDomainDetail(ipv64, name)
                }
            } catch (e: IPv64Exception) {
                showLoadingDialog = false
                println(e.message)
                val error = e.toError()
                if (error.requestType != null) {
                    requestType = error.requestType
                    showRequestDialog = true
                } else {
                    errorDialogTitle = error.title
                    errorDialogText = error.text
                    errorDialogButtonText = R.string.retry
                    showDialog = true
                }
            }
        }
    }

    fun getMyV6() {
        showLoadingDialog = true
        scope.launch(Dispatchers.IO) {
            NetworkService(ctx).GetMyIP(forV4 = false) { nwResult ->
                showLoadingDialog = false
                when (nwResult.status) {
                    200 -> {
                        if (nwResult.data == null) {
                            println(nwResult.message)
                            errorDialogTitle = "Data not found"
                            errorDialogText = "There are no data found!"
                            errorDialogButtonText = R.string.retry
                            showDialog = true
                        } else {
                            (nwResult.data as IPResult).also { myV6 = it }
                            println(myV6)
                            getDomains()
                        }
                    }

                    400 -> {
                        println(nwResult.message)
                        errorDialogTitle = "Loading error"
                        errorDialogText = nwResult.message.toString()
                        errorDialogButtonText = R.string.retry
                        showDialog = true
                    }

                    else -> {
//                        println(nwResult.message)
//                        errorDialogTitle = "Loading error"
//                        errorDialogText = nwResult.message.toString()
//                        errorDialogButtonText = R.string.retry
//                        showDialog = true
                        myV6.ip = "::"
                        getDomains()
                    }
                }
            }
        }
    }

    fun getMyV4() {
        showLoadingDialog = true
        scope.launch(Dispatchers.IO) {
            NetworkService(ctx).GetMyIP(forV4 = true) { nwResult ->
                showLoadingDialog = false
                when (nwResult.status) {
                    200 -> {
                        if (nwResult.data == null) {
                            println(nwResult.message)
                            errorDialogTitle = "Data not found"
                            errorDialogText = "There are no data found!"
                            errorDialogButtonText = R.string.retry
                            showDialog = true
                        } else {
                            (nwResult.data as IPResult).also { myV4 = it }
                            println(myV4)
                            getMyV6()
                        }
                    }

                    400 -> {
                        println(nwResult.message)
                        errorDialogTitle = "Loading error"
                        errorDialogText = nwResult.message.toString()
                        errorDialogButtonText = R.string.retry
                        showDialog = true
                    }

                    else -> {
//                        println(nwResult.message)
//                        errorDialogTitle = "Loading error"
//                        errorDialogText = nwResult.message.toString()
//                        errorDialogButtonText = R.string.retry
//                        showDialog = true
                        myV4.ip = "0.0.0.0"
                        getMyV6()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(Tabs.getLabel(Tab.domains))
                }, modifier = Modifier.statusBarsPadding()
            )
        },
        floatingActionButton = {
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
                        contentDescription = "icon"
                    )
                }
            }
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                String().v64domains().forEach { dom ->
                    val filtered = domains.filter { it -> it.baseDomain == dom }
                    if (filtered.isNotEmpty()) {
                        stickyHeader {
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
                                domainDetails[d.name].tintColor(myV4.ip ?: NO_IPV4, myV6.ip ?: NO_IPV6)
                            ) { selectedDomain ->
                                navController.currentBackStackEntry?.savedStateHandle?.set(
                                    "SELECTED_DOMAIN",
                                    selectedDomain.name
                                )
                                navController.navigate(Tabs.getRoute(Tab.domain_details))
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        getMyV4()
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

    if (showLoadingDialog) {
        SpinnerDialog(
            spinnerText = "Loading..."
        )
    }

    if (showDialog) {
        ErrorDialog(
            onDismissRequest = { showDialog = false },
            onConfirmation = { showDialog = false; getDomains() },
            dialogTitle = errorDialogTitle,
            dialogText = errorDialogText,
            dialogConfirmText = errorDialogButtonText
        )
    }

    if (showRequestDialog) {
        RequestDialogs(
            onDismissRequest = { showRequestDialog = false },
            onConfirmation = { showRequestDialog = false; getDomains() },
            dialogConfirmText = errorDialogButtonText,
            request = requestType
        )
    }
}