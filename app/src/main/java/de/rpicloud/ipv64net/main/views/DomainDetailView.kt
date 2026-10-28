package de.rpicloud.ipv64net.main.views

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.DomainCache
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.IPv64Context
import de.rpicloud.ipv64net.helper.ipv64Request
import de.rpicloud.ipv64net.helper.myIp
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.helper.toError
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.helper.views.RequestDialogs
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.models.NO_IPV4
import de.rpicloud.ipv64net.models.NO_IPV6
import de.rpicloud.ipv64net.models.RequestTyp
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.rpicloud.ipv64net.models.IsWildcard
import de.rpicloud.ipv64net.models.isSameTypeAAAAAddress
import de.rpicloud.ipv64net.models.knownIp
import de.sr.ipv64.model.IpFamily
import de.rpicloud.ipv64net.models.isSameTypeAAddress
import de.sr.ipv64.IPv64ApiException
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.DomainDetail
import de.sr.ipv64.model.DynDnsOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@SuppressLint("UnrememberedGetBackStackEntry")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainDetailView(navController: NavHostController, mainPadding: PaddingValues) {

    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboard.current

    var showLoadingDialog by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeleteDNSDialog by remember { mutableStateOf(false) }
    var fabVisible by remember { mutableStateOf(true) }
    var errorDialogTitle by remember { mutableStateOf("") }
    var errorDialogText by remember { mutableStateOf("") }
    var errorDialogButtonText by remember { mutableIntStateOf(android.R.string.ok) }
    var deleteDialogTitle by remember { mutableStateOf("") }
    var deleteDialogText by remember { mutableStateOf("") }
    var deleteDialogButtonText by remember { mutableIntStateOf(R.string.delete) }
    var selectedDomain by remember { mutableStateOf(DomainDetail()) }
    var selectedRecordId by remember { mutableLongStateOf(0L) }

    var showRequestDialog by remember { mutableStateOf(false) }
    var requestType by remember { mutableStateOf(RequestTyp.UnAuthorized) }

    var myV4 by remember { mutableStateOf(NO_IPV4) }
    var myV6 by remember { mutableStateOf(NO_IPV6) }
    val domainRoute = Tabs.getRoute(Tab.domains)
    val domainBackStackEntry = remember(domainRoute) {
        navController.getBackStackEntry(domainRoute)
    }
    // Jetpack Compose Best Practice: collectAsStateWithLifecycle pauses flow collection when UI is stopped (e.g. backgrounded), saving battery and CPU.
    val domain by domainBackStackEntry.savedStateHandle.getStateFlow<String>("SELECTED_DOMAIN", "").collectAsStateWithLifecycle()

    fun GetDomainUpdateUrl(): String {
        return "https://ipv64.net/nic/update?key=" + selectedDomain.updateToken
    }

    var domainJob by remember { mutableStateOf<Job?>(null) }

    fun GetDomain() {
        // Eine neue Anfrage ersetzt eine noch laufende
        domainJob?.cancel()
        // Mit Cache-Treffer wird ohne Spinner im Hintergrund aktualisiert
        showLoadingDialog = DomainCache.get(domain) == null
        domainJob = scope.launch {
            try {
                val ipv64 = IPv64Client.get(ctx)
                val result = try {
                    ipv64Request { ipv64.domains.get(domain) }
                } catch (e: IPv64ApiException) {
                    // Rate-Limit: einmal nach der Wartezeit erneut versuchen
                    if (!e.isRateLimited) throw e
                    delay((e.retryAfterSeconds ?: 5) * 1000L)
                    ipv64Request { ipv64.domains.get(domain) }
                }
                DomainCache.put(domain, result)
                selectedDomain = result
            } catch (e: IPv64Exception) {
                if (!isActive) return@launch
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
            } finally {
                if (isActive) showLoadingDialog = false
            }
        }
    }

    fun GetMyIPs() {
        // Die aktuellen IPs werden nur für den Vergleich mit den Records benötigt, Fehler sind daher unkritisch
        scope.launch { myV4 = myIp(IpFamily.IPV4) ?: NO_IPV4 }
        scope.launch { myV6 = myIp(IpFamily.IPV6) ?: NO_IPV6 }
    }

    fun DeleteDomain() {
        if (domain.isEmpty()) {
            errorDialogTitle = "Data not found"
            errorDialogText = "FQDN not found!"
            errorDialogButtonText = R.string.retry
            showDialog = true
            return
        }
        showLoadingDialog = true
        scope.launch {
            try {
                ipv64Request { IPv64Client.get(ctx).domains.delete(domain) }
                DomainCache.invalidate(domain)
                showLoadingDialog = false
                navController.popBackStack()
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

    fun DeleteDNSRecord(recordId: Long) {
        showLoadingDialog = true
        scope.launch {
            try {
                ipv64Request { IPv64Client.get(ctx).records.delete(recordId.toString()) }
                DomainCache.invalidate(domain)
                showLoadingDialog = false
                GetDomain()
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

    fun UpdateDNSRecord() {
        showLoadingDialog = true
        scope.launch {
            try {
                // Nur bekannte IPs explizit senden, sonst verwendet die API die Absenderadresse
                val result = ipv64Request {
                    IPv64Client.get(ctx).dynDns.update(
                        domain,
                        ipv4 = knownIp(myV4),
                        ipv6 = knownIp(myV6)
                    )
                }
                DomainCache.invalidate(domain)
                showLoadingDialog = false
                val failed = result.results.filter {
                    it.result == DynDnsOutcome.INVALID_IP || it.result == DynDnsOutcome.CNAME_CONFLICT ||
                            it.result == DynDnsOutcome.CDN_MANAGED
                }
                if (failed.isEmpty()) {
                    navController.popBackStack()
                } else {
                    errorDialogTitle = "Update error"
                    errorDialogText = failed.joinToString("\n") { "${it.type ?: ""} ${it.ip ?: ""}: ${it.result?.value}" }
                    errorDialogButtonText = R.string.retry
                    showDialog = true
                }
            } catch (e: IPv64Exception) {
                showLoadingDialog = false
                println(e.message)
                val error = e.toError(IPv64Context.Update)
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

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        painter = painterResource(id = R.drawable.arrow_back_24px),
                        contentDescription = "Close"
                    )
                }
            }, actions = {
                IconButton(onClick = {
                    deleteDialogText = "Do you really want to delete the Domain?"
                    deleteDialogTitle = "Delete Domain"
                    showDeleteDialog = true
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.delete_24px),
                        contentDescription = "Delete"
                    )
                }
            }, title = {
                Text(domain, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    navController.currentBackStackEntry?.savedStateHandle?.set("ADD_FQDN", domain)
                    navController.navigate(Tabs.getRoute(Tab.domain_new_dns))
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.add_24px),
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
                if (selectedDomain.name != null && ((!selectedDomain.isSameTypeAAddress(myV4) && myV4 != NO_IPV4) || (!selectedDomain.isSameTypeAAAAAddress(myV6) && myV6 != NO_IPV6))) {
                    stickyHeader {
                        Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = "Notice",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp)
                            )
                        }
                    }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row {
                                    Text("Your A or AAAA record doesn't match your current IP! This could be due to an active cellular connection or the IP address being assigned a server address.")
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 32.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            UpdateDNSRecord()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = Color(
                                                ContextCompat.getColor(
                                                    ctx, R.color.ipv64_orange_trans20
                                                )
                                            ),
                                            contentColor = Color(
                                                ContextCompat.getColor(
                                                    ctx, R.color.ipv64_orange
                                                )
                                            ),
                                        )
                                    ) {
                                        if (!selectedDomain.isSameTypeAAddress(myV4) && myV4 != NO_IPV4) {
                                            Text("Set A-Record to current IP?")
                                        } else {
                                            Text("Set AAAA-Record to current IP?")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                stickyHeader {
                    Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = "General",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp)
                        )
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row {
                                Text(
                                    "Wildcard:", fontWeight = FontWeight.Bold
                                )
                                Text(
                                    selectedDomain.IsWildcard,
                                    style = MaterialTheme.typography.titleSmall,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color.Gray
                                )
                            }
                            Row(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    "Updates:", fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${selectedDomain.updateCount ?: 0}",
                                    style = MaterialTheme.typography.titleSmall,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color.Gray
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 32.dp)
                            ) {
                                Button(
                                    enabled = selectedDomain.updateToken != null,
                                    onClick = {
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        val clipboard =
                                            ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText(
                                            "Domain Update URL", GetDomainUpdateUrl()
                                        )
                                        clipboard.setPrimaryClip(clip)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color(
                                            ContextCompat.getColor(
                                                ctx, R.color.ipv64_blue_trans15
                                            )
                                        ),
                                        contentColor = Color(
                                            ContextCompat.getColor(
                                                ctx, R.color.ipv64_blue
                                            )
                                        ),
                                    )
                                ) {
                                    Text("Copy Domain Update URL")
                                }
                            }
                        }
                    }
                }
                selectedDomain.records.forEach { recordInfos ->
                    stickyHeader {
                        Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                            tonalElevation = 2.dp
                        ) {
                            recordInfos.type?.value?.let { text ->
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row {
                                    Text(
                                        "Präfix:", fontWeight = FontWeight.Bold
                                    )
                                    recordInfos.prefix?.let { text ->
                                        Text(
                                            text,
                                            style = MaterialTheme.typography.titleSmall,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.fillMaxWidth(),
                                            color = Color.Gray
                                        )
                                    }
                                }
                                Row(modifier = Modifier.padding(top = 16.dp)) {
                                    Text(
                                        "TTL:", fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${recordInfos.ttl}",
                                        style = MaterialTheme.typography.titleSmall,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color.Gray
                                    )
                                }
                                Row(modifier = Modifier.padding(top = 16.dp)) {
                                    Text(
                                        "Typ:", fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${recordInfos.type?.value}",
                                        style = MaterialTheme.typography.titleSmall,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color.Gray
                                    )
                                }
                                Row(modifier = Modifier.padding(top = 16.dp)) {
                                    Text(
                                        "Value:", fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${recordInfos.content}",
                                        style = MaterialTheme.typography.titleSmall,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color.Gray
                                    )
                                }
                                Row(modifier = Modifier.padding(top = 16.dp)) {
                                    Text(
                                        "last update:", fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        recordInfos.updatedAt?.formatGermanTime()
                                            ?: "01.01.0001 00:00:00",
                                        style = MaterialTheme.typography.titleSmall,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color.Gray
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 32.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            selectedRecordId = recordInfos.id ?: 0L
                                            deleteDialogText =
                                                "Do you really want to delete the DNS Record?"
                                            deleteDialogTitle = "Delete DNS Record"
                                            showDeleteDNSDialog = true
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = Color(
                                                ContextCompat.getColor(
                                                    ctx, R.color.ipv64_red_trans20
                                                )
                                            ),
                                            contentColor = Color(
                                                ContextCompat.getColor(
                                                    ctx, R.color.ipv64_red
                                                )
                                            ),
                                        )
                                    ) {
                                        Text("Delete DNS Record")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        DomainCache.get(domain)?.let { selectedDomain = it }
        GetDomain()
        GetMyIPs()
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
            onConfirmation = { showDialog = false; },
            dialogTitle = errorDialogTitle,
            dialogText = errorDialogText,
            dialogConfirmText = errorDialogButtonText
        )
    }

    if (showDeleteDialog) {
        ErrorDialog(
            onDismissRequest = { showDeleteDialog = false },
            onConfirmation = { showDeleteDialog = false; DeleteDomain() },
            dialogTitle = deleteDialogTitle,
            dialogText = deleteDialogText,
            dialogConfirmText = deleteDialogButtonText,
            icon = R.drawable.delete_24px,
            showDismiss = true
        )
    }

    if (showDeleteDNSDialog && selectedRecordId != 0L) {
        ErrorDialog(
            onDismissRequest = { showDeleteDNSDialog = false },
            onConfirmation = { showDeleteDNSDialog = false; DeleteDNSRecord(selectedRecordId) },
            dialogTitle = deleteDialogTitle,
            dialogText = deleteDialogText,
            dialogConfirmText = deleteDialogButtonText,
            icon = R.drawable.delete_24px,
            showDismiss = true
        )
    }

    if (showRequestDialog) {
        RequestDialogs(
            onDismissRequest = { showRequestDialog = false },
            onConfirmation = { showRequestDialog = false; },
            dialogConfirmText = errorDialogButtonText,
            request = requestType
        )
    }
}