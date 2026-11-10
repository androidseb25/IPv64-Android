package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.helper.formatDuration
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.models.Hc2StateUi
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.sr.ipv64.model.Hc2Event
import de.sr.ipv64.model.Hc2Incident
import de.sr.ipv64.model.Hc2Measurement

// Zustand eines Events: Art zuerst (down, up, degraded, degraded_end, flapping_start, flapping_end, reminder), sonst to_state
fun Hc2Event.stateUi(): Hc2StateUi = when (kind?.lowercase()) {
    "down", "reminder" -> Hc2StateUi.DOWN
    "up", "degraded_end", "flapping_end" -> Hc2StateUi.UP
    "degraded" -> Hc2StateUi.DEGRADED
    "flapping_start" -> Hc2StateUi.FLAPPING
    else -> Hc2StateUi.from(toState)
}

// Zustand einer Messung: up/down, sonst unbekannt (neutral)
fun Hc2Measurement.stateUi(): Hc2StateUi = when (status?.lowercase()) {
    "up" -> Hc2StateUi.UP
    "down" -> Hc2StateUi.DOWN
    else -> Hc2StateUi.UNKNOWN
}

// Balken-Leiste der letzten Events (neueste rechts); events kommen von der API neueste zuerst
@Composable
fun HealthcheckEventsBar(
    events: List<Hc2Event>,
    modifier: Modifier = Modifier,
    barWidth: Dp = 9.dp,
    barHeight: Dp = 40.dp,
    gap: Dp = 6.dp,
    cornerRadius: Dp = 5.dp
) {
    StatusPillsBar(events.map { it.stateUi() }, "events", modifier, barWidth, barHeight, gap, cornerRadius)
}

// Balken-Leiste der letzten Messungen (neueste rechts); measurements kommen von der API neueste zuerst
@Composable
fun HealthcheckMeasurementsBar(
    measurements: List<Hc2Measurement>,
    modifier: Modifier = Modifier,
    barWidth: Dp = 9.dp,
    barHeight: Dp = 40.dp,
    gap: Dp = 6.dp,
    cornerRadius: Dp = 5.dp
) {
    StatusPillsBar(measurements.map { it.stateUi() }, "checks", modifier, barWidth, barHeight, gap, cornerRadius)
}

// Zeichnet je Zustand eine Pille; [states] neueste zuerst, es werden so viele gezeigt wie in die Breite passen.
// [noun] ("events"/"checks") steht in der Screenreader-Zusammenfassung.
@Composable
fun StatusPillsBar(
    states: List<Hc2StateUi>,
    noun: String,
    modifier: Modifier = Modifier,
    barWidth: Dp = 9.dp,
    barHeight: Dp = 40.dp,
    gap: Dp = 6.dp,
    cornerRadius: Dp = 5.dp
) {
    val colors = Hc2StateUi.entries.associateWith { colorResource(it.color) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val visibleCount = remember(maxWidth, barWidth, gap) {
            ((maxWidth + gap) / (barWidth + gap)).toInt().coerceAtLeast(1)
        }
        val shown = states.take(visibleCount).reversed()
        // Zustand steht nur in der Farbe: für Screenreader als Zusammenfassung ausgeben (Liste darunter hat die Labels)
        val summary = remember(shown, noun) {
            if (shown.isEmpty()) "No $noun"
            else {
                val counts = shown.groupingBy { it }.eachCount().entries
                    .joinToString(", ") { "${it.value} ${it.key.label.lowercase()}" }
                "Last ${shown.size} $noun, latest: ${shown.last().label.lowercase()}, $counts"
            }
        }
        Canvas(
            modifier = Modifier.fillMaxWidth().height(barHeight).semantics {
                contentDescription = summary
            }
        ) {
            val bw = with(density) { barWidth.toPx() }
            val g = with(density) { gap.toPx() }
            val h = with(density) { barHeight.toPx() }
            val r = with(density) { cornerRadius.toPx() }
            var x = 0f
            shown.forEach { st ->
                drawRoundRect(
                    color = colors[st] ?: Color.Gray,
                    topLeft = Offset(x, size.height - h),
                    size = Size(bw, h),
                    cornerRadius = CornerRadius(r, r)
                )
                x += bw + g
            }
        }
    }
}

// Ein Event als ListRow (für eine ListGroup): Art, Zustandswechsel, Zeit, Dauer des vorigen Zustands, Messpunkt, Meldung.
// [checkName] gesetzt (Aktivität über alle Checks): Name als Titel, Art in der Untertitelzeile.
@Composable
fun HealthcheckEventItemView(event: Hc2Event, checkName: String? = null, onClick: (() -> Unit)? = null) {
    val state = event.stateUi()
    val kind = event.kind.orEmpty().replace('_', ' ').replaceFirstChar { it.uppercase() }.ifBlank { state.label }
    val meta = listOfNotNull(
        event.sourceLabel?.takeIf { it.isNotBlank() },
        event.durationSeconds?.let { "after ${it.formatDuration()}" },
        event.flaps?.takeIf { it > 0 }?.let { "$it flaps" },
        "silent".takeIf { event.silent == true }
    )
    val lines = listOfNotNull(
        kind.takeIf { checkName != null },
        if (!event.fromState.isNullOrBlank() || !event.toState.isNullOrBlank())
            "${event.fromState.orEmpty().ifBlank { "–" }} → ${event.toState.orEmpty().ifBlank { "–" }}" else null,
        meta.takeIf { it.isNotEmpty() }?.joinToString(" · "),
        event.message?.takeIf { it.isNotBlank() } ?: event.reason?.takeIf { it.isNotBlank() },
        event.time?.formatGermanTime()
    )
    ListRow(
        title = checkName ?: kind,
        subtitle = lines.joinToString("\n").ifEmpty { null },
        subtitleMaxLines = 6,
        trailing = { StatusChip(state.label, colorResource(state.color)) },
        showChevron = false,
        onClick = onClick
    )
}

// Ein Ausfall (Incident) als ListRow, Parameter wie bei [HealthcheckEventItemView]
@Composable
fun HealthcheckIncidentItemView(incident: Hc2Incident, checkName: String? = null, onClick: (() -> Unit)? = null) {
    val open = incident.status.equals("open", ignoreCase = true)
    val state = if (open) Hc2StateUi.DOWN else Hc2StateUi.UP
    val lines = listOfNotNull(
        incident.startedAt?.let { "Started ${it.formatGermanTime()}" },
        incident.endedAt?.let { "Ended ${it.formatGermanTime()}" },
        incident.durationSeconds?.let { "Duration ${it.formatDuration()}" },
        incident.message?.takeIf { it.isNotBlank() } ?: incident.reason?.takeIf { it.isNotBlank() },
        incident.sources.takeIf { it.isNotEmpty() }?.joinToString(", ")
    )
    ListRow(
        title = checkName ?: if (open) "Open incident" else "Resolved incident",
        subtitle = lines.joinToString("\n").ifEmpty { null },
        subtitleMaxLines = 6,
        trailing = { StatusChip(if (open) "Open" else "Resolved", colorResource(state.color)) },
        showChevron = false,
        onClick = onClick
    )
}
