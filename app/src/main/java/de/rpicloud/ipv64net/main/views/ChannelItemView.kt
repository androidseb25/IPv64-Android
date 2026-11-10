package de.rpicloud.ipv64net.main.views

import androidx.compose.ui.semantics.Role
import androidx.compose.material3.minimumInteractiveComponentSize
import de.rpicloud.ipv64net.main.views.common.GroupText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.channelTypeIcon
import de.rpicloud.ipv64net.helper.displayName
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.ui.theme.AppTheme
import de.sr.ipv64.model.Hc2Channel

/**
 * Zeile eines hc2-Kanals (für eine [ListGroup]): Typ-Icon, Name, Typname, letzte Nutzung/Fehler als Untertitel,
 * Chips (This device / Default / Disabled / Not verified) rechts. Keine Netzwerkaufrufe pro Zeile.
 */
@Composable
fun ChannelItemView(
    channel: Hc2Channel,
    awaitingVerification: Boolean,
    isThisDevice: Boolean,
    onClick: () -> Unit,
    onVerifyClick: () -> Unit
) {
    val typeText = channel.typeLabel?.takeIf { it.isNotBlank() } ?: channel.type
    val subtitle = listOfNotNull(
        typeText?.takeIf { it.isNotBlank() },
        channel.lastUsed?.let { "Last used: ${it.formatRelative()}" } ?: "Never used",
    ).joinToString("\n")
    val lastError = channel.lastError?.trim()?.takeIf { it.isNotEmpty() }
    val hasChips = channel.default == true || channel.enabled == false || awaitingVerification || isThisDevice
    Column {
    ListRow(
        title = channel.displayName,
        subtitle = subtitle,
        subtitleMaxLines = 3,
        icon = channelTypeIcon(channel.type),
        trailing = if (!hasChips) null else {
            {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isThisDevice) ChannelBadge("This device", colorResource(R.color.ipv64_teal))
                    if (channel.default == true) ChannelBadge("Default", colorResource(R.color.ipv64_blue))
                    if (channel.enabled == false) ChannelBadge("Disabled", colorResource(R.color.ipv64_gray))
                    if (awaitingVerification) {
                        ChannelBadge("Not verified", colorResource(R.color.ipv64_orange), onClick = onVerifyClick)
                    }
                }
            }
        },
        showChevron = false,
        onClick = onClick
    )
    lastError?.let { GroupText(it, error = true) }
    }
}

// Status-Chip; mit onClick antippbar (z. B. "Not verified" öffnet die Bestätigung)
@Composable
fun ChannelBadge(text: String, color: Color, onClick: (() -> Unit)? = null) {
    StatusChip(
        text, color,
        modifier = if (onClick != null) {
            // 48dp-Touch-Target + Button-Rolle für das antippbare Chip
            Modifier.clip(RoundedCornerShape(50)).minimumInteractiveComponentSize().clickable(role = Role.Button, onClick = onClick)
        } else Modifier
    )
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun ChannelItemViewPreview() {
    AppTheme {
        ListGroup {
            ChannelItemView(
                Hc2Channel(id = 1, type = "ipv64_mail", typeLabel = "E-Mail", name = "Mail", default = true, verified = false),
                awaitingVerification = true,
                isThisDevice = false,
                onClick = {},
                onVerifyClick = {}
            )
        }
    }
}
