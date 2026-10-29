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
import androidx.navigation.compose.currentBackStackEntryAsState
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.FeatureFlags

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
    domain_new,
    domain_new_dns,
    domain_settings,
    domain_record_edit,

    // Monitoring
    monitoring,
    healthcheck,
    healthcheck_new,
    healthcheck_edit,
    healthcheck_details,
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
    dns64,
    dns64_details,
    nodes,
    node_details,

    // Security
    security,
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
        /** Tabs der unteren Leiste (Tab-Wurzeln); Network nur mit FeatureFlags.NETWORK_TAB. */
        val tabList: List<Tab> = buildList {
            add(Tab.domains)
            add(Tab.monitoring)
            if (FeatureFlags.NETWORK_TAB) add(Tab.network)
            add(Tab.security)
            add(Tab.settings)
        }

        @Composable
        fun RowScope.AddItem(
            tabBarItem: Tab,
            navController: NavController
        ) {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            // Auch auf Unter-Screens den zugehörigen Tab hervorheben
            val selected = parentTab(currentRoute) == tabBarItem
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

            Tab.domains, Tab.domain_details, Tab.domain_new, Tab.domain_new_dns, Tab.domain_settings,
            Tab.domain_record_edit -> Tab.domains

            Tab.monitoring, Tab.healthcheck, Tab.healthcheck_new, Tab.healthcheck_edit, Tab.healthcheck_details,
            Tab.channels, Tab.channel_new, Tab.channel_edit, Tab.push_setup, Tab.hc2_activity, Tab.maintenance,
            Tab.maintenance_edit, Tab.hc2_settings, Tab.status_pages, Tab.status_page_edit, Tab.failover,
            Tab.failover_details, Tab.failover_edit, Tab.offline_pages, Tab.offline_page_edit -> Tab.monitoring

            Tab.network, Tab.cdn, Tab.cdn_details, Tab.cdn_settings, Tab.dns64, Tab.dns64_details, Tab.nodes,
            Tab.node_details -> Tab.network

            Tab.security, Tab.threat_lookup, Tab.threat_stats, Tab.threat_watch, Tab.password_check,
            Tab.honeypot -> Tab.security

            Tab.settings, Tab.account, Tab.account_details, Tab.account_edit, Tab.api_keys, Tab.account_links,
            Tab.logs, Tab.my_ip, Tab.about -> Tab.settings
        }

        /** Titel bzw. Beschriftung eines Ziels (Tab-Leiste, TopAppBar, Hub-Einträge). */
        fun getLabel(tab: Tab): String = when (tab) {
            Tab.welcome -> "Welcome"
            Tab.login -> "Login"
            Tab.domains -> "Domains"
            Tab.domain_details -> "Domain"
            Tab.domain_new -> "New domain"
            Tab.domain_new_dns -> "New DNS record"
            Tab.domain_settings -> "Domain settings"
            Tab.domain_record_edit -> "Edit record"
            Tab.monitoring -> "Monitoring"
            Tab.healthcheck -> "Healthchecks"
            Tab.healthcheck_new -> "New healthcheck"
            Tab.healthcheck_edit -> "Edit healthcheck"
            Tab.healthcheck_details -> "Healthcheck"
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
            Tab.network -> "Network"
            Tab.cdn -> "CDN"
            Tab.cdn_details -> "CDN domain"
            Tab.cdn_settings -> "CDN settings"
            Tab.dns64 -> "DNS64"
            Tab.dns64_details -> "DNS64 client"
            Tab.nodes -> "Nodes"
            Tab.node_details -> "Node"
            Tab.security -> "Security"
            Tab.threat_lookup -> "IP lookup"
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
            Tab.security -> R.drawable.shield_24px_fill
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
            Tab.security -> R.drawable.shield_24px
            Tab.settings -> R.drawable.more_horiz_24px
            Tab.healthcheck -> R.drawable.ecg_heart_24px
            Tab.channels, Tab.push_setup -> R.drawable.notifications_unread_24px
            Tab.hc2_activity -> R.drawable.history_24px
            Tab.maintenance, Tab.maintenance_edit -> R.drawable.construction_24px
            Tab.hc2_settings -> R.drawable.tune_24px
            Tab.status_pages, Tab.status_page_edit -> R.drawable.public_24px
            Tab.failover, Tab.failover_details, Tab.failover_edit -> R.drawable.alt_route_24px
            Tab.offline_pages, Tab.offline_page_edit -> R.drawable.web_asset_off_24px
            Tab.cdn, Tab.cdn_details, Tab.cdn_settings -> R.drawable.cloud_24px
            Tab.dns64, Tab.dns64_details -> R.drawable.dns_24px
            Tab.nodes, Tab.node_details -> R.drawable.hub_24px
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