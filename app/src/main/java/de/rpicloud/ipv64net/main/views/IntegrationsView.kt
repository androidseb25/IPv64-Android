package de.rpicloud.ipv64net.main.views

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.IPv64Context
import de.rpicloud.ipv64net.helper.IPv64Error
import de.rpicloud.ipv64net.helper.SecNtfyHelper
import de.rpicloud.ipv64net.helper.ipv64Request
import de.rpicloud.ipv64net.helper.toError
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.helper.views.RequestDialogs
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.models.RequestTyp
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.CreateIntegrationRequest
import de.sr.ipv64.model.Integration
import de.sr.ipv64.model.IntegrationType
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("ContextCastToActivity")
@Composable
fun IntegrationsView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var showLoadingDialog by remember { mutableStateOf(false) }
    var loadingText by remember { mutableStateOf("Loading...") }
    var showDialog by remember { mutableStateOf(false) }
    var errorDialogTitle by remember { mutableStateOf("") }
    var errorDialogText by remember { mutableStateOf("") }
    var errorDialogButtonText by remember { mutableIntStateOf(android.R.string.ok) }
    // Nur Ladefehler bieten "Retry" an; Fehler beim Hinzufügen schließen den Dialog nur
    var errorRetryLoad by remember { mutableStateOf(true) }

    var showRequestDialog by remember { mutableStateOf(false) }
    var requestType by remember { mutableStateOf(RequestTyp.UnAuthorized) }

    // Liste ohne Secrets (options werden nach der Geräteerkennung verworfen)
    val integrations = remember { mutableStateListOf<Integration>() }
    var isLoaded by remember { mutableStateOf(false) }
    // Dieses Gerät ist bereits als "IPv64 App"-Integration eingetragen
    var thisDeviceRegistered by remember { mutableStateOf(false) }
    var thisDeviceId by remember { mutableStateOf<Long?>(null) }

    var showAddDialog by remember { mutableStateOf(false) }
    var deviceName by remember { mutableStateOf("") }

    fun showError(error: IPv64Error, retryLoad: Boolean) {
        if (error.requestType != null) {
            requestType = error.requestType
            showRequestDialog = true
        } else {
            errorRetryLoad = retryLoad
            errorDialogTitle = error.title
            errorDialogText = error.text
            errorDialogButtonText = if (retryLoad) R.string.retry else android.R.string.ok
            showDialog = true
        }
    }

    fun getIntegrations() {
        loadingText = "Loading..."
        showLoadingDialog = true
        scope.launch {
            try {
                // Mit bekanntem Ntfy-Token die Secrets anfordern, um dieses Gerät in der Liste zu erkennen
                val ownToken = SecNtfyHelper.storedNtfyToken(ctx)
                val list = ipv64Request {
                    IPv64Client.get(ctx).integrations.list(includeSecrets = if (ownToken != null) true else null)
                }
                // Nur APP-Einträge vergleichen; sonst die zuletzt angelegte/erkannte ID beibehalten,
                // falls der Server den Token nicht unter einem bekannten Schlüssel liefert
                val own = ownToken?.let { token ->
                    list.firstOrNull { it.type == IntegrationType.APP && it.deviceToken() == token }
                } ?: thisDeviceId?.let { id ->
                    list.firstOrNull { it.type == IntegrationType.APP && it.id == id }
                }
                thisDeviceRegistered = own != null
                thisDeviceId = own?.id
                integrations.clear()
                // Secrets nicht im UI-State halten
                integrations.addAll(list.map { it.copy(options = null) })
                isLoaded = true
            } catch (e: IPv64Exception) {
                println(e.message)
                showError(e.toError(), retryLoad = true)
            } finally {
                showLoadingDialog = false
            }
        }
    }

    fun addThisDevice(name: String) {
        loadingText = "Adding device..."
        showLoadingDialog = true
        scope.launch {
            var reload = false
            try {
                val token = SecNtfyHelper.ntfyToken(ctx)
                if (token == null) {
                    errorRetryLoad = false
                    errorDialogTitle = "Device token unavailable"
                    errorDialogText = "This device could not be registered for push notifications " +
                            "(Firebase or SecNtfy is not reachable). Please check your internet connection and try again later."
                    errorDialogButtonText = android.R.string.ok
                    showDialog = true
                    return@launch
                }
                val ipv64 = IPv64Client.get(ctx)
                // Nicht doppelt anlegen, falls dieses Gerät bereits eingetragen ist (z. B. Token erst jetzt bekannt)
                val existing = ipv64Request { ipv64.integrations.list(includeSecrets = true) }
                    .firstOrNull { it.type == IntegrationType.APP && it.deviceToken() == token }
                if (existing != null) {
                    thisDeviceRegistered = true
                    thisDeviceId = existing.id
                    reload = true
                    return@launch
                }
                // Ab hier zeigt ein Fehler evtl. eine bereits angelegte Integration nicht an, daher immer neu laden
                reload = true
                val created = ipv64Request {
                    ipv64.integrations.create(
                        CreateIntegrationRequest(type = IntegrationType.APP, name = name, deviceToken = token)
                    )
                }
                thisDeviceRegistered = true
                thisDeviceId = created.id
            } catch (e: IPv64Exception) {
                println(e.message)
                showError(e.toError(IPv64Context.Other), retryLoad = false)
            } finally {
                showLoadingDialog = false
                // Nach Erfolg, Duplikat oder Fehler beim Anlegen die Liste aktualisieren
                if (reload) getIntegrations()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(Tabs.getLabel(Tab.integrations))
                }, modifier = Modifier.statusBarsPadding()
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(mainPadding)
            .consumeWindowInsets(mainPadding)  // avoids double insets
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .fillMaxHeight()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isLoaded) {
                    item(key = "this-device") {
                        if (thisDeviceRegistered) {
                            AddDeviceCard(
                                title = "This device is registered",
                                text = "Push notifications are sent to this device via the IPv64 App integration."
                            )
                        } else {
                            AddDeviceCard(
                                title = "Add this device (IPv64 App)",
                                text = "Receive healthcheck notifications as push notifications on this device.",
                                onClick = {
                                    // Ohne Benachrichtigungsberechtigung kommen die Pushs nicht an
                                    if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                                        errorRetryLoad = false
                                        errorDialogTitle = "Notifications disabled"
                                        errorDialogText = "Please allow notifications for IPv64.net in the system settings " +
                                                "to receive push notifications on this device."
                                        errorDialogButtonText = android.R.string.ok
                                        showDialog = true
                                    } else {
                                        deviceName = defaultDeviceName(ctx)
                                        showAddDialog = true
                                    }
                                }
                            )
                        }
                    }
                }
                itemsIndexed(
                    items = integrations,
                    key = { i, ig -> ig.id ?: "idx-$i" } // stabile Keys
                ) { _, ig ->
                    IntegrationItemView(
                        ig,
                        isThisDevice = thisDeviceId != null && ig.id == thisDeviceId
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        getIntegrations()
    }

    if (showLoadingDialog) {
        SpinnerDialog(
            spinnerText = loadingText
        )
    }

    if (showAddDialog) {
        AlertDialog(
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.mobile_24px),
                    contentDescription = "Header Icon"
                )
            },
            title = { Text("Add this device") },
            text = {
                OutlinedTextField(
                    value = deviceName,
                    singleLine = true,
                    label = { Text("Name") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    onValueChange = { newText -> deviceName = newText },
                )
            },
            onDismissRequest = { showAddDialog = false },
            confirmButton = {
                TextButton(
                    enabled = deviceName.isNotBlank(),
                    onClick = {
                        showAddDialog = false
                        addThisDevice(deviceName.trim())
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            }
        )
    }

    if (showDialog) {
        ErrorDialog(
            onDismissRequest = { showDialog = false },
            onConfirmation = {
                showDialog = false
                if (errorRetryLoad) getIntegrations()
            },
            dialogTitle = errorDialogTitle,
            dialogText = errorDialogText,
            dialogConfirmText = errorDialogButtonText
        )
    }

    if (showRequestDialog) {
        RequestDialogs(
            onDismissRequest = { showRequestDialog = false },
            onConfirmation = { showRequestDialog = false; },
            dialogConfirmText = errorDialogButtonText,
            request = requestType
        )
    }
}

@Composable
private fun AddDeviceCard(title: String, text: String, onClick: (() -> Unit)? = null) {
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.mobile_24px),
                contentDescription = "icon",
                modifier = Modifier
                    .padding(start = 16.dp)
                    .height(28.dp)
            )
            Column(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 16.dp, start = 8.dp, end = 16.dp)
                    .fillMaxWidth()
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (onClick != null) {
        Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) { content() }
    } else {
        Card(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

// Mögliche Schlüssel des Device-Tokens in options (Request: "device_token", API v1: "devicetoken")
private val DEVICE_TOKEN_KEYS = listOf("device_token", "devicetoken", "deviceToken")

private fun Integration.deviceToken(): String? =
    DEVICE_TOKEN_KEYS.firstNotNullOfOrNull { key ->
        (options?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.ifEmpty { null }
    }

// Gerätename aus den Systemeinstellungen, sonst Hersteller und Modell
private fun defaultDeviceName(ctx: Context): String {
    val systemName = runCatching {
        Settings.Global.getString(ctx.contentResolver, Settings.Global.DEVICE_NAME)
    }.getOrNull()?.trim()
    if (!systemName.isNullOrEmpty()) return systemName
    val model = Build.MODEL.orEmpty().trim()
    val manufacturer = Build.MANUFACTURER.orEmpty().trim().replaceFirstChar { it.uppercase() }
    return if (manufacturer.isEmpty() || model.startsWith(manufacturer, ignoreCase = true)) model
    else "$manufacturer $model"
}
