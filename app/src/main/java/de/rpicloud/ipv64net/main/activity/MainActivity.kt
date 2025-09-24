package de.rpicloud.ipv64net.main.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.rpicloud.ipv64net.main.views.DomainView
import de.rpicloud.ipv64net.main.views.HealthcheckView
import de.rpicloud.ipv64net.main.views.IntegrationsView
import de.rpicloud.ipv64net.main.views.SettingsView
import de.rpicloud.ipv64net.models.Integration
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.rpicloud.ipv64net.models.Tabs.Companion.AddItem
import de.rpicloud.ipv64net.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            AppTheme {
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
                        composable(Tabs.Companion.getRoute(Tab.integrations)) {
                            IntegrationsView(navController, mainPadding = mainPadding)
                        }
                        composable(Tabs.Companion.getRoute(Tab.settings)) {
                            SettingsView(navController, mainPadding = mainPadding)
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

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AppTheme {
        Greeting("Android")
    }
}