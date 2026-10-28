package de.rpicloud.ipv64net.helper

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.secntfy.android.NtfyException
import com.secntfy.android.SecNtfy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object SecNtfyHelper {
    // Ntfy-Token dieses Geräts (Device-Token der Integration "IPv64 App"); geheim, nie loggen
    private const val DEVICE_TOKEN_KEY = "SECNTFY_DEVICE_TOKEN"

    // SecNtfy ist ein Singleton; parallele Registrierungen würden sich gegenseitig die Konfiguration überschreiben
    private val registrationMutex = Mutex()

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
                    register(appContext, apiUrl, appKey, fcmToken)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Zuletzt bekannter Ntfy-Token ohne Netzwerkzugriff; null, wenn das Gerät noch nie registriert wurde
    fun storedNtfyToken(context: Context): String? =
        PreferencesManager.loadString(context.applicationContext, DEVICE_TOKEN_KEY).trim().ifEmpty { null }

    /**
     * Ntfy-Token dieses Geräts: der gespeicherte, sonst wird das Gerät bei SecNtfy registriert.
     * Null, wenn kein Token ermittelt werden kann (keine SecNtfy-Konfiguration, kein FCM-Token, Registrierung fehlgeschlagen).
     */
    suspend fun ntfyToken(context: Context): String? {
        val appContext = context.applicationContext
        storedNtfyToken(appContext)?.let { return it }

        val apiUrl = PreferencesManager.loadString(appContext, "SECNTFY_API_URL")
        val appKey = PreferencesManager.loadString(appContext, "SECNTFY_APP_KEY")
        if (apiUrl.isEmpty() || appKey.isEmpty()) return null

        return try {
            val fcmToken = PreferencesManager.loadString(appContext, "FCM_TOKEN").ifEmpty { null }
                ?: fetchFcmToken()?.also { PreferencesManager.saveString(appContext, "FCM_TOKEN", it) }
                ?: return null
            register(appContext, apiUrl, appKey, fcmToken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Registriert das Gerät und speichert den zurückgegebenen Token
    private suspend fun register(appContext: Context, apiUrl: String, appKey: String, fcmToken: String): String? =
        withContext(Dispatchers.IO) {
            registrationMutex.withLock {
                val secntfy = configureLocked(appContext, apiUrl, appKey)
                val token = try {
                    registerOnce(secntfy, fcmToken)
                } catch (e: NtfyException.ConfigurationChanged) {
                    // Konfiguration hat sich während der Anfrage geändert: einmal wiederholen
                    registerOnce(secntfy, fcmToken)
                } catch (e: NtfyException.NoDevice) {
                    // Singleton ohne Gerät (nicht konfiguriert): neu konfigurieren und einmal wiederholen
                    registerOnce(configureLocked(appContext, apiUrl, appKey, force = true), fcmToken)
                }.trim().ifEmpty { null }
                if (token != null && token != storedNtfyToken(appContext)) {
                    PreferencesManager.saveString(appContext, DEVICE_TOKEN_KEY, token)
                }
                token
            }
        }

    private suspend fun registerOnce(secntfy: SecNtfy, fcmToken: String): String {
        secntfy.setFCMToken(fcmToken)
        return secntfy.registerDevice()
    }

    /**
     * Initialisiert und konfiguriert den SecNtfy-Singleton höchstens einmal pro Prozess
     * (erneut nur, wenn sich API-URL oder App-Key geändert haben). Wird auch vom Push-Empfang genutzt,
     * damit eine laufende Registrierung nicht durch eine erneute Initialisierung zurückgesetzt wird.
     * False, wenn SecNtfy nicht konfiguriert ist; Fehler der Bibliothek werden weitergereicht.
     */
    suspend fun ensureConfigured(context: Context): Boolean {
        val appContext = context.applicationContext
        val apiUrl = PreferencesManager.loadString(appContext, "SECNTFY_API_URL")
        val appKey = PreferencesManager.loadString(appContext, "SECNTFY_APP_KEY")
        if (apiUrl.isEmpty() || appKey.isEmpty()) return false
        withContext(Dispatchers.IO) {
            registrationMutex.withLock { configureLocked(appContext, apiUrl, appKey) }
        }
        return true
    }

    // Nur unter registrationMutex aufrufen
    private var configuredApiUrl: String? = null
    private var configuredAppKey: String? = null

    private fun configureLocked(appContext: Context, apiUrl: String, appKey: String, force: Boolean = false): SecNtfy {
        val secntfy = SecNtfy.getInstance()
        if (force || configuredApiUrl != apiUrl || configuredAppKey != appKey) {
            configuredApiUrl = null
            configuredAppKey = null
            secntfy.initialize(apiUrl = apiUrl, ctx = appContext)
            secntfy.configure(appKey)
            configuredApiUrl = apiUrl
            configuredAppKey = appKey
        }
        return secntfy
    }

    // Aktueller FCM-Token; null, wenn Firebase keinen liefert oder nicht innerhalb von 15 Sekunden antwortet
    private suspend fun fetchFcmToken(): String? = withTimeoutOrNull(15_000) {
        suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (cont.isActive) cont.resume(if (task.isSuccessful) task.result?.ifEmpty { null } else null)
            }
        }
    }
}
