package de.rpicloud.ipv64net.helper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.secntfy.android.SecNtfy
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.main.activity.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * [SecNtfyMessagingService] is a background message receiver extending [FirebaseMessagingService].
 * 
 * ## Android Best Practices & Background Execution:
 * - **Background Reception**: Even when the app is completely closed or removed from the recent apps list ("app slider"), 
 *   Firebase Cloud Messaging (FCM) wakes up this service to process incoming push payloads.
 * - **Delivery Receipts**: Automatically calls `SecNtfy.getInstance().messageReceived(messageId)` in an IO coroutine 
 *   to securely acknowledge receipt to the SecNtfy server.
 * - **Notification Channels**: Creates high-priority notification channels so users receive alerts reliably.
 */
class SecNtfyMessagingService : FirebaseMessagingService() {

    // SupervisorJob ensures child coroutine failures do not cancel the entire service scope.
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    /**
     * Called when a new FCM registration token is generated or refreshed.
     * Best Practice: Persist the token locally and register it with the SecNtfy client.
     */
    @Suppress("OverridingDeprecatedMember", "DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Save token to SharedPreferences for later use
        PreferencesManager.saveString(applicationContext, "FCM_TOKEN", token)
        try {
            val apiUrl = PreferencesManager.loadString(applicationContext, "SECNTFY_API_URL")
            val appKey = PreferencesManager.loadString(applicationContext, "SECNTFY_APP_KEY")
            if (apiUrl.isNotEmpty() && appKey.isNotEmpty()) {
                val secntfy = SecNtfy.getInstance()
                secntfy.initialize(apiUrl = apiUrl, ctx = applicationContext)
                secntfy.configure(appKey)
                secntfy.setFCMToken(token)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Called when an FCM message is received while the app is in the background or terminated.
     * Best Practice: Extract payload identifiers, display system notification, and send delivery receipt.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val messageId = data["message_id"] ?: data["id"] ?: remoteMessage.messageId ?: ""
        val title = remoteMessage.notification?.title ?: data["title"] ?: "IPv64.net Notification"
        val body = remoteMessage.notification?.body ?: data["body"] ?: data["message"] ?: "New notification received"

        // 1. Display system notification so the user sees it immediately
        showNotification(title, body)

        // 2. Acknowledge delivery receipt to SecNtfy backend if messageId is available
        if (messageId.isNotEmpty()) {
            scope.launch {
                try {
                    val apiUrl = PreferencesManager.loadString(applicationContext, "SECNTFY_API_URL")
                    val appKey = PreferencesManager.loadString(applicationContext, "SECNTFY_APP_KEY")
                    if (apiUrl.isNotEmpty() && appKey.isNotEmpty()) {
                        val secntfy = SecNtfy.getInstance()
                        secntfy.initialize(apiUrl = apiUrl, ctx = applicationContext)
                        secntfy.configure(appKey)
                        secntfy.messageReceived(messageId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Builds and displays a high-priority system notification with a PendingIntent pointing to [MainActivity].
     */
    private fun showNotification(title: String, message: String) {
        val channelId = "secntfy_channel"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Create NotificationChannel (Required for Android 8.0 / API 26+)
        val channel = NotificationChannel(
            channelId,
            "SecNtfy Notifications",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notifications received via SecNtfy"
        }
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.notifications_unread_24px)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel coroutine scope to avoid memory leaks when service is destroyed
        job.cancel()
    }
}
