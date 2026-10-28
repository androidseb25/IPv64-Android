package de.rpicloud.ipv64net.main.views

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.DeviceChannelSettings
import de.rpicloud.ipv64net.helper.HC2_CHANNEL_TYPE_APP
import de.rpicloud.ipv64net.helper.DeviceRegistration
import de.rpicloud.ipv64net.helper.Hc2ChannelCache
import de.rpicloud.ipv64net.helper.Hc2ChannelTypeCache
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.IPv64Context
import de.rpicloud.ipv64net.helper.PushChannelRegistrar
import de.rpicloud.ipv64net.helper.PushRegistrationException
import de.rpicloud.ipv64net.helper.PushSetupStore
import de.rpicloud.ipv64net.helper.displayName
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.systemChannelLang
import de.rpicloud.ipv64net.helper.toError
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.Hc2Channel
import kotlinx.coroutines.launch

/**
 * Einrichtung der Push-Benachrichtigungen (beim ersten Start und später aus dem Benachrichtigungs-Tab):
 * 1) Berechtigung, 2) dieses Gerät als hc2-Kanal "app" registrieren (oder bestehenden übernehmen), 3) Testnachricht.
 * Skip, Zurück und Done rufen PushSetupStore.markDone() vor popBackStack() auf (sonst löst der Trigger erneut aus).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushSetupView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dialog = rememberChannelDialogState()

    // Schritt 1: Berechtigung
    val permission = rememberNotificationPermissionState()

    // Schritt 2: Gerät registrieren
    var deviceName by rememberSaveable { mutableStateOf(PushChannelRegistrar.defaultDeviceName(ctx)) }
    var notifyDown by rememberSaveable { mutableStateOf(true) }
    var notifyUp by rememberSaveable { mutableStateOf(true) }
    var useAsDefault by rememberSaveable { mutableStateOf(false) }
    // "Use as default" nur einmal vorbelegen (nicht nach Rotation die Eingabe überschreiben)
    var defaultInitialized by rememberSaveable { mutableStateOf(false) }
    var appSupported by remember { mutableStateOf<Boolean?>(null) }
    var deviceChannel by remember { mutableStateOf<Hc2Channel?>(null) }
    // Bestehender Kanal übernommen bzw. beim Öffnen erkannt
    var reusedExisting by remember { mutableStateOf(false) }
    var detectedOnOpen by remember { mutableStateOf(false) }
    var initialLoadDone by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<Hc2Channel>?>(null) }

    // Schritt 3: Test
    var testText by remember { mutableStateOf<String?>(null) }

    var spinnerText by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }

    // Erst als erledigt markieren, dann zurück; doppeltes Antippen darf keinen zweiten Eintrag vom Stack nehmen
    fun finish() {
        if (finished) return
        finished = true
        PushSetupStore.markDone(ctx)
        if (!navController.popBackStack()) {
            navController.navigate(Tabs.getRoute(Tab.channels)) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    fun register(adoptChannelId: Long? = null, createNew: Boolean = false) {
        val name = deviceName.trim()
        if (name.isEmpty()) return
        spinnerText = "Registering this device..."
        scope.launch {
            try {
                val result = PushChannelRegistrar.registerThisDevice(
                    ctx,
                    DeviceChannelSettings(
                        name = name,
                        default = useAsDefault,
                        notifyDown = notifyDown,
                        notifyUp = notifyUp,
                        lang = systemChannelLang()
                    ),
                    adoptChannelId = adoptChannelId,
                    createNew = createNew
                )
                when (result) {
                    is DeviceRegistration.Registered -> {
                        deviceChannel = result.channel
                        reusedExisting = result.reused
                        detectedOnOpen = false
                        testText = null
                    }

                    is DeviceRegistration.ExistingCandidates -> candidates = result.candidates
                }
            } catch (e: PushRegistrationException) {
                dialog.message(e.title, e.message.orEmpty(), icon = R.drawable.error_24px)
            } catch (e: IPv64Exception) {
                dialog.error(e.toError(IPv64Context.Other))
            } finally {
                spinnerText = null
            }
        }
    }

    fun sendTest() {
        val id = deviceChannel?.id ?: return
        spinnerText = "Sending test..."
        scope.launch {
            try {
                val result = ipv64Retrying { IPv64Client.get(ctx).hc2.channels.test(id) }
                Hc2ChannelCache.invalidate()
                testText = when {
                    result.sent == true -> "Sent – you should get a push now."
                    result.queuedAtNode == true -> "Queued – you should get a push shortly."
                    else -> "The server did not send the test notification."
                }
            } catch (e: IPv64Exception) {
                dialog.error(e.toError(IPv64Context.Other))
            } finally {
                spinnerText = null
            }
        }
    }

    BackHandler { finish() }

    // Bestehenden Gerätekanal erkennen und "Default" vorbelegen (nur wenn es noch keinen Standardkanal gibt)
    LaunchedEffect(Unit) {
        try {
            PushChannelRegistrar.ensureAccountId(ctx)
            val list = Hc2ChannelCache.get(ctx)
            val types = try {
                Hc2ChannelTypeCache.get(ctx)
            } catch (e: IPv64Exception) {
                null
            }
            appSupported = types?.let { PushChannelRegistrar.appType(it) != null }
            try {
                PushChannelRegistrar.syncTokenIfChanged(ctx, list)
            } catch (e: IPv64Exception) {
                // nicht kritisch
            }
            if (deviceChannel == null) {
                deviceChannel = PushChannelRegistrar.detectThisDevice(ctx, list, types)
                detectedOnOpen = deviceChannel != null
            }
            if (!defaultInitialized) {
                // App-Kanäle (dieses oder andere Geräte, ggf. später übernommen) zählen nicht: ein übernommener
                // Default-Kanal bleibt ohnehin Default, ein neues Gerät soll zusätzlich Default werden können
                useAsDefault = list.none { it.default == true && it.type != HC2_CHANNEL_TYPE_APP }
                defaultInitialized = true
            }
        } catch (e: IPv64Exception) {
            // Liste nicht ladbar: Fehler zeigt sich beim Registrieren
        } finally {
            initialLoadDone = true
        }
    }

    val registered = deviceChannel?.id != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Tabs.getLabel(Tab.push_setup)) },
                modifier = Modifier.statusBarsPadding(),
                actions = {
                    TextButton(onClick = { finish() }) {
                        Text(if (registered) "Done" else "Skip")
                    }
                }
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(mainPadding)
            .consumeWindowInsets(mainPadding)  // avoids double insets
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.notifications_unread_24px),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    "Get a push notification on this device when a healthcheck goes down or recovers.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            // Schritt 1: Berechtigung
            PushSetupStepCard(number = 1, title = "Allow notifications", done = permission.notificationsOk) {
                if (permission.notificationsOk) {
                    Text("Notifications are allowed.", style = MaterialTheme.typography.bodySmall)
                } else {
                    NotificationPermissionActions(permission)
                }
            }

            // Schritt 2: Gerät registrieren
            PushSetupStepCard(number = 2, title = "Register this device", done = registered) {
                val channel = deviceChannel
                if (channel != null && registered) {
                    Text(
                        when {
                            detectedOnOpen -> "Already registered as “${channel.displayName}”. Its existing settings are kept."
                            reusedExisting -> "Using the existing channel “${channel.displayName}” with your settings."
                            else -> "Registered as “${channel.displayName}”."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (appSupported == false) {
                    Text(
                        "Push notifications to the IPv64 app are not supported by the server yet. You can skip this step.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    if (!PushSetupStore.isSecNtfyConfigured(ctx)) {
                        Text(
                            "The push service is not configured on this device yet. Registering may fail; " +
                                    "try logging in again if it does.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorResource(R.color.ipv64_orange)
                        )
                    }
                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = { deviceName = it },
                        label = { Text("Device name *") },
                        singleLine = true,
                        isError = deviceName.isBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    ChannelSwitchRow(
                        title = "Notify when a check goes down",
                        checked = notifyDown,
                        onCheckedChange = { notifyDown = it }
                    )
                    ChannelSwitchRow(
                        title = "Notify when a check recovers",
                        checked = notifyUp,
                        onCheckedChange = { notifyUp = it }
                    )
                    ChannelSwitchRow(
                        title = "Use as default channel",
                        subtitle = "Checks without explicit channels notify default channels.",
                        checked = useAsDefault,
                        onCheckedChange = { useAsDefault = it }
                    )
                    Button(
                        onClick = { register() },
                        enabled = initialLoadDone && deviceName.isNotBlank()
                    ) { Text("Add this device") }
                }
            }

            // Schritt 3: Test
            PushSetupStepCard(number = 3, title = "Send a test", done = false, enabled = registered) {
                if (!registered) {
                    Text(
                        "Register this device first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    if (!permission.notificationsOk) {
                        Text(
                            "Notifications are not allowed yet, so the test push won't be shown.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorResource(R.color.ipv64_orange)
                        )
                    }
                    OutlinedButton(onClick = { sendTest() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.send_24px),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Send test notification", modifier = Modifier.padding(start = 8.dp))
                    }
                    testText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
            }
        }
    }

    // Es gibt App-Kanäle, die nicht als dieses Gerät erkannt wurden: übernehmen statt doppelt anlegen
    candidates?.let { list ->
        AlertDialog(
            icon = { Icon(painter = painterResource(id = R.drawable.mobile_24px), contentDescription = null) },
            title = { Text("Existing app channels") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "This account already has app channels. If one of them is this device (for example after " +
                                "reinstalling the app or using a new API key), use it to avoid duplicate notifications.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Column(
                        modifier = Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        list.forEach { ch ->
                            TextButton(onClick = {
                                candidates = null
                                ch.id?.let { register(adoptChannelId = it) }
                            }) {
                                Text(
                                    "Use existing channel “${ch.displayName}”",
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            onDismissRequest = { candidates = null },
            confirmButton = {
                TextButton(onClick = {
                    candidates = null
                    register(createNew = true)
                }) { Text("Create new") }
            },
            dismissButton = {
                TextButton(onClick = { candidates = null }) { Text("Cancel") }
            }
        )
    }

    spinnerText?.let { SpinnerDialog(spinnerText = it) }

    ChannelDialogHost(dialog)
}

// Karte für einen Schritt mit Nummer bzw. Haken, Titel und Inhalt
@Composable
private fun PushSetupStepCard(
    number: Int,
    title: String,
    done: Boolean,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val green = colorResource(R.color.ipv64_green)
    val accent = when {
        done -> green
        enabled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(accent.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (done) {
                        Icon(
                            painter = painterResource(id = R.drawable.verified_24px),
                            contentDescription = "Done",
                            tint = green,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(number.toString(), color = accent, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled || done) Color.Unspecified else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            content()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Benachrichtigungs-Berechtigung (gemeinsam mit dem Benachrichtigungs-Tab)
// ---------------------------------------------------------------------------------------------

/**
 * Zustand der Benachrichtigungs-Berechtigung. [canRequest]: Laufzeitanfrage möglich (Android 13+, nicht erteilt und
 * nicht dauerhaft abgelehnt); sonst bleibt nur die Systemeinstellung. Wird bei ON_RESUME neu geprüft.
 */
@Stable
internal class NotificationPermissionState(
    private val ctx: Context,
    private val activity: Activity?,
    deniedState: MutableState<Boolean>
) {
    var notificationsOk by mutableStateOf(notificationsAllowed(ctx))
        private set

    // In dieser Sitzung abgelehnt; zusammen mit der Rationale erkennbar, ob dauerhaft abgelehnt
    private var denied by deniedState
    private var canAskAgain by mutableStateOf(true)
    internal var launcher: ActivityResultLauncher<String>? = null

    init {
        recheck()
    }

    val canRequest: Boolean
        get() = Build.VERSION.SDK_INT >= 33 && !notificationsOk &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED &&
                (!denied || canAskAgain)

    fun request() {
        if (Build.VERSION.SDK_INT >= 33) launcher?.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun openSettings() = openAppNotificationSettings(ctx)

    internal fun onResult(granted: Boolean) {
        if (!granted) denied = true
        recheck()
    }

    fun recheck() {
        notificationsOk = notificationsAllowed(ctx)
        if (denied && Build.VERSION.SDK_INT >= 33) {
            canAskAgain = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
            } ?: false
        }
    }
}

@Composable
internal fun rememberNotificationPermissionState(): NotificationPermissionState {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findPushSetupActivity() }
    val denied = rememberSaveable { mutableStateOf(false) }
    val state = remember(ctx) { NotificationPermissionState(ctx, activity, denied) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        state.onResult(granted)
    }
    state.launcher = launcher
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.recheck() }
    return state
}

// Text und Schaltfläche(n): Laufzeitanfrage, solange möglich, sonst Systemeinstellungen
@Composable
internal fun NotificationPermissionActions(permission: NotificationPermissionState) {
    val canRequest = permission.canRequest
    Text(
        if (canRequest) "Allow IPv64.net to show notifications so alerts can reach you."
        else "Notifications are turned off for IPv64.net. Turn them on in the system settings.",
        style = MaterialTheme.typography.bodySmall
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canRequest) {
            Button(onClick = { permission.request() }) { Text("Allow notifications") }
            OutlinedButton(onClick = { permission.openSettings() }) { Text("Settings") }
        } else {
            Button(onClick = { permission.openSettings() }) { Text("Open settings") }
        }
    }
}

// Benachrichtigungen für die App erlaubt (ab Android 13 inkl. Laufzeitberechtigung)
internal fun notificationsAllowed(ctx: Context): Boolean {
    val enabled = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    if (Build.VERSION.SDK_INT >= 33) {
        return enabled && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }
    return enabled
}

// Öffnet die Benachrichtigungseinstellungen der App (Fallback: App-Details)
internal fun openAppNotificationSettings(ctx: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
    if (ctx !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
        if (ctx !is Activity) fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(fallback)
        } catch (e: ActivityNotFoundException) {
            // Keine Einstellungen verfügbar
        }
    }
}

private fun Context.findPushSetupActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
