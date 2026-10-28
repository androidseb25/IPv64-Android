package de.rpicloud.ipv64net.main.views

import android.text.TextUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.IPv64Error
import de.rpicloud.ipv64net.helper.parseChannelBool
import de.rpicloud.ipv64net.helper.toConfigValue
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.helper.views.RequestDialogs
import de.rpicloud.ipv64net.models.RequestTyp
import de.sr.ipv64.model.Hc2ChannelTestResult
import de.sr.ipv64.model.Hc2ChannelTypeField
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// ---------------------------------------------------------------------------------------------
// Feld-Hilfen
// ---------------------------------------------------------------------------------------------

// Feldart in Kleinbuchstaben; fehlende Art gilt als Text
internal val Hc2ChannelTypeField.kindKey: String
    get() = kind?.trim()?.lowercase()?.ifEmpty { null } ?: "text"

// Geheimes Feld (wird vom Server nie zurückgeliefert)
internal val Hc2ChannelTypeField.isSecretField: Boolean
    get() = secret == true || kindKey == "password"

// Konfigurationswert als Formulartext
internal fun JsonElement?.toChannelFieldText(): String = when (this) {
    null, is JsonNull -> ""
    is JsonPrimitive -> content
    else -> toString()
}

// Startwert eines Formularfelds: beim Bearbeiten aus der Konfiguration (Secrets leer), sonst Standardwert
internal fun initialChannelFieldValue(field: Hc2ChannelTypeField, config: JsonObject?, isEdit: Boolean): String {
    val key = field.key ?: return ""
    val isBool = field.kindKey == "bool"
    if (field.isSecretField) return if (isEdit) "" else field.default.orEmpty()
    if (isEdit) {
        val existing = config?.get(key)
        if (existing != null && existing !is JsonNull) {
            return if (isBool) parseChannelBool(existing.toChannelFieldText()).toString() else existing.toChannelFieldText()
        }
        return if (isBool) parseChannelBool(field.default).toString() else ""
    }
    return if (isBool) parseChannelBool(field.default).toString() else field.default.orEmpty()
}

// Text wie gesendet: Passwörter und mehrzeilige Felder unverändert, sonst ohne Leerraum am Rand
private fun Hc2ChannelTypeField.normalized(raw: String): String =
    if (kindKey == "password" || kindKey == "textarea" || isSecretField) raw else raw.trim()

/**
 * Baut die Kanal-Konfiguration aus den Formularwerten.
 * Neu: leere optionale Felder entfallen. Bearbeiten: Ausgangspunkt ist die bestehende Konfiguration (unbekannte Schlüssel
 * bleiben erhalten); geheime Felder werden nur gesendet, wenn eingetippt ([touched]); ein geleertes nicht geheimes Feld
 * wird als "" gesendet. Schlüssel in [keepKeys] bleiben unverändert (z. B. Gerätetoken des App-Kanals).
 */
internal fun buildChannelConfig(
    fields: List<Hc2ChannelTypeField>,
    values: Map<String, String>,
    touched: Set<String>,
    base: JsonObject?,
    isEdit: Boolean,
    keepKeys: Set<String> = emptySet()
): JsonObject {
    val map = LinkedHashMap<String, JsonElement>()
    if (isEdit) base?.forEach { (k, v) -> map[k] = v }
    fields.forEach { field ->
        val key = field.key?.takeIf { it.isNotBlank() } ?: return@forEach
        if (key in keepKeys) return@forEach
        val text = field.normalized(values[key].orEmpty())
        when {
            field.kindKey == "bool" -> map[key] = field.toConfigValue(text)
            field.isSecretField -> {
                if (text.isNotBlank() && (!isEdit || key in touched)) map[key] = field.toConfigValue(text)
                // Nicht eingetippt: weglassen, der Server behält das gespeicherte Secret
                else map.remove(key)
            }

            text.isBlank() -> {
                if (isEdit && base?.get(key).toChannelFieldText().isNotEmpty()) map[key] = JsonPrimitive("")
            }

            else -> map[key] = field.toConfigValue(text)
        }
    }
    return JsonObject(map)
}

// Validierungsfehler eines Felds (null = gültig)
internal fun channelFieldError(field: Hc2ChannelTypeField, value: String, required: Boolean): String? {
    if (field.kindKey == "bool") return null
    val text = value.trim()
    if (text.isEmpty()) return if (required) "Required" else null
    if (field.kindKey == "number" && text.toDoubleOrNull() == null) return "Must be a number"
    return null
}

// Titel und Text für das Ergebnis einer Testnachricht
internal fun channelTestResultMessage(result: Hc2ChannelTestResult): Pair<String, String> = when {
    result.sent == true -> "Test sent" to "The test notification was sent."
    result.queuedAtNode == true -> "Test queued" to "The test notification was queued at the node."
    else -> "Test not sent" to "The server did not send the test notification. Please check the channel settings."
}

// Fehlertext für Inline-Anzeigen (z. B. im Bestätigungsdialog)
internal fun IPv64Error.inlineText(): String {
    val request = requestType
    if (request != null) {
        return listOfNotNull(request.type.errorTitle, request.type.errorDescription)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
    return text.ifBlank { title }
}

// ---------------------------------------------------------------------------------------------
// Dialog-Zustand (Fehler, Hinweise) für die Kanal-Screens
// ---------------------------------------------------------------------------------------------

@Stable
class ChannelDialogState {
    var visible by mutableStateOf(false)
        private set
    var request by mutableStateOf<RequestTyp?>(null)
        private set
    var title by mutableStateOf("")
        private set
    var text by mutableStateOf("")
        private set
    var icon by mutableIntStateOf(R.drawable.error_24px)
        private set
    var confirmText by mutableIntStateOf(android.R.string.ok)
        private set
    var showDismiss by mutableStateOf(false)
        private set

    private var onConfirm: (() -> Unit)? = null
    private var onClose: (() -> Unit)? = null

    /** API-Fehler anzeigen; [retry] bietet "Retry" an, [onClose] läuft beim Schließen ohne Retry. */
    fun error(error: IPv64Error, retry: (() -> Unit)? = null, onClose: (() -> Unit)? = null) {
        if (error.requestType != null) {
            show(error.requestType, "", "", R.drawable.error_24px, android.R.string.ok, false, onClose, onClose)
        } else {
            show(
                null, error.title, TextUtils.htmlEncode(error.text), R.drawable.error_24px,
                if (retry != null) R.string.retry else android.R.string.ok,
                retry != null, retry ?: onClose, onClose
            )
        }
    }

    /** Hinweis mit Titel und Text (Text wird HTML-kodiert). */
    fun message(title: String, text: String, icon: Int = R.drawable.info_24px, onClose: (() -> Unit)? = null) {
        show(null, title, TextUtils.htmlEncode(text), icon, android.R.string.ok, false, onClose, onClose)
    }

    private fun show(
        request: RequestTyp?, title: String, text: String, icon: Int, confirmText: Int, showDismiss: Boolean,
        onConfirm: (() -> Unit)?, onClose: (() -> Unit)?
    ) {
        this.request = request
        this.title = title
        this.text = text
        this.icon = icon
        this.confirmText = confirmText
        this.showDismiss = showDismiss
        this.onConfirm = onConfirm
        this.onClose = onClose
        visible = true
    }

    fun confirm() {
        val action = onConfirm
        hide()
        action?.invoke()
    }

    fun dismiss() {
        val action = onClose
        hide()
        action?.invoke()
    }

    private fun hide() {
        visible = false
        onConfirm = null
        onClose = null
    }
}

@Composable
fun rememberChannelDialogState(): ChannelDialogState = remember { ChannelDialogState() }

@Composable
fun ChannelDialogHost(state: ChannelDialogState) {
    if (!state.visible) return
    val request = state.request
    if (request != null) {
        RequestDialogs(
            onDismissRequest = { state.dismiss() },
            onConfirmation = { state.confirm() },
            request = request
        )
    } else {
        ErrorDialog(
            onDismissRequest = { state.dismiss() },
            onConfirmation = { state.confirm() },
            dialogTitle = state.title,
            dialogText = state.text,
            dialogConfirmText = state.confirmText,
            icon = state.icon,
            showDismiss = state.showDismiss
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Formular-Bausteine
// ---------------------------------------------------------------------------------------------

// Karte mit Überschrift für einen Formularabschnitt
@Composable
fun ChannelFormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

// Schalter mit Titel und optionaler Beschreibung
@Composable
fun ChannelSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

// Sprache der Benachrichtigungen (de/en)
@Composable
fun ChannelLanguageSelector(lang: String, onChange: (String) -> Unit) {
    Column {
        Text("Language", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = lang == "en", onClick = { onChange("en") }, label = { Text("English") })
            FilterChip(selected = lang == "de", onClick = { onChange("de") }, label = { Text("Deutsch") })
        }
    }
}

/**
 * Eingabe für ein Konfigurationsfeld je nach Art: text/url/email/number/password → Textfeld mit passender Tastatur,
 * geheime Felder verdeckt mit Umschalter, textarea mehrzeilig, select als Dropdown, bool als Schalter.
 * [secretIsSet]: Secret ist gespeichert, leer lassen behält es.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelConfigField(
    field: Hc2ChannelTypeField,
    value: String,
    onValueChange: (String) -> Unit,
    required: Boolean,
    error: String?,
    secretIsSet: Boolean = false,
    enabled: Boolean = true
) {
    val label = (field.label?.takeIf { it.isNotBlank() } ?: field.key.orEmpty()) + if (required) " *" else ""
    when (field.kindKey) {
        "bool" -> ChannelSwitchRow(
            title = label,
            checked = parseChannelBool(value),
            onCheckedChange = { onValueChange(it.toString()) },
            enabled = enabled
        )

        "select" -> {
            var expanded by remember { mutableStateOf(false) }
            val options = field.options.filter { it.isNotEmpty() }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (enabled) expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {},
                    readOnly = true,
                    enabled = enabled,
                    label = { Text(label) },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = enabled)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    if (!required) {
                        DropdownMenuItem(
                            text = { Text("–") },
                            onClick = {
                                onValueChange("")
                                expanded = false
                            }
                        )
                    }
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onValueChange(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        else -> {
            val secret = field.isSecretField
            var revealed by rememberSaveable(field.key) { mutableStateOf(false) }
            val keyboardType = when {
                secret -> KeyboardType.Password
                field.kindKey == "url" -> KeyboardType.Uri
                field.kindKey == "email" -> KeyboardType.Email
                field.kindKey == "number" -> KeyboardType.Decimal
                else -> KeyboardType.Text
            }
            val multiLine = field.kindKey == "textarea" && !secret
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                label = { Text(label) },
                placeholder = if (secret && secretIsSet) {
                    { Text("Set – leave empty to keep") }
                } else null,
                singleLine = !multiLine,
                minLines = if (multiLine) 3 else 1,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    autoCorrectEnabled = !secret && field.kindKey == "textarea"
                ),
                visualTransformation = if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                trailingIcon = if (secret) {
                    {
                        IconButton(onClick = { revealed = !revealed }) {
                            Icon(
                                painter = painterResource(
                                    id = if (revealed) R.drawable.visibility_off_24px else R.drawable.visibility_24px
                                ),
                                contentDescription = if (revealed) "Hide" else "Show"
                            )
                        }
                    }
                } else null,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
