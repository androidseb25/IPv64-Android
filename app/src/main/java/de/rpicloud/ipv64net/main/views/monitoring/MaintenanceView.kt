package de.rpicloud.ipv64net.main.views.monitoring

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.main.views.common.NotAvailableView
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.itemsOrEmpty
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.model.Hc2Maintenance

// Wartungsfenster: Liste aus dem Cache (60 s), Pull-to-Refresh erzwingt neu laden
@Composable
fun MaintenanceView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    var items by remember { mutableStateOf(MaintenanceCache.peek(ctx)) }
    var refreshing by remember { mutableStateOf(false) }

    fun load(force: Boolean = false, pull: Boolean = false) {
        if (pull) refreshing = true
        req.launch(
            scope,
            spinner = if (pull || items != null) null else "Loading...",
            retry = { load(true) },
            planGate = true,
            onFinally = { refreshing = false }
        ) {
            items = MaintenanceCache.get(ctx, force) { it.hc2.maintenance.list() }
        }
    }

    LaunchedEffect(Unit) { load() }

    fun open(item: Hc2Maintenance?) {
        // Beim Anlegen den Schlüssel explizit entfernen (sonst öffnet sich das zuletzt bearbeitete Fenster)
        navController.navigateWithArgs(Tab.maintenance_edit, NavKeys.SELECTED_MAINTENANCE_ID to item?.id)
    }

    ScreenScaffold(
        Tabs.getLabel(Tab.maintenance), navController, mainPadding,
        fab = {
            if (req.notAvailable == null) {
                FloatingActionButton(onClick = { open(null) }) {
                    Icon(painterResource(R.drawable.add_24px), contentDescription = "New maintenance window")
                }
            }
        }
    ) {
        val na = req.notAvailable
        if (na != null) {
            NotAvailableView("Maintenance windows", na)
        } else {
            // Laufende zuerst, dann geplante (nächste zuerst), inaktive, zuletzt beendete (neueste zuerst)
            val list = items.orEmpty().sortedWith(
                compareBy<Hc2Maintenance> {
                    when (it.phase()) {
                        MaintenancePhase.RUNNING -> 0
                        MaintenancePhase.SCHEDULED -> 1
                        MaintenancePhase.INACTIVE -> 2
                        MaintenancePhase.ENDED -> 3
                    }
                }.thenBy {
                    val start = it.startAt?.toEpochMilliseconds() ?: 0L
                    if (it.phase() == MaintenancePhase.ENDED) -start else start
                }
            )
            RefreshableList(refreshing, { load(force = true, pull = true) }) {
                itemsOrEmpty(list, items != null, "No maintenance windows yet.", key = { "m_" + (it.id ?: it.hashCode()) }) { item ->
                    MaintenanceItemView(item) { open(item) }
                }
            }
        }
    }
    RequestStateHost(req)
}
