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
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.ui.theme.AppTheme
import de.sr.ipv64.model.Integration
import de.sr.ipv64.model.IntegrationType

// Icon je Integrationstyp; "mobil" aus API v1 heißt in API v2 "app"
val IntegrationType?.icon: Int
    get() = when (this) {
        IntegrationType.WEBHOOK -> R.drawable.webhook_24px
        IntegrationType.DISCORD -> R.drawable.discord_24px
        IntegrationType.NTFY -> R.drawable.ntfy_24px
        IntegrationType.PUSHOVER -> R.drawable.pushover_24px
        IntegrationType.TELEGRAM -> R.drawable.telegram_24px
        IntegrationType.GOTIFY -> R.drawable.gotify_24px
        IntegrationType.EMAIL, IntegrationType.ACCOUNT_EMAIL -> R.drawable.alternate_email_24px
        IntegrationType.SMS -> R.drawable.sms_24px
        IntegrationType.APP -> R.drawable.mobile_24px
        null -> R.drawable.indeterminate_question_box_24px
    }

// Anzeigename; die Account-E-Mail (ID 0) hat ggf. keinen eigenen Namen
val Integration.displayName: String
    get() = name?.takeIf { it.isNotBlank() } ?: when (type) {
        IntegrationType.ACCOUNT_EMAIL -> "Account e-mail"
        IntegrationType.APP -> "IPv64 App"
        else -> type?.value.orEmpty()
    }

@Composable
fun IntegrationItemView(
    integration: Integration,
    isThisDevice: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row {
            Column(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 16.dp, start = 16.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = integration.type.icon),
                    contentDescription = integration.type?.value ?: "icon",
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
                Text(
                    integration.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (isThisDevice) {
                    Text(
                        "This device",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    "added: ${integration.createdAt?.formatGermanTime() ?: "–"}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "last use: ${integration.lastUsedAt?.formatGermanTime() ?: "never"}",
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
        IntegrationItemView(
            Integration(id = 1, type = IntegrationType.WEBHOOK, name = "Webhook")
        )
    }
}
