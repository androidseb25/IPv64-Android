package de.rpicloud.ipv64net.main.views

import android.R.attr.checked
import android.annotation.SuppressLint
import android.widget.ToggleButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.gson.Gson
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.NetworkService
import de.rpicloud.ipv64net.helper.v64Units
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.models.AddDomainResult
import de.rpicloud.ipv64net.models.HealthCheck
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HealthcheckEditView {
}

@SuppressLint("UnrememberedGetBackStackEntry")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthcheckEditView(navController: NavHostController, mainPadding: PaddingValues) {

    val ctx = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var showLoadingDialog by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var expandedAlarm by remember { mutableStateOf(false) }
    var expandedGrace by remember { mutableStateOf(false) }
    var hcNotiDown by remember { mutableStateOf(false) }
    var hcNotiUp by remember { mutableStateOf(false) }
    var hcAlarmUnit by remember { mutableStateOf(String().v64Units().first()) }
    var hcAlarmCount by remember { mutableFloatStateOf(30f) }
    var hcGraceUnit by remember { mutableStateOf(String().v64Units().first()) }
    var hcGraceCount by remember { mutableFloatStateOf(30f) }
    var hcName by remember { mutableStateOf("") }
    var errorDialogTitle by remember { mutableStateOf("") }
    var errorDialogText by remember { mutableStateOf("") }
    var errorDialogButtonText by remember { mutableIntStateOf(android.R.string.ok) }

    var editHc by remember { mutableStateOf(HealthCheck.empty) }

    var editHealthcheckResult by remember { mutableStateOf(AddDomainResult.empty) }

    val hcRoute = Tabs.getRoute(Tab.healthcheck_details)
    var hcBackStackEntry = remember(hcRoute) {
        navController.getBackStackEntry(hcRoute)
    }
    val hc by hcBackStackEntry.savedStateHandle.getStateFlow<String>("EDIT_HEALTHCHECK", "")
        .collectAsState()

    fun onSave() {
        keyboardController?.hide()
        if (hcName.isEmpty()) {
            errorDialogTitle = "Empty field"
            errorDialogText = "Please fill the Healthcheck field!"
            errorDialogButtonText = R.string.retry
            showDialog = true
            return
        }

        editHc.name = hcName
        editHc.alarm_unit = hcAlarmUnit.Unit.unit ?: 1
        editHc.alarm_count = hcAlarmCount.toInt()
        editHc.grace_unit = hcGraceUnit.Unit.unit ?: 1
        editHc.grace_count = hcGraceCount.toInt()
        editHc.alarm_up = if (hcNotiUp) 1 else 0
        editHc.alarm_down = if (hcNotiDown) 1 else 0

        showLoadingDialog = true
        scope.launch(Dispatchers.IO) {
            NetworkService(ctx).PostEditHealthcheck(
                editHc
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
                            (nwResult.data as AddDomainResult).also { editHealthcheckResult = it }
                            println(editHealthcheckResult)
                            navController.popBackStack(
                                route = Tabs.getRoute(Tab.healthcheck), // die Parent-Route
                                inclusive = false               // Parent im Stack behalten
                            )
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
                        (nwResult.data as AddDomainResult).also { editHealthcheckResult = it }
                        println(nwResult.message)
                        errorDialogTitle = "Loading error"
                        errorDialogText = editHealthcheckResult.add_domain.toString()
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
                    Text("Edit Healthcheck")
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
                stickyHeader {
                    Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = "Name of Healthcheck",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp)
                        )
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
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
                    }
                }
                stickyHeader {
                    Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = "Alarm period",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp)
                        )
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Slider(
                                value = hcAlarmCount,
                                onValueChange = { count ->
                                    hcAlarmCount = count
                                },
                                valueRange = 1f..60f,
                                steps = 59,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )

                            ExposedDropdownMenuBox(
                                expanded = expandedAlarm,
                                onExpandedChange = { expandedAlarm = !expandedAlarm }
                            ) {
                                // WICHTIG: menuAnchor() am TextField
                                OutlinedTextField(
                                    value = "${hcAlarmCount.toInt()} ${hcAlarmUnit.Unit.name ?: ""}",
                                    onValueChange = {},           // read-only Dropdown
                                    readOnly = true,
                                    label = { Text("Select Unit") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedAlarm) },
                                    modifier = Modifier
                                        .menuAnchor( // 🟢 neuer Overload
                                            type = MenuAnchorType.PrimaryNotEditable,
                                            enabled = true
                                        )            // <-- Anker für das Popup
                                        .fillMaxWidth()
                                        .padding(top = 16.dp)
                                        .clickable { expandedAlarm = true } // hilft auf manchen Setups
                                )

                                ExposedDropdownMenu(
                                    expanded = expandedAlarm,
                                    onDismissRequest = { expandedAlarm = false }
                                ) {
                                    String().v64Units().forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.Unit.name ?: "") },
                                            onClick = {
                                                hcAlarmUnit = option
                                                expandedAlarm = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                stickyHeader {
                    Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = "Grace period",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp)
                        )
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Slider(
                                value = hcGraceCount,
                                onValueChange = { count ->
                                    hcGraceCount = count
                                },
                                valueRange = 1f..60f,
                                steps = 59,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )

                            ExposedDropdownMenuBox(
                                expanded = expandedGrace,
                                onExpandedChange = { expandedGrace = !expandedGrace }
                            ) {
                                // WICHTIG: menuAnchor() am TextField
                                OutlinedTextField(
                                    value = "${hcGraceCount.toInt()} ${hcGraceUnit.Unit.name ?: ""}",
                                    onValueChange = {},           // read-only Dropdown
                                    readOnly = true,
                                    label = { Text("Select Unit") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedGrace) },
                                    modifier = Modifier
                                        .menuAnchor( // 🟢 neuer Overload
                                            type = MenuAnchorType.PrimaryNotEditable,
                                            enabled = true
                                        )            // <-- Anker für das Popup
                                        .fillMaxWidth()
                                        .padding(top = 16.dp)
                                        .clickable { expandedGrace = true } // hilft auf manchen Setups
                                )

                                ExposedDropdownMenu(
                                    expanded = expandedGrace,
                                    onDismissRequest = { expandedGrace = false }
                                ) {
                                    String().v64Units().forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.Unit.name ?: "") },
                                            onClick = {
                                                hcGraceUnit = option
                                                expandedGrace = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                stickyHeader {
                    Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = "Notification",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp)
                        )
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Notification UP")
                                Spacer(Modifier.weight(1f))
                                Switch(
                                    checked = hcNotiUp,
                                    onCheckedChange = { isUp -> hcNotiUp = isUp },
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = Color.Green
                                    )
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Notification DOWN")
                                Spacer(Modifier.weight(1f))
                                Switch(
                                    checked = hcNotiDown,
                                    onCheckedChange = { isDown -> hcNotiDown = isDown },
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = Color.Red
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val gson = Gson()
        editHc = gson.fromJson(hc, HealthCheck::class.java)
        hcName = editHc.name
        hcAlarmUnit = editHc.AlarmUnit
        hcAlarmCount = editHc.alarm_count.toFloat()
        hcGraceUnit = editHc.GraceUnit
        hcGraceCount = editHc.grace_count.toFloat()
        hcNotiUp = editHc.alarm_up == 1
        hcNotiDown = editHc.alarm_down == 1
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