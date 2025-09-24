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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.models.Domain
import de.rpicloud.ipv64net.ui.theme.AppTheme

@Composable
fun DomainItemView(
    domain: Domain,
    onClick: (selectedDomain: Domain) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onClick(domain) }
    ) {
        Row {
            Column(
                modifier = Modifier
                    .padding(top = 24.dp, bottom = 16.dp, start = 16.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.circle_24px),
                    contentDescription = "icon",
                    tint = domain.tintColor,
                    modifier = Modifier.height(14.dp)
                )
            }
            Column(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 16.dp, start = 8.dp, end = 16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                domain.fqdn?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "Updates: ${domain.updates}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "Wildcard: ${domain.IsWildcard}",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun DomainItemViewPreview() {
    AppTheme {
        val domain = Domain.empty
        domain.fqdn = "seb.home64.de"
        domain.updates = 5
        domain.wildcard = 1
        DomainItemView(domain) {
            println(it)
        }
    }
}