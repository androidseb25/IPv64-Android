package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.R
import de.sr.ipv64.model.Hc2CheckParams
import de.sr.ipv64.model.Hc2CheckType
import de.sr.ipv64.model.Hc2DnsRecordType
import de.sr.ipv64.model.Hc2DnsResolver
import de.sr.ipv64.model.Hc2JsonOp
import de.sr.ipv64.model.Hc2JsonRule

// ---------------------------------------------------------------------------------------------
// Zustand der typabhängigen Parameter (alles als Text, damit Eingaben unverändert bleiben)
// ---------------------------------------------------------------------------------------------

data class HeaderRow(val key: String = "", val value: String = "")

data class JsonRuleRow(val path: String = "", val op: Hc2JsonOp = Hc2JsonOp.EQ, val value: String = "")

// Einheiten für Zeitdauer-Felder
val DURATION_UNITS: List<Pair<String, Int>> = listOf(
    "seconds" to 1,
    "minutes" to 60,
    "hours" to 3600,
    "days" to 86400,
)

// Sekunden → (Wert, Einheiten-Index) mit der größten Einheit, die ohne Rest teilt
fun secondsToDuration(seconds: Int?): Pair<String, Int> {
    if (seconds == null || seconds <= 0) return "" to 1
    for (idx in DURATION_UNITS.indices.reversed()) {
        val size = DURATION_UNITS[idx].second
        if (seconds % size == 0) return (seconds / size).toString() to idx
    }
    return seconds.toString() to 0
}

// Leer → null (Serverstandard), ungültig → -1
fun durationToSeconds(value: String, unitIdx: Int): Int? {
    if (value.isBlank()) return null
    val n = value.trim().toLongOrNull() ?: return -1
    val total = n * DURATION_UNITS[unitIdx.coerceIn(DURATION_UNITS.indices)].second
    return if (total in 0..Int.MAX_VALUE) total.toInt() else -1
}

data class ParamsState(
    // http / keyword
    val method: String = "GET",
    val acceptStatus: String = "",
    val followRedirects: Boolean = true,
    val maxRedirects: String = "",
    val keyword: String = "",
    val keywordAbsent: Boolean = false,
    val basicUser: String = "",
    val basicPassword: String = "",
    val headers: List<HeaderRow> = emptyList(),
    val body: String = "",
    val insecureTls: Boolean = false,
    val certMinDays: String = "",
    // json
    val jsonRules: List<JsonRuleRow> = emptyList(),
    val jsonAny: Boolean = false,
    // dns
    val recordType: Hc2DnsRecordType = Hc2DnsRecordType.A,
    val resolver: Hc2DnsResolver = Hc2DnsResolver.SYSTEM,
    val server: String = "",
    val values: String = "",
    val dnssec: Boolean = false,
    val minTtl: String = "",
    // dns / tcp / port
    val expect: String = "",
    // icmp
    val count: String = "",
    val maxLossPercent: String = "",
    val maxLatencyMs: String = "",
    // tcp / port / cert
    val port: String = "",
    val send: String = "",
    val tls: Boolean = false,
    // cert
    val minDays: String = "",
    val serverName: String = "",
    val skipChain: Boolean = false,
    val skipHostname: Boolean = false,
    // push
    val periodValue: String = "",
    val periodUnit: Int = 1,
    val graceValue: String = "",
    val graceUnit: Int = 1,
) {
    companion object {
        fun initial(type: Hc2CheckType): ParamsState = when (type) {
            Hc2CheckType.DNS -> ParamsState(expect = "any")
            Hc2CheckType.PORT -> ParamsState(expect = "open")
            else -> ParamsState()
        }

        // Geladene Parameter in den Formularzustand übernehmen; Passwörter kommen nie zurück ins Formular
        fun from(type: Hc2CheckType, p: Hc2CheckParams?): ParamsState {
            val base = initial(type)
            if (p == null) return base
            val (pv, pu) = secondsToDuration(p.periodSeconds)
            val (gv, gu) = secondsToDuration(p.graceSeconds)
            return base.copy(
                method = p.method?.takeIf { it.isNotBlank() } ?: base.method,
                acceptStatus = p.acceptStatus.orEmpty(),
                followRedirects = p.followRedirects ?: base.followRedirects,
                maxRedirects = p.maxRedirects?.toString().orEmpty(),
                keyword = p.keyword.orEmpty(),
                keywordAbsent = p.keywordAbsent ?: false,
                basicUser = p.basicUser.orEmpty(),
                basicPassword = "",
                headers = p.headers?.map { HeaderRow(it.key, it.value) }.orEmpty(),
                body = p.body.orEmpty(),
                insecureTls = p.insecureTls ?: false,
                certMinDays = p.certMinDays?.toString().orEmpty(),
                jsonRules = p.jsonRules?.map { JsonRuleRow(it.path, it.op, it.value.orEmpty()) }.orEmpty(),
                jsonAny = p.jsonAny ?: false,
                recordType = p.recordType ?: base.recordType,
                resolver = p.resolver ?: base.resolver,
                server = p.server.orEmpty(),
                values = p.values?.joinToString("\n").orEmpty(),
                dnssec = p.dnssec ?: false,
                minTtl = p.minTtl?.toString().orEmpty(),
                expect = p.expect?.takeIf { it.isNotBlank() } ?: base.expect,
                count = p.count?.toString().orEmpty(),
                maxLossPercent = p.maxLossPercent?.toString().orEmpty(),
                maxLatencyMs = p.maxLatencyMs?.toString().orEmpty(),
                port = p.port?.toString().orEmpty(),
                send = p.send.orEmpty(),
                tls = p.tls ?: false,
                minDays = p.minDays?.toString().orEmpty(),
                serverName = p.serverName.orEmpty(),
                skipChain = p.skipChain ?: false,
                skipHostname = p.skipHostname ?: false,
                periodValue = pv,
                periodUnit = pu,
                graceValue = gv,
                graceUnit = gu,
            )
        }
    }
}

// Text: nicht leer → Wert; sonst "" wenn vorher etwas drin stand (Löschen), sonst nicht senden
private fun txt(cur: String, ini: String): String? =
    if (cur.isNotBlank()) cur.trim() else if (ini.isNotBlank()) "" else null

private fun int(s: String): Int? = s.trim().toIntOrNull()

// Parameter für die Anfrage. Passwort und Header werden nur gesendet, wenn sie geändert wurden.
fun ParamsState.toParams(type: Hc2CheckType, ini: ParamsState): Hc2CheckParams? {
    return when (type) {
        Hc2CheckType.HTTP, Hc2CheckType.KEYWORD, Hc2CheckType.JSON -> Hc2CheckParams(
            method = method.trim().ifBlank { null },
            acceptStatus = txt(acceptStatus, ini.acceptStatus),
            followRedirects = followRedirects,
            maxRedirects = int(maxRedirects),
            keyword = if (type == Hc2CheckType.KEYWORD) keyword.trim() else null,
            keywordAbsent = if (type == Hc2CheckType.KEYWORD) keywordAbsent else null,
            basicUser = txt(basicUser, ini.basicUser),
            basicPassword = basicPassword.ifEmpty { null },
            headers = headersForRequest(ini),
            body = txt(body, ini.body),
            insecureTls = insecureTls,
            certMinDays = int(certMinDays),
            jsonRules = if (type == Hc2CheckType.JSON) jsonRules.map {
                Hc2JsonRule(
                    it.path.trim(), it.op,
                    if (it.op.needsValue()) it.value else null
                )
            } else null,
            jsonAny = if (type == Hc2CheckType.JSON) jsonAny else null,
        )

        Hc2CheckType.DNS -> Hc2CheckParams(
            recordType = recordType,
            resolver = resolver,
            server = if (resolver == Hc2DnsResolver.SERVER) server.trim() else null,
            expect = expect.ifBlank { null },
            values = if (expect == "contains" || expect == "equals" || expect == "regex")
                values.lines().map { it.trim() }.filter { it.isNotEmpty() } else null,
            dnssec = dnssec,
            minTtl = int(minTtl),
        )

        Hc2CheckType.ICMP -> Hc2CheckParams(
            count = int(count),
            maxLossPercent = int(maxLossPercent),
            maxLatencyMs = int(maxLatencyMs),
        )

        Hc2CheckType.TCP -> Hc2CheckParams(
            port = int(port),
            send = txt(send, ini.send),
            expect = txt(expect, ini.expect),
            tls = tls,
        )

        Hc2CheckType.PORT -> Hc2CheckParams(
            port = int(port),
            expect = expect.ifBlank { null },
        )

        Hc2CheckType.CERT -> Hc2CheckParams(
            port = int(port),
            minDays = int(minDays),
            serverName = txt(serverName, ini.serverName),
            skipChain = skipChain,
            skipHostname = skipHostname,
        )

        Hc2CheckType.PUSH -> Hc2CheckParams(
            periodSeconds = durationToSeconds(periodValue, periodUnit)?.takeIf { it >= 0 },
            graceSeconds = durationToSeconds(graceValue, graceUnit)?.takeIf { it >= 0 },
        )

        Hc2CheckType.WHOIS, Hc2CheckType.DOCKER -> null
    }
}

// Immer die komplette Header-Map senden (PATCH könnte params ersetzen). Unveränderte Werte, die wie
// maskierte Platzhalter aussehen, werden weggelassen, damit nie ein Platzhalter als echter Wert gespeichert wird.
private fun ParamsState.headersForRequest(ini: ParamsState): Map<String, String>? {
    if (headers.isEmpty() && ini.headers.isEmpty()) return null
    val old = ini.headers.toHeaderMap()
    return headers.toHeaderMap().filter { (k, v) -> !(old[k] == v && v.looksMasked()) }
}

private fun String.looksMasked(): Boolean =
    startsWith("…") || length >= 3 && (all { it == '*' || it == '•' || it == '●' } || equals("<redacted>", true) || equals("[redacted]", true))

private fun List<HeaderRow>.toHeaderMap(): Map<String, String> =
    filter { it.key.isNotBlank() }.associate { it.key.trim() to it.value }

fun Hc2JsonOp.needsValue(): Boolean = this != Hc2JsonOp.EXISTS && this != Hc2JsonOp.NOT_EXISTS

private fun inRange(s: String, range: IntRange): Boolean = s.isBlank() || (int(s)?.let { it in range } ?: false)

// Fehlertext oder null
fun ParamsState.validate(type: Hc2CheckType, target: String): String? {
    when (type) {
        Hc2CheckType.HTTP, Hc2CheckType.KEYWORD, Hc2CheckType.JSON -> {
            if (!inRange(maxRedirects, 0..50)) return "Max redirects must be a number between 0 and 50."
            if (!inRange(certMinDays, 0..3650)) return "Certificate minimum days must be between 0 and 3650."
            if (headers.any { it.key.isBlank() && it.value.isNotBlank() }) return "Every header needs a name."
            if (type == Hc2CheckType.KEYWORD && keyword.isBlank()) return "Please enter a keyword."
            if (type == Hc2CheckType.JSON) {
                if (jsonRules.isEmpty()) return "Add at least one JSON rule."
                if (jsonRules.size > 10) return "At most 10 JSON rules are allowed."
                if (jsonRules.any { it.path.isBlank() }) return "Every JSON rule needs a path."
                if (jsonRules.any { it.op.needsValue() && it.value.isBlank() }) return "Every JSON rule except exists / not exists needs a value."
            }
        }

        Hc2CheckType.DNS -> {
            if (resolver == Hc2DnsResolver.SERVER && server.isBlank()) return "Please enter a DNS server."
            if (!inRange(minTtl, 0..Int.MAX_VALUE)) return "Minimum TTL must be a number of 0 or more."
            if ((expect == "contains" || expect == "equals" || expect == "regex") && values.isBlank())
                return "Please enter the expected value(s)."
        }

        Hc2CheckType.ICMP -> {
            if (!inRange(count, 1..20)) return "Packet count must be between 1 and 20."
            if (!inRange(maxLossPercent, 0..100)) return "Max packet loss must be between 0 and 100."
            if (!inRange(maxLatencyMs, 0..Int.MAX_VALUE)) return "Max latency must be a number of 0 or more."
        }

        Hc2CheckType.TCP, Hc2CheckType.PORT -> {
            if (!inRange(port, 1..65535)) return "Port must be between 1 and 65535."
            if (port.isBlank() && !target.contains(":")) return "Please enter a port or use host:port as target."
        }

        Hc2CheckType.CERT -> {
            if (!inRange(port, 1..65535)) return "Port must be between 1 and 65535."
            if (!inRange(minDays, 0..3650)) return "Minimum days must be between 0 and 3650."
        }

        Hc2CheckType.PUSH -> {
            if (durationToSeconds(periodValue, periodUnit) == -1) return "Expected interval is not a valid number."
            if (durationToSeconds(graceValue, graceUnit) == -1) return "Grace period is not a valid number."
        }

        Hc2CheckType.WHOIS, Hc2CheckType.DOCKER -> {}
    }
    return null
}

// ---------------------------------------------------------------------------------------------
// Gemeinsame Formular-Bausteine
// ---------------------------------------------------------------------------------------------

@Composable
internal fun FormSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
internal fun FormSwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
internal fun FormTextField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supporting: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
    secret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = if (secret) KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
        else KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        modifier = modifier.fillMaxWidth()
    )
}

// Zahlenfeld; leer = Serverstandard
@Composable
internal fun FormNumberField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    FormTextField(
        label = label,
        value = value,
        onValue = { s -> onValue(s.filter { it.isDigit() || (decimal && it == '.') }) },
        modifier = modifier,
        supporting = supporting,
        keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        enabled = enabled
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> FormDropdown(
    label: String,
    value: T,
    options: List<T>,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    optionEnabled: (T) -> Boolean = { true },
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = labelOf(value),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            textStyle = MaterialTheme.typography.bodyMedium,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = enabled)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelOf(option)) },
                    enabled = optionEnabled(option),
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

// Zeitdauer mit Einheit; leer = Serverstandard
@Composable
internal fun DurationField(
    label: String,
    value: String,
    unitIdx: Int,
    onValue: (String) -> Unit,
    onUnit: (Int) -> Unit,
    supporting: String? = null,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        FormNumberField(
            label = label, value = value, onValue = onValue,
            modifier = Modifier.weight(1f), supporting = supporting
        )
        FormDropdown(
            label = "Unit",
            value = unitIdx,
            options = DURATION_UNITS.indices.toList(),
            labelOf = { DURATION_UNITS[it].first },
            onSelect = onUnit,
            modifier = Modifier.width(140.dp)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Editoren pro Check-Typ
// ---------------------------------------------------------------------------------------------

private val HTTP_METHODS = listOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")

@Composable
fun HealthcheckParamsEditor(
    type: Hc2CheckType,
    state: ParamsState,
    onChange: (ParamsState) -> Unit,
    online: Boolean,
    isEdit: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (type) {
            Hc2CheckType.HTTP, Hc2CheckType.KEYWORD, Hc2CheckType.JSON -> HttpParams(type, state, onChange, isEdit)
            Hc2CheckType.DNS -> DnsParams(state, onChange, online)
            Hc2CheckType.ICMP -> {
                FormNumberField("Packets (1-20)", state.count, { onChange(state.copy(count = it)) })
                FormNumberField(
                    "Max packet loss (%)", state.maxLossPercent,
                    { onChange(state.copy(maxLossPercent = it)) })
                FormNumberField(
                    "Max latency (ms)", state.maxLatencyMs, { onChange(state.copy(maxLatencyMs = it)) },
                    supporting = "0 = off"
                )
            }

            Hc2CheckType.TCP -> {
                FormNumberField(
                    "Port", state.port, { onChange(state.copy(port = it)) },
                    supporting = "Or use host:port as target"
                )
                FormTextField("Data to send", state.send, { onChange(state.copy(send = it)) })
                FormTextField(
                    "Expected response start", state.expect, { onChange(state.copy(expect = it)) }
                )
                FormSwitchRow("Use TLS", state.tls, { onChange(state.copy(tls = it)) })
            }

            Hc2CheckType.PORT -> {
                FormNumberField(
                    "Port", state.port, { onChange(state.copy(port = it)) },
                    supporting = "Or use host:port as target"
                )
                FormDropdown(
                    label = "Expected state",
                    value = state.expect.ifBlank { "open" },
                    options = listOf("open", "closed"),
                    labelOf = { it.replaceFirstChar { c -> c.uppercase() } },
                    onSelect = { onChange(state.copy(expect = it)) }
                )
            }

            Hc2CheckType.CERT -> {
                FormNumberField("Port", state.port, { onChange(state.copy(port = it)) }, supporting = "Default: 443")
                FormNumberField(
                    "Minimum remaining days", state.minDays, { onChange(state.copy(minDays = it)) }
                )
                FormTextField("Server name (SNI)", state.serverName, { onChange(state.copy(serverName = it)) })
                FormSwitchRow("Skip chain validation", state.skipChain, { onChange(state.copy(skipChain = it)) })
                FormSwitchRow(
                    "Skip hostname validation", state.skipHostname, { onChange(state.copy(skipHostname = it)) })
            }

            Hc2CheckType.PUSH -> {
                DurationField(
                    "Expected interval", state.periodValue, state.periodUnit,
                    { onChange(state.copy(periodValue = it)) }, { onChange(state.copy(periodUnit = it)) },
                    supporting = "Time between two pushes"
                )
                DurationField(
                    "Grace period", state.graceValue, state.graceUnit,
                    { onChange(state.copy(graceValue = it)) }, { onChange(state.copy(graceUnit = it)) },
                    supporting = "Extra time before the check goes down"
                )
            }

            Hc2CheckType.WHOIS, Hc2CheckType.DOCKER -> {}
        }
    }
}

@Composable
private fun HttpParams(type: Hc2CheckType, s: ParamsState, onChange: (ParamsState) -> Unit, isEdit: Boolean) {
    var showSecrets by remember { mutableStateOf(false) }
    FormDropdown(
        label = "Method",
        value = s.method.uppercase().ifBlank { "GET" },
        options = (HTTP_METHODS + s.method.uppercase()).distinct().filter { it.isNotBlank() },
        labelOf = { it },
        onSelect = { onChange(s.copy(method = it)) }
    )
    FormTextField(
        "Accepted status codes", s.acceptStatus, { onChange(s.copy(acceptStatus = it)) },
        placeholder = "200-299,301"
    )
    if (type == Hc2CheckType.KEYWORD) {
        FormTextField("Keyword", s.keyword, { onChange(s.copy(keyword = it)) })
        FormSwitchRow(
            "Keyword must be absent", s.keywordAbsent, { onChange(s.copy(keywordAbsent = it)) },
            supporting = "The check fails if the text is found"
        )
    }
    if (type == Hc2CheckType.JSON) {
        JsonRulesEditor(s, onChange)
    }
    FormSwitchRow("Follow redirects", s.followRedirects, { onChange(s.copy(followRedirects = it)) })
    if (s.followRedirects) FormNumberField(
        "Max redirects", s.maxRedirects, { onChange(s.copy(maxRedirects = it)) }
    )
    FormTextField("Basic auth user", s.basicUser, { onChange(s.copy(basicUser = it)) })
    FormTextField(
        "Basic auth password", s.basicPassword, { onChange(s.copy(basicPassword = it)) },
        // Hinweise zum gespeicherten Passwort gibt es nur beim Bearbeiten
        supporting = if (!isEdit) null
        else if (s.basicUser.isNotBlank() && s.basicPassword.isEmpty())
            "Re-enter the password if the server requires it; leave empty to keep the current one"
        else "Leave empty to keep the stored password",
        secret = true,
        visualTransformation = PasswordVisualTransformation()
    )

    // Header: Werte maskiert, ein Umschalter für alle
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Headers", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        IconButton(onClick = { showSecrets = !showSecrets }) {
            Icon(
                painter = painterResource(if (showSecrets) R.drawable.visibility_off_24px else R.drawable.visibility_24px),
                contentDescription = if (showSecrets) "Hide values" else "Show values"
            )
        }
    }
    s.headers.forEachIndexed { idx, row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            FormTextField(
                "Name", row.key,
                { v -> onChange(s.copy(headers = s.headers.replaceAt(idx, row.copy(key = v)))) },
                modifier = Modifier.weight(1f)
            )
            FormTextField(
                "Value", row.value,
                { v -> onChange(s.copy(headers = s.headers.replaceAt(idx, row.copy(value = v)))) },
                modifier = Modifier.weight(1f),
                secret = true,
                visualTransformation = if (showSecrets) VisualTransformation.None else PasswordVisualTransformation()
            )
            IconButton(onClick = { onChange(s.copy(headers = s.headers.filterIndexed { i, _ -> i != idx })) }) {
                Icon(painterResource(R.drawable.delete_24px), contentDescription = "Remove header")
            }
        }
    }
    TextButton(onClick = { onChange(s.copy(headers = s.headers + HeaderRow())) }) {
        Icon(painterResource(R.drawable.add_24px), contentDescription = null)
        Text("Add header", modifier = Modifier.padding(start = 8.dp))
    }

    FormTextField(
        "Request body", s.body, { onChange(s.copy(body = it)) },
        singleLine = false, minLines = 3
    )
    FormSwitchRow(
        "Ignore certificate errors", s.insecureTls, { onChange(s.copy(insecureTls = it)) }
    )
    FormNumberField(
        "Fail if certificate expires within (days)", s.certMinDays, { onChange(s.copy(certMinDays = it)) },
        supporting = "HTTPS only, 0 = off"
    )
}

private fun <T> List<T>.replaceAt(idx: Int, item: T): List<T> =
    mapIndexed { i, old -> if (i == idx) item else old }

@Composable
private fun JsonRulesEditor(s: ParamsState, onChange: (ParamsState) -> Unit) {
    Text("JSON rules", style = MaterialTheme.typography.titleSmall)
    s.jsonRules.forEachIndexed { idx, rule ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                FormTextField(
                    "Path", rule.path,
                    { v -> onChange(s.copy(jsonRules = s.jsonRules.replaceAt(idx, rule.copy(path = v)))) },
                    modifier = Modifier.weight(1f), placeholder = "data.items[0].status"
                )
                IconButton(onClick = { onChange(s.copy(jsonRules = s.jsonRules.filterIndexed { i, _ -> i != idx })) }) {
                    Icon(painterResource(R.drawable.delete_24px), contentDescription = "Remove rule")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                FormDropdown(
                    label = "Operator", value = rule.op, options = Hc2JsonOp.entries,
                    labelOf = { it.value },
                    onSelect = { v -> onChange(s.copy(jsonRules = s.jsonRules.replaceAt(idx, rule.copy(op = v)))) },
                    modifier = Modifier.weight(1f)
                )
                if (rule.op.needsValue()) FormTextField(
                    "Value", rule.value,
                    { v -> onChange(s.copy(jsonRules = s.jsonRules.replaceAt(idx, rule.copy(value = v)))) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
    if (s.jsonRules.size < 10) TextButton(onClick = { onChange(s.copy(jsonRules = s.jsonRules + JsonRuleRow())) }) {
        Icon(painterResource(R.drawable.add_24px), contentDescription = null)
        Text("Add rule", modifier = Modifier.padding(start = 8.dp))
    }
    if (s.jsonRules.size > 1) FormSwitchRow(
        "Any rule is enough", s.jsonAny, { onChange(s.copy(jsonAny = it)) },
        supporting = "Otherwise all rules must match"
    )
}

@Composable
private fun DnsParams(s: ParamsState, onChange: (ParamsState) -> Unit, online: Boolean) {
    FormDropdown(
        label = "Record type", value = s.recordType, options = Hc2DnsRecordType.entries,
        labelOf = { it.value }, onSelect = { onChange(s.copy(recordType = it)) }
    )
    FormDropdown(
        label = "Resolver", value = s.resolver, options = Hc2DnsResolver.entries,
        labelOf = { it.value.replaceFirstChar { c -> c.uppercase() } },
        onSelect = { onChange(s.copy(resolver = it)) }
    )
    if (s.resolver == Hc2DnsResolver.SERVER) FormTextField(
        "DNS server", s.server, { onChange(s.copy(server = it)) },
        placeholder = "1.1.1.1", supporting = "IP address, optionally with port"
    )
    // public_ip gibt es nur lokal; ein vorhandener Wert bleibt auswählbar
    val expectOptions = buildList {
        addAll(listOf("any", "contains", "equals", "regex", "nxdomain"))
        if (!online || s.expect == "public_ip") add("public_ip")
    }
    FormDropdown(
        label = "Expected answer", value = s.expect.ifBlank { "any" }, options = expectOptions,
        labelOf = { it }, onSelect = { onChange(s.copy(expect = it)) }
    )
    if (s.expect == "contains" || s.expect == "equals" || s.expect == "regex") FormTextField(
        if (s.expect == "regex") "Pattern" else "Expected values", s.values, { onChange(s.copy(values = it)) },
        singleLine = false, minLines = 2, supporting = if (s.expect == "regex") null else "One value per line"
    )
    FormSwitchRow("Require DNSSEC", s.dnssec, { onChange(s.copy(dnssec = it)) })
    FormNumberField(
        "Minimum TTL (seconds)", s.minTtl, { onChange(s.copy(minTtl = it)) }, supporting = "0 = any"
    )
}
