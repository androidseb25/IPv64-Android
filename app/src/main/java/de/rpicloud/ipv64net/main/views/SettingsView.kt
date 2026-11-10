package de.rpicloud.ipv64net.main.views

import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.CacheRegistry
import de.rpicloud.ipv64net.helper.PreferencesManager
import de.rpicloud.ipv64net.helper.findActivity
import de.rpicloud.ipv64net.main.startup.activity.LoginActivity
import de.rpicloud.ipv64net.main.views.common.HubScreen
import de.rpicloud.ipv64net.main.views.common.HubSection
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.GroupDivider
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.rememberPreferenceState

/**
 * Hub "More": Account (Account, API keys, Account links), Nodes, Notifications (Channels, Notification settings),
 * Security (IP lookup & My IP, Password check, Honeypot), Tools (Logs), App (Lockscreen, About, YouTube, Discord)
 * und Logout als eigene Gruppe. Der Lockscreen-Schalter nutzt weiter die compose-preference-Einstellung
 * "LOCKSCREEN_ENABLED" und spiegelt sie in PreferencesManager.
 */
@Composable
fun SettingsView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current

    ProvidePreferenceLocals {
        val lockScreenEnabled = rememberPreferenceState("LOCKSCREEN_ENABLED", false)

        val sections = listOf(
            HubSection(Tab.account, group = "Account"),
            HubSection(Tab.api_keys, group = "Account"),
            HubSection(Tab.account_links, group = "Account"),
            HubSection(Tab.nodes, subtitle = "Connect a node with its API key", group = "Nodes"),
            HubSection(Tab.channels, subtitle = "Where notifications are sent", group = "Notifications"),
            HubSection(Tab.hc2_settings, subtitle = "Quiet hours and batching", group = "Notifications"),
            HubSection(Tab.threat_lookup, title = "IP lookup & My IP", subtitle = "Your public IP and why an IP is on the blocklist", group = "Security"),
            HubSection(Tab.password_check, subtitle = "Was a password used in attacks? Stays on your device", group = "Security"),
            HubSection(Tab.honeypot, subtitle = "Attacks, ports and rankings of the Honeypot64 network", group = "Security"),
            HubSection(Tab.logs, group = "Tools")
        )

        HubScreen(
            title = Tabs.getLabel(Tab.settings),
            navController = navController,
            mainPadding = mainPadding,
            sections = sections,
            footer = {
                appGroup(
                    lockScreenEnabled = lockScreenEnabled.value,
                    onLockScreenChange = { lockScreenEnabled.value = it },
                    onAbout = { navController.navigate(Tabs.getRoute(Tab.about)) { launchSingleTop = true } },
                    onOpen = { url -> ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                )
                logoutGroup {
                    PreferencesManager.saveString(ctx, "APIKEY", "")
                    // Zwischengespeicherte Daten (u. a. Update-Tokens) aus dem Speicher entfernen
                    CacheRegistry.clearAll()
                    lockScreenEnabled.value = false
                    val activity = ctx.findActivity()

                    val intent = Intent(activity, LoginActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity?.startActivity(intent)
                    activity?.finish()
                }
            }
        )

        LaunchedEffect(lockScreenEnabled.value) {
            PreferencesManager.saveBool(ctx, "LOCKSCREEN_ENABLED", lockScreenEnabled.value)
        }
    }
}

private fun LazyListScope.appGroup(
    lockScreenEnabled: Boolean,
    onLockScreenChange: (Boolean) -> Unit,
    onAbout: () -> Unit,
    onOpen: (String) -> Unit
) {
    item(key = "group_app") {
        ListGroup(title = "App") {
            ListRow(
                title = "Lockscreen",
                subtitle = if (lockScreenEnabled) "enabled" else "disabled",
                icon = R.drawable.fingerprint_24px,
                centerIcon = true,
                trailing = { Switch(checked = lockScreenEnabled, onCheckedChange = null) },
                modifier = Modifier.toggleable(value = lockScreenEnabled, role = Role.Switch, onValueChange = onLockScreenChange)
            )
            GroupDivider(56.dp)
            ListRow(title = "About", icon = R.drawable.info_24px, centerIcon = true, onClick = onAbout)
            GroupDivider(56.dp)
            ListRow(title = "YouTube", icon = R.drawable.youtube_activity_24px, centerIcon = true, onClick = { onOpen("https://www.youtube.com/c/RaspberryPiCloud") })
            GroupDivider(56.dp)
            ListRow(title = "Discord", icon = R.drawable.discord_24px, centerIcon = true, onClick = { onOpen("https://discord.gg/rpicloud") })
        }
    }
}

private fun LazyListScope.logoutGroup(onLogout: () -> Unit) {
    item(key = "group_logout") {
        // etwas Abstand, damit Logout nicht an der App-Gruppe klebt
        ListGroup(modifier = Modifier.padding(top = 16.dp)) {
            val error = MaterialTheme.colorScheme.error
            ListRow(title = "Logout", icon = R.drawable.logout_24px, titleColor = error, iconTint = error, centerIcon = true, onClick = onLogout, showChevron = false)
        }
    }
}
