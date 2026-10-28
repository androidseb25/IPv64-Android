package de.rpicloud.ipv64net.main.views

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.Hc2ChannelCache
import de.rpicloud.ipv64net.helper.Hc2ChannelTypeCache
import de.rpicloud.ipv64net.helper.Hc2NavKeys
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.IPv64Context
import de.rpicloud.ipv64net.helper.PushChannelRegistrar
import de.rpicloud.ipv64net.helper.displayName
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.isAwaitingVerification
import de.rpicloud.ipv64net.helper.toError
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.Hc2Channel
import de.sr.ipv64.model.Hc2ChannelType
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Benachrichtigungs-Tab: hc2-Kanäle, Karte "Dieses Gerät", Hinweis bei deaktivierten Benachrichtigungen.
 * Anfragen: Kanalliste (Cache), Kanaltypen (Cache), ggf. ein PATCH bei geändertem Gerätetoken.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val dialog = rememberChannelDialogState()

    var spinnerText by remember { mutableStateOf<String?>(null) }
    var channels by remember { mutableStateOf<List<Hc2Channel>>(emptyList()) }
    var types by remember { mutableStateOf<List<Hc2ChannelType>?>(null) }
    var thisDeviceId by remember { mutableStateOf<Long?>(null) }
    var isLoaded by remember { mutableStateOf(false) }
    val permission = rememberNotificationPermissionState()
    var verifyChannel by remember { mutableStateOf<Hc2Channel?>(null) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    // Erzwungenes Neuladen, während ein Laden läuft: danach erneut (frisch) laden statt verwerfen
    var reloadQueued by remember { mutableStateOf(false) }

    // Ein Ladedurchgang; wirft IPv64Exception
    suspend fun loadOnce(force: Boolean) {
        // Stabile Account-ID (einmal pro API-Key), bevor die gespeicherte Geräte-Kanal-ID gelesen wird
        PushChannelRegistrar.ensureAccountId(ctx)
        // Sequentiell: Liste, dann Typen (beide gecacht)
        val list = Hc2ChannelCache.get(ctx, force)
        val loadedTypes = try {
            Hc2ChannelTypeCache.get(ctx)
        } catch (e: IPv64Exception) {
            // Ohne Typen gilt für "Not verified" die Fallback-Liste (Mail/SMS)
            null
        }
        try {
            PushChannelRegistrar.syncTokenIfChanged(ctx, list)
        } catch (e: IPv64Exception) {
            // Token-Abgleich ist nicht kritisch, beim nächsten Öffnen erneut
        }
        types = loadedTypes
        thisDeviceId = PushChannelRegistrar.detectThisDevice(ctx, list, loadedTypes)?.id
        channels = list
        isLoaded = true
    }

    fun load(force: Boolean = false) {
        if (loadJob?.isActive == true) {
            if (force) reloadQueued = true
            return
        }
        spinnerText = "Loading..."
        loadJob = scope.launch {
            try {
                var nextForce = force
                do {
                    reloadQueued = false
                    loadOnce(nextForce)
                    nextForce = true
                } while (reloadQueued)
            } catch (e: IPv64Exception) {
                dialog.error(e.toError(IPv64Context.Other), retry = { load(force = true) })
            } finally {
                reloadQueued = false
                spinnerText = null
            }
        }
    }

    fun sendTest(channel: Hc2Channel) {
        val id = channel.id ?: return
        spinnerText = "Sending test..."
        scope.launch {
            try {
                val result = ipv64Retrying { IPv64Client.get(ctx).hc2.channels.test(id) }
                // lastUsed/lastError ändern sich, beim nächsten Laden neu abrufen
                Hc2ChannelCache.invalidate()
                val (title, text) = channelTestResultMessage(result)
                dialog.message(title, text, icon = R.drawable.send_24px)
            } catch (e: IPv64Exception) {
                dialog.error(e.toError(IPv64Context.Other))
            } finally {
                spinnerText = null
            }
        }
    }

    fun openChannel(channel: Hc2Channel) {
        val id = channel.id ?: return
        navController.currentBackStackEntry?.savedStateHandle?.set(Hc2NavKeys.SELECTED_CHANNEL_ID, id)
        navController.navigate(Tabs.getRoute(Tab.channel_edit))
    }

    val thisDevice = channels.firstOrNull { it.id != null && it.id == thisDeviceId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Tabs.getLabel(Tab.channels)) },
                actions = {
                    IconButton(onClick = { load(force = true) }) {
                        Icon(painter = painterResource(id = R.drawable.refresh_24px), contentDescription = "Refresh")
                    }
                },
                modifier = Modifier.statusBarsPadding()
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = isLoaded,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                FloatingActionButton(onClick = { navController.navigate(Tabs.getRoute(Tab.channel_new)) }) {
                    Icon(painter = painterResource(id = R.drawable.add_24px), contentDescription = "Add channel")
                }
            }
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
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!permission.notificationsOk) {
                    item(key = "notifications-disabled") {
                        NotificationsDisabledCard(permission)
                    }
                }
                if (isLoaded) {
                    item(key = "this-device") {
                        ThisDeviceCard(
                            channel = thisDevice,
                            onSetup = { navController.navigate(Tabs.getRoute(Tab.push_setup)) { launchSingleTop = true } },
                            onOpen = { thisDevice?.let { ch -> openChannel(ch) } },
                            onTest = { thisDevice?.let { ch -> sendTest(ch) } }
                        )
                    }
                    if (channels.none { it.default == true }) {
                        item(key = "no-default") {
                            Text(
                                "No default channel set. Checks without explicit channels notify default channels.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                    if (channels.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                "No channels yet. Tap + to add one.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
                items(
                    items = channels,
                    key = { ch -> ch.id ?: "idx-${channels.indexOf(ch)}" }
                ) { ch ->
                    val awaiting = ch.isAwaitingVerification(types)
                    ChannelItemView(
                        channel = ch,
                        awaitingVerification = awaiting,
                        isThisDevice = ch.id != null && ch.id == thisDeviceId,
                        onClick = { openChannel(ch) },
                        onVerifyClick = { verifyChannel = ch }
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        load()
    }

    verifyChannel?.let { ch ->
        ChannelVerificationDialog(
            channel = ch,
            onVerified = {
                verifyChannel = null
                load(force = true)
            },
            onDismiss = { verifyChannel = null }
        )
    }

    spinnerText?.let { SpinnerDialog(spinnerText = it) }

    ChannelDialogHost(dialog)
}

// Karte "Dieses Gerät": einrichten oder (registriert) Name, Status und Testnachricht
@Composable
private fun ThisDeviceCard(
    channel: Hc2Channel?,
    onSetup: () -> Unit,
    onOpen: () -> Unit,
    onTest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (channel != null) onOpen else onSetup
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                painter = painterResource(id = R.drawable.mobile_24px),
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(28.dp)
            )
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .fillMaxWidth()
            ) {
                if (channel == null) {
                    Text("This device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Receive healthcheck alerts as push notifications on this device.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onSetup) { Text("Set up push notifications") }
                } else {
                    Text(
                        channel.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (channel.enabled == false) "This device · Disabled" else "This device · Enabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (channel.enabled == false) colorResource(R.color.ipv64_gray)
                        else colorResource(R.color.ipv64_teal)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onTest) {
                        Icon(
                            painter = painterResource(id = R.drawable.send_24px),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Send test", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

// Warnhinweis, wenn Benachrichtigungen für die App deaktiviert sind
@Composable
private fun NotificationsDisabledCard(permission: NotificationPermissionState) {
    val orange = colorResource(R.color.ipv64_orange)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = orange.copy(alpha = 0.15f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                painter = painterResource(id = R.drawable.notifications_off_24px),
                contentDescription = null,
                tint = orange,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(28.dp)
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    "Notifications are disabled",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                NotificationPermissionActions(permission)
            }
        }
    }
}
