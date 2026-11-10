package de.rpicloud.ipv64net.main.views

import de.rpicloud.ipv64net.models.hasIp
import de.rpicloud.ipv64net.main.views.common.ListGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.models.IsWildcard
import de.rpicloud.ipv64net.models.NO_IPV4
import de.rpicloud.ipv64net.models.NO_IPV6
import de.rpicloud.ipv64net.models.effectiveKind
import de.rpicloud.ipv64net.models.isLocked
import de.rpicloud.ipv64net.models.label
import de.rpicloud.ipv64net.models.lockReason
import de.rpicloud.ipv64net.models.mismatchV4
import de.rpicloud.ipv64net.models.mismatchV6
import de.rpicloud.ipv64net.models.prefixLabel
import de.rpicloud.ipv64net.ui.theme.AppTheme
import de.sr.ipv64.model.Domain
import de.sr.ipv64.model.DomainDetail
import de.sr.ipv64.model.DomainKind
import de.sr.ipv64.model.Record
import de.sr.ipv64.model.RecordState

/** Status der Domain-IPs gegenüber den eigenen IPs als Text + Farbe (Status nie nur über Farbe). */
@Composable
fun domainIpStatus(detail: DomainDetail?, myV4: String, myV6: String): Pair<String, Color> = when {
    detail == null -> "Checking" to colorResource(R.color.ipv64_gray)
    !detail.hasIp() -> "No IP" to colorResource(R.color.ipv64_gray)
    myV4 == NO_IPV4 && myV6 == NO_IPV6 -> "IP unknown" to colorResource(R.color.ipv64_gray)
    detail.mismatchV4(myV4) || detail.mismatchV6(myV6) -> "IP differs" to colorResource(R.color.ipv64_red)
    else -> "IP matches" to colorResource(R.color.ipv64_green)
}

/** Zeile der Domainliste (Gruppe/Rundung über groupedRows). */
@Composable
fun DomainItemView(
    domain: Domain,
    detail: DomainDetail?,
    myV4: String,
    myV6: String,
    onClick: (selectedDomain: Domain) -> Unit
) {
    val (statusText, statusColor) = domainIpStatus(detail, myV4, myV6)
    ListRow(
        title = domain.name.orEmpty(),
        subtitle = listOfNotNull(
            "Updates: ${domain.updateCount ?: 0}",
            "Wildcard: ${domain.IsWildcard}",
            if (domain.effectiveKind == DomainKind.OWN) "Own domain" else null
        ).joinToString(" · "),
        trailing = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusChip(statusText, statusColor)
                if (domain.active == false) StatusChip("Inactive", colorResource(R.color.ipv64_gray))
            }
        },
        onClick = { onClick(domain) }
    )
}

/**
 * Zeile eines DNS-Records in der Domain-Detailansicht (innerhalb einer ListGroup je Record-Typ).
 * Gesperrte Records (CDN/Failover) zeigen Status und Schloss und sind weder antippbar noch löschbar.
 * Antippen öffnet die Bearbeitung ([onEdit]), der Papierkorb fragt vor dem Löschen nach ([onDelete]).
 */
@Composable
fun DomainRecordRow(
    record: Record,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val locked = record.isLocked
    val state = record.state
    ListRow(
        title = record.prefixLabel,
        subtitle = listOfNotNull(
            record.content ?: "–",
            "TTL ${record.ttl ?: "–"} · " + (record.updatedAt?.formatGermanTime() ?: "–"),
            record.lockReason
        ).joinToString("\n"),
        subtitleMaxLines = 6,
        monospaceSubtitle = true,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if ((state != null && state != RecordState.ACTIVE) || record.pendingDns == true) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (state != null && state != RecordState.ACTIVE) {
                            StatusChip(
                                state.label,
                                if (locked) colorResource(R.color.ipv64_teal) else colorResource(R.color.ipv64_gray)
                            )
                        }
                        if (record.pendingDns == true) StatusChip("DNS pending", colorResource(R.color.ipv64_orange))
                    }
                }
                if (!locked) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            painter = painterResource(id = R.drawable.delete_24px),
                            contentDescription = "Delete record",
                            tint = colorResource(R.color.ipv64_red)
                        )
                    }
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.lock_24px),
                        contentDescription = "Locked",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(12.dp)
                            .size(20.dp)
                    )
                }
            }
        },
        showChevron = false,
        onClick = if (locked) null else onEdit
    )
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun DomainItemViewPreview() {
    AppTheme {
        val domain = Domain(name = "seb.home64.de", updateCount = 5, wildcard = true)
        ListGroup { DomainItemView(domain, null, NO_IPV4, NO_IPV6) {} }
    }
}
