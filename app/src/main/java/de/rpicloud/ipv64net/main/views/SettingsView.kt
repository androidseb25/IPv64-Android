package de.rpicloud.ipv64net.main.views

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.PreferencesManager
import de.rpicloud.ipv64net.helper.findActivity
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.main.startup.activity.LoginActivity
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.preference
import me.zhanghai.compose.preference.rememberPreferenceState
import me.zhanghai.compose.preference.switchPreference

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("ContextCastToActivity")
@Composable
fun SettingsView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val listState = rememberLazyListState()

    ProvidePreferenceLocals {
        val lockScreenEnabled = rememberPreferenceState("LOCKSCREEN_ENABLED", false)
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(Tabs.getLabel(Tab.settings))
                    },
                    modifier = Modifier.statusBarsPadding()
                )
            }, modifier = Modifier
                .fillMaxSize()
                .padding(mainPadding)
                .consumeWindowInsets(mainPadding)  // avoids double insets
        ) { it ->
            Column(modifier = Modifier.padding(it)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    stickyHeader {
                        Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = "General",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp)
                            )
                        }
                    }
                    preference(
                        key = "PREF_ACCOUNT_STATS",
                        title = { Text(text = "Account") },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.account_circle_24px),
                                contentDescription = "Account Icon"
                            )
                        }
                    ) {
                        navController.navigate(Tabs.getRoute(Tab.account))
                    }
                    preference(
                        key = "PREF_ACCOUNT_LOGS",
                        title = { Text(text = "Logs") },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.data_info_alert_24px),
                                contentDescription = "Log Icon"
                            )
                        }
                    ) {
                        navController.navigate(Tabs.getRoute(Tab.logs))
                    }
                    preference(
                        key = "PREF_ACCOUNT_MY_IP",
                        title = { Text(text = "My IP") },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.bring_your_own_ip_24px),
                                contentDescription = "IP Icon"
                            )
                        }
                    ) {
                        navController.navigate(Tabs.getRoute(Tab.my_ip))
                    }
                    stickyHeader {
                        Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = "Security",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp)
                            )
                        }
                    }
                    switchPreference(
                        key = "PREF_SECURITY_SCREENLOCK",
                        title = { Text(text = "Lockscreen") },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.fingerprint_24px),
                                contentDescription = "Lockscreen Icon"
                            )
                        },
                        summary = {
                            Text(if (lockScreenEnabled.value) "enabled" else "disabled")
                        },
                        defaultValue = false,
                        rememberState = { lockScreenEnabled }
                    )
                    stickyHeader {
                        Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = "Other",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp)
                            )
                        }
                    }
                    preference(
                        key = "PREF_OTHER_ABOUT",
                        title = { Text(text = "About") },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.info_24px),
                                contentDescription = "About Icon"
                            )
                        }
                    ) {
                        navController.navigate(Tabs.getRoute(Tab.about))
                    }
                    preference(
                        key = "PREF_OTHER_YT",
                        title = { Text(text = "YouTube") },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.youtube_activity_24px),
                                contentDescription = "YouTube Icon"
                            )
                        }
                    ) {
                        val intent = Intent(Intent.ACTION_VIEW, "https://www.youtube.com/c/RaspberryPiCloud".toUri())
                        ctx.startActivity(intent)
                    }
//                    preference(
//                        key = "PREF_OTHER_DC",
//                        title = { Text(text = "Discord") },
//                        icon = {
//                            Icon(
//                                painter = painterResource(id = R.drawable.discord_24px),
//                                contentDescription = "Discord Icon"
//                            )
//                        }
//                    ) {
//
//                    }
                    item { }
                    preference(
                        key = "PREF_LOGOUT",
                        title = { Text(
                            text = "Logout",
                            fontWeight = FontWeight.Bold,
                            color = Color.Red
                        ) },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.logout_24px),
                                contentDescription = "Discord Icon",
                                tint = Color.Red
                            )
                        }
                    ) {
                        PreferencesManager.saveString(ctx, "APIKEY", "")
                        lockScreenEnabled.value = false
                        val activity = ctx.findActivity()

                        val intent = Intent(activity, LoginActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        activity?.startActivity(intent)
                        activity?.finish()
                    }
                }
            }
        }
    }
}