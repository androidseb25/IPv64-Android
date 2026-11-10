package de.rpicloud.ipv64net.helper

import android.content.Context
import androidx.annotation.DrawableRes
import de.rpicloud.ipv64net.R
import de.sr.ipv64.model.Hc2Channel
import de.sr.ipv64.model.Hc2ChannelType
import de.sr.ipv64.model.Hc2Check

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

// Kanalliste zwischenspeichern (Rate-Limit ~5 Anfragen / 5 s); Benachrichtigungs-Tab, Kanalauswahl und Detailansicht teilen sich die Liste.
// Hülle um [TimedCache] (meldet sich bei CacheRegistry an, Generationszähler gegen veraltete Abrufe)
object Hc2ChannelCache {
    private val cache = TimedCache<List<Hc2Channel>>(5 * 60 * 1000L)

    // Liefert die gecachte Liste oder lädt sie (force = true lädt immer neu); wirft IPv64Exception
    suspend fun get(ctx: Context, force: Boolean = false): List<Hc2Channel> =
        cache.get(ctx, force) { it.hc2.channels.list() }

    // Gecachte Liste ohne Netzwerkzugriff (null, wenn nicht geladen oder abgelaufen)
    fun peek(): List<Hc2Channel>? = cache.peek()

    // Nach jeder Änderung an Kanälen aufrufen; ein bereits laufender Abruf speichert seine (veraltete) Liste nicht mehr
    fun invalidate() = cache.invalidate()
}

// Kanaltypen (Formularfelder) ändern sich selten, daher längere Lebensdauer
object Hc2ChannelTypeCache {
    private val cache = TimedCache<List<Hc2ChannelType>>(30 * 60 * 1000L)

    // Liefert die gecachten Kanaltypen oder lädt sie; wirft IPv64Exception
    suspend fun get(ctx: Context): List<Hc2ChannelType> = cache.get(ctx) { it.hc2.channelTypes() }

    // Gecachte Kanaltypen ohne Netzwerkzugriff (null, wenn nicht geladen oder abgelaufen)
    fun peek(): List<Hc2ChannelType>? = cache.peek()
}

// Check-Liste nur kurz zwischenspeichern (Tab-Wechsel, Zurück aus der Detailansicht); laden über [loadHc2Checks]
object Hc2CheckListCache {
    private val cache = TimedCache<List<Hc2Check>>(20 * 1000L)

    // Gecachte Liste oder null, wenn nicht vorhanden/abgelaufen
    fun get(): List<Hc2Check>? = cache.peek()

    // Gecachte Liste oder neu geladen (force = true lädt immer neu); wirft IPv64Exception
    suspend fun get(ctx: Context, force: Boolean = false): List<Hc2Check> =
        cache.get(ctx, force) { it.hc2.checks.list() }

    // Nach Anlegen/Ändern/Löschen/Pausieren eines Checks aufrufen; ein laufender Abruf speichert danach nicht mehr
    fun invalidate() = cache.invalidate()
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
