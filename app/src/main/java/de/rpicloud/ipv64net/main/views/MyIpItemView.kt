package de.rpicloud.ipv64net.main.views

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.models.IPResult

@Composable
fun MyIpItemView(
    ctx: Context,
    haptics: HapticFeedback,
    ip: IPResult
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            val clipboard =
                ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip =
                ClipData.newPlainText("IP", ip.ip)
            clipboard.setPrimaryClip(clip)
        }
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            ip.ip?.let {
                if (it.contains(other = ":")) {
                    Text(
                        "IPv6:",
                        style = MaterialTheme.typography.titleSmall
                    )
                } else {
                    Text(
                        "IPv4:",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
            ip.ip?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}