package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.DomainCache
import de.rpicloud.ipv64net.helper.DomainListCache
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.rememberNavArg
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.rpicloud.ipv64net.models.label
import de.sr.ipv64.model.DomainDetail
import de.sr.ipv64.model.IgnoreFamily

// Plausibilitätsprüfung für ein IPv6-Präfix wie 2001:db8:1234:5600::/56 (leer = aus)
private fun ipv6PrefixError(s: String): String? {
    val t = s.trim()
    if (t.isEmpty()) return null
    val addr = t.substringBefore('/')
    val len = t.substringAfter('/', "")
    val addrOk = addr.contains(':') && addr.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' }
    val lenOk = len.isEmpty() || (len.toIntOrNull()?.let { it in 1..128 } ?: false)
    return if (addrOk && lenOk) null else "Expected e.g. 2001:db8:1234:5600::/56"
}

/**
 * Domain-Einstellungen: Wildcard, ignorierte Adressfamilie bei DynDNS-Updates und festes IPv6-Präfix.
 * Lädt zuerst aus dem DomainCache (sonst 1 Anfrage), speichert nur geänderte Felder und legt das Ergebnis
 * wieder im DomainCache ab.
 */
@Composable
fun DomainSettingsView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    val keyboard = LocalSoftwareKeyboardController.current
    val domain = navController.rememberNavArg<String>(NavKeys.EDIT_DOMAIN_NAME).orEmpty()

    var original by remember { mutableStateOf<DomainDetail?>(null) }
    var wildcard by rememberSaveable { mutableStateOf(false) }
    var ignore by rememberSaveable { mutableStateOf(IgnoreFamily.NONE) }
    var ipv6Prefix by rememberSaveable { mutableStateOf("") }
    var initialized by rememberSaveable { mutableStateOf(false) }

    fun apply(detail: DomainDetail) {
        original = detail
        if (!initialized) {
            wildcard = detail.wildcard == true
            ignore = detail.ignore ?: IgnoreFamily.NONE
            ipv6Prefix = detail.ipv6Prefix.orEmpty()
            initialized = true
        }
    }

    fun load() {
        if (domain.isEmpty()) return
        DomainCache.get(domain)?.let { apply(it); return }
        req.launch(scope, retry = { load() }) {
            val detail = ipv64Retrying { IPv64Client.get(ctx).domains.get(domain) }
            DomainCache.put(domain, detail)
            apply(detail)
        }
    }

    val prefixError = ipv6PrefixError(ipv6Prefix)

    fun save() {
        keyboard?.hide()
        val orig = original ?: return
        if (prefixError != null) return
        val newWildcard = wildcard.takeIf { it != (orig.wildcard == true) }
        val newIgnore = ignore.takeIf { it != (orig.ignore ?: IgnoreFamily.NONE) }
        val newPrefix = ipv6Prefix.trim().takeIf { it != orig.ipv6Prefix.orEmpty() }
        if (newWildcard == null && newIgnore == null && newPrefix == null) {
            navController.popBackStack()
            return
        }
        req.launch(scope, spinner = "Saving...") {
            val result = ipv64Retrying {
                IPv64Client.get(ctx).domains.update(domain, wildcard = newWildcard, ignore = newIgnore, ipv6Prefix = newPrefix)
            }
            // Falls die Antwort keine Records enthält, die bekannten behalten
            val merged = if (result.records.isEmpty() && orig.records.isNotEmpty()) result.copy(records = orig.records) else result
            DomainCache.put(domain, merged)
            DomainListCache.invalidate()
            navController.popBackStack()
        }
    }

    LaunchedEffect(Unit) { load() }

    ScreenScaffold(
        title = Tabs.getLabel(Tab.domain_settings),
        navController = navController,
        mainPadding = mainPadding,
        actions = {
            IconButton(onClick = { save() }, enabled = original != null && prefixError == null && !req.isBusy) {
                Icon(painter = painterResource(id = R.drawable.save_24px), contentDescription = "Save")
            }
        }
    ) {
        if (domain.isEmpty()) {
            EmptyState("No domain selected.")
        } else LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(domain, style = MaterialTheme.typography.titleLarge)
            }
            if (original != null) {
                item {
                    ChannelFormSection("Wildcard") {
                        FormSwitchRow(
                            label = "Wildcard records",
                            checked = wildcard,
                            onChange = { wildcard = it },
                            supporting = "Also resolve *.$domain to the records of the domain."
                        )
                    }
                }
                item {
                    ChannelFormSection("DynDNS updates") {
                        FormDropdown(
                            label = "Ignored address family",
                            value = ignore,
                            options = IgnoreFamily.entries.toList(),
                            labelOf = { it.label },
                            onSelect = { ignore = it }
                        )
                        Text(
                            "Updates of the ignored family are not applied (e.g. ignore IPv6 if your router " +
                                    "reports an unwanted address).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    ChannelFormSection("IPv6 prefix") {
                        FormTextField(
                            label = "Fixed IPv6 prefix",
                            value = ipv6Prefix,
                            onValue = { ipv6Prefix = it.trim() },
                            placeholder = "2001:db8:1234:5600::/56",
                            supporting = prefixError ?: "Combined with the interface IDs of your records. Empty = off."
                        )
                    }
                }
            }
        }
    }

    RequestStateHost(req)
}
