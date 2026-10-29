package de.rpicloud.ipv64net.main.views.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.ipv64Request
import de.rpicloud.ipv64net.helper.loadMe
import de.rpicloud.ipv64net.main.views.FormDropdown
import de.rpicloud.ipv64net.main.views.FormTextField
import de.rpicloud.ipv64net.main.views.common.ConfirmDialog
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.SecretOnceDialog
import de.rpicloud.ipv64net.main.views.common.SectionHeader
import de.rpicloud.ipv64net.main.views.common.itemsOrEmpty
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64ApiException
import de.sr.ipv64.model.ApiKey
import de.sr.ipv64.model.AuthType
import de.sr.ipv64.model.Me

private const val MAX_KEY_NAME = 64

/**
 * API-Keys des Accounts: Liste (Name, Token-Hinweis, Erstellt, Ablauf, Aufrufe), Anlegen mit Gültigkeit und
 * einmaliger Anzeige des neuen Keys (SecretOnceDialog), Löschen mit Bestätigung. Den Key, mit dem diese App
 * angemeldet ist (me.auth.keyId), kann man nicht löschen.
 * Anfragen: sequentiell loadMe (AccountCache) + apiKeys.list (ApiKeyListCache), beide gecacht.
 */
@Composable
fun ApiKeysView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()

    var me by remember { mutableStateOf<Me?>(null) }
    var keys by remember { mutableStateOf<List<ApiKey>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    var showCreate by remember { mutableStateOf(false) }
    var keyToDelete by remember { mutableStateOf<ApiKey?>(null) }
    // Neuer Key: nur im Speicher (nicht rememberSaveable), nach "Done" sofort entfernt
    var newSecret by remember { mutableStateOf<String?>(null) }

    val currentKeyId = me?.auth?.takeIf { it.type == AuthType.API_KEY }?.keyId

    fun load(force: Boolean = false, pull: Boolean = false) {
        if (pull) refreshing = true
        req.launch(
            scope,
            spinner = if (pull) null else "Loading...",
            retry = { load(true) },
            onFinally = { refreshing = false }
        ) {
            me = loadMe(ctx, force)
            keys = ApiKeyListCache.get(ctx, force) { it.apiKeys.list() }
            loaded = true
        }
    }

    fun create(name: String, expiresIn: Int) {
        req.launch(scope, spinner = "Creating...") {
            val created = ipv64Request { IPv64Client.get(ctx).apiKeys.create(name = name, expiresIn = expiresIn) }
            val updated = keys + created.withoutSecret()
            keys = updated
            ApiKeyListCache.put(updated)
            showCreate = false
            val token = created.token
            if (token.isNullOrEmpty()) {
                req.message("API key created", "The server did not return the key. Delete it and try again.")
            } else {
                newSecret = token
            }
        }
    }

    fun delete(key: ApiKey) {
        val id = key.id ?: return
        if (id == currentKeyId) return
        req.launch(
            scope,
            spinner = "Deleting...",
            onError = { e ->
                if (e is IPv64ApiException && e.isConflict) {
                    req.message(
                        "Key in use",
                        e.detail ?: "This key is used by this app and cannot be deleted.",
                        R.drawable.warning_24px
                    )
                    true
                } else false
            }
        ) {
            ipv64Request { IPv64Client.get(ctx).apiKeys.delete(id.toString()) }
            val updated = keys.filter { it.id != id }
            keys = updated
            ApiKeyListCache.put(updated)
        }
    }

    LaunchedEffect(Unit) { load() }

    ScreenScaffold(
        title = Tabs.getLabel(Tab.api_keys),
        navController = navController,
        mainPadding = mainPadding,
        fab = {
            if (loaded) {
                FloatingActionButton(onClick = { showCreate = true }) {
                    Icon(painter = painterResource(id = R.drawable.add_24px), contentDescription = "New API key")
                }
            }
        }
    ) {
        RefreshableList(refreshing = refreshing, onRefresh = { load(force = true, pull = true) }) {
            if (loaded) {
                item(key = "info") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val auth = me?.auth
                            Text(
                                when {
                                    auth?.type == AuthType.API_KEY ->
                                        "This app uses the API key \"${auth.keyName ?: "#${auth.keyId ?: "?"}"}\"."

                                    auth?.type == AuthType.ACCOUNT_KEY -> "This app uses your account key."
                                    else -> "This app's key type is unknown."
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "A new key is shown only once after creating it. Keys give full access to your " +
                                        "account; delete keys you no longer use.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item(key = "header") { SectionHeader("API keys (${keys.size})") }
            }
            itemsOrEmpty(
                items = keys,
                loaded = loaded,
                emptyText = "No API keys yet.",
                key = { k -> "key_${k.id ?: k.hashCode()}" }
            ) { k ->
                ApiKeyItemView(
                    key = k,
                    isCurrent = k.id != null && k.id == currentKeyId,
                    onDelete = { keyToDelete = k }
                )
            }
        }
    }

    if (showCreate) {
        CreateApiKeyDialog(
            busy = req.isBusy,
            onCreate = { name, validity -> create(name, validity) },
            onDismiss = { showCreate = false }
        )
    }

    keyToDelete?.let { k ->
        ConfirmDialog(
            title = "Delete API key?",
            text = "\"${k.name ?: "Unnamed key"}\" stops working immediately. Apps and scripts using it lose access.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { keyToDelete = null; delete(k) },
            onDismiss = { keyToDelete = null }
        )
    }

    newSecret?.let { secret ->
        SecretOnceDialog(
            title = "New API key",
            secret = secret,
            warning = "Copy the key now and store it securely. It is shown only once and cannot be retrieved later.",
            onDone = { newSecret = null }
        )
    }

    RequestStateHost(req)
}

// Dialog zum Anlegen eines API-Keys: Name und Gültigkeit
@Composable
private fun CreateApiKeyDialog(busy: Boolean, onCreate: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var validityIdx by remember { mutableIntStateOf(0) }
    val nameOk = name.isNotBlank() && name.length <= MAX_KEY_NAME
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(painter = painterResource(id = R.drawable.key_24px), contentDescription = null) },
        title = { Text("New API key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FormTextField(
                    label = "Name",
                    value = name,
                    onValue = { name = it.take(MAX_KEY_NAME) },
                    placeholder = "e.g. Home Assistant",
                    supporting = "${name.length} / $MAX_KEY_NAME"
                )
                FormDropdown(
                    label = "Valid for",
                    value = validityIdx,
                    options = API_KEY_VALIDITIES.indices.toList(),
                    labelOf = { API_KEY_VALIDITIES[it].first },
                    onSelect = { validityIdx = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim(), API_KEY_VALIDITIES[validityIdx].second) },
                enabled = nameOk && !busy
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } }
    )
}
