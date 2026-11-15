package de.rpicloud.ipv64net.main.views

import de.rpicloud.ipv64net.main.views.common.SecureWindowEffect
import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
import de.rpicloud.ipv64net.ui.theme.chartPalette
import kotlinx.coroutines.launch
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.Me
import de.sr.ipv64.model.UsageCounter

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
                        usageItem("Own Domain", usage?.ownDomains),
                        usageItem("DynDNS Domains", usage?.dyndnsDomains),
                        usageItem("DynDNS Update Limit / 24h", usage?.dyndnsUpdates24h)
                    )
                )
            }
            item(key = "healthcheck") {
                InfoGroup(
                    "Healthcheck",
                    listOf(
                        usageItem("Healthchecks", usage?.healthchecks),
                        usageItem("Healthchecks Updates / 24h", usage?.healthcheckPings24h),
                        usageItem("SMS Limit / 24h", usage?.sms24h)
                    )
                )
            }
            item(key = "failover") {
                InfoGroup(
                    "Failover",
                    listOf(
                        usageItem("Failover Policies", usage?.failoverPolicies),
                        usageItem("Failover Monitors", usage?.failoverMonitors),
                        usageItem("Failover Rules", usage?.failoverRules)
                    )
                )
            }
            item(key = "cdn") {
                InfoGroup(
                    "CDN",
                    listOf(
                        usageItem("CDN Domains", usage?.cdnDomains),
                        usageItem("CDN Traffic (GB)", usage?.cdnTrafficGb)
                    )
                )
            }
            item(key = "vpn") {
                InfoGroup(
                    "VPN / DNS64",
                    listOf(
                        usageItem(
                            "VPN Traffic (GB)", usage?.vpnTrafficGb.usageText(),
                            usage?.vpnTrafficGb?.used, usage?.vpnTrafficGb?.limit?.toDouble(),
                            usage?.vpnTrafficGb?.limited == true
                        ),
                        usageItem("DNS64 Clients", usage?.dns64Clients)
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

@Composable
private fun usageItem(label: String, counter: UsageCounter?): InfoItem =
    usageItem(label, counter.usageText(), counter?.used, counter?.limit?.toDouble())

@Composable
private fun usageItem(label: String, rawValue: String, used: Double?, limit: Double?, reached: Boolean = false): InfoItem {
    val severity = usageSeverity(used, limit, reached)
    val palette = chartPalette()
    val status = when (severity) {
        UsageSeverity.NEAR_LIMIT -> " · Near limit"
        UsageSeverity.LIMIT_REACHED -> if (rawValue.endsWith(" (limit reached)")) "" else " · Limit reached"
        UsageSeverity.NORMAL, UsageSeverity.UNKNOWN -> ""
    }
    val color = when (severity) {
        UsageSeverity.NORMAL -> palette.categorical(5)
        UsageSeverity.NEAR_LIMIT -> palette.categorical(1)
        UsageSeverity.LIMIT_REACHED -> palette.categorical(7)
        UsageSeverity.UNKNOWN -> Color.Unspecified
    }
    val readableColor = when {
        !color.isSpecified -> color
        MaterialTheme.colorScheme.surface.luminance() < 0.5f -> lerp(color, Color.White, 0.15f)
        else -> lerp(color, Color.Black, 0.20f)
    }
    return InfoItem(label, rawValue + status, valueColor = readableColor)
}

internal enum class UsageSeverity { UNKNOWN, NORMAL, NEAR_LIMIT, LIMIT_REACHED }

internal fun usageSeverity(used: Double?, limit: Double?, reached: Boolean = false): UsageSeverity = when {
    reached || (used != null && limit != null && used >= limit) -> UsageSeverity.LIMIT_REACHED
    used == null -> UsageSeverity.UNKNOWN
    limit == null || limit <= 0 -> UsageSeverity.NORMAL
    used >= limit * 0.8 -> UsageSeverity.NEAR_LIMIT
    else -> UsageSeverity.NORMAL
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
