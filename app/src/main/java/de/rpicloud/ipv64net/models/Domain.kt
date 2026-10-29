package de.rpicloud.ipv64net.models

import androidx.compose.ui.graphics.Color
import de.rpicloud.ipv64net.helper.v64domains
import de.sr.ipv64.model.Domain
import de.sr.ipv64.model.DomainDetail
import de.sr.ipv64.model.DomainKind
import de.sr.ipv64.model.DynDnsOutcome
import de.sr.ipv64.model.IgnoreFamily
import de.sr.ipv64.model.Record
import de.sr.ipv64.model.RecordState
import de.sr.ipv64.model.RecordType

const val NO_IPV4 = "0.0.0.0"
const val NO_IPV6 = "::"

val Domain.IsWildcard: String
    get() = if (wildcard == true) "yes" else "no"

val DomainDetail.IsWildcard: String
    get() = if (wildcard == true) "yes" else "no"

// IPs der A- und AAAA-Records der Domain
val DomainDetail.ipv4: String
    get() = records.firstOrNull { it.type == RecordType.A }?.content ?: NO_IPV4

val DomainDetail.ipv6: String
    get() = records.firstOrNull { it.type == RecordType.AAAA }?.content ?: NO_IPV6

// Die Vergleichs-IPs sind die aktuellen IPs des Geräts, nicht die der Domain
fun DomainDetail.isSameTypeAAddress(myIpv4: String): Boolean =
    records.any { it.type == RecordType.A && it.content == myIpv4 }

fun DomainDetail.isSameTypeAAAAAddress(myIpv6: String): Boolean =
    records.any { it.type == RecordType.AAAA && it.content == myIpv6 }

fun DomainDetail?.tintColor(myIpv4: String, myIpv6: String): Color = when {
    this == null -> Color.Gray
    isSameTypeAAddress(myIpv4) || isSameTypeAAAAAddress(myIpv6) -> Color.Green
    else -> Color.Red
}

val Domain.baseDomain: String
    get() {
        val parts = name?.split(".")
        parts?.size?.let {
            if (it >= 2) {
                val domain = parts.takeLast(2).joinToString(".")
                return if (domain in String().v64domains()) domain else "Own Domain"
            }
        }
        return "Own Domain"
    }


// Platzhalter-IPs (unbekannte eigene IP) liefern null
fun knownIp(ip: String?): String? =
    ip?.takeIf { it.isNotBlank() && it != NO_IPV4 && it != NO_IPV6 && it != "::1" }

// ---------------------------------------------------------------------------------------------
// Domain-Art (Filter-Chips der Domainliste)
// ---------------------------------------------------------------------------------------------

enum class DomainKindFilter(val label: String) { ALL("All"), DYNDNS("DynDNS"), OWN("Own") }

// Art der Domain; fehlt sie in der Antwort, wird sie aus der Zone abgeleitet (IPv64-Zone = DynDNS)
val Domain.effectiveKind: DomainKind
    get() = kind ?: if (baseDomain == "Own Domain") DomainKind.OWN else DomainKind.DYNDNS

fun Domain.matches(filter: DomainKindFilter): Boolean = when (filter) {
    DomainKindFilter.ALL -> true
    DomainKindFilter.DYNDNS -> effectiveKind == DomainKind.DYNDNS
    DomainKindFilter.OWN -> effectiveKind == DomainKind.OWN
}

val DomainKind?.label: String
    get() = when (this) {
        DomainKind.DYNDNS -> "DynDNS"
        DomainKind.OWN -> "Own domain"
        null -> "–"
    }

val IgnoreFamily?.label: String
    get() = when (this) {
        IgnoreFamily.IPV4 -> "Ignore IPv4"
        IgnoreFamily.IPV6 -> "Ignore IPv6"
        IgnoreFamily.NONE, null -> "None"
    }

// Update-URL für Router/Skripte (enthält das geheime Update-Token: nur sensibel kopieren, nie loggen)
fun domainUpdateUrl(updateToken: String): String = "https://ipv64.net/nic/update?key=$updateToken"

// ---------------------------------------------------------------------------------------------
// Records
// ---------------------------------------------------------------------------------------------

// Von CDN oder DNS-Failover verwaltete Records sind gesperrt (nicht bearbeitbar/löschbar)
val Record.isLocked: Boolean
    get() = when (state) {
        RecordState.CDN_RECORD, RecordState.CDN_PROXIED, RecordState.CDN_PARTIAL,
        RecordState.FAILOVER_RECORD, RecordState.FAILOVER_REPLACED -> true

        else -> false
    }

val RecordState.label: String
    get() = when (this) {
        RecordState.ACTIVE -> "Active"
        RecordState.INACTIVE -> "Inactive"
        RecordState.CDN_RECORD -> "CDN"
        RecordState.CDN_PROXIED -> "CDN proxied"
        RecordState.CDN_PARTIAL -> "CDN partial"
        RecordState.FAILOVER_RECORD -> "Failover"
        RecordState.FAILOVER_REPLACED -> "Replaced by failover"
    }

// Hinweistext für gesperrte Records
val Record.lockReason: String?
    get() = when (state) {
        RecordState.CDN_RECORD, RecordState.CDN_PROXIED, RecordState.CDN_PARTIAL ->
            "Managed by the CDN. Change it in the CDN settings."

        RecordState.FAILOVER_RECORD, RecordState.FAILOVER_REPLACED ->
            "Managed by DNS failover. Change it in the failover policy."

        else -> null
    }

// Präfix für die Anzeige; leer bzw. "@" = die Domain selbst
val Record.prefixLabel: String
    get() = prefix?.takeIf { it.isNotBlank() && it != "@" } ?: "@"

// ---------------------------------------------------------------------------------------------
// DynDNS-Ergebnis
// ---------------------------------------------------------------------------------------------

val DynDnsOutcome?.label: String
    get() = when (this) {
        DynDnsOutcome.CREATED -> "created"
        DynDnsOutcome.UPDATED -> "updated"
        DynDnsOutcome.UNCHANGED -> "unchanged"
        DynDnsOutcome.IGNORED -> "ignored (domain setting)"
        DynDnsOutcome.INVALID_IP -> "invalid IP"
        DynDnsOutcome.CNAME_CONFLICT -> "CNAME conflict"
        DynDnsOutcome.CDN_MANAGED -> "managed by the CDN"
        null -> "unknown"
    }

val DynDnsOutcome?.isFailure: Boolean
    get() = this == DynDnsOutcome.INVALID_IP || this == DynDnsOutcome.CNAME_CONFLICT || this == DynDnsOutcome.CDN_MANAGED

// ---------------------------------------------------------------------------------------------
// Einfache IP-Prüfung (nur Plausibilität; der Server prüft endgültig)
// ---------------------------------------------------------------------------------------------

private val IPV4_REGEX = Regex("""^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$""")

fun looksLikeIpv4(s: String): Boolean = IPV4_REGEX.matches(s.trim())

fun looksLikeIpv6(s: String): Boolean {
    val t = s.trim()
    return t.contains(':') && t.length in 2..45 && t.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }
}
