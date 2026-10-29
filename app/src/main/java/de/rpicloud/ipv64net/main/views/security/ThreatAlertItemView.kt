package de.rpicloud.ipv64net.main.views.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.sr.ipv64.model.ThreatWatchAlert

/** Karte eines Frühwarn-Alarms; Antippen öffnet die IP-Auskunft. */
@Composable
fun ThreatAlertItemView(alert: ThreatWatchAlert, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text(
                    alert.ip ?: "–",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (alert.listed == true) {
                    StatusChip("Listed", colorResource(R.color.ipv64_red))
                } else {
                    StatusChip("Seen", colorResource(R.color.ipv64_orange))
                }
            }

            val sourceText = when (alert.source) {
                "dyndns" -> "DynDNS"
                "network" -> "Network"
                else -> alert.source
            }
            val target = alert.label?.takeIf { it.isNotBlank() } ?: alert.cidr
            Text(
                listOfNotNull(sourceText, target).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium
            )

            if (alert.kinds.isNotEmpty()) {
                Text(alert.kinds.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = muted)
            }

            val facts = listOfNotNull(
                alert.score?.let { "Score $it" },
                alert.details?.accounts?.let { "$it accounts" },
                alert.details?.events?.let { "${it.formatCount()} events" },
                alert.details?.reason?.takeIf { it.isNotBlank() }?.let { "Reason: ${prettyKey(it)}" }
            )
            if (facts.isNotEmpty()) {
                Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = muted)
            }

            val seen = alert.details?.seen?.firstOrNull()
            if (seen != null) {
                val what = listOfNotNull(seen.service, seen.port?.toString(), seen.kind).joinToString(" · ")
                if (what.isNotEmpty()) {
                    Text("Top: $what", style = MaterialTheme.typography.bodySmall, color = muted)
                }
            }

            val time = alert.createdAt?.formatRelative()
                ?: alert.lastSeen
            if (time != null) {
                Text(time, style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
    }
}
