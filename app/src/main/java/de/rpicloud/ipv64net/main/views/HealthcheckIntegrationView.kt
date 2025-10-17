package de.rpicloud.ipv64net.main.views

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.NetworkService
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.helper.views.RequestDialogs
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.models.IntegrationResult
import de.rpicloud.ipv64net.models.RequestTyp
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("UnrememberedGetBackStackEntry")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthcheckIntegrationView(
    navController: NavHostController,
    mainPadding: PaddingValues
) {
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showLoadingDialog by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var errorDialogTitle by remember { mutableStateOf("") }
    var errorDialogText by remember { mutableStateOf("") }
    var errorDialogButtonText by remember { mutableIntStateOf(android.R.string.ok) }

    var integrationResult by remember { mutableStateOf(IntegrationResult.empty) }

    var showRequestDialog by remember { mutableStateOf(false) }
    var requestType by remember { mutableStateOf(RequestTyp.UnAuthorized) }

    val hcRoute = Tabs.getRoute(Tab.healthcheck_edit)
    var hcBackStackEntry = remember(hcRoute) {
        navController.getBackStackEntry(hcRoute)
    }
    val integrationString by hcBackStackEntry.savedStateHandle.getStateFlow<String>(
        "INTEGRATION_STRING",
        ""
    ).collectAsState()

    fun getIntegrations() {
        showLoadingDialog = true
        scope.launch(Dispatchers.IO) {
            NetworkService(ctx).GetIntegrations { nwResult ->
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
                            (nwResult.data as IntegrationResult).also { integrationResult = it }
                            println(integrationResult)
                            integrationResult.integration.forEach { integration ->
                                if (integrationString.contains("${integration.integration_id}")) {
                                    integration.selectedState = true
                                }
                            }
                        }
                    }

                    400 -> {
                        println(nwResult.message)
                        errorDialogTitle = "Loading error"
                        errorDialogText = nwResult.message.toString()
                        errorDialogButtonText = R.string.retry
                        showDialog = true
                    }

                    401 -> {
                        requestType = RequestTyp.UnAuthorized
                        showRequestDialog = true
                    }

                    403 -> {
                        requestType = if ((nwResult.data as String).contains("domain limit reached")) {
                            RequestTyp.DomainLimitReached
                        }
                        else if ((nwResult.data as String).contains("domainname not available"))
                            RequestTyp.DomainNotAvailable
                        else
                            RequestTyp.DomainRulesNotCreated

                        showRequestDialog = true
                    }

                    429 -> {
                        requestType = if ((nwResult.data as String).contains("Updateintervall overcommited")) {
                            RequestTyp.TooManyRequests
                        } else
                            RequestTyp.UpdateCoolDown
                        showRequestDialog = true
                    }

                    500 -> {
                        requestType = RequestTyp.WebsiteRequestFailed
                        showRequestDialog = true
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
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_back_24px),
                            contentDescription = "Close"
                        )
                    }
                },
                title = {
                    Text("Notification methods")
                }, modifier = Modifier.statusBarsPadding(),
                actions = {
                    IconButton(onClick = {
                        val selectedList = integrationResult.integration.filter { it.selectedState }
                        var selectedListString = "";
                        selectedList.forEach { integration ->
                            selectedListString += if (selectedListString.isEmpty()) {
                                "${integration.integration_id}"
                            } else {
                                "_${integration.integration_id}"
                            }
                        }
                        navController.previousBackStackEntry?.savedStateHandle?.set(
                            "INTEGRATION_IDS",
                            selectedListString
                        )
                        navController.popBackStack()
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.save_24px),
                            contentDescription = "Save"
                        )
                    }
                }
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
                    items = integrationResult.integration,
                    key = { integr -> integr.integration_id ?: 0 } // stabile Keys
                ) { integr ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(integr.integration_name ?: "")
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = integr.selectedState,
                            onCheckedChange = { selected -> integr.selectedState = selected; println(integr) }
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        getIntegrations()
    }

    if (showLoadingDialog) {
        SpinnerDialog(
            spinnerText = "Loading..."
        )
    }

    if (showDialog) {
        ErrorDialog(
            onDismissRequest = { showDialog = false },
            onConfirmation = { showDialog = false; getIntegrations() },
            dialogTitle = errorDialogTitle,
            dialogText = errorDialogText,
            dialogConfirmText = errorDialogButtonText
        )
    }

    if (showRequestDialog) {
        RequestDialogs(
            onDismissRequest = { showRequestDialog = false },
            onConfirmation = { showRequestDialog = false; },
            dialogConfirmText = errorDialogButtonText,
            request = requestType
        )
    }
}