package de.rpicloud.ipv64net.main.views.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.myIp
import de.rpicloud.ipv64net.helper.openUrl
import de.rpicloud.ipv64net.main.views.common.GroupDivider
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.SearchField
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.main.views.common.InfoItem
import de.rpicloud.ipv64net.main.views.common.InfoRows
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64ApiException
import kotlinx.coroutines.launch
import de.sr.ipv64.model.IpFamily
import de.sr.ipv64.model.ThreatIp
import de.sr.ipv64.model.ThreatIpStatus

/** IP-Auskunft: Warum steht eine IP auf der Blocklist? Eine Anfrage je Abfrage (plus je eine Abfrage für die eigene IPv4 und IPv6). */
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

    // Eigene IPs wie in MyIpView: unabhängig voneinander laden, null = nicht verfügbar
    var myV4 by remember { mutableStateOf<String?>(null) }
    var myV6 by remember { mutableStateOf<String?>(null) }
    var v4Loading by remember { mutableStateOf(true) }
    var v6Loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        launch { myV4 = myIp(IpFamily.IPV4); v4Loading = false }
        launch { myV6 = myIp(IpFamily.IPV6); v6Loading = false }
        if (!autoStarted && initialIp.isNotBlank()) {
            autoStarted = true
            lookup(initialIp)
        }
    }

    ScreenScaffold("IP lookup & My IP", navController, mainPadding) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ListGroup("Your public IP") {
                MyIpRow("IPv4", myV4, v4Loading, !req.isBusy) { lookup(it) }
                GroupDivider()
                MyIpRow("IPv6", myV6, v6Loading, !req.isBusy) { lookup(it) }
            }
            Spacer(Modifier.height(8.dp)) // zusätzlich zum 8-dp-Abstand: 16 dp über dem Suchfeld
            SearchField(
                value = input,
                onValueChange = { input = it; fieldError = null },
                placeholder = "IP address",
                onSearch = { if (!req.isBusy) lookup(input) },
                isError = fieldError != null,
                supportingText = fieldError,
                keyboardType = KeyboardType.Uri,
                submitContentDescription = "Look up"
            )

            result?.let { ThreatIpResult(it) }
        }
    }
    RequestStateHost(req)
}

@Composable
private fun MyIpRow(label: String, ip: String?, loading: Boolean, enabled: Boolean, onLookup: (String) -> Unit) {
    ListRow(
        title = ip ?: if (loading) "Loading..." else "Not available",
        subtitle = label,
        monospaceTitle = ip != null,
        showChevron = false,
        trailing = { if (ip != null) OutlinedButton(onClick = { onLookup(ip) }, enabled = enabled) { Text("Look up") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThreatIpResult(r: ThreatIp) {
    val ctx = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val facts = listOfNotNull(
        r.family?.let { InfoItem("Family", "IPv$it") },
        r.score?.let { InfoItem("Score", "${it.coerceIn(0, 100)} / 100") },
        r.accounts?.let { InfoItem("Reporting accounts", it.formatCount()) },
        r.country?.let { InfoItem("Country", it.countryFlag()) },
        r.asn?.let { InfoItem("ASN", "AS$it") },
        r.firstSeen?.let { InfoItem("First seen", it) },
        r.lastSeen?.let { InfoItem("Last seen", it) },
        r.release?.status?.let { InfoItem("Release", it.label()) },
        r.release?.protectedUntil?.let { InfoItem("Release valid until", it.formatGermanTime()) }
    )
    ListGroup("Result") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                r.ip ?: "–",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            StatusChip(r.status.label(r.listed), r.status.color(r.listed))
        }
        r.score?.let { score ->
            LinearProgressIndicator(
                progress = { score.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )
        }
        if (facts.isNotEmpty()) {
            GroupDivider()
            InfoRows(facts)
        }
    }

    if (r.lists.isNotEmpty()) {
        ListGroup("Lists") {
            FlowRow(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) { r.lists.forEach { SuggestionChip(onClick = {}, label = { Text(it) }) } }
        }
    }
    if (r.kinds.isNotEmpty()) {
        ListGroup("Attack kinds") {
            FlowRow(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) { r.kinds.forEach { SuggestionChip(onClick = {}, label = { Text(it) }) } }
        }
    }
    if (r.ports.isNotEmpty()) {
        ListGroup("Attacked ports") {
            InfoRows(r.ports.map { InfoItem(it.service ?: "–", it.port?.toString() ?: "–", monospace = true) })
            GroupDivider()
            Text(
                "Only ports reported by at least two accounts are shown.",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(16.dp)
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
