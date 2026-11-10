package de.rpicloud.ipv64net.main.views.monitoring

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.main.views.common.FormSection
import de.rpicloud.ipv64net.main.views.FormNumberField
import de.rpicloud.ipv64net.main.views.FormSwitchRow
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.model.Hc2SettingsUpdate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

// Formularzustand der Benachrichtigungs-Einstellungen; Zahlenfelder als Text (leer = nicht gesetzt)
private data class SettingsForm(
    val quietEnabled: Boolean = false,
    val from: String = "22:00",
    val to: String = "07:00",
    val days: Set<Int> = (1..7).toSet(),
    val rate: String = "",
    val threshold: String = "",
    val window: String = ""
)

// Gelesene Einstellungen plus die Schlüssel, die der Server tatsächlich geliefert hat
private class ParsedSettings(val form: SettingsForm, val present: Set<String>)

// Antwort ist ein Objekt unbekannter Form: flache Schlüssel ("notify.quiet_from"), ein verschachteltes Objekt
// "notify" oder Schlüssel ohne Präfix; Werte können Strings, Zahlen oder Booleans sein
private fun JsonObject.notifyValue(key: String): String? {
    fun prim(e: kotlinx.serialization.json.JsonElement?): String? = (e as? JsonPrimitive)?.contentOrNull
    prim(this["notify.$key"])?.let { return it }
    (this["notify"] as? JsonObject)?.let { nested -> prim(nested[key])?.let { return it } }
    return prim(this[key])
}

private fun parseDays(text: String): Set<Int> {
    val result = mutableSetOf<Int>()
    text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
        if (part.contains('-')) {
            val a = part.substringBefore('-').trim().toIntOrNull()
            val b = part.substringAfter('-').trim().toIntOrNull()
            if (a != null && b != null) result += (a..b).filter { it in 1..7 }
        } else {
            part.toIntOrNull()?.takeIf { it in 1..7 }?.let { result += it }
        }
    }
    return result
}

private fun String.toBoolLoose(): Boolean = trim().lowercase() in setOf("1", "true", "yes", "on")

private fun JsonObject.toSettings(): ParsedSettings {
    val defaults = SettingsForm()
    val present = mutableSetOf<String>()
    fun value(key: String): String? = notifyValue(key)?.also { present += key }
    val enabled = value("quiet_enabled")?.toBoolLoose() ?: defaults.quietEnabled
    val from = normalizeTimeOfDay(value("quiet_from")) ?: defaults.from
    val to = normalizeTimeOfDay(value("quiet_to")) ?: defaults.to
    val days = value("quiet_days")?.let { parseDays(it) }?.takeIf { it.isNotEmpty() } ?: defaults.days
    val rate = value("rate_per_hour")?.trim()?.toIntOrNull()?.toString() ?: ""
    val threshold = value("batch_threshold")?.trim()?.toIntOrNull()?.toString() ?: ""
    val window = value("batch_window_seconds")?.trim()?.toIntOrNull()?.toString() ?: ""
    return ParsedSettings(SettingsForm(enabled, from, to, days, rate, threshold, window), present)
}

// Formular überlebt eine Rotation (Tage als kommagetrennter Text)
private val SettingsFormSaver = listSaver<SettingsForm, Any>(
    save = { listOf(it.quietEnabled, it.from, it.to, it.days.sorted().joinToString(","), it.rate, it.threshold, it.window) },
    restore = {
        SettingsForm(
            quietEnabled = it[0] as Boolean, from = it[1] as String, to = it[2] as String,
            days = parseDays(it[3] as String), rate = it[4] as String, threshold = it[5] as String, window = it[6] as String
        )
    }
)

private fun String.nonNegativeIntOrNull(): Int? = trim().toIntOrNull()?.takeIf { it >= 0 }

// Benachrichtigungs-Einstellungen (Ruhezeiten, Sammelmeldung); PATCH sendet nur geänderte Felder
@Composable
fun Hc2SettingsView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()

    var baseline by remember { mutableStateOf<SettingsForm?>(null) }
    var present by remember { mutableStateOf<Set<String>>(emptySet()) }
    var form by rememberSaveable(stateSaver = SettingsFormSaver) { mutableStateOf(SettingsForm()) }
    // Nach einer Rotation bleibt die Eingabe erhalten; nur Pull-to-Refresh verwirft sie
    var formInitialized by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    fun load(pull: Boolean = false) {
        if (pull) refreshing = true
        req.launch(
            scope,
            spinner = if (pull) null else "Loading...",
            retry = { load() },
            onFinally = { refreshing = false }
        ) {
            val parsed = monitoringCall(ctx) { it.hc2.settings() }.toSettings()
            baseline = parsed.form
            present = parsed.present
            if (pull || !formInitialized) form = parsed.form
            formInitialized = true
        }
    }

    LaunchedEffect(Unit) { load() }

    val base = baseline
    val changed = base != null && form != base
    // Leere Rate = 0 (unbegrenzt). Schwelle/Fenster dürfen nur leer bleiben, wenn der Server keinen Wert hat
    val rateOk = form.rate.isBlank() || form.rate.nonNegativeIntOrNull() != null
    val thresholdOk = if (form.threshold.isBlank()) base?.threshold.isNullOrBlank() else form.threshold.nonNegativeIntOrNull() != null
    val windowOk = if (form.window.isBlank()) base?.window.isNullOrBlank() else form.window.nonNegativeIntOrNull() != null
    val daysOk = !form.quietEnabled || form.days.isNotEmpty()
    val valid = rateOk && thresholdOk && windowOk && daysOk

    fun save() {
        val b = base ?: return
        if (!valid) return
        val f = form
        // Ruhezeiten komplett senden, wenn sie aktiviert werden und der Server bisher keine Werte geliefert hat
        val sendQuietAll = f.quietEnabled && listOf("quiet_from", "quiet_to", "quiet_days").any { it !in present }
        val update = Hc2SettingsUpdate(
            quietEnabled = f.quietEnabled.takeIf { it != b.quietEnabled },
            quietFrom = f.from.takeIf { sendQuietAll || it != b.from },
            quietTo = f.to.takeIf { sendQuietAll || it != b.to },
            quietDays = f.days.sorted().joinToString(",").takeIf { sendQuietAll || f.days != b.days },
            ratePerHour = (if (f.rate.isBlank()) b.rate.nonNegativeIntOrNull()?.let { 0 } else f.rate.nonNegativeIntOrNull())
                ?.takeIf { it != b.rate.nonNegativeIntOrNull() },
            batchThreshold = f.threshold.nonNegativeIntOrNull()?.takeIf { it != b.threshold.nonNegativeIntOrNull() },
            batchWindowSeconds = f.window.nonNegativeIntOrNull()?.takeIf { it != b.window.nonNegativeIntOrNull() }
        )
        if (update == Hc2SettingsUpdate()) {
            // Nichts zu senden (z. B. nur ein leeres Feld ohne Serverwert): Stand übernehmen, damit nichts "dirty" bleibt
            baseline = f
            return
        }
        req.launch(scope, spinner = "Saving...") {
            val result = monitoringCall(ctx) { it.hc2.updateSettings(update) }.toSettings()
            // Antwort ohne bekannte Schlüssel: eigene Eingaben als neuen Stand übernehmen
            if (result.present.isEmpty()) {
                baseline = f
                present = present + setOf("quiet_from", "quiet_to", "quiet_days")
            } else {
                baseline = result.form
                present = present + result.present
                form = result.form
            }
            req.message("Saved", "Notification settings were updated.")
        }
    }

    ScreenScaffold(
        Tabs.getLabel(Tab.hc2_settings), navController, mainPadding,
        actions = {
            TextButton(onClick = { save() }, enabled = changed && valid && !req.isBusy) { Text("Save") }
        }
    ) {
        RefreshableList(refreshing, { load(pull = true) }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (base == null) return@RefreshableList
            item(key = "quiet") {
                FormSection("Quiet hours") {
                    FormSwitchRow(
                        "Enable quiet hours", form.quietEnabled, { form = form.copy(quietEnabled = it) },
                        supporting = "Notifications are held back during these hours."
                    )
                    if (form.quietEnabled) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TimeOfDayField("From", form.from, { form = form.copy(from = it) }, Modifier.weight(1f))
                            TimeOfDayField("To", form.to, { form = form.copy(to = it) }, Modifier.weight(1f))
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DAY_LABELS.forEachIndexed { index, label ->
                                val day = index + 1
                                FilterChip(
                                    selected = day in form.days,
                                    onClick = {
                                        form = form.copy(days = if (day in form.days) form.days - day else form.days + day)
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                        Text(
                            if (!daysOk) "Select at least one day." else "Times use the server's time zone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!daysOk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item(key = "batch") {
                FormSection("Rate limit and batching") {
                    FormNumberField(
                        "Notifications per hour", form.rate, { form = form.copy(rate = it) },
                        supporting = if (rateOk) "0 = unlimited" else "Enter a whole number."
                    )
                    FormNumberField(
                        "Batch threshold", form.threshold, { form = form.copy(threshold = it) },
                        supporting = if (thresholdOk) "From this many notifications they are combined into one." else "Enter a whole number (cannot be empty)."
                    )
                    FormNumberField(
                        "Batch window (seconds)", form.window, { form = form.copy(window = it) },
                        supporting = if (windowOk) "Time span in which notifications are counted." else "Enter a whole number (cannot be empty)."
                    )
                }
            }
        }
    }
    RequestStateHost(req)
}
