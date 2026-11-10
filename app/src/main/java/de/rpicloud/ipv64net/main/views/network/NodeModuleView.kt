package de.rpicloud.ipv64net.main.views.network

import de.rpicloud.ipv64net.main.views.security.countryFlag
import androidx.annotation.ColorRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.helper.rememberNavArg
import de.rpicloud.ipv64net.main.views.common.EmptyState
import de.rpicloud.ipv64net.main.views.common.GroupDivider
import de.rpicloud.ipv64net.main.views.common.GroupText
import de.rpicloud.ipv64net.main.views.common.InfoGroup
import de.rpicloud.ipv64net.main.views.common.InfoItem
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.RefreshableList
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.StatTile
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.Node64ApiException
import de.sr.ipv64.node64.Node64
import de.sr.ipv64.node64.model.CheckStateValue
import de.sr.ipv64.node64.model.DNS64Range
import de.sr.ipv64.node64.model.DNS64TopKind
import de.sr.ipv64.node64.model.DynDnsTargetStatus
import de.sr.ipv64.node64.model.Honeypot64Count
import de.sr.ipv64.node64.model.HostReachState
import de.rpicloud.ipv64net.main.views.common.FormSection
import de.rpicloud.ipv64net.main.views.security.RankRow
import de.rpicloud.ipv64net.main.views.security.RankingCard
import de.rpicloud.ipv64net.main.views.security.SimpleBarChart
import de.rpicloud.ipv64net.main.views.security.formatCount
import de.rpicloud.ipv64net.ui.theme.chartPalette
import de.sr.ipv64.node64.model.Honeypot64Stats
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Instant

private const val TOP = 10

/** Module name (as the node reports it) → screen label; null = no screen for this module. */
internal fun moduleLabel(name: String): String? = when (name.lowercase()) {
    "honeypot" -> "Honeypot"
    "dns64" -> "DNS64"
    "cdn64" -> "CDN64 / Proxy"
    "dyndns" -> "DynDNS"
    "checks", "healthchecks" -> "Healthchecks"
    else -> null
}

internal fun hasModuleScreen(name: String) = moduleLabel(name) != null

// Pure data of one section, built while loading, so the composable only draws
private class ModRow(val title: String, val subtitle: String? = null, val chip: Pair<String, Int>? = null)

private class ModSection(
    val title: String,
    val stats: List<Pair<String, String>> = emptyList(),
    val facts: List<InfoItem> = emptyList(),
    val rows: List<ModRow> = emptyList(),
    val empty: String = "Nothing to show.",
    val error: String? = null,
    val bars: List<Pair<String, Long>> = emptyList(),
    val ranks: List<RankRow>? = null,
    val mono: Boolean = false,
    val hidden: Boolean = false
)

// One failing section shows its error inline; the others stay
private suspend fun section(title: String, block: suspend () -> ModSection): ModSection =
    try {
        block()
    } catch (e: IPv64Exception) {
        ModSection(title, error = if (e is Node64ApiException && e.status == 404) "Not available on this node." else localNodeError(e).let { "${it.first}: ${it.second}" })
    }

private fun List<Honeypot64Count>.rows() = map { RankRow(it.key ?: "–", it.count ?: 0L, (it.count ?: 0L).formatCount()) }

private val hourFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault())

// Stats-based sections share one request: its failure shows once (in "Last 24 hours"), the others stay hidden
private suspend fun statSection(stats: Deferred<Result<Honeypot64Stats>>, title: String, show: (Honeypot64Stats) -> Boolean = { true }, build: (Honeypot64Stats) -> ModSection): ModSection {
    val s = stats.await().getOrNull() ?: return ModSection(title, hidden = true)
    return if (show(s)) build(s) else ModSection(title, hidden = true)
}

private suspend fun rank(stats: Deferred<Result<Honeypot64Stats>>, title: String, always: Boolean = false, mono: Boolean = false, rows: (Honeypot64Stats) -> List<RankRow>) =
    statSection(stats, title, { always || rows(it).isNotEmpty() }) { ModSection(title, ranks = rows(it).take(TOP), mono = mono) }

// Node meldet "nie" als Nulldatum (0001-01-01)
private fun Instant?.ago() = this?.takeIf { it.epochSeconds > 0 }?.formatRelative()

private fun stateColor(s: CheckStateValue?): Int = when (s) {
    CheckStateValue.UP -> R.color.ipv64_green
    CheckStateValue.DOWN -> R.color.ipv64_red
    CheckStateValue.DEGRADED, CheckStateValue.FLAPPING -> R.color.ipv64_orange
    else -> R.color.ipv64_gray
}

private suspend fun loadModule(node: Node64, module: String): List<ModSection> = coroutineScope {
    // One shared request; its failure is a value so it cannot cancel the sibling sections
    val stats = async { try { Result.success(node.honeypot.stats(top = TOP)) } catch (e: IPv64Exception) { Result.failure(e) } }
    when (module.lowercase()) {
        "honeypot" -> listOf(
            async {
                section("Status") {
                    val s = node.honeypot.status()
                    ModSection(
                        "Status",
                        facts = listOf(
                            InfoItem("State", if (s.running == true) "Running" else "Not running"),
                            InfoItem("Version", s.version),
                            InfoItem("Profile", s.profile),
                            InfoItem("Services", s.services.size.toString()),
                            InfoItem("Connections", s.counters?.connections?.toString()),
                            InfoItem("Logins", s.counters?.logins?.toString())
                        ).filter { !it.value.isNullOrBlank() }
                    )
                }
            },
            async {
                section("Last 24 hours") {
                    val s = stats.await().getOrThrow()
                    ModSection(
                        "Last 24 hours",
                        stats = listOf("Events" to "${s.events ?: 0}", "Sources" to "${s.sources ?: 0}", "Logins" to "${s.logins ?: 0}")
                    )
                }
            },
            async {
                statSection(stats, "Attacks (last 24 h)", { it.timeline.isNotEmpty() }) { s ->
                    ModSection("Attacks (last 24 h)", bars = s.timeline.map { (it.time?.let { t -> hourFmt.format(java.time.Instant.ofEpochMilli(t.toEpochMilliseconds())) } ?: "–") to (it.events ?: 0L) })
                }
            },
            async { rank(stats, "Top attackers", always = true) { s -> s.topSources.map { RankRow(it.key ?: "–", it.count ?: 0L, listOfNotNull((it.count ?: 0L).formatCount(), it.country?.countryFlag()).joinToString(" · ")) } } },
            async { rank(stats, "Top ports", always = true) { it.byPort.rows() } },
            async {
                rank(stats, "Top countries", always = true) { s ->
                    s.byCountry.map { RankRow(it.key?.countryFlag() ?: "–", it.count ?: 0L, "${(it.count ?: 0L).formatCount()} · ${it.sources ?: 0} sources") }
                }
            },
            async { rank(stats, "Services") { it.byService.rows() } },
            async { rank(stats, "Attack kinds") { it.byKind.rows() } },
            async { rank(stats, "Usernames", mono = true) { it.topUsers.rows() } },
            async { rank(stats, "Passwords", mono = true) { it.topPasswords.rows() } },
            async { rank(stats, "Paths", mono = true) { it.topPaths.rows() } },
            async { rank(stats, "User agents") { it.topUserAgents.rows() } },
            async {
                section("Recent events") {
                    val ev = node.honeypot.events(limit = 20)
                    ModSection(
                        "Recent events",
                        rows = ev.map {
                            ModRow(
                                it.src ?: "–",
                                listOfNotNull(it.time.ago(), it.service ?: it.port?.let { p -> "port $p" }, it.country?.countryFlag()).filter { s -> s.isNotBlank() }.joinToString(" · ")
                            )
                        },
                        empty = "No events yet."
                    )
                }
            }
        )
        "dns64" -> listOf(
            async {
                section("Status") {
                    val s = node.dns64.status()
                    ModSection(
                        "Status",
                        facts = listOf(
                            InfoItem("Version", s.version),
                            InfoItem("Listen", s.listen, monospace = true),
                            InfoItem("Filtering", if (s.blocking?.paused == true) "Paused" else "Active"),
                            InfoItem("Running since", s.startedAt?.formatGermanTime())
                        ).filter { !it.value.isNullOrBlank() }
                    )
                }
            },
            async {
                section("Last 24 hours") {
                    val s = node.dns64.summary(DNS64Range.HOURS_24).summary
                    val total = s?.total ?: 0
                    val blocked = s?.blocked ?: 0
                    ModSection(
                        "Last 24 hours",
                        stats = listOf(
                            "Queries" to "$total",
                            "Blocked" to "$blocked",
                            "Blocked %" to if (total > 0) (blocked * 100.0 / total).let { p -> if (p < 10) "%.1f %%".format(p) else "${p.toInt()} %" } else "–",
                            "Clients" to "${s?.clients ?: 0}"
                        )
                    )
                }
            },
            *listOf("Top blocked domains" to DNS64TopKind.BLOCKED, "Top queried domains" to DNS64TopKind.ALLOWED, "Top clients" to DNS64TopKind.CLIENTS).map { (t, kind) ->
                async {
                    section(t) {
                        ModSection(t, rows = node.dns64.top(kind, DNS64Range.HOURS_24, limit = TOP).map { ModRow(it.name ?: it.hostname ?: "–", it.hostname.takeIf { _ -> it.name != null }, "${it.count ?: 0}" to R.color.ipv64_gray) }, empty = "No data yet.")
                    }
                }
            }.toTypedArray()
        )
        "cdn64" -> listOf(
            async {
                section("Hosts") {
                    ModSection(
                        "Hosts",
                        rows = node.proxy.hosts.list().map { h ->
                            val (txt, col) = when {
                                h.enabled == false -> "Disabled" to R.color.ipv64_gray
                                h.reach?.state == HostReachState.UP -> "Up" to R.color.ipv64_green
                                h.reach?.state == HostReachState.SLOW -> "Slow" to R.color.ipv64_orange
                                h.reach?.state == HostReachState.DOWN -> "Down" to R.color.ipv64_red
                                else -> "Enabled" to R.color.ipv64_gray
                            }
                            ModRow(
                                h.names.joinToString().ifBlank { "–" },
                                listOfNotNull(h.kind?.value, h.targetHost?.let { t -> h.targetPort?.let { p -> "$t:$p" } ?: t }).filter { s -> s.isNotBlank() }.joinToString(" → "),
                                txt to col
                            )
                        },
                        empty = "No hosts."
                    )
                }
            }
        )
        "dyndns" -> listOf(
            async {
                section("Targets") {
                    ModSection(
                        "Targets",
                        rows = node.dynDns.listTargets().map { t ->
                            val st = t.status
                            ModRow(
                                t.fqdn ?: listOfNotNull(t.host, t.domain).joinToString(".").ifBlank { "–" },
                                listOfNotNull(t.lastIpv4, t.lastIpv6, t.lastOkAt.ago()?.let { a -> "updated $a" }, t.lastError.takeIf { st != DynDnsTargetStatus.OK })
                                    .filter { s -> s.isNotBlank() }.joinToString(" · "),
                                (st?.value?.replaceFirstChar { ch -> ch.uppercase() } ?: "–") to when (st) {
                                    DynDnsTargetStatus.OK -> R.color.ipv64_green
                                    DynDnsTargetStatus.ERROR -> R.color.ipv64_red
                                    else -> R.color.ipv64_gray
                                }
                            )
                        },
                        empty = "No targets."
                    )
                }
            }
        )
        else -> listOf(
            async {
                section("Checks") {
                    ModSection(
                        "Checks",
                        rows = node.checks.list().map { c ->
                            val st = c.state
                            ModRow(c.name ?: "–", st?.lastCheckAt.ago()?.let { "checked $it" }, (st?.state?.value?.replaceFirstChar { ch -> ch.uppercase() } ?: "–") to stateColor(st?.state))
                        },
                        empty = "No checks."
                    )
                }
            }
        )
    }.awaitAll().filter { !it.hidden }
}

/**
 * Read-only details of one node module over the stored direct connection (node id + module name via nav args).
 * Every section loads in parallel; a failing section shows its error inline.
 */
@Composable
fun NodeModuleView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val id = navController.rememberNavArg<String>(NavKeys.SELECTED_NODE_ID)
    val module = navController.rememberNavArg<String>(NavKeys.NODE_MODULE)
    val config = remember(id) { id?.let { LocalNodeStore.get(ctx, it) } }
    var sections by remember { mutableStateOf<List<ModSection>?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    fun load() {
        if (refreshing) return
        val (url, key) = config ?: return
        val m = module ?: return
        refreshing = true
        scope.launch {
            try {
                sections = loadModule(localNodeClient(url, key), m)
            } finally {
                refreshing = false
            }
        }
    }
    LaunchedEffect(config, module) { load() }

    ScreenScaffold(title = module?.let { moduleLabel(it) } ?: "Module", navController = navController, mainPadding = mainPadding) {
        val list = sections
        when {
            config == null || module == null -> EmptyState("No direct connection to this node.")
            else -> RefreshableList(refreshing = refreshing, onRefresh = ::load, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (list ?: emptyList()).forEachIndexed { i, s ->
                    item(key = "$i-${s.title}") { SectionView(s) }
                }
            }
        }
    }
}

@Composable
private fun SectionView(s: ModSection) {
    when {
        s.error != null -> ListGroup(title = s.title) { GroupText(s.error, error = true) }
        s.ranks != null -> RankingCard(s.title, s.ranks, monospace = s.mono)
        s.bars.isNotEmpty() -> FormSection(title = s.title) {
            SimpleBarChart(
                values = s.bars.map { it.second },
                firstLabel = s.bars.first().first,
                lastLabel = s.bars.last().first,
                color = chartPalette().categorical(0),
                labels = s.bars.map { it.first }
            )
        }
        s.facts.isNotEmpty() -> InfoGroup(title = s.title, rows = s.facts)
        s.stats.isNotEmpty() -> ListGroup(title = s.title) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.stats.forEach { (label, value) -> StatTile(value, label, Modifier.weight(1f)) }
            }
        }
        else -> ListGroup(title = s.title) {
            if (s.rows.isEmpty()) GroupText(s.empty)
            s.rows.forEachIndexed { i, r ->
                if (i > 0) GroupDivider()
                ListRow(
                    title = r.title,
                    subtitle = r.subtitle?.takeIf { it.isNotBlank() },
                    showChevron = false,
                    trailing = r.chip?.let { (t, c) -> { StatusChip(t, colorResource(c)) } }
                )
            }
        }
    }
}
