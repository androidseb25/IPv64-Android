package de.rpicloud.ipv64net.helper

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import de.rpicloud.ipv64net.models.Unit
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

fun Date.formatDbTime(): String {
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY)
    return format.format(this)
}

fun String.parseDbDateTime(): String {
    val dbFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val date: Date? = runCatching { dbFormat.parse(this) }.getOrNull()
    return date?.formatGermanTime() ?: "01.01.0001 00:00:00"
}

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

fun String.v64Units(): List<Unit> {
    return listOf(
        Unit.Minuten,
        Unit.Stunden,
        Unit.Tage
    )
}