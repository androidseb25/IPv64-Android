package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.sr.ipv64.model.MeUsage
import de.sr.ipv64.node64.model.CDNOnlineQuota
import de.sr.ipv64.model.UsageCounter
import java.util.Calendar
import java.util.Locale

// GB -> "114.5 MB" / "1.20 GB" / "20.0 TB"
private fun Double.gbText(): String = when {
    this >= 1024 -> String.format(Locale.getDefault(), "%.1f TB", this / 1024)
    this >= 1 -> String.format(Locale.getDefault(), "%.2f GB", this)
    else -> String.format(Locale.getDefault(), "%.1f MB", this * 1024)
}

/** Hochrechnung auf den Monat aus dem bisherigen Verbrauch; null am Monatsanfang (zu ungenau) oder ohne Verbrauch. */
private fun Double.forecastGb(): Double? {
    val cal = Calendar.getInstance()
    val day = cal.get(Calendar.DAY_OF_MONTH) - 1 + cal.get(Calendar.HOUR_OF_DAY) / 24.0
    return if (this > 0 && day >= 1) this / day * cal.getActualMaximum(Calendar.DAY_OF_MONTH) else null
}

@Composable
private fun StatTile(title: String, value: String, fraction: Float?, hint: String?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
        if (fraction != null) LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private fun UsageCounter?.fraction(): Float? = this?.let { c -> c.limit?.takeIf { it > 0 }?.let { ((c.used ?: 0.0) / it).toFloat() } }

/**
 * Zwei Kacheln: CDN-Domains und CDN-Traffic des Monats. Mit [quota] (Node `proxy.online()`, altes + neues CDN zusammen,
 * Traffic in MB) aus dieser, sonst aus `account.me` ([usage], nur altes Cloud-CDN).
 */
@Composable
fun CdnStatsCard(usage: MeUsage?, quota: CDNOnlineQuota? = null, modifier: Modifier = Modifier) {
    val domains: String?
    val domainFraction: Float?
    val domainHint: String?
    val trafficUsedGb: Double?
    val trafficLimit: String
    val trafficFraction: Float?
    val bandwidth: String?
    if (quota != null) {
        val d = quota.domains
        domains = "${d?.used ?: 0} / ${d?.limit?.takeIf { it > 0 } ?: "∞"}"
        domainFraction = d?.limit?.takeIf { it > 0 }?.let { (d.used ?: 0).toFloat() / it }
        domainHint = d?.oldCdn?.takeIf { it > 0 }?.let { "$it in the old CDN" }
        val t = quota.trafficMb
        trafficUsedGb = (t?.used ?: 0.0) / 1024
        trafficLimit = t?.limit?.takeIf { it > 0 }?.let { (it.toDouble() / 1024).gbText() } ?: "∞"
        trafficFraction = t?.limit?.takeIf { it > 0 }?.let { ((t.used ?: 0.0) / it).toFloat() }
        bandwidth = quota.bandwidthMbit?.let { if (it > 0) "Bandwidth: $it Mbit/s" else "Bandwidth: unlimited" }
    } else {
        val d = usage?.cdnDomains
        val t = usage?.cdnTrafficGb
        if (d == null && t == null) return
        domains = "${d?.used?.toInt() ?: 0} / ${d?.limit?.takeIf { it > 0 } ?: "∞"}"
        domainFraction = d.fraction()
        domainHint = null
        trafficUsedGb = t?.used ?: 0.0
        trafficLimit = t?.limit?.takeIf { it > 0 }?.let { it.toDouble().gbText() } ?: "∞"
        trafficFraction = t.fraction()
        bandwidth = null
    }
    ListGroup(modifier = modifier) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatTile("CDN domains", domains, domainFraction, domainHint, Modifier.weight(1f))
            StatTile(
                "CDN traffic this month",
                "${trafficUsedGb.gbText()} / $trafficLimit",
                trafficFraction,
                listOfNotNull(trafficUsedGb.forecastGb()?.let { "At this rate: ${it.gbText()} this month" }, bandwidth).joinToString("\n").ifEmpty { null },
                Modifier.weight(1f)
            )
        }
    }
}
