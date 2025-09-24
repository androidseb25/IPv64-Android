package de.rpicloud.ipv64net.main.views

import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.NetworkService
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.models.DomainResult
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("ContextCastToActivity")
@Composable
fun DomainView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showLoadingDialog by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var errorDialogTitle by remember { mutableStateOf("") }
    var errorDialogText by remember { mutableStateOf("") }
    var errorDialogButtonText by remember { mutableIntStateOf(android.R.string.ok) }

    var domainResult by remember { mutableStateOf(DomainResult.empty) }

    rememberCoroutineScope()

    fun getDomains() {
        showLoadingDialog = true
        scope.launch(Dispatchers.IO) {
            NetworkService(ctx).GetDomains { nwResult ->
                showLoadingDialog = false
                when (nwResult.status) {
                    200 -> {
                        if (nwResult.data == null) {
                            println(nwResult.message)
                            errorDialogTitle = "Data not found"
                            errorDialogText = "There are no data found!"
                            errorDialogButtonText = R.string.retry
                            showDialog = true
                        } else {
                            (nwResult.data as DomainResult).also { domainResult = it }
                            println(domainResult)
                        }
                    }

                    400 -> {
                        println(nwResult.message)
                        errorDialogTitle = "Loading error"
                        errorDialogText = nwResult.message.toString()
                        errorDialogButtonText = R.string.retry
                        showDialog = true
                    }

                    else -> {
                        println(nwResult.message)
                        errorDialogTitle = "Loading error"
                        errorDialogText = nwResult.message.toString()
                        errorDialogButtonText = R.string.retry
                        showDialog = true
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(Tabs.getLabel(Tab.domains))
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
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    items = domainResult.subdomains,
                    key = { d -> d.domain_update_hash!! } // stabile Keys
                ) { d ->
                    DomainItemView(d) { selectedDomain ->
                        println(selectedDomain)
//                            val gson = Gson()
//                            val selectedGwAsString = gson.toJson(selectedGw).toString()
//                            navController.currentBackStackEntry?.savedStateHandle?.set("SELECTED_GW", selectedGwAsString)
//                            navController.navigate(Tabs.getRoute(Tab.peers))
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        getDomains()
    }

    if (showLoadingDialog) {
        SpinnerDialog(
            spinnerText = "Loading..."
        )
    }

    if (showDialog) {
        ErrorDialog(
            onDismissRequest = { showDialog = false },
            onConfirmation = { showDialog = false; getDomains() },
            dialogTitle = errorDialogTitle,
            dialogText = errorDialogText,
            dialogConfirmText = errorDialogButtonText
        )
    }
}