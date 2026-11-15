package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.sr.ipv64.model.MeUsage
import de.sr.ipv64.node64.model.CDNOnlineQuota
import de.sr.ipv64.model.UsageCounter
import java.util.Calendar
import java.util.Locale

// GB -> "114.5 MB" / "1.20 GB" / "20.0 TB"
private fun Double.gbText(): String = when {
    this >= 1024 -> String.format(Locale.getDefault(), "%.1f\u00a0TB", this / 1024)
    this >= 1 -> String.format(Locale.getDefault(), "%.2f\u00a0GB", this)
    else -> String.format(Locale.getDefault(), "%.1f\u00a0MB", this * 1024)
}

/** Hochrechnung auf den Monat aus dem bisherigen Verbrauch; null am Monatsanfang (zu ungenau) oder ohne Verbrauch. */
private fun Double.forecastGb(): Double? {
    val cal = Calendar.getInstance()
    val day = cal.get(Calendar.DAY_OF_MONTH) - 1 + cal.get(Calendar.HOUR_OF_DAY) / 24.0
    return if (this > 0 && day >= 1) this / day * cal.getActualMaximum(Calendar.DAY_OF_MONTH) else null
}

@Composable
private fun QuotaTile(
    title: String,
    value: String,
    limit: String?,
    fraction: Float?,
    modifier: Modifier,
    vararg details: String?
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxHeight()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val secondary = MaterialTheme.colorScheme.onSurfaceVariant
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(title, style = MaterialTheme.typography.bodySmall, color = secondary)
            Text(limit?.let { "Limit: $it" } ?: "No limit", style = MaterialTheme.typography.labelSmall, color = secondary)
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                )
            }
            details.filterNotNull().forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = secondary)
            }
        }
    }
}

private fun UsageCounter?.fraction(): Float? = this?.let { c -> c.limit?.takeIf { it > 0 }?.let { ((c.used ?: 0.0) / it).toFloat() } }

/**
 * Zwei Kacheln: CDN-Domains und CDN-Traffic des Monats. Mit [quota] (Node `proxy.online()`, altes + neues CDN zusammen,
 * Traffic in MB) aus dieser, sonst aus `account.me` ([usage], nur altes Cloud-CDN).
 */
@Composable
fun CdnStatsCard(usage: MeUsage?, quota: CDNOnlineQuota? = null, modifier: Modifier = Modifier) {
    val domainCount: Int
    val domainLimit: Int?
    val domainFraction: Float?
    val domainHint: String?
    val trafficUsedGb: Double
    val trafficLimit: String?
    val trafficFraction: Float?
    val bandwidth: String?
    if (quota != null) {
        val d = quota.domains
        domainCount = d?.used ?: 0
        domainLimit = d?.limit
        domainFraction = d?.limit?.takeIf { it > 0 }?.let { (d.used ?: 0).toFloat() / it }
        domainHint = d?.oldCdn?.takeIf { it > 0 }?.let { "$it in the old CDN" }
        val t = quota.trafficMb
        trafficUsedGb = (t?.used ?: 0.0) / 1024
        trafficLimit = t?.limit?.let { (it.toDouble() / 1024).gbText() }
        trafficFraction = t?.limit?.takeIf { it > 0 }?.let { ((t.used ?: 0.0) / it).toFloat() }
        bandwidth = quota.bandwidthMbit?.let { if (it > 0) "Bandwidth: $it Mbit/s" else "Bandwidth: unlimited" }
    } else {
        val d = usage?.cdnDomains
        val t = usage?.cdnTrafficGb
        if (d == null && t == null) return
        domainCount = d?.used?.toInt() ?: 0
        domainLimit = d?.limit
        domainFraction = d.fraction()
        domainHint = null
        trafficUsedGb = t?.used ?: 0.0
        trafficLimit = t?.limit?.let { it.toDouble().gbText() }
        trafficFraction = t.fraction()
        bandwidth = null
    }
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuotaTile("CDN domains", domainCount.toString(), domainLimit?.toString(), domainFraction, Modifier.weight(1f), domainHint)
        QuotaTile(
            "Monthly CDN traffic",
            trafficUsedGb.gbText(),
            trafficLimit,
            trafficFraction,
            Modifier.weight(1f),
            trafficUsedGb.forecastGb()?.let { "Projected: ${it.gbText()}" },
            bandwidth
        )
    }
}
