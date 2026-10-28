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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.PreferencesManager
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.helper.ipv64Request
import de.rpicloud.ipv64net.helper.toError
import de.rpicloud.ipv64net.helper.usageText
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.helper.views.RequestDialogs
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.main.activity.TabView
import de.rpicloud.ipv64net.models.RequestTyp
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.rpicloud.ipv64net.models.User
import de.rpicloud.ipv64net.ui.theme.AppTheme
import kotlinx.coroutines.launch
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.Me
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

    var showRequestDialog by remember { mutableStateOf(false) }
    var requestType by remember { mutableStateOf(RequestTyp.UnAuthorized) }


    var me by remember { mutableStateOf<Me?>(null) }

    fun getAccountInfos() {
        showLoadingDialog = true
        scope.launch {
            val ipv64 = IPv64Client.get(ctx)
            try {
                me = ipv64Request { ipv64.account.me() }
                showLoadingDialog = false
            } catch (e: IPv64Exception) {
                showLoadingDialog = false
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
                    User.current?.let { user ->
//                    val user = User.empty
//                    user.Information = "No Informations"
//                    user.Username = "Default Username"
                        item {
                            Button(onClick = {
                                navController.navigate(Tabs.getRoute(Tab.account_details))
                            }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.account_circle_24px),
                                        contentDescription = "Account Icon",
                                        modifier = Modifier
                                            .width(55.dp)
                                            .height(55.dp)
                                    )
                                    Column {
                                        Text(
                                            user.Username,
                                            style = MaterialTheme.typography.titleLarge,
                                            modifier = Modifier.padding(start = 8.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            user.Information,
                                            modifier = Modifier.padding(start = 8.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
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
                        key = "PREF_ACCOUNT_USERNAME",
                        title = { Text(text = "Username") },
                        summary = { Text(me?.username.orEmpty().ifEmpty { "none" }) }
                    )
                    preference(
                        key = "PREF_ACCOUNT_PLAN",
                        title = { Text(text = "Account Plan") },
                        summary = { Text(me?.plan?.name.orEmpty().ifEmpty { "none" }) }
                    )
                    preference(
                        key = "PREF_ACCOUNT_PLAN_VALID",
                        title = { Text(text = "Plan valid until") },
                        summary = { Text(me?.plan?.let { it.validUntil?.formatGermanTime() ?: "unlimited" } ?: "–") }
                    )
                    preference(
                        key = "PREF_ACCOUNT_MAIL",
                        title = { Text(text = "E-Mail") },
                        summary = { Text(me?.email.orEmpty().ifEmpty { "none" }) }
                    )
                    preference(
                        key = "PREF_ACCOUNT_REGISTERED",
                        title = { Text(text = "Registered since") },
                        summary = { Text(me?.registeredAt?.formatGermanTime() ?: "none") }
                    )
                    preference(
                        key = "PREF_ACCOUNT_LAST_LOGIN",
                        title = { Text(text = "Last login") },
                        summary = { Text(me?.lastLoginAt?.formatGermanTime() ?: "none") }
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
                        summary = { Text(me?.usage?.ownDomains.usageText()) }
                    )
                    preference(
                        key = "PREF_DYNDNS_DOMAIN_LIMIT",
                        title = { Text(text = "DynDNS Domains") },
                        summary = { Text(me?.usage?.dyndnsDomains.usageText()) }
                    )
                    preference(
                        key = "PREF_DYNDNS_UPDATE_LIMIT",
                        title = { Text(text = "DynDNS Update Limit / 24h") },
                        summary = { Text(me?.usage?.dyndnsUpdates24h.usageText()) }
                    )
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
                        summary = { Text(me?.usage?.healthchecks.usageText()) }
                    )
                    preference(
                        key = "PREF_HEALTHCHECK_UPDATE_LIMIT",
                        title = { Text(text = "Healthchecks Updates / 24h") },
                        summary = { Text(me?.usage?.healthcheckPings24h.usageText()) }
                    )
                    preference(
                        key = "PREF_HEALTHCHECK_SMS_LIMIT",
                        title = { Text(text = "SMS Limit / 24h") },
                        summary = { Text(me?.usage?.sms24h.usageText()) }
                    )
                    stickyHeader {
                        Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = "Failover",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp)
                            )
                        }
                    }
                    preference(
                        key = "PREF_FAILOVER_POLICIES",
                        title = { Text(text = "Failover Policies") },
                        summary = { Text(me?.usage?.failoverPolicies.usageText()) }
                    )
                    preference(
                        key = "PREF_FAILOVER_MONITORS",
                        title = { Text(text = "Failover Monitors") },
                        summary = { Text(me?.usage?.failoverMonitors.usageText()) }
                    )
                    preference(
                        key = "PREF_FAILOVER_RULES",
                        title = { Text(text = "Failover Rules") },
                        summary = { Text(me?.usage?.failoverRules.usageText()) }
                    )
                    stickyHeader {
                        Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = "CDN",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp)
                            )
                        }
                    }
                    preference(
                        key = "PREF_CDN_DOMAINS",
                        title = { Text(text = "CDN Domains") },
                        summary = { Text(me?.usage?.cdnDomains.usageText()) }
                    )
                    preference(
                        key = "PREF_CDN_TRAFFIC",
                        title = { Text(text = "CDN Traffic (GB)") },
                        summary = { Text(me?.usage?.cdnTrafficGb.usageText()) }
                    )
                    stickyHeader {
                        Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = "VPN / DNS64",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp)
                            )
                        }
                    }
                    preference(
                        key = "PREF_VPN_TRAFFIC",
                        title = { Text(text = "VPN Traffic (GB)") },
                        summary = { Text(me?.usage?.vpnTrafficGb.usageText()) }
                    )
                    preference(
                        key = "PREF_DNS64_CLIENTS",
                        title = { Text(text = "DNS64 Clients") },
                        summary = { Text(me?.usage?.dns64Clients.usageText()) }
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
                        key = "PREF_API_KEY",
                        title = { Text(text = "API Key") },
                        summary = { Text("•••••••••••••••••••") }
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val clipboard =
                            ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        // v2 liefert den Key nicht mehr zurück, daher der lokal gespeicherte Key
                        val clip = ClipData.newPlainText(
                            "APIKey",
                            PreferencesManager.loadString(ctx, "APIKEY").trim()
                        )
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

            if (showRequestDialog) {
                RequestDialogs(
                    onDismissRequest = { showRequestDialog = false },
                    onConfirmation = { showRequestDialog = false; getAccountInfos() },
                    dialogConfirmText = errorDialogButtonText,
                    request = requestType
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        getAccountInfos()
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun AccountViewPreview() {
    AppTheme {
        val navController = rememberNavController()
        User.init(context = LocalContext.current)
        Scaffold(bottomBar = { TabView(Tabs.tabList, navController) }) { mainPadding ->
            AccountView(navController, mainPadding)
        }
    }
}