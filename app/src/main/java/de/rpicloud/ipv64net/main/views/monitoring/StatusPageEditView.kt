package de.rpicloud.ipv64net.main.views.monitoring

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.copyText
import de.rpicloud.ipv64net.helper.loadHc2Checks
import de.rpicloud.ipv64net.helper.openUrl
import de.rpicloud.ipv64net.helper.rememberNavArg
import de.rpicloud.ipv64net.helper.shareText
import de.rpicloud.ipv64net.main.views.common.FormSection
import de.rpicloud.ipv64net.main.views.FormDropdown
import de.rpicloud.ipv64net.main.views.FormSwitchRow
import de.rpicloud.ipv64net.main.views.FormTextField
import de.rpicloud.ipv64net.main.views.common.ConfirmDialog
import de.rpicloud.ipv64net.main.views.common.Hc2CheckPickerDialog
import de.rpicloud.ipv64net.main.views.common.InfoRow
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.SecureWindowEffect
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.main.views.common.unknownCheckLabel
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64ApiException
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.CreateStatusPageRequest
import de.sr.ipv64.model.StatusPage
import de.sr.ipv64.model.UpdateStatusPageRequest

private val SLUG_REGEX = Regex("^[A-Za-z0-9]{3,128}$")
private val STATUS_PAGE_FIELDS = setOf("name", "description", "slug", "password", "design", "autorefresh", "healthchecks")

// Passwort ist dreiwertig: unverändert lassen / neu setzen / entfernen (leerer String)
private enum class PasswordMode { KEEP, SET, REMOVE }

// Statusseite anlegen/bearbeiten/löschen (Schlüssel SELECTED_STATUS_PAGE_ID fehlt = neu)
@Composable
fun StatusPageEditView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()

    val id = navController.rememberNavArg<String>(NavKeys.SELECTED_STATUS_PAGE_ID)
    val isEdit = id != null

    var original by remember { mutableStateOf<StatusPage?>(null) }
    // Formularfelder überleben eine Rotation; "initialized" verhindert, dass das erneute Laden Eingaben überschreibt
    var initialized by rememberSaveable { mutableStateOf(!isEdit) }
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var slug by rememberSaveable { mutableStateOf("") }
    var design by rememberSaveable { mutableIntStateOf(1) }
    var autorefresh by rememberSaveable { mutableStateOf(true) }
    var passwordMode by rememberSaveable { mutableStateOf(PasswordMode.KEEP) }
    // Das Passwort wird nie angezeigt, nie gespeichert (bewusst kein rememberSaveable) und nie geloggt
    var password by remember { mutableStateOf("") }
    var tokens by rememberSaveable { mutableStateOf<List<String>>(ArrayList()) }
    var checkNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showPicker by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val errors = remember { mutableStateMapOf<String, String>() }

    // Namen der Checks (gecachte Liste); Fehler sind nicht kritisch, Tokens bleiben als "Unknown check" sichtbar
    suspend fun refreshNames() {
        try {
            checkNames = loadHc2Checks(ctx).mapNotNull { c -> c.token?.let { it to c.name.orEmpty() } }.toMap()
        } catch (_: IPv64Exception) {
        }
    }

    fun applyFrom(p: StatusPage) {
        original = p
        if (initialized) return
        initialized = true
        name = p.name.orEmpty()
        description = p.description.orEmpty()
        slug = p.slug.orEmpty()
        design = p.design?.coerceIn(1, 4) ?: 1
        autorefresh = p.autorefresh ?: false
        tokens = ArrayList(p.healthchecks)
        passwordMode = PasswordMode.KEEP
        password = ""
    }

    fun load() {
        val pid = id ?: return
        req.launch(scope, retry = { load() }) {
            val p = StatusPageCache.peek(ctx)?.firstOrNull { it.id?.toString() == pid }
                ?: monitoringCall(ctx) { it.statusPages.get(pid) }
            applyFrom(p)
            if (tokens.isNotEmpty()) refreshNames()
        }
    }

    LaunchedEffect(Unit) { load() }

    fun validate(): Boolean {
        errors.clear()
        if (name.isBlank()) errors["name"] = "Please enter a title."
        val s = slug.trim()
        if (s.isNotEmpty() && !SLUG_REGEX.matches(s)) errors["slug"] = "3 to 128 characters: letters and digits only."
        if (passwordMode == PasswordMode.SET && password.isBlank()) errors["password"] = "Please enter a password."
        return errors.isEmpty()
    }

    fun handleError(e: IPv64Exception): Boolean {
        if (e is IPv64ApiException) {
            val field = e.field
            val info = "${e.code.orEmpty()} ${e.detail.orEmpty()}".lowercase()
            // Konflikt nur dem Adressfeld zuordnen, wenn Feld/Code darauf hinweisen; sonst Servertext im Dialog
            if (e.isConflict && (field == "slug" || (field == null && (info.contains("slug") || info.contains("address"))))) {
                errors["slug"] = e.detail?.takeIf { it.isNotBlank() } ?: "This address is already taken."
                return true
            }
            if ((e.isValidationError || e.isConflict) && field != null && field in STATUS_PAGE_FIELDS) {
                errors[field] = e.detail ?: "Invalid value."
                return true
            }
        }
        return false
    }

    fun save() {
        if (isEdit && original == null) return  // Seite noch nicht geladen
        if (!validate()) return
        val orig = original
        val pwd = when (passwordMode) {
            PasswordMode.KEEP -> null
            PasswordMode.SET -> password
            PasswordMode.REMOVE -> ""
        }
        req.launch(scope, spinner = "Saving...", onError = ::handleError) {
            if (orig == null) {
                monitoringCall(ctx) {
                    it.statusPages.create(
                        CreateStatusPageRequest(
                            name = name.trim(),
                            description = description.trim().ifBlank { null },
                            slug = slug.trim().ifBlank { null },
                            password = pwd?.ifBlank { null },
                            design = design,
                            autorefresh = autorefresh,
                            healthchecks = tokens
                        )
                    )
                }
            } else {
                // Nur geänderte Felder senden (null = unverändert); leere Beschreibung "" löscht sie
                val update = UpdateStatusPageRequest(
                    name = name.trim().takeIf { it != orig.name },
                    description = description.trim().takeIf { it != orig.description.orEmpty() },
                    slug = slug.trim().takeIf { it.isNotEmpty() && it != orig.slug },
                    password = pwd,
                    design = design.takeIf { it != orig.design },
                    autorefresh = autorefresh.takeIf { it != (orig.autorefresh ?: false) },
                    healthchecks = tokens.takeIf { it != orig.healthchecks }
                )
                val oid = id
                if (oid != null && update != UpdateStatusPageRequest()) {
                    monitoringCall(ctx) { it.statusPages.update(oid, update) }
                }
            }
            password = ""
            StatusPageCache.invalidate()
            navController.popBackStack()
        }
    }

    fun delete() {
        val pid = id ?: return
        req.launch(scope, spinner = "Deleting...") {
            monitoringCall(ctx) { it.statusPages.delete(pid) }
            StatusPageCache.invalidate()
            navController.popBackStack()
        }
    }

    fun moveToken(index: Int, delta: Int) {
        val target = index + delta
        if (target !in tokens.indices) return
        tokens = ArrayList(tokens).also {
            val t = it.removeAt(index)
            it.add(target, t)
        }
    }

    if (passwordMode == PasswordMode.SET) SecureWindowEffect()

    ScreenScaffold(Tabs.getLabel(Tab.status_page_edit), navController, mainPadding) {
        if (!initialized) return@ScreenScaffold
        val protectedNow = original?.passwordProtected == true
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val url = original?.url?.takeIf { it.isNotBlank() }
            if (url != null) {
                FormSection("Public URL") {
                    InfoRow("Public URL", url, copyable = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { openUrl(ctx, url) }) { Text("Open") }
                        OutlinedButton(onClick = { copyText(ctx, "Status page URL", url) }) { Text("Copy") }
                        OutlinedButton(onClick = { shareText(ctx, url, name.ifBlank { null }) }) { Text("Share") }
                    }
                }
            }

            FormSection("General") {
                FormTextField("Title", name, { name = it; errors.remove("name") }, supporting = errors["name"])
                FormTextField(
                    "Description", description, { description = it; errors.remove("description") },
                    singleLine = false, minLines = 2, supporting = errors["description"] ?: "Optional."
                )
                FormTextField(
                    "Address", slug, { slug = it.filter { c -> c.isLetterOrDigit() }; errors.remove("slug") },
                    supporting = errors["slug"] ?: if (isEdit) "Changing the address changes the public URL."
                    else "Optional; letters and digits (3 to 128). Leave empty for a random address."
                )
                FormDropdown(
                    label = "Design", value = design, options = listOf(1, 2, 3, 4),
                    labelOf = { "Design $it" }, onSelect = { design = it }
                )
                FormSwitchRow(
                    "Auto refresh", autorefresh, { autorefresh = it },
                    supporting = "The page reloads itself regularly."
                )
            }

            FormSection("Access") {
                Text(
                    if (protectedNow) "This page is protected by a password." else "This page is public.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = passwordMode == PasswordMode.KEEP,
                        onClick = { passwordMode = PasswordMode.KEEP; password = ""; errors.remove("password") },
                        label = { Text(if (protectedNow) "Keep password" else "Public") }
                    )
                    FilterChip(
                        selected = passwordMode == PasswordMode.SET,
                        onClick = { passwordMode = PasswordMode.SET },
                        label = { Text(if (protectedNow) "Change password" else "Set password") }
                    )
                    if (protectedNow) {
                        FilterChip(
                            selected = passwordMode == PasswordMode.REMOVE,
                            onClick = { passwordMode = PasswordMode.REMOVE; password = ""; errors.remove("password") },
                            label = { Text("Remove password") }
                        )
                    }
                }
                if (passwordMode == PasswordMode.SET) {
                    FormTextField(
                        "New password", password, { password = it; errors.remove("password") },
                        secret = true, visualTransformation = PasswordVisualTransformation(),
                        supporting = errors["password"]
                    )
                }
            }

            FormSection("Healthchecks") {
                if (tokens.isEmpty()) {
                    Text("No checks selected.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    tokens.forEachIndexed { index, token ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                checkNames[token]?.takeIf { it.isNotBlank() } ?: unknownCheckLabel(token),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { moveToken(index, -1) }, enabled = index > 0) {
                                Icon(painterResource(R.drawable.arrow_upward_24px), contentDescription = "Move up")
                            }
                            IconButton(onClick = { moveToken(index, 1) }, enabled = index < tokens.lastIndex) {
                                Icon(painterResource(R.drawable.arrow_downward_24px), contentDescription = "Move down")
                            }
                            IconButton(onClick = { tokens = ArrayList(tokens.filterIndexed { i, _ -> i != index }) }) {
                                Icon(painterResource(R.drawable.close_24px), contentDescription = "Remove")
                            }
                        }
                    }
                }
                errors["healthchecks"]?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (tokens.isEmpty()) "Add checks" else "Add or remove checks")
                }
            }

            Button(onClick = { save() }, enabled = !req.isBusy, modifier = Modifier.fillMaxWidth()) {
                Text(if (isEdit) "Save" else "Create")
            }
            if (isEdit) {
                OutlinedButton(
                    onClick = { showDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Delete") }
            }
        }
    }

    if (showPicker) {
        Hc2CheckPickerDialog(
            initial = tokens,
            multi = true,
            allowManualToken = true,
            onConfirm = {
                // Unbekannte Tokens bleiben erhalten; Reihenfolge: bisherige zuerst, neue danach
                tokens = ArrayList(it.distinct())
                showPicker = false
                req.launch(scope, spinner = null) { refreshNames() }
            },
            onDismiss = { showPicker = false }
        )
    }
    if (showDelete) {
        ConfirmDialog(
            title = "Delete status page?",
            text = "\"${name.ifBlank { "This page" }}\" and its public URL will be removed.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { showDelete = false; delete() },
            onDismiss = { showDelete = false }
        )
    }
    RequestStateHost(req)
}
