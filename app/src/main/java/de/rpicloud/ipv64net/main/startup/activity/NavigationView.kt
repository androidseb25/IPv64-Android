package de.rpicloud.ipv64net.main.startup.activity

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import de.rpicloud.ipv64net.main.startup.views.LoginView
import de.rpicloud.ipv64net.main.startup.views.WelcomeView
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationView(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Tabs.getRoute(Tab.welcome)) {
        composable(Tabs.getRoute(Tab.welcome)) {
            WelcomeView(navController = navController)
        }
        composable(Tabs.getRoute(Tab.login)) {
            LoginView(navController = navController)
        }
    }
}