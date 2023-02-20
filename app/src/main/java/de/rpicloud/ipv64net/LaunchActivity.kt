package de.rpicloud.ipv64net

import android.app.UiModeManager
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class LaunchActivity : AppCompatActivity() {
    private lateinit var activeScreen: LaunchScreens

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mode = applicationContext.getSharedInt("THEME", "THEME")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val man = applicationContext.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            man.setApplicationNightMode(mode)
        } else {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
        val isPortrait = resources.getBoolean(R.bool.portrait_only)
        Functions().setOrientaiton(this, isPortrait)

        val isLoggedIn: Boolean = applicationContext.getSharedBool("ISLOGGEDIN", "ISLOGGEDIN")
        val isBiometric: Boolean = applicationContext.getSharedBool("BIOMETRIC", "BIOMETRIC")
        val APIKEY = this.getSharedString("APIKEY", "APIKEY")

        activeScreen = if (!isLoggedIn)
            LaunchScreens.LOGIN
        else {
            LaunchScreens.MAIN
        }

        if (isBiometric)
            activeScreen = LaunchScreens.BIOMETRIC

        println("APIKEY: $APIKEY")
        println(activeScreen)

        val intent = Intent(applicationContext, MainActivity::class.java)
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK)

        when (activeScreen) {
            LaunchScreens.LOGIN -> intent.setClass(applicationContext, LoginActivity::class.java)
            LaunchScreens.BIOMETRIC -> intent.setClass(applicationContext, BiometricActivity::class.java)
            else -> {
                intent.setClass(applicationContext, MainActivity::class.java)
            }
        }
        fetchFCMToken()

        applicationContext.startActivity(intent)
        finish()
    }

    private fun fetchFCMToken() {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    return@OnCompleteListener
                }

                // Get new FCM registration token
                val token = task.result
                println("FCM TOKEN: $token")

                val os = "${System.getProperty("os.version")} SDK: ${Build.VERSION.SDK_INT}"
                val device =
                    "Device: ${Build.DEVICE} Model (and Product): ${Build.MODEL} (${Build.PRODUCT})"

                ApiNetwork.context = applicationContext
                if (getSharedString("FCM_TOKEN", "FCM_TOKEN") != token!!) {
                    setSharedString("FCM_TOKEN", "FCM_TOKEN", token)
                    GlobalScope.launch(Dispatchers.Default) {
                        ApiNetwork.PostAddIntegration("mobil", token, Build.MODEL)
                    }
                }
            })
    }
}