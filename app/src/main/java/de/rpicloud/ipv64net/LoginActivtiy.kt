package de.rpicloud.ipv64net

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import de.rpicloud.ipv64net.databinding.ActivityLoginActivtiyBinding
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.BarcodeFormat
import io.github.g00fy2.quickie.config.ScannerConfig

class LoginActivity : AppCompatActivity() {
    private lateinit var activity: AppCompatActivity
    private lateinit var binding: ActivityLoginActivtiyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginActivtiyBinding.inflate(layoutInflater)
        setContentView(binding.root)
//        setContentView(R.layout.activity_login_activtiy)

        val isPortrait = resources.getBoolean(R.bool.portrait_only)
        Functions().setOrientaiton(this, isPortrait)

        activity = this

        val scanCustomCode = this.registerForActivityResult(ScanCustomCode(), ::handleResult)

        binding.btnLogin.setOnClickListener {
            val text = binding.tiePassword.text.toString()
            if (text.length >= 32) {
                login(text)
            } else {
                val toastText = if (text.isEmpty()) {
                    "API-KEY ist leer!"
                } else {
                    "API-KEY zu kurz!"
                }
                Toast.makeText(applicationContext, toastText, Toast.LENGTH_LONG).show()
            }
        }

        binding.btnScanqrcode.setOnClickListener {
            println("LOGIN")

            scanCustomCode.launch(ScannerConfig.build {
                setBarcodeFormats(listOf(BarcodeFormat.FORMAT_QR_CODE)) // set interested barcode formats
                setOverlayStringRes(R.string.scan_barcode) // string resource used for the scanner overlay
                setOverlayDrawableRes(R.drawable.ic_scan_barcode) // drawable resource used for the scanner overlay
                setHapticSuccessFeedback(true) // enable (default) or disable haptic feedback when a barcode was detected
                setShowTorchToggle(true) // show or hide (default) torch/flashlight toggle button
                setShowCloseButton(true) // show or hide (default) close button
                setHorizontalFrameRatio(1f) // set the horizontal overlay ratio (default is 1 / square frame)
                setUseFrontCamera(false) // use the front camera
            })
        }
    }

    private fun handleResult(result: QRResult) {
        val text = when (result) {
            is QRResult.QRSuccess -> login(result.content.rawValue)
            QRResult.QRUserCanceled -> Toast.makeText(
                applicationContext,
                "Beendet vom Nutzer",
                Toast.LENGTH_LONG
            ).show()

            QRResult.QRMissingPermission -> Toast.makeText(
                applicationContext,
                "Fehlende Berechtigungen!",
                Toast.LENGTH_LONG
            ).show()

            is QRResult.QRError -> Toast.makeText(
                applicationContext,
                "${result.exception.javaClass.simpleName}: ${result.exception.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
        }
        println(text)
    }

    private fun login(apiKey: String) {
        if (apiKey.isEmpty()) {
            return
        }

        activity.applicationContext.setSharedString(
            "APIKEY", "APIKEY", apiKey
        )
        activity.applicationContext.setSharedBool("ISLOGGEDIN", "ISLOGGEDIN", true)
        intent.setClass(activity, MainActivity::class.java)
        finish()
        startActivity(intent)
    }
}