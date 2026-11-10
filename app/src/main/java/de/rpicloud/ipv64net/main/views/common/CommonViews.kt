package de.rpicloud.ipv64net.main.views.common

import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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
            // Fester Anker oben: später eingefügte Kopf-Einträge (z. B. Status-Kacheln) schieben die Liste sonst aus dem Blick
            item(key = "hub_top") {}
            header()
            // Aufeinanderfolgende Einträge mit gleicher Gruppe bilden EINE Fläche (wie Android-Einstellungen)
            val chunks = mutableListOf<List<HubSection>>()
            sections.forEach { s -> if (chunks.lastOrNull()?.first()?.group == s.group) chunks[chunks.lastIndex] = chunks.last() + s else chunks += listOf(s) }
            chunks.forEach { chunk ->
                val first = chunk.first()
                item(key = "group_${first.tab.name}") {
                    ListGroup(title = first.group) {
                        chunk.forEachIndexed { i, section ->
                            if (i > 0) GroupDivider(56.dp)
                            ListRow(
                                title = section.title,
                                subtitle = section.subtitle,
                                icon = section.icon,
                                badge = section.badge,
                                enabled = section.enabled,
                                centerIcon = true,
                                // launchSingleTop: Doppeltippen legt den Screen nicht zweimal auf den Stack
                                onClick = { navController.navigate(Tabs.getRoute(section.tab)) { launchSingleTop = true } }
                            )
                        }
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

// =============================================================================================
// Bausteine
// =============================================================================================

/**
 * Gruppe von Zeilen auf einer gemeinsamen Fläche (surfaceContainer, shapes.large) mit optionaler [SectionHeader]-Überschrift.
 * Zeilen mit [ListRow] einfügen, dazwischen [GroupDivider].
 */
@Composable
fun ListGroup(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier) {
        title?.let { SectionHeader(it, Modifier.padding(start = 4.dp, bottom = 6.dp)) }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

/** Trennlinie zwischen Zeilen einer [ListGroup]; [inset] = linker Einzug (56.dp bei Zeilen mit Icon). */
@Composable
fun GroupDivider(inset: Dp = 16.dp) {
    HorizontalDivider(modifier = Modifier.padding(start = inset), color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * Zeile einer [ListGroup] (M3 ListItem, transparent).
 * @param icon Drawable links (onSurfaceVariant); [leading] hat Vorrang.
 * @param trailing eigener Inhalt rechts (z. B. [StatusChip]); danach folgen [badge] und Pfeil.
 * @param badge kurzer Wert rechts in Primärfarbe.
 * @param enabled false = ausgegraut (alpha 0.38) und nicht antippbar.
 * @param titleColor Farbe des Titels; Unspecified = Standard. [iconTint] analog für das Icon (Standard onSurfaceVariant),
 *   z. B. beide colorScheme.error für "Logout".
 * @param monospaceTitle / [monospaceSubtitle] Festbreitenschrift (IPs, Record-Inhalte, Token-Hinweise).
 * @param showChevron Pfeil rechts; null (Standard) = nur wenn [onClick] gesetzt ist. (Kotlin erlaubt keinen Default, der den später deklarierten onClick referenziert.)
 * @param onClick null = nicht klickbar.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    badge: String? = null,
    enabled: Boolean = true,
    showChevron: Boolean? = null,
    subtitleMaxLines: Int = 2,
    titleColor: Color = Color.Unspecified,
    iconTint: Color = Color.Unspecified,
    monospaceTitle: Boolean = false,
    monospaceSubtitle: Boolean = false,
    // ListItem setzt das Icon bei umbrechendem Untertitel nach oben; true hält es immer mittig
    centerIcon: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val chevron = showChevron ?: (onClick != null)
    val hasTrailing = trailing != null || badge != null || chevron
    val lead: (@Composable () -> Unit)? = leading ?: icon?.let {
        {
            Icon(
                painter = painterResource(id = it),
                contentDescription = null,
                tint = iconTint.takeIf { c -> c.isSpecified } ?: MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    val headline: @Composable () -> Unit = {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = titleColor,
            fontFamily = if (monospaceTitle) FontFamily.Monospace else null
        )
    }
    val supporting: (@Composable () -> Unit)? = subtitle?.let {
        {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = if (monospaceSubtitle) FontFamily.Monospace else null,
                maxLines = subtitleMaxLines
            )
        }
    }
    val inline = centerIcon && lead != null
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = if (inline) null else lead,
        headlineContent = if (!inline) headline else {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    lead!!()
                    Spacer(Modifier.width(16.dp))
                    Column {
                        headline()
                        supporting?.invoke()
                    }
                }
            }
        },
        supportingContent = if (inline) null else supporting,
        trailingContent = if (!hasTrailing) null else {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailing?.let {
                        it()
                        if (badge != null || chevron) Spacer(Modifier.width(8.dp))
                    }
                    badge?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                    if (chevron) {
                        Icon(painter = painterResource(id = R.drawable.chevron_right_24px), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        modifier = modifier
            .heightIn(min = 56.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
    )
}

/**
 * Abgerundetes Suchfeld mit Lupe.
 * @param onSearch gesetzt: Absende-Button rechts (aktiv bei nicht leerem [value]) und IME-Aktion "Suchen";
 *   null: stattdessen Löschen-Button (x), solange [value] nicht leer ist.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    submitContentDescription: String = "Search"
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(painterResource(R.drawable.search_24px), contentDescription = null) },
        trailingIcon = when {
            onSearch != null -> ({
                IconButton(onClick = onSearch, enabled = value.isNotBlank()) {
                    Icon(painterResource(R.drawable.chevron_right_24px), contentDescription = submitContentDescription)
                }
            })
            value.isNotBlank() -> ({
                IconButton(onClick = { onValueChange("") }) {
                    Icon(painterResource(R.drawable.close_24px), contentDescription = "Clear")
                }
            })
            else -> null
        },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        shape = MaterialTheme.shapes.extraLarge,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Horizontal scrollbare Reihe von [FilterChip]s (Einfachauswahl über [selected]).
 * @param trailingContent zweiter Filtersatz in DERSELBEN Reihe: wird nach einem kleinen vertikalen Divider angehängt
 *   (Chips darin ebenfalls mit [FilterChip] bauen).
 */
@Composable
fun <T> FilterChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
        if (trailingContent != null) {
            VerticalDivider(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.outlineVariant)
            trailingContent()
        }
    }
}

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
 * @param valueColor Farbe des Werts; Unspecified (Standard) = onSurface.
 */
@Composable
fun InfoRow(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    copyable: Boolean = false,
    sensitive: Boolean = false,
    monospace: Boolean = false,
    valueColor: Color = Color.Unspecified
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
                style = MaterialTheme.typography.bodyLarge,
                color = valueColor.takeIf { it.isSpecified } ?: MaterialTheme.colorScheme.onSurface,
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

/** Innenabstand für [InfoRow]s (und eigene Inhalte) direkt in einer [ListGroup]. */
val GroupRowPadding: Modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)

/**
 * Eine Tatsache für [InfoGroup]/[InfoRows]; Parameter wie bei [InfoRow].
 * @param onClick gesetzt = ganze Zeile antippbar.
 */
data class InfoItem(
    val label: String,
    val value: String?,
    val copyable: Boolean = false,
    val sensitive: Boolean = false,
    val monospace: Boolean = false,
    val valueColor: Color = Color.Unspecified,
    val onClick: (() -> Unit)? = null
)

/** [InfoRow]s mit Gruppen-Einzug und [GroupDivider] dazwischen; innerhalb einer [ListGroup] aufrufen (z. B. mit weiterem Inhalt). */
@Composable
fun InfoRows(rows: List<InfoItem>) {
    rows.forEachIndexed { i, r ->
        if (i > 0) GroupDivider()
        InfoRow(
            r.label, r.value,
            modifier = (if (r.onClick != null) Modifier.clickable(onClick = r.onClick) else Modifier).then(GroupRowPadding),
            copyable = r.copyable, sensitive = r.sensitive, monospace = r.monospace, valueColor = r.valueColor
        )
    }
}

/**
 * Fakten als [ListGroup] aus [InfoRow]s.
 * @param hideEmpty true = Zeilen ohne Wert weglassen (und bei nur leeren Zeilen gar nichts zeigen); sonst steht "–".
 */
@Composable
fun InfoGroup(title: String?, rows: List<InfoItem>, modifier: Modifier = Modifier, hideEmpty: Boolean = false) {
    val shown = if (hideEmpty) rows.filter { !it.value.isNullOrBlank() } else rows
    if (shown.isEmpty()) return
    ListGroup(title = title, modifier = modifier) { InfoRows(shown) }
}

/** Hinweis- oder Leer-Text innerhalb einer [ListGroup] ([error] = Fehlerfarbe). */
@Composable
fun GroupText(text: String, modifier: Modifier = Modifier, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.then(GroupRowPadding)
    )
}

/**
 * Formularabschnitt: optionale Überschrift + eine Fläche ([ListGroup]) mit 16 dp Innenabstand und 8 dp zwischen den Feldern
 * (auch für frei gestaltete Inhalte wie Diagramme).
 */
@Composable
fun FormSection(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ListGroup(title, modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
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

/** Kleine Kennzahl-Kachel (Wert über Beschriftung); [valueColor] nur zusätzlich, die Beschriftung nennt die Bedeutung. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor.takeIf { it.isSpecified } ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Aktions-Kachel (Icon im Kreis über kurzem Text); mehrere mit Modifier.weight(1f) in einer Row. */
@Composable
fun ActionTile(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.heightIn(min = 88.dp)
    ) {
        val alpha = if (enabled) 1f else 0.38f
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = alpha), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(icon), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 2,
                textAlign = TextAlign.Center
            )
        }
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
 * Einheitliches Bottom Sheet für Formulare und Auswahlen (statt AlertDialog). Bestätigungen/Löschen bleiben [ConfirmDialog].
 * Schließt sich nicht selbst: [onConfirm] bzw. [onDismiss] setzen den Anzeige-Zustand zurück.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    confirmLabel: String? = null,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    onConfirm: (() -> Unit)? = null,
    // false: Wischen, Zurück und Tippen daneben schließen nicht (z. B. während eine Anfrage läuft)
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val canDismiss by rememberUpdatedState(dismissible)
    ModalBottomSheet(
        onDismissRequest = { if (canDismiss) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || canDismiss }),
        sheetGesturesEnabled = dismissible,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = dismissible, shouldDismissOnClickOutside = dismissible)
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
            if (onConfirm != null && confirmLabel != null) {
                Button(
                    onClick = onConfirm, enabled = confirmEnabled, modifier = Modifier.fillMaxWidth(),
                    colors = if (destructive) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError
                    ) else ButtonDefaults.buttonColors()
                ) { Text(confirmLabel) }
            }
        }
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

/** Abstand nach einer [groupedRows]-Gruppe; die LazyColumn muss dafür verticalArrangement = spacedBy(0.dp) nutzen. */
val GroupGap = 16.dp

/** Item mit [GroupGap] Abstand darunter: für Screens mit [groupedRows], deren LazyColumn spacedBy(0.dp) nutzt. */
fun LazyListScope.spacedItem(key: Any? = null, content: @Composable () -> Unit) {
    item(key = key) { Column(Modifier.padding(bottom = GroupGap)) { content() } }
}

/**
 * Wie [ListGroup], aber je Zeile ein eigenes Lazy-Item (nur sichtbare Zeilen werden komponiert/laden Details).
 * Erste Zeile oben, letzte unten gerundet, dazwischen [GroupDivider]. Die LazyColumn darf KEINEN Item-Abstand haben
 * (spacedBy(0.dp)), sonst reißt die Gruppe auseinander; der Abstand nach der Gruppe ist [GroupGap].
 * [key] null = Positions-Key; sonst muss er in der Liste eindeutig sein.
 */
fun <T> LazyListScope.groupedRows(
    items: List<T>,
    key: ((T) -> Any)? = null,
    title: String? = null,
    // Eindeutiger Header-Key, wenn Titel doppelt vorkommen können (z. B. gleichnamige Nodes)
    headerKey: String? = null,
    row: @Composable (T) -> Unit
) {
    if (items.isEmpty()) return
    title?.let { item(key = "group_header_${headerKey ?: it}") { SectionHeader(it, Modifier.padding(start = 4.dp, bottom = 6.dp)) } }
    itemsIndexed(items, key = key?.let { k -> { _: Int, t: T -> k(t) } }) { i, t ->
        val large = MaterialTheme.shapes.large
        val none = CornerSize(0.dp)
        val last = i == items.lastIndex
        val shape = large.copy(
            topStart = if (i == 0) large.topStart else none,
            topEnd = if (i == 0) large.topEnd else none,
            bottomStart = if (last) large.bottomStart else none,
            bottomEnd = if (last) large.bottomEnd else none
        )
        Column(Modifier.padding(bottom = if (last) GroupGap else 0.dp)) {
            Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column {
                    if (i > 0) GroupDivider()
                    row(t)
                }
            }
        }
    }
}

/**
 * Listen-Screens: alle Einträge als [ListRow]s in EINER Gruppe ([groupedRows], je Zeile ein Lazy-Item);
 * leer und geladen → [EmptyState]. [key] ist nur Namensgeber für den Leer-Key; [itemKey] optional (sonst Positions-Key).
 * Die LazyColumn braucht spacedBy(0.dp) (siehe [groupedRows]).
 */
fun <T> LazyListScope.groupedItemsOrEmpty(
    items: List<T>,
    loaded: Boolean,
    emptyText: String,
    key: Any,
    itemKey: ((T) -> Any)? = null,
    row: @Composable (T) -> Unit
) {
    if (loaded && items.isEmpty()) {
        item(key = "empty_$key") { EmptyState(emptyText) }
    } else {
        groupedRows(items, itemKey, row = row)
    }
}
