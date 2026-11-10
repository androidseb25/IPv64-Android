package de.rpicloud.ipv64net.main.views.monitoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.loadHc2Checks
import de.rpicloud.ipv64net.helper.rememberNavArg
import de.rpicloud.ipv64net.main.views.common.FormSection
import de.rpicloud.ipv64net.main.views.FormDropdown
import de.rpicloud.ipv64net.main.views.FormSwitchRow
import de.rpicloud.ipv64net.main.views.FormTextField
import de.rpicloud.ipv64net.main.views.common.ConfirmDialog
import de.rpicloud.ipv64net.main.views.common.Hc2CheckPickerDialog
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.main.views.common.unknownCheckLabel
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64ApiException
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.Hc2Maintenance
import de.sr.ipv64.model.Hc2MaintenanceInput
import de.sr.ipv64.model.Hc2MaintenanceRepeat
import de.sr.ipv64.model.Hc2MaintenanceUpdate
import kotlin.time.Instant

private val MAINTENANCE_FIELDS = setOf("name", "start_at", "end_at", "repeat_until", "tokens")

// Wartungsfenster anlegen/bearbeiten/löschen (Schlüssel SELECTED_MAINTENANCE_ID fehlt = neu)
@Composable
fun MaintenanceEditView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()

    val id = navController.rememberNavArg<Long>(NavKeys.SELECTED_MAINTENANCE_ID)
    val isEdit = id != null

    // Vorgabe beim Anlegen: nächste volle Stunde, eine Stunde lang
    val defaultStart = remember { Instant.fromEpochMilliseconds((nowMinute().toEpochMilliseconds() / 3_600_000L + 1) * 3_600_000L) }

    var original by remember { mutableStateOf<Hc2Maintenance?>(null) }
    // Formularfelder überleben eine Rotation; "initialized" verhindert, dass das erneute Laden Eingaben überschreibt
    var initialized by rememberSaveable { mutableStateOf(!isEdit) }
    var name by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveableInstant(if (isEdit) null else defaultStart)
    var end by rememberSaveableInstant(
        if (isEdit) null else Instant.fromEpochMilliseconds(defaultStart.toEpochMilliseconds() + 3_600_000L)
    )
    var repeat by rememberSaveable { mutableStateOf(Hc2MaintenanceRepeat.NONE) }
    var repeatUntil by rememberSaveableInstant(null)
    var active by rememberSaveable { mutableStateOf(true) }
    var limitToChecks by rememberSaveable { mutableStateOf(false) }
    var tokens by rememberSaveable { mutableStateOf<List<String>>(ArrayList()) }
    var checkNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showPicker by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val errors = remember { mutableStateMapOf<String, String>() }

    // Namen der gewählten Checks (gecachte Liste); Fehler hier sind nicht kritisch, Tokens bleiben als "Unknown check" sichtbar
    suspend fun refreshNames() {
        try {
            checkNames = loadHc2Checks(ctx).mapNotNull { c -> c.token?.let { it to c.name.orEmpty() } }.toMap()
        } catch (_: IPv64Exception) {
        }
    }

    fun applyFrom(m: Hc2Maintenance) {
        original = m
        if (initialized) return
        initialized = true
        name = m.name.orEmpty()
        start = m.startAt
        end = m.endAt
        repeat = m.repeat ?: Hc2MaintenanceRepeat.NONE
        repeatUntil = m.repeatUntil
        active = m.active ?: true
        tokens = ArrayList(m.tokens)
        limitToChecks = m.tokens.isNotEmpty()
    }

    fun load() {
        val mid = id ?: return
        req.launch(scope, retry = { load() }) {
            val m = MaintenanceCache.peek(ctx)?.firstOrNull { it.id == mid }
                ?: monitoringCall(ctx) { it.hc2.maintenance.get(mid) }
            applyFrom(m)
            if (tokens.isNotEmpty()) refreshNames()
        }
    }

    LaunchedEffect(Unit) { load() }

    val nodeManaged = !original?.nodeId.isNullOrBlank()

    fun validate(): Boolean {
        errors.clear()
        if (name.isBlank()) errors["name"] = "Please enter a name."
        val s = start
        val e = end
        if (s == null) errors["start_at"] = "Please select a start."
        if (e == null) errors["end_at"] = "Please select an end."
        if (s != null && e != null && e <= s) errors["end_at"] = "The end must be after the start."
        val until = repeatUntil
        if (repeat != Hc2MaintenanceRepeat.NONE && until != null && s != null && until <= s) {
            errors["repeat_until"] = "Must be after the start."
        }
        if (limitToChecks && tokens.isEmpty()) errors["tokens"] = "Select at least one check or use all checks."
        return errors.isEmpty()
    }

    fun handleError(e: IPv64Exception): Boolean {
        if (e is IPv64ApiException && (e.isValidationError || e.isConflict)) {
            val field = e.field
            if (field != null && field in MAINTENANCE_FIELDS) {
                errors[field] = e.detail ?: "Invalid value."
                return true
            }
        }
        return false
    }

    fun save() {
        if (isEdit && original == null) return  // Fenster noch nicht geladen
        if (!validate()) return
        val s = start ?: return
        val e = end ?: return
        val effectiveTokens = if (limitToChecks) tokens else emptyList()
        val effectiveUntil = if (repeat != Hc2MaintenanceRepeat.NONE) repeatUntil else null
        val orig = original
        req.launch(scope, spinner = "Saving...", onError = ::handleError) {
            if (orig == null) {
                monitoringCall(ctx) {
                    it.hc2.maintenance.create(
                        Hc2MaintenanceInput(
                            name = name.trim(), startAt = s, endAt = e, repeat = repeat,
                            repeatUntil = effectiveUntil, tokens = effectiveTokens, active = active
                        )
                    )
                }
            } else {
                // Nur geänderte Felder senden; ein gesetztes "repeat until" lässt sich per PATCH nicht mehr entfernen
                val update = Hc2MaintenanceUpdate(
                    name = name.trim().takeIf { it != orig.name },
                    startAt = s.takeIf { it != orig.startAt },
                    endAt = e.takeIf { it != orig.endAt },
                    repeat = repeat.takeIf { it != (orig.repeat ?: Hc2MaintenanceRepeat.NONE) },
                    repeatUntil = effectiveUntil?.takeIf { it != orig.repeatUntil },
                    tokens = effectiveTokens.takeIf { it.toSet() != orig.tokens.toSet() },
                    active = active.takeIf { it != (orig.active ?: true) }
                )
                val oid = orig.id
                if (oid != null && update != Hc2MaintenanceUpdate()) {
                    monitoringCall(ctx) { it.hc2.maintenance.update(oid, update) }
                }
            }
            MaintenanceCache.invalidate()
            navController.popBackStack()
        }
    }

    fun delete() {
        val mid = original?.id ?: return
        req.launch(scope, spinner = "Deleting...") {
            monitoringCall(ctx) { it.hc2.maintenance.delete(mid) }
            MaintenanceCache.invalidate()
            navController.popBackStack()
        }
    }

    ScreenScaffold(Tabs.getLabel(Tab.maintenance_edit), navController, mainPadding) {
        if (!initialized) return@ScreenScaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (nodeManaged) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "This maintenance window is managed by a node and cannot be changed here.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            FormSection("General") {
                FormTextField(
                    "Name", name, { name = it; errors.remove("name") },
                    enabled = !nodeManaged,
                    supporting = errors["name"]
                )
                FormSwitchRow(
                    "Active", active, { active = it },
                    supporting = "Inactive windows do not pause any alerts.",
                    enabled = !nodeManaged
                )
            }

            FormSection("Schedule") {
                DateTimeField(
                    "Start", start, { start = it; errors.remove("start_at") },
                    enabled = !nodeManaged, error = errors["start_at"],
                    supporting = "Times use your device time zone."
                )
                DateTimeField(
                    "End", end, { end = it; errors.remove("end_at") },
                    enabled = !nodeManaged, error = errors["end_at"]
                )
                FormDropdown(
                    label = "Repeat",
                    value = repeat,
                    options = Hc2MaintenanceRepeat.entries,
                    labelOf = { it.label() },
                    onSelect = { repeat = it },
                    enabled = !nodeManaged
                )
                if (repeat != Hc2MaintenanceRepeat.NONE) {
                    val locked = original?.repeatUntil != null
                    DateTimeField(
                        "Repeat until", repeatUntil, { repeatUntil = it; errors.remove("repeat_until") },
                        enabled = !nodeManaged,
                        clearable = !locked,
                        error = errors["repeat_until"],
                        supporting = if (locked) "A repeat limit can be changed but not removed." else "Optional."
                    )
                }
            }

            FormSection("Scope") {
                FormSwitchRow(
                    "Only selected checks", limitToChecks, { limitToChecks = it; errors.remove("tokens") },
                    supporting = if (limitToChecks) "Only the selected checks are paused." else "All checks are paused.",
                    enabled = !nodeManaged
                )
                if (limitToChecks) {
                    if (tokens.isEmpty()) {
                        Text("No checks selected.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        tokens.forEach { t ->
                            Text(
                                checkNames[t]?.takeIf { it.isNotBlank() } ?: unknownCheckLabel(t),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    errors["tokens"]?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(
                        onClick = { showPicker = true },
                        enabled = !nodeManaged,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (tokens.isEmpty()) "Select checks" else "Change selection") }
                }
            }

            if (!nodeManaged) {
                Button(onClick = { save() }, enabled = !req.isBusy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isEdit) "Save" else "Create")
                }
                if (isEdit) {
                    OutlinedButton(
                        onClick = { showDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Delete") }
                }
            }
        }
    }

    if (showPicker) {
        Hc2CheckPickerDialog(
            initial = tokens,
            multi = true,
            onConfirm = {
                tokens = ArrayList(it)
                errors.remove("tokens")
                showPicker = false
                req.launch(scope, spinner = null) { refreshNames() }
            },
            onDismiss = { showPicker = false }
        )
    }
    if (showDelete) {
        ConfirmDialog(
            title = "Delete maintenance window?",
            text = "\"${name.ifBlank { "This window" }}\" will be removed. Alerts are no longer paused by it.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { showDelete = false; delete() },
            onDismiss = { showDelete = false }
        )
    }
    RequestStateHost(req)
}
