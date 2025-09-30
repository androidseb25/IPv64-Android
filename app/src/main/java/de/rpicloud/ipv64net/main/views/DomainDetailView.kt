package de.rpicloud.ipv64net.main.views

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.google.gson.Gson
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.NetworkService
import de.rpicloud.ipv64net.helper.parseDbDate
import de.rpicloud.ipv64net.helper.parseDbDateTime
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.models.AddDomainResult
import de.rpicloud.ipv64net.models.Domain
import de.rpicloud.ipv64net.models.DomainResult
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("UnrememberedGetBackStackEntry")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainDetailView(navController: NavHostController, mainPadding: PaddingValues) {

    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboard.current

    var showLoadingDialog by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeleteDNSDialog by remember { mutableStateOf(false) }
    var errorDialogTitle by remember { mutableStateOf("") }
    var errorDialogText by remember { mutableStateOf("") }
    var errorDialogButtonText by remember { mutableIntStateOf(android.R.string.ok) }
    var deleteDialogTitle by remember { mutableStateOf("") }
    var deleteDialogText by remember { mutableStateOf("") }
    var deleteDialogButtonText by remember { mutableIntStateOf(R.string.delete) }
    var selectedDomain by remember { mutableStateOf(Domain.empty) }
    var selectedRecordId by remember { mutableIntStateOf(0) }

    var addDomainResult by remember { mutableStateOf(AddDomainResult.empty) }
    val domainRoute = Tabs.getRoute(Tab.domains)
    val vpnBackStackEntry = remember(domainRoute) {
        navController.getBackStackEntry(domainRoute)
    }
    val domain by vpnBackStackEntry.savedStateHandle.getStateFlow<String>("SELECTED_DOMAIN", "")
        .collectAsState()

    fun GetAccountUpdateUrl(): String {
        return ""
//        return "https://ipv64.net/nic/update?key=" + selectedDomain.domain_update_hash + "&domain=" + selectedDomain.fqdn
    }

    fun GetDomainUpdateUrl(): String {
        return "https://ipv64.net/nic/update?key=" + selectedDomain.domain_update_hash
    }

    fun DeleteDomain() {
        showLoadingDialog = true
        scope.launch(Dispatchers.IO) {
            selectedDomain.fqdn?.let { dom ->
                NetworkService(ctx).DeleteDomain(dom) { nwResult ->
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
                                (nwResult.data as AddDomainResult).also { addDomainResult = it }
                                println(addDomainResult)
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
                            (nwResult.data as AddDomainResult).also { addDomainResult = it }
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
    }

    fun DeleteDNSRecord(recordId: Int) {
        showLoadingDialog = true
        scope.launch(Dispatchers.IO) {
            NetworkService(ctx).DeleteDNSRecord(recordId) { nwResult ->
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
                            (nwResult.data as AddDomainResult).also { addDomainResult = it }
                            println(addDomainResult)
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

                    else -> {
                        (nwResult.data as AddDomainResult).also { addDomainResult = it }
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
                actions = {
                    IconButton(onClick = {
                        deleteDialogText = "Do you really want to delete the Domain?"
                        deleteDialogTitle = "Delete Domain"
                        showDeleteDialog = true
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.delete_24px),
                            contentDescription = "Delete"
                        )
                    }
                },
                title = {
                    selectedDomain.fqdn?.let { Text(it) }
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
                stickyHeader {
                    Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = "General",
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
                            Row {
                                Text(
                                    "Wildcard:",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    selectedDomain.IsWildcard,
                                    style = MaterialTheme.typography.titleSmall,
                                    textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(),
                                    color = Color.Gray
                                )
                            }
                            Row(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    "Updates:",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${selectedDomain.updates}",
                                    style = MaterialTheme.typography.titleSmall,
                                    textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(),
                                    color = Color.Gray
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 32.dp)
                            ) {
                                Button(
                                    onClick = {
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        val clipboard =
                                            ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText(
                                            "Account Update URL",
                                            GetAccountUpdateUrl()
                                        )
                                        clipboard.setPrimaryClip(clip)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color(
                                            ContextCompat.getColor(
                                                ctx,
                                                R.color.ipv64_blue_trans15
                                            )
                                        ),
                                        contentColor = Color(
                                            ContextCompat.getColor(
                                                ctx,
                                                R.color.ipv64_blue
                                            )
                                        ),
                                    )
                                ) {
                                    Text("Copy Account Update URL")
                                }
                                Button(
                                    onClick = {
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        val clipboard =
                                            ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText(
                                            "Domain Update URL",
                                            GetDomainUpdateUrl()
                                        )
                                        clipboard.setPrimaryClip(clip)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color(
                                            ContextCompat.getColor(
                                                ctx,
                                                R.color.ipv64_blue_trans15
                                            )
                                        ),
                                        contentColor = Color(
                                            ContextCompat.getColor(
                                                ctx,
                                                R.color.ipv64_blue
                                            )
                                        ),
                                    )
                                ) {
                                    Text("Copy Domain Update URL")
                                }
                            }
                        }
                    }
                }
                selectedDomain.records?.forEach { recordInfos ->
                    stickyHeader {
                        Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                            tonalElevation = 2.dp
                        ) {
                            recordInfos.type?.let { text ->
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row {
                                    Text(
                                        "Präfix:",
                                        fontWeight = FontWeight.Bold
                                    )
                                    recordInfos.praefix?.let { text ->
                                        Text(
                                            text,
                                            style = MaterialTheme.typography.titleSmall,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.fillMaxWidth(),
                                            color = Color.Gray
                                        )
                                    }
                                }
                                Row(modifier = Modifier.padding(top = 16.dp)) {
                                    Text(
                                        "TTL:",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${recordInfos.ttl}",
                                        style = MaterialTheme.typography.titleSmall,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color.Gray
                                    )
                                }
                                Row(modifier = Modifier.padding(top = 16.dp)) {
                                    Text(
                                        "Typ:",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${recordInfos.type}",
                                        style = MaterialTheme.typography.titleSmall,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color.Gray
                                    )
                                }
                                Row(modifier = Modifier.padding(top = 16.dp)) {
                                    Text(
                                        "Value:",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${recordInfos.content}",
                                        style = MaterialTheme.typography.titleSmall,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color.Gray
                                    )
                                }
                                Row(modifier = Modifier.padding(top = 16.dp)) {
                                    Text(
                                        "last update:",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        recordInfos.last_update?.parseDbDateTime()
                                            ?: "01.01.0001 00:00:00",
                                        style = MaterialTheme.typography.titleSmall,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color.Gray
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 32.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            recordInfos.record_id?.let { recordId ->
                                                {
                                                    selectedRecordId = recordId
                                                    deleteDialogText = "Do you really want to delete the DNS Record?"
                                                    deleteDialogTitle = "Delete DNS Record"
                                                    showDeleteDNSDialog = true
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = Color(
                                                ContextCompat.getColor(
                                                    ctx,
                                                    R.color.ipv64_red_trans20
                                                )
                                            ),
                                            contentColor = Color(
                                                ContextCompat.getColor(
                                                    ctx,
                                                    R.color.ipv64_red
                                                )
                                            ),
                                        )
                                    ) {
                                        Text("Delete DNS Record")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val gson = Gson()
        selectedDomain = gson.fromJson(domain, Domain::class.java)
    }

    if (showLoadingDialog) {
        SpinnerDialog(
            spinnerText = "Saving domain..."
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

    if (showDeleteDialog) {
        ErrorDialog(
            onDismissRequest = { showDeleteDialog = false },
            onConfirmation = { showDeleteDialog = false; DeleteDomain() },
            dialogTitle = deleteDialogTitle,
            dialogText = deleteDialogText,
            dialogConfirmText = deleteDialogButtonText,
            icon = R.drawable.delete_24px,
            showDismiss = true
        )
    }

    if (showDeleteDNSDialog) {
        ErrorDialog(
            onDismissRequest = { showDeleteDNSDialog = false },
            onConfirmation = { showDeleteDNSDialog = false; DeleteDNSRecord(selectedRecordId) },
            dialogTitle = deleteDialogTitle,
            dialogText = deleteDialogText,
            dialogConfirmText = deleteDialogButtonText,
            icon = R.drawable.delete_24px,
            showDismiss = true
        )
    }
}