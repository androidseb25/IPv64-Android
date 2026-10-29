package de.rpicloud.ipv64net.main.views.security

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.SectionHeader
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.model.ThreatStats
import de.sr.ipv64.model.ThreatStatsEntry

/** Öffentliche Honeypot64-Statistik. Eine Anfrage (gecacht, 10 min); Pull-to-Refresh lädt neu. */
@Composable
fun ThreatStatsView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()
    var stats by remember { mutableStateOf<ThreatStats?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    fun load(force: Boolean = false, pull: Boolean = false) {
        if (pull) refreshing = true
        // Ohne Spinner, wenn der Wert schon im Cache liegt
        val quiet = pull || (!force && ThreatStatsCache.peek(ctx) != null)
        req.launch(
            scope,
            spinner = if (quiet) null else "Loading...",
            retry = { load(true) },
            onFinally = { refreshing = false }
        ) {
            stats = ThreatStatsCache.get(ctx, force) { it.threat.stats() }
        }
    }

    LaunchedEffect(Unit) { load() }

    ScreenScaffold(Tabs.getLabel(Tab.threat_stats), navController, mainPadding) {
        RefreshableList(refreshing = refreshing, onRefresh = { load(force = true, pull = true) }) {
            val s = stats
            if (s == null) {
                item(key = "empty") { EmptyState(if (req.isBusy) "Loading..." else "No statistics available.") }
            } else {
                statsContent(s)
            }
        }
    }
    RequestStateHost(req)
}

private fun List<ThreatStatsEntry>.toRows(): List<RankRow> = mapNotNull { e ->
    val label = e.value?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
    val detail = listOfNotNull(
        e.events?.let { "${it.formatCount()} attempts" },
        e.accounts?.let { "$it acc." }
    ).joinToString(" · ")
    RankRow(label, e.events ?: 0L, detail.ifEmpty { null })
}

private fun androidx.compose.foundation.lazy.LazyListScope.statsContent(s: ThreatStats) {
    item(key = "overview") {
        SecurityCard {
            Text("Honeypot64 network", style = MaterialTheme.typography.titleMedium)
            StatTiles(
                listOf(
                    "Sensors online" to "${(s.sensors?.online ?: 0L).formatCount()} / ${(s.sensors?.active7d ?: 0L).formatCount()}",
                    "Attacks (24 h)" to (s.attacks24h?.events ?: 0L).formatCount(),
                    "Attacker IPs (24 h)" to (s.attacks24h?.sources ?: 0L).formatCount(),
                    "Active accounts (7 d)" to (s.threshold?.activeAccounts7d ?: 0L).formatCount()
                )
            )
            s.generatedAt?.let {
                Text(
                    "Updated ${it.formatRelative()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (s.timeline.isNotEmpty()) {
        item(key = "timeline") {
            SecurityCard {
                Text("Attacks per day (30 days)", style = MaterialTheme.typography.titleSmall)
                SimpleBarChart(
                    values = s.timeline.map { it.events ?: 0L },
                    firstLabel = shortDay(s.timeline.first().day),
                    lastLabel = shortDay(s.timeline.last().day)
                )
            }
        }
    }

    item(key = "ports") {
        RankingCard(
            "Most attacked ports (7 days)",
            s.topPorts.map { RankRow("${it.service ?: "–"} · ${it.port ?: "–"}", it.events ?: 0L) }
        )
    }
    item(key = "kinds") {
        RankingCard(
            "Most frequent attack kinds (7 days)",
            s.topKinds.map { RankRow(it.kind ?: "–", it.events ?: 0L) }
        )
    }

    val top = s.top
    if (top != null) {
        item(key = "countries") {
            RankingCard(
                "Origin of blocklist IPs",
                top.countries.map {
                    RankRow(it.country ?: "–", it.ips ?: 0L, "${(it.ips ?: 0L).formatCount()} IPs")
                }
            )
        }
        item(key = "rank_header") {
            SectionHeader("Top 10 (${s.windowDays ?: 30} days)")
        }
        item(key = "usernames") { RankingCard("User names", top.usernames.toRows(), monospace = true) }
        item(key = "passwords") { RankingCard("Passwords", top.passwords.toRows(), monospace = true) }
        item(key = "paths") { RankingCard("Requested paths", top.paths.toRows(), monospace = true) }
        item(key = "user_agents") { RankingCard("User agents", top.userAgents.toRows()) }
        item(key = "fingerprints") { RankingCard("Fingerprints (JA4/HASSH)", top.fingerprints.toRows(), monospace = true) }
    }

    if (s.lists.isNotEmpty()) {
        item(key = "lists") {
            SecurityCard {
                Text("List sizes", style = MaterialTheme.typography.titleSmall)
                s.lists.entries.sortedBy { it.key }.forEach { (name, size) ->
                    KeyValueLine(prettyKey(name), size.formatCount())
                }
            }
        }
    }

    val notes = listOfNotNull(
        s.threshold?.accounts?.let { "Rankings and lists need at least $it reporting accounts." },
        s.info?.takeIf { it.isNotBlank() },
        s.license?.takeIf { it.isNotBlank() }?.let { "License: $it" }
    )
    if (notes.isNotEmpty()) {
        item(key = "notes") {
            SecurityCard {
                notes.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
