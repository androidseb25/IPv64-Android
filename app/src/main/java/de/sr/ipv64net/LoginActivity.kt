package de.sr.ipv64net

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ButtonDefaults.buttonColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import de.sr.ipv64net.ui.theme.IPv64Color50
import de.sr.ipv64net.ui.theme.IPv64netTheme
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IPv64netTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    val cameraPermissionState =
                        rememberPermissionState(permission = android.Manifest.permission.CAMERA)

                    DisposableEffect(Unit) {
                        val handler = Handler(Looper.getMainLooper())

                        val runnable = {
                            cameraPermissionState.launchPermissionRequest()
                        }

                        handler.postDelayed(runnable, 1_000)

                        onDispose {
                            handler.removeCallbacks(runnable)
                        }
                    }

                    GreetingL()
                }
            }
        }
    }
}

@Composable
fun GreetingL() {
    var showDialog = remember {
        mutableStateOf(false)
    }

    if (showDialog.value) {
        ScannerDialog(showDialog)
    } else {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ipv64_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .width(150.dp)
                        .height(150.dp),
                    contentScale = ContentScale.Fit
                )

                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(8.dp),
                    onClick = {
                        showDialog.value = true
                    },
                    colors = buttonColors(
                        containerColor = IPv64Color50
                    )
                ) {
                    Text("Login mit QR Code".uppercase(Locale.getDefault()))
                }
            }
        }
    }
}

@Preview(device = Devices.PIXEL_2_XL, showSystemUi = true)
@Composable
fun DefaultLPreview() {
    IPv64netTheme {
        GreetingL()
    }
}