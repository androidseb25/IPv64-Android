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
import de.rpicloud.ipv64net.helper.DomainCache
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.ipv64Request
import de.rpicloud.ipv64net.helper.v64DnsRecordTypes
import de.rpicloud.ipv64net.main.views.common.ActionSheet
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.sr.ipv64.model.RecordType

/** Neuer DNS-Record für [fqdn] als Sheet; [onCreated] schließt es und lädt die Domain neu. */
@Composable
fun DomainDnsNewSheet(fqdn: String, onDismiss: () -> Unit, onCreated: () -> Unit) {
    val ctx = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    val types = remember { String().v64DnsRecordTypes() }
    var dnsRecordType by remember { mutableStateOf(types.first()) }
    var dnsPrefix by remember { mutableStateOf("") }
    var dnsValue by remember { mutableStateOf("") }

    fun onSave() {
        keyboardController?.hide()
        // Leeres Präfix = die Domain selbst (Apex), wie beim Bearbeiten eines Records
        val prefix = dnsPrefix.trim()
        val value = dnsValue.trim()
        if (value.isEmpty()) {
            req.message("Empty field", "Please fill the DNS value field!", R.drawable.warning_24px)
            return
        }
        req.launch(scope, spinner = "Saving DNS Record...") {
            ipv64Request {
                IPv64Client.get(ctx).records.create(
                    domain = fqdn,
                    type = RecordType.valueOf(dnsRecordType),
                    content = value,
                    prefix = prefix
                )
            }
            DomainCache.invalidate(fqdn)
            onCreated()
        }
    }

    ActionSheet(
        title = "New DNS record",
        subtitle = fqdn,
        onDismiss = onDismiss,
        confirmLabel = "Create",
        confirmEnabled = dnsValue.isNotBlank() && !req.isBusy,
        onConfirm = { onSave() },
        dismissible = !req.isBusy
    ) {
        FormTextField("Name", dnsPrefix, { dnsPrefix = it }, placeholder = "dkim._domainkey", supporting = "Empty = the domain itself")
        FormDropdown("Type", dnsRecordType, types, { it }, { dnsRecordType = it })
        FormTextField("Value", dnsValue, { dnsValue = it }, placeholder = "IP or TXT content")
    }
    RequestStateHost(req)
}
