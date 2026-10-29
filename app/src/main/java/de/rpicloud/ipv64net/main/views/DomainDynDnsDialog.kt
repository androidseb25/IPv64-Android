package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.DomainCache
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.IPv64Context
import de.rpicloud.ipv64net.helper.ipv64Request
import de.rpicloud.ipv64net.helper.myIp
import de.rpicloud.ipv64net.main.views.common.RequestState
import de.rpicloud.ipv64net.models.isFailure
import de.rpicloud.ipv64net.models.label
import de.rpicloud.ipv64net.models.looksLikeIpv4
import de.rpicloud.ipv64net.models.looksLikeIpv6
import de.sr.ipv64.model.DynDnsResult
import de.sr.ipv64.model.IpFamily
import kotlinx.coroutines.launch

/**
 * Manuelles DynDNS-Update einer Domain: IPv4, IPv6 und Präfix eingeben (oder "Use my IP" übernehmen),
 * danach die Ergebnisse pro IP anzeigen.
 *
 * Fehler laufen über [req] mit IPv64Context.Update (429 → Cooldown-Hinweis).
 * [onClose] erhält true, wenn das Update durchgeführt wurde (die Detailansicht lädt dann neu).
 * Leere IP-Felder werden nicht gesendet; sind beide leer, verwendet der Server die Absenderadresse.
 */
@Composable
fun DomainDynDnsDialog(domain: String, req: RequestState, onClose: (updated: Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ipv4 by remember { mutableStateOf("") }
    var ipv6 by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("") }
    var loadingV4 by remember { mutableStateOf(false) }
    var loadingV6 by remember { mutableStateOf(false) }
    var myIpHint by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<DynDnsResult?>(null) }

    val v4Error = ipv4.isNotBlank() && !looksLikeIpv4(ipv4)
    val v6Error = ipv6.isNotBlank() && !looksLikeIpv6(ipv6)
    val busy = req.isBusy

    fun useMyIp(family: IpFamily) {
        myIpHint = null
        if (family == IpFamily.IPV4) loadingV4 = true else loadingV6 = true
        scope.launch {
            try {
                val ip = myIp(family)
                if (ip == null) {
                    myIpHint = if (family == IpFamily.IPV4) "No public IPv4 address found." else "No public IPv6 address found."
                } else if (family == IpFamily.IPV4) ipv4 = ip else ipv6 = ip
            } finally {
                if (family == IpFamily.IPV4) loadingV4 = false else loadingV6 = false
            }
        }
    }

    fun submit() {
        req.launch(scope, spinner = "Updating...", context = IPv64Context.Update) {
            val r = ipv64Request {
                IPv64Client.get(ctx).dynDns.update(
                    domain,
                    ipv4 = ipv4.trim().ifEmpty { null },
                    ipv6 = ipv6.trim().ifEmpty { null },
                    prefix = prefix.trim().ifEmpty { null }
                )
            }
            DomainCache.invalidate(domain)
            result = r
        }
    }

    val done = result
    if (done != null) {
        // Ergebnisansicht
        AlertDialog(
            onDismissRequest = { onClose(true) },
            icon = { Icon(painter = painterResource(id = R.drawable.autorenew_24px), contentDescription = null) },
            title = { Text("DynDNS update") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (done.changed == true) "At least one record was changed." else "No record was changed.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (done.results.isEmpty()) {
                        Text("The server returned no results per IP.", style = MaterialTheme.typography.bodySmall)
                    }
                    done.results.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (r.type ?: "?") + "  ",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(r.ip ?: "–", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    r.result.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (r.result.isFailure) colorResource(R.color.ipv64_red)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onClose(true) }) { Text("Done") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose(false) },
        icon = { Icon(painter = painterResource(id = R.drawable.autorenew_24px), contentDescription = null) },
        title = { Text("Manual DynDNS update") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Sets the A/AAAA records of $domain. Leave both addresses empty to use the address the request " +
                            "comes from. Only public addresses are accepted; missing records are created.",
                    style = MaterialTheme.typography.bodySmall
                )
                FormTextField(
                    label = "IPv4 address",
                    value = ipv4,
                    onValue = { ipv4 = it.trim() },
                    placeholder = "e.g. 203.0.113.10",
                    supporting = if (v4Error) "Not a valid IPv4 address" else null,
                    keyboardType = KeyboardType.Uri
                )
                FormTextField(
                    label = "IPv6 address",
                    value = ipv6,
                    onValue = { ipv6 = it.trim() },
                    placeholder = "e.g. 2001:db8::10",
                    supporting = if (v6Error) "Not a valid IPv6 address" else null,
                    keyboardType = KeyboardType.Uri
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { useMyIp(IpFamily.IPV4) },
                        enabled = !loadingV4,
                        label = { Text("My IPv4") },
                        leadingIcon = {
                            if (loadingV4) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    )
                    AssistChip(
                        onClick = { useMyIp(IpFamily.IPV6) },
                        enabled = !loadingV6,
                        label = { Text("My IPv6") },
                        leadingIcon = {
                            if (loadingV6) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    )
                }
                myIpHint?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colorResource(R.color.ipv64_orange))
                }
                FormTextField(
                    label = "Prefix (optional)",
                    value = prefix,
                    onValue = { prefix = it.trim() },
                    placeholder = "e.g. nas",
                    supporting = "Empty = the domain itself"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = !busy && !v4Error && !v6Error) { Text("Update") }
        },
        dismissButton = {
            TextButton(onClick = { onClose(false) }, enabled = !busy) { Text("Cancel") }
        },
        modifier = Modifier.fillMaxWidth()
    )
}
