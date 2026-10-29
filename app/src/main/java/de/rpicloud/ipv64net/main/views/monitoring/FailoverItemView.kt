package de.rpicloud.ipv64net.main.views.monitoring

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.sr.ipv64.model.FailoverPolicy

// Zeile der Failover-Liste: nur Daten aus der Liste, keine Einzelabfragen
@Composable
fun FailoverItemView(policy: FailoverPolicy, onClick: (FailoverPolicy) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = { onClick(policy) }) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = policy.name?.takeIf { it.isNotBlank() } ?: "Unnamed policy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(policy.status.failoverLabel(), policy.status.failoverColor())
            }
            Text(
                text = policy.mode.failoverTitle(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val down = policy.monitorsDown ?: 0
            val monitors = "${policy.monitors.size} monitor" + (if (policy.monitors.size == 1) "" else "s") +
                    (if (down > 0) " ($down down)" else "")
            val rules = "${policy.rules.size} rule" + (if (policy.rules.size == 1) "" else "s")
            val lastCheck = policy.lastCheckAt?.let { " · checked ${it.formatRelative()}" }.orEmpty()
            Text(
                text = "$monitors · $rules$lastCheck",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
