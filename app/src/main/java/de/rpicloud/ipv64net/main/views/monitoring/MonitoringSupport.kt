package de.rpicloud.ipv64net.main.views.monitoring

import android.content.Context
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.TimedCache
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.sr.ipv64.IPv64
import de.sr.ipv64.model.Hc2Incident
import de.sr.ipv64.model.Hc2Maintenance
import de.sr.ipv64.model.StatusPage
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

// ---------------------------------------------------------------------------------------------
// Caches des Monitoring-Bereichs (melden sich selbst bei der CacheRegistry an)
// ---------------------------------------------------------------------------------------------

/** Offene Incidents (`hc2.incidents(limit = 20, open = true)`) für den Monitoring-Hub. 60 s. */
val OpenIncidentsCache: TimedCache<List<Hc2Incident>> = TimedCache(60_000L)

/** Wartungsfenster (`hc2.maintenance.list()`). 60 s. Nach jeder Änderung invalidate(). */
val MaintenanceCache: TimedCache<List<Hc2Maintenance>> = TimedCache(60_000L)

/** Statusseiten (`statusPages.list()`). 60 s. Nach jeder Änderung invalidate(). */
val StatusPageCache: TimedCache<List<StatusPage>> = TimedCache(60_000L)

/** Navigations-Schlüssel dieses Bereichs. */
object MonitoringNavKeys {
    /** Boolean: Hc2ActivityView öffnet mit dem Filter "nur offene Incidents" (Hub-Banner). */
    const val ACTIVITY_OPEN_ONLY = "HC2_ACTIVITY_OPEN_ONLY"

    /** String (Hc2StateUi-Name): HealthcheckView öffnet mit diesem Status-Filter (Hub-Kacheln). */
    const val CHECK_FILTER = "HC2_CHECK_FILTER"
}

/** Anfrage mit dem aktuellen Client; bei 429 genau ein zweiter Versuch (ipv64Retrying). Wirft IPv64Exception. */
suspend fun <T> monitoringCall(ctx: Context, block: suspend (IPv64) -> T): T {
    val api = IPv64Client.get(ctx)
    return ipv64Retrying { block(api) }
}

// ---------------------------------------------------------------------------------------------
// Zeit
// ---------------------------------------------------------------------------------------------

private val minuteFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

private fun Instant.toZoned(): ZonedDateTime =
    java.time.Instant.ofEpochMilli(toEpochMilliseconds()).atZone(ZoneId.systemDefault())

/** Datum und Uhrzeit auf die Minute, in der Zeitzone des Geräts (z. B. "24.12.2026 18:30"). */
fun Instant.formatMinute(): String = toZoned().format(minuteFormat)

private fun zonedToInstant(date: LocalDate, hour: Int, minute: Int): Instant {
    val z = ZonedDateTime.of(date, LocalTime.of(hour, minute), ZoneId.systemDefault())
    return Instant.fromEpochMilliseconds(z.toInstant().toEpochMilli())
}

/** Aktuelle Zeit, auf die volle Minute abgerundet. */
fun nowMinute(): Instant {
    val ms = System.currentTimeMillis()
    return Instant.fromEpochMilliseconds(ms - ms % 60_000L)
}

/** Zeitpunkt, der Rotation/Prozess-Neustart überlebt (als Millisekunden gespeichert, Long.MIN_VALUE = null). */
@Composable
fun rememberSaveableInstant(initial: Instant?): MutableState<Instant?> = rememberSaveable(
    stateSaver = Saver<Instant?, Long>(
        save = { it?.toEpochMilliseconds() ?: Long.MIN_VALUE },
        restore = { if (it == Long.MIN_VALUE) null else Instant.fromEpochMilliseconds(it) }
    )
) { mutableStateOf(initial) }

// ---------------------------------------------------------------------------------------------
// Datum-/Zeitfelder (Material3 Date-/TimePicker)
// ---------------------------------------------------------------------------------------------

/**
 * Feld für einen Zeitpunkt: Antippen öffnet erst den Datums-, dann den Zeitdialog (Zeitzone des Geräts).
 * @param clearable zeigt einen Löschen-Button, solange ein Wert gesetzt ist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(
    label: String,
    value: Instant?,
    onValue: (Instant?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    clearable: Boolean = false,
    error: String? = null,
    supporting: String? = null
) {
    var step by remember { mutableIntStateOf(0) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    val interaction = remember { MutableInteractionSource() }
    LaunchedEffect(interaction, enabled) {
        interaction.interactions.collect {
            if (enabled && it is PressInteraction.Release) step = 1
        }
    }

    OutlinedTextField(
        value = value?.formatMinute().orEmpty(),
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        label = { Text(label) },
        placeholder = { Text("Select date and time") },
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        trailingIcon = {
            if (clearable && value != null && enabled) {
                IconButton(onClick = { onValue(null) }) {
                    Icon(painterResource(R.drawable.close_24px), contentDescription = "Clear")
                }
            } else {
                IconButton(onClick = { step = 1 }, enabled = enabled) {
                    Icon(painterResource(R.drawable.calendar_month_24px), contentDescription = "Select")
                }
            }
        },
        interactionSource = interaction,
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )

    if (step == 1) {
        // Der DatePicker arbeitet mit UTC-Mitternacht des gewählten Tages
        val initialDay = (value?.toZoned()?.toLocalDate() ?: LocalDate.now())
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initialDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { step = 0 },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let {
                            pickedDate = java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                            step = 2
                        }
                    }
                ) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("Cancel") } }
        ) {
            DatePicker(state = state)
        }
    }

    if (step == 2) {
        val initial = value?.toZoned()
        val timeState = rememberTimePickerState(
            initialHour = initial?.hour ?: 12,
            initialMinute = initial?.minute ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { step = 0 },
            confirmButton = {
                TextButton(onClick = {
                    pickedDate?.let { onValue(zonedToInstant(it, timeState.hour, timeState.minute)) }
                    step = 0
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("Cancel") } },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TimePicker(state = timeState)
                }
            }
        )
    }
}

/** "HH:MM" aus beliebigem Text ("22:00", "22:00:00", "2200") oder null, wenn nicht lesbar. */
fun normalizeTimeOfDay(text: String?): String? {
    val s = text?.trim().orEmpty()
    if (s.isEmpty()) return null
    val parts = s.split(':')
    val (h, m) = when {
        parts.size >= 2 -> (parts[0].toIntOrNull() ?: return null) to (parts[1].toIntOrNull() ?: return null)
        s.length == 4 && s.all { it.isDigit() } -> s.substring(0, 2).toInt() to s.substring(2).toInt()
        else -> return null
    }
    if (h !in 0..23 || m !in 0..59) return null
    return "%02d:%02d".format(h, m)
}

/** Feld für eine Uhrzeit "HH:MM" (24 h), Antippen öffnet den TimePicker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeOfDayField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    LaunchedEffect(interaction, enabled) {
        interaction.interactions.collect {
            if (enabled && it is PressInteraction.Release) open = true
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { open = true }, enabled = enabled) {
                Icon(painterResource(R.drawable.schedule_24px), contentDescription = "Select")
            }
        },
        interactionSource = interaction,
        singleLine = true,
        modifier = modifier
    )
    if (open) {
        val parsed = normalizeTimeOfDay(value)
        val state = rememberTimePickerState(
            initialHour = parsed?.substringBefore(':')?.toIntOrNull() ?: 0,
            initialMinute = parsed?.substringAfter(':')?.toIntOrNull() ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onValue("%02d:%02d".format(state.hour, state.minute))
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TimePicker(state = state)
                }
            }
        )
    }
}
