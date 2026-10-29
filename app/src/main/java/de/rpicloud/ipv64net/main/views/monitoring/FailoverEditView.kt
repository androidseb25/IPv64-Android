package de.rpicloud.ipv64net.main.views.monitoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.helper.AccountCache
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.rememberNavArg
import de.rpicloud.ipv64net.main.views.ChannelFormSection
import de.rpicloud.ipv64net.main.views.ChannelSwitchRow
import de.rpicloud.ipv64net.main.views.FormDropdown
import de.rpicloud.ipv64net.main.views.FormTextField
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.model.CreateFailoverPolicyRequest
import de.sr.ipv64.model.FailoverLinking
import de.sr.ipv64.model.FailoverMode
import de.sr.ipv64.model.FailoverPolicy
import de.sr.ipv64.model.FailoverStatus
import de.sr.ipv64.model.UpdateFailoverPolicyRequest

/**
 * Failover-Policy anlegen oder bearbeiten. Bearbeiten: Id aus NavKeys.EDIT_FAILOVER_ID (fehlt = anlegen).
 * Anfragen: beim Bearbeiten nur dann ein GET, wenn die Policy nicht im Cache liegt; beim Speichern genau eine
 * (POST bzw. PATCH nur mit geänderten Feldern). Nicht verwaltete Integrationen bleiben unverändert erhalten.
 */
@Composable
fun FailoverEditView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    val id = navController.rememberNavArg<String>(NavKeys.EDIT_FAILOVER_ID)
    val isEdit = id != null

    var original by remember { mutableStateOf(id?.let { i -> FailoverCache.peek(ctx)?.firstOrNull { it.id == i } }) }
    var name by remember { mutableStateOf(original?.name.orEmpty()) }
    var mode by remember { mutableStateOf(original?.mode ?: FailoverMode.AUTO_FAILOVER) }
    var linking by remember { mutableStateOf(original?.linking ?: FailoverLinking.OR) }
    var fallbackAll by remember { mutableStateOf(original?.fallbackRequiresAll ?: false) }
    var notifications by remember { mutableStateOf(original?.notificationsEnabled ?: true) }
    var accountEmail by remember { mutableStateOf(original?.hasAccountEmail() ?: true) }
    var nameError by remember { mutableStateOf<String?>(null) }

    // Formular mit der Policy füllen
    fun fill(p: FailoverPolicy) {
        original = p
        name = p.name.orEmpty()
        mode = p.mode ?: FailoverMode.AUTO_FAILOVER
        linking = p.linking ?: FailoverLinking.OR
        fallbackAll = p.fallbackRequiresAll ?: false
        notifications = p.notificationsEnabled ?: true
        accountEmail = p.hasAccountEmail()
    }

    fun loadExisting() {
        val pid = id ?: return
        req.launch(scope, retry = { loadExisting() }) {
            val p = ipv64Retrying { IPv64Client.get(ctx).failover.get(pid) }
            FailoverCache.upsert(p) { it.id == p.id }
            fill(p)
        }
    }

    LaunchedEffect(Unit) {
        if (id != null && original == null) loadExisting()
    }

    val loaded = !isEdit || original != null
    // Verknüpfung lässt sich während eines aktiven Failovers nicht ändern
    val linkingLocked = original?.let { it.status == FailoverStatus.FAILED_OVER || it.isTransitioning() } == true

    // Integrationsliste: Account-E-Mail (0) nach Schalter, alle übrigen IDs unverändert
    fun integrationList(): List<Long> {
        val others = original?.otherIntegrations().orEmpty()
        return if (accountEmail) listOf(ACCOUNT_EMAIL_INTEGRATION) + others else others
    }

    fun save() {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            nameError = "Enter a name."
            return
        }
        nameError = null
        val orig = original
        if (orig == null) {
            req.launch(scope, spinner = "Creating...") {
                val created = ipv64Retrying {
                    IPv64Client.get(ctx).failover.create(
                        CreateFailoverPolicyRequest(
                            name = trimmed,
                            mode = mode,
                            fallbackRequiresAll = fallbackAll,
                            linking = linking,
                            notificationsEnabled = notifications,
                            integrations = integrationList()
                        )
                    )
                }
                FailoverCache.upsert(created) { it.id == created.id }
                // Plan-Zähler (failoverPolicies) hat sich geändert
                AccountCache.invalidate()
                // Die Details lesen die ID vom Eintrag unter diesem Screen (Liste)
                navController.previousBackStackEntry?.savedStateHandle?.set(NavKeys.SELECTED_FAILOVER_ID, created.id)
                navController.navigate(Tabs.getRoute(Tab.failover_details)) {
                    popUpTo(Tabs.getRoute(Tab.failover_edit)) { inclusive = true }
                }
            }
            return
        }

        val newIntegrations = integrationList()
        val request = UpdateFailoverPolicyRequest(
            name = trimmed.takeIf { it != orig.name },
            mode = mode.takeIf { it != orig.mode },
            fallbackRequiresAll = fallbackAll.takeIf { it != (orig.fallbackRequiresAll ?: false) },
            linking = linking.takeIf { !linkingLocked && it != orig.linking },
            notificationsEnabled = notifications.takeIf { it != orig.notificationsEnabled },
            integrations = newIntegrations.takeIf { it.toSet() != orig.integrations.toSet() }
        )
        if (request == UpdateFailoverPolicyRequest()) {
            navController.popBackStack()
            return
        }
        val pid = orig.id ?: return
        req.launch(scope, spinner = "Saving...") {
            val updated = ipv64Retrying { IPv64Client.get(ctx).failover.update(pid, request) }
            FailoverCache.upsert(updated) { it.id == updated.id }
            navController.popBackStack()
        }
    }

    ScreenScaffold(
        title = if (isEdit) Tabs.getLabel(Tab.failover_edit) else "New failover policy",
        navController = navController,
        mainPadding = mainPadding
    ) {
        if (!loaded) {
            Box(modifier = Modifier)
        } else {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChannelFormSection("General") {
                    FormTextField(
                        label = "Name",
                        value = name,
                        onValue = { name = it; nameError = null },
                        supporting = nameError,
                    )
                }

                ChannelFormSection("Switching") {
                    FormDropdown(
                        label = "Mode",
                        value = mode,
                        options = FailoverMode.entries.toList(),
                        labelOf = { it.failoverTitle() },
                        onSelect = { mode = it }
                    )
                    Text(
                        mode.failoverDescription(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ChannelSwitchRow(
                        title = "Fall back only when all monitors are UP",
                        subtitle = "Only relevant for automatic fallback.",
                        checked = fallbackAll,
                        onCheckedChange = { fallbackAll = it },
                        enabled = mode == FailoverMode.AUTO_FAILOVER_AND_FALLBACK
                    )
                }

                ChannelFormSection("Monitors") {
                    FormDropdown(
                        label = "Monitor logic",
                        value = linking,
                        options = FailoverLinking.entries.toList(),
                        labelOf = { it.failoverTitle() },
                        onSelect = { linking = it },
                        enabled = !linkingLocked
                    )
                    Text(
                        if (linkingLocked) "The logic cannot be changed while a failover is active."
                        else "Monitors are added and removed on the policy page after saving.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ChannelFormSection("Notifications") {
                    ChannelSwitchRow(
                        title = "Send notifications",
                        checked = notifications,
                        onCheckedChange = { notifications = it }
                    )
                    ChannelSwitchRow(
                        title = "Account e-mail",
                        subtitle = "Sends an e-mail to the address of your IPv64 account.",
                        checked = accountEmail,
                        onCheckedChange = { accountEmail = it },
                        enabled = notifications
                    )
                    val others = original?.otherIntegrations()?.size ?: 0
                    if (others > 0) {
                        Text(
                            "$others classic integration" + (if (others == 1) " is" else "s are") +
                                    " also linked and stay unchanged. Manage them at ipv64.net.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = { save() },
                    enabled = !req.isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (isEdit) "Save" else "Create") }
            }
        }
    }
    RequestStateHost(req)
}
