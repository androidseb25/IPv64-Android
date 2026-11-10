package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.main.views.common.ActionSheet
import de.rpicloud.ipv64net.helper.formatDuration
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.main.views.common.GroupDivider
import de.rpicloud.ipv64net.main.views.common.GroupText
import de.rpicloud.ipv64net.main.views.common.InfoGroup
import de.rpicloud.ipv64net.main.views.common.InfoItem
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.sr.ipv64.node64.model.DNS64Status
import de.sr.ipv64.node64.model.ModuleStatus
import de.sr.ipv64.node64.model.NodeStatus
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Modulname laut Node64-Doku/Tests: "dns64"
fun NodeStatus.dnsModule(): ModuleStatus? = modules.firstOrNull { it.name.equals("dns64", ignoreCase = true) }

// Intervall der automatischen Aktualisierung (nur solange der Screen RESUMED ist)
const val REFRESH_MS = 5_000L

const val LOST_TEXT = "Connection lost — retrying…"

class LiveInfo(val resumed: Boolean, val failing: Boolean, val lastOk: Long)

// Live-Zustand der lokalen Verbindung als Chip: Pausiert (Screen nicht sichtbar) / Offline (letzter Tick fehlgeschlagen) / Live
@Composable
fun LiveInfo.chip(): Pair<String, Color> = when {
    lastOk == 0L -> "Connecting" to colorResource(R.color.ipv64_gray)
    !resumed -> "Paused" to colorResource(R.color.ipv64_gray)
    failing -> "Offline" to MaterialTheme.colorScheme.error
    else -> "Live" to colorResource(R.color.ipv64_green)
}

// Uhr nur hier gelesen: pro Sekunde wird nur diese Zeile neu gezeichnet
@Composable
fun UpdatedText(lastOk: Long, clock: State<Long>) {
    val sec = ((clock.value - lastOk) / 1000).coerceAtLeast(0)
    GroupText(if (sec < 2) "Updated just now" else "Updated ${sec.formatDuration()} ago")
}

@Composable
fun LocalModuleRow(m: ModuleStatus, busy: Boolean, onOpen: (() -> Unit)? = null, onToggle: (Boolean) -> Unit) {
    val (label, color) = when {
        m.enabled == false -> "Disabled" to R.color.ipv64_gray
        m.running == true -> "Running" to R.color.ipv64_green
        else -> "Not running" to R.color.ipv64_orange
    }
    ListRow(
        title = m.name ?: "–",
        subtitle = m.version,
        onClick = onOpen,
        showChevron = onOpen != null,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(label, colorResource(color))
                Switch(checked = m.enabled == true, onCheckedChange = onToggle, enabled = !busy && m.name != null)
            }
        }
    )
    m.lastError?.takeIf { it.isNotBlank() }?.let { GroupText(it, error = true) }
}

private const val LOG_PREVIEW = 5

/** Logs der Node, [lines] = Rohzeilen, neueste zuerst: zuerst [LOG_PREVIEW], der Rest hinter "Show all logs". */
@Composable
fun NodeLogsGroup(lines: List<String>) {
    var all by remember { mutableStateOf(false) }
    var raw by remember { mutableStateOf(emptySet<String>()) }
    ListGroup(title = "Logs") {
        if (lines.isEmpty()) GroupText("No log lines.")
        (if (all) lines else lines.take(LOG_PREVIEW)).forEachIndexed { i, line ->
            if (i > 0) GroupDivider()
            NodeLogLine(line, showRaw = line in raw) { raw = if (line in raw) raw - line else raw + line }
        }
        if (lines.size > LOG_PREVIEW) {
            GroupDivider()
            ListRow(
                title = if (all) "Show fewer logs" else "Show all logs",
                showChevron = false,
                trailing = {
                    Icon(painter = painterResource(if (all) R.drawable.expand_less_24px else R.drawable.expand_more_24px), contentDescription = null)
                },
                onClick = { all = !all }
            )
        }
    }
}

// Eine Zeile; Antippen zeigt/versteckt den Rohtext. Der Level steht auch als Text da (nicht nur Farbe).
@Composable
private fun NodeLogLine(line: String, showRaw: Boolean, onToggle: () -> Unit) {
    val e = remember(line) { parseNodeLogLine(line) }
    val color = when (e?.level) {
        "DEBUG" -> MaterialTheme.colorScheme.onSurfaceVariant
        "WARN", "WARNING" -> colorResource(R.color.ipv64_orange)
        "ERROR" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (e == null) {
            Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = if (showRaw) FontFamily.Monospace else null)
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            nodeLogClock(e.time)?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            e.level?.let { Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color) }
            e.module?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        e.msg?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = color) }
        if (showRaw) {
            Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Log-Zeilen sind nicht dokumentiert: "msg" eines Objekts, sonst der Rohtext
// Objekte werden zu einer logfmt-Zeile (time, level, module, msg), damit die Anzeige sie wie Text-Zeilen parst
fun JsonElement.logText(): String {
    if (this is JsonPrimitive) return contentOrNull ?: toString()
    val o = this as? JsonObject ?: return toString()
    fun f(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
    val msg = f("msg") ?: return toString()
    fun q(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    return listOfNotNull(f("time")?.let { "time=" + q(it) }, f("level")?.let { "level=" + q(it) }, f("module")?.let { "module=" + q(it) }, "msg=" + q(msg))
        .joinToString(" ")
}
