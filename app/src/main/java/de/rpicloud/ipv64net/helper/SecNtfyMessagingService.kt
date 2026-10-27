package de.rpicloud.ipv64net.helper

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
 * Overrides [handleIntent] to intercept background notification payloads before FCM auto-displays ciphertext.
 */
class SecNtfyMessagingService : FirebaseMessagingService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    @Suppress("OverridingDeprecatedMember", "DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PreferencesManager.saveString(applicationContext, "FCM_TOKEN", token)
        SecNtfyHelper.registerDeviceToken(applicationContext)
    }

    /**
     * Intercepts notification intents so FCM never displays the encrypted ciphertext itself in the background.
     */
    override fun handleIntent(intent: Intent) {
        val extras = intent.extras
        val title = extras?.getString("gcm.notification.title")
            ?: extras?.getString("gcm.n.title")
            ?: extras?.getString("title")

        if (intent.action == "com.google.android.c2dm.intent.RECEIVE" && extras != null && title != null) {
            val body = extras.getString("gcm.notification.body")
                ?: extras.getString("gcm.n.body")
                ?: extras.getString("body")
                ?: ""
            val image = extras.getString("gcm.notification.image")
                ?: extras.getString("gcm.n.image")
                ?: extras.getString("image")
                ?: ""
            val messageId = extras.getString("messageId")
                ?: extras.getString("id")
                ?: ""

            processNotification(title, body, image, messageId)
        } else {
            super.handleIntent(intent)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        processNotification(
            title = remoteMessage.data["title"].orEmpty(),
            body = remoteMessage.data["body"].orEmpty(),
            image = remoteMessage.data["image"].orEmpty(),
            messageId = remoteMessage.data["messageId"] ?: remoteMessage.data["id"] ?: remoteMessage.messageId.orEmpty(),
        )
    }

    private fun processNotification(title: String, body: String, image: String, messageId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val apiUrl = PreferencesManager.loadString(applicationContext, "SECNTFY_API_URL")
                val appKey = PreferencesManager.loadString(applicationContext, "SECNTFY_APP_KEY")

                val secntfy = SecNtfy.getInstance()
                if (apiUrl.isNotEmpty() && appKey.isNotEmpty()) {
                    secntfy.initialize(apiUrl = apiUrl, ctx = applicationContext)
                    secntfy.configure(appKey)
                }

                val decryptedTitle = try {
                    if (title.isNotEmpty()) secntfy.decryptMessage(title) else "IPv64.net Notification"
                } catch (_: Exception) {
                    title
                }

                val decryptedBody = try {
                    if (body.isNotEmpty()) secntfy.decryptMessage(body) else "New notification received"
                } catch (_: Exception) {
                    body
                }

                val decryptedImage = try {
                    if (image.isNotEmpty()) secntfy.decryptMessage(image) else ""
                } catch (_: Exception) {
                    image
                }

                showNotification(decryptedTitle, decryptedBody, decryptedImage)

                if (messageId.isNotEmpty()) {
                    try {
                        if (apiUrl.isNotEmpty() && appKey.isNotEmpty()) {
                            secntfy.messageReceived(messageId)
                        }
                    } catch (error: Exception) {
                        error.printStackTrace()
                    }
                }
            } catch (error: Exception) {
                error.printStackTrace()
            }
        }
    }

    private fun showNotification(title: String, message: String, imageUrl: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

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
                e.printStackTrace()
            }
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
