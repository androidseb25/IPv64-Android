package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.helper.rememberNavArg
import de.rpicloud.ipv64net.main.views.FormTextField
import de.rpicloud.ipv64net.main.views.common.ActionSheet
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.SectionHeader
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.rpicloud.ipv64net.models.User
import de.sr.ipv64.IPv64Exception

private const val HTTP_LOCAL_ONLY = "Enter a valid address. HTTP works with an IP address (e.g. 192.168.1.10 or a public IP) or a local name."

/**
 * Sheet für den direkten Zugang zu einer Node (Node64-API): Adresse + API-Key, Prüfung mit health() und status().
 * Bei Erfolg wird der Zugang gespeichert und [onSaved] mit der Node-ID gerufen.
 * Ohne [id] (neue direkte Node, ohne Cloud-Verknüpfung): ID und Name kommen aus status().
 */
@Composable
fun NodeLocalSheet(id: String?, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()

    // Bestehender Zugang (z. B. Node nicht mehr erreichbar): Adresse vorausfüllen, leeres Key-Feld behält den Key
    val stored = remember(id) { id?.let { LocalNodeStore.get(ctx, it) } }
    var urlInput by remember { mutableStateOf(stored?.first.orEmpty()) }
    var keyInput by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }

    // Fehler inline im Formular (Cloud-Fehlertexte passen hier nicht)
    fun handle(e: IPv64Exception): Boolean {
        val (title, text) = localNodeError(e)
        formError = "$title: $text"
        return true
    }

    fun connect() {
        val url = normalizeLocalNodeUrl(urlInput)
        if (url == null) {
            formError = if (urlInput.trim().startsWith("http://", ignoreCase = true)) HTTP_LOCAL_ONLY
            else "Enter a valid address, e.g. http://192.168.1.10:8080/api"
            return
        }
        val key = keyInput.trim().ifEmpty { stored?.second.orEmpty() }
        // OkHttp wirft bei Nicht-ASCII/Steuerzeichen im Header
        if (key.any { it !in '!'..'~' }) {
            formError = "The API key contains invalid characters."
            return
        }
        urlInput = url
        formError = null
        req.launch(scope, spinner = "Connecting...", onError = { handle(it) }) {
            val st = localNodeClient(url, key).node.also { it.health() }.status()
            val owner = User.current?.id?.toString().orEmpty()
            if (id == null) {
                // Neue direkte Node: unter ihrer eigenen ID speichern (vorhandener Zugang wird aktualisiert)
                val newId = st.node?.id?.takeIf { it.isNotBlank() }
                if (newId == null) {
                    formError = "The node did not report its ID. Update Node64 and try again."
                    return@launch
                }
                LocalNodeStore.save(ctx, newId, url, key, owner, st.node?.name?.takeIf { it.isNotBlank() })
                LocalNodeStore.saveModules(ctx, newId, st.modules)
                keyInput = ""
                onSaved(newId)
                return@launch
            }
            val nid: String = id
            LocalNodeStore.save(ctx, nid, url, key, owner)
            LocalNodeStore.saveModules(ctx, nid, st.modules)
            keyInput = ""
            // Andere Node unter dieser Adresse: nur warnen
            val remoteId = st.node?.id
            if (remoteId != null && remoteId != nid) {
                req.message(
                    "Different node?",
                    "The node at this address reports the ID $remoteId instead of $nid. The connection was saved anyway — check the address if this is unexpected.",
                    R.drawable.warning_24px,
                    onClose = { onSaved(nid) }
                )
            } else {
                onSaved(nid)
            }
        }
    }

    val newNode = id == null
    ActionSheet(
        title = if (newNode) "Connect directly" else "Direct access",
        subtitle = (if (newNode) "Add a node without linking it to your account: connect to it" else "Connect directly to this node") +
            " by its local IP or by its link (e.g. https://node.example.com/api). Create an API key in the node's web interface on the API tab.",
        onDismiss = onDismiss,
        confirmLabel = "Connect",
        confirmEnabled = !req.isBusy && urlInput.isNotBlank() && (keyInput.isNotBlank() || stored != null),
        onConfirm = { connect() },
        dismissible = !req.isBusy
    ) {
        LocalNodeFields(
            url = urlInput,
            key = keyInput,
            keepsKey = stored != null,
            error = formError,
            busy = req.isBusy,
            onUrl = { urlInput = it; formError = null },
            onKey = { keyInput = it; formError = null }
        )
    }

    RequestStateHost(req)
}

@Composable
private fun LocalNodeFields(
    url: String,
    key: String,
    keepsKey: Boolean,
    error: String?,
    busy: Boolean,
    onUrl: (String) -> Unit,
    onKey: (String) -> Unit
) {
    val normalized = normalizeLocalNodeUrl(url)
    val insecure = normalized?.startsWith("http://") == true
    val overInternet = normalized?.let { isPublicHttp(it) } == true
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FormTextField(
            label = "Address",
            value = url,
            onValue = onUrl,
            placeholder = "http://192.168.1.10:8080/api",
            supporting = "Local or public IP (http, port 8080 added) or link (https); /api is added if missing.",
            keyboardType = KeyboardType.Uri,
            enabled = !busy
        )
        FormTextField(
            label = "API key",
            value = key,
            onValue = onKey,
            placeholder = "node64_…",
            visualTransformation = PasswordVisualTransformation(),
            secret = true,
            supporting = if (keepsKey) "Leave empty to keep the saved key." else null,
            enabled = !busy
        )
        if (insecure) {
            Text(
                if (overInternet) "The API key is sent unencrypted over the internet. Use https:// or a VPN if you can."
                else "The key is sent unencrypted on your local network.",
                style = MaterialTheme.typography.bodySmall,
                color = if (overInternet) MaterialTheme.colorScheme.error else colorResource(R.color.ipv64_orange)
            )
        }
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
