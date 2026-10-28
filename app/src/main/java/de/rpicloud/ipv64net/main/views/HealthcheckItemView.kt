package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.helper.formatLatency
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.models.Hc2StateUi
import de.rpicloud.ipv64net.models.label
import de.sr.ipv64.model.Hc2Check
import de.sr.ipv64.model.Hc2Execution

// Zustands-Badge (Icon + Text in der Zustandsfarbe)
@Composable
fun Hc2StateBadge(state: Hc2StateUi, modifier: Modifier = Modifier) {
    val color = colorResource(state.color)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(state.icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(state.label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
        }
    }
}

// Kurzer Hinweis, wo ein Check läuft (null = online, keine Anzeige)
fun Hc2Check.executionLabel(): String? = when (execution) {
    Hc2Execution.LOCAL -> nodeName?.takeIf { it.isNotBlank() } ?: "Node"
    Hc2Execution.BOTH -> "Online + ${nodeName?.takeIf { it.isNotBlank() } ?: "Node"}"
    else -> null
}

// Zeile der Check-Liste: nur Daten aus der Liste, keine Einzelabfragen
@Composable
fun HealthcheckItemView(check: Hc2Check, onClick: (Hc2Check) -> Unit) {
    val state = Hc2StateUi.from(check.state?.state, check.paused)
    val color = colorResource(state.color)
    Card(modifier = Modifier.fillMaxWidth(), onClick = { onClick(check) }) {
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
                    check.name.orEmpty().ifBlank { "(unnamed)" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Hc2StateBadge(state)
            }
            val target = check.target.orEmpty()
            Text(
                if (target.isBlank()) check.type.label else "${check.type.label} · $target",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val details = listOfNotNull(
                check.state?.lastCheckAt?.let { "Checked ${it.formatRelative()}" },
                check.state?.lastLatencyMs?.formatLatency(),
                check.executionLabel()
            )
            if (details.isNotEmpty() || check.paused == true) {
                Text(
                    buildList {
                        if (check.paused == true) add("Paused")
                        addAll(details)
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (check.paused == true) color else Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
