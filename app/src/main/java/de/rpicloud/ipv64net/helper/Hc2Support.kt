package de.rpicloud.ipv64net.helper

import android.content.Context
import android.os.SystemClock
import androidx.annotation.DrawableRes
import de.rpicloud.ipv64net.R
import de.sr.ipv64.model.Hc2Channel
import de.sr.ipv64.model.Hc2ChannelType
import de.sr.ipv64.model.Hc2Check
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Schlüssel für Werte, die per previousBackStackEntry.savedStateHandle zwischen Screens übergeben werden
object Hc2NavKeys {
    // String: Check-Token, Liste → Detail
    const val SELECTED_HC2_TOKEN = "SELECTED_HC2_TOKEN"

    // String: Check-Token, Detail → Bearbeiten
    const val EDIT_HC2_TOKEN = "EDIT_HC2_TOKEN"

    // Long: Kanal-ID, Benachrichtigungen → Kanal bearbeiten
    const val SELECTED_CHANNEL_ID = "SELECTED_CHANNEL_ID"
}

// Kanaltyp "IPv64 App" (dieses Gerät), Token = SecNtfy-Gerätetoken
const val HC2_CHANNEL_TYPE_APP = "app"

// Kanaltypen, die per Code bestätigt werden müssen, falls die Kanaltypen nicht geladen werden konnten
val HC2_VERIFICATION_FALLBACK_TYPES: Set<String> = setOf("ipv64_mail", "ipv64_sms")

// Kanalliste zwischenspeichern (Rate-Limit ~5 Anfragen / 5 s); Benachrichtigungs-Tab, Kanalauswahl und Detailansicht teilen sich die Liste
object Hc2ChannelCache {
    private const val TTL_MS = 5 * 60 * 1000L
    private val loadMutex = Mutex()

    private var channels: List<Hc2Channel>? = null
    private var time = 0L

    // Wird bei clear() erhöht, damit ein laufender Abruf mit altem API-Key nichts mehr speichert
    private var generation = 0L

    // Liefert die gecachte Liste oder lädt sie (force = true lädt immer neu); wirft IPv64Exception
    suspend fun get(ctx: Context, force: Boolean = false): List<Hc2Channel> = loadMutex.withLock {
        // Zuerst den Client holen: bei geändertem API-Key leert IPv64Client.get() die Caches, bevor fresh() greift
        val client = IPv64Client.get(ctx)
        if (!force) fresh()?.let { return@withLock it }
        val gen = currentGeneration()
        val list = ipv64Retrying { client.hc2.channels.list() }
        store(list, gen)
        list
    }

    // Gecachte Liste ohne Netzwerkzugriff (null, wenn nicht geladen oder abgelaufen)
    @Synchronized
    fun peek(): List<Hc2Channel>? = fresh()

    // Nach jeder Änderung an Kanälen aufrufen; ein bereits laufender Abruf speichert seine (veraltete) Liste nicht mehr
    @Synchronized
    fun invalidate() {
        channels = null
        generation++
    }

    // Beim Account-Wechsel/Logout (aus IPv64Client.get)
    @Synchronized
    fun clear() {
        channels = null
        generation++
    }

    @Synchronized
    private fun fresh(): List<Hc2Channel>? {
        val list = channels ?: return null
        if (SystemClock.elapsedRealtime() - time > TTL_MS) {
            channels = null
            return null
        }
        return list
    }

    @Synchronized
    private fun currentGeneration(): Long = generation

    @Synchronized
    private fun store(list: List<Hc2Channel>, gen: Long) {
        if (gen != generation) return
        channels = list
        time = SystemClock.elapsedRealtime()
    }
}

// Kanaltypen (Formularfelder) ändern sich selten, daher längere Lebensdauer
object Hc2ChannelTypeCache {
    private const val TTL_MS = 30 * 60 * 1000L
    private val loadMutex = Mutex()

    private var types: List<Hc2ChannelType>? = null
    private var time = 0L
    private var generation = 0L

    // Liefert die gecachten Kanaltypen oder lädt sie; wirft IPv64Exception
    suspend fun get(ctx: Context): List<Hc2ChannelType> = loadMutex.withLock {
        // Zuerst den Client holen: bei geändertem API-Key leert IPv64Client.get() die Caches, bevor fresh() greift
        val client = IPv64Client.get(ctx)
        fresh()?.let { return@withLock it }
        val gen = currentGeneration()
        val list = ipv64Retrying { client.hc2.channelTypes() }
        store(list, gen)
        list
    }

    // Gecachte Kanaltypen ohne Netzwerkzugriff (null, wenn nicht geladen oder abgelaufen)
    @Synchronized
    fun peek(): List<Hc2ChannelType>? = fresh()

    @Synchronized
    fun clear() {
        types = null
        generation++
    }

    @Synchronized
    private fun fresh(): List<Hc2ChannelType>? {
        val list = types ?: return null
        if (SystemClock.elapsedRealtime() - time > TTL_MS) {
            types = null
            return null
        }
        return list
    }

    @Synchronized
    private fun currentGeneration(): Long = generation

    @Synchronized
    private fun store(list: List<Hc2ChannelType>, gen: Long) {
        if (gen != generation) return
        types = list
        time = SystemClock.elapsedRealtime()
    }
}

// Check-Liste nur kurz zwischenspeichern (Tab-Wechsel, Zurück aus der Detailansicht); laden muss der Aufrufer selbst
object Hc2CheckListCache {
    private const val TTL_MS = 20 * 1000L

    private var checks: List<Hc2Check>? = null
    private var time = 0L

    // Gecachte Liste oder null, wenn nicht vorhanden/abgelaufen
    @Synchronized
    fun get(): List<Hc2Check>? {
        val list = checks ?: return null
        if (SystemClock.elapsedRealtime() - time > TTL_MS) {
            checks = null
            return null
        }
        return list
    }

    @Synchronized
    fun put(list: List<Hc2Check>) {
        checks = list
        time = SystemClock.elapsedRealtime()
    }

    // Nach Anlegen/Ändern/Löschen/Pausieren eines Checks aufrufen
    @Synchronized
    fun invalidate() {
        checks = null
    }

    @Synchronized
    fun clear() {
        checks = null
    }
}

// Icon je Kanaltyp; unbekannte Typen bekommen ein neutrales Glocken-Icon
@DrawableRes
fun channelTypeIcon(type: String?): Int = when (type?.lowercase()) {
    "ipv64_mail", "smtp", "email", "mail" -> R.drawable.alternate_email_24px
    "ipv64_sms", "sms" -> R.drawable.sms_24px
    HC2_CHANNEL_TYPE_APP -> R.drawable.mobile_24px
    "telegram" -> R.drawable.telegram_24px
    "discord" -> R.drawable.discord_24px
    "ntfy", "secntfy" -> R.drawable.ntfy_24px
    "gotify" -> R.drawable.gotify_24px
    "pushover" -> R.drawable.pushover_24px
    "webhook" -> R.drawable.webhook_24px
    "slack", "teams", "matrix" -> R.drawable.extension_24px
    null, "" -> R.drawable.indeterminate_question_box_24px
    else -> R.drawable.notifications_unread_24px
}

// Anzeigename: Name, sonst Typname, sonst Typ bzw. ID
val Hc2Channel.displayName: String
    get() = name?.trim()?.takeIf { it.isNotEmpty() }
        ?: typeLabel?.trim()?.takeIf { it.isNotEmpty() }
        ?: type?.takeIf { it.isNotBlank() }
        ?: "Channel #${id ?: "?"}"

// Kanal muss noch per Code bestätigt werden; ohne geladene Kanaltypen gilt die Fallback-Liste (Mail/SMS)
fun Hc2Channel.isAwaitingVerification(types: List<Hc2ChannelType>?): Boolean {
    if (verified == true) return false
    val channelType = type ?: return false
    val needsVerification = types?.firstOrNull { it.type == channelType }?.needsVerification
        ?: (channelType in HC2_VERIFICATION_FALLBACK_TYPES)
    return needsVerification
}
