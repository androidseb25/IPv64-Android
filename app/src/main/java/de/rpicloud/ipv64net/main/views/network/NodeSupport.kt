package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.TimedCache
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.sr.ipv64.model.Hc2Node
import de.sr.ipv64.model.Hc2NodeDetail
import de.sr.ipv64.model.Hc2RemoteMgmt

// =============================================================================================
// Cache
// =============================================================================================

// Liste der verknüpften Nodes (30 s); Änderungen schreiben das zurückgelieferte Objekt zurück
val NodeListCache = TimedCache<List<Hc2Node>>(30_000)

// Node in der gecachten Liste ersetzen bzw. anhängen; ohne gecachte Liste nichts tun
fun upsertCachedNode(node: Hc2Node) {
    val current = NodeListCache.peek() ?: return
    NodeListCache.put(
        if (current.any { it.nodeId == node.nodeId }) current.map { if (it.nodeId == node.nodeId) node else it }
        else current + node
    )
}

// Node aus der gecachten Liste entfernen
fun removeCachedNode(nodeId: String) {
    val current = NodeListCache.peek() ?: return
    NodeListCache.put(current.filterNot { it.nodeId == nodeId })
}

// =============================================================================================
// Modelle und Beschriftungen
// =============================================================================================

// Listenansicht einer Detail-Node (ohne Checks)
fun Hc2NodeDetail.toNode(): Hc2Node = Hc2Node(
    nodeId = nodeId, name = name, location = location, version = version, timezone = timezone,
    modules = modules, remoteMgmt = remoteMgmt, online = online, lastSeen = lastSeen, lastSync = lastSync,
    outboxSize = outboxSize, conflicts = conflicts, hub = hub, linkedAt = linkedAt
)

// Antwort von PATCH übernehmen; fehlende Werte der Antwort bleiben beim bisherigen Stand
fun Hc2NodeDetail.withUpdate(n: Hc2Node, name: String?, location: String?, mgmt: Hc2RemoteMgmt?): Hc2NodeDetail = copy(
    name = n.name ?: name ?: this.name,
    location = n.location ?: location ?: this.location,
    remoteMgmt = n.remoteMgmt ?: mgmt ?: this.remoteMgmt,
    version = n.version ?: version,
    timezone = n.timezone ?: timezone,
    modules = n.modules.ifEmpty { modules },
    online = n.online ?: online,
    lastSeen = n.lastSeen ?: lastSeen,
    lastSync = n.lastSync ?: lastSync,
    outboxSize = n.outboxSize ?: outboxSize,
    conflicts = n.conflicts ?: conflicts,
    hub = n.hub ?: hub
)

fun Hc2Node.displayName(): String =
    name?.takeIf { it.isNotBlank() } ?: "Node …" + nodeId.orEmpty().takeLast(4)

fun Hc2NodeDetail.displayName(): String =
    name?.takeIf { it.isNotBlank() } ?: "Node …" + nodeId.orEmpty().takeLast(4)

fun Hc2RemoteMgmt?.label(): String = when (this) {
    Hc2RemoteMgmt.OFF -> "Off"
    Hc2RemoteMgmt.READ -> "Read only"
    Hc2RemoteMgmt.FULL -> "Full"
    null -> "Unknown"
}

fun Hc2RemoteMgmt.description(): String = when (this) {
    Hc2RemoteMgmt.OFF -> "Remote management is off."
    Hc2RemoteMgmt.READ -> "Read access, the node does not accept commands."
    Hc2RemoteMgmt.FULL -> "Commands from ipv64.net (run check, repair, pause, resume) are accepted."
}

// Befehle sind nur mit "full" erlaubt
fun Hc2RemoteMgmt?.allowsCommands(): Boolean = this == Hc2RemoteMgmt.FULL

// =============================================================================================
// Listenzeile
// =============================================================================================

// Zeile der Node-Liste: nur Daten aus der Liste, keine Einzelabfragen
@Composable
fun NodeItemView(node: Hc2Node, onClick: (Hc2Node) -> Unit) {
    val online = node.online == true
    Card(modifier = Modifier.fillMaxWidth(), onClick = { onClick(node) }) {
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
                    text = node.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(
                    if (online) "Online" else "Offline",
                    colorResource(if (online) R.color.ipv64_green else R.color.ipv64_gray)
                )
            }
            val line = listOfNotNull(
                node.location?.takeIf { it.isNotBlank() },
                node.version?.takeIf { it.isNotBlank() }?.let { "v$it" },
                node.lastSeen?.let { "seen ${it.formatRelative()}" }
            ).joinToString(" · ")
            if (line.isNotEmpty()) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip("Remote: " + node.remoteMgmt.label(), colorResource(R.color.ipv64_teal))
                val conflicts = node.conflicts ?: 0
                if (conflicts > 0) {
                    StatusChip("$conflicts conflict" + if (conflicts == 1) "" else "s", colorResource(R.color.ipv64_orange))
                }
            }
        }
    }
}
