package de.rpicloud.ipv64net.main.views

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.NetworkService
import de.rpicloud.ipv64net.helper.apiUsageText
import de.rpicloud.ipv64net.helper.parseDbDate
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.models.AccountInfo
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.preference

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter", "ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountView(navController: NavHostController, mainPadding: PaddingValues) {

    val haptics = LocalHapticFeedback.current

    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var showLoadingDialog by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var errorDialogTitle by remember { mutableStateOf("") }
    var errorDialogText by remember { mutableStateOf("") }
    var errorDialogButtonText by remember { mutableIntStateOf(R.string.retry) }

    var accountInfos by remember { mutableStateOf(AccountInfo.empty) }

    fun getAccountInfos() {
        showLoadingDialog = true
        scope.launch(Dispatchers.IO) {
            NetworkService(ctx).GetAccountStatus { nwResult ->
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
                            (nwResult.data as AccountInfo).also { accountInfos = it }
                            println(accountInfos)
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

    ProvidePreferenceLocals {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(Tabs.getLabel(Tab.account))
                    }, modifier = Modifier.statusBarsPadding(), navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.arrow_back_24px),
                                contentDescription = "Close"
                            )
                        }
                    }
                )
            }, modifier = Modifier
                .fillMaxSize()
                .padding(mainPadding)
                .consumeWindowInsets(mainPadding)  // avoids double insets
        ) { it ->
            Column(modifier = Modifier.padding(it)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    stickyHeader {
                        Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = "Account",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp)
                            )
                        }
                    }
                    preference(
                        key = "PREF_ACCOUNT_STATUS",
                        title = { Text(text = "Account Status") },
                        summary = { Text(if (accountInfos.account_status == 1) "activated" else "disabled") }
                    )
                    preference(
                        key = "PREF_ACCOUNT_PLAN",
                        title = { Text(text = "Account Plan") },
                        summary = { Text(accountInfos.account_class.class_name.ifEmpty { "none" }) }
                    )
                    preference(
                        key = "PREF_ACCOUNT_MAIL",
                        title = { Text(text = "E-Mail") },
                        summary = { Text(accountInfos.email.ifEmpty { "none" }) }
                    )
                    preference(
                        key = "PREF_ACCOUNT_REGISTERED",
                        title = { Text(text = "Registered since") },
                        summary = { Text(accountInfos.reg_date.parseDbDate()) }
                    )
                    stickyHeader {
                        Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = "DynDNS",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp)
                            )
                        }
                    }
                    preference(
                        key = "PREF_DYNDNS_OWN_DOMAIN_LIMIT",
                        title = { Text(text = "Own Domain") },
                        summary = {
                            Text(
                                accountInfos.account_class.owndomain_limit.apiUsageText(
                                    accountInfos.owndomains
                                )
                            )
                        }
                    )
                    preference(
                        key = "PREF_DYNDNS_DOMAIN_LIMIT",
                        title = { Text(text = "DynDNS Domains") },
                        summary = {
                            Text(
                                accountInfos.account_class.dyndns_domain_limit.apiUsageText(
                                    accountInfos.dyndns_subdomains
                                )
                            )
                        }
                    )
                    preference(
                        key = "PREF_DYNDNS_UPDATE_LIMIT",
                        title = { Text(text = "DynDNS Update Limit / 24h") },
                        summary = {
                            Text(
                                accountInfos.account_class.dyndns_update_limit.apiUsageText(
                                    accountInfos.dyndns_updates
                                )
                            )
                        }
                    )
                    preference(
                        key = "PREF_DYNDNS_KEY",
                        title = { Text(text = "DynDNS Updatehash") },
                        summary = { Text("•••••••••••••••••••") }
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val clipboard =
                            ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip =
                            ClipData.newPlainText("DynDNS Updatehash", accountInfos.update_hash)
                        clipboard.setPrimaryClip(clip)
                    }
                    stickyHeader {
                        Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = "Healthcheck",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp)
                            )
                        }
                    }
                    preference(
                        key = "PREF_HEALTHCHECK_LIMIT",
                        title = { Text(text = "Healthchecks") },
                        summary = {
                            Text(
                                accountInfos.account_class.healthcheck_limit.apiUsageText(
                                    accountInfos.healthchecks
                                )
                            )
                        }
                    )
                    preference(
                        key = "PREF_HEALTHCHECK_UPDATE_LIMIT",
                        title = { Text(text = "Healthchecks Updates / 24h") },
                        summary = {
                            Text(
                                accountInfos.account_class.healthcheck_update_limit.apiUsageText(
                                    accountInfos.healthchecks_updates
                                )
                            )
                        }
                    )
                    preference(
                        key = "PREF_HEALTHCHECK_SMS_LIMIT",
                        title = { Text(text = "SMS Limit / 24h") },
                        summary = {
                            Text(
                                accountInfos.account_class.sms_limit.apiUsageText(
                                    accountInfos.sms_count
                                )
                            )
                        }
                    )
                    stickyHeader {
                        Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = "API",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp)
                            )
                        }
                    }
                    preference(
                        key = "PREF_API_LIMIT",
                        title = { Text(text = "API Limit / 24") },
                        summary = {
                            Text(
                                accountInfos.account_class.api_limit.apiUsageText(
                                    accountInfos.api_updates
                                )
                            )
                        }
                    )
                    preference(
                        key = "PREF_API_KEY",
                        title = { Text(text = "API Key") },
                        summary = { Text("•••••••••••••••••••") }
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val clipboard =
                            ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("APIKey", accountInfos.api_key)
                        clipboard.setPrimaryClip(clip)
                    }
                }
            }

            if (showLoadingDialog) {
                SpinnerDialog(
                    spinnerText = "Loading..."
                )
            }

            if (showDialog) {
                ErrorDialog(
                    onDismissRequest = { showDialog = false },
                    onConfirmation = { showDialog = false; getAccountInfos() },
                    dialogTitle = errorDialogTitle,
                    dialogText = errorDialogText,
                    dialogConfirmText = errorDialogButtonText,
                    icon = 0
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        getAccountInfos()
    }
}