package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.helper.formatDuration
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.models.Hc2StateUi
import de.sr.ipv64.model.Hc2Event
import de.sr.ipv64.model.Hc2Incident

// Zustand eines Events: Art zuerst (down, up, degraded, degraded_end, flapping_start, flapping_end, reminder), sonst to_state
fun Hc2Event.stateUi(): Hc2StateUi = when (kind?.lowercase()) {
    "down", "reminder" -> Hc2StateUi.DOWN
    "up", "degraded_end", "flapping_end" -> Hc2StateUi.UP
    "degraded" -> Hc2StateUi.DEGRADED
    "flapping_start" -> Hc2StateUi.FLAPPING
    else -> Hc2StateUi.from(toState)
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
    val colors = Hc2StateUi.entries.associateWith { colorResource(it.color) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val visibleCount = remember(maxWidth, barWidth, gap) {
            ((maxWidth + gap) / (barWidth + gap)).toInt().coerceAtLeast(1)
        }
        val shown = events.take(visibleCount).reversed()
        Canvas(modifier = Modifier.fillMaxWidth().height(barHeight)) {
            val bw = with(density) { barWidth.toPx() }
            val g = with(density) { gap.toPx() }
            val h = with(density) { barHeight.toPx() }
            val r = with(density) { cornerRadius.toPx() }
            var x = 0f
            shown.forEach { e ->
                drawRoundRect(
                    color = colors[e.stateUi()] ?: Color.Gray,
                    topLeft = Offset(x, size.height - h),
                    size = Size(bw, h),
                    cornerRadius = CornerRadius(r, r)
                )
                x += bw + g
            }
        }
    }
}

// Ein Event: Art, Zustandswechsel, Zeit, Dauer des vorigen Zustands, Messpunkt, Meldung
@Composable
fun HealthcheckEventItemView(event: Hc2Event) {
    val state = event.stateUi()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    event.kind.orEmpty().replace('_', ' ').replaceFirstChar { it.uppercase() }.ifBlank { state.label },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(state.color)
                )
                if (event.silent == true) {
                    Text("silent", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
            if (!event.fromState.isNullOrBlank() || !event.toState.isNullOrBlank()) {
                Text(
                    "${event.fromState.orEmpty().ifBlank { "–" }} → ${event.toState.orEmpty().ifBlank { "–" }}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            val meta = listOfNotNull(
                event.sourceLabel?.takeIf { it.isNotBlank() },
                event.durationSeconds?.let { "after ${it.formatDuration()}" },
                event.flaps?.takeIf { it > 0 }?.let { "$it flaps" }
            )
            if (meta.isNotEmpty()) {
                Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            val text = event.message?.takeIf { it.isNotBlank() } ?: event.reason?.takeIf { it.isNotBlank() }
            if (text != null) {
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
            event.time?.let {
                Text(it.formatGermanTime(), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

// Ein Ausfall (Incident)
@Composable
fun HealthcheckIncidentItemView(incident: Hc2Incident) {
    val open = incident.status.equals("open", ignoreCase = true)
    val state = if (open) Hc2StateUi.DOWN else Hc2StateUi.UP
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (open) "Open incident" else "Resolved incident",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(state.color)
                )
                incident.durationSeconds?.let {
                    Text(it.formatDuration(), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                }
            }
            incident.startedAt?.let {
                Text("Started ${it.formatGermanTime()}", style = MaterialTheme.typography.bodySmall)
            }
            incident.endedAt?.let {
                Text("Ended ${it.formatGermanTime()}", style = MaterialTheme.typography.bodySmall)
            }
            val text = incident.message?.takeIf { it.isNotBlank() } ?: incident.reason?.takeIf { it.isNotBlank() }
            if (text != null) {
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
            if (incident.sources.isNotEmpty()) {
                Text(incident.sources.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}
