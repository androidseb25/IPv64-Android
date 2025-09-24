package de.rpicloud.ipv64net.main.startup.views

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.PreferencesManager
import de.rpicloud.ipv64net.helper.findActivity
import de.rpicloud.ipv64net.helper.views.QRCodeDialogView
import de.rpicloud.ipv64net.helper.views.ShowPermissionDialog
import de.rpicloud.ipv64net.main.activity.MainActivity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun LoginView(navController: NavHostController) {

    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var hasHandledResult by remember { mutableStateOf(false) }
    var currentPermissionRequest by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var showRationaleDialog by remember { mutableStateOf(false) }

    val cameraPermissionState = rememberPermissionState(
        permission = Manifest.permission.CAMERA
    )
    val readExternalStoragePermissionState = rememberPermissionState(
        permission = Manifest.permission.READ_EXTERNAL_STORAGE
    )

    fun openCamera() {
        hasHandledResult = false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || cameraPermissionState.status.isGranted) {
            currentPermissionRequest = Manifest.permission.CAMERA
            showRationaleDialog = cameraPermissionState.status.shouldShowRationale
        } else {
            cameraPermissionState.launchPermissionRequest()
        }

        if (cameraPermissionState.status.isGranted) {
            // CAMERA CODE
            showDialog = true
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || readExternalStoragePermissionState.status.isGranted) {
            currentPermissionRequest = Manifest.permission.READ_EXTERNAL_STORAGE
            showRationaleDialog = readExternalStoragePermissionState.status.shouldShowRationale
        } else {
            readExternalStoragePermissionState.launchPermissionRequest()
        }
        apiKey = PreferencesManager.loadString(context, "APIKEY")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Login")
                },
                actions = {
                    IconButton(onClick = {
                        openCamera()
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.qr_code_24px),
                            contentDescription = "Scannen"
                        )
                    }
                },
                modifier = Modifier.statusBarsPadding()
            )
        },
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .padding(it)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("1. ")
                        }
                        append("Go to ipv64.net and log in to your account.")
                    }, modifier = Modifier.padding(top = 16.dp))
                    Text(buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("2. ")
                        }
                        append("Select the ")

                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Account")
                        }
                        append(" button at the top.")
                    }, modifier = Modifier.padding(top = 16.dp))
                    Text(buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("3. ")
                        }
                        append("A drop-down menu will open. Select ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("API")
                        }
                        append(".")
                    }, modifier = Modifier.padding(top = 16.dp))
                    Text(buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("4. ")
                        }
                        append("You will now see a list of API keys (if you have already created more than one). Select the one you want to use by tapping the QR code button.")
                    }, modifier = Modifier.padding(top = 16.dp))
                    Text(buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("5. ")
                        }
                        append("Open the QR code scanner in this app and scan the QR code.")
                    }, modifier = Modifier.padding(top = 16.dp))
                    Text(buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("6. ")
                        }
                        append("Done! You are now logged in.")
                    }, modifier = Modifier.padding(top = 16.dp))

                    OutlinedTextField(
                        value = apiKey,
                        singleLine = true,
                        label = { Text("API Key") },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 25.dp),
                        onValueChange = { newText -> apiKey = newText },
                    )
                }
                Column(modifier = Modifier.padding(bottom = 32.dp)) {
                    Button(
                        onClick = {
                            PreferencesManager.saveString(context, "APIKEY", apiKey)
                            val activity = context.findActivity()
                            val intent = Intent(activity, MainActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            activity?.startActivity(intent)
                            activity?.finish()
                        },
                        enabled = !apiKey.isEmpty(),
                        modifier = Modifier
                            .padding()
                            .height(53.dp)
                            .fillMaxWidth()
                    ) {
                        Text("Login")
                    }
                }
            }
        }
    }

    if (showRationaleDialog) {
        ShowPermissionDialog(
            currentPermissionRequest = currentPermissionRequest,
            readExternalStoragePermissionState = readExternalStoragePermissionState,
            cameraPermissionState = cameraPermissionState
        ) {
            showRationaleDialog = false
        }
    }

    if (showDialog) {
        QRCodeDialogView(
            onDismissRequest = {
                if (!hasHandledResult) {
                    hasHandledResult = true
                    showDialog = false
                    apiKey = it
                    PreferencesManager.saveString(context, "APIKEY", it)
                }
            }
        )
    }
}