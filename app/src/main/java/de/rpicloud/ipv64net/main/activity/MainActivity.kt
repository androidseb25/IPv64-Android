package de.rpicloud.ipv64net.main.activity

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.BiometricPromptManager
import de.rpicloud.ipv64net.helper.PreferencesManager
import de.rpicloud.ipv64net.helper.PushChannelRegistrar
import de.rpicloud.ipv64net.helper.PushSetupStore
import de.rpicloud.ipv64net.main.startup.views.LoginView
import de.rpicloud.ipv64net.main.views.AboutView
import de.rpicloud.ipv64net.main.views.ChannelSetupView
import de.rpicloud.ipv64net.main.views.AccountDetailView
import de.rpicloud.ipv64net.main.views.AccountEditView
import de.rpicloud.ipv64net.main.views.AccountView
import de.rpicloud.ipv64net.main.views.DomainDetailView
import de.rpicloud.ipv64net.main.views.DomainView
import de.rpicloud.ipv64net.main.views.HealthcheckDetailView
import de.rpicloud.ipv64net.main.views.HealthcheckEditView
import de.rpicloud.ipv64net.main.views.HealthcheckNewView
import de.rpicloud.ipv64net.main.views.HealthcheckView
import de.rpicloud.ipv64net.main.views.ChannelsView
import de.rpicloud.ipv64net.main.views.PushSetupView
import de.rpicloud.ipv64net.main.views.LogView
import de.rpicloud.ipv64net.main.views.MyIpView
import de.rpicloud.ipv64net.main.views.SettingsView
import de.rpicloud.ipv64net.main.views.DomainRecordEditView
import de.rpicloud.ipv64net.main.views.DomainSettingsView
import de.rpicloud.ipv64net.main.views.account.AccountLinksView
import de.rpicloud.ipv64net.main.views.account.ApiKeysView
import de.rpicloud.ipv64net.main.views.monitoring.FailoverDetailView
import de.rpicloud.ipv64net.main.views.monitoring.FailoverEditView
import de.rpicloud.ipv64net.main.views.monitoring.FailoverView
import de.rpicloud.ipv64net.main.views.monitoring.Hc2ActivityView
import de.rpicloud.ipv64net.main.views.monitoring.Hc2SettingsView
import de.rpicloud.ipv64net.main.views.monitoring.MaintenanceEditView
import de.rpicloud.ipv64net.main.views.monitoring.MaintenanceView
import de.rpicloud.ipv64net.main.views.monitoring.MonitoringView
import de.rpicloud.ipv64net.main.views.monitoring.OfflinePageEditView
import de.rpicloud.ipv64net.main.views.monitoring.OfflinePagesView
import de.rpicloud.ipv64net.main.views.monitoring.StatusPageEditView
import de.rpicloud.ipv64net.main.views.monitoring.StatusPagesView
import de.rpicloud.ipv64net.main.views.network.CdnDetailView
import de.rpicloud.ipv64net.main.views.network.CdnSettingsView
import de.rpicloud.ipv64net.main.views.network.CdnView
import de.rpicloud.ipv64net.main.views.network.ConnectedNodes
import de.rpicloud.ipv64net.main.views.network.ProxyHostEditView
import de.rpicloud.ipv64net.main.views.network.ProxyView
import de.rpicloud.ipv64net.main.views.NodeCheckEditView
import de.rpicloud.ipv64net.main.views.NodeCheckDetailView
import de.rpicloud.ipv64net.main.views.monitoring.Monitor64HostDetailView
import de.rpicloud.ipv64net.main.views.monitoring.Monitor64HostEditView
import de.rpicloud.ipv64net.main.views.network.NodeDnsLogView
import de.rpicloud.ipv64net.main.views.network.NodeDnsRecordsView
import de.rpicloud.ipv64net.main.views.network.NodeDnsView
import de.rpicloud.ipv64net.main.views.network.NodeModuleView
import de.rpicloud.ipv64net.main.views.network.Dns64DetailView
import de.rpicloud.ipv64net.main.views.network.Dns64View
import de.rpicloud.ipv64net.main.views.network.NetworkView
import de.rpicloud.ipv64net.main.views.network.NodeDetailView
import de.rpicloud.ipv64net.main.views.network.NodesView
import de.rpicloud.ipv64net.main.views.security.HoneypotView
import de.rpicloud.ipv64net.main.views.security.PasswordCheckView
import de.rpicloud.ipv64net.main.views.security.ThreatLookupView
import de.rpicloud.ipv64net.main.views.security.ThreatStatsView
import de.rpicloud.ipv64net.main.views.security.ThreatWatchView
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.rpicloud.ipv64net.models.Tabs.Companion.AddItem
import de.rpicloud.ipv64net.models.User
import de.rpicloud.ipv64net.ui.theme.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Routen der unteren Tab-Leiste; nur dort darf die Push-Einrichtung automatisch starten
private val bottomTabRoutes: Set<String> = Tabs.rootTabs.map { Tabs.getRoute(it) }.toSet()

class MainActivity : AppCompatActivity() {

    private val promptManager by lazy {
        BiometricPromptManager(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        User.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            AppTheme {
                // Sperre beim Kaltstart und nach jedem Wechsel in den Hintergrund (ON_STOP).
                // Der Biometrie-Dialog ist ab API 28 ein System-Overlay und stoppt die Activity nicht (keine Sperrschleife).
                var isLocked by remember { mutableStateOf(PreferencesManager.loadBool(applicationContext, "LOCKSCREEN_ENABLED")) }

                LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
                    if (PreferencesManager.loadBool(applicationContext, "LOCKSCREEN_ENABLED")) {
                        isLocked = true
                    }
                }

                LifecycleEventEffect(Lifecycle.Event.ON_START) {
                    if (isLocked) {
                        promptManager.showBiometricPrompt(
                            "Authentication Required",
                            "Please authenticate to continue"
                        )
                    }
                }

                LaunchedEffect(Unit) {
                    promptManager.promptResults.collect { result ->
                        when (result) {
                            BiometricPromptManager.BiometricResult.AuthenticationSuccess,
                            // Keine Biometrie/Gerätesperre nutzbar: nicht dauerhaft aussperren
                            BiometricPromptManager.BiometricResult.AuthenticationNotSet,
                            BiometricPromptManager.BiometricResult.HardwareUnavailable,
                            BiometricPromptManager.BiometricResult.FeatureUnavailable -> isLocked = false
                            else -> Unit
                        }
                    }
                }

                // NavHost bleibt auch gesperrt erhalten (Eingaben, Dialoge, Back-Stack); LockView liegt darüber
                Box(modifier = Modifier.fillMaxSize()) {
                    // Erstes Einrichten der Push-Benachrichtigungen (einmal pro Account und Gerät), nur auf Tab-Routen.
                    // PushSetupView ruft markDone() vor popBackStack() auf; zusätzlich höchstens einmal pro Account
                    // und Activity-Sitzung, damit es keine Navigationsschleife geben kann.
                    val ctx = LocalContext.current
                    val scope = rememberCoroutineScope()
                    var pushSetupTriggeredFor by rememberSaveable { mutableStateOf<String?>(null) }
                    var tokenSyncedFor by rememberSaveable { mutableStateOf<String?>(null) }
                    // Verbundene Nodes steuern die Tab-Leiste: Zugänge (Prefs) bei jedem Screen-Wechsel neu lesen
                    // (auch nach Verbinden/Entfernen oder Account-Wechsel), Proxy64-Status nur bei geänderter Liste
                    LaunchedEffect(currentRoute) { ConnectedNodes.refresh(ctx) }
                    LaunchedEffect(ConnectedNodes.ids) { ConnectedNodes.refreshModules(ctx) }
                    // Modul im Node-Webinterface umgeschaltet: beim Zurückkehren in die App erneut prüfen
                    LifecycleEventEffect(Lifecycle.Event.ON_START) { scope.launch { ConnectedNodes.refreshModules(ctx) } }
                    LaunchedEffect(currentRoute, isLocked) {
                        if (isLocked) return@LaunchedEffect
                        val route = currentRoute ?: return@LaunchedEffect
                        if (route !in bottomTabRoutes) return@LaunchedEffect
                        // Gerätetoken im Kanal des aktiven Accounts nachziehen: einmal pro API-Key und Sitzung (auch nach
                        // Account-Wechsel), im Hintergrund und best effort; bei unverändertem Token ohne Anfrage
                        val keyHash = PushSetupStore.apiKeyHash(ctx)
                        if (keyHash != null && tokenSyncedFor != keyHash) {
                            tokenSyncedFor = keyHash
                            scope.launch {
                                try {
                                    PushChannelRegistrar.ensureAccountId(ctx)
                                    PushChannelRegistrar.syncTokenIfChanged(ctx, null)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                        if (!PushSetupStore.shouldShow(ctx)) return@LaunchedEffect
                        // Account-ID zum (evtl. neuen) API-Key ermitteln, damit ein bereits eingerichteter Account
                        // nicht erneut gefragt wird; einmal pro Key, Fehler werden ignoriert
                        PushChannelRegistrar.ensureAccountId(ctx)
                        if (!PushSetupStore.shouldShow(ctx)) return@LaunchedEffect
                        val account = PushSetupStore.accountHash(ctx) ?: return@LaunchedEffect
                        if (pushSetupTriggeredFor == account) return@LaunchedEffect
                        pushSetupTriggeredFor = account
                        navController.navigate(Tabs.getRoute(Tab.push_setup)) {
                            launchSingleTop = true
                        }
                    }

                    Scaffold(
                        // Gesperrt: Inhalt für Bedienungshilfen ausblenden
                        modifier = if (isLocked) Modifier.clearAndSetSemantics {} else Modifier,
                        bottomBar = {
                        if (currentRoute != Tabs.Companion.getRoute(Tab.login) &&
                            currentRoute != Tabs.Companion.getRoute(Tab.push_setup)
                        )
                            TabView(Tabs.tabList, navController)
                    }) { mainPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = Tabs.Companion.getRoute(Tab.domains)
                        ) {
                            composable(Tabs.Companion.getRoute(Tab.domains)) {
                                DomainView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.Companion.getRoute(Tab.healthcheck)) {
                                HealthcheckView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.Companion.getRoute(Tab.healthcheck_details)) {
                                HealthcheckDetailView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.Companion.getRoute(Tab.healthcheck_new)) {
                                HealthcheckNewView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.Companion.getRoute(Tab.healthcheck_edit)) {
                                HealthcheckEditView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.node_check_edit)) {
                                NodeCheckEditView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.node_check_details)) {
                                NodeCheckDetailView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.monitor64_host_edit)) {
                                Monitor64HostEditView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.monitor64_host_details)) {
                                Monitor64HostDetailView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.node_dns_log)) {
                                NodeDnsLogView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.node_dns_records)) {
                                NodeDnsRecordsView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.Companion.getRoute(Tab.channels)) {
                                ChannelsView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.Companion.getRoute(Tab.channel_new)) {
                                ChannelSetupView(navController, mainPadding = mainPadding, editMode = false)
                            }
                            composable(Tabs.Companion.getRoute(Tab.channel_edit)) {
                                ChannelSetupView(navController, mainPadding = mainPadding, editMode = true)
                            }
                            composable(Tabs.Companion.getRoute(Tab.push_setup)) {
                                PushSetupView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.Companion.getRoute(Tab.settings)) {
                                SettingsView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.Companion.getRoute(Tab.domain_details)) {
                                DomainDetailView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.Companion.getRoute(Tab.account)) {
                                AccountView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.Companion.getRoute(Tab.account_details)) {
                                AccountDetailView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.Companion.getRoute(Tab.account_edit)) {
                                AccountEditView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.Companion.getRoute(Tab.logs)) {
                                LogView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.Companion.getRoute(Tab.my_ip)) {
                                MyIpView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.Companion.getRoute(Tab.about)) {
                                AboutView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.Companion.getRoute(Tab.login)) {
                                LoginView(navController, true)
                            }

                            // Neue Bereiche (Plan 2); Signatur aller Screens: (navController, mainPadding)
                            composable(Tabs.getRoute(Tab.domain_settings)) {
                                DomainSettingsView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.domain_record_edit)) {
                                DomainRecordEditView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.monitoring)) {
                                MonitoringView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.hc2_activity)) {
                                Hc2ActivityView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.maintenance)) {
                                MaintenanceView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.maintenance_edit)) {
                                MaintenanceEditView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.hc2_settings)) {
                                Hc2SettingsView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.status_pages)) {
                                StatusPagesView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.status_page_edit)) {
                                StatusPageEditView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.failover)) {
                                FailoverView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.failover_details)) {
                                FailoverDetailView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.failover_edit)) {
                                FailoverEditView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.offline_pages)) {
                                OfflinePagesView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.offline_page_edit)) {
                                OfflinePageEditView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.network)) {
                                NetworkView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.cdn)) {
                                CdnView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.cdn_details)) {
                                CdnDetailView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.cdn_settings)) {
                                CdnSettingsView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.proxy64)) {
                                ProxyView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.proxy_host_edit)) {
                                ProxyHostEditView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.dns64)) {
                                // DNS64 bezieht sich nur auf die Nodes; Cloud-DNS64 (Dns64View) vorerst nicht erreichbar
                                NodeDnsView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.dns64_details)) {
                                Dns64DetailView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.nodes)) {
                                NodesView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.node_details)) {
                                NodeDetailView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.node_dns)) {
                                NodeDnsView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.node_module)) {
                                NodeModuleView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.threat_lookup)) {
                                ThreatLookupView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.threat_stats)) {
                                ThreatStatsView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.threat_watch)) {
                                ThreatWatchView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.password_check)) {
                                PasswordCheckView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.honeypot)) {
                                HoneypotView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.api_keys)) {
                                ApiKeysView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.getRoute(Tab.account_links)) {
                                AccountLinksView(navController, mainPadding = mainPadding)
                            }
                        }
                    }

                    if (isLocked) {
                        // Deckt den Inhalt im selben Fenster sofort ab (kein Aufblitzen beim Kaltstart)
                        LockView(modifier = Modifier, promptManager)
                        // Eigenes Fenster über bereits offenen Dialogen der Screens (z. B. Geheimnis-Anzeige), die sonst
                        // über der Sperre sichtbar blieben. Fängt auch Zurück ab (nicht an den verdeckten NavHost):
                        // App wie bisher verlassen, der Zustand bleibt erhalten
                        Dialog(
                            onDismissRequest = { moveTaskToBack(true) },
                            properties = DialogProperties(
                                dismissOnClickOutside = false,
                                usePlatformDefaultWidth = false,
                                decorFitsSystemWindows = false
                            )
                        ) {
                            LockView(modifier = Modifier, promptManager)
                        }
                    }
                }

            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // uiMode wird selbst behandelt (configChanges, keine Neuerstellung): Systemleisten-Icons an Hell/Dunkel anpassen
        enableEdgeToEdge()
    }
}

@Composable
fun TabView(tabBarItems: List<Tab>, navController: NavController) {

    NavigationBar {
        // looping over each tab to generate the views and navigation for each item
        tabBarItems.forEachIndexed { _, tabBarItem ->
            AddItem(tabBarItem = tabBarItem, navController)
        }
    }
}

@Composable
fun LockView(modifier: Modifier = Modifier, promptManager: BiometricPromptManager?) {
    Scaffold(
        // Deckend (Scaffold-Hintergrund) und schluckt alle Berührungen, damit darunter nichts bedienbar ist
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {}
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .consumeWindowInsets(it)  // avoids double insets
        ) {
            Icon(
                painter = painterResource(id = R.drawable.fingerprint_24px),
                contentDescription = "Delete",
                modifier = Modifier.size(65.dp)
            )
            Button(onClick = {
                promptManager.let {
                    it?.showBiometricPrompt(
                        "Authentication Required",
                        "Please authenticate to continue"
                    )
                }
            }, modifier = Modifier.padding(top = 25.dp)) {
                Text("Start Authentication")
            }
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun LockViewPreview() {

    AppTheme {
        LockView(Modifier, null)
    }
}