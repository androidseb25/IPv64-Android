package de.rpicloud.ipv64net.main.views.common

import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph
import androidx.navigation.NavBackStackEntry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.collectAsState
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.IPV64_PLANS_URL
import de.rpicloud.ipv64net.helper.IPv64Context
import de.rpicloud.ipv64net.helper.copyText
import de.rpicloud.ipv64net.helper.findActivity
import de.rpicloud.ipv64net.helper.isPlanRestricted
import de.rpicloud.ipv64net.helper.openUrl
import de.rpicloud.ipv64net.helper.planRestrictionText
import de.rpicloud.ipv64net.helper.toError
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.main.views.ChannelDialogHost
import de.rpicloud.ipv64net.main.views.ChannelDialogState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64Exception
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// =============================================================================================
// Gerüst
// =============================================================================================

/**
 * Ob der aufrufende Screen einen Zurück-Pfeil zeigen soll. Ermittelt über den eigenen Back-Stack-Eintrag
 * (LocalLifecycleOwner ist im NavHost der NavBackStackEntry) und den beobachteten Back-Stack:
 * previousBackStackEntry/currentDestination sind nicht beobachtbar und liefern während Übergängen
 * (Tab-Wechsel, Zurück-Wischen) veraltete Werte. Tab-Wurzeln haben nie einen Zurück-Pfeil.
 */
@Composable
fun rememberCanGoBack(navController: NavHostController): Boolean {
    val ownEntry = LocalLifecycleOwner.current as? NavBackStackEntry
    val backStack by navController.currentBackStack.collectAsState()
    val ownRoute = ownEntry?.destination?.route
    if (ownRoute != null && ownRoute in Tabs.tabList.map { Tabs.getRoute(it) }) return false
    val screens = backStack.filter { it.destination !is NavGraph }
    return if (ownEntry != null) screens.indexOfFirst { it.id == ownEntry.id } > 0
    else navController.previousBackStackEntry != null
}

/**
 * Standard-Gerüst eines Screens: TopAppBar (Titel, optional Zurück-Pfeil, Aktionen), optionaler FAB und Inhalt.
 * Wendet mainPadding (untere Leiste) und das innere Scaffold-Padding bereits an; [content] läuft in einer Box mit
 * fillMaxSize.
 *
 * @param showBack null = automatisch: Zurück-Pfeil nur, wenn es einen vorherigen Back-Stack-Eintrag gibt
 *   (Tab-Wurzeln haben keinen). true/false erzwingt das Verhalten.
 * @param onBack eigene Zurück-Aktion; Standard ist popBackStack() (höchstens einmal pro Screen, gegen Doppeltippen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    navController: NavHostController,
    mainPadding: PaddingValues,
    showBack: Boolean? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    val canGoBack = rememberCanGoBack(navController)
    val back = showBack ?: canGoBack
    var popped by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (back) {
                        IconButton(onClick = {
                            if (onBack != null) onBack()
                            else if (!popped) {
                                popped = true
                                navController.popBackStack()
                            }
                        }) {
                            Icon(painter = painterResource(id = R.drawable.arrow_back_24px), contentDescription = "Back")
                        }
                    }
                },
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = actions,
                modifier = Modifier.statusBarsPadding()
            )
        },
        floatingActionButton = fab,
        modifier = Modifier
            .fillMaxSize()
            .padding(mainPadding)
            .consumeWindowInsets(mainPadding)  // avoids double insets
    ) { inner ->
        Box(
            modifier = Modifier
                .padding(inner)
                .consumeWindowInsets(inner)
                .fillMaxSize(),
            content = content
        )
    }
}

/**
 * LazyColumn mit Pull-to-Refresh (füllt den verfügbaren Platz).
 * @param refreshing Indikator sichtbar (nur für Pull-to-Refresh setzen, sonst bleibt der Spinner-Dialog zuständig).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableList(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    content: LazyListScope.() -> Unit
) {
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            content = content
        )
    }
}

// =============================================================================================
// Anfragen: Spinner, Fehlerdialoge, Plan-Sperre
// =============================================================================================

/**
 * Zustand für Netzwerkanfragen eines Screens: Spinner-Dialog, Fehler-/Hinweisdialoge (über [ChannelDialogState])
 * und Plan-Sperre ([notAvailable]). Mit [rememberRequestState] anlegen und [RequestStateHost] einmal pro Screen
 * aufrufen.
 *
 * ```
 * val req = rememberRequestState()
 * val scope = rememberCoroutineScope()
 * fun load(force: Boolean = false) = req.launch(scope, retry = { load(true) }, planGate = true) {
 *     items = MyCache.get(ctx, force) { it.failover.list() }
 * }
 * ...
 * RequestStateHost(req)
 * ```
 */
@Stable
class RequestState internal constructor(
    /** Darunterliegender Dialogzustand (z. B. für eigene Hinweise mit [ChannelDialogState.message]). */
    val dialogs: ChannelDialogState
) {
    /** Text des Spinner-Dialogs, null = kein Spinner. */
    var spinnerText by mutableStateOf<String?>(null)
        private set

    /** Anzahl laufender [launch]-Jobs. */
    var running by mutableIntStateOf(0)
        private set

    /** true, solange mindestens ein [launch]-Job läuft (z. B. Buttons deaktivieren, Pull-to-Refresh-Indikator). */
    val isBusy: Boolean get() = running > 0

    /**
     * Grund, wenn eine Anfrage mit planGate = true wegen des Plans abgelehnt wurde; dann statt des Inhalts
     * [NotAvailableView] anzeigen. Wird beim nächsten erfolgreichen planGate-Aufruf nicht automatisch
     * zurückgesetzt, dafür [notAvailable] = null setzen.
     */
    var notAvailable by mutableStateOf<String?>(null)

    private var spinnerCount = 0

    /**
     * Startet [block] in [scope]. Fängt nur IPv64Exception (CancellationException und andere Fehler laufen durch).
     * Der Spinner wird im finally zurückgesetzt.
     *
     * @param spinner Text des Spinner-Dialogs; null = kein Spinner (z. B. Pull-to-Refresh oder stilles Nachladen).
     * @param retry bietet im Fehlerdialog "Retry" an.
     * @param planGate true: Plan-Ablehnung (isPlanRestricted) setzt [notAvailable] statt einen Dialog zu zeigen.
     *   Nur für Lade-Anfragen verwenden.
     * @param context Fehlerkontext für toError (neue Funktionen: Other; Update für 429-Cooldown-Text).
     * @param onError eigene Fehlerbehandlung (z. B. 409/422 → Feldfehler); true = erledigt, kein Dialog.
     * @param onFinally läuft nach dem Block (Erfolg oder Fehler), z. B. refreshing = false.
     */
    fun launch(
        scope: CoroutineScope,
        spinner: String? = "Loading...",
        retry: (() -> Unit)? = null,
        planGate: Boolean = false,
        context: IPv64Context = IPv64Context.Other,
        onError: (IPv64Exception) -> Boolean = { false },
        onFinally: () -> Unit = {},
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        running++
        if (spinner != null) {
            spinnerCount++
            spinnerText = spinner
        }
        return scope.launch {
            try {
                block()
            } catch (e: IPv64Exception) {
                if (!onError(e)) {
                    if (planGate && e.isPlanRestricted()) notAvailable = e.planRestrictionText()
                    else error(e, retry, context)
                }
            } finally {
                running--
                if (spinner != null) {
                    spinnerCount--
                    if (spinnerCount <= 0) {
                        spinnerCount = 0
                        spinnerText = null
                    }
                }
                onFinally()
            }
        }
    }

    /** Fehler als Dialog anzeigen (toError(context)); [retry] bietet "Retry" an. */
    fun error(e: IPv64Exception, retry: (() -> Unit)? = null, context: IPv64Context = IPv64Context.Other) {
        dialogs.error(e.toError(context), retry)
    }

    /** Hinweisdialog (Text wird HTML-kodiert). */
    fun message(title: String, text: String, @DrawableRes icon: Int = R.drawable.info_24px, onClose: (() -> Unit)? = null) {
        dialogs.message(title, text, icon, onClose)
    }
}

/** Neuer [RequestState] für den aktuellen Screen. */
@Composable
fun rememberRequestState(): RequestState = remember { RequestState(ChannelDialogState()) }

/** Zeigt Spinner und Dialoge des [RequestState]; einmal pro Screen (außerhalb des Scaffold-Inhalts) aufrufen. */
@Composable
fun RequestStateHost(state: RequestState) {
    state.spinnerText?.let { SpinnerDialog(spinnerText = it) }
    ChannelDialogHost(state.dialogs)
}

// =============================================================================================
// Hubs
// =============================================================================================

/**
 * Eintrag eines Hubs; Antippen navigiert zu [tab].
 * @param title Standard: Tabs.getLabel(tab).
 * @param subtitle Beschreibung oder Status (z. B. "Not available on your plan").
 * @param icon Standard: Tabs.getUnSelectedIcon(tab).
 * @param badge kurzer Wert rechts (z. B. "2 / 5"); null = keiner.
 * @param enabled false = ausgegraut und nicht antippbar.
 * @param group Überschrift; aufeinanderfolgende Einträge mit gleicher Gruppe stehen unter einer [SectionHeader].
 */
data class HubSection(
    val tab: Tab,
    val title: String = Tabs.getLabel(tab),
    val subtitle: String? = null,
    @param:DrawableRes val icon: Int = Tabs.getUnSelectedIcon(tab),
    val badge: String? = null,
    val enabled: Boolean = true,
    val group: String? = null
)

/**
 * Hub-Screen (Tab-Wurzel): Kopfbereich ([header], LazyListScope) und darunter die [sections] als Liste.
 * Pull-to-Refresh nur, wenn [onRefresh] gesetzt ist.
 */
@Composable
fun HubScreen(
    title: String,
    navController: NavHostController,
    mainPadding: PaddingValues,
    sections: List<HubSection>,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    header: LazyListScope.() -> Unit = {},
    footer: LazyListScope.() -> Unit = {}
) {
    ScreenScaffold(title = title, navController = navController, mainPadding = mainPadding, actions = actions) {
        val body: LazyListScope.() -> Unit = {
            header()
            var lastGroup: String? = null
            sections.forEachIndexed { index, section ->
                if (section.group != null && (index == 0 || section.group != lastGroup)) {
                    item(key = "group_${index}_${section.group}") { SectionHeader(section.group) }
                }
                lastGroup = section.group
                item(key = "section_${section.tab.name}") {
                    HubSectionRow(section) {
                        // launchSingleTop: Doppeltippen legt den Screen nicht zweimal auf den Stack
                        navController.navigate(Tabs.getRoute(section.tab)) { launchSingleTop = true }
                    }
                }
            }
            footer()
        }
        if (onRefresh != null) {
            RefreshableList(refreshing = refreshing, onRefresh = onRefresh, verticalArrangement = Arrangement.spacedBy(8.dp), content = body)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = body
            )
        }
    }
}

/** Eine Zeile eines Hubs (Karte mit Icon, Titel, Untertitel, Badge, Pfeil). */
@Composable
fun HubSectionRow(section: HubSection, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = section.enabled,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (section.enabled) 1f else 0.5f)
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(painter = painterResource(id = section.icon), contentDescription = null) },
            headlineContent = { Text(section.title, fontWeight = FontWeight.SemiBold) },
            supportingContent = section.subtitle?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    section.badge?.let {
                        Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Icon(painter = painterResource(id = R.drawable.chevron_right_24px), contentDescription = null)
                }
            }
        )
    }
}

// =============================================================================================
// Bausteine
// =============================================================================================

/** Abschnittsüberschrift (titleSmall, Primärfarbe). */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

/**
 * Bezeichnung/Wert-Zeile.
 * @param copyable Kopier-Button anzeigen.
 * @param sensitive Wert verdeckt (mit Anzeigen-Umschalter) und als sensibel kopieren; für Tokens/Keys/Update-URLs.
 * @param monospace Wert in Festbreitenschrift (Tokens, IPs, Hashes).
 */
@Composable
fun InfoRow(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    copyable: Boolean = false,
    sensitive: Boolean = false,
    monospace: Boolean = false
) {
    val ctx = LocalContext.current
    var revealed by rememberSaveable(label) { mutableStateOf(false) }
    val text = value?.takeIf { it.isNotEmpty() }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = when {
                    text == null -> "–"
                    sensitive && !revealed -> "••••••••"
                    else -> text
                },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (monospace) FontFamily.Monospace else null
            )
        }
        if (text != null && sensitive) {
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    painter = painterResource(id = if (revealed) R.drawable.visibility_off_24px else R.drawable.visibility_24px),
                    contentDescription = if (revealed) "Hide" else "Show"
                )
            }
        }
        if (text != null && copyable) {
            IconButton(onClick = { copyText(ctx, label, text, sensitive) }) {
                Icon(painter = painterResource(id = R.drawable.content_copy_24px), contentDescription = "Copy $label")
            }
        }
    }
}

/** Kleiner farbiger Status-Chip; [color] z. B. colorResource(R.color.ipv64_green). */
@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.18f),
        contentColor = color,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

/** Hinweis für leere Listen, optional mit Aktion (z. B. "Add"). */
@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        if (actionLabel != null && onAction != null) {
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * Anzeige "im Plan nicht enthalten" mit Button zur Preisübersicht.
 * @param feature Name der Funktion (z. B. PlanFeature.CDN.label oder "Offline pages").
 * @param reason optionaler Servertext (RequestState.notAvailable).
 */
@Composable
fun NotAvailableView(feature: String, reason: String? = null, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.lock_24px),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "$feature is not available on your plan",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        if (!reason.isNullOrBlank()) {
            Text(
                reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Button(onClick = { openUrl(ctx, IPV64_PLANS_URL) }) { Text("View plans") }
    }
}

/**
 * Bestätigungsdialog.
 * @param destructive Bestätigen-Button rot (Löschen, Zurücksetzen, ...).
 * @param requireTyping wenn gesetzt, muss dieser Text exakt eingetippt werden, bevor Bestätigen aktiv wird
 *   (z. B. der Domainname bei "Reset").
 * Der Dialog schließt sich nicht selbst: [onConfirm] bzw. [onDismiss] müssen den Anzeige-Zustand zurücksetzen.
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "Confirm",
    destructive: Boolean = false,
    requireTyping: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var typed by remember { mutableStateOf("") }
    val canConfirm = requireTyping == null || typed.trim() == requireTyping
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(id = if (destructive) R.drawable.warning_24px else R.drawable.info_24px),
                contentDescription = null
            )
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text)
                if (requireTyping != null) {
                    Text("Type \"$requireTyping\" to confirm.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canConfirm) {
                Text(
                    confirmLabel,
                    color = if (!canConfirm) Color.Unspecified
                    else if (destructive) colorResource(R.color.ipv64_red) else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Zeigt ein Geheimnis genau einmal an (z. B. neuer API-Key). Bildschirmaufnahmen sind gesperrt (FLAG_SECURE),
 * Kopieren ist als sensibel markiert, "Done" erst nach dem Haken "I have stored it". Nicht per Zurück/außen
 * schließbar. Das Geheimnis nie loggen und nach [onDone] aus dem State entfernen.
 */
@Composable
fun SecretOnceDialog(title: String, secret: String, warning: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var stored by remember { mutableStateOf(false) }
    SecureWindowEffect()
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(painter = painterResource(id = R.drawable.key_24px), contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(warning, style = MaterialTheme.typography.bodyMedium)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Bewusst nicht markierbar: das Kopieren der Auswahl wäre nicht als sensibel markiert
                    Text(
                        secret,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                OutlinedButton(onClick = { copyText(ctx, title, secret, sensitive = true) }) {
                    Icon(painterResource(R.drawable.content_copy_24px), null, Modifier.size(18.dp))
                    Text(" Copy")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = stored, onCheckedChange = { stored = it })
                    Text("I have stored it", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone, enabled = stored) { Text("Done") } },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            securePolicy = SecureFlagPolicy.SecureOn
        )
    )
}

/**
 * Sperrt Screenshots/Bildschirmaufnahmen und die Vorschau in "Letzte Apps" (FLAG_SECURE), solange der aufrufende
 * Composable sichtbar ist (z. B. Passwort-Check, Honeypot-Token). Mehrfach gleichzeitig nutzbar (Zähler).
 * Dialoge haben eigene Fenster: dort zusätzlich DialogProperties(securePolicy = SecureFlagPolicy.SecureOn) setzen.
 */
@Composable
fun SecureWindowEffect() {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val window = ctx.findActivity()?.window
        if (window != null) SecureWindowCounter.acquire(window)
        onDispose { if (window != null) SecureWindowCounter.release(window) }
    }
}

// Zählt aktive Nutzer, damit FLAG_SECURE erst entfernt wird, wenn kein Screen/Dialog es mehr braucht
private object SecureWindowCounter {
    private var count = 0

    fun acquire(window: android.view.Window) {
        if (count++ == 0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun release(window: android.view.Window) {
        count = (count - 1).coerceAtLeast(0)
        if (count == 0) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/**
 * Liste von Einträgen mit [EmptyState], wenn leer; Hilfsfunktion für LazyListScope.
 * [emptyKey] muss bei mehreren Aufrufen in derselben Liste eindeutig sein.
 */
fun <T> LazyListScope.itemsOrEmpty(
    items: List<T>,
    loaded: Boolean,
    emptyText: String,
    key: ((T) -> Any)? = null,
    emptyKey: Any = "empty_state",
    itemContent: @Composable (T) -> Unit
) {
    if (loaded && items.isEmpty()) {
        item(key = emptyKey) { EmptyState(emptyText) }
    } else {
        items(items = items, key = key) { itemContent(it) }
    }
}
