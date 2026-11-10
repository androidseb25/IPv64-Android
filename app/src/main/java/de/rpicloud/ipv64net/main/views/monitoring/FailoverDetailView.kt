package de.rpicloud.ipv64net.main.views.monitoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.AccountCache
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.helper.pollUntil
import de.rpicloud.ipv64net.helper.rememberNavArg
import de.rpicloud.ipv64net.main.views.common.ConfirmDialog
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.Hc2CheckPickerDialog
import de.rpicloud.ipv64net.main.views.common.GroupDivider
import de.rpicloud.ipv64net.main.views.common.InfoRow
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.NotAvailableView
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.SectionHeader
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.main.views.common.unknownCheckLabel
import de.rpicloud.ipv64net.main.views.common.GroupRowPadding
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.model.FailoverMonitor
import de.sr.ipv64.model.FailoverPolicy
import de.sr.ipv64.model.FailoverRule
import de.sr.ipv64.model.FailoverStatus
import kotlinx.coroutines.Job

// Ausstehende Bestätigung im Detail-Screen
private sealed interface DetailConfirm {
    data object Failover : DetailConfirm
    data object Fallback : DetailConfirm
    data object Delete : DetailConfirm
    data class RemoveMonitor(val token: String, val name: String) : DetailConfirm
    data class RemoveRule(val record: String, val name: String) : DetailConfirm
}

/**
 * Details einer Failover-Policy. Anfragen: ein GET beim Öffnen (Start mit Cache-Daten, falls vorhanden),
 * danach eine pro Aktion; Änderungen liefern das neue Objekt zurück. Nach manueller Umschaltung (*_requested)
 * wird alle 3 s (höchstens 30 s) gefragt, bis die Umschaltung abgeschlossen ist.
 */
@Composable
fun FailoverDetailView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    val id = navController.rememberNavArg<String>(NavKeys.SELECTED_FAILOVER_ID)

    var policy by remember { mutableStateOf(id?.let { i -> FailoverCache.peek(ctx)?.firstOrNull { it.id == i } }) }
    var refreshing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<DetailConfirm?>(null) }
    var showMonitorPicker by remember { mutableStateOf(false) }
    var pendingMonitor by remember { mutableStateOf<String?>(null) }
    var pendingNegate by remember { mutableStateOf(false) }
    var showRuleDialog by remember { mutableStateOf(false) }
    var pollJob by remember { mutableStateOf<Job?>(null) }

    // Neues Objekt übernehmen und in den Listen-Cache schreiben
    fun store(p: FailoverPolicy) {
        policy = p
        FailoverCache.upsert(p) { it.id == p.id }
    }

    // Wartet (3 s Takt, höchstens 30 s), bis eine laufende Umschaltung abgeschlossen ist
    fun track() {
        val pid = id ?: return
        if (pollJob?.isActive == true) return
        pollJob = req.launch(scope, spinner = null) {
            val finished = pollUntil(
                intervalMs = 3_000,
                maxMs = 30_000,
                onEach = { p: FailoverPolicy -> store(p) },
                fetch = { IPv64Client.get(ctx).failover.get(pid) },
                done = { p -> !p.isTransitioning() }
            )
            if (finished == null) {
                req.message("Still switching", "The switch is taking longer than expected. Pull down to refresh later.")
            }
        }
    }

    fun apply(p: FailoverPolicy) {
        store(p)
        if (p.isTransitioning()) track()
    }

    fun load(pull: Boolean = false) {
        val pid = id ?: return
        if (pull) refreshing = true
        req.launch(
            scope,
            spinner = if (pull || policy != null) null else "Loading...",
            retry = { load() },
            planGate = true,
            onFinally = { refreshing = false }
        ) {
            apply(ipv64Retrying { IPv64Client.get(ctx).failover.get(pid) })
        }
    }

    // Aktion mit Spinner, die die neue Policy zurückliefert
    fun act(spinner: String, block: suspend (de.sr.ipv64.IPv64, String) -> FailoverPolicy) {
        val pid = id ?: return
        req.launch(scope, spinner = spinner) {
            apply(block(IPv64Client.get(ctx), pid))
        }
    }

    LaunchedEffect(Unit) { load() }

    val p = policy
    val busy = req.isBusy
    val transitioning = p?.isTransitioning() == true
    val canDelete = p != null && p.status != FailoverStatus.FAILED_OVER && !transitioning

    ScreenScaffold(
        title = p?.name?.takeIf { it.isNotBlank() } ?: Tabs.getLabel(Tab.failover_details),
        navController = navController,
        mainPadding = mainPadding,
        actions = {
            IconButton(
                enabled = p != null && !busy,
                onClick = { navController.navigateWithArgs(Tab.failover_edit, NavKeys.EDIT_FAILOVER_ID to id) }
            ) { Icon(painter = painterResource(R.drawable.edit_24px), contentDescription = "Edit") }
            IconButton(
                enabled = canDelete && !busy,
                onClick = { confirm = DetailConfirm.Delete }
            ) { Icon(painter = painterResource(R.drawable.delete_24px), contentDescription = "Delete") }
        }
    ) {
        val unavailable = req.notAvailable
        when {
            id == null -> EmptyState("Policy not found.")
            unavailable != null -> NotAvailableView("DNS failover", unavailable)
            p == null -> Box(modifier = Modifier)
            else -> RefreshableList(refreshing = refreshing, onRefresh = { load(pull = true) }) {
                item(key = "header") { PolicySummary(p, transitioning) }

                item(key = "actions") {
                    PolicyActions(
                        policy = p,
                        enabled = !busy && !transitioning,
                        onFailover = { confirm = DetailConfirm.Failover },
                        onFallback = { confirm = DetailConfirm.Fallback },
                        onPause = { act("Pausing...") { api, pid -> ipv64Retrying { api.failover.pause(pid) } } },
                        onResume = { act("Resuming...") { api, pid -> ipv64Retrying { api.failover.resume(pid) } } }
                    )
                }

                item(key = "facts") { PolicyFacts(p) }

                item(key = "monitors_header") {
                    SectionRow("Monitors", "Add monitor", !busy) { showMonitorPicker = true }
                }
                if (p.monitors.isEmpty()) {
                    item(key = "monitors_empty") { EmptyState("No monitors. Add a healthcheck that triggers the failover.") }
                } else {
                    item(key = "monitors") {
                        ListGroup {
                            p.monitors.forEachIndexed { index, monitor ->
                                if (index > 0) GroupDivider()
                                MonitorRow(monitor, enabled = !busy) {
                                    val token = monitor.healthcheck ?: return@MonitorRow
                                    confirm = DetailConfirm.RemoveMonitor(token, monitor.name?.takeIf { it.isNotBlank() } ?: unknownCheckLabel(token))
                                }
                            }
                        }
                    }
                }

                item(key = "rules_header") {
                    SectionRow("Rules", "Add rule", !busy) { showRuleDialog = true }
                }
                if (p.rules.isEmpty()) {
                    item(key = "rules_empty") { EmptyState("No rules. Add an A or AAAA record that should be replaced.") }
                } else {
                    item(key = "rules") {
                        ListGroup {
                            p.rules.forEachIndexed { index, rule ->
                                if (index > 0) GroupDivider()
                                RuleRow(rule, enabled = !busy) {
                                    val record = rule.record ?: return@RuleRow
                                    confirm = DetailConfirm.RemoveRule(record, rule.fqdn?.takeIf { it.isNotBlank() } ?: "…" + record.takeLast(4))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- Bestätigungen ----
    when (val c = confirm) {
        DetailConfirm.Failover -> ConfirmDialog(
            title = "Switch to replacement?",
            text = "The DNS records of all rules are switched to their replacement targets now. " +
                    "Visitors reach the replacement as soon as DNS caches expire. " +
                    if (p?.mode == de.sr.ipv64.model.FailoverMode.AUTO_FAILOVER_AND_FALLBACK)
                        "The policy may switch back automatically once the monitors are UP again."
                    else "You have to switch back manually.",
            confirmLabel = "Switch",
            destructive = true,
            onConfirm = {
                confirm = null
                act("Switching...") { api, pid -> ipv64Retrying { api.failover.failover(pid) } }
            },
            onDismiss = { confirm = null }
        )

        DetailConfirm.Fallback -> ConfirmDialog(
            title = "Switch back?",
            text = "The DNS records of all rules are switched back to their original targets. " +
                    "If the original server is still unreachable, visitors will see errors until the replacement is used again.",
            confirmLabel = "Switch back",
            destructive = true,
            onConfirm = {
                confirm = null
                act("Switching back...") { api, pid -> ipv64Retrying { api.failover.fallback(pid) } }
            },
            onDismiss = { confirm = null }
        )

        DetailConfirm.Delete -> ConfirmDialog(
            title = "Delete policy?",
            text = "\"${p?.name.orEmpty()}\" with its monitors and rules will be deleted. This cannot be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirm = null
                val pid = id
                if (pid != null) {
                    req.launch(scope, spinner = "Deleting...") {
                        ipv64Retrying { IPv64Client.get(ctx).failover.delete(pid) }
                        FailoverCache.removeWhere { it.id == pid }
                        // Plan-Zähler (failoverPolicies) hat sich geändert
                        AccountCache.invalidate()
                        navController.popBackStack()
                    }
                }
            },
            onDismiss = { confirm = null }
        )

        is DetailConfirm.RemoveMonitor -> ConfirmDialog(
            title = "Remove monitor?",
            text = "${c.name} will no longer trigger this failover policy.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                confirm = null
                val pid = id
                if (pid != null) {
                    req.launch(scope, spinner = "Removing...") {
                        ipv64Retrying { IPv64Client.get(ctx).failover.removeMonitor(pid, c.token) }
                        policy?.let { store(it.copy(monitors = it.monitors.filterNot { m -> m.healthcheck == c.token })) }
                    }
                }
            },
            onDismiss = { confirm = null }
        )

        is DetailConfirm.RemoveRule -> ConfirmDialog(
            title = "Remove rule?",
            text = "${c.name} will no longer be protected by this policy." +
                    if (p?.status == FailoverStatus.FAILED_OVER) " Records that are currently replaced may be restored." else "",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                confirm = null
                val pid = id
                if (pid != null) {
                    req.launch(scope, spinner = "Removing...") {
                        ipv64Retrying { IPv64Client.get(ctx).failover.removeRule(pid, c.record) }
                        policy?.let { store(it.copy(rules = it.rules.filterNot { r -> r.record == c.record })) }
                    }
                }
            },
            onDismiss = { confirm = null }
        )

        null -> Unit
    }

    // ---- Monitor hinzufügen: Check wählen (oder Token eintragen), dann invertieren ja/nein ----
    if (showMonitorPicker) {
        Hc2CheckPickerDialog(
            initial = emptyList(),
            multi = false,
            title = "Select monitor",
            onConfirm = { tokens ->
                showMonitorPicker = false
                val token = tokens.firstOrNull()
                if (token != null) {
                    if (policy?.monitors?.any { it.healthcheck == token } == true) {
                        req.message("Already added", "This healthcheck is already a monitor of this policy.")
                    } else {
                        pendingNegate = false
                        pendingMonitor = token
                    }
                }
            },
            onDismiss = { showMonitorPicker = false }
        )
    }
    pendingMonitor?.let { token ->
        AlertDialog(
            onDismissRequest = { pendingMonitor = null },
            title = { Text("Add monitor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Healthcheck …${token.takeLast(4)} will be added as monitor.")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pendingNegate = !pendingNegate },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Inverted", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Counts UP as DOWN and DOWN as UP.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = pendingNegate, onCheckedChange = { pendingNegate = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val negate = pendingNegate
                    pendingMonitor = null
                    act("Adding monitor...") { api, pid -> ipv64Retrying { api.failover.addMonitor(pid, token, negate) } }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { pendingMonitor = null }) { Text("Cancel") } }
        )
    }

    // ---- Regel hinzufügen ----
    if (showRuleDialog) {
        FailoverRuleDialog(
            existingRecordRefs = p?.rules?.mapNotNull { it.record }?.toSet().orEmpty(),
            onConfirm = { request ->
                showRuleDialog = false
                act("Adding rule...") { api, pid -> ipv64Retrying { api.failover.addRule(pid, request) } }
            },
            onDismiss = { showRuleDialog = false }
        )
    }

    RequestStateHost(req)
}

@Composable
private fun PolicySummary(policy: FailoverPolicy, transitioning: Boolean) {
    ListGroup {
        ListRow(
            title = policy.name?.takeIf { it.isNotBlank() } ?: "Unnamed policy",
            subtitle = policy.mode.failoverTitle(),
            trailing = { StatusChip(policy.status.failoverLabel(), policy.status.failoverColor()) }
        )
        if (transitioning) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    "Switching DNS records. This usually takes a few seconds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PolicyFacts(policy: FailoverPolicy) {
    ListGroup("Details") {
        InfoRow("Mode", policy.mode.failoverTitle(), GroupRowPadding)
        GroupDivider()
        InfoRow("Monitor logic", policy.linking.failoverTitle(), GroupRowPadding)
        GroupDivider()
        InfoRow("Fall back only when all monitors are UP", if (policy.fallbackRequiresAll == true) "Yes" else "No", GroupRowPadding)
        GroupDivider()
        InfoRow("Notifications", notificationText(policy), GroupRowPadding)
        GroupDivider()
        InfoRow("Monitors down", "${policy.monitorsDown ?: 0} of ${policy.monitors.size}", GroupRowPadding)
        GroupDivider()
        InfoRow("Last check", policy.lastCheckAt?.formatRelative(), GroupRowPadding)
        if (policy.manual == true) {
            GroupDivider()
            InfoRow("Last switch", "Manual", GroupRowPadding)
        }
    }
}

private fun notificationText(policy: FailoverPolicy): String {
    if (policy.notificationsEnabled != true) return "Off"
    val parts = mutableListOf<String>()
    if (policy.hasAccountEmail()) parts += "Account e-mail"
    val others = policy.otherIntegrations().size
    if (others > 0) parts += "$others other integration" + if (others == 1) "" else "s"
    return if (parts.isEmpty()) "On, no recipient" else parts.joinToString(" + ")
}

@Composable
private fun PolicyActions(
    policy: FailoverPolicy,
    enabled: Boolean,
    onFailover: () -> Unit,
    onFallback: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            when (policy.status) {
                FailoverStatus.ACTIVE -> {
                    FilledTonalButton(onClick = onFailover, enabled = enabled && policy.rules.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Text("Switch to replacement")
                    }
                    OutlinedButton(onClick = onPause, enabled = enabled) { Text("Pause") }
                }

                FailoverStatus.FAILED_OVER -> FilledTonalButton(onClick = onFallback, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text("Switch back to original")
                }

                FailoverStatus.PAUSED -> FilledTonalButton(onClick = onResume, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text("Resume")
                }

                else -> Unit
            }
        }
        if (policy.status == FailoverStatus.ACTIVE && policy.rules.isEmpty()) {
            Text(
                "Add a rule before switching manually.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionRow(title: String, addLabel: String, enabled: Boolean, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionHeader(title, modifier = Modifier.weight(1f))
        TextButton(onClick = onAdd, enabled = enabled) {
            Icon(painter = painterResource(R.drawable.add_24px), contentDescription = null)
            Text(addLabel, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun MonitorRow(monitor: FailoverMonitor, enabled: Boolean, onRemove: () -> Unit) {
    ListRow(
        title = monitor.name?.takeIf { it.isNotBlank() } ?: unknownCheckLabel(monitor.healthcheck.orEmpty()),
        subtitle = if (monitor.negate == true) "Inverted" else null,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                monitor.healthcheckStatus?.takeIf { it.isNotBlank() }?.let {
                    StatusChip(it.uppercase(), failoverMonitorColor(it))
                }
                IconButton(onClick = onRemove, enabled = enabled) {
                    Icon(painter = painterResource(R.drawable.close_24px), contentDescription = "Remove monitor")
                }
            }
        }
    )
}

@Composable
private fun RuleRow(rule: FailoverRule, enabled: Boolean, onRemove: () -> Unit) {
    ListRow(
        title = "${rule.fqdn?.takeIf { it.isNotBlank() } ?: "Record …" + rule.record.orEmpty().takeLast(4)} (${rule.recordType ?: "?"})",
        subtitle = rule.target.describe(OfflinePageCache.peek()),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(rule.state.ruleLabel(), rule.state.ruleColor())
                IconButton(onClick = onRemove, enabled = enabled) {
                    Icon(painter = painterResource(R.drawable.close_24px), contentDescription = "Remove rule")
                }
            }
        }
    )
}
