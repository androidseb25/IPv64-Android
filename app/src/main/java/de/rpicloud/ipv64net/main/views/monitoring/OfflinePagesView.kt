package de.rpicloud.ipv64net.main.views.monitoring

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import de.rpicloud.ipv64net.main.views.common.GroupGap
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.main.views.common.NotAvailableView
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.main.views.common.groupedItemsOrEmpty
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.model.OfflinePage

/**
 * Liste der Offline-Seiten (eine Anfrage, 60 s Cache). 403 mit Plan-Bezug zeigt NotAvailableView.
 * Antippen öffnet den Editor (NavKeys.SELECTED_OFFLINE_PAGE_ID), das Plus legt eine neue Seite an.
 */
@Composable
fun OfflinePagesView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()

    var pages by remember { mutableStateOf(OfflinePageCache.peek(ctx)) }
    var refreshing by remember { mutableStateOf(false) }

    fun load(force: Boolean = false, pull: Boolean = false) {
        if (pull) refreshing = true
        req.notAvailable = null
        req.launch(
            scope,
            spinner = if (pull || pages != null) null else "Loading...",
            retry = { load(true) },
            planGate = true,
            onFinally = { refreshing = false }
        ) {
            pages = OfflinePageCache.get(ctx, force) { it.offlinePages.list() }
        }
    }

    LaunchedEffect(Unit) { load() }

    ScreenScaffold(
        title = Tabs.getLabel(Tab.offline_pages),
        navController = navController,
        mainPadding = mainPadding,
        fab = {
            if (req.notAvailable == null) {
                FloatingActionButton(
                    onClick = { navController.navigateWithArgs(Tab.offline_page_edit, NavKeys.SELECTED_OFFLINE_PAGE_ID to null) }
                ) { Icon(painter = painterResource(R.drawable.add_24px), contentDescription = "New offline page") }
            }
        }
    ) {
        val unavailable = req.notAvailable
        if (unavailable != null) {
            NotAvailableView("Offline pages", unavailable)
        } else {
            RefreshableList(refreshing = refreshing, onRefresh = { load(true, true) }, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                item(key = "hint") {
                    Text(
                        modifier = Modifier.padding(bottom = GroupGap),
                        text =
                        "Offline pages are shown instead of an error while a failover rule points to them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                groupedItemsOrEmpty(
                    items = pages.orEmpty(),
                    loaded = pages != null,
                    emptyText = "No offline pages yet.",
                    key = "offline_pages"
                ) { page ->
                    OfflinePageItem(page) {
                        navController.navigateWithArgs(Tab.offline_page_edit, NavKeys.SELECTED_OFFLINE_PAGE_ID to it.id)
                    }
                }
            }
        }
    }
    RequestStateHost(req)
}

@Composable
private fun OfflinePageItem(page: OfflinePage, onClick: (OfflinePage) -> Unit) {
    val meta = "${page.views ?: 0} views" + if (page.iconUrl.isNullOrBlank()) "" else " · with logo"
    ListRow(
        title = page.name?.takeIf { it.isNotBlank() } ?: "Offline page",
        subtitle = (page.domain?.takeIf { it.isNotBlank() }?.let { "$it\n" }.orEmpty()) + meta,
        trailing = when {
            page.active == true -> ({ StatusChip("Live", colorResource(R.color.ipv64_red)) })
            page.inUseByPolicy != null -> ({ StatusChip("In use", colorResource(R.color.ipv64_green)) })
            else -> null
        },
        onClick = { onClick(page) }
    )
}
