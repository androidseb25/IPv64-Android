package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.channelTypeIcon
import de.rpicloud.ipv64net.helper.displayName
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.ui.theme.AppTheme
import de.sr.ipv64.model.Hc2Channel

/**
 * Zeile eines hc2-Kanals: Typ-Icon, Name, Typname, Badges (Default / Disabled / Not verified / This device),
 * letzte Nutzung und letzter Fehler. Keine Netzwerkaufrufe pro Zeile.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChannelItemView(
    channel: Hc2Channel,
    awaitingVerification: Boolean,
    isThisDevice: Boolean,
    onClick: () -> Unit,
    onVerifyClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                painter = painterResource(id = channelTypeIcon(channel.type)),
                contentDescription = channel.typeLabel ?: channel.type ?: "icon",
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(28.dp)
            )
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    channel.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val typeText = channel.typeLabel?.takeIf { it.isNotBlank() } ?: channel.type
                if (!typeText.isNullOrBlank()) {
                    Text(
                        typeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val showBadges = channel.default == true || channel.enabled == false || awaitingVerification || isThisDevice
                if (showBadges) {
                    FlowRow(
                        modifier = Modifier.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isThisDevice) ChannelBadge("This device", colorResource(R.color.ipv64_teal))
                        if (channel.default == true) ChannelBadge("Default", colorResource(R.color.ipv64_blue))
                        if (channel.enabled == false) ChannelBadge("Disabled", colorResource(R.color.ipv64_gray))
                        if (awaitingVerification) {
                            ChannelBadge("Not verified", colorResource(R.color.ipv64_orange), onClick = onVerifyClick)
                        }
                    }
                }
                Text(
                    channel.lastUsed?.let { "Last used: ${it.formatRelative()}" } ?: "Never used",
                    style = MaterialTheme.typography.bodySmall
                )
                channel.lastError?.trim()?.takeIf { it.isNotEmpty() }?.let { error ->
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorResource(R.color.ipv64_red),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// Kleines farbiges Etikett; mit onClick antippbar (z. B. "Not verified" öffnet die Bestätigung)
@Composable
fun ChannelBadge(text: String, color: Color, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(50)
    val content: @Composable () -> Unit = {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = color.copy(alpha = 0.15f), content = content)
    } else {
        Surface(shape = shape, color = color.copy(alpha = 0.15f), content = content)
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun ChannelItemViewPreview() {
    AppTheme {
        ChannelItemView(
            Hc2Channel(id = 1, type = "ipv64_mail", typeLabel = "E-Mail", name = "Mail", default = true, verified = false),
            awaitingVerification = true,
            isThisDevice = false,
            onClick = {},
            onVerifyClick = {}
        )
    }
}
