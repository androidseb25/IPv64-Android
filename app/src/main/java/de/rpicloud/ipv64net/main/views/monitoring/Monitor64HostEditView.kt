package de.rpicloud.ipv64net.main.views.monitoring

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.rememberNavArg
import de.rpicloud.ipv64net.main.views.FormDropdown
import de.rpicloud.ipv64net.main.views.FormNumberField
import de.rpicloud.ipv64net.main.views.FormSwitchRow
import de.rpicloud.ipv64net.main.views.FormTextField
import de.rpicloud.ipv64net.main.views.common.ConfirmDialog
import de.rpicloud.ipv64net.main.views.common.GroupDivider
import de.rpicloud.ipv64net.main.views.common.GroupText
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.main.views.network.LocalNodeStore
import de.rpicloud.ipv64net.main.views.network.localNodeClient
import de.rpicloud.ipv64net.main.views.network.localNodeError
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.node64.model.Monitor64AccessConfig
import de.sr.ipv64.node64.model.Monitor64AccessInput
import de.sr.ipv64.node64.model.Monitor64AccessState
import de.sr.ipv64.node64.model.Monitor64AccessType
import de.sr.ipv64.node64.model.Monitor64Host
import de.sr.ipv64.node64.model.Monitor64HostCreate
import de.sr.ipv64.node64.model.Monitor64HostInput
import de.sr.ipv64.node64.model.Monitor64HostKind
import de.sr.ipv64.node64.model.Monitor64TestInput
import de.sr.ipv64.node64.model.Monitor64TestResult

/**
 * Zugang für Monitor64 aus den Formularfeldern. Der Server prüft streng "nur Felder des Zugangstyps", daher gehen nur
 * nicht leere Felder raus. [fingerprint] (bestätigt) wird bei SSH zu hostkey, sonst zu config.pin.
 */
internal fun buildMonitor64Access(
    type: Monitor64AccessType, port: String, user: String, url: String, tokenId: String, secret: String, fingerprint: String?,
    base: Monitor64AccessConfig? = null,
): Monitor64AccessInput {
    val ssh = type == Monitor64AccessType.SSH
    // Bestehende Config als Basis: PUT ersetzt die Config, sonst gingen nicht angezeigte Felder (SNMP, Token, ...) verloren
    val config = (base ?: Monitor64AccessConfig()).copy(
        port = port.trim().toIntOrNull(), user = user.trim().ifEmpty { null }, url = url.trim().ifEmpty { null },
        tokenId = tokenId.trim().ifEmpty { null }, pin = fingerprint?.takeIf { !ssh } ?: base?.pin
    )
    return Monitor64AccessInput(
        type = type,
        config = if (base != null) config else config.takeIf { it != Monitor64AccessConfig() },
        secret = secret.ifEmpty { null },
        hostkey = fingerprint?.takeIf { ssh }
    )
}

private fun Monitor64HostKind.label() = if (this == Monitor64HostKind.NONE) "None" else value

// Monitor64-Host einer direkt verbundenen Node anlegen (UID fehlt) oder bearbeiten (inkl. Zugänge)
@Composable
fun Monitor64HostEditView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    val nodeId = navController.rememberNavArg<String>(NavKeys.SELECTED_NODE_ID)
    val uid = navController.rememberNavArg<String>(NavKeys.MONITOR64_HOST_UID)
    val client = remember(nodeId) { nodeId?.let { id -> LocalNodeStore.get(ctx, id)?.let { (u, k) -> localNodeClient(u, k) } } }

    var loaded by remember { mutableStateOf<Monitor64Host?>(null) }
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(Monitor64HostKind.NONE) }
    var group by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var paused by remember { mutableStateOf(false) }
    // Zugang (nur beim Anlegen); das Secret nie in saveable State oder Logs
    var accessType by remember { mutableStateOf<Monitor64AccessType?>(null) }
    var port by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var tokenId by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<Monitor64TestResult?>(null) }
    var confirmedFp by remember { mutableStateOf<String?>(null) }
    var askFp by remember { mutableStateOf<String?>(null) }
    // Bearbeiten: Zugänge des Hosts; editing = gewählter Zugang (uid null = neuer Zugang)
    var accesses by remember { mutableStateOf<List<Monitor64AccessState>>(emptyList()) }
    var editing by remember { mutableStateOf<Monitor64AccessState?>(null) }
    var accessEnabled by remember { mutableStateOf(true) }
    var confirmDeleteAccess by remember { mutableStateOf(false) }

    fun fill(h: Monitor64Host) {
        loaded = h
        name = h.name.orEmpty(); address = h.address.orEmpty(); kind = h.kind ?: Monitor64HostKind.NONE
        group = h.group.orEmpty(); tags = h.tags.joinToString(", "); notes = h.notes.orEmpty()
        interval = h.intervalSec?.takeIf { it > 0 }?.toString().orEmpty(); paused = h.paused == true
    }

    // Fehler der Node als Dialog; bei Ladefehlern danach zurück
    fun fail(e: IPv64Exception, close: Boolean = false): Boolean {
        val title = localNodeError(e).first
        val text = monitor64Error(e)
        req.message(title, text, R.drawable.warning_24px, onClose = if (close) ({ navController.popBackStack() }) else null)
        return true
    }

    LaunchedEffect(client, uid) {
        if (client == null) return@LaunchedEffect
        if (uid != null) req.launch(scope, onError = { fail(it, close = true) }) {
            val d = client.monitor64.hosts.get(uid)
            accesses = d.accesses
            val h = d.host
            if (h == null) req.message("Host not found", "The node returned no data for this host.", R.drawable.warning_24px, onClose = { navController.popBackStack() })
            else fill(h)
        }
    }

    // Geänderte Zieldaten machen Test und bestätigten Fingerprint ungültig
    LaunchedEffect(address, accessType, port, user, url, tokenId, secret, editing) { testResult = null; confirmedFp = null }

    fun access(): Monitor64AccessInput? = accessType?.let { buildMonitor64Access(it, port, user, url, tokenId, secret, confirmedFp, editing?.config) }

    // Zugang zum Bearbeiten in die Felder laden; Secret bleibt leer (= unverändert)
    fun startEdit(a: Monitor64AccessState) {
        editing = a
        accessType = a.type ?: Monitor64AccessType.AGENT
        port = a.config?.port?.takeIf { it > 0 }?.toString().orEmpty()
        user = a.config?.user.orEmpty(); url = a.config?.url.orEmpty(); tokenId = a.config?.tokenId.orEmpty()
        secret = ""; accessEnabled = a.enabled != false
    }

    fun saveAccess() {
        val hostUid = uid ?: return
        val e = editing ?: return
        val a = access()?.copy(enabled = accessEnabled) ?: return
        req.launch(scope, spinner = "Saving...", onError = { fail(it) }) {
            val c = client ?: return@launch
            if (e.uid != null) c.monitor64.hosts.updateAccess(e.uid!!, a) else c.monitor64.hosts.createAccess(hostUid, a)
            editing = null; secret = ""
            accesses = c.monitor64.hosts.get(hostUid).accesses
        }
    }

    fun deleteAccess() {
        val hostUid = uid ?: return
        val accessUid = editing?.uid ?: return
        req.launch(scope, spinner = "Deleting...", onError = { fail(it) }) {
            val c = client ?: return@launch
            c.monitor64.hosts.deleteAccess(accessUid)
            editing = null; secret = ""
            accesses = c.monitor64.hosts.get(hostUid).accesses
        }
    }

    fun test() {
        val a = access() ?: return
        if (address.isBlank()) { req.message("Check your input", "Enter an address.", R.drawable.warning_24px); return }
        val accessUid = editing?.uid
        req.launch(scope, spinner = "Testing...", onError = { fail(it) }) {
            val c = client ?: return@launch
            // Gespeicherter Zugang: leere Felder (auch Secret) gelten als gespeichert
            val r = if (accessUid != null) c.monitor64.hosts.testAccess(accessUid, a) else c.monitor64.test(Monitor64TestInput(address.trim(), a))
            testResult = r
            if (r.needConfirm == true) askFp = r.fingerprint
        }
    }

    fun save() {
        val sec = interval.trim().takeIf { it.isNotEmpty() }
        val sec30 = sec?.let { it.toIntOrNull()?.takeIf { v -> v in 30..900 } ?: run { req.message("Check your input", "Interval must be 30 to 900 seconds.", R.drawable.warning_24px); return } }
        val msg = when {
            name.isBlank() -> "Enter a name."
            name.trim().length > 100 -> "The name can have at most 100 characters."
            address.isBlank() -> "Enter an address."
            else -> null
        }
        msg?.let { req.message("Check your input", it, R.drawable.warning_24px); return }
        if (client == null) return
        // Bearbeiten erst nach erfolgreichem Laden (PUT ersetzt alle Felder)
        if (uid != null && loaded == null) return
        val tagList = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (uid != null) {
            val input = Monitor64HostInput(
                name = name.trim(), address = address.trim(), kind = loaded?.kind, group = group.trim(), tags = tagList,
                intervalSec = sec30, notes = notes.trim(), paused = paused
            )
            req.launch(scope, spinner = "Saving...", onError = { fail(it) }) {
                // Leeres Intervall = Standard der Node; die API kennt beim Bearbeiten nur "unverändert", daher Wert holen
                val iv = sec30 ?: client.monitor64.settings().interval?.toInt()
                client.monitor64.hosts.update(uid, input.copy(intervalSec = iv))
                navController.popBackStack()
            }
            return
        }
        val a = access()
        // Dienste nur nach einem Test (Standard-Dienste gleich übernehmen)
        val tested = testResult.takeIf { a != null && it?.ok == true }
        val create = Monitor64HostCreate(
            name = name.trim(), address = address.trim(), kind = kind.takeIf { it != Monitor64HostKind.NONE }, group = group.trim().ifEmpty { null },
            tags = tagList.ifEmpty { null }, intervalSec = sec30, notes = notes.trim().ifEmpty { null }, paused = paused.takeIf { it },
            accesses = a?.let { listOf(it) },
            services = tested?.services?.takeIf { it.isNotEmpty() },
            adopt = tested?.services?.filter { it.isDefault == true }?.map { it.key }?.takeIf { it.isNotEmpty() }
        )
        req.launch(scope, spinner = "Saving...", onError = { fail(it) }) {
            client.monitor64.hosts.create(create)
            navController.popBackStack()
        }
    }

    ScreenScaffold(
        title = when {
            editing != null -> if (editing?.uid != null) "Edit access" else "New access"
            uid == null -> "New Monitor64 host"
            else -> Tabs.getLabel(Tab.monitor64_host_edit)
        },
        navController = navController,
        mainPadding = mainPadding,
        // Offener Zugang: Zurück schließt nur den Zugang
        onBack = if (editing != null) ({ editing = null; secret = "" }) else null,
        actions = {
            if (editing?.uid != null) IconButton(enabled = !req.isBusy, onClick = { confirmDeleteAccess = true }) {
                Icon(painterResource(R.drawable.delete_24px), contentDescription = "Delete access")
            }
            // Speichern sichert den offenen Zugang, sonst den Host
            IconButton(enabled = !req.isBusy && (editing == null || accessType != null), onClick = { if (editing != null) saveAccess() else save() }) {
                Icon(painterResource(R.drawable.save_24px), contentDescription = "Save")
            }
        }
    ) {
        val scroll = rememberScrollState()
        LaunchedEffect(editing) { scroll.scrollTo(0) }
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (client == null) {
                Text("This node is not connected.", color = MaterialTheme.colorScheme.error)
                return@Column
            }
            val busy = req.isBusy
            // Offener Zugang: nur dessen Felder zeigen, Speichern sichert dann nur den Zugang
            if (editing == null) {
            FormTextField(label = "Name", value = name, onValue = { name = it }, enabled = !busy, supporting = "At most 100 characters")
            FormTextField(label = "Address", value = address, onValue = { address = it }, enabled = !busy, supporting = "Hostname, IPv4 or IPv6", keyboardType = KeyboardType.Uri)
            FormDropdown(
                label = "Kind", value = kind, options = Monitor64HostKind.entries.toList(), labelOf = { it.label() },
                onSelect = { kind = it }, enabled = !busy && uid == null
            )
            FormTextField("Group", group, { group = it }, enabled = !busy)
            FormTextField("Tags", tags, { tags = it }, supporting = "Comma-separated", enabled = !busy)
            FormNumberField("Interval (seconds)", interval, { interval = it }, supporting = if (uid == null) "30 to 900, empty = node default" else "30 to 900, empty = current node default", enabled = !busy)
            FormTextField("Notes", notes, { notes = it }, singleLine = false, minLines = 2, enabled = !busy)
            FormSwitchRow("Paused", paused, { paused = it }, enabled = !busy)
            }

            if (uid != null && editing == null) {
                ListGroup("Accesses") {
                    accesses.forEachIndexed { i, a ->
                        if (i > 0) GroupDivider()
                        ListRow(
                            title = (a.name?.takeIf { it.isNotBlank() } ?: a.type?.value.orEmpty()).m64Text(),
                            subtitle = listOfNotNull(a.type?.value, "Off".takeIf { a.enabled == false }, a.error?.takeIf { it.isNotBlank() }?.m64Text())
                                .joinToString(" · "),
                            subtitleMaxLines = 3,
                            onClick = { if (!busy) startEdit(a) }
                        )
                    }
                    if (accesses.isNotEmpty()) GroupDivider()
                    ListRow(title = "Add access", icon = R.drawable.add_24px, onClick = { if (!busy) startEdit(Monitor64AccessState()) })
                }
            } else {
                val existing = editing?.uid != null
                FormDropdown(
                    label = "Access", value = accessType,
                    options = (if (uid == null) listOf<Monitor64AccessType?>(null) else emptyList()) + Monitor64AccessType.entries,
                    labelOf = { it?.value ?: "None (add later)" },
                    onSelect = { accessType = it }, enabled = !busy && !existing
                )
                if (accessType != null) {
                    FormNumberField("Port", port, { port = it }, supporting = "Empty = default of the type", enabled = !busy)
                    FormTextField("User", user, { user = it }, enabled = !busy)
                    FormTextField("URL", url, { url = it }, enabled = !busy, keyboardType = KeyboardType.Uri)
                    FormTextField("Token ID", tokenId, { tokenId = it }, enabled = !busy)
                    FormTextField(
                        "Secret", secret, { secret = it }, enabled = !busy, secret = true,
                        visualTransformation = PasswordVisualTransformation(), supporting = "Password, token or key; write-only"
                    )
                    if (uid != null) FormSwitchRow("Enabled", accessEnabled, { accessEnabled = it }, enabled = !busy)
                    OutlinedButton(onClick = { test() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Test connection") }
                    testResult?.let { r ->
                        val line = if (r.ok == true) {
                            listOfNotNull("Connection OK", r.summary?.takeIf { it.isNotBlank() }, "${r.services.size} services found", r.warning?.takeIf { it.isNotBlank() }).joinToString(" · ").m64Text()
                        } else r.error?.takeIf { it.isNotBlank() }?.m64Text() ?: "Connection failed."
                        Text(line, style = MaterialTheme.typography.bodySmall, color = if (r.ok == true) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                    }
                }

            }
        }
    }

    askFp?.let { fp ->
        ConfirmDialog(
            title = "Trust this fingerprint?", text = fp, confirmLabel = "Trust and test again",
            onDismiss = { askFp = null },
            onConfirm = { askFp = null; confirmedFp = fp; test() }
        )
    }

    BackHandler(enabled = editing != null) { editing = null; secret = "" }

    if (confirmDeleteAccess) {
        ConfirmDialog(
            title = "Delete access?", text = "The host can no longer be queried through this access.", confirmLabel = "Delete", destructive = true,
            onDismiss = { confirmDeleteAccess = false },
            onConfirm = { confirmDeleteAccess = false; deleteAccess() }
        )
    }

    RequestStateHost(req)
}
