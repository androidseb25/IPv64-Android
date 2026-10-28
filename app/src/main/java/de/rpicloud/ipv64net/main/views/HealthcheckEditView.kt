package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.helper.Hc2NavKeys
import de.rpicloud.ipv64net.helper.views.ErrorDialog

// Dünner Wrapper: Check bearbeiten, der Token kommt aus der Detailansicht
@Composable
fun HealthcheckEditView(navController: NavHostController, mainPadding: PaddingValues) {
    val token = remember {
        navController.previousBackStackEntry?.savedStateHandle?.get<String>(Hc2NavKeys.EDIT_HC2_TOKEN)
    }
    if (token.isNullOrBlank()) {
        // Ohne Token nicht im Anlegen-Modus weiterlaufen
        var show by remember { mutableStateOf(true) }
        if (show) ErrorDialog(
            onDismissRequest = {},
            onConfirmation = { show = false; navController.popBackStack() },
            dialogTitle = "Healthcheck not found",
            dialogText = "No healthcheck was selected for editing."
        )
        return
    }
    HealthcheckForm(navController, mainPadding, editToken = token)
}
