package de.rpicloud.ipv64net.main.views.monitoring

import de.rpicloud.ipv64net.main.views.common.GroupGap
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
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
import de.rpicloud.ipv64net.helper.openUrl
import de.rpicloud.ipv64net.main.views.common.NotAvailableView
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.main.views.common.groupedItemsOrEmpty
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.model.StatusPage

// Statusseiten: Liste aus dem Cache (60 s), Pull-to-Refresh erzwingt neu laden
@Composable
fun StatusPagesView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    var pages by remember { mutableStateOf(StatusPageCache.peek(ctx)) }
    var refreshing by remember { mutableStateOf(false) }

    fun load(force: Boolean = false, pull: Boolean = false) {
        if (pull) refreshing = true
        req.launch(
            scope,
            spinner = if (pull || pages != null) null else "Loading...",
            retry = { load(true) },
            planGate = true,
            onFinally = { refreshing = false }
        ) {
            pages = StatusPageCache.get(ctx, force) { it.statusPages.list() }
        }
    }

    LaunchedEffect(Unit) { load() }

    fun open(page: StatusPage?) {
        // Beim Anlegen den Schlüssel explizit entfernen (sonst öffnet sich die zuletzt bearbeitete Seite)
        navController.navigateWithArgs(Tab.status_page_edit, NavKeys.SELECTED_STATUS_PAGE_ID to page?.id?.toString())
    }

    ScreenScaffold(
        Tabs.getLabel(Tab.status_pages), navController, mainPadding,
        fab = {
            if (req.notAvailable == null) {
                FloatingActionButton(onClick = { open(null) }) {
                    Icon(painterResource(R.drawable.add_24px), contentDescription = "New status page")
                }
            }
        }
    ) {
        val na = req.notAvailable
        if (na != null) {
            NotAvailableView("Status pages", na)
        } else {
            val list = pages.orEmpty().sortedBy { it.name.orEmpty().lowercase() }
            RefreshableList(refreshing, { load(force = true, pull = true) }, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                groupedItemsOrEmpty(list, pages != null, "No status pages yet.", key = "status_pages") { page ->
                    StatusPageItemView(
                        page,
                        onClick = { open(page) },
                        onOpen = { page.url?.let { openUrl(ctx, it) } }
                    )
                }
            }
        }
    }
    RequestStateHost(req)
}
