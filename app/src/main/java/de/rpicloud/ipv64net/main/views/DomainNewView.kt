package de.rpicloud.ipv64net.main.views

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.NetworkService
import de.rpicloud.ipv64net.helper.v64domains
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.helper.views.RequestDialogs
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.models.AddDomainResult
import de.rpicloud.ipv64net.models.Domain
import de.rpicloud.ipv64net.models.DomainResult
import de.rpicloud.ipv64net.models.IPResult
import de.rpicloud.ipv64net.models.Integration
import de.rpicloud.ipv64net.models.RequestTyp
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainNewView(navController: NavHostController, mainPadding: PaddingValues) {

    val ctx = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var showLoadingDialog by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var domain by remember { mutableStateOf(String().v64domains().first()) }
    var subDomain by remember { mutableStateOf("") }
    var errorDialogTitle by remember { mutableStateOf("") }
    var errorDialogText by remember { mutableStateOf("") }
    var errorDialogButtonText by remember { mutableIntStateOf(android.R.string.ok) }

    var showRequestDialog by remember { mutableStateOf(false) }
    var requestType by remember { mutableStateOf(RequestTyp.UnAuthorized) }

    var newDomainResult by remember { mutableStateOf(AddDomainResult.empty) }

    fun onSave() {
        keyboardController?.hide()
        if (subDomain.isEmpty()) {
            errorDialogTitle = "Empty field"
            errorDialogText = "Please fill the subdomain field!"
            errorDialogButtonText = R.string.retry
            showDialog = true
            return
        }
        val newDomain = "$subDomain.$domain"
        showLoadingDialog = true
        scope.launch(Dispatchers.IO) {
            NetworkService(ctx).PostNewDomain(newDomain) { nwResult ->
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
                            (nwResult.data as AddDomainResult).also { newDomainResult = it }
                            println(newDomainResult)
                            navController.popBackStack()
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
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_back_24px),
                            contentDescription = "Close"
                        )
                    }
                },
                title = {
                    Text("New Domain")
                }, modifier = Modifier.statusBarsPadding(),
                actions = {
                    IconButton(onClick = { onSave() }) {
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
                item {
                    OutlinedTextField(
                        value = subDomain,
                        singleLine = true,
                        label = { Text("eg.: homelab01") },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 25.dp),
                        onValueChange = { newText -> subDomain = newText },
                    )
                }
                item {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        // WICHTIG: menuAnchor() am TextField
                        OutlinedTextField(
                            value = domain,
                            onValueChange = {},           // read-only Dropdown
                            readOnly = true,
                            label = { Text("Select domain") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier
                                .menuAnchor( // 🟢 neuer Overload
                                    type = MenuAnchorType.PrimaryNotEditable,
                                    enabled = true
                                )            // <-- Anker für das Popup
                                .fillMaxWidth()
                                .clickable { expanded = true } // hilft auf manchen Setups
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            String().v64domains().forEach { option ->
                                if (option != "Own Domain") {
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            domain = option
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLoadingDialog) {
        SpinnerDialog(
            spinnerText = "Saving Domain..."
        )
    }

    if (showDialog) {
        ErrorDialog(
            onDismissRequest = { showDialog = false },
            onConfirmation = { showDialog = false; },
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