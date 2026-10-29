package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.AccountCache
import de.rpicloud.ipv64net.helper.FeatureAvailability
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.PlanFeature
import de.rpicloud.ipv64net.helper.availability
import de.rpicloud.ipv64net.helper.canCreate
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.isLimitReached
import de.rpicloud.ipv64net.helper.isPlanRestricted
import de.rpicloud.ipv64net.helper.loadMe
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.helper.planRestrictionText
import de.rpicloud.ipv64net.main.views.common.DomainPickerDialog
import de.rpicloud.ipv64net.main.views.common.NotAvailableView
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.common.itemsOrEmpty
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.CdnDomain

/**
 * Liste der CDN-Domains. Anfragen: je Öffnen/Aktualisieren höchstens `account.me` (Plan, gecacht) und `cdn.list`
 * (30 s Cache), danach eine pro "Domain hinzufügen". Keine Einzelabfragen pro Zeile.
 */
@Composable
fun CdnView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()

    var domains by remember { mutableStateOf(CdnCache.peek(ctx)) }
    var availability by remember { mutableStateOf<FeatureAvailability>(FeatureAvailability.Unknown) }
    var refreshing by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    fun load(force: Boolean = false, pull: Boolean = false) {
        if (pull) refreshing = true
        req.notAvailable = null
        req.launch(
            scope,
            spinner = if (pull || domains != null) null else "Loading...",
            retry = { load(true) },
            planGate = true,
            onFinally = { refreshing = false }
        ) {
            // Plan-Prüfung ist nicht kritisch: bei Fehlern entscheidet der Server beim Laden der Liste
            try {
                availability = loadMe(ctx, force).availability(PlanFeature.CDN)
            } catch (e: IPv64Exception) {
                availability = FeatureAvailability.Unknown
            }
            if (availability is FeatureAvailability.NotInPlan) {
                req.notAvailable = "Your plan does not include the CDN."
                return@launch
            }
            domains = CdnCache.get(ctx, force) { it.cdn.list() }
        }
    }

    fun add(domain: String) {
        req.launch(
            scope,
            spinner = "Adding domain...",
            onError = { e ->
                if (e.isLimitReached() || e.isPlanRestricted()) {
                    req.message("Plan limit reached", e.planRestrictionText())
                    true
                } else false
            }
        ) {
            val created = ipv64Retrying { IPv64Client.get(ctx).cdn.add(domain) }
            CdnDetailStore.put(created)
            CdnCache.upsertCdn(created.toCdnDomain())
            AccountCache.invalidate()
            domains = CdnCache.peek()
            // Die Details lesen das Token vom Eintrag unter diesem Screen (Liste)
            navController.navigateWithArgs(Tab.cdn_details, NavKeys.SELECTED_CDN_TOKEN to created.id)
        }
    }

    LaunchedEffect(Unit) { load() }

    val canCreate = availability.canCreate
    ScreenScaffold(
        title = Tabs.getLabel(Tab.cdn),
        navController = navController,
        mainPadding = mainPadding,
        fab = {
            if (req.notAvailable == null) {
                FloatingActionButton(
                    onClick = {
                        if (!canCreate) {
                            val limit = availability as? FeatureAvailability.LimitReached
                            req.message(
                                "Plan limit reached",
                                if (limit != null) "Your plan allows ${limit.limit} CDN domains. Remove one or upgrade your plan."
                                else "Your plan does not allow more CDN domains."
                            )
                            return@FloatingActionButton
                        }
                        // Ohne geladene Liste lässt sich nicht ausschließen, welche Domains schon im CDN sind
                        if (domains == null) load(true) else showPicker = true
                    },
                    containerColor = if (canCreate) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (canCreate) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ) {
                    Icon(painter = painterResource(R.drawable.add_24px), contentDescription = "Add domain to CDN")
                }
            }
        }
    ) {
        val unavailable = req.notAvailable
        if (unavailable != null) {
            NotAvailableView(PlanFeature.CDN.label, unavailable)
        } else {
            RefreshableList(refreshing = refreshing, onRefresh = { load(true, true) }) {
                val limit = availability as? FeatureAvailability.LimitReached
                if (limit != null) {
                    item(key = "limit") {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Plan limit reached (${limit.used} of ${limit.limit} domains)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "Remove a domain from the CDN or upgrade your plan to add more.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                itemsOrEmpty(
                    items = domains.orEmpty(),
                    loaded = domains != null,
                    emptyText = "No domains in the CDN yet. Tap + to add one.",
                    key = { it.id ?: it.hashCode() }
                ) { cdn ->
                    CdnItemView(cdn) {
                        navController.navigateWithArgs(Tab.cdn_details, NavKeys.SELECTED_CDN_TOKEN to it.id)
                    }
                }
            }
        }
    }

    if (showPicker) {
        DomainPickerDialog(
            exclude = domains.orEmpty().mapNotNull { it.domain }.toSet(),
            title = "Add domain to CDN",
            onPick = { name ->
                showPicker = false
                add(name)
            },
            onDismiss = { showPicker = false }
        )
    }
    RequestStateHost(req)
}

// Zeile der Liste: nur Daten aus der Liste, keine Einzelabfragen
@Composable
private fun CdnItemView(cdn: CdnDomain, onClick: (CdnDomain) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = { onClick(cdn) }) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = cdn.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (cdn.isBusy()) StatusChip("Working", colorResource(R.color.ipv64_orange))
                else if (cdn.active == true) StatusChip("Active", colorResource(R.color.ipv64_green))
                else StatusChip("Inactive", colorResource(R.color.ipv64_gray))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(cdn.proxy?.state.proxyLabel(), cdn.proxy?.state.proxyColor())
                if (cdn.portmapper?.state == de.sr.ipv64.model.CdnPortmapperState.ON) {
                    StatusChip("Port mapper", colorResource(R.color.ipv64_blue))
                }
            }
            val server = cdn.server?.name?.takeIf { it.isNotBlank() }?.let { "Server $it · " }.orEmpty()
            Text(
                text = server + cdn.trafficMb.trafficText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
