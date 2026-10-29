package de.rpicloud.ipv64net.main.views.network

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import android.net.InetAddresses
import android.os.SystemClock
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.CacheRegistry
import de.rpicloud.ipv64net.helper.ClearableCache
import de.rpicloud.ipv64net.helper.TimedCache
import de.sr.ipv64.model.AccessListEntry
import de.sr.ipv64.model.CdnAccessListMode
import de.sr.ipv64.model.CdnBackendScheme
import de.sr.ipv64.model.CdnCertificate
import de.sr.ipv64.model.CdnDomain
import de.sr.ipv64.model.CdnDomainDetail
import de.sr.ipv64.model.CdnFrontend
import de.sr.ipv64.model.CdnMapping
import de.sr.ipv64.model.CdnPendingTask
import de.sr.ipv64.model.CdnPortmapperState
import de.sr.ipv64.model.CdnProxyState
import de.sr.ipv64.model.CdnRecordMode
import de.sr.ipv64.model.CdnTraffic
import de.sr.ipv64.model.CdnTransport
import de.sr.ipv64.model.Record
import de.sr.ipv64.model.RecordState
import java.net.Inet6Address
import java.util.Locale

// =============================================================================================
// Caches
// =============================================================================================

// Liste der CDN-Domains (30 s); Änderungen schreiben das zurückgelieferte Objekt zurück
val CdnCache = TimedCache<List<CdnDomain>>(30_000)

// Objekt in der gecachten Liste ersetzen bzw. anhängen; ohne gecachte Liste nichts tun
fun TimedCache<List<CdnDomain>>.upsertCdn(item: CdnDomain) {
    val current = peek() ?: return
    put(if (current.any { it.id == item.id }) current.map { if (it.id == item.id) item else it } else current + item)
}

fun TimedCache<List<CdnDomain>>.removeCdn(token: String) {
    val current = peek() ?: return
    put(current.filterNot { it.id == token })
}

/**
 * Vollständige CDN-Details (inkl. Mappings) pro Token. Dient als gemeinsame Quelle für Detail- und Einstellungs-Screen:
 * Der Zustand ist beobachtbar, der Detail-Screen aktualisiert sich daher nach dem Speichern der Einstellungen selbst.
 * Wird beim Wechsel des API-Keys geleert (CacheRegistry).
 */
object CdnDetailStore : ClearableCache {
    private val items = mutableStateMapOf<String, CdnDomainDetail>()
    private val times = HashMap<String, Long>()

    init {
        CacheRegistry.register(this)
    }

    fun get(token: String): CdnDomainDetail? = items[token]

    fun put(detail: CdnDomainDetail) {
        val token = detail.id ?: return
        items[token] = detail
        synchronized(times) { times[token] = SystemClock.elapsedRealtime() }
    }

    fun remove(token: String) {
        items.remove(token)
        synchronized(times) { times.remove(token) }
    }

    // true, wenn der Eintrag vor weniger als [maxAgeMs] gespeichert wurde (dann kein erneutes Laden nötig)
    fun isFresh(token: String, maxAgeMs: Long): Boolean {
        if (items[token] == null) return false
        val time = synchronized(times) { times[token] } ?: return false
        return SystemClock.elapsedRealtime() - time <= maxAgeMs
    }

    override fun clear() {
        items.clear()
        synchronized(times) { times.clear() }
    }
}

// =============================================================================================
// Umwandlung Liste <-> Details
// =============================================================================================

fun CdnDomainDetail.toCdnDomain(): CdnDomain = CdnDomain(
    id = id, domain = domain, domainId = domainId, active = active, pending = pending,
    dnsRecordsEnabled = dnsRecordsEnabled, server = server, proxy = proxy, portmapper = portmapper,
    trafficMb = trafficMb, createdAt = createdAt
)

// Antworten der 202-Aktionen enthalten keine Mappings: die bekannten Mappings bleiben erhalten
fun CdnDomain.toDetail(mappings: List<CdnMapping>): CdnDomainDetail = CdnDomainDetail(
    id = id, domain = domain, domainId = domainId, active = active, pending = pending,
    dnsRecordsEnabled = dnsRecordsEnabled, server = server, proxy = proxy, portmapper = portmapper,
    trafficMb = trafficMb, createdAt = createdAt, mappings = mappings
)

// Es gibt noch Arbeit des CDN-Bots (Aufgaben offen oder etwas wird gerade abgeschaltet) -> Polling nötig
fun CdnDomain.isBusy(): Boolean =
    pending.isNotEmpty() || proxy?.state == CdnProxyState.DISABLING || portmapper?.state == CdnPortmapperState.DISABLING

fun CdnDomainDetail.isBusy(): Boolean = toCdnDomain().isBusy()

fun CdnDomain.displayName(): String = domain?.takeIf { it.isNotBlank() } ?: "Domain …" + id.orEmpty().takeLast(4)

// =============================================================================================
// Beschriftungen und Farben
// =============================================================================================

fun CdnPendingTask.label(): String = when (this) {
    CdnPendingTask.SERVER_ASSIGNMENT -> "Assigning CDN server"
    CdnPendingTask.DNS -> "Updating DNS"
    CdnPendingTask.CONFIG_DEPLOY -> "Deploying configuration"
    CdnPendingTask.CERTIFICATE -> "Issuing certificate"
    CdnPendingTask.SERVER_MOVE -> "Moving to another server"
    CdnPendingTask.DELETION -> "Removing from CDN"
}

fun CdnProxyState?.proxyLabel(): String = when (this) {
    CdnProxyState.ON -> "Proxy on"
    CdnProxyState.OFF -> "Proxy off"
    CdnProxyState.DISABLING -> "Proxy switching off"
    null -> "Proxy unknown"
}

@Composable
fun CdnProxyState?.proxyColor(): Color = colorResource(
    when (this) {
        CdnProxyState.ON -> R.color.ipv64_green
        CdnProxyState.DISABLING -> R.color.ipv64_orange
        else -> R.color.ipv64_gray
    }
)

fun CdnPortmapperState?.portmapperLabel(): String = when (this) {
    CdnPortmapperState.ON -> "On"
    CdnPortmapperState.OFF -> "Off"
    CdnPortmapperState.DISABLING -> "Switching off"
    CdnPortmapperState.SUSPENDED_TRAFFIC_LIMIT -> "Suspended (traffic limit reached)"
    null -> "Unknown"
}

@Composable
fun CdnPortmapperState?.portmapperColor(): Color = colorResource(
    when (this) {
        CdnPortmapperState.ON -> R.color.ipv64_green
        CdnPortmapperState.DISABLING -> R.color.ipv64_orange
        CdnPortmapperState.SUSPENDED_TRAFFIC_LIMIT -> R.color.ipv64_red
        else -> R.color.ipv64_gray
    }
)

fun CdnFrontend?.frontendLabel(): String = when (this) {
    CdnFrontend.HTTP -> "HTTP only"
    CdnFrontend.HTTPS_REDIRECT -> "Redirect HTTP to HTTPS"
    CdnFrontend.HTTP_AND_HTTPS -> "HTTP and HTTPS"
    null -> "Unknown"
}

fun CdnBackendScheme?.schemeLabel(): String = when (this) {
    CdnBackendScheme.HTTP -> "HTTP"
    CdnBackendScheme.HTTPS -> "HTTPS"
    null -> "Unknown"
}

fun CdnCertificate?.certificateLabel(): String = when (this) {
    CdnCertificate.DEFAULT -> "Default certificate"
    CdnCertificate.LETSENCRYPT -> "Let's Encrypt"
    CdnCertificate.ISSUING -> "Being issued"
    null -> "Unknown"
}

fun CdnAccessListMode?.accessModeLabel(): String = when (this) {
    CdnAccessListMode.ALLOWLIST -> "Allowlist (only listed addresses may connect)"
    CdnAccessListMode.DENYLIST -> "Denylist (listed addresses are blocked)"
    null -> "Unknown"
}

fun CdnTransport?.transportLabel(): String = when (this) {
    CdnTransport.TCP -> "TCP"
    CdnTransport.UDP -> "UDP"
    null -> "TCP"
}

fun CdnRecordMode.modeTitle(): String = when (this) {
    CdnRecordMode.FULL -> "Full"
    CdnRecordMode.OTHER_FAMILY_ONLY -> "Other address family only"
}

fun CdnRecordMode.modeDescription(): String = when (this) {
    CdnRecordMode.FULL ->
        "The name points completely to the CDN. All visitors reach your server through the CDN."
    CdnRecordMode.OTHER_FAMILY_ONLY ->
        "The record stays directly reachable and the CDN serves only the other address family, " +
                "for example IPv4 for an IPv6-only connection. Only possible if this is the only record on its name."
}

// Zustand eines Records im CDN-Kontext
fun Record.cdnStateLabel(): String = when (state) {
    RecordState.ACTIVE -> "Direct"
    RecordState.INACTIVE -> "Inactive"
    RecordState.CDN_PROXIED -> "Via CDN"
    RecordState.CDN_PARTIAL -> "Partly via CDN"
    RecordState.CDN_RECORD -> "Managed by CDN"
    RecordState.FAILOVER_RECORD -> "Failover record"
    RecordState.FAILOVER_REPLACED -> "Replaced by failover"
    null -> "Unknown"
}

@Composable
fun Record.cdnStateColor(): Color = colorResource(
    when (state) {
        RecordState.CDN_PROXIED, RecordState.CDN_PARTIAL -> R.color.ipv64_blue
        RecordState.ACTIVE -> R.color.ipv64_green
        RecordState.FAILOVER_RECORD, RecordState.FAILOVER_REPLACED -> R.color.ipv64_orange
        else -> R.color.ipv64_gray
    }
)

// CDN lässt sich einschalten, wenn der Record direkt ausgeliefert wird
fun Record.canEnableCdn(): Boolean = state == RecordState.ACTIVE

// CDN lässt sich ausschalten, wenn der Record (teilweise) über das CDN läuft
fun Record.canDisableCdn(): Boolean = state == RecordState.CDN_PROXIED || state == RecordState.CDN_PARTIAL

// Ziel eines Port-Mappings muss ein Record sein, der über das CDN läuft
fun Record.canBeMappingTarget(): Boolean = canDisableCdn()

// Referenz für die API: die numerische ID (das Record-Token ist ein Geheimnis und wird nicht verwendet)
fun Record.cdnRef(): String? = id?.toString()

// Zeilenbeschriftung eines Records
fun Record.cdnTitle(): String {
    val name = fqdn?.takeIf { it.isNotBlank() }
        ?: listOf(prefix.orEmpty(), domain.orEmpty()).filter { it.isNotBlank() }.joinToString(".")
    return "$name (${type?.value ?: "?"})"
}

// =============================================================================================
// Anzeige: Traffic
// =============================================================================================

fun Double.formatMegabytes(): String =
    if (this >= 1024) String.format(Locale.getDefault(), "%.2f GB", this / 1024)
    else String.format(Locale.getDefault(), "%.1f MB", this)

fun CdnTraffic?.trafficText(): String {
    if (this == null) return "–"
    return "in ${(incoming ?: 0.0).formatMegabytes()} · out ${(outgoing ?: 0.0).formatMegabytes()}"
}

// =============================================================================================
// Eingaben prüfen
// =============================================================================================

// Port 1..65535 oder null
fun parsePort(text: String, range: IntRange = 1..65535): Int? =
    text.trim().toIntOrNull()?.takeIf { it in range }

private val IPV4_REGEX = Regex("^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$")

private fun isIpv4Address(s: String): Boolean = IPV4_REGEX.matches(s)

// isNumericAddress löst nie per DNS auf; erst danach wird geparst (kein getByName auf Benutzereingaben)
private fun isIpv6Address(s: String): Boolean {
    if (!s.contains(':') || s.length > 45) return false
    return InetAddresses.isNumericAddress(s) && InetAddresses.parseNumericAddress(s) is Inet6Address
}

// Fehlertext für die Zugriffsliste (IP oder CIDR); null = gültig
fun addressError(input: String): String? {
    val value = input.trim()
    if (value.isEmpty()) return "Enter an IP address or network."
    val parts = value.split('/')
    if (parts.size > 2) return "Use the format 203.0.113.0/24 or 2001:db8::/32."
    val ip = parts[0]
    val v4 = isIpv4Address(ip)
    val v6 = !v4 && isIpv6Address(ip)
    if (!v4 && !v6) return "Enter a valid IPv4 or IPv6 address."
    if (parts.size == 2) {
        val prefix = parts[1].toIntOrNull() ?: return "The prefix length must be a number."
        val max = if (v4) 32 else 128
        if (prefix !in 0..max) return "The prefix length must be between 0 and $max."
    }
    return null
}

// Anzeige des Access-List-Eintrags
fun AccessListEntry.label(): String = address.orEmpty()
