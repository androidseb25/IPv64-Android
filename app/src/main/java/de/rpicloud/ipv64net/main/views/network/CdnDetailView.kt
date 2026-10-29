package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.AccountCache
import de.rpicloud.ipv64net.helper.DomainCache
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.PlanFeature
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.helper.pollUntil
import de.rpicloud.ipv64net.helper.rememberNavArg
import de.rpicloud.ipv64net.main.views.common.ConfirmDialog
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.NotAvailableView
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64
import de.sr.ipv64.IPv64ApiException
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.AccessListEntry
import de.sr.ipv64.model.CdnDomain
import de.sr.ipv64.model.CdnDomainDetail
import de.sr.ipv64.model.CdnMapping
import de.sr.ipv64.model.CdnPortmapperState
import de.sr.ipv64.model.CdnProxyState
import de.sr.ipv64.model.CdnRecordMode
import de.sr.ipv64.model.CreateCdnMappingRequest
import de.sr.ipv64.model.Record
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Ausstehende Bestätigung im Detail-Screen
private sealed interface CdnConfirm {
    data object Move : CdnConfirm
    data object ResetTraffic : CdnConfirm
    data object Delete : CdnConfirm
    data class DisableRecord(val record: Record) : CdnConfirm
    data class DeleteMapping(val mapping: CdnMapping) : CdnConfirm
}

// Ergebnis einer Abfrage beim Polling; detail == null bedeutet: Domain existiert nicht mehr (404)
private class CdnPoll(val detail: CdnDomainDetail?)

/**
 * Details einer CDN-Domain. Anfragen: ein `cdn.get` beim Öffnen (Details inkl. Mappings; entfällt, wenn der
 * Eintrag gerade erst gespeichert wurde), DNS-Records und Zugriffsliste erst beim Aufklappen, danach eine Anfrage
 * pro Aktion. Nach jeder 202-Aktion fragt das Polling alle 5 s (höchstens 60 s) `cdn.get`, solange der CDN-Bot
 * Aufgaben offen hat oder etwas abgeschaltet wird; danach werden geladene Records einmal neu gelesen.
 */
@Composable
fun CdnDetailView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    val token = navController.rememberNavArg<String>(NavKeys.SELECTED_CDN_TOKEN)

    // Beobachtbar: Änderungen aus dem Einstellungs-Screen erscheinen sofort
    val detail: CdnDomainDetail? = token?.let { CdnDetailStore.get(it) }
    // Platzhalter bis die Details da sind (Listendaten ohne Mappings)
    val listItem: CdnDomain? = remember(token) { token?.let { t -> CdnCache.peek(ctx)?.firstOrNull { it.id == t } } }

    var refreshing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<CdnConfirm?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var pollJob by remember { mutableStateOf<Job?>(null) }

    var records by remember { mutableStateOf<List<Record>?>(null) }
    var recordsExpanded by remember { mutableStateOf(false) }
    var accessList by remember { mutableStateOf<List<AccessListEntry>?>(null) }
    var accessExpanded by remember { mutableStateOf(false) }
    var enableTarget by remember { mutableStateOf<Record?>(null) }
    var showMappingDialog by remember { mutableStateOf(false) }
    var showAddAddress by remember { mutableStateOf(false) }

    // Neues Objekt übernehmen und in den Listen-Cache schreiben
    fun store(d: CdnDomainDetail) {
        CdnDetailStore.put(d)
        CdnCache.upsertCdn(d.toCdnDomain())
    }

    // Antwort einer Aktion (ohne Mappings) übernehmen, bekannte Mappings bleiben erhalten
    fun storeUpdate(u: CdnDomain) {
        val known = token?.let { CdnDetailStore.get(it) }?.mappings.orEmpty()
        store(u.toDetail(known))
    }

    fun domainChanged() {
        (detail?.domain ?: listItem?.domain)?.let { DomainCache.invalidate(it) }
    }

    // Fragt cdn.get alle 5 s (höchstens 60 s), bis nichts mehr aussteht oder die Domain entfernt wurde
    fun track() {
        val t = token ?: return
        if (pollJob?.isActive == true) return
        // Eigener Job statt req.launch: das Hintergrund-Polling soll req.isBusy (und damit die Bedienung) nicht sperren
        pollJob = scope.launch {
          try {
            val result = pollUntil(
                intervalMs = 5_000,
                maxMs = 60_000,
                onEach = { p: CdnPoll -> p.detail?.let { store(it) } },
                fetch = {
                    try {
                        CdnPoll(IPv64Client.get(ctx).cdn.get(t))
                    } catch (e: IPv64ApiException) {
                        if (e.status == 404) CdnPoll(null) else throw e
                    }
                },
                done = { p -> p.detail == null || !p.detail.isBusy() }
            )
            when {
                result == null ->
                    req.message("Still working", "The CDN needs longer than expected. Pull down to refresh later.")

                result.detail == null -> {
                    // Domain ist aus dem CDN entfernt
                    CdnDetailStore.remove(t)
                    CdnCache.removeCdn(t)
                    AccountCache.invalidate()
                    domainChanged()
                    navController.popBackStack()
                }

                else -> {
                    domainChanged()
                    // Geladene Records haben nach den Änderungen einen neuen Zustand
                    if (records != null) records = ipv64Retrying { IPv64Client.get(ctx).cdn.records(t) }
                }
            }
          } catch (e: IPv64Exception) {
            // Fehler des Pollings werden weiterhin angezeigt
            req.error(e)
          }
        }
    }

    fun load(force: Boolean = false, pull: Boolean = false) {
        val t = token ?: return
        if (!force && CdnDetailStore.isFresh(t, 10_000)) return
        if (pull) refreshing = true
        req.notAvailable = null
        req.launch(
            scope,
            spinner = if (pull || detail != null || listItem != null) null else "Loading...",
            retry = { load(true) },
            planGate = true,
            onFinally = { refreshing = false }
        ) {
            store(ipv64Retrying { IPv64Client.get(ctx).cdn.get(t) })
        }
    }

    // Aktion (202 oder 200), die die neue CDN-Domain zurückliefert; danach bei offenen Aufgaben beobachten
    fun act(spinner: String, always: Boolean = false, block: suspend (IPv64, String) -> CdnDomain) {
        val t = token ?: return
        req.launch(scope, spinner = spinner) {
            val updated = block(IPv64Client.get(ctx), t)
            storeUpdate(updated)
            domainChanged()
            if (always || updated.isBusy()) track()
        }
    }

    fun loadRecords() {
        val t = token ?: return
        req.launch(scope, spinner = "Loading records...", retry = { loadRecords() }) {
            records = ipv64Retrying { IPv64Client.get(ctx).cdn.records(t) }
        }
    }

    fun loadAccessList() {
        val t = token ?: return
        req.launch(scope, spinner = "Loading access list...", retry = { loadAccessList() }) {
            accessList = ipv64Retrying { IPv64Client.get(ctx).cdn.accessList(t) }
        }
    }

    fun upsertRecord(updated: Record) {
        records = records?.map { if (it.id == updated.id) updated else it }
    }

    LaunchedEffect(Unit) { load() }
    // Offene Aufgaben (z. B. nach dem Öffnen oder nach den Einstellungen) automatisch beobachten
    val busy = (detail?.isBusy() ?: listItem?.isBusy()) == true
    LaunchedEffect(busy) { if (busy) track() }

    val shown: CdnDomain? = detail?.toCdnDomain() ?: listItem
    val enabled = !req.isBusy && detail != null
    // Schalter und Menüaktionen, die mit einer laufenden Aufgabe des CDN-Bots kollidieren, sind währenddessen gesperrt
    val taskRunning = detail?.isBusy() == true
    val configEnabled = enabled && !taskRunning

    ScreenScaffold(
        title = shown?.displayName() ?: Tabs.getLabel(Tab.cdn_details),
        navController = navController,
        mainPadding = mainPadding,
        actions = {
            IconButton(
                enabled = enabled,
                onClick = { navController.navigateWithArgs(Tab.cdn_settings, NavKeys.SELECTED_CDN_TOKEN to token) }
            ) { Icon(painter = painterResource(R.drawable.edit_24px), contentDescription = "Settings") }
            Box {
                IconButton(enabled = enabled, onClick = { menuOpen = true }) {
                    Icon(painter = painterResource(R.drawable.more_vert_24px), contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Redeploy configuration") }, enabled = configEnabled, onClick = {
                        menuOpen = false
                        act("Redeploying...", always = true) { api, t -> ipv64Retrying { api.cdn.redeploy(t) } }
                    })
                    DropdownMenuItem(text = { Text("Move to another server") }, enabled = configEnabled, onClick = {
                        menuOpen = false
                        confirm = CdnConfirm.Move
                    })
                    DropdownMenuItem(text = { Text("Reset traffic counters") }, onClick = {
                        menuOpen = false
                        confirm = CdnConfirm.ResetTraffic
                    })
                    DropdownMenuItem(text = { Text("Remove from CDN") }, enabled = configEnabled, onClick = {
                        menuOpen = false
                        confirm = CdnConfirm.Delete
                    })
                }
            }
        }
    ) {
        val unavailable = req.notAvailable
        when {
            token == null -> EmptyState("CDN domain not found.")
            unavailable != null -> NotAvailableView(PlanFeature.CDN.label, unavailable)
            shown == null -> Box(modifier = Modifier)
            else -> RefreshableList(refreshing = refreshing, onRefresh = { load(true, true) }) {
                item(key = "header") { CdnHeaderCard(shown) }

                if (detail != null) {
                    item(key = "proxy") {
                        CdnProxyCard(
                            detail = detail,
                            enabled = enabled,
                            lockTasks = taskRunning,
                            onToggleProxy = {
                                if (detail.proxy?.state == CdnProxyState.ON) {
                                    act("Switching proxy off...", always = true) { api, t ->
                                        ipv64Retrying { api.cdn.disableProxy(t) }
                                    }
                                } else {
                                    act("Switching proxy on...", always = true) { api, t ->
                                        ipv64Retrying { api.cdn.enableProxy(t) }
                                    }
                                }
                            },
                            onRenewCertificate = {
                                act("Renewing certificate...", always = true) { api, t ->
                                    ipv64Retrying { api.cdn.renewCertificate(t) }
                                }
                            },
                            onSettings = {
                                navController.navigateWithArgs(Tab.cdn_settings, NavKeys.SELECTED_CDN_TOKEN to token)
                            }
                        )
                    }

                    item(key = "portmapper") {
                        CdnPortmapperCard(
                            detail = detail,
                            enabled = enabled,
                            lockTasks = taskRunning,
                            onTogglePortmapper = {
                                if (detail.portmapper?.state == CdnPortmapperState.ON) {
                                    act("Switching port mapper off...", always = true) { api, t ->
                                        ipv64Retrying { api.cdn.disablePortmapper(t) }
                                    }
                                } else {
                                    act("Switching port mapper on...", always = true) { api, t ->
                                        ipv64Retrying { api.cdn.enablePortmapper(t) }
                                    }
                                }
                            },
                            onAddMapping = {
                                // Ziel-Records brauchen die Record-Liste: einmal laden, dann Dialog zeigen
                                if (records != null) showMappingDialog = true
                                else {
                                    val t = token
                                    if (t != null) req.launch(scope, spinner = "Loading records...") {
                                        records = ipv64Retrying { IPv64Client.get(ctx).cdn.records(t) }
                                        showMappingDialog = true
                                    }
                                }
                            },
                            onDeleteMapping = { confirm = CdnConfirm.DeleteMapping(it) }
                        )
                    }

                    cdnRecordsSection(
                        records = records,
                        expanded = recordsExpanded,
                        enabled = !req.isBusy,
                        onToggle = {
                            recordsExpanded = !recordsExpanded
                            if (recordsExpanded && records == null) loadRecords()
                        },
                        onRefresh = { loadRecords() },
                        onEnable = { enableTarget = it },
                        onDisable = { confirm = CdnConfirm.DisableRecord(it) }
                    )

                    val acl = detail.proxy?.accessList
                    cdnAccessListSection(
                        entries = accessList,
                        settingsText = if (acl?.enabled == true) "Active. ${acl.mode.accessModeLabel()}."
                        else "Currently switched off. Turn it on in the settings.",
                        expanded = accessExpanded,
                        enabled = !req.isBusy,
                        onToggle = {
                            accessExpanded = !accessExpanded
                            if (accessExpanded && accessList == null) loadAccessList()
                        },
                        onRefresh = { loadAccessList() },
                        onAdd = { showAddAddress = true },
                        onRemove = { entry ->
                            val id = entry.id ?: return@cdnAccessListSection
                            val t = token
                            req.launch(scope, spinner = "Removing...") {
                                ipv64Retrying { IPv64Client.get(ctx).cdn.removeAccessListEntry(t, id.toString()) }
                                accessList = accessList?.filterNot { it.id == id }
                            }
                        }
                    )
                }
            }
        }
    }

    // ---- Bestätigungen ----
    when (val c = confirm) {
        CdnConfirm.Move -> ConfirmDialog(
            title = "Move to another server?",
            text = "The domain is moved to another CDN server. The IP addresses of the CDN change and DNS " +
                    "caches may keep pointing to the old server for a while.",
            confirmLabel = "Move",
            destructive = true,
            onConfirm = {
                confirm = null
                act("Moving...", always = true) { api, t -> ipv64Retrying { api.cdn.move(t) } }
            },
            onDismiss = { confirm = null }
        )

        CdnConfirm.ResetTraffic -> ConfirmDialog(
            title = "Reset traffic counters?",
            text = "The traffic counters of this domain are set back to zero. This cannot be undone.",
            confirmLabel = "Reset",
            destructive = true,
            onConfirm = {
                confirm = null
                act("Resetting...") { api, t -> ipv64Retrying { api.cdn.resetTraffic(t) } }
            },
            onDismiss = { confirm = null }
        )

        CdnConfirm.Delete -> ConfirmDialog(
            title = "Remove from CDN?",
            text = "\"${shown?.displayName().orEmpty()}\" is removed from the CDN with its port mappings and " +
                    "access list. The CDN must be disabled for all records first, otherwise the server rejects " +
                    "this. This cannot be undone.",
            confirmLabel = "Remove",
            destructive = true,
            requireTyping = shown?.domain,
            onConfirm = {
                confirm = null
                act("Removing...", always = true) { api, t -> ipv64Retrying { api.cdn.delete(t) } }
            },
            onDismiss = { confirm = null }
        )

        is CdnConfirm.DisableRecord -> ConfirmDialog(
            title = "Deliver record directly?",
            text = "${c.record.cdnTitle()} is no longer routed through the CDN. Visitors reach your server " +
                    "directly again once DNS caches expire, and your server's IP address becomes visible.",
            confirmLabel = "Disable CDN",
            destructive = true,
            onConfirm = {
                confirm = null
                val ref = c.record.cdnRef()
                val t = token
                if (ref != null && t != null) {
                    req.launch(scope, spinner = "Updating record...") {
                        upsertRecord(ipv64Retrying { IPv64Client.get(ctx).cdn.disableRecord(t, ref) })
                        domainChanged()
                        track()
                    }
                }
            },
            onDismiss = { confirm = null }
        )

        is CdnConfirm.DeleteMapping -> ConfirmDialog(
            title = "Delete port mapping?",
            text = "Public port ${c.mapping.listenPort ?: "–"} no longer forwards to port ${c.mapping.targetPort ?: "–"}.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirm = null
                val id = c.mapping.id
                val t = token
                if (id != null && t != null) {
                    req.launch(scope, spinner = "Deleting...") {
                        ipv64Retrying { IPv64Client.get(ctx).cdn.deleteMapping(t, id) }
                        CdnDetailStore.get(t)?.let { store(it.copy(mappings = it.mappings.filterNot { m -> m.id == id })) }
                    }
                }
            },
            onDismiss = { confirm = null }
        )

        null -> Unit
    }

    // ---- Record über das CDN leiten ----
    enableTarget?.let { record ->
        CdnEnableRecordDialog(
            record = record,
            onConfirm = { mode: CdnRecordMode ->
                enableTarget = null
                val ref = record.cdnRef()
                val t = token
                if (ref != null && t != null) {
                    req.launch(scope, spinner = "Updating record...") {
                        upsertRecord(ipv64Retrying { IPv64Client.get(ctx).cdn.enableRecord(t, ref, mode) })
                        domainChanged()
                        track()
                    }
                }
            },
            onDismiss = { enableTarget = null }
        )
    }

    // ---- Port-Mapping anlegen ----
    if (showMappingDialog) {
        CdnMappingDialog(
            records = records.orEmpty().filter { it.canBeMappingTarget() },
            onConfirm = { request: CreateCdnMappingRequest ->
                showMappingDialog = false
                val t = token
                if (t != null) {
                    req.launch(scope, spinner = "Adding mapping...") {
                        val created = ipv64Retrying { IPv64Client.get(ctx).cdn.createMapping(t, request) }
                        CdnDetailStore.get(t)?.let { store(it.copy(mappings = it.mappings + created)) }
                    }
                }
            },
            onDismiss = { showMappingDialog = false }
        )
    }

    // ---- Eintrag der Zugriffsliste ----
    if (showAddAddress) {
        CdnAddAddressDialog(
            onConfirm = { address ->
                showAddAddress = false
                val t = token
                if (t != null) {
                    req.launch(scope, spinner = "Adding...") {
                        val entry = ipv64Retrying { IPv64Client.get(ctx).cdn.addAccessListEntry(t, address) }
                        accessList = accessList.orEmpty() + entry
                    }
                }
            },
            onDismiss = { showAddAddress = false }
        )
    }

    RequestStateHost(req)
}
