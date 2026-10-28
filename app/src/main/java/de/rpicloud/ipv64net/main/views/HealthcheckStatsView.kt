package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import de.sr.ipv64.model.Hc2Stats
import de.sr.ipv64.model.Hc2StatsRange

fun Hc2StatsRange.chipLabel(): String = when (this) {
    Hc2StatsRange.HOURS_24 -> "24 h"
    Hc2StatsRange.DAYS_7 -> "7 d"
    Hc2StatsRange.DAYS_30 -> "30 d"
    Hc2StatsRange.YEAR_1 -> "1 y"
}

// Statistik-Karte: Zeitraum-Chips, Verfügbarkeit, Messungen, Latenz je Messpunkt und kleiner Latenzverlauf
@Composable
fun HealthcheckStatsView(
    range: Hc2StatsRange,
    onRangeChange: (Hc2StatsRange) -> Unit,
    stats: Hc2Stats?,
    loading: Boolean,
    error: String?,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Hc2StatsRange.entries.forEach { r ->
                    FilterChip(
                        selected = r == range,
                        onClick = { onRangeChange(r) },
                        label = { Text(r.chipLabel()) }
                    )
                }
            }
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                error != null -> Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                stats == null -> Text("No data", color = Color.Gray)
                else -> {
                    StatRow("Availability", stats.availabilityPercent.formatPercent())
                    StatRow("Measurements", stats.measurements?.toString() ?: "–")
                    stats.sources.forEach { s ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                s.label ?: s.source.orEmpty(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Availability ${s.availabilityPercent.formatPercent()} · ${s.measurements ?: 0} measurements",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            Text(
                                "Latency min ${s.latencyMs?.min.formatLatency()} · avg ${s.latencyMs?.avg.formatLatency()} · max ${s.latencyMs?.max.formatLatency()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                    LatencyChart(stats)
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.titleSmall, color = Color.Gray)
    }
}

// Linienverlauf der durchschnittlichen Latenz, eine Linie je Messpunkt
@Composable
private fun LatencyChart(stats: Hc2Stats) {
    val bySource = stats.series.filter { it.latencyAvgMs != null }.groupBy { it.source.orEmpty() }
    if (bySource.isEmpty() || stats.series.size < 2) return
    val maxValue = stats.series.mapNotNull { it.latencyAvgMs }.max().coerceAtLeast(1.0)
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary
    )
    Text("Average latency (max ${maxValue.formatLatency()})", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
        bySource.values.forEachIndexed { index, points ->
            if (points.size < 2) return@forEachIndexed
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = size.width * i / (points.size - 1)
                val y = size.height - (size.height * ((p.latencyAvgMs ?: 0.0) / maxValue)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, palette[index % palette.size], style = Stroke(width = 3f))
        }
        drawLine(Color.Gray.copy(alpha = 0.4f), Offset(0f, size.height), Offset(size.width, size.height), 1f)
    }
}
