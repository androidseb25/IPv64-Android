package de.rpicloud.ipv64net.main.views.monitoring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import de.rpicloud.ipv64net.main.views.common.RecordPickerDialog
import de.rpicloud.ipv64net.main.views.common.displayName
import de.sr.ipv64.model.AddFailoverRuleRequest
import de.sr.ipv64.model.FailoverTargetKind
import de.sr.ipv64.model.Record
import de.sr.ipv64.model.RecordType

/**
 * Regel hinzufügen: erst den zu schützenden Record wählen (A/AAAA, ohne CDN-/Failover-Records und ohne bereits
 * geschützte), dann das Ersatzziel (IP der passenden Familie, anderer Record gleichen Typs oder Offline-Seite).
 * Der Dialog schließt sich nicht selbst; [onConfirm] und [onDismiss] müssen ihn ausblenden.
 *
 * @param existingRecordRefs Token/IDs der Records, die bereits durch Regeln dieser Policy geschützt sind.
 */
@Composable
fun FailoverRuleDialog(
    existingRecordRefs: Set<String>,
    onConfirm: (AddFailoverRuleRequest) -> Unit,
    onDismiss: () -> Unit
) {
    var protectedRecord by remember { mutableStateOf<Record?>(null) }
    var protectedDomain by remember { mutableStateOf<String?>(null) }
    val record = protectedRecord
    if (record == null) {
        RecordPickerDialog(
            types = setOf(RecordType.A, RecordType.AAAA),
            filter = { it.state !in BLOCKED_RULE_RECORD_STATES && it.ref() != null && it.ref() !in existingRecordRefs },
            title = "Record to protect",
            onPick = { domain, picked ->
                protectedDomain = domain
                protectedRecord = picked
            },
            onDismiss = onDismiss
        )
    } else {
        RuleTargetDialog(
            record = record,
            recordName = record.displayName(protectedDomain),
            onBack = { protectedRecord = null },
            onConfirm = onConfirm,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun RuleTargetDialog(
    record: Record,
    recordName: String,
    onBack: () -> Unit,
    onConfirm: (AddFailoverRuleRequest) -> Unit,
    onDismiss: () -> Unit
) {
    var kind by remember { mutableStateOf(FailoverTargetKind.IP) }
    var ip by remember { mutableStateOf("") }
    var targetRecord by remember { mutableStateOf<Record?>(null) }
    var targetRecordName by remember { mutableStateOf("") }
    var offlinePageId by remember { mutableStateOf<String?>(null) }
    var offlinePageLabel by remember { mutableStateOf("") }
    var showRecordPicker by remember { mutableStateOf(false) }
    var showPagePicker by remember { mutableStateOf(false) }

    val ref = record.ref()
    val ipError = if (kind == FailoverTargetKind.IP && ip.isNotBlank()) ipFamilyError(record.type, ip) else null
    val valid = ref != null && when (kind) {
        FailoverTargetKind.IP -> ip.isNotBlank() && ipFamilyError(record.type, ip) == null
        FailoverTargetKind.RECORD -> targetRecord?.ref() != null
        FailoverTargetKind.OFFLINE_PAGE -> offlinePageId != null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replacement target") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Protected: $recordName (${record.type?.value ?: "?"}${record.content?.let { ", $it" }.orEmpty()})",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "While the failover is active, this record points to the replacement target instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TargetKindRow("Replacement IP", kind == FailoverTargetKind.IP) { kind = FailoverTargetKind.IP }
                TargetKindRow("Other record", kind == FailoverTargetKind.RECORD) { kind = FailoverTargetKind.RECORD }
                TargetKindRow("Offline page", kind == FailoverTargetKind.OFFLINE_PAGE) { kind = FailoverTargetKind.OFFLINE_PAGE }

                when (kind) {
                    FailoverTargetKind.IP -> OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it.trim() },
                        label = { Text(if (record.type == RecordType.AAAA) "IPv6 address" else "IPv4 address") },
                        isError = ipError != null,
                        supportingText = { Text(ipError ?: "Must match the record type (${record.type?.value ?: "A/AAAA"}).") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )

                    FailoverTargetKind.RECORD -> {
                        Text(
                            "The IP of another of your ${record.type?.value ?: ""} records is used.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = { showRecordPicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (targetRecord == null) "Choose record" else targetRecordName)
                        }
                    }

                    FailoverTargetKind.OFFLINE_PAGE -> {
                        Text(
                            "Visitors see an offline page instead of an error.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = { showPagePicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (offlinePageId == null) "Choose offline page" else offlinePageLabel)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val protectedRef = ref ?: return@TextButton
                    onConfirm(
                        when (kind) {
                            FailoverTargetKind.IP -> AddFailoverRuleRequest(record = protectedRef, targetIp = ip.trim())
                            FailoverTargetKind.RECORD -> AddFailoverRuleRequest(record = protectedRef, targetRecord = targetRecord?.ref())
                            FailoverTargetKind.OFFLINE_PAGE -> AddFailoverRuleRequest(record = protectedRef, offlinePage = offlinePageId)
                        }
                    )
                }
            ) { Text("Add rule") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onBack) { Text("Back") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )

    if (showRecordPicker) {
        RecordPickerDialog(
            types = setOf(record.type ?: RecordType.A),
            filter = { it.state !in BLOCKED_RULE_RECORD_STATES && it.ref() != null && it.ref() != ref },
            title = "Target record",
            onPick = { domain, picked ->
                targetRecord = picked
                targetRecordName = picked.displayName(domain)
                showRecordPicker = false
            },
            onDismiss = { showRecordPicker = false }
        )
    }
    if (showPagePicker) {
        OfflinePagePickerDialog(
            onPick = { id, label ->
                offlinePageId = id
                offlinePageLabel = label
                showPagePicker = false
            },
            onDismiss = { showPagePicker = false }
        )
    }
}

@Composable
private fun TargetKindRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
