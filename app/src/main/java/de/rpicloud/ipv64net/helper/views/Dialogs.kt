package de.rpicloud.ipv64net.helper.views

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import de.rpicloud.ipv64net.R
import android.Manifest
import android.annotation.SuppressLint
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import de.rpicloud.ipv64net.helper.QrCodeAnalyzer

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ShowPermissionDialog(
    currentPermissionRequest: String,
    vpnPermissionPermissionState: PermissionState? = null,
    readExternalStoragePermissionState: PermissionState? = null,
    cameraPermissionState: PermissionState? = null,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.release_alert_24px),
                contentDescription = "Header Icon"
            )
        },
        title = { Text("Berechtigung benötigt!") },
        text = {
            val message = when (currentPermissionRequest) {
                Manifest.permission.BIND_VPN_SERVICE -> "Diese App benötigt die VPN Berechtigung, um eine VPN Verbindung zu IPv64.net aufzubauen."
                Manifest.permission.READ_EXTERNAL_STORAGE -> "Diese App benötigt die Speicherberechtigung, um deine Config Dateien lesen zu können."
                Manifest.permission.CAMERA -> "Diese App benötigt die Kamera Berechtigung, um Wireguard Konfigurationscodes zu lesen."
                else -> ""
            }
            Text(message)
        },
        onDismissRequest = { },
        confirmButton = {
            TextButton(onClick = {
                when (currentPermissionRequest) {
                    Manifest.permission.BIND_VPN_SERVICE -> vpnPermissionPermissionState?.launchPermissionRequest()
                    Manifest.permission.READ_EXTERNAL_STORAGE -> readExternalStoragePermissionState?.launchPermissionRequest()
                    Manifest.permission.CAMERA -> cameraPermissionState?.launchPermissionRequest()
                }
                onDismissRequest()
            }) {
                Text(text = "Fortsetzen")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismissRequest()
            }) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = false, dismissOnClickOutside = false
        )
    )
}

@Composable
fun ErrorDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    dialogTitleResource: Int = 0,
    dialogTitle: String = "",
    dialogTextResource: Int = 0,
    dialogText: String = "",
    dialogConfirmText: Int = android.R.string.ok,
    dialogDismissText: Int = android.R.string.cancel,
    icon: Int = R.drawable.error_24px,
    showDismiss: Boolean = false
) {
    AlertDialog(
        modifier = Modifier.zIndex(1f), // Stellt sicher, dass der Dialog über allem in der App ist
        icon = {
            if (icon > 0) Icon(
                painter = painterResource(id = icon), contentDescription = "Header Icon"
            )
        },
        title = {
            if (dialogTitleResource > 0) Text(text = stringResource(id = dialogTitleResource))
            else Text(text = dialogTitle)
        },
        text = {
            if (dialogTextResource > 0) Text(text = stringResource(id = dialogTextResource))
            else StyledText(HtmlCompat.fromHtml(dialogText, HtmlCompat.FROM_HTML_MODE_LEGACY))
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirmation()
            }) {
                Text(text = stringResource(id = dialogConfirmText))
            }
        },
        dismissButton = {
            if (showDismiss) {
                TextButton(onClick = {
//                    onDismissRequest()
                }) {
                    Text(stringResource(id = dialogDismissText))
                }
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = false, dismissOnClickOutside = false
        )
    )
}

@SuppressLint("ResourceType")
@Composable
fun StyledText(text: CharSequence, modifier: Modifier = Modifier) {
    val colorScheme = colorScheme.primary
    AndroidView(modifier = modifier, factory = { context ->
        TextView(context).apply {
            setTextColor(colorScheme.toArgb())
        }
    }, update = {
        it.text = text
    })
}

@Composable
fun SpinnerDialog(
    spinnerTextResource: Int = 0, spinnerText: String = ""
) {
    Dialog(onDismissRequest = { }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(modifier = Modifier.padding(6.dp)) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .width(64.dp)
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center),
                    color = colorScheme.secondary,
                    trackColor = colorScheme.surfaceVariant
                )
                if (spinnerTextResource > 0) Text(
                    text = stringResource(id = spinnerTextResource),
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.CenterStart)
                        .padding(start = 8.dp),
                    textAlign = TextAlign.Center,
                )
                else Text(
                    text = spinnerText,
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.CenterStart)
                        .padding(start = 8.dp),
                    textAlign = TextAlign.Center,
                )

            }
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun QRCodeDialogView(onDismissRequest: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    var scannedText by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
        }
    }

    LaunchedEffect(cameraProviderFuture) {
        cameraProvider = cameraProviderFuture.get()
    }

    Dialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = { onDismissRequest("") }) {

        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)

                    val preview = Preview.Builder().build()
                    val selector = CameraSelector.DEFAULT_BACK_CAMERA
                    preview.surfaceProvider = previewView.surfaceProvider

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(
                        ContextCompat.getMainExecutor(ctx),
                        QrCodeAnalyzer { result ->
                            if (scannedText.isEmpty()) {
                                scannedText = result
                                onDismissRequest(result)
                            }
                        }
                    )

                    try {
                        val provider = cameraProviderFuture.get()
                        provider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
                        cameraProvider = provider
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}