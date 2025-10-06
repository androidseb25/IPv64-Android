package de.rpicloud.ipv64net.main.views

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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import de.rpicloud.ipv64net.helper.v64Units
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.models.AddDomainResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthcheckNewView(navController: NavHostController, mainPadding: PaddingValues) {

    val ctx = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var showLoadingDialog by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var hcUnit by remember { mutableStateOf(String().v64Units().first()) }
    var hcCount by remember { mutableFloatStateOf(30f) }
    var hcName by remember { mutableStateOf("") }
    var errorDialogTitle by remember { mutableStateOf("") }
    var errorDialogText by remember { mutableStateOf("") }
    var errorDialogButtonText by remember { mutableIntStateOf(android.R.string.ok) }

    var newHealthcheckResult by remember { mutableStateOf(AddDomainResult.empty) }

    fun onSave() {
        keyboardController?.hide()
        if (hcName.isEmpty()) {
            errorDialogTitle = "Empty field"
            errorDialogText = "Please fill the Healthcheck field!"
            errorDialogButtonText = R.string.retry
            showDialog = true
            return
        }

        showLoadingDialog = true
        scope.launch(Dispatchers.IO) {
            NetworkService(ctx).PostNewHealthcheck(
                hcName,
                hcCount.toInt(),
                hcUnit.Unit.unit ?: 1
            ) { nwResult ->
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
                            (nwResult.data as AddDomainResult).also { newHealthcheckResult = it }
                            println(newHealthcheckResult)
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

                    403 -> {
                        (nwResult.data as AddDomainResult).also { newHealthcheckResult = it }
                        println(nwResult.message)
                        errorDialogTitle = "Loading error"
                        errorDialogText = newHealthcheckResult.add_domain.toString()
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
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_back_24px),
                            contentDescription = "Close"
                        )
                    }
                },
                title = {
                    Text("New Healthcheck")
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
                        value = hcName,
                        singleLine = true,
                        label = { Text("Healthcheck #001") },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth(),
                        onValueChange = { newText -> hcName = newText },
                    )
                }
                item {
                    Slider(
                        value = hcCount,
                        onValueChange = { count ->
                            hcCount = count
                        },
                        valueRange = 1f..60f,
                        steps = 59,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
                item {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        // WICHTIG: menuAnchor() am TextField
                        OutlinedTextField(
                            value = "${hcCount.toInt()} ${hcUnit.Unit.name ?: ""}",
                            onValueChange = {},           // read-only Dropdown
                            readOnly = true,
                            label = { Text("Select Unit") },
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
                            String().v64Units().forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.Unit.name ?: "") },
                                    onClick = {
                                        hcUnit = option
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

    if (showLoadingDialog) {
        SpinnerDialog(
            spinnerText = "Saving Healthcheck..."
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
}