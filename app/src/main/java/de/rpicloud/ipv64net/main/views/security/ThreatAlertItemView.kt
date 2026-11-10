package de.rpicloud.ipv64net.main.views.security

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.sr.ipv64.model.ThreatWatchAlert

/** Zeile eines Frühwarn-Alarms (in einer [de.rpicloud.ipv64net.main.views.common.ListGroup]); Antippen öffnet die IP-Auskunft. */
@Composable
fun ThreatAlertItemView(alert: ThreatWatchAlert, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sourceText = when (alert.source) {
        "dyndns" -> "DynDNS"
        "network" -> "Network"
        else -> alert.source
    }
    val target = alert.label?.takeIf { it.isNotBlank() } ?: alert.cidr

    val facts = listOfNotNull(
        alert.score?.let { "Score $it" },
        alert.details?.accounts?.let { "$it accounts" },
        alert.details?.events?.let { "${it.formatCount()} events" },
        alert.details?.reason?.takeIf { it.isNotBlank() }?.let { "Reason: ${prettyKey(it)}" }
    )
    val seen = alert.details?.seen?.firstOrNull()
    val top = seen?.let { listOfNotNull(it.service, it.port?.toString(), it.kind).joinToString(" · ") }
    val time = alert.createdAt?.formatRelative() ?: alert.lastSeen

    val subtitle = listOfNotNull(
        listOfNotNull(sourceText, target).joinToString(" · "),
        alert.kinds.takeIf { it.isNotEmpty() }?.joinToString(", "),
        facts.takeIf { it.isNotEmpty() }?.joinToString(" · "),
        top?.takeIf { it.isNotEmpty() }?.let { "Top: $it" },
        time
    ).joinToString("\n")

    ListRow(
        title = alert.ip ?: "–",
        monospaceTitle = true,
        subtitle = subtitle,
        subtitleMaxLines = 5,
        trailing = {
            if (alert.listed == true) StatusChip("Listed", colorResource(R.color.ipv64_red))
            else StatusChip("Seen", colorResource(R.color.ipv64_orange))
        },
        onClick = onClick,
        modifier = modifier
    )
}
