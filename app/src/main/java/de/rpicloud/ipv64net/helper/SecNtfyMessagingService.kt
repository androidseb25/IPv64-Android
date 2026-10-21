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

class SecNtfyMessagingService : FirebaseMessagingService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    @Suppress("OverridingDeprecatedMember", "DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PreferencesManager.saveString(applicationContext, "FCM_TOKEN", token)
        try {
            val secntfy = SecNtfy.getInstance()
            secntfy.setFCMToken(token)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val messageId = data["message_id"] ?: data["id"] ?: remoteMessage.messageId ?: ""
        val title = remoteMessage.notification?.title ?: data["title"] ?: "IPv64.net Notification"
        val body = remoteMessage.notification?.body ?: data["body"] ?: data["message"] ?: "New notification received"

        // Show notification so user sees it even when app is closed / swiped away
        showNotification(title, body)

        // Process SecNtfy message received / delivery receipt if messageId is present
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

    private fun showNotification(title: String, message: String) {
        val channelId = "secntfy_channel"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

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
        job.cancel()
    }
}
