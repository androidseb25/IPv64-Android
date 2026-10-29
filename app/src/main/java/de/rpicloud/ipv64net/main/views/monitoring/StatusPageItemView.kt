package de.rpicloud.ipv64net.main.views.monitoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.sr.ipv64.model.StatusPage

// Zeile der Statusseiten-Liste; onOpen öffnet die öffentliche URL im Browser
@Composable
fun StatusPageItemView(page: StatusPage, onClick: () -> Unit, onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    page.name.orEmpty().ifBlank { page.slug.orEmpty().ifBlank { "(unnamed)" } },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                page.url?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (page.passwordProtected == true) StatusChip("Password", colorResource(R.color.ipv64_orange))
                    else StatusChip("Public", colorResource(R.color.ipv64_green))
                    Text(
                        listOfNotNull(
                            "${page.healthchecks.size} check(s)",
                            page.design?.let { "Design $it" },
                            page.lastViewedAt?.let { "Viewed ${it.formatRelative()}" }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!page.url.isNullOrBlank()) {
                IconButton(onClick = onOpen) {
                    Icon(painterResource(R.drawable.open_in_new_24px), contentDescription = "Open status page")
                }
            }
        }
    }
}
