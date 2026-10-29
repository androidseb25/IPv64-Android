package de.rpicloud.ipv64net.main.views.network

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.PlanFeature
import de.rpicloud.ipv64net.helper.copyText
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.pollUntil
import de.rpicloud.ipv64net.helper.rememberNavArg
import de.rpicloud.ipv64net.main.views.common.ConfirmDialog
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.NotAvailableView
import de.rpicloud.ipv64net.main.views.common.RecordPickerDialog
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.IPv64ApiException
import de.sr.ipv64.model.Dns64Blocklist
import de.sr.ipv64.model.Dns64Client
import de.sr.ipv64.model.Dns64Rule
import de.sr.ipv64.model.Dns64Server
import de.sr.ipv64.model.Dns64Source
import de.sr.ipv64.model.Dns64SourceKind
import de.sr.ipv64.model.UpdateDns64ClientRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Ausstehende Bestätigung im Detail-Screen
private sealed interface DetailConfirm {
    data object Delete : DetailConfirm
    data class DeleteRule(val rule: Dns64Rule) : DetailConfirm
    data class DeleteSource(val source: Dns64Source) : DetailConfirm
}

/**
 * Details eines DNS64-Clients. Anfragen: ein GET beim Öffnen (Start mit Cache-Daten, falls vorhanden),
 * danach eine pro Aktion; Änderungen liefern den neuen Client zurück (Löschen von Regeln/Quellen: lokal entfernt).
 * Solange der Client oder eine Regel "applying" ist, wird alle 3 s (höchstens 60 s) nachgefragt.
 */
@Composable
fun Dns64DetailView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    val id = navController.rememberNavArg<String>(NavKeys.SELECTED_DNS64_TOKEN)

    var client by remember { mutableStateOf(id?.let { i -> Dns64Cache.peek(ctx)?.firstOrNull { it.id == i } }) }
    var refreshing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<DetailConfirm?>(null) }
    var showRename by remember { mutableStateOf(false) }
    var showRuleDialog by remember { mutableStateOf(false) }
    var showIpDialog by remember { mutableStateOf(false) }
    var showRecordPicker by remember { mutableStateOf(false) }
    var unavailableLists by remember { mutableStateOf(emptySet<Dns64Blocklist>()) }
    var pollJob by remember { mutableStateOf<Job?>(null) }

    // Neuen Zustand übernehmen (fehlende Details bleiben erhalten) und in den Listen-Cache schreiben
    // [detail] = vollständige GET-Antwort (Regeln/Quellen maßgeblich); sonst bleiben fehlende Listen erhalten
    fun store(c: Dns64Client, detail: Boolean = true) {
        val merged = mergeDns64(client, c, detail)
        client = merged
        dns64CacheUpsert(merged)
    }

    // Wartet (3 s Takt, höchstens 60 s), bis nichts mehr "applying" ist
    fun track() {
        val token = id ?: return
        if (pollJob?.isActive == true) return
        // Eigener Job (nicht über req.launch), damit req.isBusy nicht bis zu 60 s Aktionen sperrt; Fehler werden angezeigt
        pollJob = scope.launch {
            try {
                val finished = pollUntil(
                    intervalMs = 3_000,
                    maxMs = 60_000,
                    onEach = { c: Dns64Client -> store(c) },
                    fetch = { IPv64Client.get(ctx).dns64.getClient(token) },
                    done = { c -> !c.isApplying() }
                )
                if (finished == null) {
                    req.message("Still applying", "The changes are taking longer than expected. Pull down to refresh later.")
                }
            } catch (e: IPv64Exception) {
                req.error(e)
            }
        }
    }

    fun apply(c: Dns64Client, detail: Boolean = true) {
        store(c, detail)
        if (c.isApplying()) track()
    }

    fun load(pull: Boolean = false) {
        val token = id ?: return
        if (pull) refreshing = true
        req.launch(
            scope,
            spinner = if (pull || client != null) null else "Loading...",
            retry = { load() },
            planGate = true,
            onFinally = { refreshing = false }
        ) {
            apply(ipv64Retrying { IPv64Client.get(ctx).dns64.getClient(token) })
        }
    }

    // Aktion mit Spinner, die den neuen Client zurückliefert
    fun act(
        spinner: String,
        onError: (IPv64Exception) -> Boolean = { false },
        reload: Boolean = false,
        block: suspend (IPv64, String) -> Dns64Client
    ) {
        val token = id ?: return
        req.launch(scope, spinner = spinner, onError = onError) {
            val api = IPv64Client.get(ctx)
            val response = block(api, token)
            if (reload) {
                // Regeln/Quellen stehen nur in der Detailantwort: einmal nachladen
                apply(ipv64Retrying { api.dns64.getClient(token) })
            } else {
                apply(response, detail = false)
            }
        }
    }

    // Blocklisten adult/threats: 403 = nicht im Plan
    fun updateBlocklist(list: Dns64Blocklist, on: Boolean) {
        val current = client ?: return
        // Bekanntes Risiko: Bibliothek verwirft unbekannte Blocklisten-Werte, ein Umschalten würde eine künftige Liste abschalten
        val next = if (on) (current.blocklists + list).distinct() else current.blocklists - list
        act(
            spinner = "Saving...",
            onError = { e ->
                if (e is IPv64ApiException && e.status == 403) {
                    unavailableLists = unavailableLists + list
                    req.message("Not in your plan", "The \"${list.dns64Title()}\" blocklist is not included in your plan.")
                    true
                } else false
            }
        ) { api, token ->
            ipv64Retrying { api.dns64.updateClient(token, UpdateDns64ClientRequest(blocklists = next)) }
        }
    }

    // DoT-Host kopieren und die Private-DNS-Einstellungen öffnen
    fun usePrivateDns(server: Dns64Server) {
        val host = server.dot?.takeIf { it.isNotBlank() } ?: return
        copyText(ctx, "Private DNS host", host)
        if (!openPrivateDnsSettings(ctx)) {
            Toast.makeText(ctx, "Open Settings > Network & internet > Private DNS and paste the host.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) { load() }

    val c = client
    val busy = req.isBusy

    ScreenScaffold(
        title = c?.displayName() ?: Tabs.getLabel(Tab.dns64_details),
        navController = navController,
        mainPadding = mainPadding,
        actions = {
            IconButton(enabled = c != null && !busy, onClick = { showRename = true }) {
                Icon(painter = painterResource(R.drawable.edit_24px), contentDescription = "Rename")
            }
            IconButton(enabled = c != null && !busy, onClick = { confirm = DetailConfirm.Delete }) {
                Icon(painter = painterResource(R.drawable.delete_24px), contentDescription = "Delete")
            }
        }
    ) {
        val unavailable = req.notAvailable
        when {
            id == null -> EmptyState("Client not found.")
            unavailable != null -> NotAvailableView(PlanFeature.DNS64.label, unavailable)
            c == null -> Unit
            else -> RefreshableList(refreshing = refreshing, onRefresh = { load(pull = true) }) {
                dns64HeaderItem(c)
                dns64ResolverItems(c, onUsePrivateDns = ::usePrivateDns)
                dns64FilteringItems(
                    client = c,
                    unavailable = unavailableLists,
                    enabled = !busy,
                    onBlocking = { on ->
                        act("Saving...") { api, token ->
                            ipv64Retrying { api.dns64.updateClient(token, UpdateDns64ClientRequest(blockingEnabled = on)) }
                        }
                    },
                    onBlocklist = ::updateBlocklist
                )
                dns64RuleItems(
                    client = c,
                    enabled = !busy,
                    onAdd = { showRuleDialog = true },
                    onDelete = { confirm = DetailConfirm.DeleteRule(it) }
                )
                dns64SourceItems(
                    client = c,
                    enabled = !busy,
                    onAddIp = { showIpDialog = true },
                    onAddRecord = { showRecordPicker = true },
                    onDelete = { confirm = DetailConfirm.DeleteSource(it) }
                )
            }
        }
    }

    // ---- Bestätigungen ----
    when (val cf = confirm) {
        DetailConfirm.Delete -> {
            val typed = c?.name?.takeIf { it.isNotBlank() && it.length <= 40 } ?: "delete"
            ConfirmDialog(
                title = "Delete client?",
                text = "\"${c?.displayName().orEmpty()}\" and its resolver addresses, rules and sources will be deleted. " +
                        "Devices that use it lose their DNS resolver. This cannot be undone.",
                confirmLabel = "Delete",
                destructive = true,
                requireTyping = typed,
                onConfirm = {
                    confirm = null
                    val token = id
                    if (token != null) {
                        req.launch(scope, spinner = "Deleting...") {
                            ipv64Retrying { IPv64Client.get(ctx).dns64.deleteClient(token) }
                            dns64CacheRemove(token)
                            AccountCache.invalidate()
                            navController.popBackStack()
                        }
                    }
                },
                onDismiss = { confirm = null }
            )
        }

        is DetailConfirm.DeleteRule -> ConfirmDialog(
            title = "Delete rule?",
            text = "The rule for ${cf.rule.domain.orEmpty()} will be removed.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirm = null
                val token = id
                val ruleId = cf.rule.id
                if (token != null && ruleId != null) {
                    req.launch(scope, spinner = "Deleting...") {
                        ipv64Retrying { IPv64Client.get(ctx).dns64.deleteRule(token, ruleId) }
                        client?.let { store(it.copy(rules = it.rules.filterNot { r -> r.id == ruleId })) }
                    }
                }
            },
            onDismiss = { confirm = null }
        )

        is DetailConfirm.DeleteSource -> ConfirmDialog(
            title = "Delete source address?",
            text = "${cf.source.displayText()} will no longer be assigned to this client on the shared resolver IPs.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirm = null
                val token = id
                val sourceId = cf.source.id
                if (token != null && sourceId != null) {
                    req.launch(scope, spinner = "Deleting...") {
                        ipv64Retrying { IPv64Client.get(ctx).dns64.deleteSource(token, sourceId) }
                        client?.let { store(it.copy(sources = it.sources.filterNot { s -> s.id == sourceId })) }
                    }
                }
            },
            onDismiss = { confirm = null }
        )

        null -> Unit
    }

    // ---- Umbenennen ----
    if (showRename) {
        Dns64NameDialog(
            title = "Rename client",
            initial = c?.name.orEmpty(),
            confirmLabel = "Save",
            required = true,
            onConfirm = { name ->
                showRename = false
                act("Saving...") { api, token ->
                    ipv64Retrying { api.dns64.updateClient(token, UpdateDns64ClientRequest(name = name)) }
                }
            },
            onDismiss = { showRename = false }
        )
    }

    // ---- Regel hinzufügen ----
    if (showRuleDialog) {
        Dns64RuleDialog(
            existingDomains = c?.rules?.mapNotNull { it.domain?.lowercase() }?.toSet().orEmpty(),
            onConfirm = { domain, action ->
                showRuleDialog = false
                act("Adding rule...", reload = true) { api, token -> ipv64Retrying { api.dns64.addRule(token, domain, action) } }
            },
            onDismiss = { showRuleDialog = false }
        )
    }

    // ---- Quelle: feste IP ----
    if (showIpDialog) {
        Dns64IpDialog(
            existingIps = c?.sources?.ofKind(Dns64SourceKind.IP)?.mapNotNull { it.address?.trim() }?.toSet().orEmpty(),
            onConfirm = { ip ->
                showIpDialog = false
                act("Adding source...", reload = true) { api, token -> ipv64Retrying { api.dns64.addSource(token, ip = ip) } }
            },
            onDismiss = { showIpDialog = false }
        )
    }

    // ---- Quelle: DynDNS-Record ----
    if (showRecordPicker) {
        val used = c?.sources?.ofKind(Dns64SourceKind.RECORD)?.mapNotNull { it.id }?.toSet().orEmpty()
        RecordPickerDialog(
            title = "Select record",
            filter = { r -> r.token == null || r.token !in used },
            onPick = { _, record ->
                showRecordPicker = false
                val ref = record.id?.toString() ?: record.token
                if (ref != null) {
                    act("Adding source...", reload = true) { api, token -> ipv64Retrying { api.dns64.addSource(token, record = ref) } }
                }
            },
            onDismiss = { showRecordPicker = false }
        )
    }

    RequestStateHost(req)
}
