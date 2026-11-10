package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import de.rpicloud.ipv64net.main.views.common.GroupText
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import de.rpicloud.ipv64net.main.views.common.ActionSheet
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.main.views.FormDropdown
import de.rpicloud.ipv64net.main.views.FormNumberField
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.GroupDivider
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.SectionHeader
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.common.labelForToken
import de.sr.ipv64.model.Hc2Check
import de.sr.ipv64.model.Hc2Command
import de.sr.ipv64.model.Hc2CommandArgs
import de.sr.ipv64.model.Hc2ConflictKeep
import de.sr.ipv64.model.Hc2NodeCommandName
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
// Befehle pro Check
// =============================================================================================

// Befehl für einen Check der Node; null ohne Token
private fun Hc2Check.command(cmd: Hc2NodeCommandName): NodeCommand? {
    val t = token ?: return null
    return NodeCommand(cmd, Hc2CommandArgs(token = t), name?.takeIf { it.isNotBlank() } ?: "…" + t.takeLast(4))
}

/** ⋮-Menü einer Check-Zeile: Run check und Pause/Resume. Der Aufrufer bestätigt den Befehl ([onPick]) und sendet ihn. */
@Composable
fun CheckCommandMenu(check: Hc2Check, enabled: Boolean, onPick: (NodeCommand) -> Unit) {
    if (check.token == null) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(enabled = enabled, onClick = { open = true }) {
            Icon(
                painter = painterResource(R.drawable.more_vert_24px),
                contentDescription = "Commands for ${check.name.orEmpty().ifBlank { "check" }}"
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(Hc2NodeCommandName.RUN_CHECK, if (check.paused == true) Hc2NodeCommandName.RESUME else Hc2NodeCommandName.PAUSE)
                .forEach { cmd ->
                    DropdownMenuItem(
                        text = { Text(cmd.label()) },
                        onClick = { open = false; check.command(cmd)?.let(onPick) }
                    )
                }
        }
    }
}


// Konflikt-Nummer und die Seite, die behalten wird
@Composable
fun ResolveConflictDialog(
    onConfirm: (id: Long, keep: Hc2ConflictKeep) -> Unit,
    onDismiss: () -> Unit
) {
    var number by remember { mutableStateOf("") }
    var keep by remember { mutableStateOf(Hc2ConflictKeep.SERVER) }
    val id = number.toLongOrNull()

    ActionSheet(
        title = "Resolve conflict",
        onDismiss = onDismiss,
        confirmLabel = "Continue",
        confirmEnabled = id != null,
        onConfirm = { id?.let { onConfirm(it, keep) } }
    ) {
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
    // Gleiches Muster wie "Details": eine Gruppe, Kopfzeile darin, Inhalt beim Aufklappen
    item(key = "history") {
        ListGroup {
            ListRow(
                title = "Command history",
                showChevron = false,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(24.dp), strokeWidth = 2.dp)
                        } else if (open) {
                            IconButton(onClick = onRefresh) {
                                Icon(painter = painterResource(R.drawable.refresh_24px), contentDescription = "Reload commands")
                            }
                        }
                        Icon(
                            painter = painterResource(if (open) R.drawable.expand_less_24px else R.drawable.expand_more_24px),
                            contentDescription = if (open) "Collapse" else "Expand"
                        )
                    }
                },
                onClick = onToggle
            )
            if (open) {
                GroupDivider()
                if (loaded && commands.isEmpty()) GroupText("No commands sent yet.")
                commands.forEachIndexed { index, command ->
                    if (index > 0) GroupDivider()
                    NodeCommandRow(command, checks)
                }
            }
        }
    }
}

@Composable
private fun NodeCommandRow(command: Hc2Command, checks: List<Hc2Check>) {
    val times = listOfNotNull(
        command.createdAt?.let { "sent ${it.formatRelative()}" },
        command.doneAt?.let { "finished ${it.formatGermanTime()}" }
    ).joinToString(" · ")
    ListRow(
        title = command.name.commandLabel(),
        subtitle = listOfNotNull(command.args.argsText(checks), times.takeIf { it.isNotEmpty() }).joinToString("\n")
            .takeIf { it.isNotEmpty() },
        trailing = {
            when {
                command.doneAt == null -> StatusChip("Pending", colorResource(R.color.ipv64_orange))
                command.ok == true -> StatusChip("OK", colorResource(R.color.ipv64_green))
                command.ok == false -> StatusChip("Failed", colorResource(R.color.ipv64_red))
                else -> StatusChip("Done", colorResource(R.color.ipv64_gray))
            }
        }
    )
    command.output.outputText()?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        )
    }
}
