package de.rpicloud.ipv64net.main.views.network

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
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.PlanFeature
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.rememberNavArg
import de.rpicloud.ipv64net.main.views.ChannelFormSection
import de.rpicloud.ipv64net.main.views.FormDropdown
import de.rpicloud.ipv64net.main.views.FormNumberField
import de.rpicloud.ipv64net.main.views.FormSwitchRow
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.NotAvailableView
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.model.CdnAccessListMode
import de.sr.ipv64.model.CdnBackendScheme
import de.sr.ipv64.model.CdnDomainDetail
import de.sr.ipv64.model.CdnFrontend
import de.sr.ipv64.model.CdnProxyState
import de.sr.ipv64.model.UpdateCdnAccessList
import de.sr.ipv64.model.UpdateCdnCaching
import de.sr.ipv64.model.UpdateCdnDomainRequest

// Formularstand der Proxy-Einstellungen; Zahlenfelder als Text, damit leere Eingaben möglich sind
private data class CdnForm(
    val frontend: CdnFrontend,
    val scheme: CdnBackendScheme,
    val port: String,
    val websocket: Boolean,
    val cacheEnabled: Boolean,
    val cacheMinutes: String,
    val cacheSizeMb: String,
    val aclEnabled: Boolean,
    val aclMode: CdnAccessListMode,
)

private fun CdnDomainDetail.toForm(): CdnForm = CdnForm(
    frontend = proxy?.frontend ?: CdnFrontend.HTTP,
    scheme = proxy?.backend?.scheme ?: CdnBackendScheme.HTTP,
    port = proxy?.backend?.port?.toString().orEmpty(),
    websocket = proxy?.websocket ?: false,
    cacheEnabled = proxy?.caching?.enabled ?: false,
    cacheMinutes = proxy?.caching?.timeMinutes?.toString().orEmpty(),
    cacheSizeMb = proxy?.caching?.sizeMb?.toString().orEmpty(),
    aclEnabled = proxy?.accessList?.enabled ?: false,
    aclMode = proxy?.accessList?.mode ?: CdnAccessListMode.ALLOWLIST,
)

private fun CdnFrontend.hint(): String = when (this) {
    CdnFrontend.HTTP -> "Visitors connect without encryption."
    CdnFrontend.HTTPS_REDIRECT -> "HTTP requests are redirected to HTTPS. A Let's Encrypt certificate is issued."
    CdnFrontend.HTTP_AND_HTTPS -> "Both HTTP and HTTPS are served. A Let's Encrypt certificate is issued."
}

/**
 * Proxy-Einstellungen einer CDN-Domain. Anfragen: keine, wenn die Details vom Detail-Screen im [CdnDetailStore]
 * liegen (sonst ein `cdn.get`); Speichern sendet ein PATCH, das nur geänderte Felder enthält.
 */
@Composable
fun CdnSettingsView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    val token = navController.rememberNavArg<String>(NavKeys.SELECTED_CDN_TOKEN)

    var original by remember { mutableStateOf(token?.let { CdnDetailStore.get(it) }) }
    var initial by remember { mutableStateOf(original?.toForm()) }
    var form by remember { mutableStateOf(original?.toForm()) }
    var portError by remember { mutableStateOf<String?>(null) }
    var timeError by remember { mutableStateOf<String?>(null) }
    var sizeError by remember { mutableStateOf<String?>(null) }

    fun loadInitial() {
        val t = token ?: return
        req.notAvailable = null
        req.launch(scope, retry = { loadInitial() }, planGate = true) {
            val d = ipv64Retrying { IPv64Client.get(ctx).cdn.get(t) }
            CdnDetailStore.put(d)
            CdnCache.upsertCdn(d.toCdnDomain())
            original = d
            initial = d.toForm()
            form = d.toForm()
        }
    }

    LaunchedEffect(Unit) {
        if (original == null) loadInitial()
    }

    fun save() {
        val t = token ?: return
        val start = initial ?: return
        val f = form ?: return

        portError = null
        timeError = null
        sizeError = null
        // Unbekannter Port (leer) bleibt unverändert, solange nichts eingetragen wird
        val port = parsePort(f.port)
        if (port == null && (f.port.isNotBlank() || start.port.isNotBlank())) {
            portError = "Enter a port between 1 and 65535."
            return
        }
        var minutes: Int? = null
        var size: Int? = null
        if (f.cacheEnabled) {
            minutes = parsePort(f.cacheMinutes, 1..600)
            if (minutes == null) {
                timeError = "Enter 1 to 600 minutes."
                return
            }
            size = f.cacheSizeMb.trim().toIntOrNull()?.takeIf { it >= 1 }
            if (size == null) {
                sizeError = "Enter a size of at least 1 MB."
                return
            }
        }

        // Nur geänderte Felder senden
        val caching = if (f.cacheEnabled) UpdateCdnCaching(
            enabled = true.takeIf { !start.cacheEnabled },
            timeMinutes = minutes.takeIf { it != start.cacheMinutes.toIntOrNull() || !start.cacheEnabled },
            sizeMb = size.takeIf { it != start.cacheSizeMb.toIntOrNull() || !start.cacheEnabled }
        ) else UpdateCdnCaching(enabled = false.takeIf { start.cacheEnabled })
        val cachingChanged = caching != UpdateCdnCaching()

        val acl = UpdateCdnAccessList(
            enabled = f.aclEnabled.takeIf { it != start.aclEnabled },
            mode = f.aclMode.takeIf { it != start.aclMode }
        )
        val aclChanged = acl != UpdateCdnAccessList()

        val request = UpdateCdnDomainRequest(
            frontend = f.frontend.takeIf { it != start.frontend },
            backendScheme = f.scheme.takeIf { it != start.scheme },
            backendPort = port.takeIf { it != null && it != start.port.toIntOrNull() },
            websocket = f.websocket.takeIf { it != start.websocket },
            caching = caching.takeIf { cachingChanged },
            accessList = acl.takeIf { aclChanged }
        )
        if (request == UpdateCdnDomainRequest()) {
            navController.popBackStack()
            return
        }
        req.launch(scope, spinner = "Saving...") {
            val updated = ipv64Retrying { IPv64Client.get(ctx).cdn.update(t, request) }
            if (updated.id != null) {
                CdnDetailStore.put(updated)
                CdnCache.upsertCdn(updated.toCdnDomain())
            } else {
                // Antwort ohne ID: Zwischenspeicher verwerfen, damit der Detail-Screen neu lädt
                CdnDetailStore.remove(t)
                CdnCache.invalidate()
            }
            navController.popBackStack()
        }
    }

    ScreenScaffold(
        title = Tabs.getLabel(Tab.cdn_settings),
        navController = navController,
        mainPadding = mainPadding
    ) {
        val unavailable = req.notAvailable
        val f = form
        when {
            token == null -> EmptyState("CDN domain not found.")
            unavailable != null -> NotAvailableView(PlanFeature.CDN.label, unavailable)
            f == null -> Box(modifier = Modifier)
            else -> Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val proxyOn = original?.proxy?.state == CdnProxyState.ON
                Text(
                    original?.displayDomain().orEmpty(),
                    style = MaterialTheme.typography.titleMedium
                )

                ChannelFormSection("Visitors") {
                    FormDropdown(
                        label = "Frontend",
                        value = f.frontend,
                        options = CdnFrontend.entries.toList(),
                        labelOf = { it.frontendLabel() },
                        onSelect = { form = f.copy(frontend = it) }
                    )
                    Text(
                        f.frontend.hint(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ChannelFormSection("Your server") {
                    FormDropdown(
                        label = "Protocol",
                        value = f.scheme,
                        options = CdnBackendScheme.entries.toList(),
                        labelOf = { it.schemeLabel() },
                        onSelect = { form = f.copy(scheme = it) }
                    )
                    FormNumberField(
                        label = "Port",
                        value = f.port,
                        onValue = { form = f.copy(port = it); portError = null },
                        supporting = portError
                    )
                    FormSwitchRow(
                        label = "WebSocket",
                        checked = f.websocket,
                        onChange = { form = f.copy(websocket = it) },
                        supporting = if (proxyOn) "Allows WebSocket connections through the proxy."
                        else "Only available while the reverse proxy is switched on.",
                        enabled = proxyOn || f.websocket
                    )
                }

                ChannelFormSection("Cache") {
                    FormSwitchRow(
                        label = "Cache responses",
                        checked = f.cacheEnabled,
                        onChange = { form = f.copy(cacheEnabled = it) }
                    )
                    if (f.cacheEnabled) {
                        FormNumberField(
                            label = "Cache duration (minutes)",
                            value = f.cacheMinutes,
                            onValue = { form = f.copy(cacheMinutes = it); timeError = null },
                            supporting = timeError ?: "1 to 600 minutes."
                        )
                        FormNumberField(
                            label = "Cache size (MB)",
                            value = f.cacheSizeMb,
                            onValue = { form = f.copy(cacheSizeMb = it); sizeError = null },
                            supporting = sizeError ?: "At least 1 MB, up to the limit of your plan."
                        )
                    }
                }

                ChannelFormSection("Access list") {
                    FormSwitchRow(
                        label = "Use access list",
                        checked = f.aclEnabled,
                        onChange = { form = f.copy(aclEnabled = it) },
                        supporting = "Entries are managed on the CDN page."
                    )
                    if (f.aclEnabled) {
                        FormDropdown(
                            label = "Mode",
                            value = f.aclMode,
                            options = CdnAccessListMode.entries.toList(),
                            labelOf = { it.accessModeLabel() },
                            onSelect = { form = f.copy(aclMode = it) }
                        )
                    }
                }

                Button(
                    onClick = { save() },
                    enabled = !req.isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save") }
            }
        }
    }
    RequestStateHost(req)
}

private fun CdnDomainDetail.displayDomain(): String = toCdnDomain().displayName()
