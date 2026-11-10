package de.rpicloud.ipv64net.main.views.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.DomainCache
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.loadDomains
import de.rpicloud.ipv64net.helper.loadHc2Checks
import de.rpicloud.ipv64net.helper.toError
import de.rpicloud.ipv64net.main.views.Hc2StateBadge
import de.rpicloud.ipv64net.main.views.inlineText
import de.rpicloud.ipv64net.models.Hc2StateUi
import de.rpicloud.ipv64net.models.effectiveKind
import de.rpicloud.ipv64net.models.label
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.Domain
import de.sr.ipv64.model.DomainKind
import de.sr.ipv64.model.Hc2Check
import de.sr.ipv64.model.Record
import de.sr.ipv64.model.RecordType
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// =============================================================================================
// Anzeige-Hilfen für Check-Tokens
// =============================================================================================

/** Anzeige für einen Token ohne passenden Check (z. B. klassischer Healthcheck oder fremder Token). */
fun unknownCheckLabel(token: String): String = "Unknown check …" + token.takeLast(4)

/** Name des Checks zu [token] aus dieser Liste, sonst [unknownCheckLabel]. */
fun List<Hc2Check>.labelForToken(token: String): String =
    firstOrNull { it.token == token }?.name?.takeIf { it.isNotBlank() } ?: unknownCheckLabel(token)

/** Anzeigename eines Records: FQDN, sonst prefix.domain bzw. domain. */
fun Record.displayName(domain: String? = this.domain): String =
    fqdn?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(prefix?.takeIf { it.isNotBlank() && it != "@" }, domain?.takeIf { it.isNotBlank() })
            .joinToString(".")
            .ifEmpty { "Record #${id ?: "?"}" }

// =============================================================================================
// Healthcheck-Auswahl (hc2)
// =============================================================================================

/**
 * Auswahl von hc2-Checks (Tokens). Lädt die Liste über loadHc2Checks (gecacht, eine Anfrage).
 *
 * - [initial]: bereits gewählte Tokens, Reihenfolge bleibt erhalten. Tokens, die in der Liste fehlen, werden als
 *   "Unknown check …abcd" angezeigt und bleiben gewählt, bis der Nutzer sie abwählt.
 * - [multi]: Mehrfachauswahl (Checkboxen), sonst Einzelauswahl (Radio; höchstens ein Token im Ergebnis).
 * - [allowManualToken]: zusätzliches Feld "Enter token manually" (Kompatibilität mit hc2-Tokens unverifiziert).
 * - [onConfirm] liefert die gewählten Tokens: erst die aus [initial] in alter Reihenfolge, dann neu gewählte in
 *   Auswahlreihenfolge.
 */
@Composable
fun Hc2CheckPickerDialog(
    initial: List<String>,
    multi: Boolean,
    allowManualToken: Boolean = true,
    title: String = if (multi) "Select healthchecks" else "Select healthcheck",
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checks by remember { mutableStateOf<List<Hc2Check>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf("") }
    val selected = remember {
        mutableStateListOf<String>().apply { addAll(initial.distinct().let { if (multi) it else it.take(1) }) }
    }
    // Unbekannte Tokens (auch manuell eingegebene) bleiben in der Liste sichtbar, auch wenn sie abgewählt werden
    val extraTokens = remember { mutableStateListOf<String>() }

    fun load(force: Boolean) {
        loading = true
        error = null
        scope.launch {
            try {
                checks = loadHc2Checks(ctx, force).sortedBy { it.name.orEmpty().lowercase() }
            } catch (e: IPv64Exception) {
                error = e.toError().inlineText()
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(Unit) { load(false) }

    fun toggle(token: String) {
        if (multi) {
            if (token in selected) selected.remove(token) else selected.add(token)
        } else {
            selected.clear()
            selected.add(token)
        }
    }

    val known = checks.orEmpty()
    val knownTokens = remember(known) { known.mapNotNull { it.token }.toSet() }
    LaunchedEffect(knownTokens) {
        if (checks == null) return@LaunchedEffect
        initial.filter { it !in knownTokens && it !in extraTokens }.forEach { extraTokens.add(it) }
    }
    val q = query.trim().lowercase()
    val visible = known.filter { c ->
        c.token != null && (q.isEmpty() || c.name.orEmpty().lowercase().contains(q) || c.target.orEmpty().lowercase().contains(q))
    }

    ActionSheet(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = "OK",
        onConfirm = {
            val ordered = initial.filter { it in selected } + selected.filter { it !in initial }
            onConfirm(ordered.distinct())
        }
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("Search") },
            leadingIcon = { Icon(painterResource(R.drawable.search_24px), null) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(modifier = Modifier.heightIn(min = 80.dp, max = 320.dp)) {
            when {
                loading && checks == null -> PickerLoading()
                error != null && checks == null -> PickerError(error.orEmpty()) { load(true) }
                else -> LazyColumn {
                    items(extraTokens.toList(), key = { "x_$it" }) { token ->
                        PickerRow(
                            selected = token in selected,
                            multi = multi,
                            title = unknownCheckLabel(token),
                            subtitle = "Not found in your healthchecks",
                            onClick = { toggle(token) }
                        )
                    }
                    items(visible, key = { "c_" + it.token.orEmpty() }) { c ->
                        val token = c.token.orEmpty()
                        PickerRow(
                            selected = token in selected,
                            multi = multi,
                            title = c.name?.takeIf { it.isNotBlank() } ?: unknownCheckLabel(token),
                            subtitle = listOfNotNull(c.type.label, c.target?.takeIf { it.isNotBlank() })
                                .joinToString(" · "),
                            trailing = { Hc2StateBadge(Hc2StateUi.from(c.state?.state, c.paused)) },
                            onClick = { toggle(token) }
                        )
                    }
                    if (checks != null && visible.isEmpty() && extraTokens.isEmpty()) {
                        item {
                            Text(
                                if (known.isEmpty()) "No healthchecks yet." else "No healthchecks match.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
        if (allowManualToken) {
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = manual,
                    onValueChange = { manual = it.trim() },
                    singleLine = true,
                    label = { Text("Enter token manually") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    enabled = manual.isNotBlank(),
                    onClick = {
                        val token = manual.trim()
                        if (token !in knownTokens && token !in extraTokens) extraTokens.add(token)
                        if (multi) {
                            if (token !in selected) selected.add(token)
                        } else {
                            selected.clear()
                            selected.add(token)
                        }
                        manual = ""
                    }
                ) {
                    Icon(painterResource(R.drawable.add_24px), contentDescription = "Add token")
                }
            }
        }
    }
}

// =============================================================================================
// Domain-Auswahl
// =============================================================================================

/**
 * Auswahl einer Domain des Accounts (eine Anfrage, DomainListCache).
 * @param exclude Domainnamen, die nicht angeboten werden (z. B. bereits im CDN).
 * @param kind nur Domains dieser Art (null = alle).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainPickerDialog(
    exclude: Set<String> = emptySet(),
    kind: DomainKind? = null,
    title: String = "Select domain",
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var domains by remember { mutableStateOf<List<Domain>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    fun load(force: Boolean) {
        loading = true
        error = null
        scope.launch {
            try {
                domains = loadDomains(ctx, force).sortedBy { it.name.orEmpty().lowercase() }
            } catch (e: IPv64Exception) {
                error = e.toError().inlineText()
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(Unit) { load(false) }

    val q = query.trim().lowercase()
    val visible = domains.orEmpty().filter { d ->
        val name = d.name.orEmpty()
        name.isNotBlank() && name !in exclude && (kind == null || d.effectiveKind == kind) &&
            (q.isEmpty() || name.lowercase().contains(q))
    }

    // Sheet statt Dialog: volle Höhe für die Liste
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Search") },
                leadingIcon = { Icon(painterResource(R.drawable.search_24px), null) },
                modifier = Modifier.fillMaxWidth()
            )
            Box(modifier = Modifier.weight(1f)) {
                when {
                    loading && domains == null -> PickerLoading()
                    error != null && domains == null -> PickerError(error.orEmpty()) { load(true) }
                    visible.isEmpty() -> Text(
                        if (domains.isNullOrEmpty()) "No domains yet." else "No domains available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )

                    else -> LazyColumn {
                        items(visible, key = { it.name.orEmpty() }) { d ->
                            PickerRow(
                                title = d.name.orEmpty(),
                                subtitle = if (d.effectiveKind == DomainKind.OWN) "Own domain" else "DynDNS",
                                onClick = { onPick(d.name.orEmpty()) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================================
// Record-Auswahl (erst Domain, dann Record)
// =============================================================================================

/**
 * Auswahl eines DNS-Records: zuerst die Domain (DomainListCache), dann deren Records (DomainCache, sonst
 * domains.get → DomainCache.put). Höchstens zwei Anfragen nacheinander.
 *
 * @param types nur Records dieser Typen (Standard A/AAAA).
 * @param filter zusätzlicher Filter, z. B. gesperrte CDN-/Failover-Records ausblenden.
 * @param onPick liefert Domainname und Record; die Record-ID für die API ist `record.id.toString()`
 *   (alternativ record.token).
 */
@Composable
fun RecordPickerDialog(
    types: Set<RecordType> = setOf(RecordType.A, RecordType.AAAA),
    filter: (Record) -> Boolean = { true },
    title: String = "Select record",
    onPick: (domain: String, record: Record) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var domain by remember { mutableStateOf<String?>(null) }
    var records by remember { mutableStateOf<List<Record>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    // Laufender Abruf; wird bei "Back" bzw. neuer Domainwahl abgebrochen
    var loadJob by remember { mutableStateOf<Job?>(null) }

    fun resetSelection() {
        loadJob?.cancel()
        loadJob = null
        loading = false
        domain = null
        records = null
        error = null
    }

    fun loadRecords(name: String) {
        loadJob?.cancel()
        // Zuerst den Client holen: bei API-Key-Wechsel werden die Caches geleert, bevor DomainCache gelesen wird
        val client = IPv64Client.get(ctx)
        DomainCache.get(name)?.let {
            records = it.records
            loading = false
            return
        }
        loading = true
        error = null
        loadJob = scope.launch {
            try {
                val detail = ipv64Retrying { client.domains.get(name) }
                DomainCache.put(name, detail)
                // Nur übernehmen, wenn die Domain noch gewählt ist (sonst gehören die Records zu einer anderen)
                if (domain == name) records = detail.records
            } catch (e: IPv64Exception) {
                if (domain == name) error = e.toError().inlineText()
            } finally {
                if (domain == name) loading = false
            }
        }
    }

    val current = domain
    if (current == null) {
        DomainPickerDialog(
            title = title,
            onPick = {
                resetSelection()
                domain = it
                loadRecords(it)
            },
            onDismiss = onDismiss
        )
        return
    }

    val visible = records.orEmpty().filter { r -> r.type in types && r.id != null && filter(r) }
    ActionSheet(title = current, onDismiss = onDismiss) {
        Box(modifier = Modifier.heightIn(min = 80.dp, max = 400.dp)) {
            when {
                loading && records == null -> PickerLoading()
                error != null && records == null -> PickerError(error.orEmpty()) { loadRecords(current) }
                visible.isEmpty() -> Text(
                    "No matching records (${types.joinToString("/") { it.value }}) in this domain.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )

                else -> LazyColumn {
                    items(visible, key = { it.id ?: 0L }) { r ->
                        PickerRow(
                            title = r.displayName(current),
                            subtitle = "${r.type?.value.orEmpty()} · ${r.content.orEmpty()}",
                            onClick = { onPick(current, r) }
                        )
                    }
                }
            }
        }
        TextButton(onClick = { resetSelection() }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

// =============================================================================================
// Interne Bausteine
// =============================================================================================

@Composable
private fun PickerLoading() {
    Box(modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun PickerError(text: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(8.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = onRetry) { Text("Retry") }
    }
}

// Zeile mit optionaler Auswahl (selected != null → Checkbox/Radio)
@Composable
private fun PickerRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    selected: Boolean? = null,
    multi: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected != null) {
            if (multi) Checkbox(checked = selected, onCheckedChange = { onClick() })
            else RadioButton(selected = selected, onClick = onClick)
        }
        Column(modifier = Modifier
            .weight(1f)
            .padding(start = if (selected == null) 8.dp else 0.dp, end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke()
    }
}
