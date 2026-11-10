package de.rpicloud.ipv64net.helper

import android.os.SystemClock
import de.sr.ipv64.IPv64NetworkException
import kotlinx.coroutines.delay

/**
 * Fragt wiederholt einen Zustand ab, bis [done] erfüllt ist oder die Frist [maxMs] abgelaufen ist.
 *
 * - Wartet vor jeder Abfrage [intervalMs] (auch vor der ersten, weil direkt nach einer Änderung der alte Zustand
 *   zu erwarten ist).
 * - Frist nach Uhrzeit (SystemClock.elapsedRealtime), nicht nach Anzahl der Versuche.
 * - Jede Abfrage läuft über [ipv64Retrying] (einmaliger Retry bei 429).
 * - Genau ein [IPv64NetworkException] wird toleriert (Abfrage wird übersprungen); ein zweiter wird weitergeworfen.
 *   Alle anderen IPv64Exceptions werden direkt weitergeworfen.
 * - CancellationException wird nie gefangen (Screen verlassen → Polling endet sofort).
 *
 * @param onEach wird nach jeder erfolgreichen Abfrage mit dem neuen Wert aufgerufen (z. B. UI aktualisieren).
 * @return den ersten Wert, für den [done] true liefert, oder null, wenn die Frist abgelaufen ist.
 */
suspend fun <T> pollUntil(
    intervalMs: Long,
    maxMs: Long,
    onEach: (T) -> Unit = {},
    fetch: suspend () -> T,
    done: (T) -> Boolean
): T? {
    val deadline = SystemClock.elapsedRealtime() + maxMs
    var networkErrorTolerated = false
    while (true) {
        delay(intervalMs)
        val value: T
        try {
            value = ipv64Retrying { fetch() }
        } catch (e: IPv64NetworkException) {
            if (networkErrorTolerated) throw e
            networkErrorTolerated = true
            if (SystemClock.elapsedRealtime() >= deadline) return null
            continue
        }
        onEach(value)
        if (done(value)) return value
        if (SystemClock.elapsedRealtime() >= deadline) return null
    }
}
