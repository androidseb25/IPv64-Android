package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.ui.theme.AppTheme
import de.sr.ipv64.model.LogEntry
import kotlin.time.Instant

@Composable
fun LogItemView(
    log: LogEntry
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                log.title ?: "",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                log.message ?: "",
                style = MaterialTheme.typography.titleSmall
            )
            log.time?.let {
                Text(
                    it.formatGermanTime(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 5.dp),
                    color = Color.Gray
                )
            }
        }
    }
}


@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun LogItemViewPreview() {
    AppTheme {
        val log = LogEntry(
            id = 1,
            title = "API: Get Domains",
            message = "Get all domains from this account.",
            time = Instant.parse("2025-10-02T10:44:05Z")
        )
        LogItemView(log)
    }
}