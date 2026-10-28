package de.rpicloud.ipv64net.main.views

import android.text.TextUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.Hc2ChannelCache
import de.rpicloud.ipv64net.helper.Hc2ChannelTypeCache
import de.rpicloud.ipv64net.helper.Hc2CheckListCache
import de.rpicloud.ipv64net.helper.Hc2MoveOutcome
import de.rpicloud.ipv64net.helper.Hc2MoveRunner
import de.rpicloud.ipv64net.helper.Hc2NavKeys
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.IPv64Context
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.toError
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.helper.views.RequestDialogs
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.models.RequestTyp
import de.rpicloud.ipv64net.models.creatableOnline
import de.rpicloud.ipv64net.models.label
import de.rpicloud.ipv64net.models.repeatMinutesLabel
import de.rpicloud.ipv64net.models.targetHint
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.Hc2Account
import de.sr.ipv64.model.Hc2Channel
import de.sr.ipv64.model.Hc2CheckDetail
import de.sr.ipv64.model.Hc2CheckInput
import de.sr.ipv64.model.Hc2CheckType
import de.sr.ipv64.model.Hc2CheckUpdate
import de.sr.ipv64.model.Hc2Execution
import de.sr.ipv64.model.Hc2IpMode
import de.sr.ipv64.model.Hc2Policy
import kotlinx.coroutines.launch

private val REPEAT_OPTIONS: List<Int?> = listOf(null, 0, 5, 10, 15, 30, 60, 120, 360, 720, 1440)

private val CREATE_TYPE_ORDER = listOf(
    Hc2CheckType.HTTP, Hc2CheckType.KEYWORD, Hc2CheckType.JSON, Hc2CheckType.DNS, Hc2CheckType.ICMP,
    Hc2CheckType.TCP, Hc2CheckType.PORT, Hc2CheckType.CERT, Hc2CheckType.PUSH, Hc2CheckType.WHOIS,
    Hc2CheckType.DOCKER,
)

private fun formatDouble(d: Double?): String =
    if (d == null) "" else if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

// Meldungstext für ErrorDialog (HTML): Sonderzeichen maskieren, Zeilenumbrüche erhalten
// Push-URL als sensibel markiert in die Zwischenablage (Android 13+ blendet die Vorschau aus)
private fun copySensitive(ctx: android.content.Context, text: String) {
    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = android.content.ClipData.newPlainText("Push URL", text)
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    cm.setPrimaryClip(clip)
}

private fun htmlText(s: String): String = TextUtils.htmlEncode(s).replace("\n", "<br>")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HealthcheckForm(navController: NavHostController, mainPadding: PaddingValues, editToken: String?) {
    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val isEdit = editToken != null

    // Lade-/Speicherzustand
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var spinnerText by remember { mutableStateOf("Saving healthcheck...") }
    var account by remember { mutableStateOf<Hc2Account?>(null) }
    var channels by remember { mutableStateOf<List<Hc2Channel>>(emptyList()) }
    var detail by remember { mutableStateOf<Hc2CheckDetail?>(null) }
    val channelTypes = remember(loading) { Hc2ChannelTypeCache.peek() }

    // Dialoge
    var errorTitle by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var popAfterError by remember { mutableStateOf(false) }
    var requestType by remember { mutableStateOf(RequestTyp.UnAuthorized) }
    var showRequest by remember { mutableStateOf(false) }
    var popAfterRequest by remember { mutableStateOf(false) }
    var pushUrl by remember { mutableStateOf<String?>(null) }
    // Nur ein Pop-Pfad für Ladefehler (sonst würde auch der Detail-Screen entfernt)
    var popped by remember { mutableStateOf(false) }
    fun safePop() {
        if (popped) return
        popped = true
        navController.popBackStack()
    }

    // Formularfelder
    var type by remember { mutableStateOf(Hc2CheckType.HTTP) }
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var ps by remember { mutableStateOf(ParamsState.initial(Hc2CheckType.HTTP)) }
    var iniPs by remember { mutableStateOf(ParamsState.initial(Hc2CheckType.HTTP)) }

    var intervalValue by remember { mutableStateOf("") }
    var intervalUnit by remember { mutableStateOf(1) }
    var retryValue by remember { mutableStateOf("") }
    var retryUnit by remember { mutableStateOf(0) }
    var retries by remember { mutableStateOf("") }
    var timeout by remember { mutableStateOf("") }
    var connectTimeout by remember { mutableStateOf("") }
    var stabilize by remember { mutableStateOf("") }
    var invert by remember { mutableStateOf(false) }
    var ipMode by remember { mutableStateOf<Hc2IpMode?>(null) }

    var locations by remember { mutableStateOf<List<String>>(emptyList()) }
    var policy by remember { mutableStateOf<Hc2Policy?>(null) }

    var notifyDown by remember { mutableStateOf(true) }
    var notifyUp by remember { mutableStateOf(true) }
    var notifyDegraded by remember { mutableStateOf(false) }
    var critical by remember { mutableStateOf(false) }
    var repeatMinutes by remember { mutableStateOf<Int?>(null) }
    var useDefaultChannels by remember { mutableStateOf(true) }
    var selectedChannels by remember { mutableStateOf<Set<Long>>(emptySet()) }

    var tags by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var createPaused by remember { mutableStateOf(false) }

    // Existierende Checks, die auf einem Node laufen (local/both): Ausführung nur lesen
    val isNodeCheck = detail?.execution.let { it == Hc2Execution.LOCAL || it == Hc2Execution.BOTH }
    val online = !isNodeCheck
    val maxLocations = account?.locationsPerCheck?.takeIf { it > 0 } ?: Int.MAX_VALUE

    fun showApiError(e: IPv64Exception, popAfter: Boolean = false) {
        val err = e.toError(IPv64Context.Other)
        if (err.requestType != null) {
            requestType = err.requestType
            popAfterRequest = popAfter
            showRequest = true
        } else {
            errorTitle = err.title
            errorText = htmlText(err.text)
            popAfterError = popAfter
            showError = true
        }
    }

    fun showMessage(title: String, text: String, popAfter: Boolean = false) {
        errorTitle = title
        errorText = htmlText(text)
        popAfterError = popAfter
        showError = true
    }

    fun applyDetail(d: Hc2CheckDetail) {
        val t = d.type ?: Hc2CheckType.HTTP
        type = t
        name = d.name.orEmpty()
        target = d.target.orEmpty()
        ps = ParamsState.from(t, d.params)
        iniPs = ps
        secondsToDuration(d.intervalSeconds).let { intervalValue = it.first; intervalUnit = it.second }
        secondsToDuration(d.retryIntervalSeconds).let { retryValue = it.first; retryUnit = it.second }
        retries = d.retries?.toString().orEmpty()
        timeout = formatDouble(d.timeoutSeconds)
        connectTimeout = formatDouble(d.connectTimeoutSeconds)
        stabilize = d.stabilize?.toString().orEmpty()
        invert = d.invert ?: false
        ipMode = d.ipMode
        locations = d.locations
        policy = d.policy
        notifyDown = d.notifyDown ?: true
        notifyUp = d.notifyUp ?: true
        notifyDegraded = d.notifyDegraded ?: false
        critical = d.critical ?: false
        repeatMinutes = d.repeatMinutes
        useDefaultChannels = d.channels.isEmpty()
        // IDs gelöschter Kanäle verwerfen (sonst 422 beim Speichern)
        val known = channels.mapNotNull { it.id }.toSet()
        val validIds = d.channels.filter { it in known }
        selectedChannels = validIds.toSet()
        if (validIds.isEmpty()) useDefaultChannels = true
        tags = d.tags.joinToString(", ")
        group = d.group.orEmpty()
        description = d.description.orEmpty()
    }

    // Sequenziell laden (Rate-Limit): Account, Kanäle, bei Bearbeitung der Check
    LaunchedEffect(Unit) {
        loading = true
        try {
            val client = IPv64Client.get(ctx)
            val acc = ipv64Retrying { client.hc2.account() }
            account = acc
            channels = Hc2ChannelCache.get(ctx, force = true)
            if (editToken != null) {
                val d = ipv64Retrying { client.hc2.checks.get(editToken) }
                detail = d
                applyDetail(d)
            } else {
                locations = acc.locations.mapNotNull { it.code }.take(1)
            }
        } catch (e: IPv64Exception) {
            showApiError(e, popAfter = true)
        } finally {
            loading = false
        }
    }

    if (isEdit && !loading && detail == null && !showError && !showRequest) {
        // Kein Token übergeben oder Laden fehlgeschlagen: Formular nicht anzeigen
        LaunchedEffect(Unit) { safePop() }
    }

    fun parseTags(): List<String> = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    // Gemeinsame Prüfung der Eingaben, Fehlertext oder null
    fun validate(): String? {
        if (name.isBlank()) return "Please enter a name."
        if (type != Hc2CheckType.PUSH && target.isBlank()) return "Please enter a target."
        ps.validate(type, target)?.let { return it }
        durationToSeconds(intervalValue, intervalUnit).let {
            if (it == -1 || (it != null && it < 20)) return "Interval must be at least 20 seconds."
        }
        durationToSeconds(retryValue, retryUnit).let {
            if (it == -1 || (it != null && it < 5)) return "Retry interval must be at least 5 seconds."
        }
        if (retries.isNotBlank() && retries.toIntOrNull()?.let { it in 0..10 } != true)
            return "Retries must be between 0 and 10."
        if (stabilize.isNotBlank() && stabilize.toIntOrNull()?.let { it in 1..20 } != true)
            return "Stabilize must be between 1 and 20."
        for ((label, v) in listOf("Timeout" to timeout, "Connect timeout" to connectTimeout)) {
            if (v.isNotBlank() && (v.toDoubleOrNull()?.let { it > 0 } != true)) return "$label must be greater than 0."
        }
        if (online && type != Hc2CheckType.PUSH) {
            if (locations.isEmpty()) return "Select at least one location."
            if (locations.size > maxLocations) return "Your plan allows up to $maxLocations locations per check."
        }
        if (!useDefaultChannels && selectedChannels.isEmpty())
            return "Select at least one channel or use the default channels."
        return null
    }

    fun finish() {
        Hc2CheckListCache.invalidate()
        navController.popBackStack()
    }

    fun saveCreate() {
        val locs = if (type == Hc2CheckType.PUSH) null else locations
        val input = Hc2CheckInput(
            name = name.trim(),
            type = type,
            target = if (type == Hc2CheckType.PUSH) null else target.trim(),
            params = ps.toParams(type, iniPs),
            intervalSeconds = durationToSeconds(intervalValue, intervalUnit),
            retryIntervalSeconds = durationToSeconds(retryValue, retryUnit),
            retries = retries.toIntOrNull(),
            connectTimeoutSeconds = connectTimeout.toDoubleOrNull(),
            timeoutSeconds = timeout.toDoubleOrNull(),
            invert = invert,
            stabilize = stabilize.toIntOrNull(),
            ipMode = ipMode,
            tags = parseTags(),
            group = group.trim().ifBlank { null },
            description = description.trim().ifBlank { null },
            paused = createPaused,
            critical = critical,
            execution = Hc2Execution.ONLINE,
            locations = locs,
            policy = if ((locs?.size ?: 0) > 1) policy else null,
            notifyDegraded = notifyDegraded,
            notifyDown = notifyDown,
            notifyUp = notifyUp,
            channels = if (useDefaultChannels) emptyList() else selectedChannels.sorted(),
            repeatMinutes = repeatMinutes,
        )
        saving = true
        spinnerText = "Saving healthcheck..."
        scope.launch {
            try {
                val client = IPv64Client.get(ctx)
                val created = ipv64Retrying { client.hc2.checks.create(input) }
                Hc2CheckListCache.invalidate()
                val url = created.push?.url ?: created.push?.hubUrl
                if (type == Hc2CheckType.PUSH && !url.isNullOrBlank()) {
                    pushUrl = url
                } else {
                    navController.popBackStack()
                }
            } catch (e: IPv64Exception) {
                showApiError(e)
            } finally {
                saving = false
            }
        }
    }

    // Minimaler Diff gegen den geladenen Check
    fun buildUpdate(d: Hc2CheckDetail): Hc2CheckUpdate {
        fun <T> diff(new: T?, old: T?): T? = if (new != null && new != old) new else null
        fun text(new: String, old: String?): String? {
            val n = new.trim()
            return if (n == old.orEmpty()) null else n
        }

        val newTags = parseTags()
        val newChannels = if (useDefaultChannels) emptyList() else selectedChannels.sorted()
        val locChanged = locations.toSet() != d.locations.toSet()
        val paramsDirty = ps != iniPs
        return Hc2CheckUpdate(
            name = text(name, d.name),
            target = if (type == Hc2CheckType.PUSH) null else text(target, d.target),
            params = if (paramsDirty) ps.toParams(type, iniPs) else null,
            intervalSeconds = diff(durationToSeconds(intervalValue, intervalUnit), d.intervalSeconds),
            retryIntervalSeconds = diff(durationToSeconds(retryValue, retryUnit), d.retryIntervalSeconds),
            retries = diff(retries.toIntOrNull(), d.retries),
            connectTimeoutSeconds = diff(connectTimeout.toDoubleOrNull(), d.connectTimeoutSeconds),
            timeoutSeconds = diff(timeout.toDoubleOrNull(), d.timeoutSeconds),
            invert = diff(invert, d.invert ?: false),
            stabilize = diff(stabilize.toIntOrNull(), d.stabilize),
            ipMode = diff(ipMode, d.ipMode),
            tags = if (newTags.toSet() != d.tags.toSet() || newTags.size != d.tags.size) newTags else null,
            group = text(group, d.group),
            description = text(description, d.description),
            critical = diff(critical, d.critical ?: false),
            // Standort/Execution nie im PATCH, das übernimmt checks.move()
            policy = if (online && locations.size > 1 && !locChanged) diff(policy, d.policy) else null,
            notifyDegraded = diff(notifyDegraded, d.notifyDegraded ?: false),
            notifyDown = diff(notifyDown, d.notifyDown ?: true),
            notifyUp = diff(notifyUp, d.notifyUp ?: true),
            channels = if (newChannels.toSet() != d.channels.toSet()) newChannels else null,
            repeatMinutes = diff(repeatMinutes, d.repeatMinutes),
        )
    }

    fun saveEdit(d: Hc2CheckDetail, token: String) {
        val update = buildUpdate(d)
        val moveNeeded = online && type != Hc2CheckType.PUSH && locations.toSet() != d.locations.toSet()
        // Die Policy gehört zum neuen Standortsatz und wird nach dem Wechsel gesetzt
        val policyAfterMove = if (moveNeeded && locations.size > 1 && policy != d.policy) policy else null
        saving = true
        spinnerText = "Saving healthcheck..."
        scope.launch {
            try {
                val client = IPv64Client.get(ctx)
                if (update != Hc2CheckUpdate()) {
                    val updated = ipv64Retrying { client.hc2.checks.update(token, update) }
                    detail = updated
                    iniPs = ps.copy(basicPassword = "")
                    ps = iniPs
                    Hc2CheckListCache.invalidate()
                }
                if (moveNeeded) {
                    spinnerText = "Testing new locations…"
                    when (val outcome = Hc2MoveRunner.moveOnline(ctx, token, locations)) {
                        is Hc2MoveOutcome.Applied -> {
                            Hc2CheckListCache.invalidate()
                            if (policyAfterMove != null) {
                                spinnerText = "Saving healthcheck..."
                                ipv64Retrying { client.hc2.checks.update(token, Hc2CheckUpdate(policy = policyAfterMove)) }
                            }
                            finish()
                            return@launch
                        }

                        is Hc2MoveOutcome.Failed -> {
                            showMessage(
                                "Locations not changed",
                                "The test run failed, so the check keeps its old locations. Other changes were saved.\n\n" + outcome.message
                            )
                            return@launch
                        }

                        is Hc2MoveOutcome.TimedOut -> {
                            showMessage(
                                "Location change pending",
                                "The test run did not finish within 60 seconds. Check the healthcheck details later to see whether the locations were changed.",
                                popAfter = true
                            )
                            return@launch
                        }
                    }
                }
                finish()
            } catch (e: IPv64Exception) {
                showApiError(e)
            } finally {
                saving = false
            }
        }
    }

    fun onSave() {
        keyboard?.hide()
        validate()?.let {
            showMessage("Invalid input", it)
            return
        }
        val d = detail
        if (editToken != null && d != null) saveEdit(d, editToken) else if (editToken == null) saveCreate()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = "Close")
                    }
                },
                title = { Text(if (isEdit) "Edit Healthcheck" else "New Healthcheck") },
                modifier = Modifier.statusBarsPadding(),
                actions = {
                    IconButton(onClick = { onSave() }, enabled = !loading && !saving) {
                        Icon(painterResource(R.drawable.save_24px), contentDescription = "Save")
                    }
                }
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(mainPadding)
            .consumeWindowInsets(mainPadding)
    ) { inner ->
        if (!loading && (!isEdit || detail != null)) {
            Column(
                modifier = Modifier
                    .padding(inner)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // --- Grunddaten ---
                FormSectionTitle("Check")
                if (isEdit) {
                    FormTextField("Type", type.label, {}, enabled = false)
                } else {
                    FormDropdown(
                        label = "Type", value = type, options = CREATE_TYPE_ORDER,
                        labelOf = { if (it.creatableOnline) it.label else "${it.label} (requires a node)" },
                        optionEnabled = { it.creatableOnline },
                        onSelect = {
                            if (it != type) {
                                type = it
                                ps = ParamsState.initial(it)
                                iniPs = ps
                            }
                        }
                    )
                    FormHint("Docker checks need a node. Create them at ipv64.net.")
                }
                FormTextField("Name", name, { name = it })
                if (type != Hc2CheckType.PUSH) {
                    FormTextField(
                        "Target", target, { target = it },
                        placeholder = type.targetHint, keyboardType = KeyboardType.Uri
                    )
                } else if (!isEdit) {
                    FormHint("A push URL is created with the check.")
                }

                // --- Typabhängige Parameter ---
                if (type != Hc2CheckType.WHOIS && type != Hc2CheckType.DOCKER) {
                    FormSectionTitle("${type.label} options")
                    HealthcheckParamsEditor(type, ps, { ps = it }, online = online, isEdit = isEdit)
                }

                // --- Zeitplan ---
                FormSectionTitle("Schedule")
                FormHint(
                    if (isEdit) "Clearing a field keeps the current value; it cannot be reset to the default here."
                    else "Empty fields use the server default."
                )
                DurationField(
                    "Interval", intervalValue, intervalUnit,
                    { intervalValue = it }, { intervalUnit = it }, supporting = "Minimum 20 seconds"
                )
                DurationField(
                    "Retry interval", retryValue, retryUnit,
                    { retryValue = it }, { retryUnit = it }, supporting = "Minimum 5 seconds"
                )
                FormNumberField("Retries (0-10)", retries, { retries = it })
                FormNumberField("Timeout (seconds)", timeout, { timeout = it }, decimal = true)
                FormNumberField("Connect timeout (seconds)", connectTimeout, { connectTimeout = it }, decimal = true)
                FormNumberField(
                    "Stabilize (1-20)", stabilize, { stabilize = it },
                    supporting = "Measurements until the state changes"
                )
                FormSwitchRow("Invert result", invert, { invert = it })
                FormDropdown(
                    label = "IP mode", value = ipMode, options = (if (isEdit && detail?.ipMode != null) emptyList() else listOf(null)) + Hc2IpMode.entries,
                    labelOf = { it?.value ?: "Default" }, onSelect = { ipMode = it }
                )

                // --- Ausführung ---
                if (type != Hc2CheckType.PUSH) {
                    FormSectionTitle("Execution")
                    if (isNodeCheck) {
                        val nodeLabel = detail?.nodeName ?: detail?.nodeId ?: "a node"
                        FormHint("Runs on node $nodeLabel. Change it at ipv64.net.")
                    } else {
                        val limitText = if (maxLocations == Int.MAX_VALUE) "" else " (up to $maxLocations)"
                        Text("Locations$limitText", style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            account?.locations.orEmpty().forEach { loc ->
                                val code = loc.code ?: return@forEach
                                val selected = code in locations
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        locations = when {
                                            selected -> locations - code
                                            locations.size < maxLocations -> locations + code
                                            else -> locations
                                        }
                                    },
                                    label = { Text(loc.name?.takeIf { it.isNotBlank() } ?: code) }
                                )
                            }
                        }
                        if (locations.size > 1) {
                            FormDropdown(
                                label = "Overall rule", value = policy,
                                options = (if (isEdit && detail?.policy != null) emptyList() else listOf(null)) + Hc2Policy.entries,
                                labelOf = { p ->
                                    when (p) {
                                        null -> "Default"
                                        Hc2Policy.ALL -> "Down if all locations fail"
                                        Hc2Policy.ANY -> "Down if any location fails"
                                        Hc2Policy.MAJORITY -> "Down if most locations fail"
                                    }
                                },
                                onSelect = { policy = it }
                            )
                        }
                        if (isEdit) FormHint("Changing locations runs a test measurement first.")
                    }
                }

                // --- Benachrichtigungen ---
                FormSectionTitle("Notifications")
                FormSwitchRow("Notify when down", notifyDown, { notifyDown = it })
                FormSwitchRow("Notify when back up", notifyUp, { notifyUp = it })
                FormSwitchRow(
                    "Notify when degraded", notifyDegraded, { notifyDegraded = it },
                    supporting = "Also notify on partial failure"
                )
                FormSwitchRow(
                    "Critical", critical, { critical = it },
                    supporting = "Ignores quiet hours"
                )
                FormDropdown(
                    label = "Repeat reminder",
                    value = repeatMinutes,
                    options = (REPEAT_OPTIONS + repeatMinutes).distinct().filter { !(isEdit && detail?.repeatMinutes != null && it == null) },
                    labelOf = { if (it == null) "Default" else repeatMinutesLabel(it) },
                    onSelect = { repeatMinutes = it }
                )
                HealthcheckChannelPicker(
                    useDefault = useDefaultChannels,
                    onUseDefaultChange = { useDefaultChannels = it },
                    channels = channels,
                    types = channelTypes,
                    selected = selectedChannels,
                    onToggle = { id ->
                        selectedChannels = if (id in selectedChannels) selectedChannels - id else selectedChannels + id
                    }
                )

                // --- Organisation ---
                FormSectionTitle("Organisation")
                FormTextField("Tags", tags, { tags = it }, supporting = "Comma-separated")
                FormTextField("Group", group, { group = it })
                FormTextField(
                    "Description", description, { description = it },
                    singleLine = false, minLines = 2
                )
                if (!isEdit) FormSwitchRow(
                    "Create paused", createPaused, { createPaused = it },
                    supporting = "No measurements until you resume the check"
                )
            }
        }
    }

    if (loading || saving) {
        SpinnerDialog(spinnerText = if (loading) "Loading..." else spinnerText)
    }

    if (showError) {
        ErrorDialog(
            onDismissRequest = { showError = false },
            onConfirmation = {
                showError = false
                if (popAfterError) safePop()
            },
            dialogTitle = errorTitle,
            dialogText = errorText
        )
    }

    if (showRequest) {
        RequestDialogs(
            onDismissRequest = { showRequest = false },
            onConfirmation = {
                showRequest = false
                if (popAfterRequest) safePop()
            },
            request = requestType
        )
    }

    pushUrl?.let { url ->
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(painterResource(R.drawable.content_copy_24px), contentDescription = null) },
            title = { Text("Push URL") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Call this URL from your job or service to report that it is alive.")
                    Text(url, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pushUrl = null
                    navController.popBackStack()
                }) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = { copySensitive(ctx, url) }) { Text("Copy") }
            }
        )
    }
}

@Composable
private fun FormHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
