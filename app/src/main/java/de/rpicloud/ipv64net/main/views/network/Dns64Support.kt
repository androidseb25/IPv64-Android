package de.rpicloud.ipv64net.main.views.network

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.TimedCache
import de.rpicloud.ipv64net.models.isIpv4
import de.rpicloud.ipv64net.models.isIpv6
import de.sr.ipv64.model.Dns64Blocklist
import de.sr.ipv64.model.Dns64Client
import de.sr.ipv64.model.Dns64RuleAction
import de.sr.ipv64.model.Dns64Server
import de.sr.ipv64.model.Dns64Source
import de.sr.ipv64.model.Dns64SourceKind
import de.sr.ipv64.model.Dns64State

// =============================================================================================
// Cache der Client-Liste (30 s); Änderungen schreiben das zurückgelieferte Objekt zurück
// =============================================================================================

val Dns64Cache = TimedCache<List<Dns64Client>>(30_000)

// Client in der gecachten Liste ersetzen bzw. anhängen; ohne gecachte Liste nichts tun
fun dns64CacheUpsert(client: Dns64Client) {
    val current = Dns64Cache.peek() ?: return
    Dns64Cache.put(
        if (current.any { it.id == client.id }) current.map { if (it.id == client.id) client else it }
        else current + client
    )
}

fun dns64CacheRemove(id: String) {
    val current = Dns64Cache.peek() ?: return
    Dns64Cache.put(current.filterNot { it.id == id })
}

// Maximale Anzahl Quell-Adressen je Art
const val DNS64_MAX_SOURCES_PER_KIND = 5

// =============================================================================================
// Zustand und Beschriftungen
// =============================================================================================

fun Dns64Client.displayName(): String =
    name?.takeIf { it.isNotBlank() } ?: clientId?.takeIf { it.isNotBlank() } ?: "DNS64 client"

// Der Client oder eine seiner Regeln wird noch vom Hintergrunddienst umgesetzt
fun Dns64Client.isApplying(): Boolean =
    state == Dns64State.APPLYING || rules.any { it.state == Dns64State.APPLYING }

fun Dns64State?.dns64Label(): String = when (this) {
    Dns64State.READY -> "Ready"
    Dns64State.APPLYING -> "Applying"
    null -> "Unknown"
}

@Composable
fun Dns64State?.dns64Color(): Color = colorResource(
    when (this) {
        Dns64State.READY -> R.color.ipv64_green
        Dns64State.APPLYING -> R.color.ipv64_orange
        null -> R.color.ipv64_gray
    }
)

@Composable
fun Dns64RuleAction?.dns64ActionColor(): Color = colorResource(
    when (this) {
        Dns64RuleAction.ALLOW -> R.color.ipv64_green
        Dns64RuleAction.BLOCK -> R.color.ipv64_red
        null -> R.color.ipv64_gray
    }
)

fun Dns64RuleAction?.dns64ActionLabel(): String = when (this) {
    Dns64RuleAction.ALLOW -> "Allow"
    Dns64RuleAction.BLOCK -> "Block"
    null -> "Unknown"
}

fun Dns64Blocklist.dns64Title(): String = when (this) {
    Dns64Blocklist.ADS -> "Ads and trackers"
    Dns64Blocklist.ADULT -> "Adult content"
    Dns64Blocklist.THREATS -> "Threats"
}

fun Dns64Blocklist.dns64Description(): String = when (this) {
    Dns64Blocklist.ADS -> "Blocks advertising and tracking domains."
    Dns64Blocklist.ADULT -> "Blocks adult websites."
    Dns64Blocklist.THREATS -> "Blocks malware, phishing and other dangerous domains."
}

// adult und threats gibt es nicht im Free-Paket; der Server antwortet dort mit 403
fun Dns64Blocklist.isPaidOnly(): Boolean = this == Dns64Blocklist.ADULT || this == Dns64Blocklist.THREATS

// Name eines Servers für die Anzeige (Standort, sonst Hostname)
fun Dns64Server.displayName(index: Int): String =
    location?.takeIf { it.isNotBlank() } ?: hostname?.takeIf { it.isNotBlank() } ?: "Server ${index + 1}"

// Quellen einer Art (ohne Art: Einträge mit fqdn zählen als Record)
fun List<Dns64Source>.ofKind(kind: Dns64SourceKind): List<Dns64Source> =
    filter { (it.kind ?: if (it.fqdn != null) Dns64SourceKind.RECORD else Dns64SourceKind.IP) == kind }

// Anzeigetext einer Quelle: Record = FQDN (mit aktueller Adresse), IP = Adresse
fun Dns64Source.displayText(): String = when {
    !fqdn.isNullOrBlank() -> fqdn!!
    !address.isNullOrBlank() -> address!!
    else -> "Unknown source"
}

// Antworten auf Änderungen (PATCH, POST) enthalten Regeln/Quellen nicht (nur die Detailansicht): sie bleiben dann
// erhalten, ebenso nicht gelieferte Details (Stamps, Name, Client-ID). [detail] = vollständige Detailantwort (GET).
fun mergeDns64(old: Dns64Client?, new: Dns64Client, detail: Boolean = false): Dns64Client {
    if (old == null || detail) return new
    val servers = if (new.servers.isEmpty()) old.servers else new.servers.map { server ->
        if (server.stamps != null) server
        else server.copy(stamps = old.servers.firstOrNull { it.hostname == server.hostname }?.stamps)
    }
    return new.copy(
        name = new.name ?: old.name,
        clientId = new.clientId ?: old.clientId,
        servers = servers,
        createdAt = new.createdAt ?: old.createdAt,
        rules = if (new.rules.isEmpty()) old.rules else new.rules,
        sources = if (new.sources.isEmpty()) old.sources else new.sources
    )
}

// =============================================================================================
// Eingaben prüfen
// =============================================================================================

// Domain für eine Regel bereinigen (Schema, Pfad, Punkt am Ende entfernen); null = ungültig
fun normalizeDns64Domain(input: String): String? {
    var d = input.trim().lowercase()
    d = d.removePrefix("https://").removePrefix("http://")
    d = d.substringBefore('/').substringBefore('?').trimEnd('.')
    if (d.isEmpty() || !d.contains('.')) return null
    if (d.startsWith('.') || d.contains("..")) return null
    if (!d.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' || it == '*' }) return null
    return d
}

// Nur Adress-Literale zulassen (keine Namen, damit nie eine DNS-Auflösung stattfindet)
fun isIpLiteral(input: String): Boolean = input.trim().let { isIpv4(it) || isIpv6(it) }

// =============================================================================================
// Android Private DNS
// =============================================================================================

// Öffnet die Private-DNS-Einstellungen bzw. als Rückfall die Netzwerk-Einstellungen; false = nichts ließ sich öffnen
fun openPrivateDnsSettings(ctx: Context): Boolean {
    val intents = listOf(
        Intent("android.settings.PRIVATE_DNS_SETTINGS"),
        Intent(Settings.ACTION_WIRELESS_SETTINGS)
    )
    for (intent in intents) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (e: ActivityNotFoundException) {
            // nächste Möglichkeit versuchen
        } catch (e: SecurityException) {
            // nächste Möglichkeit versuchen
        }
    }
    return false
}
