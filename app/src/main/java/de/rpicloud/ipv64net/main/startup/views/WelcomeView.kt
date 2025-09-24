package de.rpicloud.ipv64net.main.startup.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeView(navController: NavHostController) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.padding(top = 32.dp)) {
                Text(
                    "Welcome to",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "IPv64.net",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "The App for the Free DynDNS2 Service from Dennis Schröder",
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Button(
                    onClick = {
                        navController.navigate(Tabs.getRoute(Tab.login))
                    },
                    modifier = Modifier
                        .padding()
                        .height(53.dp)
                        .fillMaxWidth()
                ) {
                    Text("Let's go!")
                }
            }
        }
    }
}