package de.rpicloud.ipv64net.main.views.network

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.TimedCache
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.helper.pollUntil
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.sr.ipv64.model.Hc2Command
import de.sr.ipv64.model.Hc2Node
import de.sr.ipv64.model.Hc2NodeDetail
import de.sr.ipv64.model.Hc2RemoteMgmt
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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

// Wartet (3 s Takt, höchstens 60 s), bis die Node den Befehl abgeschlossen hat; null bei Zeitüberschreitung.
// [onEach] erhält nach jeder Abfrage die aktuelle Befehlsliste (z. B. für den Verlauf).
suspend fun awaitNodeCommand(
    ctx: Context,
    nodeId: String,
    commandId: Long,
    onEach: (List<Hc2Command>) -> Unit = {}
): Hc2Command? = pollUntil(
    intervalMs = 3_000,
    maxMs = 60_000,
    onEach = onEach,
    fetch = { IPv64Client.get(ctx).nodes.commands(nodeId, 20) },
    done = { l: List<Hc2Command> -> l.any { it.commandId == commandId && it.doneAt != null } }
)?.firstOrNull { it.commandId == commandId }

// =============================================================================================
// Listenzeile
// =============================================================================================

// Zeile der Node-Liste (für eine ListGroup): nur Daten aus der Liste, keine Einzelabfragen
@Composable
fun NodeRow(node: Hc2Node, onClick: (Hc2Node) -> Unit) {
    val online = node.online == true
    val meta = listOfNotNull(
        node.location?.takeIf { it.isNotBlank() },
        node.version?.takeIf { it.isNotBlank() }?.let { "v$it" },
        node.lastSeen?.let { "seen ${it.formatRelative()}" }
    ).joinToString(" · ")
    val conflicts = node.conflicts ?: 0
    val remote = "Remote: " + node.remoteMgmt.label() +
            if (conflicts > 0) " · $conflicts conflict" + (if (conflicts == 1) "" else "s") else ""
    ListRow(
        title = node.displayName(),
        subtitle = if (meta.isNotEmpty()) "$meta\n$remote" else remote,
        trailing = {
            StatusChip(
                if (online) "Online" else "Offline",
                colorResource(if (online) R.color.ipv64_green else R.color.ipv64_gray)
            )
        },
        onClick = { onClick(node) }
    )
}

// Zeile einer direkt verbundenen Node (ohne Cloud): Name aus dem gespeicherten Status, Untertitel = Host der Adresse
@Composable
fun DirectNodeRow(nodeId: String, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val url = LocalNodeStore.get(ctx, nodeId)?.first
    val name = LocalNodeStore.name(ctx, nodeId)
    ListRow(
        title = name?.takeIf { it.isNotBlank() } ?: ("Node …" + nodeId.takeLast(4)),
        subtitle = url?.let { it.toHttpUrlOrNull()?.host ?: it },
        trailing = { StatusChip("Direct", colorResource(R.color.ipv64_blue)) },
        onClick = onClick
    )
}
