package de.rpicloud.ipv64net.helper

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.secntfy.android.SecNtfy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SecNtfyHelper {
    /**
     * Fetches the current Firebase FCM token and registers the device with the SecNtfy backend.
     * Best Practice: Call this on app startup and whenever tokens/credentials change.
     */
    fun registerDeviceToken(context: Context) {
        val appContext = context.applicationContext
        val apiUrl = PreferencesManager.loadString(appContext, "SECNTFY_API_URL")
        val appKey = PreferencesManager.loadString(appContext, "SECNTFY_APP_KEY")

        if (apiUrl.isEmpty() || appKey.isEmpty()) return

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                task.exception?.printStackTrace()
                return@addOnCompleteListener
            }
            val fcmToken = task.result ?: return@addOnCompleteListener
            PreferencesManager.saveString(appContext, "FCM_TOKEN", fcmToken)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val secntfy = SecNtfy.getInstance()
                    secntfy.initialize(apiUrl = apiUrl, ctx = appContext)
                    secntfy.configure(appKey)
                    secntfy.setFCMToken(fcmToken)
                    val registeredToken = secntfy.registerDevice()
                    println("✅ SecNtfy device registered successfully: $registeredToken")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
