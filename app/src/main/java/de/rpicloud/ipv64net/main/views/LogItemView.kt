package de.rpicloud.ipv64net.main.views

import android.R
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
import de.rpicloud.ipv64net.helper.parseDbDateTime
import de.rpicloud.ipv64net.models.Domain
import de.rpicloud.ipv64net.models.IPResult
import de.rpicloud.ipv64net.models.MyLogs
import de.rpicloud.ipv64net.ui.theme.AppTheme

@Composable
fun LogItemView(
    log: MyLogs
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
                log.header ?: "",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                log.content ?: "",
                style = MaterialTheme.typography.titleSmall
            )
            log.time?.let {
                Text(
                    it.parseDbDateTime(),
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
        val log = MyLogs.empty
        log.content = "Get all domains from this account."
        log.header = "API: Get Domains"
        log.time = "2025-10-02 10:44:05"
        log.subdomain = ""
        LogItemView(log)
    }
}