package de.rpicloud.ipv64net.main.views.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.main.views.common.ActionSheet
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.rememberNavArg
import de.rpicloud.ipv64net.main.views.common.ConfirmDialog
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.FilterChipRow
import de.rpicloud.ipv64net.main.views.common.GroupDivider
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.node64.model.DNS64Record
import de.sr.ipv64.node64.model.DNS64RecordRequest
import de.sr.ipv64.node64.model.DNS64RecordType

/** Eigene DNS-Einträge einer direkt verbundenen Node (Node-ID per Nav-Argument). */
@Composable
fun NodeDnsRecordsView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    val id = navController.rememberNavArg<String>(NavKeys.SELECTED_NODE_ID)
    var config by remember { mutableStateOf<Pair<String, String>?>(null) }
    var records by remember { mutableStateOf<List<DNS64Record>?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DNS64Record?>(null) }   // id == null = neu
    var showForm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<DNS64Record?>(null) }

    fun load(pull: Boolean = false) {
        val (url, key) = config ?: return
        if (pull) refreshing = true
        req.launch(
            scope,
            spinner = if (pull || records != null) null else "Loading...",
            onFinally = { refreshing = false },
            onError = { e -> localNodeError(e).let { req.message(it.first, it.second) }; true }
        ) { records = localNodeClient(url, key).dns64.records.list() }
    }

    // Schreibaktion, danach Liste neu laden
    fun act(call: suspend (de.sr.ipv64.node64.api.Dns64RecordsApi) -> Unit) {
        val (url, key) = config ?: return
        req.launch(
            scope,
            spinner = "Updating...",
            onError = { e -> localNodeError(e).let { req.message(it.first, it.second) }; true }
        ) {
            val api = localNodeClient(url, key).dns64.records
            call(api)
            records = api.list()
        }
    }

    LaunchedEffect(id) {
        config = id?.let { LocalNodeStore.get(ctx, it) }
        load()
    }

    ScreenScaffold(
        title = Tabs.getLabel(Tab.node_dns_records),
        navController = navController,
        mainPadding = mainPadding,
        fab = {
            if (config != null) FloatingActionButton(onClick = { editing = null; showForm = true }) {
                Icon(painterResource(R.drawable.add_24px), contentDescription = "Add record")
            }
        }
    ) {
        val list = records
        when {
            config == null -> EmptyState("No direct connection to this node.")
            list == null -> Unit
            list.isEmpty() -> EmptyState("No local DNS records yet.")
            else -> RefreshableList(refreshing = refreshing, onRefresh = { load(pull = true) }, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp)) {
                item {
                    ListGroup {
                        list.forEachIndexed { i, r ->
                            if (i > 0) GroupDivider()
                            ListRow(
                                title = r.name ?: "–",
                                subtitle = "${r.type ?: "–"} · ${r.value ?: "–"} · TTL ${r.ttl ?: "–"}" + (r.comment?.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: ""),
                                subtitleMaxLines = 3,
                                monospaceSubtitle = true,
                                showChevron = false,
                                trailing = {
                                    Switch(
                                        checked = r.enabled != false,
                                        onCheckedChange = { on -> r.id?.let { rid -> act { if (on) it.enable(rid) else it.disable(rid) } } },
                                        enabled = !req.isBusy && r.id != null
                                    )
                                },
                                onClick = { editing = r; showForm = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        RecordForm(
            record = editing,
            onSave = { rid, body -> showForm = false; act { if (rid == null) it.create(body) else it.update(rid, body) } },
            onDelete = { r -> showForm = false; deleting = r },
            onDismiss = { showForm = false }
        )
    }
    deleting?.let { r ->
        ConfirmDialog(
            title = "Delete record",
            text = "Delete ${r.name} (${r.type})?",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { deleting = null; r.id?.let { rid -> act { it.delete(rid) } } },
            onDismiss = { deleting = null }
        )
    }
    RequestStateHost(req)
}

@Composable
private fun RecordForm(record: DNS64Record?, onSave: (Long?, DNS64RecordRequest) -> Unit, onDelete: (DNS64Record) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(record?.name.orEmpty()) }
    var type by remember { mutableStateOf(DNS64RecordType.entries.firstOrNull { it.value == record?.type } ?: DNS64RecordType.A) }
    var value by remember { mutableStateOf(record?.value.orEmpty()) }
    var ttl by remember { mutableStateOf(record?.ttl?.toString().orEmpty()) }
    var comment by remember { mutableStateOf(record?.comment.orEmpty()) }
    val valid = name.isNotBlank() && value.isNotBlank() && (ttl.isEmpty() || ttl.toIntOrNull() != null)
    ActionSheet(
        title = if (record == null) "New record" else "Edit record",
        onDismiss = onDismiss,
        confirmLabel = "Save",
        confirmEnabled = valid,
        onConfirm = {
            onSave(record?.id, DNS64RecordRequest(
                name.trim(), type, value.trim(), ttl.toIntOrNull(),
                comment = if (record != null) comment.trim() else comment.trim().ifEmpty { null },
                noPtr = record?.noPtr, localOnly = record?.localOnly
            ))
        }
    ) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FilterChipRow(DNS64RecordType.entries, type, { it.value }, { type = it })
        OutlinedTextField(value, { value = it }, label = { Text("Value") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(ttl, { ttl = it.filter(Char::isDigit) }, label = { Text("TTL (seconds, optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(comment, { comment = it }, label = { Text("Comment (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (record != null) TextButton(onClick = { onDelete(record) }, modifier = Modifier.fillMaxWidth()) {
            Text("Delete", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
        }
    }
}
