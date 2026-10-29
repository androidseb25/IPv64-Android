package de.rpicloud.ipv64net.main.views.security

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.TimedCache
import de.sr.ipv64.model.HoneypotSensor
import de.sr.ipv64.model.HoneypotStats
import de.sr.ipv64.model.ThreatIpStatus
import de.sr.ipv64.model.ThreatReleaseStatus
import de.sr.ipv64.model.ThreatStats
import java.text.NumberFormat

// =============================================================================================
// Caches
// =============================================================================================

/** Öffentliche Threat-Statistik (serverseitig bis 10 Minuten gecacht). */
val ThreatStatsCache = TimedCache<ThreatStats>(10 * 60_000L)

/** Eigene Honeypot-Statistik und Sensoren (zwei getrennte Abrufe, nacheinander geladen). */
val HoneypotStatsCache = TimedCache<HoneypotStats>(2 * 60_000L)
val HoneypotSensorsCache = TimedCache<List<HoneypotSensor>>(2 * 60_000L)

// =============================================================================================
// Lokale Prüfung von IP-Adressen und Netzen (keine DNS-Auflösung, kein Netzwerkzugriff)
// =============================================================================================

/** Ergebnis von [parseNetwork]: Adressfamilie und optionale Präfixlänge (null = einzelne IP). */
data class ParsedNetwork(val isV6: Boolean, val prefix: Int?)

private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

/** Strenge IPv4-Prüfung (vier Dezimalblöcke 0–255, keine führenden Nullen). */
fun isValidIpv4(s: String): Boolean {
    val parts = s.split('.')
    if (parts.size != 4) return false
    return parts.all { p ->
        p.isNotEmpty() && p.length <= 3 && p.all { it in '0'..'9' } &&
                (p.length == 1 || p[0] != '0') && p.toInt() in 0..255
    }
}

// Anzahl 16-Bit-Gruppen eines IPv6-Teils; -1 = ungültig. Eine eingebettete IPv4-Adresse zählt als zwei Gruppen.
private fun ipv6Groups(part: String, allowV4Last: Boolean): Int {
    if (part.isEmpty()) return 0
    val groups = part.split(':')
    var count = 0
    groups.forEachIndexed { i, g ->
        if (g.contains('.')) {
            if (!(allowV4Last && i == groups.lastIndex) || !isValidIpv4(g)) return -1
            count += 2
        } else {
            if (g.isEmpty() || g.length > 4 || !g.all { isHex(it) }) return -1
            count++
        }
    }
    return count
}

/** IPv6-Prüfung inkl. "::"-Kurzform und eingebetteter IPv4-Adresse; ohne Zonen-ID. */
fun isValidIpv6(s: String): Boolean {
    if (!s.contains(':') || s.contains(":::")) return false
    val idx = s.indexOf("::")
    if (idx < 0) return ipv6Groups(s, true) == 8
    if (s.indexOf("::", idx + 1) >= 0) return false
    val head = ipv6Groups(s.substring(0, idx), false)
    val tail = ipv6Groups(s.substring(idx + 2), true)
    return head >= 0 && tail >= 0 && head + tail <= 7
}

fun isValidIp(s: String): Boolean = isValidIpv4(s) || isValidIpv6(s)

/** Eingabe bereinigen: Leerraum entfernen, "[::1]" → "::1". */
fun normalizeIpInput(s: String): String {
    val t = s.trim()
    return if (t.length > 2 && t.startsWith("[") && t.endsWith("]")) t.substring(1, t.length - 1) else t
}

/** "ip" oder "ip/prefix" prüfen; null = ungültig. */
fun parseNetwork(input: String): ParsedNetwork? {
    val t = input.trim()
    val slash = t.indexOf('/')
    val ip = if (slash < 0) t else t.substring(0, slash)
    val v4 = isValidIpv4(ip)
    val v6 = !v4 && isValidIpv6(ip)
    if (!v4 && !v6) return null
    if (slash < 0) return ParsedNetwork(isV6 = v6, prefix = null)
    val ps = t.substring(slash + 1)
    if (ps.isEmpty() || ps.length > 3 || !ps.all { it in '0'..'9' }) return null
    val p = ps.toInt()
    if (p > (if (v6) 128 else 32)) return null
    return ParsedNetwork(isV6 = v6, prefix = p)
}

// =============================================================================================
// Formatierung
// =============================================================================================

fun Long.formatCount(): String = NumberFormat.getInstance().format(this)

/** "2026-10-20" → "20.10." (für Diagrammbeschriftungen). */
fun shortDay(day: String?): String {
    val parts = day?.split('-') ?: return ""
    return if (parts.size == 3) "${parts[2]}.${parts[1]}." else day
}

/** "ssh:bruteforce" bleibt lesbar; Unterstriche in Schlüsseln werden zu Leerzeichen. */
fun prettyKey(key: String): String = key.replace('_', ' ')

fun ThreatIpStatus?.label(listed: Boolean?): String = when (this) {
    ThreatIpStatus.LISTED -> "Listed"
    ThreatIpStatus.NOT_LISTED -> "Not listed"
    ThreatIpStatus.RELEASED -> "Released"
    ThreatIpStatus.ALLOWLISTED -> "Allowlisted"
    null -> if (listed == true) "Listed" else "Not listed"
}

@Composable
fun ThreatIpStatus?.color(listed: Boolean?): Color = when (this) {
    ThreatIpStatus.LISTED -> colorResource(R.color.ipv64_red)
    ThreatIpStatus.NOT_LISTED -> colorResource(R.color.ipv64_green)
    ThreatIpStatus.RELEASED -> colorResource(R.color.ipv64_orange)
    ThreatIpStatus.ALLOWLISTED -> colorResource(R.color.ipv64_teal)
    null -> if (listed == true) colorResource(R.color.ipv64_red) else colorResource(R.color.ipv64_green)
}

fun ThreatReleaseStatus.label(): String = when (this) {
    ThreatReleaseStatus.NONE -> "No release"
    ThreatReleaseStatus.IN_REVIEW -> "In review"
    ThreatReleaseStatus.RELEASED -> "Released"
    ThreatReleaseStatus.ALLOWLISTED -> "Allowlisted"
    ThreatReleaseStatus.REJECTED -> "Rejected"
}

// =============================================================================================
// Bausteine
// =============================================================================================

/** Karte mit einheitlichem Innenabstand. */
@Composable
fun SecurityCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

/** Zeile aus Bezeichnung links und Wert rechts. */
@Composable
fun KeyValueLine(label: String, value: String, modifier: Modifier = Modifier, monospace: Boolean = false) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.padding(horizontal = 6.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Kennzahlen als Kacheln, zwei pro Zeile. */
@Composable
fun StatTiles(items: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (label, value) ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Einfaches Balkendiagramm (Canvas, keine Bibliothek).
 * Beschriftung: erster und letzter Tag sowie der Höchstwert.
 */
@Composable
fun SimpleBarChart(
    values: List<Long>,
    firstLabel: String?,
    lastLabel: String?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(96.dp)) {
            if (values.isEmpty()) return@Canvas
            val n = values.size
            val gap = 2.dp.toPx()
            val barW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
            values.forEachIndexed { i, v ->
                val h = if (v > 0) (v.toFloat() / max * size.height).coerceAtLeast(2.dp.toPx()) else 1.dp.toPx()
                drawRoundRect(
                    color = if (v > 0) color else color.copy(alpha = 0.25f),
                    topLeft = Offset(i * (barW + gap), size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val small = MaterialTheme.typography.labelSmall
            val tint = MaterialTheme.colorScheme.onSurfaceVariant
            Text(firstLabel.orEmpty(), style = small, color = tint)
            Text("max ${max.formatCount()}", style = small, color = tint)
            Text(lastLabel.orEmpty(), style = small, color = tint)
        }
    }
}

/** Eine Zeile einer Rangliste. */
data class RankRow(val label: String, val value: Long, val detail: String? = null)

/** Rangliste mit Balken relativ zum größten Wert. */
@Composable
fun RankingCard(title: String, rows: List<RankRow>, modifier: Modifier = Modifier, monospace: Boolean = false) {
    SecurityCard(modifier) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (rows.isEmpty()) {
            Text("No data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val max = rows.maxOf { it.value }.coerceAtLeast(1L)
            rows.forEach { row ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            row.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = if (monospace) FontFamily.Monospace else null,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.padding(horizontal = 6.dp))
                        Text(
                            row.detail ?: row.value.formatCount(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LinearProgressIndicator(
                        progress = { row.value.toFloat() / max },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Ruft [onStop] auf, wenn die App in den Hintergrund geht. Dient dazu, Geheimnisse (Passwort, Listen-Token)
 * aus dem Bildschirmzustand zu entfernen.
 */
@Composable
fun ClearOnStop(onStop: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onStop() }
}
