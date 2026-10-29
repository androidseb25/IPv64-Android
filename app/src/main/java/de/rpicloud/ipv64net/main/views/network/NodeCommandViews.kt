package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.main.views.FormDropdown
import de.rpicloud.ipv64net.main.views.FormNumberField
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.SectionHeader
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.common.labelForToken
import de.sr.ipv64.model.Hc2Check
import de.sr.ipv64.model.Hc2Command
import de.sr.ipv64.model.Hc2CommandArgs
import de.sr.ipv64.model.Hc2ConflictKeep
import de.sr.ipv64.model.Hc2NodeCommandName
import de.sr.ipv64.model.Hc2NodeDetail
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// =============================================================================================
// Befehle: Modell und Texte
// =============================================================================================

// Ein zu sendender Befehl; targetLabel = Name des betroffenen Checks (nur für Texte)
data class NodeCommand(
    val name: Hc2NodeCommandName,
    val args: Hc2CommandArgs? = null,
    val targetLabel: String? = null
)

fun Hc2NodeCommandName.label(): String = when (this) {
    Hc2NodeCommandName.RUN_CHECK -> "Run check"
    Hc2NodeCommandName.REPAIR -> "Repair"
    Hc2NodeCommandName.PAUSE -> "Pause check"
    Hc2NodeCommandName.RESUME -> "Resume check"
    Hc2NodeCommandName.RESOLVE_CONFLICT -> "Resolve conflict"
}

// Name aus der API (z. B. "run_check") lesbar machen; Unbekanntes bleibt unverändert
fun String?.commandLabel(): String =
    Hc2NodeCommandName.entries.firstOrNull { it.value == this }?.label() ?: (this ?: "Command")

fun NodeCommand.confirmText(nodeName: String): String {
    val target = targetLabel?.let { "\"$it\"" }
    return when (name) {
        Hc2NodeCommandName.RUN_CHECK -> "$nodeName is asked to run the check $target now."
        Hc2NodeCommandName.PAUSE -> "$nodeName is asked to pause the check $target. It stays paused until you resume it."
        Hc2NodeCommandName.RESUME -> "$nodeName is asked to resume the check $target."
        Hc2NodeCommandName.REPAIR -> "$nodeName is asked to run its repair routine."
        Hc2NodeCommandName.RESOLVE_CONFLICT ->
            "Conflict #${args?.id} is resolved by keeping the ${args?.keep} version. The other version is discarded."
    }
}

// Befehl noch nicht von der Node bestätigt
fun Hc2Command.isPending(): Boolean = doneAt == null

fun JsonElement?.outputText(): String? {
    if (this == null || this is JsonNull) return null
    val text = if (this is JsonPrimitive) content else toString()
    return text.trim().takeIf { it.isNotEmpty() }?.take(400)
}

private fun JsonObject?.argsText(checks: List<Hc2Check>): String? {
    if (this == null) return null
    val parts = mutableListOf<String>()
    (this["token"] as? JsonPrimitive)?.content?.let { parts += checks.labelForToken(it) }
    (this["id"] as? JsonPrimitive)?.content?.let { parts += "Conflict #$it" }
    (this["keep"] as? JsonPrimitive)?.content?.let { parts += "keep $it" }
    return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
}

// =============================================================================================
// Befehlsschaltflächen
// =============================================================================================

private sealed interface CommandDialog {
    data class PickCheck(val name: Hc2NodeCommandName) : CommandDialog
    data object Resolve : CommandDialog
}

/**
 * Karte mit den Befehlen. Nur bei Fernverwaltung "full" gibt es Schaltflächen, sonst einen Hinweis.
 * Der Aufrufer bestätigt den gewählten Befehl ([onPick]) und sendet ihn.
 */
@Composable
fun NodeCommandActions(
    node: Hc2NodeDetail,
    enabled: Boolean,
    onPick: (NodeCommand) -> Unit
) {
    var dialog by remember { mutableStateOf<CommandDialog?>(null) }
    val canSend = node.remoteMgmt.allowsCommands()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Remote commands", style = MaterialTheme.typography.titleSmall)
            if (!canSend) {
                Text(
                    "Remote management is set to \"${node.remoteMgmt.label()}\". Set it to \"Full\" (Edit) to send commands to this node.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                if (node.online != true) {
                    Text(
                        "The node is offline. Commands are picked up when it reconnects.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        enabled = enabled && node.checks.any { it.token != null },
                        onClick = { dialog = CommandDialog.PickCheck(Hc2NodeCommandName.RUN_CHECK) },
                        modifier = Modifier.weight(1f)
                    ) { Text(Hc2NodeCommandName.RUN_CHECK.label(), maxLines = 1) }
                    OutlinedButton(
                        enabled = enabled,
                        onClick = { onPick(NodeCommand(Hc2NodeCommandName.REPAIR)) },
                        modifier = Modifier.weight(1f)
                    ) { Text(Hc2NodeCommandName.REPAIR.label(), maxLines = 1) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        enabled = enabled && node.checks.any { it.token != null && it.paused != true },
                        onClick = { dialog = CommandDialog.PickCheck(Hc2NodeCommandName.PAUSE) },
                        modifier = Modifier.weight(1f)
                    ) { Text(Hc2NodeCommandName.PAUSE.label(), maxLines = 1) }
                    OutlinedButton(
                        enabled = enabled && node.checks.any { it.token != null && it.paused == true },
                        onClick = { dialog = CommandDialog.PickCheck(Hc2NodeCommandName.RESUME) },
                        modifier = Modifier.weight(1f)
                    ) { Text(Hc2NodeCommandName.RESUME.label(), maxLines = 1) }
                }
                if ((node.conflicts ?: 0) > 0) {
                    OutlinedButton(
                        enabled = enabled,
                        onClick = { dialog = CommandDialog.Resolve },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("${Hc2NodeCommandName.RESOLVE_CONFLICT.label()} (${node.conflicts})", maxLines = 1) }
                }
            }
        }
    }

    when (val d = dialog) {
        is CommandDialog.PickCheck -> {
            val candidates = node.checks.filter {
                it.token != null && when (d.name) {
                    Hc2NodeCommandName.PAUSE -> it.paused != true
                    Hc2NodeCommandName.RESUME -> it.paused == true
                    else -> true
                }
            }
            NodeCheckSelectDialog(
                title = d.name.label(),
                checks = candidates,
                onPick = { check ->
                    dialog = null
                    val token = check.token ?: return@NodeCheckSelectDialog
                    onPick(
                        NodeCommand(
                            d.name,
                            Hc2CommandArgs(token = token),
                            check.name?.takeIf { it.isNotBlank() } ?: "…" + token.takeLast(4)
                        )
                    )
                },
                onDismiss = { dialog = null }
            )
        }

        CommandDialog.Resolve -> ResolveConflictDialog(
            onConfirm = { id, keep ->
                dialog = null
                onPick(NodeCommand(Hc2NodeCommandName.RESOLVE_CONFLICT, Hc2CommandArgs(id = id, keep = keep.value)))
            },
            onDismiss = { dialog = null }
        )

        null -> Unit
    }
}

// Check der Node wählen (kein Netzwerkzugriff, die Checks sind schon geladen)
@Composable
private fun NodeCheckSelectDialog(
    title: String,
    checks: List<Hc2Check>,
    onPick: (Hc2Check) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (checks.isEmpty()) {
                Text("No matching checks on this node.")
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    checks.forEachIndexed { index, check ->
                        if (index > 0) HorizontalDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(check) }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                check.name?.takeIf { it.isNotBlank() } ?: "…" + check.token.orEmpty().takeLast(4),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            check.target?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// Konflikt-Nummer und die Seite, die behalten wird
@Composable
private fun ResolveConflictDialog(
    onConfirm: (id: Long, keep: Hc2ConflictKeep) -> Unit,
    onDismiss: () -> Unit
) {
    var number by remember { mutableStateOf("") }
    var keep by remember { mutableStateOf(Hc2ConflictKeep.SERVER) }
    val id = number.toLongOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resolve conflict") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter the conflict number shown on the node and choose which version to keep.",
                    style = MaterialTheme.typography.bodyMedium
                )
                FormNumberField(label = "Conflict number", value = number, onValue = { number = it })
                FormDropdown(
                    label = "Keep",
                    value = keep,
                    options = Hc2ConflictKeep.entries,
                    labelOf = { if (it == Hc2ConflictKeep.SERVER) "Server version (ipv64.net)" else "Local version (node)" },
                    onSelect = { keep = it }
                )
            }
        },
        confirmButton = {
            TextButton(enabled = id != null, onClick = { id?.let { onConfirm(it, keep) } }) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// =============================================================================================
// Befehlsverlauf (wird erst beim Aufklappen geladen)
// =============================================================================================

fun LazyListScope.nodeCommandHistory(
    open: Boolean,
    loading: Boolean,
    loaded: Boolean,
    commands: List<Hc2Command>,
    checks: List<Hc2Check>,
    onToggle: () -> Unit,
    onRefresh: () -> Unit
) {
    item(key = "history_header") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader("Command history", modifier = Modifier.weight(1f))
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
            } else if (open) {
                IconButton(onClick = onRefresh) {
                    Icon(painter = painterResource(R.drawable.refresh_24px), contentDescription = "Reload commands")
                }
            }
            Icon(
                painter = painterResource(if (open) R.drawable.expand_less_24px else R.drawable.expand_more_24px),
                contentDescription = if (open) "Collapse" else "Expand",
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
    if (!open) return
    if (loaded && commands.isEmpty()) {
        item(key = "history_empty") { EmptyState("No commands sent yet.") }
    }
    items(commands.size, key = { "cmd_${commands[it].commandId ?: it}" }) { index ->
        NodeCommandRow(commands[index], checks)
    }
}

@Composable
private fun NodeCommandRow(command: Hc2Command, checks: List<Hc2Check>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    command.name.commandLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                when {
                    command.doneAt == null -> StatusChip("Pending", colorResource(R.color.ipv64_orange))
                    command.ok == true -> StatusChip("OK", colorResource(R.color.ipv64_green))
                    command.ok == false -> StatusChip("Failed", colorResource(R.color.ipv64_red))
                    else -> StatusChip("Done", colorResource(R.color.ipv64_gray))
                }
            }
            command.args.argsText(checks)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val times = listOfNotNull(
                command.createdAt?.let { "sent ${it.formatRelative()}" },
                command.doneAt?.let { "finished ${it.formatGermanTime()}" }
            ).joinToString(" · ")
            if (times.isNotEmpty()) {
                Text(times, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            command.output.outputText()?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
