package de.rpicloud.ipv64net.helper

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

fun String.parseDbDate(): String {
    val dbFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val date: Date? = runCatching { dbFormat.parse(this) }.getOrNull()
    return date?.formatGerman() ?: "01.01.0001"
}

fun String.parseDbDateTime(): String {
    val dbFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val date: Date? = runCatching { dbFormat.parse(this) }.getOrNull()
    return date?.formatGermanTime() ?: "01.01.0001 00:00:00"
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