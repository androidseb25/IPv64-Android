package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.helper.SecNtfyLog
import de.rpicloud.ipv64net.helper.SecNtfyLog.Status
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.common.groupedItemsOrEmpty
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import java.util.Date

@Composable
fun SecNtfyLogView(navController: NavHostController, mainPadding: PaddingValues) {
    val context = LocalContext.current
    val revision by SecNtfyLog.revision.collectAsState()
    val entries = remember(context, revision) { SecNtfyLog.entries(context) }
    ScreenScaffold(Tabs.getLabel(Tab.notification_logs), navController, mainPadding) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            groupedItemsOrEmpty(entries, loaded = true, emptyText = "No notification activity yet.", key = "notification_logs") { entry ->
                val colors = MaterialTheme.colorScheme
                val color = when (entry.status) {
                    Status.SUCCESS -> if (colors.surface.luminance() < 0.5f) Color(0xFF77D78D) else Color(0xFF175F2C)
                    Status.FAILED -> colors.error
                    Status.SKIPPED, Status.INFO -> colors.onSurfaceVariant
                }
                ListRow(
                    title = entry.message,
                    subtitle = Date(entry.time).formatGermanTime(),
                    trailing = { StatusChip(entry.status.label, color) }
                )
            }
        }
    }
}
