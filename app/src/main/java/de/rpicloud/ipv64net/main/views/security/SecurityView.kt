package de.rpicloud.ipv64net.main.views.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.helper.NavKeys
import de.rpicloud.ipv64net.helper.navigateWithArgs
import de.rpicloud.ipv64net.main.views.common.HubScreen
import de.rpicloud.ipv64net.main.views.common.HubSection
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs

/**
 * Security-Hub (Tab-Wurzel): keine Anfragen. Das IP-Feld prüft lokal und öffnet danach die IP-Auskunft, die
 * die Abfrage selbst ausführt (NavKeys.THREAT_LOOKUP_IP).
 */
@Composable
fun SecurityView(navController: NavHostController, mainPadding: PaddingValues) {
    var ip by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val value = normalizeIpInput(ip)
        if (!isValidIp(value)) {
            error = "Enter a valid IPv4 or IPv6 address."
            return
        }
        error = null
        navController.navigateWithArgs(Tab.threat_lookup, NavKeys.THREAT_LOOKUP_IP to value)
    }

    HubScreen(
        title = Tabs.getLabel(Tab.security),
        navController = navController,
        mainPadding = mainPadding,
        header = {
            item(key = "quick_lookup") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Check an IP address", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = ip,
                            onValueChange = { ip = it; error = null },
                            label = { Text("IPv4 or IPv6 address") },
                            singleLine = true,
                            isError = error != null,
                            supportingText = error?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { submit() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = { submit() }, enabled = ip.isNotBlank()) { Text("Look up") }
                    }
                }
            }
        },
        sections = listOf(
            HubSection(Tab.threat_lookup, subtitle = "Why is an IP address on the blocklist?"),
            HubSection(Tab.threat_watch, subtitle = "Get alerts about attackers in your networks"),
            HubSection(Tab.threat_stats, subtitle = "Attacks, ports and rankings of the Honeypot64 network"),
            HubSection(Tab.password_check, subtitle = "Was a password used in attacks? Stays on your device"),
            HubSection(Tab.honeypot, subtitle = "Your sensors, contribution and blocklist token")
        )
    )
}
