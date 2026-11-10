package de.rpicloud.ipv64net.main.views

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.ui.theme.AppTheme
import de.sr.ipv64.model.LogEntry
import kotlin.time.Instant

/** Zeile eines Log-Eintrags (in einer [ListGroup]): Titel, Meldung und Zeitpunkt. */
@Composable
fun LogItemView(
    log: LogEntry
) {
    ListRow(
        title = log.title ?: "",
        subtitle = listOfNotNull(log.message?.takeIf { it.isNotEmpty() }, log.time?.formatGermanTime()).joinToString("\n")
            .ifEmpty { null },
        subtitleMaxLines = 6
    )
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
        ListGroup {
            LogItemView(log)
        }
    }
}
