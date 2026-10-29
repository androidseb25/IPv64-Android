package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.helper.AccountCache
import de.rpicloud.ipv64net.helper.FeatureAvailability
import de.rpicloud.ipv64net.helper.Hc2AccountCache
import de.rpicloud.ipv64net.helper.PlanFeature
import de.rpicloud.ipv64net.helper.availability
import de.rpicloud.ipv64net.helper.loadHc2Account
import de.rpicloud.ipv64net.helper.loadMe
import de.rpicloud.ipv64net.helper.usageText
import de.rpicloud.ipv64net.main.views.common.HubScreen
import de.rpicloud.ipv64net.main.views.common.HubSection
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.model.Hc2Account
import de.sr.ipv64.model.Me

/**
 * Hub "Network": CDN, DNS64 und Nodes.
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
        }
    }

    LaunchedEffect(Unit) { load(force = false) }

    fun planSubtitle(feature: PlanFeature, default: String): String = when (me.availability(feature)) {
        FeatureAvailability.NotInPlan -> "Not available on your plan"
        is FeatureAvailability.LimitReached -> "$default · limit reached"
        else -> default
    }

    val account = me
    val nodes = hc2Account?.nodes
    val sections = listOf(
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
        ),
        HubSection(
            tab = Tab.nodes,
            subtitle = "Node64 devices for local checks",
            badge = nodes?.let { "${it.online ?: 0} / ${it.total ?: 0} online" }
        )
    )

    HubScreen(
        title = Tabs.getLabel(Tab.network),
        navController = navController,
        mainPadding = mainPadding,
        sections = sections,
        refreshing = refreshing,
        onRefresh = { load(force = true, pull = true) }
    )

    RequestStateHost(req)
}
