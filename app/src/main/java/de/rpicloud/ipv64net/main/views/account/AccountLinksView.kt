package de.rpicloud.ipv64net.main.views.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.openUrl
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.SectionHeader
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.common.itemsOrEmpty
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.model.AccountLink
import de.sr.ipv64.model.AccountLinkStatus
import de.sr.ipv64.model.AccountLinks

private const val IPV64_ACCOUNT_URL = "https://ipv64.net/account"

/**
 * Account-Freigaben (nur lesen): wer den eigenen Account verwalten darf und welche Accounts man selbst verwaltet.
 * 1 Anfrage (accountLinks), Pull-to-Refresh lädt neu. Verwaltung nur auf ipv64.net.
 */
@Composable
fun AccountLinksView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    var links by remember { mutableStateOf(AccountLinks()) }
    var loaded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    fun load(pull: Boolean = false) {
        if (pull) refreshing = true
        req.launch(
            scope,
            spinner = if (pull) null else "Loading...",
            retry = { load() },
            onFinally = { refreshing = false }
        ) {
            links = ipv64Retrying { IPv64Client.get(ctx).account.accountLinks() }
            loaded = true
        }
    }

    LaunchedEffect(Unit) { load() }

    ScreenScaffold(
        title = Tabs.getLabel(Tab.account_links),
        navController = navController,
        mainPadding = mainPadding
    ) {
        RefreshableList(refreshing = refreshing, onRefresh = { load(pull = true) }) {
            item(key = "hint") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Account links let another IPv64 account manage yours, or you manage theirs. " +
                                    "Links are managed at ipv64.net.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(onClick = { openUrl(ctx, IPV64_ACCOUNT_URL) }) { Text("Manage at ipv64.net") }
                    }
                }
            }
            item(key = "shared_header") { SectionHeader("Can manage your account") }
            itemsOrEmpty(
                items = links.sharedWith,
                loaded = loaded,
                emptyText = "Nobody else can manage your account.",
                key = { l -> "shared_${l.id ?: l.hashCode()}" }
            ) { AccountLinkItem(it) }
            item(key = "managed_header") { SectionHeader("Accounts you manage") }
            // Eigener Schlüssel für den Leer-Zustand, da itemsOrEmpty immer "empty_state" verwendet
            if (loaded && links.managed.isEmpty()) {
                item(key = "managed_empty") {
                    Text(
                        "You don't manage any other account.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else {
                links.managed.forEach { l ->
                    item(key = "managed_${l.id ?: l.hashCode()}") { AccountLinkItem(l) }
                }
            }
        }
    }

    RequestStateHost(req)
}

@Composable
private fun AccountLinkItem(link: AccountLink) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    link.email ?: "Unknown account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                when (link.status) {
                    AccountLinkStatus.ACTIVE -> StatusChip("Active", colorResource(R.color.ipv64_green))
                    AccountLinkStatus.PENDING -> StatusChip("Pending", colorResource(R.color.ipv64_orange))
                    null -> Unit
                }
            }
            link.createdAt?.let {
                Text(
                    "Created ${it.formatGermanTime()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Last used: ${link.lastUsedAt.relativeOrDash()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
