package de.rpicloud.ipv64net.main.views.monitoring

import android.graphics.BitmapFactory
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.formatBytes
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.openUrl
import de.rpicloud.ipv64net.helper.rememberNavArg
import de.rpicloud.ipv64net.main.views.common.FormSection
import de.rpicloud.ipv64net.main.views.FormTextField
import de.rpicloud.ipv64net.main.views.common.ConfirmDialog
import de.rpicloud.ipv64net.main.views.common.NotAvailableView
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64ApiException
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.CreateOfflinePageRequest
import de.sr.ipv64.model.OfflinePage
import de.sr.ipv64.model.UpdateOfflinePageRequest
import kotlinx.coroutines.launch

// Felder, für die der Server Validierungsfehler (422, field) liefern kann
private val FORM_FIELDS = setOf("name", "domain", "email", "phone", "alternative_url", "text")

// Telefonnummer: Leerzeichen, Bindestriche, Klammern und Schrägstriche entfernen (Server erwartet Ziffern, optional +)
private fun normalizePhone(s: String): String = s.filterNot { it == ' ' || it == '-' || it == '(' || it == ')' || it == '/' }

/**
 * Offline-Seite anlegen oder bearbeiten (NavKeys.SELECTED_OFFLINE_PAGE_ID; fehlt = anlegen), mit Logo-Upload,
 * Vorschau und Löschen (gesperrt, solange eine Failover-Policy die Seite nutzt).
 * Anfragen: beim Bearbeiten nur ein GET, wenn die Seite nicht im Cache liegt; beim Speichern POST/PATCH (nur
 * geänderte Felder) und ggf. ein Upload des Logos. Das Logo wird erst beim Speichern hochgeladen.
 */
@Composable
fun OfflinePageEditView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    val argId = navController.rememberNavArg<String>(NavKeys.SELECTED_OFFLINE_PAGE_ID)

    var page by remember { mutableStateOf(argId?.let { i -> OfflinePageCache.peek(ctx)?.firstOrNull { it.id == i } }) }
    var pageId by remember { mutableStateOf(argId) }

    var name by remember { mutableStateOf(page?.name.orEmpty()) }
    var domain by remember { mutableStateOf(page?.domain.orEmpty()) }
    var email by remember { mutableStateOf(page?.email.orEmpty()) }
    var phone by remember { mutableStateOf(page?.phone.orEmpty()) }
    var altUrl by remember { mutableStateOf(page?.alternativeUrl.orEmpty()) }
    var text by remember { mutableStateOf(htmlToPlainText(page?.text)) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    var pendingLogo by remember { mutableStateOf<LogoImage?>(null) }
    var logoError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRemoveLogo by remember { mutableStateOf(false) }

    fun fill(p: OfflinePage) {
        page = p
        name = p.name.orEmpty()
        domain = p.domain.orEmpty()
        email = p.email.orEmpty()
        phone = p.phone.orEmpty()
        altUrl = p.alternativeUrl.orEmpty()
        text = htmlToPlainText(p.text)
    }

    fun loadExisting() {
        val pid = argId ?: return
        req.launch(scope, planGate = true, retry = { loadExisting() }) {
            val p = ipv64Retrying { IPv64Client.get(ctx).offlinePages.get(pid) }
            OfflinePageCache.upsert(p) { it.id == p.id }
            fill(p)
        }
    }

    LaunchedEffect(Unit) {
        if (argId != null && page == null) loadExisting()
    }

    // PickVisualMedia braucht keine Berechtigung; Bild wird über den ContentResolver gelesen und geprüft
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = readLogo(ctx, uri)
                if (result.logo != null) {
                    pendingLogo = result.logo
                    logoError = null
                } else {
                    logoError = result.error
                }
            }
        }
    }

    val loaded = pageId == null || page != null
    val existing = page
    val inUse = existing?.inUseByPolicy != null

    fun validate(): Map<String, String> {
        val e = mutableMapOf<String, String>()
        if (name.isBlank()) e["name"] = "Enter a heading."
        if (domain.isBlank()) e["domain"] = "Enter the domain the page appears under."
        if (email.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) e["email"] = "Enter a valid e-mail address."
        if (phone.isNotBlank() && !Regex("^\\+?\\d{3,20}$").matches(normalizePhone(phone.trim()))) {
            e["phone"] = "Digits with an optional leading +."
        }
        val url = altUrl.trim()
        if (url.isNotEmpty() && !(url.startsWith("http://") || url.startsWith("https://"))) {
            e["alternative_url"] = "Start with http:// or https://."
        }
        return e
    }

    // 422 mit bekanntem Feld → Feldfehler, 413/415 beim Logo → Logo-Fehler
    fun handleError(e: IPv64Exception): Boolean {
        if (e !is IPv64ApiException) return false
        if (e.status == 413 || e.status == 415 || (e.isValidationError && e.field == "icon")) {
            logoError = e.detail ?: "The server rejected the logo."
            return true
        }
        val field = e.field
        if (e.isValidationError && field != null && field in FORM_FIELDS) {
            errors = errors + (field to (e.detail ?: "Invalid value."))
            return true
        }
        return false
    }

    fun save() {
        val found = validate()
        errors = found
        if (found.isNotEmpty()) return
        val phoneValue = normalizePhone(phone.trim())
        val logo = pendingLogo
        req.launch(scope, spinner = "Saving...", onError = ::handleError) {
            val api = IPv64Client.get(ctx)
            var current = page
            val id = pageId
            if (id == null) {
                val created = ipv64Retrying {
                    api.offlinePages.create(
                        CreateOfflinePageRequest(
                            name = name.trim(),
                            domain = domain.trim(),
                            email = email.trim().ifEmpty { null },
                            phone = phoneValue.ifEmpty { null },
                            alternativeUrl = altUrl.trim().ifEmpty { null },
                            text = text.trim().ifEmpty { null }
                        )
                    )
                }
                current = created
                page = created
                pageId = created.id
                OfflinePageCache.upsert(created) { it.id == created.id }
            } else if (current != null) {
                val request = UpdateOfflinePageRequest(
                    name = name.trim().takeIf { it != current!!.name.orEmpty() },
                    domain = domain.trim().takeIf { it != current!!.domain.orEmpty() },
                    email = email.trim().takeIf { it != current!!.email.orEmpty() },
                    phone = phoneValue.takeIf { it != current!!.phone.orEmpty() },
                    alternativeUrl = altUrl.trim().takeIf { it != current!!.alternativeUrl.orEmpty() },
                    // Text nur senden, wenn er geändert wurde (der Server liefert HTML, Eingaben werden dort maskiert)
                    text = text.trim().takeIf { it != htmlToPlainText(current!!.text) }
                )
                if (request != UpdateOfflinePageRequest()) {
                    val updated = ipv64Retrying { api.offlinePages.update(id, request) }
                    current = updated
                    page = updated
                    OfflinePageCache.upsert(updated) { it.id == updated.id }
                }
            }
            val targetId = pageId
            if (logo != null && targetId != null) {
                // Falls der Upload scheitert, ist die Seite bereits angelegt/gespeichert (pageId gesetzt → kein Duplikat)
                val withIcon = ipv64Retrying { api.offlinePages.uploadIcon(targetId, logo.bytes, logo.fileName, logo.mime) }
                page = withIcon
                pendingLogo = null
                OfflinePageCache.upsert(withIcon) { it.id == withIcon.id }
            }
            navController.popBackStack()
        }
    }

    ScreenScaffold(
        title = if (argId == null && pageId == null) "New offline page" else Tabs.getLabel(Tab.offline_page_edit),
        navController = navController,
        mainPadding = mainPadding,
        actions = {
            val preview = existing?.previewUrl
            if (!preview.isNullOrBlank()) {
                IconButton(onClick = { openUrl(ctx, preview) }) {
                    Icon(painter = painterResource(R.drawable.open_in_new_24px), contentDescription = "Preview")
                }
            }
            if (pageId != null) {
                // Löschen ist gesperrt, solange eine Failover-Policy die Seite nutzt
                IconButton(enabled = !inUse && !req.isBusy, onClick = { confirmDelete = true }) {
                    Icon(painter = painterResource(R.drawable.delete_24px), contentDescription = "Delete")
                }
            }
        }
    ) {
        val unavailable = req.notAvailable
        when {
            unavailable != null -> NotAvailableView("Offline pages", unavailable)
            !loaded -> Box(modifier = Modifier)
            else -> Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (inUse) {
                    Text(
                        "This page is used by a failover policy and cannot be deleted. Remove the rule first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FormSection("Content") {
                    FormTextField("Heading", name, { name = it; errors = errors - "name" }, supporting = errors["name"])
                    FormTextField(
                        "Domain", domain, { domain = it; errors = errors - "domain" },
                        supporting = errors["domain"] ?: "The domain the page appears under, e.g. www.example.com.",
                        keyboardType = KeyboardType.Uri
                    )
                    FormTextField(
                        "Text", text, { text = it; errors = errors - "text" },
                        supporting = errors["text"] ?: "Line breaks are kept, HTML is shown as plain text.",
                        singleLine = false, minLines = 3
                    )
                }
                FormSection("Contact (optional)") {
                    FormTextField("E-mail", email, { email = it; errors = errors - "email" },
                        supporting = errors["email"], keyboardType = KeyboardType.Email)
                    FormTextField("Phone", phone, { phone = it; errors = errors - "phone" },
                        supporting = errors["phone"] ?: "Digits with an optional leading +.", keyboardType = KeyboardType.Phone)
                    FormTextField("Alternative address", altUrl, { altUrl = it; errors = errors - "alternative_url" },
                        supporting = errors["alternative_url"] ?: "Shown as a link, e.g. https://status.example.com.",
                        keyboardType = KeyboardType.Uri)
                }
                FormSection("Logo") {
                    val staged = pendingLogo
                    if (staged != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LogoThumbnail(staged)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("New logo", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${staged.fileName} · ${staged.bytes.size.toLong().formatBytes()} · uploaded when you save",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { pendingLogo = null }) {
                                Icon(painter = painterResource(R.drawable.close_24px), contentDescription = "Discard new logo")
                            }
                        }
                    } else {
                        Text(
                            if (existing?.iconUrl.isNullOrBlank()) "No logo set." else "A logo is set.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    logoError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Text(
                        "PNG, JPEG or WebP, at most 1 MB and 1024 x 1024 pixels.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(painter = painterResource(R.drawable.image_24px), contentDescription = null)
                            Text("Choose logo", modifier = Modifier.padding(start = 6.dp))
                        }
                        if (pageId != null && !existing?.iconUrl.isNullOrBlank()) {
                            OutlinedButton(onClick = { confirmRemoveLogo = true }, enabled = !req.isBusy) { Text("Remove logo") }
                        }
                    }
                }
                Button(onClick = { save() }, enabled = !req.isBusy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (pageId == null) "Create" else "Save")
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete offline page?",
            text = "\"${existing?.name.orEmpty()}\" will be deleted. This cannot be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                val pid = pageId
                if (pid != null) {
                    req.launch(scope, spinner = "Deleting...") {
                        ipv64Retrying { IPv64Client.get(ctx).offlinePages.delete(pid) }
                        OfflinePageCache.removeWhere { it.id == pid }
                        navController.popBackStack()
                    }
                }
            },
            onDismiss = { confirmDelete = false }
        )
    }
    if (confirmRemoveLogo) {
        ConfirmDialog(
            title = "Remove logo?",
            text = "The logo is deleted from the offline page.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                confirmRemoveLogo = false
                val pid = pageId
                if (pid != null) {
                    req.launch(scope, spinner = "Removing...") {
                        ipv64Retrying { IPv64Client.get(ctx).offlinePages.deleteIcon(pid) }
                        page?.let {
                            val updated = it.copy(iconUrl = null)
                            page = updated
                            OfflinePageCache.upsert(updated) { p -> p.id == updated.id }
                        }
                    }
                }
            },
            onDismiss = { confirmRemoveLogo = false }
        )
    }
    RequestStateHost(req)
}

// Kleine Vorschau des gewählten Logos (verkleinert dekodiert)
@Composable
private fun LogoThumbnail(logo: LogoImage) {
    val bitmap = remember(logo) {
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        BitmapFactory.decodeByteArray(logo.bytes, 0, logo.bytes.size, options)?.asImageBitmap()
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = "Selected logo", modifier = Modifier.size(56.dp))
    }
}
