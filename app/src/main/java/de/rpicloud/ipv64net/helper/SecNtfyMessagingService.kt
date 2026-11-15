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
import com.secntfy.android.DeliveryReceipt
import com.secntfy.android.SecNtfy
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.SecNtfyLog.Status
import de.rpicloud.ipv64net.main.activity.MainActivity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * [SecNtfyMessagingService] is a background message receiver extending [FirebaseMessagingService].
 * Overrides [handleIntent] to intercept background notification payloads before FCM auto-displays ciphertext.
 */
class SecNtfyMessagingService : FirebaseMessagingService() {

    @Suppress("OverridingDeprecatedMember", "DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        SecNtfyLog.record(applicationContext, "Push token refreshed", status = Status.SUCCESS)
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
        val body = extras?.getString("gcm.notification.body")
            ?: extras?.getString("gcm.n.body")
            ?: extras?.getString("body")
        val messageId = extras?.getString("messageId")?.takeIf { it.isNotBlank() }
            ?: extras?.getString("id").orEmpty()

        if (intent.action == "com.google.android.c2dm.intent.RECEIVE" && extras != null &&
            (title != null || body != null || messageId.isNotBlank())
        ) {
            val image = extras.getString("gcm.notification.image")
                ?: extras.getString("gcm.n.image")
                ?: extras.getString("image")
                ?: ""
            processNotification(title.orEmpty(), body.orEmpty(), image, messageId)
        } else {
            super.handleIntent(intent)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        processNotification(
            title = (remoteMessage.data["title"] ?: remoteMessage.notification?.title).orEmpty(),
            body = (remoteMessage.data["body"] ?: remoteMessage.notification?.body).orEmpty(),
            image = (remoteMessage.data["image"] ?: remoteMessage.notification?.imageUrl?.toString()).orEmpty(),
            messageId = remoteMessage.data["messageId"]?.takeIf { it.isNotBlank() } ?: remoteMessage.data["id"].orEmpty(),
        )
    }

    // Synchron (handleIntent/onMessageReceived laufen bereits im Hintergrund-Thread): Firebase beendet den Dienst
    // direkt nach der Rückkehr, ein gestarteter Job ginge mit Empfangsbestätigung/Benachrichtigung verloren
    private fun processNotification(title: String, body: String, image: String, messageId: String) {
        runBlocking {
            val context = applicationContext
            val secntfy = SecNtfy.getInstance()
            val notificationId = System.currentTimeMillis().toInt()
            SecNtfyLog.record(context, "Notification received")
            if (messageId.isBlank()) SecNtfyLog.record(context, "Receipt unavailable: missing message ID", status = Status.SKIPPED)

            val notification = withSecNtfyReceipt(messageId, acknowledge = { id ->
                try {
                    when (secntfy.messageReceived(id)) {
                        DeliveryReceipt.DELIVERED -> SecNtfyLog.record(context, "Delivery receipt accepted", status = Status.SUCCESS)
                        DeliveryReceipt.ALREADY_DELIVERED -> SecNtfyLog.record(context, "Delivery receipt already accepted", status = Status.SUCCESS)
                    }
                } catch (error: Exception) {
                    SecNtfyLog.record(context, "Delivery receipt failed", error)
                }
            }) {
                try {
                    // Registration may briefly hold the configuration lock while the SDK is already usable.
                    when (withTimeoutOrNull(3_000) { SecNtfyHelper.ensureConfigured(context) }) {
                        null -> SecNtfyLog.record(context, "Timed out waiting for push configuration", status = Status.FAILED)
                        false -> SecNtfyLog.record(context, "Push configuration missing", status = Status.SKIPPED)
                        true -> Unit
                    }
                    fun decrypt(value: String, fallback: String, field: String): String {
                        if (value.isEmpty()) return fallback
                        return try {
                            secntfy.decryptMessage(value)
                        } catch (error: Exception) {
                            SecNtfyLog.record(context, "Could not decrypt notification $field", error)
                            value
                        }
                    }
                    val decryptedTitle = decrypt(title, "IPv64.net Notification", "title")
                    val decryptedBody = decrypt(body, "New notification received", "body")
                    val decryptedImage = decrypt(image, "", "image")
                    val builder = showNotification(notificationId, decryptedTitle, decryptedBody)
                    if (builder == null) {
                        SecNtfyLog.record(context, "Notification permission missing", status = Status.SKIPPED)
                        null
                    } else {
                        SecNtfyLog.record(context, "Notification posted", status = Status.SUCCESS)
                        Triple(builder, decryptedBody, decryptedImage)
                    }
                } catch (error: Exception) {
                    SecNtfyLog.record(context, "Could not prepare or post notification", error)
                    null
                }
            }
            // An optional image fetch must never delay the receipt.
            notification?.let { (builder, text, url) ->
                if (url.startsWith("https://")) showImage(notificationId, builder, text, url)
            }
        }
    }

    // Zeigt die Textbenachrichtigung an; liefert den Builder für das spätere Bild, null ohne Berechtigung
    private fun showNotification(notificationId: Int, title: String, message: String): NotificationCompat.Builder? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
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
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            // Nachträgliches Bild-Update ohne zweiten Ton/Vibration
            .setOnlyAlertOnce(true)

        notificationManager.notify(notificationId, builder.build())
        return builder
    }

    // Bild mit kurzen Timeouts laden und die Benachrichtigung unter derselben ID erneut anzeigen; Fehler ignorieren
    private fun showImage(notificationId: Int, builder: NotificationCompat.Builder, message: String, imageUrl: String) {
        try {
            val url = URL(imageUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 4_000
            connection.readTimeout = 4_000
            connection.connect()
            val bitmap = try {
                connection.inputStream.use(BitmapFactory::decodeStream)
            } finally {
                connection.disconnect()
            } ?: return
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .setSummaryText(message)
                    .bigLargeIcon(null as android.graphics.Bitmap?)
            )
            builder.setLargeIcon(bitmap)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(notificationId, builder.build())
            SecNtfyLog.record(applicationContext, "Notification image posted", status = Status.SUCCESS)
        } catch (e: Exception) {
            SecNtfyLog.record(applicationContext, "Could not load notification image", e)
        }
    }
}

internal suspend fun <T> withSecNtfyReceipt(
    messageId: String,
    acknowledge: suspend (String) -> Unit,
    process: suspend () -> T
): T = try {
    process()
} finally {
    if (messageId.isNotBlank()) acknowledge(messageId)
}
