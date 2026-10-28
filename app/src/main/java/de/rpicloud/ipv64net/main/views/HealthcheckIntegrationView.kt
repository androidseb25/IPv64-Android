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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.ipv64Request
import de.rpicloud.ipv64net.helper.toError
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.helper.views.RequestDialogs
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.models.RequestTyp
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.Integration
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

    // Integrationen aus API v2; die IDs entsprechen den integration_id-Werten aus API v1 (0 = Account-E-Mail)
    val integrations = remember { mutableStateListOf<Integration>() }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    var showRequestDialog by remember { mutableStateOf(false) }
    var requestType by remember { mutableStateOf(RequestTyp.UnAuthorized) }

    val hcRoute = Tabs.getRoute(Tab.healthcheck_edit)
    var hcBackStackEntry = remember(hcRoute) {
        navController.getBackStackEntry(hcRoute)
    }
    // Jetpack Compose Best Practice: collectAsStateWithLifecycle pauses flow collection when UI is stopped (e.g. backgrounded), saving battery and CPU.
    val integrationString by hcBackStackEntry.savedStateHandle.getStateFlow<String>(
        "INTEGRATION_STRING",
        ""
    ).collectAsStateWithLifecycle()

    fun getIntegrations() {
        showLoadingDialog = true
        scope.launch {
            try {
                val list = ipv64Request { IPv64Client.get(ctx).integrations.list() }
                integrations.clear()
                // Ohne ID kann eine Integration nicht für den Healthcheck ausgewählt werden
                integrations.addAll(list.filter { it.id != null })
                // INTEGRATION_STRING enthält die bisher gewählten IDs, getrennt mit "_"
                val preselected = integrationString.split(Regex("[^0-9]+"))
                    .mapNotNull { it.toLongOrNull() }
                    .toSet()
                selectedIds = integrations.mapNotNull { it.id }.filter { it in preselected }.toSet()
            } catch (e: IPv64Exception) {
                println(e.message)
                val error = e.toError()
                if (error.requestType != null) {
                    requestType = error.requestType
                    showRequestDialog = true
                } else {
                    errorDialogTitle = error.title
                    errorDialogText = error.text
                    errorDialogButtonText = R.string.retry
                    showDialog = true
                }
            } finally {
                showLoadingDialog = false
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
                        // Gleiches Format wie bisher: IDs in Listenreihenfolge, getrennt mit "_"
                        val selectedListString = integrations
                            .mapNotNull { it.id }
                            .filter { it in selectedIds }
                            .joinToString("_")
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
                    items = integrations,
                    key = { integr -> integr.id ?: 0L } // stabile Keys
                ) { integr ->
                    val id = integr.id ?: 0L
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(integr.displayName)
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = id in selectedIds,
                            onCheckedChange = { selected ->
                                selectedIds = if (selected) selectedIds + id else selectedIds - id
                            }
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