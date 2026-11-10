package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.main.views.FormDropdown
import de.rpicloud.ipv64net.main.views.FormSwitchRow
import de.rpicloud.ipv64net.main.views.FormTextField
import de.rpicloud.ipv64net.main.views.common.ActionSheet
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.SectionHeader
import de.sr.ipv64.model.Hc2RemoteMgmt

private val remoteMgmtOptions = listOf(Hc2RemoteMgmt.FULL, Hc2RemoteMgmt.READ, Hc2RemoteMgmt.OFF)

// Code aus der Node: 8 Zeichen (Buchstaben/Ziffern), Anzeige/Versand als ABCD-EFGH
private fun String.normalizeLinkCode(): String = filter { it.isLetterOrDigit() }.uppercase().take(8)

private fun String.formatLinkCode(): String = if (length > 4) take(4) + "-" + drop(4) else this

// Der Bindestrich wird nur angezeigt; der Feldwert bleibt der rohe Code (Cursor-Positionen werden umgerechnet)
private object LinkCodeTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val shown = text.text.formatLinkCode()
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = if (offset <= 4) offset else offset + 1
            override fun transformedToOriginal(offset: Int): Int =
                (if (offset <= 4) offset else offset - 1).coerceIn(0, text.length)
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}

/**
 * Node per Code verknüpfen. Der Code steht auf der Node unter "ipv64 link". Der Dialog bleibt während der Anfrage
 * offen ([busy]) und zeigt Serverfehler inline ([error]).
 */
@Composable
fun LinkNodeDialog(
    busy: Boolean,
    error: String?,
    onConfirm: (code: String, location: String?, remoteMgmt: Hc2RemoteMgmt) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var mgmt by remember { mutableStateOf(Hc2RemoteMgmt.FULL) }
    val valid = code.length == 8

    ActionSheet(
        title = "Link node",
        onDismiss = onDismiss,
        dismissible = !busy,
        confirmLabel = "Link",
        confirmEnabled = valid && !busy,
        onConfirm = { onConfirm(code.formatLinkCode(), location.trim().takeIf { it.isNotEmpty() }, mgmt) }
    ) {
        Text(
            "Open \"ipv64 link\" on your Node64 to get an 8-character code. After linking, the node fetches its access token by itself.",
            style = MaterialTheme.typography.bodyMedium
        )
        SectionHeader("Node")
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.normalizeLinkCode() },
            label = { Text("Code") },
            placeholder = { Text("ABCD-EFGH") },
            singleLine = true,
            enabled = !busy,
            isError = error != null,
            visualTransformation = LinkCodeTransformation,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false
            ),
            modifier = Modifier.fillMaxWidth()
        )
        SectionHeader("Options")
        FormTextField(
            label = "Location (optional)",
            value = location,
            onValue = { location = it },
            enabled = !busy
        )
        FormDropdown(
            label = "Remote management",
            value = mgmt,
            options = remoteMgmtOptions,
            labelOf = { it.label() },
            onSelect = { mgmt = it },
            enabled = !busy
        )
        Text(
            mgmt.description(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * Name, Standort und Fernverwaltung ändern. Geliefert werden nur geänderte Werte (null = unverändert).
 */
@Composable
fun NodeEditDialog(
    name: String,
    location: String,
    remoteMgmt: Hc2RemoteMgmt,
    onSave: (name: String?, location: String?, remoteMgmt: Hc2RemoteMgmt?) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf(name) }
    var newLocation by remember { mutableStateOf(location) }
    var newMgmt by remember { mutableStateOf(remoteMgmt) }
    val changed = newName.trim() != name || newLocation.trim() != location || newMgmt != remoteMgmt

    ActionSheet(
        title = "Edit node",
        onDismiss = onDismiss,
        confirmLabel = "Save",
        confirmEnabled = changed && newName.isNotBlank(),
        onConfirm = {
            onSave(
                newName.trim().takeIf { it != name },
                newLocation.trim().takeIf { it != location },
                newMgmt.takeIf { it != remoteMgmt }
            )
        }
    ) {
        SectionHeader("Node")
        FormTextField(label = "Name", value = newName, onValue = { newName = it })
        FormTextField(label = "Location", value = newLocation, onValue = { newLocation = it })
        SectionHeader("Remote management")
        FormDropdown(
            label = "Remote management",
            value = newMgmt,
            options = remoteMgmtOptions,
            labelOf = { it.label() },
            onSelect = { newMgmt = it }
        )
        Text(
            newMgmt.description(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Node trennen: Bestätigung durch Eintippen des Namens plus Schalter "Checks weiterführen".
 * Der Aufrufer schließt den Dialog in beiden Callbacks.
 */
@Composable
fun NodeUnlinkDialog(
    nodeName: String,
    onConfirm: (keepChecks: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val phrase = nodeName.trim().ifEmpty { "unlink" }
    var typed by remember { mutableStateOf("") }
    var keepChecks by remember { mutableStateOf(true) }
    val canConfirm = typed.trim() == phrase

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painter = painterResource(R.drawable.link_off_24px), contentDescription = null) },
        title = { Text("Unlink node?") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "The access token of \"$nodeName\" is revoked. The node keeps running standalone but no longer syncs with ipv64.net. This cannot be undone."
                )
                FormSwitchRow(
                    label = "Keep checks as online checks",
                    checked = keepChecks,
                    onChange = { keepChecks = it },
                    supporting = if (keepChecks) "Checks with a public target continue as online checks."
                    else "The checks of this node are not continued and will be deleted."
                )
                Text("Type \"$phrase\" to confirm.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(keepChecks) }, enabled = canConfirm) {
                Text("Unlink", color = if (canConfirm) colorResource(R.color.ipv64_red) else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Auswahl hinter dem + der Node-Liste: per Code mit dem Account verknüpfen oder direkt (Adresse + API-Key) verbinden. */
@Composable
fun AddNodeDialog(canLink: Boolean, onLink: () -> Unit, onDirect: () -> Unit, onDismiss: () -> Unit) {
    ActionSheet(title = "Add node", onDismiss = onDismiss) {
        ListRow(
            title = "Link node",
            subtitle = if (canLink) "Link it to your account with the code from \"ipv64 link\"" else "Not available on your plan",
            icon = R.drawable.link_24px,
            enabled = canLink,
            showChevron = false,
            onClick = onLink
        )
        ListRow(
            title = "Connect directly",
            subtitle = "By IP or link and API key, without linking",
            icon = R.drawable.lan_24px,
            showChevron = false,
            onClick = onDirect
        )
    }
}
