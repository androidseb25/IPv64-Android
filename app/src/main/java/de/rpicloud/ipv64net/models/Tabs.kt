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

enum class Tab {
    welcome, login, domains, domain_details, domain_new, healthcheck, integrations, settings, account
}

sealed class Tabs {
    companion object {
        val tabList: List<Tab> = listOf(Tab.domains, Tab.healthcheck, Tab.integrations, Tab.settings)

        @Composable
        fun RowScope.AddItem(
            tabBarItem: Tab,
            navController: NavController
        ) {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            NavigationBarItem(
                selected = currentRoute == getRoute(tabBarItem),
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
                        isSelected = currentRoute == getRoute(tabBarItem),
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
                        fontWeight = if (currentRoute == getRoute(tabBarItem)) FontWeight.Bold else null
                    )
                })
        }

        fun getRoute(tab: Tab): String {
            return when (tab) {
                Tab.welcome -> "welcome"
                Tab.login -> "login"
                Tab.domains -> "domains"
                Tab.healthcheck -> "healthcheck"
                Tab.integrations -> "integrations"
                Tab.settings -> "settings"
                Tab.account -> "account"
                Tab.domain_details -> "domain_details"
                Tab.domain_new -> "domain_new"
            }
        }

        fun getLabel(tab: Tab): String {
            return when (tab) {
                Tab.domains -> "Domains"
                Tab.healthcheck -> "Healthchecks"
                Tab.integrations -> "Integrations"
                else -> "Settings"
            }
        }

        fun getSelectedIcon(tab: Tab): Int {
            return when (tab) {
                Tab.domains -> R.drawable.domain_24px_fill
                Tab.healthcheck -> R.drawable.ecg_heart_24px_fill
                Tab.integrations -> R.drawable.extension_24px_fill
                else -> R.drawable.settings_24px_fill
            }
        }

        fun getUnSelectedIcon(tab: Tab): Int {
            return when (tab) {
                Tab.domains -> R.drawable.domain_24px
                Tab.healthcheck -> R.drawable.ecg_heart_24px
                Tab.integrations -> R.drawable.extension_24px
                else -> R.drawable.settings_24px
            }
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