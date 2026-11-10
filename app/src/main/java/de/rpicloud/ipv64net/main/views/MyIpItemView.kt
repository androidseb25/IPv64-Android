package de.rpicloud.ipv64net.main.views

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.helper.copyText
import de.rpicloud.ipv64net.main.views.common.InfoRow

/** Zeile einer eigenen IP-Adresse (in einer ListGroup); Antippen kopiert die Adresse. */
@Composable
fun MyIpItemView(
    ctx: Context,
    haptics: HapticFeedback,
    ip: String
) {
    InfoRow(
        label = if (ip.contains(other = ":")) "IPv6" else "IPv4",
        value = ip,
        copyable = true,
        monospace = true,
        modifier = Modifier
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                copyText(ctx, "IP", ip)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}
