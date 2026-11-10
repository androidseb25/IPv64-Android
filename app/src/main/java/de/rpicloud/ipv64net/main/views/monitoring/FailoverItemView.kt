package de.rpicloud.ipv64net.main.views.monitoring

import androidx.compose.runtime.Composable
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.sr.ipv64.model.FailoverPolicy

// Zeile der Failover-Liste: nur Daten aus der Liste, keine Einzelabfragen
@Composable
fun FailoverItemView(policy: FailoverPolicy, onClick: (FailoverPolicy) -> Unit) {
    val down = policy.monitorsDown ?: 0
    val monitors = "${policy.monitors.size} monitor" + (if (policy.monitors.size == 1) "" else "s") +
            (if (down > 0) " ($down down)" else "")
    val rules = "${policy.rules.size} rule" + (if (policy.rules.size == 1) "" else "s")
    val lastCheck = policy.lastCheckAt?.let { " · checked ${it.formatRelative()}" }.orEmpty()
    ListRow(
        title = policy.name?.takeIf { it.isNotBlank() } ?: "Unnamed policy",
        subtitle = "${policy.mode.failoverTitle()}\n$monitors · $rules$lastCheck",
        trailing = { StatusChip(policy.status.failoverLabel(), policy.status.failoverColor()) },
        onClick = { onClick(policy) }
    )
}
