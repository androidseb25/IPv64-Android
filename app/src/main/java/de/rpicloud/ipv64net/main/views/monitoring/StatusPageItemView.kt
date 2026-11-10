package de.rpicloud.ipv64net.main.views.monitoring

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.sr.ipv64.model.StatusPage

// Zeile der Statusseiten-Liste; onOpen öffnet die öffentliche URL im Browser
@Composable
fun StatusPageItemView(page: StatusPage, onClick: () -> Unit, onOpen: () -> Unit) {
    val meta = listOfNotNull(
        "${page.healthchecks.size} check(s)",
        page.design?.let { "Design $it" },
        page.lastViewedAt?.let { "Viewed ${it.formatRelative()}" }
    ).joinToString(" · ")
    ListRow(
        title = page.name.orEmpty().ifBlank { page.slug.orEmpty().ifBlank { "(unnamed)" } },
        subtitle = (page.url?.takeIf { it.isNotBlank() }?.let { "$it\n" }.orEmpty()) + meta,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (page.passwordProtected == true) StatusChip("Password", colorResource(R.color.ipv64_orange))
                else StatusChip("Public", colorResource(R.color.ipv64_green))
                if (!page.url.isNullOrBlank()) {
                    IconButton(onClick = onOpen) {
                        Icon(painterResource(R.drawable.open_in_new_24px), contentDescription = "Open status page")
                    }
                }
            }
        },
        showChevron = false,
        onClick = onClick
    )
}
