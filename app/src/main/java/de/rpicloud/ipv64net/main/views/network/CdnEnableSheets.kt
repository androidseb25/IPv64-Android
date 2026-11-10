package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row

/** Ein Proxy-Host, für den CDN64 eingeschaltet werden kann. */
class CdnHostChoice(val nodeId: String, val nodeName: String, val hostId: Long, val label: String)

// Modus-Karte; nur 1 und 2 sind wählbar, 3 ist reine Info
@Composable
private fun ModeCard(mode: Int, selected: Boolean, selectable: Boolean, onClick: () -> Unit) {
    val (title, desc) = cdnModeLabel(mode)
    Surface(
        shape = MaterialTheme.shapes.large,
        // Theme ist monochrom: primaryContainer wäre hellgrau unter weißer Schrift, daher nur Rahmen + Radio
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().clickable(enabled = selectable, onClick = onClick)
    ) {
      Row(Modifier.padding(end = 12.dp, top = 4.dp, bottom = 8.dp)) {
        if (selectable) RadioButton(selected, onClick) else Spacer(Modifier.width(48.dp))
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val alpha = if (selectable) 1f else 0.6f
            Text("Mode $mode · $title", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            cdnModeFlow(mode)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            }
        }
      }
    }
}

/** Modus (1/2, Standard 1) und Healthcheck-Option wählen, bevor CDN64 für einen Host eingeschaltet wird. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CdnModeSheet(hostLabel: String?, onConfirm: (mode: Int, healthcheck: Boolean) -> Unit, onDismiss: () -> Unit) {
    var mode by remember { mutableIntStateOf(1) }
    var hc by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Enable CDN64", style = MaterialTheme.typography.titleLarge)
            hostLabel?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            Text(
                "Makes this host reachable from the internet via the ipv64 CDN64 servers, over IPv4 and IPv6. " +
                    "The node builds a tunnel outwards; no port needs to be open at home.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            listOf(1, 2, 3).forEach { m -> ModeCard(m, selected = mode == m, selectable = m != 3) { mode = m } }
            Row(Modifier.fillMaxWidth().clickable { hc = !hc }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(hc, { hc = it })
                Text("Create healthcheck")
            }
            Button(onClick = { onConfirm(mode, hc) }, modifier = Modifier.fillMaxWidth()) { Text("Enable") }
        }
    }
}

/** Erst Host wählen, dann Modus; [onEnable] erledigt den Aufruf. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CdnEnableFlow(choices: List<CdnHostChoice>, onEnable: (CdnHostChoice, Int, Boolean) -> Unit, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf<CdnHostChoice?>(null) }
    val p = picked
    if (p != null) {
        CdnModeSheet(p.label, { m, hc -> onEnable(p, m, hc); onDismiss() }, onDismiss)
        return
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Enable CDN64 for a proxy host", style = MaterialTheme.typography.titleLarge)
            if (choices.isEmpty()) Text("All proxy hosts already use CDN64.", style = MaterialTheme.typography.bodyMedium)
            LazyColumn {
                items(choices, key = { "${it.nodeId}/${it.hostId}" }) { c ->
                    Column(Modifier.fillMaxWidth().clickable { picked = c }.padding(vertical = 10.dp)) {
                        Text(c.label, style = MaterialTheme.typography.titleMedium)
                        Text(c.nodeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
