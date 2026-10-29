package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.DomainCache
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.helper.rememberNavArg
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.InfoRow
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.rpicloud.ipv64net.models.isLocked
import de.rpicloud.ipv64net.models.label
import de.rpicloud.ipv64net.models.lockReason
import de.sr.ipv64.model.Record
import de.sr.ipv64.model.RecordType

/**
 * Bearbeiten eines DNS-Records (Typ, Präfix, Inhalt). Der Record kommt aus dem DomainCache, sonst per
 * records.get (1 Anfrage). Gespeichert werden nur geänderte Felder; danach wird der DomainCache der Domain
 * verworfen, damit die Detailansicht den (ggf. neu angelegten) Record lädt.
 */
@Composable
fun DomainRecordEditView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    val keyboard = LocalSoftwareKeyboardController.current
    val recordId = navController.rememberNavArg<String>(NavKeys.EDIT_RECORD_ID).orEmpty()
    val domain = navController.rememberNavArg<String>(NavKeys.EDIT_RECORD_DOMAIN).orEmpty()

    var original by remember { mutableStateOf<Record?>(null) }
    var type by rememberSaveable { mutableStateOf(RecordType.A) }
    var prefix by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var initialized by rememberSaveable { mutableStateOf(false) }

    fun apply(record: Record) {
        original = record
        if (!initialized) {
            type = record.type ?: RecordType.A
            prefix = record.prefix?.takeIf { it != "@" }.orEmpty()
            content = record.content.orEmpty()
            initialized = true
        }
    }

    fun load() {
        if (recordId.isEmpty()) return
        val cached = if (domain.isNotEmpty()) {
            DomainCache.get(domain)?.records?.firstOrNull { it.id?.toString() == recordId }
        } else null
        if (cached != null) {
            apply(cached)
            return
        }
        req.launch(scope, retry = { load() }) {
            apply(ipv64Retrying { IPv64Client.get(ctx).records.get(recordId) })
        }
    }

    val orig = original
    val locked = orig?.isLocked == true
    val origPrefix = orig?.prefix?.takeIf { it != "@" }.orEmpty()
    val typeChanged = orig != null && type != orig.type
    val prefixChanged = orig != null && prefix.trim() != origPrefix
    val contentChanged = orig != null && content.trim() != orig.content.orEmpty()
    val isIpType = orig?.type == RecordType.A || orig?.type == RecordType.AAAA
    // Nur der Inhalt eines A/AAAA-Records ändert sich → ID und Token bleiben; sonst wird der Record ersetzt
    val replaces = typeChanged || prefixChanged || (contentChanged && !isIpType)
    val canSave = orig != null && !locked && content.isNotBlank() && !req.isBusy

    fun save() {
        keyboard?.hide()
        val o = original ?: return
        if (!canSave) return
        if (!typeChanged && !prefixChanged && !contentChanged) {
            navController.popBackStack()
            return
        }
        req.launch(scope, spinner = "Saving...") {
            ipv64Retrying {
                IPv64Client.get(ctx).records.update(
                    recordId,
                    type = if (typeChanged) type else null,
                    prefix = if (prefixChanged) prefix.trim() else null,
                    content = if (contentChanged) content.trim() else null
                )
            }
            // Der Record kann eine neue ID bekommen haben: Domain beim nächsten Öffnen neu laden
            setOfNotNull(o.domain, domain).filter { it.isNotEmpty() }.forEach { DomainCache.invalidate(it) }
            navController.popBackStack()
        }
    }

    LaunchedEffect(Unit) { load() }

    ScreenScaffold(
        title = Tabs.getLabel(Tab.domain_record_edit),
        navController = navController,
        mainPadding = mainPadding,
        actions = {
            IconButton(onClick = { save() }, enabled = canSave) {
                Icon(painter = painterResource(id = R.drawable.save_24px), contentDescription = "Save")
            }
        }
    ) {
        if (recordId.isEmpty()) {
            EmptyState("No record selected.")
        } else LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (orig != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoRow("Name", orig.fqdn ?: listOfNotNull(origPrefix.ifEmpty { null }, domain.ifEmpty { null }).joinToString("."))
                            InfoRow("TTL", orig.ttl?.let { "$it s" })
                            orig.state?.let { InfoRow("State", it.label) }
                            if (orig.pendingDns == true) InfoRow("DNS", "Change is being propagated")
                            orig.updatedAt?.let { InfoRow("Last update", it.formatGermanTime()) }
                        }
                    }
                }
                if (locked) {
                    item {
                        ChannelFormSection("Locked") {
                            Text(orig.lockReason ?: "This record cannot be edited.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item {
                    ChannelFormSection("Record") {
                        FormDropdown(
                            label = "Type",
                            value = type,
                            options = RecordType.entries.toList(),
                            labelOf = { it.value },
                            onSelect = { type = it },
                            enabled = !locked
                        )
                        FormTextField(
                            label = "Prefix",
                            value = prefix,
                            onValue = { prefix = it.trim() },
                            placeholder = "e.g. www",
                            supporting = "Empty = the domain itself",
                            enabled = !locked
                        )
                        FormTextField(
                            label = "Value",
                            value = content,
                            onValue = { content = it },
                            placeholder = "e.g. IP, TXT record, MX \"10 mail.example.com\"",
                            supporting = if (content.isBlank()) "Required" else null,
                            singleLine = type != RecordType.TXT,
                            enabled = !locked
                        )
                    }
                }
                if (!locked) {
                    item {
                        Text(
                            if (replaces) "This change replaces the record: it gets a new ID and record token. " +
                                    "Scripts that use the old record token must be updated."
                            else "Changing only the IP of an A/AAAA record keeps its ID and record token. " +
                                    "Any other change replaces the record with a new ID and token.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    RequestStateHost(req)
}
