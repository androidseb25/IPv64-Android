package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.rememberNavArg
import de.rpicloud.ipv64net.main.views.common.ConfirmDialog
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.main.views.network.LocalNodeStore
import de.rpicloud.ipv64net.main.views.network.localNodeClient
import de.rpicloud.ipv64net.main.views.network.localNodeError
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.repeatMinutesLabel
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.node64.model.Check
import de.sr.ipv64.node64.model.CheckIpMode
import de.sr.ipv64.node64.model.CheckRequest
import de.sr.ipv64.node64.model.CheckType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Check einer direkt verbundenen Node anlegen (kein Token) oder bearbeiten. Kanäle, Ausführung, Policy, Parent u. Ä.
 * werden beim Speichern unverändert aus dem geladenen Check übernommen; params werden um die Eingaben ergänzt.
 */

// Lesbarer Typname wie im Account-Formular (Hc2CheckType.label)
private fun CheckType.label() = when (this) {
    CheckType.HTTP -> "HTTP(S)"
    CheckType.KEYWORD -> "Keyword"
    CheckType.JSON -> "JSON"
    CheckType.DNS -> "DNS"
    CheckType.ICMP -> "Ping (ICMP)"
    CheckType.TCP -> "TCP"
    CheckType.PORT -> "Port"
    CheckType.CERT -> "Certificate"
    CheckType.WHOIS -> "WHOIS"
    CheckType.PUSH -> "Push"
    CheckType.DOCKER -> "Docker"
}

private val DNS_RECORDS = listOf("A", "AAAA", "CNAME", "MX", "TXT", "NS", "SRV", "CAA", "PTR", "SOA")
private val REPEAT_OPTIONS: List<Int?> = listOf(null, 0, 5, 10, 15, 30, 60, 120, 360, 720, 1440)

@Composable
fun NodeCheckEditView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    val nodeId = navController.rememberNavArg<String>(NavKeys.SELECTED_NODE_ID)
    val token = navController.rememberNavArg<String>(NavKeys.NODE_CHECK_TOKEN)
    val client = remember(nodeId) { nodeId?.let { id -> LocalNodeStore.get(ctx, id)?.let { (u, k) -> localNodeClient(u, k) } } }

    var loaded by remember { mutableStateOf<Check?>(null) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf<CheckType?>(null) }
    var target by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("") }
    var retries by remember { mutableStateOf("") }
    var retryInterval by remember { mutableStateOf("") }
    var timeout by remember { mutableStateOf("") }
    var connectTimeout by remember { mutableStateOf("") }
    var stabilize by remember { mutableStateOf("") }
    var invert by remember { mutableStateOf(false) }
    var ipMode by remember { mutableStateOf<CheckIpMode?>(null) }
    var notifyDown by remember { mutableStateOf(true) }
    var notifyUp by remember { mutableStateOf(true) }
    var notifyDegraded by remember { mutableStateOf(false) }
    var critical by remember { mutableStateOf(false) }
    var repeatMinutes by remember { mutableStateOf<Int?>(null) }
    var tags by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    // Typparameter (nur die hier bekannten Schlüssel; Rest bleibt im geladenen params)
    var keyword by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var recordType by remember { mutableStateOf("A") }
    var advOpen by remember { mutableStateOf(false) }
    var notifOpen by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var paused by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun fill(c: Check) {
        loaded = c
        name = c.name.orEmpty(); type = c.type; target = c.target.orEmpty()
        interval = c.intervalSeconds?.toString().orEmpty(); retries = c.retries?.toString().orEmpty()
        description = c.description.orEmpty(); paused = c.paused == true
        retryInterval = c.retryIntervalSeconds?.toString().orEmpty(); timeout = c.timeoutSeconds?.toString().orEmpty()
        connectTimeout = c.connectTimeoutSeconds?.toString().orEmpty(); stabilize = c.stabilize?.toString().orEmpty()
        invert = c.invert == true; ipMode = c.ipMode
        notifyDown = c.notifyDown ?: true; notifyUp = c.notifyUp ?: true; notifyDegraded = c.notifyDegraded == true
        critical = c.critical == true; repeatMinutes = c.repeatMinutes
        tags = c.tags.joinToString(", "); group = c.group.orEmpty()
        val p = c.params
        keyword = p?.get("keyword")?.jsonPrimitive?.contentOrNull.orEmpty()
        port = p?.get("port")?.jsonPrimitive?.intOrNull?.toString().orEmpty()
        recordType = p?.get("record_type")?.jsonPrimitive?.contentOrNull ?: "A"
    }

    // Fehler der Node als Dialog; bei Ladefehlern danach zurück
    fun fail(e: IPv64Exception, close: Boolean = false): Boolean {
        val (title, text) = localNodeError(e)
        req.message(title, text, R.drawable.warning_24px, onClose = if (close) ({ navController.popBackStack() }) else null)
        return true
    }

    LaunchedEffect(client, token) {
        if (client == null) return@LaunchedEffect
        if (token != null) req.launch(scope, onError = { fail(it, close = true) }) { fill(client.checks.get(token)) }
    }

    fun save() {
        val t = type
        fun num(v: String, label: String): Double? = v.trim().takeIf { it.isNotEmpty() }?.let { it.toDoubleOrNull() ?: return null.also { formError = "$label must be a number." } }
        formError = null
        val i = num(interval, "Interval"); val r = num(retries, "Retries"); val ri = num(retryInterval, "Retry interval")
        val to = num(timeout, "Timeout"); val cto = num(connectTimeout, "Connect timeout"); val st = num(stabilize, "Stabilize")
        val pt = num(port, "Port")
        formError?.let { req.message("Check your input", it, R.drawable.warning_24px); return }
        formError = when {
            name.isBlank() -> "Enter a name."
            t == null -> "Choose a type."
            t != CheckType.PUSH && target.isBlank() -> "Enter a target."
            t == CheckType.KEYWORD && keyword.isBlank() -> "Enter a keyword."
            else -> null
        }
        formError?.let { req.message("Check your input", it, R.drawable.warning_24px); return }
        if (t == null || client == null) return
        val base = loaded
        // Bearbeiten erst nach erfolgreichem Laden (sonst gingen die übernommenen Felder verloren)
        if (token != null && base == null) return
        val desc = description.trim()
        // Eingaben in die vorhandenen params mischen (leer = Schlüssel entfernen), Rest bleibt unverändert
        val merged = buildMap<String, kotlinx.serialization.json.JsonElement> {
            base?.params?.let { putAll(it) }
            fun set(k: String, v: kotlinx.serialization.json.JsonElement?) { if (v == null) remove(k) else put(k, v) }
            if (t == CheckType.KEYWORD) set("keyword", keyword.trim().takeIf { it.isNotEmpty() }?.let(::JsonPrimitive))
            if (t in listOf(CheckType.TCP, CheckType.PORT, CheckType.CERT)) set("port", pt?.toInt()?.let(::JsonPrimitive))
            if (t == CheckType.DNS) set("record_type", JsonPrimitive(recordType))
        }.takeIf { it.isNotEmpty() }?.let { JsonObject(it) }
        val tagList = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        // Beim Anlegen nur senden, was vom Standard abweicht; beim Bearbeiten immer (leere Zahlenfelder behalten den alten Wert)
        fun flag(v: Boolean, def: Boolean) = if (base != null || v != def) v else null
        val request = CheckRequest(
            name = name.trim(), type = t, target = target.trim().ifEmpty { null }, params = merged,
            intervalSeconds = i?.toInt() ?: base?.intervalSeconds, retryIntervalSeconds = ri?.toInt() ?: base?.retryIntervalSeconds,
            retries = r?.toInt() ?: base?.retries, connectTimeoutSeconds = cto ?: base?.connectTimeoutSeconds,
            timeoutSeconds = to ?: base?.timeoutSeconds, invert = flag(invert, false), stabilize = st?.toInt() ?: base?.stabilize,
            ipMode = ipMode ?: base?.ipMode, tags = tagList.takeIf { base != null || it.isNotEmpty() },
            description = desc, paused = if (base == null) paused else null, parent = base?.parent,
            critical = flag(critical, false), execution = base?.execution, policy = base?.policy,
            locations = base?.locations?.takeIf { it.isNotEmpty() }, notifyDegraded = flag(notifyDegraded, false),
            notifyDown = flag(notifyDown, true), notifyUp = flag(notifyUp, true), channels = base?.channels?.takeIf { it.isNotEmpty() },
            repeatMinutes = repeatMinutes ?: base?.repeatMinutes, group = group.trim().takeIf { base != null || it.isNotEmpty() },
            nodes = base?.nodes?.takeIf { it.isNotEmpty() }
        )
        req.launch(scope, spinner = "Saving...", onError = { fail(it) }) {
            if (token == null) client.checks.create(request) else client.checks.update(token, request)
            navController.popBackStack()
        }
    }

    ScreenScaffold(
        title = if (token == null) "New node healthcheck" else Tabs.getLabel(Tab.node_check_edit),
        navController = navController,
        mainPadding = mainPadding,
        actions = {
            if (loaded?.token != null) IconButton(enabled = !req.isBusy, onClick = { confirmDelete = true }) {
                Icon(painterResource(R.drawable.delete_24px), contentDescription = "Delete")
            }
            IconButton(enabled = !req.isBusy, onClick = { save() }) {
                Icon(painterResource(R.drawable.save_24px), contentDescription = "Save")
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (client == null) {
                Text("This node is not connected.", color = MaterialTheme.colorScheme.error)
                return@Column
            }
            val busy = req.isBusy
            FormTextField(label = "Name", value = name, onValue = { name = it }, enabled = !busy)
            FormDropdown(
                label = "Type", value = type, options = CheckType.entries.toList(),
                labelOf = { it?.label() ?: "Choose…" }, onSelect = { type = it },
                enabled = !busy && loaded == null
            )
            FormTextField(
                label = "Target", value = target, onValue = { target = it }, enabled = !busy,
                supporting = if (type == CheckType.PUSH) "Not needed for push checks." else "URL, host or domain, depending on the type.",
                keyboardType = KeyboardType.Uri
            )
            if (type == CheckType.KEYWORD) FormTextField(label = "Keyword", value = keyword, onValue = { keyword = it }, enabled = !busy)
            if (type == CheckType.TCP || type == CheckType.PORT || type == CheckType.CERT) {
                FormNumberField(label = "Port", value = port, onValue = { port = it }, enabled = !busy)
            }
            if (type == CheckType.DNS) FormDropdown(
                label = "Record type", value = recordType, options = DNS_RECORDS, labelOf = { it }, onSelect = { recordType = it }, enabled = !busy
            )
            FormAccordion("Advanced", advOpen, { advOpen = !advOpen }) {
                FormNumberField("Interval (seconds)", interval, { interval = it }, enabled = !busy)
                FormNumberField("Retry interval (seconds)", retryInterval, { retryInterval = it }, enabled = !busy)
                FormNumberField("Retries (0-10)", retries, { retries = it }, enabled = !busy)
                FormNumberField("Timeout (seconds)", timeout, { timeout = it }, decimal = true, enabled = !busy)
                FormNumberField("Connect timeout (seconds)", connectTimeout, { connectTimeout = it }, decimal = true, enabled = !busy)
                FormNumberField("Stabilize (1-20)", stabilize, { stabilize = it }, supporting = "Measurements until the state changes", enabled = !busy)
                FormSwitchRow("Invert result", invert, { invert = it }, enabled = !busy)
                FormDropdown(
                    label = "IP mode", value = ipMode, options = (if (loaded?.ipMode != null) emptyList() else listOf(null)) + CheckIpMode.entries,
                    labelOf = { it?.value ?: "Default" }, onSelect = { ipMode = it }, enabled = !busy
                )
            }
            FormAccordion("Notifications & organisation", notifOpen, { notifOpen = !notifOpen }) {
                FormSwitchRow("Notify when down", notifyDown, { notifyDown = it }, enabled = !busy)
                FormSwitchRow("Notify when back up", notifyUp, { notifyUp = it }, enabled = !busy)
                FormSwitchRow("Notify when degraded", notifyDegraded, { notifyDegraded = it }, supporting = "Also notify on partial failure", enabled = !busy)
                FormSwitchRow("Critical", critical, { critical = it }, supporting = "Ignores quiet hours", enabled = !busy)
                FormDropdown(
                    label = "Repeat reminder", value = repeatMinutes,
                    options = (REPEAT_OPTIONS + repeatMinutes).distinct().filter { !(loaded?.repeatMinutes != null && it == null) },
                    labelOf = { if (it == null) "Default" else repeatMinutesLabel(it) }, onSelect = { repeatMinutes = it }, enabled = !busy
                )
                FormTextField("Tags", tags, { tags = it }, supporting = "Comma-separated", enabled = !busy)
                FormTextField("Group", group, { group = it }, enabled = !busy)
                FormTextField("Description", description, { description = it }, singleLine = false, minLines = 2, enabled = !busy)
                if (loaded == null) FormSwitchRow("Start paused", paused, { paused = it }, enabled = !busy)
            }
            formError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete healthcheck?", text = "\"$name\" will be removed from the node.",
            confirmLabel = "Delete", destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                val tk = loaded?.token ?: return@ConfirmDialog
                req.launch(scope, spinner = "Deleting...", onError = { fail(it) }) {
                    client?.checks?.delete(tk)
                    // Detailansicht des gelöschten Checks überspringen: zurück zur Liste
                    navController.popBackStack(Tabs.getRoute(Tab.healthcheck), false)
                }
            }
        )
    }

    RequestStateHost(req)
}
