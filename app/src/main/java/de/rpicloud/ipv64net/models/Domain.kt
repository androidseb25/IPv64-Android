package de.rpicloud.ipv64net.models

import androidx.compose.ui.graphics.Color
import de.rpicloud.ipv64net.helper.v64domains
import de.sr.ipv64.model.Domain
import de.sr.ipv64.model.DomainDetail
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
