package de.rpicloud.ipv64net.main.views.monitoring

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.FeatureAvailability
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.PlanFeature
import de.rpicloud.ipv64net.helper.availability
import de.rpicloud.ipv64net.helper.canCreate
import de.rpicloud.ipv64net.helper.loadMe
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.main.views.common.NotAvailableView
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.itemsOrEmpty
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64Exception

/**
 * DNS-Failover-Liste. Anfragen: loadMe (60 s Cache, nur für das Plan-Gate) und die Policy-Liste (30 s Cache).
 * Beim Zurückkehren aus Details/Bearbeiten wird der Cache wiederverwendet bzw. nach Änderungen neu geladen.
 */
@Composable
fun FailoverView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()

    var policies by remember { mutableStateOf(FailoverCache.peek(ctx)) }
    var availability by remember { mutableStateOf<FeatureAvailability>(FeatureAvailability.Unknown) }
    var refreshing by remember { mutableStateOf(false) }

    fun load(force: Boolean = false, pull: Boolean = false) {
        if (pull) refreshing = true
        req.notAvailable = null
        req.launch(
            scope,
            spinner = if (pull || policies != null) null else "Loading...",
            retry = { load(true) },
            planGate = true,
            onFinally = { refreshing = false }
        ) {
            // Plan-Prüfung ist nicht kritisch: bei Fehlern entscheidet der Server beim Laden der Liste
            try {
                availability = loadMe(ctx, force).availability(PlanFeature.FAILOVER)
            } catch (e: IPv64Exception) {
                availability = FeatureAvailability.Unknown
            }
            if (availability is FeatureAvailability.NotInPlan) {
                req.notAvailable = "Your plan does not include DNS failover."
                return@launch
            }
            policies = FailoverCache.get(ctx, force) { it.failover.list() }
        }
    }

    LaunchedEffect(Unit) { load() }

    val canCreate = availability.canCreate
    ScreenScaffold(
        title = Tabs.getLabel(Tab.failover),
        navController = navController,
        mainPadding = mainPadding,
        actions = {
            IconButton(onClick = { navController.navigate(Tabs.getRoute(Tab.offline_pages)) }) {
                Icon(painter = painterResource(R.drawable.web_asset_off_24px), contentDescription = "Offline pages")
            }
        },
        fab = {
            if (req.notAvailable == null) {
                FloatingActionButton(
                    onClick = {
                        if (canCreate) {
                            navController.navigateWithArgs(Tab.failover_edit, NavKeys.EDIT_FAILOVER_ID to null)
                        }
                    },
                    containerColor = if (canCreate) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (canCreate) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ) {
                    Icon(painter = painterResource(R.drawable.add_24px), contentDescription = "New failover policy")
                }
            }
        }
    ) {
        val unavailable = req.notAvailable
        if (unavailable != null) {
            NotAvailableView(PlanFeature.FAILOVER.label, unavailable)
        } else {
            RefreshableList(refreshing = refreshing, onRefresh = { load(true, true) }) {
                val limit = availability as? FeatureAvailability.LimitReached
                if (limit != null) {
                    item(key = "limit") {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Plan limit reached (${limit.used} of ${limit.limit} policies)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "Delete a policy or upgrade your plan to create more.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                itemsOrEmpty(
                    items = policies.orEmpty(),
                    loaded = policies != null,
                    emptyText = "No failover policies yet.",
                    key = { it.id ?: it.hashCode() }
                ) { policy ->
                    FailoverItemView(policy) {
                        navController.navigateWithArgs(Tab.failover_details, NavKeys.SELECTED_FAILOVER_ID to it.id)
                    }
                }
            }
        }
    }
    RequestStateHost(req)
}
