package de.rpicloud.ipv64net.main.views

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.rpicloud.ipv64net.models.HealthCheck
import de.rpicloud.ipv64net.ui.theme.AppTheme


@Composable
fun HealthcheckItemView(
    ctx: Context,
    hc: HealthCheck,
    onClick: (selectedHealthCheck: HealthCheck) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onClick(hc) }
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                hc.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                 maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        EventsBarCanvas(
            events = hc.events,
            statusToColor = { Color(ContextCompat.getColor(ctx, it.Status.color)) } // oder { colorResource(it.colorRes) }
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun HealthcheckItemViewPreview() {
    AppTheme {
        val ctx = LocalContext.current
        val hc = HealthCheck()
        HealthcheckItemView(ctx, hc) {
            println(it)
        }
    }
}