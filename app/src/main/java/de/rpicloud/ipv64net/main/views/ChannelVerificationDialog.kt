package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.Hc2ChannelCache
import de.rpicloud.ipv64net.helper.IPv64Client
import de.rpicloud.ipv64net.helper.IPv64Context
import de.rpicloud.ipv64net.helper.displayName
import de.rpicloud.ipv64net.helper.ipv64Request
import de.rpicloud.ipv64net.helper.toError
import de.sr.ipv64.IPv64ApiException
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.Hc2Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val VERIFICATION_CODE_LENGTH = 6
private const val RESEND_COOLDOWN_SECONDS = 60

/**
 * Bestätigt einen Mail-/SMS-Kanal per Code: 1) Code senden, 2) 6-stelligen Code eingeben (erneut senden nach 60 s).
 * Fehler werden im Dialog angezeigt. Nach Erfolg wird der Kanal-Cache invalidiert und [onVerified] aufgerufen.
 */
@Composable
fun ChannelVerificationDialog(channel: Hc2Channel, onVerified: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val channelId = channel.id

    var codeSent by rememberSaveable(channelId) { mutableStateOf(false) }
    var code by rememberSaveable(channelId) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var infoText by remember { mutableStateOf<String?>(null) }
    // Wird bei jedem Senden erhöht und startet den Countdown neu
    var sendCount by remember { mutableIntStateOf(0) }
    var resendSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(sendCount) {
        if (sendCount == 0) return@LaunchedEffect
        resendSeconds = RESEND_COOLDOWN_SECONDS
        while (resendSeconds > 0) {
            delay(1000)
            resendSeconds--
        }
    }

    fun sendCode() {
        val id = channelId ?: return
        if (busy) return
        busy = true
        errorText = null
        infoText = null
        scope.launch {
            try {
                // Kein automatischer Retry: 429 bedeutet hier "zu viele Codes angefordert"
                ipv64Request { IPv64Client.get(ctx).hc2.channels.sendVerificationCode(id) }
                if (codeSent) infoText = "A new code has been sent."
                codeSent = true
                sendCount++
            } catch (e: IPv64ApiException) {
                errorText = if (e.isRateLimited) "Too many codes requested, try again later."
                else e.toError(IPv64Context.Other).inlineText()
            } catch (e: IPv64Exception) {
                errorText = e.toError(IPv64Context.Other).inlineText()
            } finally {
                busy = false
            }
        }
    }

    fun verify() {
        val id = channelId ?: return
        if (busy || code.length != VERIFICATION_CODE_LENGTH) return
        busy = true
        errorText = null
        infoText = null
        scope.launch {
            try {
                ipv64Request { IPv64Client.get(ctx).hc2.channels.verify(id, code) }
                Hc2ChannelCache.invalidate()
                onVerified()
            } catch (e: IPv64ApiException) {
                errorText = when {
                    e.isRateLimited -> "Too many attempts, try again later."
                    e.isValidationError || e.code.equals("invalid_code", ignoreCase = true) -> "Invalid or expired code."
                    else -> e.toError(IPv64Context.Other).inlineText()
                }
            } catch (e: IPv64Exception) {
                errorText = e.toError(IPv64Context.Other).inlineText()
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        icon = { Icon(painter = painterResource(id = R.drawable.verified_24px), contentDescription = null) },
        title = { Text("Verify channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (channelId == null) {
                    Text("This channel cannot be verified in the app.")
                } else if (!codeSent) {
                    Text("A 6-digit code will be sent to “${channel.displayName}”.")
                    Text(
                        "You can request up to 5 codes per hour. Each code is valid for 15 minutes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("Enter the 6-digit code sent to “${channel.displayName}”.")
                    OutlinedTextField(
                        value = code,
                        onValueChange = { input ->
                            code = input.filter { it.isDigit() }.take(VERIFICATION_CODE_LENGTH)
                            errorText = null
                        },
                        label = { Text("Code") },
                        singleLine = true,
                        enabled = !busy,
                        isError = errorText != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { sendCode() }, enabled = !busy && resendSeconds == 0) {
                            Text(if (resendSeconds > 0) "Resend code (${resendSeconds} s)" else "Resend code")
                        }
                    }
                    Text(
                        "Up to 5 codes per hour, valid for 15 minutes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                infoText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                errorText?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colorResource(R.color.ipv64_red))
                }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        onDismissRequest = { if (!busy) onDismiss() },
        confirmButton = {
            if (channelId != null) {
                if (!codeSent) {
                    TextButton(onClick = { sendCode() }, enabled = !busy) { Text("Send code") }
                } else {
                    TextButton(
                        onClick = { verify() },
                        enabled = !busy && code.length == VERIFICATION_CODE_LENGTH
                    ) { Text("Verify") }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (channelId == null) "Close" else "Later") }
        },
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = false)
    )
}
