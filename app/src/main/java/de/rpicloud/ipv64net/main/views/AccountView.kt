package de.rpicloud.ipv64net.main.views

import de.rpicloud.ipv64net.main.views.common.SecureWindowEffect
import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
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
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.InfoGroup
import de.rpicloud.ipv64net.main.views.common.InfoItem
import de.rpicloud.ipv64net.models.RequestTyp
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.rpicloud.ipv64net.models.User
import de.rpicloud.ipv64net.ui.theme.AppTheme
import kotlinx.coroutines.launch
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.Me

@SuppressLint("ContextCastToActivity")
@Composable
fun AccountView(navController: NavHostController, mainPadding: PaddingValues) {

    SecureWindowEffect() // API-Key kann per "Show" im Klartext angezeigt werden
    val ctx = LocalContext.current
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

    ScreenScaffold(title = Tabs.getLabel(Tab.account), navController = navController, mainPadding = mainPadding) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            User.current?.let { user ->
                item(key = "summary") {
                    ListGroup {
                        ListRow(
                            title = user.Username,
                            subtitle = user.Information.ifEmpty { "No Information" },
                            subtitleMaxLines = 1,
                            icon = R.drawable.account_circle_24px,
                            onClick = { navController.navigate(Tabs.getRoute(Tab.account_details)) { launchSingleTop = true } }
                        )
                    }
                }
            }
            val usage = me?.usage
            item(key = "account") {
                InfoGroup(
                    "Account",
                    listOf(
                        InfoItem("Username", me?.username.orEmpty().ifEmpty { "none" }),
                        InfoItem("Account Plan", me?.plan?.name.orEmpty().ifEmpty { "none" }),
                        InfoItem("Plan valid until", me?.plan?.let { it.validUntil?.formatGermanTime() ?: "unlimited" } ?: "–"),
                        InfoItem("E-Mail", me?.email.orEmpty().ifEmpty { "none" }),
                        InfoItem("Registered since", me?.registeredAt?.formatGermanTime() ?: "none"),
                        InfoItem("Last login", me?.lastLoginAt?.formatGermanTime() ?: "none")
                    )
                )
            }
            item(key = "dyndns") {
                InfoGroup(
                    "DynDNS",
                    listOf(
                        InfoItem("Own Domain", usage?.ownDomains.usageText()),
                        InfoItem("DynDNS Domains", usage?.dyndnsDomains.usageText()),
                        InfoItem("DynDNS Update Limit / 24h", usage?.dyndnsUpdates24h.usageText())
                    )
                )
            }
            item(key = "healthcheck") {
                InfoGroup(
                    "Healthcheck",
                    listOf(
                        InfoItem("Healthchecks", usage?.healthchecks.usageText()),
                        InfoItem("Healthchecks Updates / 24h", usage?.healthcheckPings24h.usageText()),
                        InfoItem("SMS Limit / 24h", usage?.sms24h.usageText())
                    )
                )
            }
            item(key = "failover") {
                InfoGroup(
                    "Failover",
                    listOf(
                        InfoItem("Failover Policies", usage?.failoverPolicies.usageText()),
                        InfoItem("Failover Monitors", usage?.failoverMonitors.usageText()),
                        InfoItem("Failover Rules", usage?.failoverRules.usageText())
                    )
                )
            }
            item(key = "cdn") {
                InfoGroup(
                    "CDN",
                    listOf(
                        InfoItem("CDN Domains", usage?.cdnDomains.usageText()),
                        InfoItem("CDN Traffic (GB)", usage?.cdnTrafficGb.usageText())
                    )
                )
            }
            item(key = "vpn") {
                InfoGroup(
                    "VPN / DNS64",
                    listOf(
                        InfoItem("VPN Traffic (GB)", usage?.vpnTrafficGb.usageText()),
                        InfoItem("DNS64 Clients", usage?.dns64Clients.usageText())
                    )
                )
            }
            item(key = "api") {
                // v2 liefert den Key nicht mehr zurück, daher der lokal gespeicherte Key
                InfoGroup(
                    "API",
                    listOf(
                        InfoItem(
                            "API Key",
                            PreferencesManager.loadString(ctx, "APIKEY").trim(),
                            copyable = true, sensitive = true, monospace = true
                        )
                    )
                )
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