package de.rpicloud.ipv64net.helper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.BitmapFactory
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
import java.net.HttpURLConnection
import java.net.URL

/**
 * [SecNtfyMessagingService] is a background message receiver extending [FirebaseMessagingService].
 */
class SecNtfyMessagingService : FirebaseMessagingService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    @Suppress("OverridingDeprecatedMember", "DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        println("🔔 SecNtfyMessagingService onNewToken: $token")
        PreferencesManager.saveString(applicationContext, "FCM_TOKEN", token)
        SecNtfyHelper.registerDeviceToken(applicationContext)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        println("🔔 ========================================")
        println("🔔 SecNtfyMessagingService: onMessageReceived triggered!")
        println("🔔 RemoteMessage data: ${remoteMessage.data}")
        println("🔔 RemoteMessage notification: ${remoteMessage.notification?.title} / ${remoteMessage.notification?.body}")
        println("🔔 ========================================")

        try {
            val apiUrl = PreferencesManager.loadString(applicationContext, "SECNTFY_API_URL")
            val appKey = PreferencesManager.loadString(applicationContext, "SECNTFY_APP_KEY")
            println("🔔 SecNtfy Config -> URL: $apiUrl, AppKey length: ${appKey.length}")

            val secntfy = SecNtfy.getInstance()
            if (apiUrl.isNotEmpty() && appKey.isNotEmpty()) {
                secntfy.initialize(apiUrl = apiUrl, ctx = applicationContext)
                secntfy.configure(appKey)
            }

            val data = remoteMessage.data
            val messageId = data["messageId"] ?: ""
            val rawTitle = data["title"] ?: "IPv64.net Notification"
            val rawBody = data["body"] ?: "New notification received"
            val rawImage = data["image"] ?: ""

            println("🔔 Raw Title: $rawTitle, Raw Body: $rawBody, Image: $rawImage")

            val clearTitle = try {
                secntfy.decryptMessage(rawTitle).also { println("🔔 Decrypted Title: $it") }
            } catch (e: Exception) {
                println("⚠️ Failed to decrypt title, using raw: ${e.message}")
                rawTitle
            }

            val clearBody = try {
                secntfy.decryptMessage(rawBody).also { println("🔔 Decrypted Body: $it") }
            } catch (e: Exception) {
                println("⚠️ Failed to decrypt body, using raw: ${e.message}")
                rawBody
            }

            val clearImage = try {
                if (rawImage.isNotEmpty()) secntfy.decryptMessage(rawImage).also { println("🔔 Decrypted Image: $it") } else ""
            } catch (e: Exception) {
                println("⚠️ Failed to decrypt image, using raw: ${e.message}")
                rawImage
            }

            // 1. Display system notification
            showNotification(clearTitle, clearBody, clearImage)

            // 2. Acknowledge delivery receipt
            if (messageId.isNotEmpty()) {
                scope.launch {
                    try {
                        if (apiUrl.isNotEmpty() && appKey.isNotEmpty()) {
                            val receipt = secntfy.messageReceived(messageId)
                            println("✅ SecNtfy delivery receipt sent successfully: $receipt")
                        }
                    } catch (e: Exception) {
                        println("❌ Failed to send delivery receipt: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            println("❌ Error in onMessageReceived: ${e.message}")
            e.printStackTrace()
            val fallbackTitle = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "IPv64.net Notification"
            val fallbackBody = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "New notification received"
            showNotification(fallbackTitle, fallbackBody, "")
        }
    }

    private fun showNotification(title: String, message: String, imageUrl: String) {
        val channelId = "secntfy_channel"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "SecNtfy Notifications",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notifications received via SecNtfy"
            enableLights(true)
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.notifications_unread_24px)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        if (imageUrl.startsWith("http")) {
            try {
                val url = URL(imageUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connect()
                val bitmap = BitmapFactory.decodeStream(connection.inputStream)
                if (bitmap != null) {
                    builder.setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(bitmap)
                            .bigLargeIcon(null as android.graphics.Bitmap?)
                    )
                    builder.setLargeIcon(bitmap)
                }
            } catch (e: Exception) {
                println("⚠️ Failed to load notification image from URL: ${e.message}")
            }
        }

        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, builder.build())
        println("✅ Notification displayed successfully with ID: $notificationId")
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
