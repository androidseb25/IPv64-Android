package de.rpicloud.ipv64net.main.views.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.ipv64Retrying
import de.rpicloud.ipv64net.main.views.common.RequestStateHost
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.main.views.common.SecureWindowEffect
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.main.views.common.rememberRequestState
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs

/**
 * Passwort-Check (k-Anonymität): threat.passwordCount hasht lokal mit SHA-1 und sendet nur die ersten 5 Hex-Zeichen
 * des Hashes. Das Passwort selbst liegt nur im Compose-State (kein rememberSaveable), wird nie geloggt und beim
 * Verlassen des Screens sowie beim Wechsel in den Hintergrund gelöscht. Der Screen ist gegen Screenshots gesperrt.
 * Eine Anfrage pro Prüfung.
 */
@Composable
fun PasswordCheckView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val req = rememberRequestState()

    SecureWindowEffect()

    var password by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    // Anzahl der Treffer für das zuletzt geprüfte Passwort; wird beim Ändern der Eingabe verworfen
    var count by remember { mutableStateOf<Long?>(null) }

    // Wird bei jedem Leeren erhöht; verspätete Antworten werden danach verworfen
    val generation = remember { intArrayOf(0) }

    fun clearAll() {
        generation[0]++
        password = ""
        revealed = false
        count = null
    }

    // Beim Verlassen des Screens und beim Wechsel in den Hintergrund leeren
    DisposableEffect(Unit) { onDispose { clearAll() } }
    ClearOnStop { clearAll() }

    fun check() {
        val pw = password
        if (pw.isEmpty()) return
        count = null
        val gen = generation[0]
        // Kein retry-Lambda: es würde das Passwort im Dialogzustand festhalten
        req.launch(scope, spinner = "Checking...") {
            val client = IPv64Client.get(ctx)
            val c = ipv64Retrying { client.threat.passwordCount(pw) }
            if (generation[0] == gen) count = c
        }
    }

    ScreenScaffold(Tabs.getLabel(Tab.password_check), navController, mainPadding) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Find out whether a password was tried by attackers at Honeypot64 sensors. " +
                        "The password is hashed on your device (SHA-1) and only the first 5 characters of the hash " +
                        "are sent. The password itself never leaves your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; count = null },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(onSearch = { check() }),
                trailingIcon = {
                    IconButton(onClick = { revealed = !revealed }) {
                        Icon(
                            painter = painterResource(
                                id = if (revealed) R.drawable.visibility_off_24px else R.drawable.visibility_24px
                            ),
                            contentDescription = if (revealed) "Hide password" else "Show password"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { check() }, enabled = password.isNotEmpty() && !req.isBusy) { Text("Check password") }

            count?.let { c ->
                SecurityCard {
                    if (c > 0L) {
                        StatusChip("Found in attacks", colorResource(R.color.ipv64_red))
                        Text(
                            "This password was used ${c.formatCount()} times in attacks. " +
                                    "Do not use it and change it everywhere you use it.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        StatusChip("Not found", colorResource(R.color.ipv64_green))
                        Text(
                            "This password was not seen in the Honeypot64 data. That does not mean it is safe: " +
                                    "use long, unique passwords and a password manager.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (password.isNotEmpty()) {
                OutlinedButton(onClick = { clearAll() }) { Text("Clear") }
            }
        }
    }
    RequestStateHost(req)
}
