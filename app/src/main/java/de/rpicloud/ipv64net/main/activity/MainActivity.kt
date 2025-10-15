package de.rpicloud.ipv64net.main.activity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.BiometricPromptManager
import de.rpicloud.ipv64net.helper.PreferencesManager
import de.rpicloud.ipv64net.main.views.AboutView
import de.rpicloud.ipv64net.main.views.AccountView
import de.rpicloud.ipv64net.main.views.DomainDetailView
import de.rpicloud.ipv64net.main.views.DomainDnsNewView
import de.rpicloud.ipv64net.main.views.DomainNewView
import de.rpicloud.ipv64net.main.views.DomainView
import de.rpicloud.ipv64net.main.views.HealthcheckDetailView
import de.rpicloud.ipv64net.main.views.HealthcheckEditView
import de.rpicloud.ipv64net.main.views.HealthcheckIntegrationView
import de.rpicloud.ipv64net.main.views.HealthcheckNewView
import de.rpicloud.ipv64net.main.views.HealthcheckView
import de.rpicloud.ipv64net.main.views.IntegrationsView
import de.rpicloud.ipv64net.main.views.LogView
import de.rpicloud.ipv64net.main.views.MyIpView
import de.rpicloud.ipv64net.main.views.SettingsView
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.rpicloud.ipv64net.models.Tabs.Companion.AddItem
import de.rpicloud.ipv64net.ui.theme.AppTheme

class MainActivity : AppCompatActivity() {

    private val promptManager by lazy {
        BiometricPromptManager(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            AppTheme {

                val isBiometric: Boolean = PreferencesManager.loadBool(applicationContext, "LOCKSCREEN_ENABLED")
                val biometricResult by promptManager.promptResults.collectAsState(initial = null)
                val enrollLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { println("Activity result $it") }

                if (!isBiometric || biometricResult == BiometricPromptManager.BiometricResult.AuthenticationSuccess) {
                    Scaffold(bottomBar = { TabView(Tabs.tabList, navController) }) { mainPadding ->
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
                            composable(Tabs.Companion.getRoute(Tab.healthcheck_edit_integrations)) {
                                HealthcheckIntegrationView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.Companion.getRoute(Tab.integrations)) {
                                IntegrationsView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.Companion.getRoute(Tab.settings)) {
                                SettingsView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.Companion.getRoute(Tab.domain_new)) {
                                DomainNewView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.Companion.getRoute(Tab.domain_details)) {
                                DomainDetailView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.Companion.getRoute(Tab.domain_new_dns)) {
                                DomainDnsNewView(navController, mainPadding = mainPadding)
                            }
                            composable(Tabs.Companion.getRoute(Tab.account)) {
                                AccountView(navController, mainPadding = mainPadding)
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
                        }
                    }
                } else {
                    LockView(modifier = Modifier, promptManager)
                }

                LaunchedEffect(Unit) {
                    if (isBiometric) {
                        promptManager.showBiometricPrompt(
                            "Authentication Required",
                            "Please authenticate to continue"
                        )
                    }
                }

                LaunchedEffect(biometricResult) {
                    if (biometricResult is BiometricPromptManager.BiometricResult.AuthenticationNotSet) {
                        if (Build.VERSION.SDK_INT >= 30) {
                            val enrollIntent = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                                putExtra(
                                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                                )
                            }
                            enrollLauncher.launch(enrollIntent)
                        }
                    }
                }

            }
        }
    }
}

@Composable
fun TabView(tabBarItems: List<Tab>, navController: NavController) {

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    navBackStackEntry?.destination?.route
    val isHide = false
    if (!isHide) {
        NavigationBar {
            // looping over each tab to generate the views and navigation for each item
            tabBarItems.forEachIndexed { _, tabBarItem ->
                AddItem(tabBarItem = tabBarItem, navController)
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Composable
fun LockView(modifier: Modifier = Modifier, promptManager: BiometricPromptManager?) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
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
fun GreetingPreview() {

    AppTheme {
        LockView(Modifier, null)
    }
}