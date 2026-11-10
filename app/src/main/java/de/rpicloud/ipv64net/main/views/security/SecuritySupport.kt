package de.rpicloud.ipv64net.main.views.security

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import de.rpicloud.ipv64net.ui.theme.chartPalette
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
import de.rpicloud.ipv64net.main.views.common.ListGroup
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

/** ISO-Ländercode (z. B. "DE") als Flaggen-Emoji; alles andere bleibt unverändert. */
fun String.countryFlag(): String {
    val cc = trim().uppercase()
    if (cc.length != 2 || !cc.all { it in 'A'..'Z' }) return this
    return cc.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

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

/** Kennzahlen als Kacheln, zwei pro Zeile. */
@Composable
fun StatTiles(items: List<Pair<String, String>>, modifier: Modifier = Modifier, captions: List<String?> = emptyList()) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEachIndexed { ri, row ->
            // Gleiche Höhe je Zeile, auch wenn nur eine Kachel eine Caption hat
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEachIndexed { ci, (label, value) ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            captions.getOrNull(ri * 2 + ci)?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Einfaches Balkendiagramm einer Serie über die Zeit (Canvas, keine Bibliothek).
 * Eine Serie = eine Farbe ([color], je Diagramm fest vergeben); leere Tage liegen auf der leisen Spur.
 * Tippen (oder Wischen) auf einen Balken hebt ihn hervor und zeigt Datum ([labels]) und Wert.
 * Beschriftung unten: erster und letzter Tag sowie der Höchstwert, immer in Textfarben.
 */
@Composable
fun SimpleBarChart(
    values: List<Long>,
    firstLabel: String?,
    lastLabel: String?,
    color: Color,
    modifier: Modifier = Modifier,
    labels: List<String>? = null
) {
    val palette = chartPalette()
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val small = MaterialTheme.typography.labelSmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val strong = MaterialTheme.colorScheme.onSurface
    val summary = "Bar chart with ${values.size} values, maximum ${max.formatCount()}"

    Column(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = summary },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Auslesezeile mit fester Höhe, damit beim Antippen nichts springt
        val sel = selected?.takeIf { it in values.indices }
        Text(
            text = if (sel == null) {
                buildAnnotatedString { append("Tap a bar for details") }
            } else {
                buildAnnotatedString {
                    val date = labels?.getOrNull(sel)
                    if (!date.isNullOrBlank()) append("$date  ")
                    withStyle(SpanStyle(color = strong, fontWeight = FontWeight.SemiBold)) {
                        append(values[sel].formatCount())
                    }
                }
            },
            style = small,
            color = muted,
            maxLines = 1
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .pointerInput(values.size) {
                    detectTapGestures { pos ->
                        if (values.isNotEmpty()) {
                            val i = (pos.x / size.width * values.size).toInt().coerceIn(0, values.size - 1)
                            selected = if (selected == i) null else i
                        }
                    }
                }
                .pointerInput(values.size) {
                    detectHorizontalDragGestures { change, _ ->
                        if (values.isNotEmpty()) {
                            selected = (change.position.x / size.width * values.size).toInt()
                                .coerceIn(0, values.size - 1)
                        }
                    }
                }
        ) {
            if (values.isEmpty()) return@Canvas
            val n = values.size
            val slot = size.width / n
            val gap = 2.dp.toPx()
            // Balkendicke gedeckelt (24 dp), Rest ist Luft; Trefferfläche bleibt der ganze Slot
            val barW = (slot - gap).coerceIn(1f, 24.dp.toPx())
            val radius = 4.dp.toPx()
            val base = size.height
            val hasSel = sel != null
            values.forEachIndexed { i, v ->
                val h = if (v > 0) (v.toFloat() / max * base).coerceAtLeast(3.dp.toPx()) else 2.dp.toPx()
                val left = i * slot + (slot - barW) / 2f
                val isSel = i == sel
                val fill = when {
                    v <= 0L -> if (isSel) palette.baseline else palette.track
                    hasSel && !isSel -> color.copy(alpha = 0.40f)
                    else -> color
                }
                // Nur die oberen Ecken runden, unten bündig an der Grundlinie
                val r = minOf(radius, barW / 2f, h)
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left, top = base - h, right = left + barW, bottom = base,
                            topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r),
                            bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero
                        )
                    )
                }
                drawPath(path, fill)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(firstLabel.orEmpty(), style = small, color = muted)
            Text("max ${max.formatCount()}", style = small, color = muted)
            Text(lastLabel.orEmpty(), style = small, color = muted)
        }
    }
}

/**
 * Gestapeltes Balkendiagramm (zwei Serien: [bottom] unten, [top] oben), Bedienung wie [SimpleBarChart].
 * [tooltips] je Balken (Zeitpunkt), [axisLabels] je Balken (nur ein paar werden unten gezeigt).
 */
@Composable
fun StackedBarChart(
    bottom: List<Long>,
    top: List<Long>,
    bottomName: String,
    topName: String,
    bottomColor: Color,
    topColor: Color,
    tooltips: List<String>,
    axisLabels: List<String>,
    modifier: Modifier = Modifier
) {
    val palette = chartPalette()
    val n = bottom.size
    val max = (0 until n).maxOfOrNull { bottom[it] + top.getOrElse(it) { 0L } }?.coerceAtLeast(1L) ?: 1L
    var selected by remember(bottom, top) { mutableStateOf<Int?>(null) }
    val small = MaterialTheme.typography.labelSmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val strong = MaterialTheme.colorScheme.onSurface
    val summary = "Stacked bar chart with $n bars, maximum ${max.formatCount()}"
    Column(modifier = modifier.fillMaxWidth().semantics { contentDescription = summary }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val sel = selected?.takeIf { it in 0 until n }
        Text(
            text = if (sel == null) buildAnnotatedString { append("Tap a bar for details") } else buildAnnotatedString {
                append("${tooltips.getOrElse(sel) { "" }}  ")
                withStyle(SpanStyle(color = strong, fontWeight = FontWeight.SemiBold)) { append("$bottomName: ${bottom[sel].formatCount()}") }
                append("  ")
                withStyle(SpanStyle(color = strong, fontWeight = FontWeight.SemiBold)) { append("$topName: ${top.getOrElse(sel) { 0L }.formatCount()}") }
            },
            style = small, color = muted, maxLines = 1
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .pointerInput(n) {
                    detectTapGestures { pos ->
                        if (n > 0) {
                            val i = (pos.x / size.width * n).toInt().coerceIn(0, n - 1)
                            selected = if (selected == i) null else i
                        }
                    }
                }
                .pointerInput(n) {
                    detectHorizontalDragGestures { change, _ ->
                        if (n > 0) selected = (change.position.x / size.width * n).toInt().coerceIn(0, n - 1)
                    }
                }
        ) {
            if (n == 0) return@Canvas
            val h = size.height
            // Raster: Oberkante und Mitte, Grundlinie unten
            listOf(0f, h / 2f).forEach { y -> drawLine(palette.grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()) }
            drawLine(palette.baseline, Offset(0f, h), Offset(size.width, h), 1.dp.toPx())
            val slot = size.width / n
            val barW = (slot - 2.dp.toPx()).coerceIn(1f, 24.dp.toPx())
            val radius = 3.dp.toPx()
            for (i in 0 until n) {
                val a = bottom[i]
                val b = top.getOrElse(i) { 0L }
                val dim = if (sel != null && sel != i) 0.4f else 1f
                val left = i * slot + (slot - barW) / 2f
                val aH = a.toFloat() / max * h
                val bH = b.toFloat() / max * h
                if (a > 0) drawRect(bottomColor.copy(alpha = dim), Offset(left, h - aH), Size(barW, aH))
                if (b > 0) {
                    val r = minOf(radius, barW / 2f, bH)
                    val path = Path().apply {
                        addRoundRect(RoundRect(
                            left = left, top = h - aH - bH, right = left + barW, bottom = h - aH,
                            topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r),
                            bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero
                        ))
                    }
                    drawPath(path, topColor.copy(alpha = dim))
                }
            }
        }
        // Vier gleichmäßig verteilte Zeitmarken (erste bis letzte)
        if (n > 0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0, (n - 1) / 3, 2 * (n - 1) / 3, n - 1).distinct().forEach { Text(axisLabels.getOrElse(it) { "" }, style = small, color = muted) }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            listOf(bottomName to bottomColor, topName to topColor).forEach { (name, c) ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(c))
                    Text(name, style = small, color = muted)
                }
            }
            Spacer(Modifier.weight(1f))
            Text("max ${max.formatCount()}", style = small, color = muted)
        }
    }
}

/** Eine Zeile einer Rangliste. */
data class RankRow(val label: String, val value: Long, val detail: String? = null)

/**
 * Rangliste mit Balken relativ zum größten Wert. Alle Balken haben dieselbe Farbe (sie trägt keine
 * Bedeutung); Texte bleiben in Textfarben.
 */
@Composable
fun RankingCard(
    title: String,
    rows: List<RankRow>,
    modifier: Modifier = Modifier,
    monospace: Boolean = false
) {
    val palette = chartPalette()
    val track = palette.track
    ListGroup(title, modifier) {
        if (rows.isEmpty()) {
            Text(
                "No data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            val max = rows.maxOf { it.value }.coerceAtLeast(1L)
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        // Dünner Balken auf leiser Spur
                        val fraction = if (row.value > 0) (row.value.toFloat() / max).coerceIn(0.02f, 1f) else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(track)
                        ) {
                            if (fraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(palette.categorical(0))
                                )
                            }
                        }
                    }
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
