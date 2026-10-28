package de.rpicloud.ipv64net.helper

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import de.sr.ipv64.model.UsageCounter
import de.sr.ipv64.model.VpnTrafficUsage
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import kotlin.time.Instant

fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun Date.formatGerman(): String {
    val format = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
    return format.format(this)
}

fun Date.formatGermanTime(): String {
    val format = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY)
    return format.format(this)
}

fun Instant.formatGermanTime(): String = Date(toEpochMilliseconds()).formatGermanTime()

@SuppressLint("DefaultLocale")
fun Long.formatBytes(): String {
    val unit = 1024
    if (this < unit) return "$this B"
    val exp = (ln(this.toDouble()) / ln(unit.toDouble())).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", this / unit.toDouble().pow(exp.toDouble()), pre)
}

fun String.v64domains(): List<String> {
    return listOf(
        "ipv64.net",
        "ipv64.de",
        "any64.de",
        "api64.de",
        "dns64.de",
        "dyndns64.de",
        "eth64.de",
        "dynipv6.de",
        "home64.de",
        "iot64.de",
        "lan64.de",
        "nas64.de",
        "root64.de",
        "route64.de",
        "srv64.de",
        "tcp64.de",
        "udp64.de",
        "vpn64.de",
        "wan64.de",
        "Own Domain"
    )
}

// Ganzzahlen ohne Nachkommastellen, sonst höchstens 2 (z. B. Traffic in GB)
fun Double.formatUsage(): String {
    val nf = NumberFormat.getNumberInstance(Locale.getDefault())
    nf.maximumFractionDigits = 2
    return nf.format(this)
}

// Limit null bedeutet unbegrenzt; fehlt der Zähler (noch nicht geladen), wird "–" angezeigt
fun UsageCounter?.usageText(): String {
    if (this == null) return "–"
    val usedStr = (used ?: 0.0).formatUsage()
    val maxStr = limit?.let { it.toDouble().formatUsage() } ?: "∞"
    return "$usedStr / $maxStr"
}

// Für VPN-Traffic ist "null = unbegrenzt" nicht dokumentiert, daher "–" bei fehlendem Limit
fun VpnTrafficUsage?.usageText(): String {
    if (this == null) return "–"
    val usedStr = (used ?: 0.0).formatUsage()
    val maxStr = limit?.let { it.toDouble().formatUsage() } ?: "–"
    val text = "$usedStr / $maxStr"
    return if (limited == true) "$text (limit reached)" else text
}

fun String.v64DnsRecordTypes(): List<String> {
    return listOf(
        "A",
        "AAAA",
        "TXT",
        "MX",
        "NS",
        "SRV",
        "CNAME",
        "TLSA",
        "CAA"
    )
}

// Relative Zeit ("just now", "5 min ago", "2 h ago", "3 d ago"), ab 30 Tagen das Datum
fun Instant.formatRelative(): String {
    val diffSeconds = (System.currentTimeMillis() - toEpochMilliseconds()) / 1000
    return when {
        diffSeconds < 0 -> formatGermanTime()
        diffSeconds < 60 -> "just now"
        diffSeconds < 3_600 -> "${diffSeconds / 60} min ago"
        diffSeconds < 86_400 -> "${diffSeconds / 3_600} h ago"
        diffSeconds < 30 * 86_400L -> "${diffSeconds / 86_400} d ago"
        else -> Date(toEpochMilliseconds()).formatGerman()
    }
}

// Dauer in Sekunden lesbar (höchstens zwei Einheiten), z. B. "1 h 30 min"
fun Long.formatDuration(): String {
    if (this <= 0) return "0 s"
    val units = listOf(86_400L to "d", 3_600L to "h", 60L to "min", 1L to "s")
    var rest = this
    val parts = mutableListOf<String>()
    for ((size, unit) in units) {
        val value = rest / size
        if (value > 0) {
            parts += "$value $unit"
            rest -= value * size
        }
    }
    return parts.take(2).joinToString(" ")
}

// Prozentwert mit höchstens zwei Nachkommastellen; null → "–"
fun Double?.formatPercent(): String {
    if (this == null) return "–"
    val nf = NumberFormat.getNumberInstance(Locale.getDefault())
    nf.maximumFractionDigits = 2
    return "${nf.format(this)} %"
}

// Latenz in ms; null → "–"
fun Double?.formatLatency(): String {
    if (this == null) return "–"
    val nf = NumberFormat.getNumberInstance(Locale.getDefault())
    nf.maximumFractionDigits = if (this < 10) 1 else 0
    return "${nf.format(this)} ms"
}

// Dynamischen Text für ErrorDialog (rendert HTML) maskieren; Zeilenumbrüche als <br>
fun htmlText(s: String): String = android.text.TextUtils.htmlEncode(s).replace("\n", "<br>")
