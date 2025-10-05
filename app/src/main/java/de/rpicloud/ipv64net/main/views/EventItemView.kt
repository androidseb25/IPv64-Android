package de.rpicloud.ipv64net.main.views

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.rpicloud.ipv64net.helper.parseDbDateTime
import de.rpicloud.ipv64net.models.HealthEvents
import de.rpicloud.ipv64net.models.MyLogs
import de.rpicloud.ipv64net.ui.theme.AppTheme

@Composable
fun EventItemView(
    ctx: Context,
    event: HealthEvents
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
                event.text,
                style = MaterialTheme.typography.titleSmall,
                color = Color(ContextCompat.getColor(ctx, event.Status.color))
            )
            Text(
                event.event_time.parseDbDateTime(),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 5.dp),
                color = Color.Gray
            )
        }
    }
}


@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun EventItemViewPreview() {
    AppTheme {
        val ctx = LocalContext.current
        val event = HealthEvents.empty
        event.event_time = "2025-09-25 14:00:00"
        event.status = 1
        event.text = "Alles Paletti oder was"
        EventItemView(ctx, event)
    }
}