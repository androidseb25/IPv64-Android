package de.rpicloud.ipv64net.helper

import android.content.Context
import android.os.SystemClock
import de.rpicloud.ipv64net.models.RequestTyp
import de.sr.ipv64.IPv64
import de.sr.ipv64.IPv64ApiException
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.IPv64NetworkException
import de.sr.ipv64.model.DomainDetail

object IPv64Client {
    private var cachedKey: String? = null
    private var cachedClient: IPv64? = null

    // Ein Client pro API-Key; beim Account-Wechsel oder Logout ändert sich APIKEY und der Client wird neu erstellt
    @Synchronized
    fun get(ctx: Context): IPv64 {
        val apiKey = PreferencesManager.loadString(ctx, "APIKEY").trim().ifEmpty { null }
        cachedClient?.let { if (cachedKey == apiKey) return it }
        DomainCache.clear()
        return IPv64(apiKey = apiKey).also {
            cachedKey = apiKey
            cachedClient = it
        }
    }
}

// Domain-Details kurz zwischenspeichern, damit Liste und Detailansicht das Rate-Limit nicht ausreizen
object DomainCache {
    private const val TTL_MS = 5 * 60 * 1000L
    private class Entry(val detail: DomainDetail, val time: Long)

    private val entries = mutableMapOf<String, Entry>()

    @Synchronized
    fun get(name: String): DomainDetail? {
        val entry = entries[name] ?: return null
        if (SystemClock.elapsedRealtime() - entry.time > TTL_MS) {
            entries.remove(name)
            return null
        }
        return entry.detail
    }

    @Synchronized
    fun put(name: String, detail: DomainDetail) {
        entries[name] = Entry(detail, SystemClock.elapsedRealtime())
    }

    @Synchronized
    fun invalidate(name: String) {
        entries.remove(name)
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }
}

// Ungültige Header-Werte (z. B. Zeilenumbruch im API-Key) lösen in OkHttp eine IllegalArgumentException aus
suspend fun <T> ipv64Request(block: suspend () -> T): T {
    try {
        return block()
    } catch (e: IllegalArgumentException) {
        throw IPv64NetworkException(e.message ?: "Invalid request", e)
    }
}

enum class IPv64Context { Create, Update, Other }

// Entweder requestType (RequestDialogs) oder title/text (ErrorDialog) wird angezeigt
data class IPv64Error(
    val requestType: RequestTyp? = null,
    val title: String = "Loading error",
    val text: String = ""
)

fun IPv64Exception.toError(context: IPv64Context = IPv64Context.Other): IPv64Error {
    if (this !is IPv64ApiException) {
        // Netzwerk- oder Serialisierungsfehler wie bisher als Verbindungsfehler behandeln
        return IPv64Error(requestType = RequestTyp.WebsiteRequestFailed)
    }
    val info = "${code.orEmpty()} ${detail.orEmpty()}".lowercase()
    return when {
        isUnauthorized -> IPv64Error(RequestTyp.UnAuthorized)
        isRateLimited || retryAfterSeconds != null ->
            IPv64Error(if (context == IPv64Context.Update) RequestTyp.UpdateCoolDown else RequestTyp.TooManyRequests)

        context == IPv64Context.Create && (isForbidden || isConflict) -> IPv64Error(
            when {
                info.contains("limit") -> RequestTyp.DomainLimitReached
                isConflict || info.contains("not available") || info.contains("taken") || info.contains("exists") ->
                    RequestTyp.DomainNotAvailable

                else -> RequestTyp.DomainRulesNotCreated
            }
        )

        status >= 500 -> IPv64Error(RequestTyp.WebsiteRequestFailed)
        else -> IPv64Error(text = detail ?: problem?.title ?: message.orEmpty())
    }
}
