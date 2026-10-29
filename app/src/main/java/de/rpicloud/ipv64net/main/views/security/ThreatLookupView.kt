package de.rpicloud.ipv64net.main.views.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.myIp
import de.rpicloud.ipv64net.helper.openUrl
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.SectionHeader
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64ApiException
import de.sr.ipv64.model.IpFamily
import de.sr.ipv64.model.ThreatIp
import de.sr.ipv64.model.ThreatIpStatus

/** IP-Auskunft: Warum steht eine IP auf der Blocklist? Eine Anfrage je Abfrage (plus eine für "My IP"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThreatLookupView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()

    // Übergebene IP einmal lesen und aus dem Vorgänger entfernen, damit sie nicht bei einem späteren
    // Aufruf ohne Argument erneut abgefragt wird
    val initialIp = rememberSaveable {
        val handle = navController.previousBackStackEntry?.savedStateHandle
        val value = handle?.get<String>(NavKeys.THREAT_LOOKUP_IP)
        handle?.remove<String>(NavKeys.THREAT_LOOKUP_IP)
        value.orEmpty()
    }
    var input by rememberSaveable { mutableStateOf(initialIp) }
    var autoStarted by rememberSaveable { mutableStateOf(false) }
    var fieldError by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ThreatIp?>(null) }

    fun lookup(raw: String) {
        val ip = normalizeIpInput(raw)
        if (!isValidIp(ip)) {
            fieldError = "Enter a valid IPv4 or IPv6 address."
            return
        }
        fieldError = null
        result = null
        input = ip
        req.launch(
            scope,
            spinner = "Looking up...",
            retry = { lookup(ip) },
            onError = { e ->
                // 422: ungültige oder nicht öffentliche Adresse → Feldfehler statt Dialog
                if (e is IPv64ApiException && e.isValidationError) {
                    fieldError = e.detail ?: "This is not a valid public IP address."
                    result = null
                    true
                } else false
            }
        ) {
            val client = IPv64Client.get(ctx)
            result = ipv64Retrying { client.threat.ip(ip) }
        }
    }

    fun lookupMyIp(family: IpFamily) {
        req.launch(scope, spinner = "Detecting your IP...") {
            val ip = myIp(family)
            if (ip == null) {
                val name = if (family == IpFamily.IPV6) "IPv6" else "IPv4"
                req.message("IP not available", "Your public $name address could not be determined. Your connection may not support $name.")
            } else {
                lookup(ip)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!autoStarted && initialIp.isNotBlank()) {
            autoStarted = true
            lookup(initialIp)
        }
    }

    ScreenScaffold(Tabs.getLabel(Tab.threat_lookup), navController, mainPadding) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; fieldError = null },
                label = { Text("IP address") },
                singleLine = true,
                isError = fieldError != null,
                supportingText = fieldError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { lookup(input) }),
                modifier = Modifier.fillMaxWidth()
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AssistChip(onClick = { lookupMyIp(IpFamily.IPV4) }, label = { Text("My IPv4") }, enabled = !req.isBusy)
                AssistChip(onClick = { lookupMyIp(IpFamily.IPV6) }, label = { Text("My IPv6") }, enabled = !req.isBusy)
            }
            Button(onClick = { lookup(input) }, enabled = input.isNotBlank() && !req.isBusy) { Text("Look up") }

            result?.let { ThreatIpResult(it) }
        }
    }
    RequestStateHost(req)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThreatIpResult(r: ThreatIp) {
    val ctx = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    SecurityCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                r.ip ?: "–",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            StatusChip(r.status.label(r.listed), r.status.color(r.listed))
        }
        r.family?.let { KeyValueLine("Family", "IPv$it") }
        r.score?.let { score ->
            val value = score.coerceIn(0, 100)
            KeyValueLine("Score", "$value / 100")
            LinearProgressIndicator(progress = { value / 100f }, modifier = Modifier.fillMaxWidth())
        }
        r.accounts?.let { KeyValueLine("Reporting accounts", it.formatCount()) }
        r.country?.let { KeyValueLine("Country", it) }
        r.asn?.let { KeyValueLine("ASN", "AS$it") }
        r.firstSeen?.let { KeyValueLine("First seen", it) }
        r.lastSeen?.let { KeyValueLine("Last seen", it) }
        r.release?.let { rel ->
            rel.status?.let { KeyValueLine("Release", it.label()) }
            rel.protectedUntil?.let { KeyValueLine("Release valid until", it.formatGermanTime()) }
        }
    }

    if (r.lists.isNotEmpty()) {
        SectionHeader("Lists")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            r.lists.forEach { SuggestionChip(onClick = {}, label = { Text(it) }) }
        }
    }
    if (r.kinds.isNotEmpty()) {
        SectionHeader("Attack kinds")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            r.kinds.forEach { SuggestionChip(onClick = {}, label = { Text(it) }) }
        }
    }
    if (r.ports.isNotEmpty()) {
        SectionHeader("Attacked ports")
        SecurityCard {
            r.ports.forEach { p ->
                KeyValueLine(p.service ?: "–", p.port?.toString() ?: "–", monospace = true)
            }
            Text(
                "Only ports reported by at least two accounts are shown.",
                style = MaterialTheme.typography.bodySmall,
                color = muted
            )
        }
    }
    if (r.listed != true && r.status == ThreatIpStatus.NOT_LISTED) {
        Text("No further details are available for IPs that are not listed.", style = MaterialTheme.typography.bodySmall, color = muted)
    }
    // Nur https-Links des Servers öffnen
    r.details?.takeIf { it.startsWith("https://") }?.let { url ->
        OutlinedButton(onClick = { openUrl(ctx, url) }) { Text("Explanation and release request") }
    }
}
