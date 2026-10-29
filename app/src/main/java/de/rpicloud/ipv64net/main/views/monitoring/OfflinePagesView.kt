package de.rpicloud.ipv64net.main.views.monitoring

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
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.main.views.common.NotAvailableView
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.common.itemsOrEmpty
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
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
            RefreshableList(refreshing = refreshing, onRefresh = { load(true, true) }) {
                item(key = "hint") {
                    Text(
                        "Offline pages are shown instead of an error while a failover rule points to them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                itemsOrEmpty(
                    items = pages.orEmpty(),
                    loaded = pages != null,
                    emptyText = "No offline pages yet.",
                    key = { it.id ?: it.hashCode() }
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
    Card(modifier = Modifier.fillMaxWidth(), onClick = { onClick(page) }) {
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
                    page.name?.takeIf { it.isNotBlank() } ?: "Offline page",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (page.active == true) StatusChip("Live", colorResource(R.color.ipv64_red))
                else if (page.inUseByPolicy != null) StatusChip("In use", colorResource(R.color.ipv64_green))
            }
            page.domain?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${page.views ?: 0} views" + if (page.iconUrl.isNullOrBlank()) "" else " · with logo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
