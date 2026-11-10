package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.helper.AccountCache
import de.rpicloud.ipv64net.helper.FeatureAvailability
import de.rpicloud.ipv64net.helper.Hc2AccountCache
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.PlanFeature
import de.rpicloud.ipv64net.helper.availability
import de.rpicloud.ipv64net.helper.loadHc2Account
import de.rpicloud.ipv64net.helper.loadMe
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.helper.usageText
import de.rpicloud.ipv64net.main.views.common.HubScreen
import de.rpicloud.ipv64net.main.views.common.HubSection
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.SearchField
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.main.views.security.isValidIp
import de.rpicloud.ipv64net.main.views.security.normalizeIpInput
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.model.Hc2Account
import de.sr.ipv64.model.Me
import de.sr.ipv64.IPv64Exception
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import de.rpicloud.ipv64net.models.User
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * Hub "Infrastructure": Nodes, CDN, DNS64 und Security (IP-Auskunft, Threat-Tools).
 * Höchstens zwei Anfragen nacheinander (AccountCache → Nutzung/Limits, Hc2AccountCache → Node-Zähler), beide
 * gecacht; Pull-to-Refresh lädt neu. Im Plan nicht enthaltene Funktionen bleiben antippbar (Zielscreen zeigt
 * NotAvailableView), erhalten aber den Untertitel "Not available on your plan".
 */
@Composable
fun NetworkView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    var me by remember { mutableStateOf<Me?>(AccountCache.peek(ctx)) }
    var hc2Account by remember { mutableStateOf<Hc2Account?>(Hc2AccountCache.peek()) }
    var refreshing by remember { mutableStateOf(false) }
    // Direkt verbundene Nodes (nicht verknüpft) zählen mit; Abgleich gegen die gecachte Cloud-Liste
    var directIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var cloudIds by remember { mutableStateOf(NodeListCache.peek(ctx)?.map { it.nodeId }) }
    // Nur direkt verbundene Nodes: Anzahl und davon per health() erreichbar
    var directTotal by remember { mutableStateOf(0) }
    var directOnline by remember { mutableStateOf(0) }

    fun load(force: Boolean, pull: Boolean = false) {
        if (pull) refreshing = true
        req.launch(
            scope,
            spinner = null,
            retry = { load(force = true) },
            onFinally = { refreshing = false }
        ) {
            me = loadMe(ctx, force)
            hc2Account = loadHc2Account(ctx, force)
            // Nur mit direkten Zugängen nötig; ein Fehler hier soll den Hub nicht stören
            if (directIds.isNotEmpty()) {
                cloudIds = try { NodeListCache.get(ctx, force) { it.nodes.list() }.map { it.nodeId } } catch (_: IPv64Exception) { cloudIds }
                val only = cloudIds?.let { ids -> directIds.filter { it !in ids } }.orEmpty()
                directOnline = coroutineScope {
                    only.map { id ->
                        async {
                            val (url, key) = LocalNodeStore.get(ctx, id) ?: return@async 0
                            try { localNodeClient(url, key).node.health(); 1 } catch (_: IPv64Exception) { 0 }
                        }
                    }.awaitAll().sum()
                }
                directTotal = only.size
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        directIds = LocalNodeStore.list(ctx, User.current?.id?.toString().orEmpty())
    }

    // Nach dem Laden der direkten Zugänge (ON_RESUME) laden, damit der Cloud-Abgleich mitläuft
    LaunchedEffect(directIds.isNotEmpty()) { load(force = false) }

    // IP-Feld prüft lokal und öffnet danach die IP-Auskunft, die die Abfrage selbst ausführt
    var ip by rememberSaveable { mutableStateOf("") }
    var ipError by remember { mutableStateOf<String?>(null) }

    fun submitIp() {
        val value = normalizeIpInput(ip)
        if (!isValidIp(value)) {
            ipError = "Enter a valid IPv4 or IPv6 address."
            return
        }
        ipError = null
        navController.navigateWithArgs(Tab.threat_lookup, NavKeys.THREAT_LOOKUP_IP to value)
    }

    fun planSubtitle(feature: PlanFeature, default: String): String = when (me.availability(feature)) {
        FeatureAvailability.NotInPlan -> "Not available on your plan"
        is FeatureAvailability.LimitReached -> "$default · limit reached"
        else -> default
    }

    val account = me
    val nodes = hc2Account?.nodes
    val sections = listOf(
        HubSection(
            tab = Tab.nodes,
            subtitle = "Node64 devices for local checks",
            badge = (nodes != null || directTotal > 0).takeIf { it }?.let {
                "${(nodes?.online ?: 0) + directOnline} / ${(nodes?.total ?: 0) + directTotal} online"
            }
        ),
        HubSection(
            tab = Tab.cdn,
            subtitle = planSubtitle(PlanFeature.CDN, "Reverse proxy and TLS for your domains"),
            badge = account?.usage?.cdnDomains?.takeIf { account.availability(PlanFeature.CDN) != FeatureAvailability.NotInPlan }
                ?.usageText()
        ),
        HubSection(
            tab = Tab.dns64,
            subtitle = planSubtitle(PlanFeature.DNS64, "Private DNS resolvers with filtering"),
            badge = account?.usage?.dns64Clients?.takeIf { account.availability(PlanFeature.DNS64) != FeatureAvailability.NotInPlan }
                ?.usageText()
        )
    )

    // Security-Bereich (ehemals eigener Tab): keine Anfragen; Tab.threat_lookup bleibt über das IP-Feld erreichbar
    val securitySections = listOf(
        HubSection(Tab.threat_watch, subtitle = "Get alerts about attackers in your networks"),
        HubSection(Tab.threat_stats, subtitle = "Attacks, ports and rankings of the Honeypot64 network"),
        HubSection(Tab.password_check, subtitle = "Was a password used in attacks? Stays on your device"),
        HubSection(Tab.honeypot, subtitle = "Your sensors, contribution and blocklist token")
    ).map { it.copy(group = "Security") }

    HubScreen(
        title = Tabs.getLabel(Tab.network),
        navController = navController,
        mainPadding = mainPadding,
        sections = sections.map { it.copy(group = "Network") } + securitySections,
        refreshing = refreshing,
        onRefresh = { load(force = true, pull = true) },
        header = {
            item(key = "ip_lookup") {
                SearchField(
                    value = ip,
                    onValueChange = { ip = it; ipError = null },
                    placeholder = "Look up an IP address",
                    onSearch = { submitIp() },
                    isError = ipError != null,
                    supportingText = ipError,
                    keyboardType = KeyboardType.Uri,
                    submitContentDescription = "Look up"
                )
            }
        }
    )

    RequestStateHost(req)
}
