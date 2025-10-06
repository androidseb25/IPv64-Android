package de.rpicloud.ipv64net.main.views

import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.gson.Gson
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.NetworkService
import de.rpicloud.ipv64net.helper.v64domains
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.models.DomainResult
import de.rpicloud.ipv64net.models.HealthCheckResult
import de.rpicloud.ipv64net.models.StatusType
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("ContextCastToActivity")
@Composable
fun HealthcheckView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var fabVisible by remember { mutableStateOf(true) }
    var showLoadingDialog by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var errorDialogTitle by remember { mutableStateOf("") }
    var errorDialogText by remember { mutableStateOf("") }
    var errorDialogButtonText by remember { mutableIntStateOf(android.R.string.ok) }

    var healthCheckResult by remember { mutableStateOf(HealthCheckResult.empty) }
    var activeCount by remember { mutableIntStateOf(0) }
    var warningCount by remember { mutableIntStateOf(0) }
    var alarmCount by remember { mutableIntStateOf(0) }
    var pauseCount by remember { mutableIntStateOf(0) }

    fun getHealthchecks() {
        showLoadingDialog = true
        scope.launch(Dispatchers.IO) {
            NetworkService(ctx).GetHealthchecks { nwResult ->
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
                            (nwResult.data as HealthCheckResult).also { healthCheckResult = it }
                            val sortedList = healthCheckResult.domain.sortedBy { it.name.lowercase() }
                            healthCheckResult.domain = sortedList.toMutableList()
                            println(healthCheckResult)
                            activeCount = healthCheckResult.domain.filter { hc -> hc.HealthStatus == StatusType.Active.type }.size
                            warningCount = healthCheckResult.domain.filter { hc -> hc.HealthStatus == StatusType.Warning.type }.size
                            alarmCount = healthCheckResult.domain.filter { hc -> hc.HealthStatus == StatusType.Alarm.type }.size
                            pauseCount = healthCheckResult.domain.filter { hc -> hc.HealthStatus == StatusType.Pause.type }.size
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
                    Text(Tabs.getLabel(Tab.healthcheck))
                }, modifier = Modifier.statusBarsPadding()
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = fabVisible,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                FloatingActionButton(onClick = {
                    navController.navigate(Tabs.getRoute(Tab.healthcheck_new))
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.heart_plus_24px),
                        contentDescription = "icon"
                    )
                }
            }
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
                // ✅ 2x2 Grid als NICHT-scrollbares Layout
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HealthcheckGridItemView(ctx, activeCount, StatusType.Active, Modifier.weight(1f).fillMaxWidth())
                            HealthcheckGridItemView(ctx, warningCount, StatusType.Warning, Modifier.weight(1f).fillMaxWidth())
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HealthcheckGridItemView(ctx, alarmCount, StatusType.Alarm, Modifier.weight(1f).fillMaxWidth())
                            HealthcheckGridItemView(ctx, pauseCount, StatusType.Pause, Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                }
                items(
                    items = healthCheckResult.domain,
                    key = { hc -> hc.healthtoken } // stabile Keys
                ) { hc ->
                    HealthcheckItemView(ctx, hc) { selectedHealthcheck ->
                        println(selectedHealthcheck)
                        val gson = Gson()
                        val selectedDAsString = gson.toJson(selectedHealthcheck).toString()
                        navController.currentBackStackEntry?.savedStateHandle?.set(
                            "SELECTED_HEALTHCHECK",
                            selectedDAsString
                        )
                        navController.navigate(Tabs.getRoute(Tab.healthcheck_details))
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        getHealthchecks()
    }

    // Sichtbarkeit abhängig von Scrollrichtung steuern
    LaunchedEffect(listState) {
        var lastPos = 0
        var lastOff = 0
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .map { (idx, off) ->
                val scrollingDown = idx > lastPos || (idx == lastPos && off > lastOff)
                lastPos = idx; lastOff = off
                scrollingDown
            }
            .distinctUntilChanged()
            .collectLatest { scrollingDown ->
                fabVisible = !scrollingDown || !listState.isScrollInProgress
            }
    }

    // Wenn kein Scrollen mehr stattfindet, wieder einblenden
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) fabVisible = true
    }

    if (showLoadingDialog) {
        SpinnerDialog(
            spinnerText = "Loading..."
        )
    }

    if (showDialog) {
        ErrorDialog(
            onDismissRequest = { showDialog = false },
            onConfirmation = { showDialog = false; getHealthchecks() },
            dialogTitle = errorDialogTitle,
            dialogText = errorDialogText,
            dialogConfirmText = errorDialogButtonText
        )
    }
}