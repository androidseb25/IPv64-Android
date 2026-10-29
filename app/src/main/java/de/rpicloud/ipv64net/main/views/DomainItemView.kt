package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.models.IsWildcard
import de.rpicloud.ipv64net.models.effectiveKind
import de.rpicloud.ipv64net.models.isLocked
import de.rpicloud.ipv64net.models.label
import de.rpicloud.ipv64net.models.lockReason
import de.rpicloud.ipv64net.models.prefixLabel
import de.rpicloud.ipv64net.ui.theme.AppTheme
import de.sr.ipv64.model.Domain
import de.sr.ipv64.model.DomainKind
import de.sr.ipv64.model.Record
import de.sr.ipv64.model.RecordState

@Composable
fun DomainItemView(
    domain: Domain,
    tintColor: Color,
    onClick: (selectedDomain: Domain) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onClick(domain) }
    ) {
        Row {
            Column(
                modifier = Modifier
                    .padding(top = 24.dp, bottom = 16.dp, start = 16.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.circle_24px),
                    contentDescription = "icon",
                    tint = tintColor,
                    modifier = Modifier.height(14.dp)
                )
            }
            Column(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 16.dp, start = 8.dp, end = 16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    domain.name?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (domain.active == false) {
                        StatusChip(
                            "Inactive", colorResource(R.color.ipv64_gray),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Text(
                    "Updates: ${domain.updateCount ?: 0}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "Wildcard: ${domain.IsWildcard}",
                    style = MaterialTheme.typography.titleSmall
                )
                if (domain.effectiveKind == DomainKind.OWN) {
                    Text(
                        "Own domain",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Karte eines DNS-Records in der Domain-Detailansicht.
 * Gesperrte Records (CDN/Failover) zeigen einen Status-Chip und sind weder antippbar noch löschbar.
 * Antippen öffnet die Bearbeitung ([onEdit]), der Papierkorb fragt vor dem Löschen nach ([onDelete]).
 */
@Composable
fun DomainRecordCard(
    record: Record,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val locked = record.isLocked
    Card(
        onClick = onEdit,
        enabled = !locked,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    record.type?.value ?: "?",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    record.prefixLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
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
                        modifier = Modifier
                            .padding(12.dp)
                            .size(20.dp)
                            .alpha(0.7f)
                    )
                }
            }
            Text(
                record.content ?: "–",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(end = 12.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Text(
                    "TTL ${record.ttl ?: "–"} · " + (record.updatedAt?.formatGermanTime() ?: "–"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            val state = record.state
            if ((state != null && state != RecordState.ACTIVE) || record.pendingDns == true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 12.dp)) {
                    if (state != null && state != RecordState.ACTIVE) {
                        StatusChip(
                            state.label,
                            if (locked) colorResource(R.color.ipv64_teal) else colorResource(R.color.ipv64_gray)
                        )
                    }
                    if (record.pendingDns == true) {
                        StatusChip("DNS pending", colorResource(R.color.ipv64_orange))
                    }
                }
            }
            record.lockReason?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun DomainItemViewPreview() {
    AppTheme {
        val domain = Domain(name = "seb.home64.de", updateCount = 5, wildcard = true)
        DomainItemView(domain, Color.Green) {}
    }
}
