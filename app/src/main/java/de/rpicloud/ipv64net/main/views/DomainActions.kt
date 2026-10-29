package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.copyText
import de.rpicloud.ipv64net.main.views.common.InfoRow

/** Aktionen im Überlaufmenü der Domain-Detailansicht. */
enum class DomainAction { SETTINGS, DYNDNS, ROTATE_TOKEN, RESET, DELETE }

/**
 * Überlaufmenü (⋮) der Domain-Detailansicht: Einstellungen, manuelles DynDNS-Update, Token erneuern,
 * Zurücksetzen und Löschen. [enabled] = false solange die Domain noch nicht geladen ist.
 */
@Composable
fun DomainActionsMenu(enabled: Boolean, onAction: (DomainAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(painter = painterResource(id = R.drawable.more_vert_24px), contentDescription = "More actions")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            fun pick(action: DomainAction) {
                expanded = false
                onAction(action)
            }
            DropdownMenuItem(
                text = { Text("Settings") },
                leadingIcon = { Icon(painterResource(R.drawable.tune_24px), null) },
                onClick = { pick(DomainAction.SETTINGS) }
            )
            DropdownMenuItem(
                text = { Text("Manual DynDNS update") },
                leadingIcon = { Icon(painterResource(R.drawable.autorenew_24px), null) },
                onClick = { pick(DomainAction.DYNDNS) }
            )
            DropdownMenuItem(
                text = { Text("Rotate update token") },
                leadingIcon = { Icon(painterResource(R.drawable.key_24px), null) },
                onClick = { pick(DomainAction.ROTATE_TOKEN) }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Reset domain", color = colorResource(R.color.ipv64_red)) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.restart_alt_24px), null, tint = colorResource(R.color.ipv64_red))
                },
                onClick = { pick(DomainAction.RESET) }
            )
            DropdownMenuItem(
                text = { Text("Delete domain", color = colorResource(R.color.ipv64_red)) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.delete_24px), null, tint = colorResource(R.color.ipv64_red))
                },
                onClick = { pick(DomainAction.DELETE) }
            )
        }
    }
}

/**
 * Zeigt nach dem Erneuern des Update-Tokens die neue Update-URL (verdeckt, mit Anzeigen-Umschalter) und bietet
 * sensibles Kopieren an. Das Dialogfenster ist gegen Bildschirmaufnahmen geschützt. Die URL nie loggen.
 */
@Composable
fun RotatedTokenDialog(updateUrl: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painter = painterResource(id = R.drawable.key_24px), contentDescription = null) },
        title = { Text("New update URL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "The update token was rotated. The old update URL no longer works. " +
                            "Enter the new URL in your router or update script.",
                    style = MaterialTheme.typography.bodyMedium
                )
                InfoRow(label = "Update URL", value = updateUrl, sensitive = true, monospace = true)
                OutlinedButton(
                    onClick = { copyText(ctx, "Domain Update URL", updateUrl, sensitive = true) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Copy update URL")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)
    )
}
