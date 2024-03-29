package de.rpicloud.ipv64net

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import de.rpicloud.ipv64net.databinding.ActivityBiometricBinding
import java.util.concurrent.Executor

class BiometricActivity : AppCompatActivity() {

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private lateinit var binding: ActivityBiometricBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBiometricBinding.inflate(layoutInflater)
        setContentView(binding.root)
        //setContentView(R.layout.activity_biometric)

        executor = ContextCompat.getMainExecutor(this)

        // CHECK IF BIOMETRIC IS AVAILABLE

        biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                @SuppressLint("SetTextI18n")
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    val errorText = "Error, $errString"
                    println(errorText)
//                    Toast.makeText(applicationContext, errorText, Toast.LENGTH_LONG).show()
                }

                @SuppressLint("SetTextI18n")
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val successText = "Successful auth"
                    println(successText)
                    val intent = Intent(applicationContext, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.setClass(applicationContext, MainActivity::class.java)
                    applicationContext.startActivity(intent)
                    finish()
                }

                @SuppressLint("SetTextI18n")
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    val errorText = "Authentication Failed"
//                    Toast.makeText(applicationContext, errorText, Toast.LENGTH_LONG).show()
                    println(errorText)
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Fingerabdruck")
            .setSubtitle("Nutze deinen Fingerabdruck um IPv64.net zu entsperren")
            .setNegativeButtonText("Abbrechen")
            .build()

        binding.btnFingerEnable.setOnClickListener {
            biometricPrompt.authenticate(promptInfo)
        }
        biometricPrompt.authenticate(promptInfo)
    }
}