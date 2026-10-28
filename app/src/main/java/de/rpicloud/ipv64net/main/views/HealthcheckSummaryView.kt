package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import de.rpicloud.ipv64net.models.Hc2StateUi
import de.sr.ipv64.model.Hc2Check

// Zähler der Liste, lokal berechnet (Degraded = degraded + flapping, pending zählt nur an der Zeile)
data class HealthcheckCounts(val up: Int, val degraded: Int, val down: Int, val paused: Int)

fun List<Hc2Check>.counts(): HealthcheckCounts {
    var up = 0
    var degraded = 0
    var down = 0
    var paused = 0
    forEach {
        when (Hc2StateUi.from(it.state?.state, it.paused)) {
            Hc2StateUi.UP -> up++
            Hc2StateUi.DEGRADED, Hc2StateUi.FLAPPING -> degraded++
            Hc2StateUi.DOWN -> down++
            Hc2StateUi.PAUSED -> paused++
            else -> Unit
        }
    }
    return HealthcheckCounts(up, degraded, down, paused)
}

// Eine Kachel der 2x2-Übersicht
@Composable
fun HealthcheckGridItemView(count: Int, state: Hc2StateUi, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = colorResource(state.color)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painter = painterResource(state.icon),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.height(28.dp)
                )
            }
            Text(
                count.toString(),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

// 2x2-Übersicht: Up / Degraded / Down / Paused
@Composable
fun HealthcheckSummaryView(counts: HealthcheckCounts, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HealthcheckGridItemView(counts.up, Hc2StateUi.UP, "Up", Modifier.weight(1f))
            HealthcheckGridItemView(counts.degraded, Hc2StateUi.DEGRADED, "Degraded", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HealthcheckGridItemView(counts.down, Hc2StateUi.DOWN, "Down", Modifier.weight(1f))
            HealthcheckGridItemView(counts.paused, Hc2StateUi.PAUSED, "Paused", Modifier.weight(1f))
        }
    }
}
