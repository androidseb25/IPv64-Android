package de.rpicloud.ipv64net.models

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavGraph
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.main.views.network.ConnectedNodes

/**
 * Alle Navigationsziele. Die Route ist immer der Enum-Name (Tabs.getRoute(tab) == tab.name).
 * Neue Einträge müssen in getLabel und parentTab ergänzt werden (exhaustive when).
 */
enum class Tab {
    welcome,
    login,

    // Domains
    domains,
    domain_details,
    domain_settings,
    domain_record_edit,

    // Monitoring
    monitoring,
    healthcheck,
    healthcheck_new,
    healthcheck_edit,
    healthcheck_details,
    node_check_edit,
    node_check_details,
    channels,
    channel_new,
    channel_edit,
    push_setup,
    hc2_activity,
    maintenance,
    maintenance_edit,
    hc2_settings,
    status_pages,
    status_page_edit,
    failover,
    failover_details,
    failover_edit,
    offline_pages,
    offline_page_edit,

    // Network
    network,
    cdn,
    cdn_details,
    cdn_settings,
    proxy64,
    proxy_host_edit,
    dns64,
    dns64_details,
    nodes,
    node_details,
    node_dns,
    node_dns_log,
    node_dns_records,
    node_module,

    // Security (Unterseiten des Infrastructure-Tabs)
    threat_lookup,
    threat_stats,
    threat_watch,
    password_check,
    honeypot,

    // More (Route "settings")
    settings,
    account,
    account_details,
    account_edit,
    api_keys,
    account_links,
    logs,
    my_ip,
    about
}

sealed class Tabs {
    companion object {
        /**
         * Tabs der unteren Leiste (Tab-Wurzeln), abhängig von direkt verbundenen Nodes (Compose-State, daher
         * recomponiert die Leiste beim Verbinden/Entfernen): CDN64 wird zu Proxy64, wenn eine Node Proxy64 aktiv
         * hat; DNS64 nur, wenn auf einer verbundenen Node das DNS64-Modul eingeschaltet ist. Monitoring- und Infrastructure-Hub sind vorerst nicht erreichbar.
         */
        val tabList: List<Tab>
            get() = listOfNotNull(
                Tab.domains,
                Tab.healthcheck,
                if (ConnectedNodes.proxyActive) Tab.proxy64 else Tab.cdn,
                Tab.dns64.takeIf { ConnectedNodes.dnsIds.isNotEmpty() },
                Tab.settings
            )

        /** Alle Ziele, die je Tab-Wurzel sein können (unabhängig vom aktuellen Node-Zustand). */
        val rootTabs: Set<Tab> = setOf(Tab.domains, Tab.healthcheck, Tab.cdn, Tab.proxy64, Tab.dns64, Tab.settings)

        @Composable
        fun RowScope.AddItem(
            tabBarItem: Tab,
            navController: NavController
        ) {
            val backStack by navController.currentBackStack.collectAsState()
            // Tab-Wurzel des Stacks (ein Tab-Tipp leert den Stack): so bleibt z. B. DNS64 → Local DNS markiert, auch
            // wenn der Unter-Screen sonst zu More gehört; Proxy64 teilt sich den Platz mit CDN64
            val rootRoute = backStack.firstOrNull { it.destination !is NavGraph }?.destination?.route
            val selected = parentTab(rootRoute) == parentTab(getRoute(tabBarItem))
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(getRoute(tabBarItem)) {
                        popUpTo(navController.graph.id) {
                            saveState = true
                            inclusive = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    TabBarIconView(
                        isSelected = selected,
                        selectedIcon = getSelectedIcon(tabBarItem),
                        unselectedIcon = getUnSelectedIcon(tabBarItem),
                        title = getLabel(tabBarItem)
                    )
                },
                label = {
                    Text(
                        getLabel(tabBarItem),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (selected) FontWeight.Bold else null
                    )
                })
        }

        /** Route eines Ziels; immer der Enum-Name. */
        fun getRoute(tab: Tab): String = tab.name

        /** Ziel zu einer Route (null bei unbekannter Route). */
        fun fromRoute(route: String?): Tab? = route?.let { r -> Tab.entries.firstOrNull { it.name == r } }

        /**
         * Tab der unteren Leiste, zu dem eine Route gehört (für die Hervorhebung auf Unter-Screens).
         * null für welcome/login und unbekannte Routen.
         */
        fun parentTab(route: String?): Tab? = when (fromRoute(route) ?: return null) {
            Tab.welcome, Tab.login -> null

            Tab.domains, Tab.domain_details, Tab.domain_settings,
            Tab.domain_record_edit -> Tab.domains

            Tab.healthcheck, Tab.healthcheck_new, Tab.healthcheck_edit, Tab.healthcheck_details, Tab.node_check_edit, Tab.node_check_details,
            // Vorerst nicht über die Oberfläche erreichbar (Monitoring-Hub und seine Bereiche)
            Tab.monitoring, Tab.hc2_activity, Tab.maintenance, Tab.maintenance_edit, Tab.status_pages,
            Tab.status_page_edit, Tab.failover, Tab.failover_details, Tab.failover_edit, Tab.offline_pages,
            Tab.offline_page_edit -> Tab.healthcheck

            // Proxy64 ersetzt CDN64 in der Leiste: gleicher Platz
            Tab.cdn, Tab.cdn_details, Tab.cdn_settings, Tab.proxy64, Tab.proxy_host_edit -> Tab.cdn

            Tab.dns64, Tab.dns64_details, Tab.node_dns, Tab.node_dns_log, Tab.node_dns_records -> Tab.dns64

            // More: Account, Tools, Benachrichtigungen, Security und Nodes; network (Infrastructure-Hub) vorerst
            // nicht erreichbar
            Tab.settings, Tab.account, Tab.account_details, Tab.account_edit, Tab.api_keys, Tab.account_links,
            Tab.logs, Tab.my_ip, Tab.about, Tab.channels, Tab.channel_new, Tab.channel_edit, Tab.push_setup,
            Tab.hc2_settings, Tab.network, Tab.nodes, Tab.node_details, Tab.node_module,
            Tab.threat_lookup, Tab.threat_stats, Tab.threat_watch, Tab.password_check, Tab.honeypot -> Tab.settings
        }

        /** Titel bzw. Beschriftung eines Ziels (Tab-Leiste, TopAppBar, Hub-Einträge). */
        fun getLabel(tab: Tab): String = when (tab) {
            Tab.welcome -> "Welcome"
            Tab.login -> "Login"
            Tab.domains -> "DynDNS"
            Tab.domain_details -> "Domain"
            Tab.domain_settings -> "Domain settings"
            Tab.domain_record_edit -> "Edit record"
            Tab.monitoring -> "Monitoring"
            Tab.healthcheck -> "Healthchecks"
            Tab.healthcheck_new -> "New healthcheck"
            Tab.healthcheck_edit -> "Edit healthcheck"
            Tab.healthcheck_details -> "Healthcheck"
            Tab.node_check_edit -> "Edit node healthcheck"
            Tab.node_check_details -> "Node healthcheck"
            Tab.node_dns_log -> "Query log"
            Tab.node_dns_records -> "Local DNS records"
            Tab.channels -> "Channels"
            Tab.channel_new -> "New channel"
            Tab.channel_edit -> "Edit channel"
            Tab.push_setup -> "Push notifications"
            Tab.hc2_activity -> "Activity"
            Tab.maintenance -> "Maintenance"
            Tab.maintenance_edit -> "Maintenance window"
            Tab.hc2_settings -> "Notification settings"
            Tab.status_pages -> "Status pages"
            Tab.status_page_edit -> "Status page"
            Tab.failover -> "DNS failover"
            Tab.failover_details -> "Failover policy"
            Tab.failover_edit -> "Edit failover policy"
            Tab.offline_pages -> "Offline pages"
            Tab.offline_page_edit -> "Offline page"
            Tab.network -> "Infrastructure"
            Tab.cdn -> "CDN64"
            Tab.cdn_details -> "CDN domain"
            Tab.cdn_settings -> "CDN settings"
            Tab.proxy64 -> "Proxy64"
            Tab.proxy_host_edit -> "Proxy host"
            Tab.dns64 -> "DNS64"
            Tab.dns64_details -> "DNS64 client"
            Tab.nodes -> "Nodes"
            Tab.node_details -> "Node"
            Tab.node_dns -> "Local DNS"
            Tab.node_module -> "Module"
            Tab.threat_lookup -> "IP lookup & My IP"
            Tab.threat_stats -> "Threat statistics"
            Tab.threat_watch -> "Early warning"
            Tab.password_check -> "Password check"
            Tab.honeypot -> "Honeypot"
            Tab.settings -> "More"
            Tab.account -> "Account"
            Tab.account_details -> "Account details"
            Tab.account_edit -> "Edit account"
            Tab.api_keys -> "API keys"
            Tab.account_links -> "Account links"
            Tab.logs -> "Logs"
            Tab.my_ip -> "My IP"
            Tab.about -> "About"
        }

        /** Icon im ausgewählten Zustand (Tab-Leiste). */
        fun getSelectedIcon(tab: Tab): Int = when (tab) {
            Tab.domains -> R.drawable.domain_24px_fill
            Tab.monitoring -> R.drawable.monitor_heart_24px_fill
            Tab.network -> R.drawable.lan_24px_fill
            Tab.settings -> R.drawable.more_horiz_24px
            Tab.healthcheck -> R.drawable.ecg_heart_24px_fill
            Tab.channels -> R.drawable.notifications_unread_24px_fill
            else -> getUnSelectedIcon(tab)
        }

        /** Icon im nicht ausgewählten Zustand; auch Standard-Icon für Hub-Einträge (HubSection). */
        fun getUnSelectedIcon(tab: Tab): Int = when (tab) {
            Tab.domains -> R.drawable.domain_24px
            Tab.monitoring -> R.drawable.monitor_heart_24px
            Tab.network -> R.drawable.lan_24px
            Tab.settings -> R.drawable.more_horiz_24px
            Tab.healthcheck, Tab.node_check_edit, Tab.node_check_details -> R.drawable.ecg_heart_24px
            Tab.channels, Tab.push_setup -> R.drawable.notifications_unread_24px
            Tab.hc2_activity -> R.drawable.history_24px
            Tab.maintenance, Tab.maintenance_edit -> R.drawable.construction_24px
            Tab.hc2_settings -> R.drawable.tune_24px
            Tab.status_pages, Tab.status_page_edit -> R.drawable.public_24px
            Tab.failover, Tab.failover_details, Tab.failover_edit -> R.drawable.alt_route_24px
            Tab.offline_pages, Tab.offline_page_edit -> R.drawable.web_asset_off_24px
            Tab.cdn, Tab.cdn_details, Tab.cdn_settings -> R.drawable.cloud_24px
            Tab.proxy64, Tab.proxy_host_edit -> R.drawable.swap_horiz_24px
            Tab.dns64, Tab.dns64_details, Tab.node_dns -> R.drawable.dns_24px
            Tab.nodes, Tab.node_details -> R.drawable.hub_24px
            Tab.node_module -> R.drawable.hub_24px
            Tab.threat_lookup -> R.drawable.search_24px
            Tab.threat_watch -> R.drawable.radar_24px
            Tab.threat_stats -> R.drawable.bar_chart_24px
            Tab.password_check -> R.drawable.password_24px
            Tab.honeypot -> R.drawable.pest_control_24px
            Tab.account, Tab.account_details, Tab.account_edit -> R.drawable.account_circle_24px
            Tab.api_keys -> R.drawable.key_24px
            Tab.account_links -> R.drawable.link_24px
            Tab.logs -> R.drawable.data_info_alert_24px
            Tab.my_ip -> R.drawable.bring_your_own_ip_24px
            Tab.about -> R.drawable.info_24px
            Tab.domain_settings -> R.drawable.tune_24px
            Tab.domain_record_edit -> R.drawable.edit_24px
            else -> R.drawable.settings_24px
        }
    }
}

@Composable
fun TabBarIconView(
    isSelected: Boolean,
    selectedIcon: Int,
    unselectedIcon: Int,
    title: String,
    badgeNotify: Boolean = false,
    badgeAmount: Int? = null
) {
    BadgedBox(badge = { TabBarBadgeView(badgeNotify, badgeAmount) }) {
        Icon(
            painterResource(
                id = if (isSelected) {
                    selectedIcon
                } else {
                    unselectedIcon
                }
            ),
            contentDescription = title
        )
    }
}

@Composable
fun TabBarBadgeView(notification: Boolean = false, count: Int? = null) {
    if (count != null) {
        Badge {
            Text(count.toString())
        }
    } else if (notification) {
        Badge()
    }
}