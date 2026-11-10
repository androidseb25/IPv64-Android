package de.rpicloud.ipv64net.main.views.monitoring

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.TimedCache
import de.rpicloud.ipv64net.helper.toError
import de.rpicloud.ipv64net.main.views.inlineText
import de.rpicloud.ipv64net.models.isIpv4
import de.rpicloud.ipv64net.models.isIpv6
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.FailoverMode
import de.sr.ipv64.model.FailoverPolicy
import de.sr.ipv64.model.FailoverRuleState
import de.sr.ipv64.model.FailoverRuleTarget
import de.sr.ipv64.model.FailoverStatus
import de.sr.ipv64.model.FailoverTargetKind
import de.sr.ipv64.model.OfflinePage
import de.sr.ipv64.model.Record
import de.sr.ipv64.model.RecordState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

// =============================================================================================
// Caches
// =============================================================================================

// Liste der Failover-Policies (30 s); Änderungen schreiben das zurückgelieferte Objekt per upsert zurück
val FailoverCache = TimedCache<List<FailoverPolicy>>(30_000)

// Liste der Offline-Seiten (60 s)
val OfflinePageCache = TimedCache<List<OfflinePage>>(60_000)

// Objekt in einer gecachten Liste ersetzen bzw. anhängen; ohne gecachte Liste nichts tun
fun <T : Any> TimedCache<List<T>>.upsert(item: T, same: (T) -> Boolean) {
    val current = peek() ?: return
    put(if (current.any(same)) current.map { if (same(it)) item else it } else current + item)
}

// Objekte aus einer gecachten Liste entfernen
fun <T : Any> TimedCache<List<T>>.removeWhere(predicate: (T) -> Boolean) {
    val current = peek() ?: return
    put(current.filterNot(predicate))
}

// =============================================================================================
// Failover: Status, Modi, Beschriftungen
// =============================================================================================

// ID 0 in der Integrationsliste = E-Mail-Adresse des Accounts
const val ACCOUNT_EMAIL_INTEGRATION = 0L

// Umschaltung läuft (failover_requested / fallback_requested)
fun FailoverPolicy.isTransitioning(): Boolean = status?.value?.endsWith("_requested") == true

fun FailoverPolicy.hasAccountEmail(): Boolean = ACCOUNT_EMAIL_INTEGRATION in integrations

// Andere (klassische) Integrationen, die die App nicht verwaltet und unverändert lässt
fun FailoverPolicy.otherIntegrations(): List<Long> = integrations.filter { it != ACCOUNT_EMAIL_INTEGRATION }

fun FailoverStatus?.failoverLabel(): String = when (this) {
    null -> "Unknown"
    FailoverStatus.PENDING -> "Setting up"
    FailoverStatus.ACTIVE -> "Monitoring"
    FailoverStatus.FAILED_OVER -> "Failed over"
    FailoverStatus.PAUSED -> "Paused"
    FailoverStatus.FAILOVER_REQUESTED -> "Switching to replacement"
    FailoverStatus.FALLBACK_REQUESTED -> "Switching back"
}

@Composable
fun FailoverStatus?.failoverColor(): Color = colorResource(
    when (this) {
        FailoverStatus.ACTIVE -> R.color.ipv64_green
        FailoverStatus.FAILED_OVER -> R.color.ipv64_red
        FailoverStatus.PENDING, FailoverStatus.FAILOVER_REQUESTED, FailoverStatus.FALLBACK_REQUESTED -> R.color.ipv64_orange
        else -> R.color.ipv64_gray
    }
)

fun FailoverMode?.failoverTitle(): String = when (this) {
    FailoverMode.AUTO_FAILOVER -> "Automatic failover"
    FailoverMode.AUTO_FAILOVER_AND_FALLBACK -> "Automatic failover and fallback"
    FailoverMode.MANUAL -> "Manual only"
    null -> "Unknown"
}

fun FailoverMode.failoverDescription(): String = when (this) {
    FailoverMode.AUTO_FAILOVER -> "Switches to the replacement targets automatically when the monitors report DOWN. You switch back manually."
    FailoverMode.AUTO_FAILOVER_AND_FALLBACK -> "Switches to the replacement targets and back to the original targets automatically."
    FailoverMode.MANUAL -> "Never switches by itself. You trigger every switch manually."
}

fun de.sr.ipv64.model.FailoverLinking?.failoverTitle(): String = when (this) {
    de.sr.ipv64.model.FailoverLinking.AND -> "All monitors must be DOWN"
    de.sr.ipv64.model.FailoverLinking.OR -> "One monitor DOWN is enough"
    null -> "Unknown"
}

// Farbe für den Status eines Monitors (healthcheck_status: up / down / ...)
@Composable
fun failoverMonitorColor(status: String?): Color = colorResource(
    when (status?.lowercase()) {
        "up", "ok" -> R.color.ipv64_green
        "down" -> R.color.ipv64_red
        "degraded", "warning" -> R.color.ipv64_orange
        else -> R.color.ipv64_gray
    }
)

@Composable
fun FailoverRuleState?.ruleColor(): Color = colorResource(
    if (this == FailoverRuleState.FAILED_OVER) R.color.ipv64_red else R.color.ipv64_green
)

fun FailoverRuleState?.ruleLabel(): String = when (this) {
    FailoverRuleState.STANDBY -> "Standby"
    FailoverRuleState.FAILED_OVER -> "Replaced"
    null -> "Unknown"
}

// Kurzbeschreibung des Ersatzziels; Namen nur aus bereits geladenen Offline-Seiten (keine Einzelabfragen)
fun FailoverRuleTarget?.describe(pages: List<OfflinePage>?): String {
    if (this == null) return "No target"
    return when (kind) {
        FailoverTargetKind.IP -> "Replacement IP ${ip.orEmpty()}"
        FailoverTargetKind.RECORD -> "Record …" + record.orEmpty().takeLast(4)
        FailoverTargetKind.OFFLINE_PAGE -> {
            val id = offlinePage.orEmpty()
            when {
                id.isEmpty() || id == "default" -> "IPv64 default offline page"
                else -> "Offline page " + (pages?.firstOrNull { it.id == id }?.name?.takeIf { it.isNotBlank() }
                    ?: ("…" + id.takeLast(4)))
            }
        }

        null -> "Unknown target"
    }
}

// =============================================================================================
// Records und IP-Adressen
// =============================================================================================

// Records unter CDN-/Failover-Verwaltung dürfen nicht in Regeln verwendet werden
val BLOCKED_RULE_RECORD_STATES = setOf(
    RecordState.CDN_RECORD, RecordState.CDN_PROXIED, RecordState.CDN_PARTIAL,
    RecordState.FAILOVER_RECORD, RecordState.FAILOVER_REPLACED
)

// Referenz für die API: Token (bleibt bei IP-Änderungen gleich), sonst ID
fun Record.ref(): String? = token?.takeIf { it.isNotBlank() } ?: id?.toString()

// Fehlertext, wenn die Adresse nicht zur IP-Familie des Record-Typs passt (null = passt)
fun ipFamilyError(recordType: de.sr.ipv64.model.RecordType?, ip: String): String? {
    val value = ip.trim()
    return when (recordType) {
        de.sr.ipv64.model.RecordType.A -> if (isIpv4(value)) null else "Enter an IPv4 address (record type A)."
        de.sr.ipv64.model.RecordType.AAAA -> if (isIpv6(value)) null else "Enter an IPv6 address (record type AAAA)."
        else -> "Only A and AAAA records can be protected."
    }
}

// =============================================================================================
// Offline-Seiten: Auswahl, Text, Logo
// =============================================================================================

const val DEFAULT_OFFLINE_PAGE = "default"

// Dialog zur Auswahl einer Offline-Seite (oder der Standardseite); eine Anfrage (Cache), mit Retry
@Composable
fun OfflinePagePickerDialog(
    onPick: (id: String, label: String) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    var pages by remember { mutableStateOf(OfflinePageCache.peek(ctx)) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        if (pages != null && attempt == 0) return@LaunchedEffect
        error = null
        try {
            pages = OfflinePageCache.get(ctx, force = attempt > 0) { it.offlinePages.list() }
        } catch (e: IPv64Exception) {
            error = e.toError().inlineText()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select offline page") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                PickerRow("IPv64 default page", "Neutral page without your own text") {
                    onPick(DEFAULT_OFFLINE_PAGE, "IPv64 default page")
                }
                val list = pages
                when {
                    list != null -> list.forEach { page ->
                        val id = page.id ?: return@forEach
                        HorizontalDivider()
                        val title = page.name?.takeIf { it.isNotBlank() } ?: "Offline page …" + id.takeLast(4)
                        PickerRow(title, page.domain) { onPick(id, title) }
                    }

                    error != null -> {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                        TextButton(onClick = { attempt++ }) { Text("Retry") }
                    }

                    else -> CircularProgressIndicator(modifier = Modifier
                        .padding(top = 12.dp)
                        .size(24.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PickerRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Server liefert den Text als HTML; für das Formular zurück in Klartext (Zeilenumbrüche bleiben erhalten)
fun htmlToPlainText(html: String?): String {
    if (html.isNullOrEmpty()) return ""
    return html
        .replace(Regex("<br\\s*/?>\\r?\\n?", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p\\s*>\\r?\\n?", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]*>"), "")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&amp;", "&")
        .trim()
}

const val LOGO_MAX_BYTES = 1_048_576
const val LOGO_MAX_PIXELS = 1024

// Ausgewähltes, geprüftes Logo (Bytes bleiben nur im Speicher)
class LogoImage(val bytes: ByteArray, val fileName: String, val mime: String)

class LogoReadResult(val logo: LogoImage? = null, val error: String? = null)

// Dateityp anhand der Magic Bytes; nur PNG, JPEG und WebP erlaubt
private fun sniffImage(bytes: ByteArray): Pair<String, String>? {
    fun at(i: Int) = bytes.getOrNull(i)?.toInt()?.and(0xFF) ?: -1
    return when {
        at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 -> "image/png" to "png"
        at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> "image/jpeg" to "jpg"
        bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp" to "webp"

        else -> null
    }
}

private fun readLimited(input: InputStream, limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (out.size() <= limit) {
        val read = input.read(buffer)
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

// Liest ein per Photo Picker gewähltes Bild über den ContentResolver und prüft Größe, Format und Abmessungen
suspend fun readLogo(ctx: Context, uri: Uri): LogoReadResult = withContext(Dispatchers.IO) {
    try {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { readLimited(it, LOGO_MAX_BYTES) }
            ?: return@withContext LogoReadResult(error = "The image could not be read.")
        if (bytes.size > LOGO_MAX_BYTES) {
            return@withContext LogoReadResult(error = "The logo is larger than 1 MB.")
        }
        val (mime, ext) = sniffImage(bytes)
            ?: return@withContext LogoReadResult(error = "Only PNG, JPEG and WebP images are allowed.")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return@withContext LogoReadResult(error = "This file is not a valid image.")
        }
        if (bounds.outWidth > LOGO_MAX_PIXELS || bounds.outHeight > LOGO_MAX_PIXELS) {
            return@withContext LogoReadResult(error = "The logo may be at most $LOGO_MAX_PIXELS x $LOGO_MAX_PIXELS pixels.")
        }
        LogoReadResult(logo = LogoImage(bytes, "logo.$ext", mime))
    } catch (e: IOException) {
        LogoReadResult(error = "The image could not be read.")
    } catch (e: SecurityException) {
        LogoReadResult(error = "No permission to read the image.")
    }
}
