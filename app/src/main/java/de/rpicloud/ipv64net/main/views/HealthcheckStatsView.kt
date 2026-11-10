package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.pointerInput
import de.rpicloud.ipv64net.ui.theme.chartPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.helper.formatLatency
import de.rpicloud.ipv64net.helper.formatPercent
import de.rpicloud.ipv64net.main.views.common.FilterChipRow
import de.rpicloud.ipv64net.main.views.common.InfoRow
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.sr.ipv64.model.Hc2Stats
import de.sr.ipv64.model.Hc2StatsRange

fun Hc2StatsRange.chipLabel(): String = when (this) {
    Hc2StatsRange.HOURS_24 -> "24 h"
    Hc2StatsRange.DAYS_7 -> "7 d"
    Hc2StatsRange.DAYS_30 -> "30 d"
    Hc2StatsRange.YEAR_1 -> "1 y"
}

// Statistik-Gruppe: Zeitraum-Chips, Verfügbarkeit, Messungen, Latenz je Messpunkt und kleiner Latenzverlauf
@Composable
fun HealthcheckStatsView(
    range: Hc2StatsRange,
    onRangeChange: (Hc2StatsRange) -> Unit,
    stats: Hc2Stats?,
    loading: Boolean,
    error: String?,
    modifier: Modifier = Modifier
) {
    ListGroup(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChipRow(
                options = Hc2StatsRange.entries,
                selected = range,
                label = { it.chipLabel() },
                onSelect = onRangeChange
            )
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                error != null -> Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                stats == null -> Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    InfoRow("Availability", stats.availabilityPercent.formatPercent())
                    InfoRow("Measurements", stats.measurements?.toString() ?: "–")
                    stats.sources.forEach { s ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(s.label ?: s.source.orEmpty(), style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Availability ${s.availabilityPercent.formatPercent()} · ${s.measurements ?: 0} measurements",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Latency min ${s.latencyMs?.min.formatLatency()} · avg ${s.latencyMs?.avg.formatLatency()} · max ${s.latencyMs?.max.formatLatency()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    LatencyChart(stats)
                }
            }
        }
    }
}

// Ein Messpunkt im Latenzdiagramm: Farbe folgt dem Messpunkt (Namensreihenfolge), nicht der Zeile
private class LatencyLine(val name: String, val color: Color, val points: List<Pair<Long, Double>>)

// Buckets ab Tagesebene (z. B. "1d", "day", "1w") haben keine sinnvolle Uhrzeit; unbekannte Werte zeigen sie
private fun isDateOnlyBucket(bucket: String?): Boolean =
    bucket != null && bucket.trim().lowercase().trimStart { it.isDigit() }.trimEnd('s') in setOf("d", "w", "mo", "y", "day", "week", "month", "year")

// Zeitstempel für die Auslesezeile; Tages-Buckets ohne Uhrzeit, in UTC (sonst Vortag westlich von UTC)
private fun formatBucketTime(ms: Long, dateOnly: Boolean): String =
    SimpleDateFormat(if (dateOnly) "dd.MM.yyyy" else "dd.MM.yyyy HH:mm", Locale.getDefault()).apply {
        if (dateOnly) timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(ms))

// Linienverlauf der durchschnittlichen Latenz, eine Linie je Messpunkt (>= 2 Messpunkte mit Legende).
// Tippen oder Wischen zeigt die Werte aller Messpunkte an dieser Zeit.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LatencyChart(stats: Hc2Stats) {
    val palette = chartPalette()
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val strong = MaterialTheme.colorScheme.onSurface
    val dayBucket = isDateOnlyBucket(stats.bucket)

    val lines = remember(stats, palette) {
        val names = stats.sources.associate { it.source.orEmpty() to (it.label ?: it.source.orEmpty()) }
        val grouped = stats.series.filter { it.latencyAvgMs != null && it.time != null }
            .groupBy { it.source.orEmpty() }
        // Stabile Reihenfolge nach Quellname über alle Messpunkte, damit Farben der Entität folgen
        val order = (stats.sources.map { it.source.orEmpty() } + grouped.keys).distinct().sorted()
        order.mapNotNull { key ->
            val pts = grouped[key]?.map { it.time!!.toEpochMilliseconds() to it.latencyAvgMs!! }
                ?.sortedBy { it.first }
            if (pts.isNullOrEmpty()) null
            else LatencyLine(names[key]?.ifBlank { null } ?: key.ifBlank { "–" }, palette.categorical(order.indexOf(key)), pts)
        }
    }
    if (lines.isEmpty() || stats.series.size < 2) return

    val times = remember(lines) { lines.flatMap { l -> l.points.map { it.first } }.distinct().sorted() }
    val tMin = times.first()
    val tMax = times.last()
    val maxValue = lines.flatMap { l -> l.points.map { it.second } }.max().coerceAtLeast(1.0)
    var selected by remember(stats) { mutableStateOf<Int?>(null) }
    val sel = selected?.takeIf { it in times.indices }
    val multi = lines.size >= 2

    Text("Average latency (max ${maxValue.formatLatency()})", style = MaterialTheme.typography.labelMedium, color = muted)
    // Auslesezeile mit fester Höhe: Hinweis oder Zeitpunkt samt Werten, damit das Diagramm beim Berühren nicht springt
    val readout = if (sel == null) "Touch the chart for values" else {
        val values = lines.joinToString(" · ") { l ->
            val v = l.points.firstOrNull { it.first == times[sel] }?.second
            if (multi) "${l.name}: ${v.formatLatency()}" else v.formatLatency()
        }
        formatBucketTime(times[sel], dayBucket) + (if (multi) "\n" else " · ") + values
    }
    val readoutLines = if (multi) 2 else 1
    Text(
        readout,
        style = MaterialTheme.typography.labelSmall,
        color = if (sel == null) muted else strong,
        fontWeight = if (sel == null) null else FontWeight.SemiBold,
        minLines = readoutLines,
        maxLines = readoutLines
    )
    // Legende ab 2 Serien (nur Namen). Text in Textfarben, Farbe nur im Schlüssel.
    if (multi) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            lines.forEach { l ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(width = 14.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(l.color))
                    Text(l.name, style = MaterialTheme.typography.labelSmall, color = muted)
                }
            }
        }
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics {
                contentDescription = "Latency chart, ${lines.size} sources, max ${maxValue.formatLatency()}"
            }
            .pointerInput(times) {
                detectTapGestures { pos ->
                    val i = nearestIndex(times, tMin, tMax, pos.x / size.width)
                    selected = if (selected == i) null else i
                }
            }
            .pointerInput(times) {
                detectHorizontalDragGestures { change, _ -> selected = nearestIndex(times, tMin, tMax, change.position.x / size.width) }
            }
    ) {
        val top = 6.dp.toPx()
        val h = size.height - top
        val span = (tMax - tMin).coerceAtLeast(1L).toFloat()
        fun xOf(t: Long) = if (tMax == tMin) size.width / 2f else size.width * (t - tMin) / span
        fun yOf(v: Double) = top + h - (h * (v / maxValue)).toFloat()
        val hair = 1.dp.toPx()
        // Haarlinien-Raster bei 50 % und 100 %, Grundlinie unten
        listOf(0.5f, 1f).forEach { f ->
            val y = top + h - h * f
            drawLine(palette.grid, Offset(0f, y), Offset(size.width, y), hair)
        }
        drawLine(palette.baseline, Offset(0f, size.height - hair / 2), Offset(size.width, size.height - hair / 2), hair)
        if (sel != null) {
            val x = xOf(times[sel])
            drawLine(palette.baseline, Offset(x, top), Offset(x, size.height), hair)
        }
        lines.forEach { l ->
            if (l.points.size == 1) {
                // Einzelner Messpunkt: eine Linie wäre unsichtbar
                drawCircle(l.color, radius = 3.dp.toPx(), center = Offset(xOf(l.points[0].first), yOf(l.points[0].second)))
            } else if (l.points.size >= 2) {
                val path = Path()
                l.points.forEachIndexed { i, (t, v) ->
                    if (i == 0) path.moveTo(xOf(t), yOf(v)) else path.lineTo(xOf(t), yOf(v))
                }
                drawPath(path, l.color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        // Marker mit 2 dp Ring in Oberflächenfarbe an der gewählten Zeit
        if (sel != null) {
            val t = times[sel]
            lines.forEach { l ->
                l.points.firstOrNull { it.first == t }?.let { (_, v) ->
                    val c = Offset(xOf(t), yOf(v))
                    drawCircle(surface, radius = 6.dp.toPx(), center = c)
                    drawCircle(l.color, radius = 4.dp.toPx(), center = c)
                }
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatBucketTime(tMin, dayBucket), style = MaterialTheme.typography.labelSmall, color = muted)
        if (tMax != tMin) Text(formatBucketTime(tMax, dayBucket), style = MaterialTheme.typography.labelSmall, color = muted)
    }
}

// Nächster Zeitpunkt (Index in [times]) zur Position [fraction] (0..1) auf der Zeitachse.
// Relativ zu [tMin] gerechnet: Float hat bei Epoch-Millisekunden nur ~2 Minuten Auflösung.
internal fun nearestIndex(times: List<Long>, tMin: Long, tMax: Long, fraction: Float): Int {
    val target = (tMax - tMin) * fraction.coerceIn(0f, 1f)
    var best = 0
    var bestD = Float.MAX_VALUE
    times.forEachIndexed { i, t ->
        val d = kotlin.math.abs((t - tMin) - target)
        if (d < bestD) { bestD = d; best = i }
    }
    return best
}
