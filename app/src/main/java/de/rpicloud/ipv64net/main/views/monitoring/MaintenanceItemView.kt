package de.rpicloud.ipv64net.main.views.monitoring

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.sr.ipv64.model.Hc2Maintenance
import de.sr.ipv64.model.Hc2MaintenanceRepeat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Lesbarer Name des Wiederholungsmodus. */
fun Hc2MaintenanceRepeat?.label(): String = when (this) {
    Hc2MaintenanceRepeat.DAILY -> "Daily"
    Hc2MaintenanceRepeat.WEEKLY -> "Weekly"
    Hc2MaintenanceRepeat.MONTHLY -> "Monthly"
    else -> "No repeat"
}

/** Zustand eines Wartungsfensters relativ zur aktuellen Zeit. */
enum class MaintenancePhase(val label: String, val color: Int) {
    RUNNING("Running", R.color.ipv64_orange),
    SCHEDULED("Scheduled", R.color.ipv64_teal),
    ENDED("Ended", R.color.ipv64_gray),
    INACTIVE("Inactive", R.color.ipv64_gray)
}

fun Hc2Maintenance.phase(nowMs: Long = System.currentTimeMillis()): MaintenancePhase {
    if (active == false) return MaintenancePhase.INACTIVE
    val start = startAt?.toEpochMilliseconds() ?: return MaintenancePhase.ENDED
    val end = endAt?.toEpochMilliseconds() ?: return MaintenancePhase.ENDED
    val until = repeatUntil?.toEpochMilliseconds()
    val unit = when (repeat) {
        Hc2MaintenanceRepeat.DAILY -> ChronoUnit.DAYS
        Hc2MaintenanceRepeat.WEEKLY -> ChronoUnit.WEEKS
        Hc2MaintenanceRepeat.MONTHLY -> ChronoUnit.MONTHS
        else -> null
    }
    if (unit == null) {
        return when {
            nowMs in start..end -> MaintenancePhase.RUNNING
            nowMs < start -> MaintenancePhase.SCHEDULED
            else -> MaintenancePhase.ENDED
        }
    }
    // Wiederholung: Vorkommen k = Start/Ende um k Einheiten verschoben (Zeitzone des Geräts, Annäherung bei Zeitumstellung)
    val zone = ZoneId.systemDefault()
    val startZ = Instant.ofEpochMilli(start).atZone(zone)
    val endZ = Instant.ofEpochMilli(end).atZone(zone)
    val nowZ = Instant.ofEpochMilli(nowMs).atZone(zone)
    if (nowMs < start) return MaintenancePhase.SCHEDULED
    val k0 = unit.between(startZ, nowZ).coerceAtLeast(0)
    fun occStart(k: Long) = startZ.plus(k, unit).toInstant().toEpochMilli()
    fun occEnd(k: Long) = endZ.plus(k, unit).toInstant().toEpochMilli()
    for (k in longArrayOf(k0, k0 - 1)) {
        if (k < 0) continue
        if (nowMs in occStart(k)..occEnd(k) && (until == null || occStart(k) <= until)) return MaintenancePhase.RUNNING
    }
    val next = occStart(k0 + 1)
    return if (until == null || next <= until) MaintenancePhase.SCHEDULED else MaintenancePhase.ENDED
}

// Zeile der Wartungsliste: nur Daten aus der Liste, keine Einzelabfragen
@Composable
fun MaintenanceItemView(item: Hc2Maintenance, onClick: () -> Unit) {
    val phase = item.phase()
    val start = item.startAt?.formatMinute() ?: "–"
    val end = item.endAt?.formatMinute() ?: "–"
    val details = buildList {
        if (item.repeat != null && item.repeat != Hc2MaintenanceRepeat.NONE) {
            add(item.repeat.label() + (item.repeatUntil?.let { " until ${it.formatMinute()}" } ?: ""))
        }
        add(if (item.tokens.isEmpty()) "All checks" else "${item.tokens.size} selected check(s)")
        if (!item.nodeId.isNullOrBlank()) add("Managed by a node")
    }
    ListRow(
        title = item.name.orEmpty().ifBlank { "(unnamed)" },
        subtitle = "$start – $end\n" + details.joinToString(" · "),
        trailing = { StatusChip(phase.label, colorResource(phase.color)) },
        onClick = onClick
    )
}
