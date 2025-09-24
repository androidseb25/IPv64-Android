package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.helper.parseDbDate
import de.rpicloud.ipv64net.models.Integration
import de.rpicloud.ipv64net.ui.theme.AppTheme

@Composable
fun IntegrationItemView(
    integration: Integration,
    onClick: (selectedIntegation: Integration) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onClick(integration) }
    ) {
        Row {
            Column(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 16.dp, start = 16.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = integration.icon),
                    contentDescription = "icon",
                    modifier = Modifier.height(28.dp)
                )
            }
            Column(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 16.dp, start = 8.dp, end = 16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                integration.integration_name?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "added: ${integration.add_time?.parseDbDate()}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "last use: ${integration.last_used?.parseDbDate()}",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun IntegrationItemViewPreview() {
    AppTheme {
        val integr = Integration.empty
        integr.integration = "webhook"
        integr.integration_name = "Webhook"
        IntegrationItemView(integr) {
            println(it)
        }
    }
}