package de.rpicloud.ipv64net.main.views

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import de.rpicloud.ipv64net.helper.HC2_CHANNEL_TYPE_APP
import de.rpicloud.ipv64net.helper.Hc2ChannelCache
import de.rpicloud.ipv64net.helper.Hc2ChannelTypeCache
import de.rpicloud.ipv64net.helper.Hc2NavKeys
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.IPv64Context
import de.rpicloud.ipv64net.helper.PushChannelRegistrar
import de.rpicloud.ipv64net.helper.PushSetupStore
import de.rpicloud.ipv64net.helper.channelTypeIcon
import de.rpicloud.ipv64net.helper.displayName
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.isAwaitingVerification
import de.rpicloud.ipv64net.helper.systemChannelLang
import de.rpicloud.ipv64net.helper.toError
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.Hc2Channel
import de.sr.ipv64.model.Hc2ChannelInput
import de.sr.ipv64.model.Hc2ChannelType
import de.sr.ipv64.model.Hc2ChannelTypeField
import de.sr.ipv64.model.Hc2ChannelUpdate
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * Kanal anlegen (Typauswahl → dynamisches Formular aus hc2.channelTypes()) oder bearbeiten (channels.get(id)).
 * Bearbeiten behält Secrets: geheime Felder werden nur gesendet, wenn eingetippt; PATCH enthält nur geänderte Werte.
 * Nach jeder Änderung: Hc2ChannelCache.invalidate() und zurück.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSetupView(navController: NavHostController, mainPadding: PaddingValues, editMode: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dialog = rememberChannelDialogState()
    val channelId = remember {
        if (editMode) navController.previousBackStackEntry?.savedStateHandle?.get<Long>(Hc2NavKeys.SELECTED_CHANNEL_ID)
        else null
    }

    var spinnerText by remember { mutableStateOf<String?>(null) }
    var types by remember { mutableStateOf<List<Hc2ChannelType>?>(null) }
    var selectedType by remember { mutableStateOf<Hc2ChannelType?>(null) }
    // Bearbeiten: geladener Kanal (Ausgangswerte für den Vergleich)
    var channel by remember { mutableStateOf<Hc2Channel?>(null) }
    var formVisible by remember { mutableStateOf(false) }
    var closed by remember { mutableStateOf(false) }

    // Formular: gemeinsame Eigenschaften
    var name by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    var isDefault by remember { mutableStateOf(false) }
    var lang by remember { mutableStateOf(systemChannelLang()) }
    var notifyDown by remember { mutableStateOf(true) }
    var notifyUp by remember { mutableStateOf(true) }
    // Formular: Konfigurationsfelder (Schlüssel → Text) und eingetippte Felder
    val values = remember { mutableStateMapOf<String, String>() }
    val touched = remember { mutableStateMapOf<String, Boolean>() }
    var showValidation by remember { mutableStateOf(false) }

    var verifyChannel by remember { mutableStateOf<Hc2Channel?>(null) }
    var askTestFor by remember { mutableStateOf<Hc2Channel?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun close() {
        if (closed) return
        closed = true
        navController.popBackStack()
    }

    fun startForm(type: Hc2ChannelType?, existing: Hc2Channel?) {
        selectedType = type
        values.clear()
        touched.clear()
        showValidation = false
        if (existing != null) {
            name = existing.name.orEmpty()
            enabled = existing.enabled ?: true
            isDefault = existing.default ?: false
            lang = existing.lang?.takeIf { it == "de" || it == "en" } ?: systemChannelLang()
            notifyDown = existing.notifyDown ?: true
            notifyUp = existing.notifyUp ?: true
        } else {
            name = type?.label.orEmpty()
            enabled = true
            isDefault = false
            lang = systemChannelLang()
            notifyDown = true
            notifyUp = true
        }
        type?.fields?.forEach { field ->
            val key = field.key?.takeIf { it.isNotBlank() } ?: return@forEach
            values[key] = initialChannelFieldValue(field, existing?.config, isEdit = existing != null)
        }
        formVisible = true
    }

    fun load() {
        spinnerText = "Loading..."
        scope.launch {
            try {
                // Sequentiell: Kanaltypen (Cache), beim Bearbeiten danach der Kanal
                val loadedTypes = Hc2ChannelTypeCache.get(ctx)
                types = loadedTypes
                if (editMode) {
                    val id = channelId
                    if (id == null) {
                        dialog.message("Channel not found", "The selected channel could not be opened.", onClose = { close() })
                        return@launch
                    }
                    val loaded = ipv64Retrying { IPv64Client.get(ctx).hc2.channels.get(id) }
                    channel = loaded
                    startForm(loadedTypes.firstOrNull { it.type == loaded.type }, loaded)
                }
            } catch (e: IPv64Exception) {
                dialog.error(e.toError(IPv64Context.Other), retry = { load() }, onClose = { close() })
            } finally {
                spinnerText = null
            }
        }
    }

    // Beim Bearbeiten des App-Kanals bleibt das Gerätetoken verborgen und unverändert
    val hiddenKeys: Set<String> = run {
        val ch = channel
        if (editMode && ch?.type == HC2_CHANNEL_TYPE_APP) setOf(PushChannelRegistrar.tokenKeyFor(ch, selectedType))
        else emptySet()
    }
    val visibleFields: List<Hc2ChannelTypeField> = selectedType?.fields.orEmpty()
        .filter { !it.key.isNullOrBlank() && it.key !in hiddenKeys }
    val secretsSet: Set<String> = channel?.secretsSet?.toSet().orEmpty()

    fun isRequired(field: Hc2ChannelTypeField): Boolean =
        field.required == true && field.kindKey != "bool" &&
                !(editMode && field.isSecretField && field.key in secretsSet)

    fun fieldErrorFor(field: Hc2ChannelTypeField): String? =
        channelFieldError(field, values[field.key].orEmpty(), isRequired(field))

    fun sendTest(target: Hc2Channel, closeAfter: Boolean) {
        val id = target.id ?: return
        spinnerText = "Sending test..."
        scope.launch {
            try {
                val result = ipv64Retrying { IPv64Client.get(ctx).hc2.channels.test(id) }
                Hc2ChannelCache.invalidate()
                val (title, text) = channelTestResultMessage(result)
                dialog.message(title, text, icon = R.drawable.send_24px, onClose = if (closeAfter) ({ close() }) else null)
            } catch (e: IPv64Exception) {
                dialog.error(e.toError(IPv64Context.Other), onClose = if (closeAfter) ({ close() }) else null)
            } finally {
                spinnerText = null
            }
        }
    }

    fun save() {
        showValidation = true
        if (name.isBlank() || visibleFields.any { fieldErrorFor(it) != null }) return
        val client = IPv64Client.get(ctx)
        val touchedKeys = touched.filterValues { it }.keys

        if (editMode) {
            val original = channel ?: return
            val id = original.id ?: return
            val baseConfig = original.config ?: JsonObject(emptyMap())
            val config = buildChannelConfig(
                visibleFields, values, touchedKeys, baseConfig, isEdit = true, keepKeys = hiddenKeys
            )
            val trimmedName = name.trim()
            // Vergleich mit den Startwerten des Formulars (fehlende Serverwerte = Formular-Standard)
            val originalLang = original.lang?.takeIf { it == "de" || it == "en" } ?: systemChannelLang()
            val update = Hc2ChannelUpdate(
                name = trimmedName.takeIf { it != original.name.orEmpty() },
                config = config.takeIf { it != baseConfig },
                enabled = enabled.takeIf { it != (original.enabled ?: true) },
                default = isDefault.takeIf { it != (original.default ?: false) },
                lang = lang.takeIf { it != originalLang },
                notifyDown = notifyDown.takeIf { it != (original.notifyDown ?: true) },
                notifyUp = notifyUp.takeIf { it != (original.notifyUp ?: true) }
            )
            if (update == Hc2ChannelUpdate()) {
                close()
                return
            }
            spinnerText = "Saving..."
            scope.launch {
                try {
                    ipv64Retrying { client.hc2.channels.update(id, update) }
                    Hc2ChannelCache.invalidate()
                    close()
                } catch (e: IPv64Exception) {
                    dialog.error(e.toError(IPv64Context.Other))
                } finally {
                    spinnerText = null
                }
            }
        } else {
            val type = selectedType ?: return
            val typeKey = type.type ?: return
            val config = buildChannelConfig(visibleFields, values, touchedKeys, base = null, isEdit = false)
            val input = Hc2ChannelInput(
                name = name.trim(),
                type = typeKey,
                config = config,
                enabled = enabled,
                default = isDefault,
                lang = lang,
                notifyDown = notifyDown,
                notifyUp = notifyUp
            )
            spinnerText = "Creating channel..."
            scope.launch {
                try {
                    val created = ipv64Retrying { client.hc2.channels.create(input) }
                    Hc2ChannelCache.invalidate()
                    when {
                        created.id == null -> close()
                        created.isAwaitingVerification(types) -> verifyChannel = created
                        else -> askTestFor = created
                    }
                } catch (e: IPv64Exception) {
                    dialog.error(e.toError(IPv64Context.Other))
                } finally {
                    spinnerText = null
                }
            }
        }
    }

    fun delete() {
        val id = channel?.id ?: return
        spinnerText = "Deleting..."
        scope.launch {
            try {
                ipv64Retrying { IPv64Client.get(ctx).hc2.channels.delete(id) }
                if (PushSetupStore.channelId(ctx) == id) PushSetupStore.clearChannel(ctx)
                Hc2ChannelCache.invalidate()
                close()
            } catch (e: IPv64Exception) {
                dialog.error(e.toError(IPv64Context.Other))
            } finally {
                spinnerText = null
            }
        }
    }

    // Neu: Zurück im Formular führt zur Typauswahl
    val inNewForm = !editMode && formVisible
    BackHandler(enabled = inNewForm) {
        formVisible = false
        selectedType = null
    }

    val loadedChannel = channel
    val awaitingVerification = editMode && loadedChannel != null && loadedChannel.isAwaitingVerification(types)
    val isThisDevice = editMode && loadedChannel?.id != null && loadedChannel.id == PushSetupStore.channelId(ctx)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = when {
                        editMode -> Tabs.getLabel(Tab.channel_edit)
                        formVisible -> selectedType?.label?.takeIf { it.isNotBlank() } ?: Tabs.getLabel(Tab.channel_new)
                        else -> Tabs.getLabel(Tab.channel_new)
                    }
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                modifier = Modifier.statusBarsPadding(),
                navigationIcon = {
                    IconButton(onClick = {
                        if (inNewForm) {
                            formVisible = false
                            selectedType = null
                        } else {
                            close()
                        }
                    }) {
                        Icon(painter = painterResource(id = R.drawable.arrow_back_24px), contentDescription = "Back")
                    }
                },
                actions = {
                    if (editMode && loadedChannel != null) {
                        IconButton(
                            onClick = { sendTest(loadedChannel, closeAfter = false) },
                            enabled = !awaitingVerification
                        ) {
                            Icon(painter = painterResource(id = R.drawable.send_24px), contentDescription = "Send test")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(painter = painterResource(id = R.drawable.delete_24px), contentDescription = "Delete")
                        }
                    }
                    if (formVisible) {
                        IconButton(onClick = { save() }) {
                            Icon(painter = painterResource(id = R.drawable.save_24px), contentDescription = "Save")
                        }
                    }
                }
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(mainPadding)
            .consumeWindowInsets(mainPadding)  // avoids double insets
    ) { innerPadding ->
        if (!editMode && !formVisible) {
            // Schritt 1: Typauswahl
            val list = types
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (list != null && list.isEmpty()) {
                    item(key = "empty") { Text("No channel types available.") }
                }
                items(items = list.orEmpty(), key = { it.type ?: it.hashCode() }) { type ->
                    ChannelTypeRow(type) {
                        if (type.type == HC2_CHANNEL_TYPE_APP) {
                            // Dieses Gerät wird über die Push-Einrichtung registriert
                            navController.navigate(Tabs.getRoute(Tab.push_setup)) {
                                popUpTo(Tabs.getRoute(Tab.channel_new)) { inclusive = true }
                                launchSingleTop = true
                            }
                        } else {
                            startForm(type, null)
                        }
                    }
                }
            }
        } else if (formVisible) {
            // Schritt 2: Formular
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (awaitingVerification) {
                    VerificationNeededCard(onVerify = { verifyChannel = loadedChannel })
                }
                if (editMode && loadedChannel != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = channelTypeIcon(loadedChannel.type)),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            (loadedChannel.typeLabel ?: loadedChannel.type.orEmpty()) +
                                    if (isThisDevice) " · This device" else "",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                ChannelFormSection("General") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name *") },
                        singleLine = true,
                        isError = showValidation && name.isBlank(),
                        supportingText = if (showValidation && name.isBlank()) ({ Text("Required") }) else null,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ChannelSwitchRow(title = "Enabled", checked = enabled, onCheckedChange = { enabled = it })
                    ChannelSwitchRow(
                        title = "Default channel",
                        subtitle = "Checks without explicit channels notify default channels.",
                        checked = isDefault,
                        onCheckedChange = { isDefault = it }
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
                    ChannelLanguageSelector(lang = lang, onChange = { lang = it })
                }

                val sectionTitle = selectedType?.label?.takeIf { it.isNotBlank() }
                    ?: loadedChannel?.typeLabel?.takeIf { it.isNotBlank() } ?: "Settings"
                if (selectedType == null && editMode) {
                    ChannelFormSection(sectionTitle) {
                        Text(
                            "The settings of this channel type can only be changed at ipv64.net.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else if (visibleFields.isNotEmpty()) {
                    ChannelFormSection(sectionTitle) {
                        visibleFields.forEach { field ->
                            val key = field.key.orEmpty()
                            ChannelConfigField(
                                field = field,
                                value = values[key].orEmpty(),
                                onValueChange = {
                                    values[key] = it
                                    touched[key] = true
                                },
                                required = isRequired(field),
                                error = if (showValidation) fieldErrorFor(field) else null,
                                secretIsSet = editMode && field.isSecretField && key in secretsSet
                            )
                        }
                    }
                }

                if (!editMode && selectedType?.needsVerification == true) {
                    Text(
                        "After creating the channel, a verification code will be sent to confirm it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (editMode) "Save" else "Create channel")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    LaunchedEffect(Unit) {
        load()
    }

    verifyChannel?.let { target ->
        ChannelVerificationDialog(
            channel = target,
            onVerified = {
                verifyChannel = null
                if (editMode) {
                    channel = channel?.copy(verified = true)
                } else {
                    askTestFor = target
                }
            },
            onDismiss = {
                verifyChannel = null
                // Neu angelegt: Kanal existiert bereits, Bestätigung später über die Liste
                if (!editMode) close()
            }
        )
    }

    askTestFor?.let { target ->
        AlertDialog(
            icon = { Icon(painter = painterResource(id = R.drawable.send_24px), contentDescription = null) },
            title = { Text("Channel saved") },
            text = { Text("Send a test notification to “${target.displayName}” now?") },
            onDismissRequest = { },
            confirmButton = {
                TextButton(onClick = {
                    askTestFor = null
                    sendTest(target, closeAfter = true)
                }) { Text("Send test") }
            },
            dismissButton = {
                TextButton(onClick = {
                    askTestFor = null
                    close()
                }) { Text("Later") }
            }
        )
    }

    if (showDeleteConfirm) {
        val target = loadedChannel
        ErrorDialog(
            onDismissRequest = { showDeleteConfirm = false },
            onConfirmation = {
                showDeleteConfirm = false
                delete()
            },
            dialogTitle = "Delete channel?",
            dialogText = android.text.TextUtils.htmlEncode(
                "“${target?.displayName.orEmpty()}” will be deleted. Checks using only this channel will notify " +
                        "default channels instead." +
                        if (isThisDevice) " This device will no longer receive push notifications." else ""
            ),
            dialogConfirmText = R.string.delete,
            icon = R.drawable.delete_24px,
            showDismiss = true
        )
    }

    spinnerText?.let { SpinnerDialog(spinnerText = it) }

    ChannelDialogHost(dialog)
}

// Eintrag der Typauswahl
@Composable
private fun ChannelTypeRow(type: Hc2ChannelType, onClick: () -> Unit) {
    val isApp = type.type == HC2_CHANNEL_TYPE_APP
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = channelTypeIcon(type.type)),
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    if (isApp) "IPv64 App (this device)" else type.label?.takeIf { it.isNotBlank() } ?: type.type.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (type.needsVerification == true) {
                    Text(
                        "Needs verification",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// Hinweis: Kanal noch nicht bestätigt (Test nicht möglich)
@Composable
private fun VerificationNeededCard(onVerify: () -> Unit) {
    val orange = colorResource(R.color.ipv64_orange)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = orange.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Not verified", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = orange)
            Text(
                "This channel won't receive notifications and can't be tested until it is verified.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = onVerify) { Text("Verify now") }
        }
    }
}
