package de.rpicloud.ipv64net.main.views

import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("ContextCastToActivity")
@Composable
fun SettingsView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val activity = LocalContext.current as ComponentActivity
    val scope = rememberCoroutineScope()
    var showLoadingDialog by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var errorDialogTitle by remember { mutableStateOf("") }
    var errorDialogText by remember { mutableStateOf("") }
    var errorDialogButtonText by remember { mutableIntStateOf(android.R.string.ok) }

//    var server64 by remember { mutableStateOf(Server64.empty) }

//    fun getServers() {
//        showLoadingDialog = true
//        scope.launch(Dispatchers.IO) {
//            NetworkService(ctx).GetServers { nwResult ->
//                showLoadingDialog = false
//                when (nwResult.status) {
//                    200 -> {
//                        if (nwResult.data == null) {
//                            println(nwResult.message)
//                            errorDialogTitle = "Data not found"
//                            errorDialogText = "There are no data found!"
//                            errorDialogButtonText = R.string.retry
//                            showDialog = true
//                        } else {
//                            (nwResult.data as Server64).also { server64 = it }
//                            println(server64)
//                            createPoints()
//                        }
//                    }
//
//                    400 -> {
//                        println(nwResult.message)
//                        errorDialogTitle = "Loading error"
//                        errorDialogText = nwResult.message.toString()
//                        errorDialogButtonText = R.string.retry
//                        showDialog = true
//                    }
//
//                    else -> {
//                        println(nwResult.message)
//                        errorDialogTitle = "Loading error"
//                        errorDialogText = nwResult.message.toString()
//                        errorDialogButtonText = R.string.retry
//                        showDialog = true
//                    }
//                }
//            }
//        }
//    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(Tabs.getLabel(Tab.settings))
                }, modifier = Modifier.statusBarsPadding()
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(mainPadding)
            .consumeWindowInsets(mainPadding)  // avoids double insets
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .fillMaxHeight()
        ) {

        }
    }

    LaunchedEffect(Unit) {
//        getServers()
    }

    if (showLoadingDialog) {
        SpinnerDialog(
            spinnerText = "Loading..."
        )
    }

    if (showDialog) {
        ErrorDialog(
            onDismissRequest = { showDialog = false },
            onConfirmation = { showDialog = false;
//                getServers()
            },
            dialogTitle = errorDialogTitle,
            dialogText = errorDialogText,
            dialogConfirmText = errorDialogButtonText
        )
    }
}