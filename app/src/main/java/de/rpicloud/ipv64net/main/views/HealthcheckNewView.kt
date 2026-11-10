package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

// Dünner Wrapper: neuer Check
@Composable
fun HealthcheckNewView(navController: NavHostController, mainPadding: PaddingValues) {
    HealthcheckForm(navController, mainPadding, editToken = null)
}
