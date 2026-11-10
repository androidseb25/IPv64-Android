package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import de.rpicloud.ipv64net.main.views.common.ActionTile
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.main.views.common.ActionSheet
import de.rpicloud.ipv64net.helper.FeatureAvailability
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.PlanFeature
import de.rpicloud.ipv64net.helper.availability
import de.rpicloud.ipv64net.helper.canCreate
import de.rpicloud.ipv64net.helper.loadMe
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.helper.rememberNavArg
import de.rpicloud.ipv64net.main.views.FormDropdown
import de.rpicloud.ipv64net.main.views.FormSwitchRow
import de.rpicloud.ipv64net.main.views.FormTextField
import de.rpicloud.ipv64net.main.views.common.ConfirmDialog
import de.rpicloud.ipv64net.main.views.FormAccordion
import de.rpicloud.ipv64net.main.views.common.DomainPickerDialog
import de.rpicloud.ipv64net.main.views.common.FormSection
import de.rpicloud.ipv64net.main.views.common.GroupDivider
import de.rpicloud.ipv64net.main.views.common.GroupText
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestState
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.node64.Node64
import de.sr.ipv64.model.MeUsage
import de.sr.ipv64.node64.model.CDNOnlineEdge
import de.sr.ipv64.node64.model.CDNOnlineName
import de.sr.ipv64.node64.model.CDNOnlineStatus
import de.sr.ipv64.node64.model.EnableHostOnlineRequest
import de.sr.ipv64.node64.model.HostCertificateState
import de.sr.ipv64.node64.model.HostReachState
import de.sr.ipv64.node64.model.MinTlsVersion
import de.sr.ipv64.node64.model.OnlineResultState
import de.sr.ipv64.node64.model.ProxyHost
import de.sr.ipv64.node64.model.ProxyHostInput
import de.sr.ipv64.node64.model.ProxyHostKind
import de.sr.ipv64.node64.model.ProxyScheme
import de.sr.ipv64.node64.model.RedirectScheme
import de.sr.ipv64.IPv64Exception as ApiException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

// Hosts einer Node oder deren Fehlertext
private class NodeHosts(
    val id: String, val name: String, val hosts: List<ProxyHost>, val error: String?,
    // Hosts, die über das CDN64 laufen (Host-IDs), und dessen Server; null = Status nicht lesbar
    val online: CDNOnlineStatus? = null
) {
    val cdn: Map<Long, CDNOnlineName> = online?.names.orEmpty().mapNotNull { n -> n.hostId?.let { it to n } }.toMap()
    val edges: List<CDNOnlineEdge> get() = online?.edges.orEmpty()
}

internal fun nodeOf(ctx: android.content.Context, id: String?): Node64? =
    id?.let { LocalNodeStore.get(ctx, it) }?.let { (url, key) -> localNodeClient(url, key) }

// Fehler der lokalen Node als Dialog (nicht toError, das kennt nur Cloud-Fehler)
internal fun RequestState.nodeError(e: IPv64Exception): Boolean {
    val (title, text) = localNodeError(e)
    message(title, text, R.drawable.error_24px)
    return true
}

/**
 * Proxy64-Tab (ersetzt CDN64, solange eine verbundene Node Proxy64 aktiv hat): Zugang zum Cloud-CDN64 und die
 * Proxy-Hosts jeder verbundenen Node. Je Node eine Anfrage (parallel), ein Fehler betrifft nur diese Node.
 */
@Composable
fun ProxyView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()

    var groups by remember { mutableStateOf<List<NodeHosts>?>(null) }
    var usage by remember { mutableStateOf<MeUsage?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showNodePicker by remember { mutableStateOf(false) }
    var showEnableFlow by remember { mutableStateOf(false) }

    fun load(pull: Boolean = false) {
        if (pull) refreshing = true
        req.launch(scope, spinner = if (pull || groups != null) null else "Loading...", onFinally = { refreshing = false }) {
            // Verbrauch ist nicht kritisch: ohne ihn fehlt nur die Statistik-Karte
            try {
                usage = loadMe(ctx, pull).usage
            } catch (e: IPv64Exception) {
            }
            groups = coroutineScope {
                ConnectedNodes.proxyIds.map { id ->
                    async {
                        val name = LocalNodeStore.name(ctx, id) ?: id
                        try {
                            val node = nodeOf(ctx, id) ?: return@async NodeHosts(id, name, emptyList(), "No access saved for this node.")
                            val hosts = node.proxy.hosts.list()
                            // CDN64-Status ist optional (Modul ohne Tunnel/Konto liefert Fehler)
                            val online = try { node.proxy.online() } catch (e: IPv64Exception) { null }
                            NodeHosts(id, name, hosts, null, online)
                        } catch (e: IPv64Exception) {
                            NodeHosts(id, name, emptyList(), localNodeError(e).let { "${it.first}: ${it.second}" })
                        }
                    }
                }.awaitAll()
            }
        }
    }

    // Auch beim Zurückkehren aus dem Bearbeiten-Screen
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { load() }
    // Proxy-Nodes ändern sich nach dem Öffnen (Modulstatus kommt später an): neu laden; Erstladen über ON_RESUME
    LaunchedEffect(ConnectedNodes.proxyIds) { if (groups != null) load() }

    fun newHost(nodeId: String) =
        navController.navigateWithArgs(Tab.proxy_host_edit, NavKeys.SELECTED_NODE_ID to nodeId, NavKeys.PROXY_HOST_ID to null)

    // Plan und Liste prüfen wie in CdnView, dann Domain wählen
    fun startAddCdn() {
        // Mit verbundener Node läuft CDN64 über deren Proxy-Hosts (Plan-/Limitprüfung übernimmt der Server)
        if (groups.orEmpty().any { it.error == null }) { showEnableFlow = true; return }
        req.launch(scope, spinner = "Loading...") {
            val availability = try {
                loadMe(ctx).availability(PlanFeature.CDN)
            } catch (e: ApiException) {
                FeatureAvailability.Unknown
            }
            when {
                availability is FeatureAvailability.NotInPlan -> req.message("Not available", "Your plan does not include the CDN.")
                !availability.canCreate -> req.cdnLimitMessage(availability)
                else -> {
                    CdnCache.get(ctx, false) { it.cdn.list() }
                    showPicker = true
                }
            }
        }
    }

    ScreenScaffold(
        title = Tabs.getLabel(Tab.proxy64),
        navController = navController,
        mainPadding = mainPadding,
        fab = {
            FloatingActionButton(
                onClick = {
                    val ids = ConnectedNodes.proxyIds
                    if (ids.size == 1) newHost(ids[0]) else if (ids.size > 1) showNodePicker = true
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(painter = painterResource(R.drawable.add_24px), contentDescription = "New proxy host")
            }
        }
    ) {
        RefreshableList(refreshing = refreshing, onRefresh = { load(true) }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "cdn_stats") { CdnStatsCard(usage, groups.orEmpty().firstNotNullOfOrNull { it.online?.quota }) }
            item(key = "cdn") {
                ListGroup("CDN64") {
                    ListRow(
                        title = "CDN64",
                        subtitle = CdnCache.peek(ctx)?.let { "${it.size} domains" } ?: "Cloud CDN from ipv64.net",
                        onClick = { navController.navigate(Tabs.getRoute(Tab.cdn)) }
                    )
                    GroupDivider()
                    ListRow(title = "Add domain to CDN64", onClick = { startAddCdn() })
                }
            }
            groups.orEmpty().forEach { g ->
                item(key = "node_${g.id}") {
                    ListGroup(g.name) {
                        if (g.error != null) {
                            ListRow(title = g.error, subtitleMaxLines = 3)
                            GroupText("Check the connection under More → Nodes.")
                        }
                        else if (g.hosts.isEmpty()) ListRow(title = "No hosts.")
                        g.hosts.forEachIndexed { i, h ->
                            if (i > 0) GroupDivider()
                            ProxyHostRow(
                                h, cdn = h.id?.let { g.cdn[it] }, edges = g.edges,
                                // Ohne ID würde der Editor im Anlege-Modus öffnen
                                onClick = h.id?.let { hid ->
                                    {
                                        navController.navigateWithArgs(
                                            Tab.proxy_host_edit, NavKeys.SELECTED_NODE_ID to g.id, NavKeys.PROXY_HOST_ID to hid
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        DomainPickerDialog(
            exclude = CdnCache.peek(ctx).orEmpty().mapNotNull { it.domain }.toSet(),
            title = "Add domain to CDN",
            onPick = { name ->
                showPicker = false
                req.addCdnDomain(scope, ctx, navController, name)
            },
            onDismiss = { showPicker = false }
        )
    }
    if (showEnableFlow) {
        val choices = groups.orEmpty().filter { it.error == null }.flatMap { g ->
            g.hosts.filter { it.id != null && it.id !in g.cdn }
                .map { CdnHostChoice(g.id, g.name, it.id!!, it.names.joinToString().ifBlank { "#${it.id}" }) }
        }
        CdnEnableFlow(
            choices,
            onEnable = { c, mode, hc ->
                val node = nodeOf(ctx, c.nodeId)
                if (node != null) req.launch(scope, spinner = "Enabling CDN64...", onError = { req.nodeError(it) }) {
                    node.enableCdn64(c.hostId, mode, hc)?.let { req.message("CDN64", it, R.drawable.error_24px) }
                    load()
                }
            },
            onDismiss = { showEnableFlow = false }
        )
    }
    if (showNodePicker) {
        ActionSheet(title = "Select node", onDismiss = { showNodePicker = false }) {
            ConnectedNodes.proxyIds.forEach { id ->
                TextButton(onClick = { showNodePicker = false; newHost(id) }, modifier = Modifier.fillMaxWidth()) { Text(LocalNodeStore.name(ctx, id) ?: id) }
            }
        }
    }
    RequestStateHost(req)
}

// Status wie im Node-Modul (NodeModuleView)
private fun ProxyHost.statusChip(): Pair<String, Int> = when {
    enabled == false -> "Disabled" to R.color.ipv64_gray
    reach?.state == HostReachState.UP -> "Up" to R.color.ipv64_green
    reach?.state == HostReachState.SLOW -> "Slow" to R.color.ipv64_orange
    reach?.state == HostReachState.DOWN -> "Down" to R.color.ipv64_red
    else -> "Enabled" to R.color.ipv64_gray
}

private fun ProxyHost.subtitle(): String {
    val target = targetHost?.let { t -> (scheme?.value?.let { "$it://" }.orEmpty()) + (targetPort?.let { p -> "$t:$p" } ?: t) }
    return listOfNotNull(kind?.value, redirectTarget ?: target).filter { it.isNotBlank() }.joinToString(" → ")
}

// SSL-Chip: Text + Farbe (grün, orange < 14 Tage, rot abgelaufen/fehlt)
private fun ProxyHost.sslChip(): Pair<String, Int>? {
    val c = certificate ?: return null
    return when (c.state) {
        HostCertificateState.OK, HostCertificateState.SOON ->
            "SSL ${c.daysLeft?.let { "$it days" } ?: "ok"}" to
                if (c.state == HostCertificateState.SOON || (c.daysLeft ?: 99) < 14) R.color.ipv64_orange else R.color.ipv64_green
        HostCertificateState.EXPIRED -> "SSL expired" to R.color.ipv64_red
        HostCertificateState.MISSING -> "SSL missing" to R.color.ipv64_red
        HostCertificateState.HTTP -> "HTTP" to R.color.ipv64_gray
        null -> null
    }
}

/** Kompakte Host-Zeile: Domains, Ziel, Chips (SSL, Optionen, CDN64) und Zähler. Tippen öffnet den Editor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProxyHostRow(h: ProxyHost, cdn: CDNOnlineName?, edges: List<CDNOnlineEdge>, onClick: (() -> Unit)?) {
    val gray = colorResource(R.color.ipv64_gray)
    val (txt, col) = h.statusChip()
    val reach = txt + (h.reach?.ms?.let { " · $it ms" } ?: "")
    val options = listOfNotNull(
        "WS".takeIf { h.websockets == true }, "Exploits".takeIf { h.blockExploits == true },
        "Cache".takeIf { h.cacheAssets == true }, "HTTPS forced".takeIf { h.forceSsl == true },
        "H2".takeIf { h.http2 == true }, "H3".takeIf { h.http3 == true }, "HSTS".takeIf { h.hsts == true },
        "Not public".takeIf { (h.accessListId ?: 0) > 0 }
    )
    val up = edges.count { it.connected == true }
    Column(
        Modifier.fillMaxWidth().clickable(enabled = onClick != null) { onClick?.invoke() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(h.names.joinToString().ifBlank { "–" }, style = MaterialTheme.typography.titleMedium)
        h.subtitle().takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StatusChip(reach, colorResource(col))
            h.sslChip()?.let { (t, c) -> StatusChip(t, colorResource(c)) }
            if (cdn != null) StatusChip(
                "CDN64 · ${cdnModeShort(cdn.mode)}" + if (edges.isNotEmpty()) " · $up/${edges.size} servers" else "",
                colorResource(if (edges.isNotEmpty() && up == 0) R.color.ipv64_red else R.color.ipv64_green)
            )
            options.forEach { StatusChip(it, gray) }
        }
        h.stats?.takeIf { it.requests != null }?.let {
            Text(
                "${it.requests} requests" + (it.errors?.takeIf { e -> e > 0 }?.let { e -> " · $e errors" } ?: "") +
                    (it.blocked?.takeIf { b -> b > 0 }?.let { b -> " · $b blocked" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        (h.routeError ?: h.certificateError)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

/**
 * Proxy-Host anlegen oder bearbeiten (Node-ID und Host-ID aus den Nav-Args; ohne Host-ID = neuer Host).
 * Bearbeitet werden Namen, Ziel/Weiterleitung, SSL- und Zugriffsoptionen; alles andere (Locations, Advanced-Header …)
 * bleibt auf der Node unverändert (PUT lässt fehlende Felder stehen).
 */
@Composable
fun ProxyHostEditView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    val nodeId = navController.rememberNavArg<String>(NavKeys.SELECTED_NODE_ID)
    val hostId = navController.rememberNavArg<Long>(NavKeys.PROXY_HOST_ID)
    val create = hostId == null

    var loaded by remember { mutableStateOf<ProxyHost?>(null) }
    var kind by remember { mutableStateOf(ProxyHostKind.PROXY) }
    var names by remember { mutableStateOf("") }
    var scheme by remember { mutableStateOf(ProxyScheme.HTTP) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("80") }
    var websockets by remember { mutableStateOf(false) }
    var blockExploits by remember { mutableStateOf(false) }
    var cacheAssets by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(true) }
    var forceSsl by remember { mutableStateOf(false) }
    var http2 by remember { mutableStateOf(false) }
    var http3 by remember { mutableStateOf(false) }
    var hsts by remember { mutableStateOf(false) }
    var hstsSub by remember { mutableStateOf(false) }
    var verifyTarget by remember { mutableStateOf(false) }
    var minTls by remember { mutableStateOf<MinTlsVersion?>(null) }
    var accessListId by remember { mutableStateOf(0L) }
    var certId by remember { mutableStateOf(0L) }
    var redirectTarget by remember { mutableStateOf("") }
    var redirectScheme by remember { mutableStateOf(RedirectScheme.AUTO) }
    var redirectCode by remember { mutableStateOf(301) }
    var preservePath by remember { mutableStateOf(true) }
    var advanced by remember { mutableStateOf(false) }
    var showMode by remember { mutableStateOf(false) }
    // Auswahllisten der Node (leer, wenn nicht lesbar); Anzeigename je ID
    var accessLists by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var certs by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var cdnName by remember { mutableStateOf<CDNOnlineName?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun fill(h: ProxyHost) {
        loaded = h
        kind = h.kind ?: ProxyHostKind.PROXY
        names = h.names.joinToString("\n")
        scheme = h.scheme ?: ProxyScheme.HTTP
        host = h.targetHost.orEmpty()
        port = h.targetPort?.toString().orEmpty()
        websockets = h.websockets == true
        blockExploits = h.blockExploits == true
        cacheAssets = h.cacheAssets == true
        enabled = h.enabled != false
        forceSsl = h.forceSsl == true
        http2 = h.http2 == true
        http3 = h.http3 == true
        hsts = h.hsts == true
        hstsSub = h.hstsSubdomains == true
        verifyTarget = h.verifyTarget == true
        minTls = h.minTls
        accessListId = h.accessListId ?: 0L
        certId = h.certId ?: 0L
        redirectTarget = h.redirectTarget.orEmpty()
        redirectScheme = h.redirectScheme ?: RedirectScheme.AUTO
        redirectCode = h.redirectCode ?: 301
        preservePath = h.preservePath != false
    }

    fun run(spinner: String, block: suspend (Node64) -> Unit) {
        val node = nodeOf(ctx, nodeId)
        if (node == null) {
            req.message("No access", "No access saved for this node.", R.drawable.error_24px)
            return
        }
        req.launch(scope, spinner = spinner, onError = { req.nodeError(it) }) { block(node) }
    }

    // Auswahllisten sind optional: ein Fehler (z. B. fehlende Berechtigung) lässt nur die Auswahl weg
    suspend fun loadChoices(node: Node64) {
        try { accessLists = node.proxy.accessLists.list().mapNotNull { u -> u.accessList?.let { a -> a.id?.let { it to (a.name ?: "#$it") } } }.toMap() } catch (e: IPv64Exception) { }
        try { certs = node.certificates.list().mapNotNull { c -> c.id?.let { it to (c.names.joinToString().ifBlank { "#$it" }) } }.toMap() } catch (e: IPv64Exception) { }
        if (hostId != null) try { cdnName = node.proxy.online().names.firstOrNull { it.hostId == hostId } } catch (e: IPv64Exception) { }
    }

    fun load() = run("Loading...") {
        if (!create) fill(it.proxy.hosts.get(hostId!!))
        loadChoices(it)
    }

    LaunchedEffect(Unit) { load() }

    val isProxy = kind == ProxyHostKind.PROXY
    val isRedirect = kind == ProxyHostKind.REDIRECT
    // Weitere Arten (404, Passthrough, Status) bearbeiten hier nur Namen
    val editable = isProxy || isRedirect

    fun save() {
        val nameList = names.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }
        val p = port.trim().toIntOrNull()
        error = when {
            nameList.isEmpty() -> "Enter at least one domain name."
            isProxy && host.isBlank() -> "Enter the target host."
            isProxy && (p == null || p !in 1..65535) -> "The port must be between 1 and 65535."
            isRedirect && redirectTarget.isBlank() -> "Enter the redirect target."
            else -> null
        }
        // Save sitzt oben, der Fehlertext unten im Formular: zusätzlich als Meldung zeigen
        error?.let { req.message("Check your input", it, R.drawable.warning_24px); return }
        val input = if (editable) ProxyHostInput(
            names = nameList,
            enabled = if (create) enabled else null, kind = if (create) kind else null,
            scheme = if (isProxy) scheme else null, targetHost = if (isProxy) host.trim() else null,
            targetPort = if (isProxy) p else null,
            websockets = if (isProxy) websockets else null, blockExploits = if (isProxy) blockExploits else null,
            cacheAssets = if (isProxy) cacheAssets else null, verifyTarget = if (isProxy) verifyTarget else null,
            redirectTarget = if (isRedirect) redirectTarget.trim() else null,
            redirectScheme = if (isRedirect) redirectScheme else null,
            redirectCode = if (isRedirect) redirectCode else null,
            preservePath = if (isRedirect) preservePath else null,
            forceSsl = forceSsl, http2 = http2, http3 = http3, hsts = hsts, hstsSubdomains = hstsSub,
            minTls = minTls, accessListId = accessListId, certId = certId
        ) else ProxyHostInput(names = nameList)
        run("Saving...") {
            if (create) it.proxy.hosts.create(input) else it.proxy.hosts.update(hostId!!, input)
            navController.popBackStack()
        }
    }

    // Bearbeiten erst nach erfolgreichem Laden, sonst überschriebe Speichern die Namen des Hosts
    val canSave = !req.isBusy && (create || loaded != null)
    ScreenScaffold(
        title = Tabs.getLabel(Tab.proxy_host_edit), navController = navController, mainPadding = mainPadding,
        actions = {
            if (!create) IconButton(onClick = { confirmDelete = true }, enabled = !req.isBusy && loaded != null) {
                Icon(painterResource(R.drawable.delete_24px), contentDescription = "Delete")
            }
            IconButton(onClick = { save() }, enabled = canSave) {
                Icon(painterResource(R.drawable.save_24px), contentDescription = if (create) "Create" else "Save")
            }
        }
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val l = loaded
            if (!create && l != null) {
                // Kopf wie in der Liste: gespeicherter Stand mit Erreichbarkeit, SSL und CDN64
                ListGroup { ProxyHostRow(l, cdnName, emptyList(), null) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val on = l.enabled != false
                    ActionTile(
                        if (on) R.drawable.pause_24px else R.drawable.play_arrow_24px, if (on) "Disable" else "Enable",
                        { run(if (on) "Disabling..." else "Enabling...") { loaded = if (on) it.proxy.hosts.disable(hostId!!) else it.proxy.hosts.enable(hostId!!) } },
                        Modifier.weight(1f), enabled = !req.isBusy
                    )
                    val inCdn = cdnName != null
                    ActionTile(
                        if (inCdn) R.drawable.cloud_off_24px else R.drawable.cloud_24px, if (inCdn) "Disable CDN64" else "Enable CDN64",
                        {
                            // Einschalten fragt erst den Modus ab
                            if (!inCdn) showMode = true
                            else run("Disabling CDN64...") { node ->
                                node.proxy.hosts.disableOnline(hostId!!)
                                cdnName = node.proxy.online().names.firstOrNull { it.hostId == hostId }
                            }
                        },
                        Modifier.weight(1f), enabled = !req.isBusy
                    )
                }
            }
            FormSection("Host") {
                if (create) FormDropdown("Type", kind, listOf(ProxyHostKind.PROXY, ProxyHostKind.REDIRECT), { if (it == ProxyHostKind.PROXY) "Proxy" else "Redirect" }, { kind = it })
                FormTextField("Domain names", names, { names = it; error = null }, singleLine = false, minLines = 2, supporting = "One per line")
                if (create) FormSwitchRow("Enabled", enabled, { enabled = it })
            }
            if (isProxy) FormSection("Target") {
                FormDropdown("Scheme", scheme, ProxyScheme.entries, { it.value }, { scheme = it })
                FormTextField("Target host", host, { host = it; error = null }, placeholder = "192.168.1.10")
                FormTextField("Target port", port, { port = it; error = null }, keyboardType = KeyboardType.Number)
            }
            if (isRedirect) FormSection("Redirect") {
                FormTextField("Redirect target", redirectTarget, { redirectTarget = it; error = null }, placeholder = "example.com/path", supporting = "domain[:port][/path]")
                FormDropdown("Redirect scheme", redirectScheme, RedirectScheme.entries, { it.value }, { redirectScheme = it })
                FormDropdown("Redirect code", redirectCode, listOf(300, 301, 302, 303, 307, 308), { it.toString() }, { redirectCode = it })
                FormSwitchRow("Preserve path", preservePath, { preservePath = it })
            }
            if (editable) FormSection("Options") {
                FormSwitchRow("Force HTTPS", forceSsl, { forceSsl = it })
                if (isProxy) {
                    FormSwitchRow("WebSockets", websockets, { websockets = it })
                    FormSwitchRow("Block exploits", blockExploits, { blockExploits = it })
                    FormSwitchRow("Cache assets", cacheAssets, { cacheAssets = it })
                }
            }
            if (editable) {
                FormAccordion("Advanced", advanced, { advanced = !advanced }) {
                    FormSwitchRow("HTTP/2", http2, { http2 = it })
                    FormSwitchRow("HTTP/3", http3, { http3 = it })
                    FormSwitchRow("HSTS", hsts, { hsts = it })
                    if (hsts) FormSwitchRow("HSTS for subdomains", hstsSub, { hstsSub = it })
                    FormDropdown(
                        "Minimum TLS", minTls,
                        // null wird beim Speichern weggelassen und ließe einen gesetzten Wert unverändert: dann kein "Default"
                        (if (loaded?.minTls == null) listOf(null) else emptyList()) + MinTlsVersion.entries,
                        { it?.value ?: "Default" }, { minTls = it }
                    )
                    if (isProxy) FormSwitchRow("Verify target certificate", verifyTarget, { verifyTarget = it }, supporting = "For https targets")
                    FormDropdown(
                        "Access list", accessListId, listOf(0L) + accessLists.keys.filter { it != 0L },
                        { accessLists[it] ?: if (it == 0L) "None (public)" else "#$it" }, { accessListId = it }
                    )
                    FormDropdown(
                        "Certificate", certId, listOf(0L, -1L) + certs.keys.filter { it > 0 },
                        { certs[it] ?: when (it) { 0L -> "Automatic (matching name)"; -1L -> "None (HTTP only)"; else -> "#$it" } }, { certId = it }
                    )
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }

    if (showMode) {
        CdnModeSheet(
            hostLabel = names.lines().firstOrNull(),
            onConfirm = { mode, hc ->
                showMode = false
                run("Enabling CDN64...") { node ->
                    node.enableCdn64(hostId!!, mode, hc)?.let { req.message("CDN64", it, R.drawable.error_24px) }
                    cdnName = node.proxy.online().names.firstOrNull { it.hostId == hostId }
                }
            },
            onDismiss = { showMode = false }
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete proxy host?",
            text = "${names.lines().firstOrNull().orEmpty()} will be removed from the node.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                run("Deleting...") {
                    it.proxy.hosts.delete(hostId!!)
                    navController.popBackStack()
                }
            },
            onDismiss = { confirmDelete = false }
        )
    }
    RequestStateHost(req)
}
