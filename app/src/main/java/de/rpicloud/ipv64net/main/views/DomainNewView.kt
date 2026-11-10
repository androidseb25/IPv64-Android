package de.rpicloud.ipv64net.main.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.AccountCache
import de.rpicloud.ipv64net.helper.DomainListCache
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.IPv64Context
import de.rpicloud.ipv64net.helper.ipv64Request
import de.rpicloud.ipv64net.helper.v64domains
import de.rpicloud.ipv64net.main.views.common.ActionSheet
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.rememberRequestState

/** Neue DynDNS-Subdomain als Sheet; [onCreated] schließt es und lädt die Liste neu. */
@Composable
fun DomainNewSheet(onDismiss: () -> Unit, onCreated: () -> Unit) {
    val ctx = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    val domains = remember { String().v64domains().filter { it != "Own Domain" } }
    var domain by remember { mutableStateOf(domains.first()) }
    var subDomain by remember { mutableStateOf("") }

    fun onSave() {
        keyboardController?.hide()
        val sub = subDomain.trim().lowercase()
        if (sub.isEmpty() || sub.contains('.') || sub.any { it.isWhitespace() }) {
            req.message(
                "Invalid subdomain",
                "Enter a single label without dots or spaces, e.g. homelab01.",
                R.drawable.warning_24px
            )
            return
        }
        val newDomain = "$sub.$domain"
        req.launch(scope, spinner = "Saving Domain...", context = IPv64Context.Create) {
            ipv64Request { IPv64Client.get(ctx).domains.create(newDomain) }
            // Gemeinsame Domainliste (Pickers) beim nächsten Zugriff neu laden
            DomainListCache.invalidate()
            // Domainzähler in Me.usage haben sich geändert
            AccountCache.invalidate()
            onCreated()
        }
    }

    ActionSheet(
        title = "New domain",
        onDismiss = onDismiss,
        confirmLabel = "Create",
        confirmEnabled = subDomain.isNotBlank() && !req.isBusy,
        onConfirm = { onSave() },
        dismissible = !req.isBusy
    ) {
        FormTextField("Subdomain", subDomain, { subDomain = it }, placeholder = "homelab01")
        FormDropdown("Domain", domain, domains, { it }, { domain = it })
    }
    RequestStateHost(req)
}
