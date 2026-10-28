package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.helper.channelTypeIcon
import de.rpicloud.ipv64net.helper.displayName
import de.rpicloud.ipv64net.helper.isAwaitingVerification
import de.sr.ipv64.model.Hc2Channel
import de.sr.ipv64.model.Hc2ChannelType

// Kanalauswahl: "Default channels" (channels = []) oder eine Auswahl von Kanälen
@Composable
fun HealthcheckChannelPicker(
    useDefault: Boolean,
    onUseDefaultChange: (Boolean) -> Unit,
    channels: List<Hc2Channel>,
    types: List<Hc2ChannelType>?,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onUseDefaultChange(true) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = useDefault, onClick = { onUseDefaultChange(true) })
            Column {
                Text("Default channels", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Notify all channels marked as default",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onUseDefaultChange(false) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = !useDefault, onClick = { onUseDefaultChange(false) })
            Text("Selected channels", style = MaterialTheme.typography.bodyLarge)
        }
        if (!useDefault) {
            if (channels.isEmpty()) {
                Text(
                    "No channels yet. Create one in the Channels tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 48.dp)
                )
            }
            channels.forEach { ch ->
                val id = ch.id ?: return@forEach
                val disabled = ch.enabled == false
                val unverified = ch.isAwaitingVerification(types)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp)
                        .clickable { onToggle(id) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = id in selected, onCheckedChange = { onToggle(id) })
                    Icon(
                        painter = painterResource(channelTypeIcon(ch.type)),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(ch.displayName, style = MaterialTheme.typography.bodyMedium)
                        val badges = buildList {
                            if (disabled) add("Disabled")
                            if (unverified) add("Not verified")
                        }
                        if (badges.isNotEmpty()) Text(
                            badges.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
