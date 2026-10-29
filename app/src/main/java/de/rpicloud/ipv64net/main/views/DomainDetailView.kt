package de.rpicloud.ipv64net.main.views

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.AccountCache
import de.rpicloud.ipv64net.helper.DomainCache
import de.rpicloud.ipv64net.helper.DomainListCache
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.IPv64Context
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.copyText
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.helper.ipv64Request
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.myIp
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.main.views.common.ConfirmDialog
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.InfoRow
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.SectionHeader
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.IsWildcard
import de.rpicloud.ipv64net.models.NO_IPV4
import de.rpicloud.ipv64net.models.NO_IPV6
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.rpicloud.ipv64net.models.domainUpdateUrl
import de.rpicloud.ipv64net.models.isFailure
import de.rpicloud.ipv64net.models.isLocked
import de.rpicloud.ipv64net.models.isSameTypeAAAAAddress
import de.rpicloud.ipv64net.models.isSameTypeAAddress
import de.rpicloud.ipv64net.models.knownIp
import de.rpicloud.ipv64net.models.label
import de.rpicloud.ipv64net.models.prefixLabel
import de.sr.ipv64.IPv64ApiException
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.DomainDetail
import de.sr.ipv64.model.IpFamily
import de.sr.ipv64.model.Record
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Schlüssel im savedStateHandle dieses Eintrags; DomainDnsNewView liest ihn über getBackStackEntry("domain_details")
private const val ADD_FQDN_KEY = "ADD_FQDN"

@SuppressLint("UnrememberedGetBackStackEntry")
@Composable
fun DomainDetailView(navController: NavHostController, mainPadding: PaddingValues) {

    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    val haptics = LocalHapticFeedback.current

    var fabVisible by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var selectedDomain by remember { mutableStateOf(DomainDetail()) }

    // Offene Dialoge
    var confirmAction by remember { mutableStateOf<DomainAction?>(null) }
    var recordToDelete by remember { mutableStateOf<Record?>(null) }
    var showDynDns by remember { mutableStateOf(false) }
    // Neue Update-URL nach dem Token-Wechsel (nur im Speicher, nicht im savedState)
    var rotatedUrl by remember { mutableStateOf<String?>(null) }

    var myV4 by remember { mutableStateOf(NO_IPV4) }
    var myV6 by remember { mutableStateOf(NO_IPV6) }
    val domainRoute = Tabs.getRoute(Tab.domains)
    val domainBackStackEntry = remember(domainRoute) {
        navController.getBackStackEntry(domainRoute)
    }
    // collectAsStateWithLifecycle pausiert das Sammeln, solange die UI gestoppt ist
    val domain by domainBackStackEntry.savedStateHandle.getStateFlow("SELECTED_DOMAIN", "").collectAsStateWithLifecycle()

    var domainJob by remember { mutableStateOf<Job?>(null) }

    fun getDomain(pull: Boolean = false) {
        if (domain.isEmpty()) return
        // Eine neue Anfrage ersetzt eine noch laufende
        domainJob?.cancel()
        if (pull) refreshing = true
        // Mit Cache-Treffer wird ohne Spinner im Hintergrund aktualisiert
        val silent = pull || DomainCache.get(domain) != null
        domainJob = req.launch(
            scope,
            spinner = if (silent) null else "Loading...",
            retry = { getDomain() },
            onFinally = { refreshing = false }
        ) {
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
                // Abgelöste Anfrage: keinen Fehler anzeigen
                if (!isActive) return@launch
                throw e
            }
        }
    }

    fun getMyIPs() {
        // Die aktuellen IPs werden nur für den Vergleich mit den Records benötigt, Fehler sind daher unkritisch
        scope.launch { myV4 = myIp(IpFamily.IPV4) ?: NO_IPV4 }
        scope.launch { myV6 = myIp(IpFamily.IPV6) ?: NO_IPV6 }
    }

    fun deleteDomain() {
        if (domain.isEmpty()) return
        req.launch(
            scope,
            spinner = "Deleting...",
            onError = { e ->
                // 409: Domain wird von einer aktiven DNS-Failover-Policy verwendet
                if (e is IPv64ApiException && e.isConflict) {
                    req.message(
                        "Domain cannot be deleted",
                        e.detail ?: "DNS failover is active for this domain. Remove or pause the failover policy first.",
                        R.drawable.warning_24px
                    )
                    true
                } else false
            }
        ) {
            ipv64Request { IPv64Client.get(ctx).domains.delete(domain) }
            DomainCache.invalidate(domain)
            DomainListCache.invalidate()
            // Domainzähler in Me.usage haben sich geändert
            AccountCache.invalidate()
            navController.popBackStack()
        }
    }

    fun deleteRecord(record: Record) {
        val id = record.id ?: return
        req.launch(scope, spinner = "Deleting...") {
            ipv64Request { IPv64Client.get(ctx).records.delete(id.toString()) }
            // Record lokal entfernen statt neu zu laden
            val updated = selectedDomain.copy(records = selectedDomain.records.filter { it.id != id })
            selectedDomain = updated
            DomainCache.put(domain, updated)
        }
    }

    fun rotateToken() {
        req.launch(scope, spinner = "Rotating token...") {
            val result = ipv64Retrying { IPv64Client.get(ctx).domains.rotateToken(domain) }
            DomainListCache.invalidate()
            val token = result.updateToken
            if (token.isNullOrEmpty()) {
                // Antwort ohne Token: Domain neu laden, die URL steht dann im Abschnitt "General"
                DomainCache.invalidate(domain)
                getDomain()
                req.message("Token rotated", "The update token was rotated. Copy the new update URL from the domain details.")
            } else {
                val updated = selectedDomain.copy(updateToken = token)
                selectedDomain = updated
                DomainCache.put(domain, updated)
                rotatedUrl = domainUpdateUrl(token)
            }
        }
    }

    fun resetDomain() {
        req.launch(scope, spinner = "Resetting...") {
            val result = ipv64Retrying { IPv64Client.get(ctx).domains.reset(domain) }
            DomainCache.put(domain, result)
            DomainListCache.invalidate()
            selectedDomain = result
        }
    }

    fun updateDNSRecord() {
        req.launch(scope, spinner = "Loading...", context = IPv64Context.Update) {
            // Nur bekannte IPs explizit senden, sonst verwendet die API die Absenderadresse
            val result = ipv64Request {
                IPv64Client.get(ctx).dynDns.update(
                    domain,
                    ipv4 = knownIp(myV4),
                    ipv6 = knownIp(myV6)
                )
            }
            DomainCache.invalidate(domain)
            val failed = result.results.filter { it.result.isFailure }
            if (failed.isEmpty()) {
                navController.popBackStack()
            } else {
                req.message(
                    "Update error",
                    failed.joinToString("\n") { "${it.type ?: ""} ${it.ip ?: ""}: ${it.result.label}" },
                    R.drawable.error_24px
                )
            }
        }
    }

    fun onAction(action: DomainAction) {
        when (action) {
            DomainAction.SETTINGS ->
                navController.navigateWithArgs(Tab.domain_settings, NavKeys.EDIT_DOMAIN_NAME to domain)

            DomainAction.DYNDNS -> showDynDns = true
            else -> confirmAction = action
        }
    }

    ScreenScaffold(
        title = domain,
        navController = navController,
        mainPadding = mainPadding,
        actions = {
            DomainActionsMenu(enabled = domain.isNotEmpty() && selectedDomain.name != null) { onAction(it) }
        },
        fab = {
            AnimatedVisibility(
                visible = fabVisible,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                FloatingActionButton(onClick = {
                    navController.currentBackStackEntry?.savedStateHandle?.set(ADD_FQDN_KEY, domain)
                    navController.navigate(Tabs.getRoute(Tab.domain_new_dns))
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.add_24px),
                        contentDescription = "New DNS record"
                    )
                }
            }
        }
    ) {
        RefreshableList(
            refreshing = refreshing,
            onRefresh = { getMyIPs(); getDomain(pull = true) },
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val mismatchV4 = !selectedDomain.isSameTypeAAddress(myV4) && myV4 != NO_IPV4
            val mismatchV6 = !selectedDomain.isSameTypeAAAAAddress(myV6) && myV6 != NO_IPV6
            if (selectedDomain.name != null && (mismatchV4 || mismatchV6)) {
                item(key = "notice_header") { SectionHeader("Notice") }
                item(key = "notice") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Your A or AAAA record doesn't match your current IP! This could be due to an active cellular connection or the IP address being assigned a server address.")
                            Button(
                                onClick = { updateDNSRecord() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color(ContextCompat.getColor(ctx, R.color.ipv64_orange_trans20)),
                                    contentColor = Color(ContextCompat.getColor(ctx, R.color.ipv64_orange)),
                                )
                            ) {
                                Text(if (mismatchV4) "Set A-Record to current IP?" else "Set AAAA-Record to current IP?")
                            }
                        }
                    }
                }
            }
            item(key = "general_header") { SectionHeader("General") }
            item(key = "general") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InfoRow("Kind", selectedDomain.kind.label)
                        InfoRow("Wildcard", selectedDomain.IsWildcard)
                        InfoRow("Updates", "${selectedDomain.updateCount ?: 0}")
                        InfoRow("Ignored on DynDNS updates", selectedDomain.ignore.label)
                        InfoRow("Fixed IPv6 prefix", selectedDomain.ipv6Prefix?.takeIf { it.isNotBlank() } ?: "Off", monospace = true)
                        if (selectedDomain.active == false) InfoRow("Status", "Inactive")
                        selectedDomain.createdAt?.let { InfoRow("Created", it.formatGermanTime()) }
                        Button(
                            enabled = !selectedDomain.updateToken.isNullOrEmpty(),
                            onClick = {
                                val token = selectedDomain.updateToken ?: return@Button
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                copyText(ctx, "Domain Update URL", domainUpdateUrl(token), sensitive = true)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(ContextCompat.getColor(ctx, R.color.ipv64_blue_trans15)),
                                contentColor = Color(ContextCompat.getColor(ctx, R.color.ipv64_blue)),
                            )
                        ) {
                            Text("Copy Domain Update URL")
                        }
                    }
                }
            }
            if (selectedDomain.name != null) {
                item(key = "records_header") { SectionHeader("DNS records (${selectedDomain.records.size})") }
                if (selectedDomain.records.isEmpty()) {
                    item(key = "records_empty") { EmptyState("No DNS records.") }
                }
                val sorted = selectedDomain.records.sortedWith(
                    compareBy<Record> { it.type?.ordinal ?: Int.MAX_VALUE }.thenBy { it.prefixLabel }
                )
                items(sorted, key = { r -> "record_${r.id ?: r.token ?: r.hashCode()}" }) { record ->
                    DomainRecordCard(
                        record = record,
                        onEdit = {
                            val id = record.id
                            if (!record.isLocked && id != null) {
                                navController.navigateWithArgs(
                                    Tab.domain_record_edit,
                                    NavKeys.EDIT_RECORD_ID to id.toString(),
                                    NavKeys.EDIT_RECORD_DOMAIN to domain
                                )
                            }
                        },
                        onDelete = { recordToDelete = record }
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        DomainCache.get(domain)?.let { selectedDomain = it }
        getDomain()
        getMyIPs()
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

    when (confirmAction) {
        DomainAction.ROTATE_TOKEN -> ConfirmDialog(
            title = "Rotate update token?",
            text = "A new update token is created and the old one stops working immediately. " +
                    "Routers and scripts using the old update URL stop updating until you enter the new URL.",
            confirmLabel = "Rotate",
            destructive = true,
            onConfirm = { confirmAction = null; rotateToken() },
            onDismiss = { confirmAction = null }
        )

        DomainAction.RESET -> ConfirmDialog(
            title = "Reset domain?",
            text = "All active DNS records of $domain are deleted and the zone is set up anew. This cannot be undone.",
            confirmLabel = "Reset",
            destructive = true,
            requireTyping = domain,
            onConfirm = { confirmAction = null; resetDomain() },
            onDismiss = { confirmAction = null }
        )

        DomainAction.DELETE -> ConfirmDialog(
            title = "Delete domain?",
            text = "Do you really want to delete $domain with all its DNS records?",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { confirmAction = null; deleteDomain() },
            onDismiss = { confirmAction = null }
        )

        else -> Unit
    }

    recordToDelete?.let { record ->
        ConfirmDialog(
            title = "Delete DNS record?",
            text = "Do you really want to delete the ${record.type?.value ?: ""} record \"${record.prefixLabel}\" " +
                    "(${record.content ?: "–"})?",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { recordToDelete = null; deleteRecord(record) },
            onDismiss = { recordToDelete = null }
        )
    }

    if (showDynDns && domain.isNotEmpty()) {
        DomainDynDnsDialog(domain = domain, req = req) { updated ->
            showDynDns = false
            if (updated) getDomain()
        }
    }

    rotatedUrl?.let { url ->
        RotatedTokenDialog(updateUrl = url) { rotatedUrl = null }
    }

    RequestStateHost(req)
}
