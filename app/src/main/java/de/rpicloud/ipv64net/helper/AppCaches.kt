package de.rpicloud.ipv64net.helper

import android.content.Context
import android.os.SystemClock
import de.sr.ipv64.IPv64
import de.sr.ipv64.model.Domain
import de.sr.ipv64.model.Hc2Account
import de.sr.ipv64.model.Hc2Check
import de.sr.ipv64.model.Me
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ein Cache, der beim Account-Wechsel/Logout geleert werden muss.
 * Eigene Caches (z. B. pro Paket) implementieren dies und melden sich mit [CacheRegistry.register] an;
 * [TimedCache] erledigt das automatisch.
 */
interface ClearableCache {
    /** Inhalt verwerfen; ein gerade laufender Abruf darf sein Ergebnis danach nicht mehr speichern. */
    fun clear()
}

/**
 * Zentrale Liste aller Caches, die beim API-Key-Wechsel geleert werden (TimedCache, DomainCache und die
 * hc2-Caches). [IPv64Client.get] ruft [clearAll] auf, sobald sich der API-Key ändert; Logout und Account-Entfernen
 * rufen es ebenfalls auf. Ein weiterer Aufruf einzelner clear()-Methoden ist nicht nötig.
 */
object CacheRegistry {
    private val caches = mutableListOf<ClearableCache>()

    /** Cache anmelden (idempotent). Aufruf typischerweise im init-Block bzw. bei der Erzeugung des Caches. */
    @Synchronized
    fun register(cache: ClearableCache) {
        if (caches.none { it === cache }) caches += cache
    }

    /** Alle angemeldeten Caches leeren (Account-Wechsel, Logout). */
    fun clearAll() {
        val snapshot = synchronized(this) { caches.toList() }
        snapshot.forEach { it.clear() }
    }
}

/**
 * Wert mit Ablaufzeit. Meldet sich selbst bei [CacheRegistry] an.
 *
 * - [get] liefert den gecachten Wert oder lädt ihn (ein Ladevorgang gleichzeitig, Mutex). Zuerst wird
 *   [IPv64Client.get] aufgerufen, damit ein API-Key-Wechsel die Caches leert, bevor der alte Wert gelesen wird.
 *   Der Abruf läuft über [ipv64Retrying]; Fehler (IPv64Exception) werden an den Aufrufer weitergereicht.
 * - Ein Generationszähler verhindert, dass ein Abruf, der vor [invalidate]/[clear] gestartet wurde, sein
 *   (veraltetes) Ergebnis speichert.
 *
 * Beispiel: `val OfflinePageCache = TimedCache<List<OfflinePage>>(60_000L)` und
 * `OfflinePageCache.get(ctx, force) { it.offlinePages.list() }`.
 * Caches als Top-Level-`val` oder in einem `object` anlegen (eine Instanz pro Prozess).
 *
 * @param ttlMs Lebensdauer eines Werts in Millisekunden.
 */
class TimedCache<T : Any>(private val ttlMs: Long) : ClearableCache {
    private val loadMutex = Mutex()
    private val lock = Any()

    private var value: T? = null
    private var time = 0L
    private var generation = 0L

    init {
        CacheRegistry.register(this)
    }

    /**
     * Gecachter Wert oder neu geladen.
     * @param force true lädt immer neu (z. B. Pull-to-Refresh).
     * @param load Abruf mit dem aktuellen Client; wird in [ipv64Retrying] ausgeführt.
     * @throws de.sr.ipv64.IPv64Exception bei Fehlern des Abrufs.
     */
    suspend fun get(ctx: Context, force: Boolean = false, load: suspend (IPv64) -> T): T = loadMutex.withLock {
        // Zuerst den Client holen: bei geändertem API-Key leert IPv64Client.get() die Caches, bevor fresh() greift
        val client = IPv64Client.get(ctx)
        if (!force) fresh()?.let { return@withLock it }
        val gen = synchronized(lock) { generation }
        val loaded = ipv64Retrying { load(client) }
        store(loaded, gen)
        loaded
    }

    /**
     * Gecachter Wert ohne Netzwerkzugriff; null, wenn nicht geladen oder abgelaufen.
     * Mit [ctx] wird vorher [IPv64Client.get] aufgerufen (leert die Caches nach einem Account-Wechsel).
     */
    fun peek(ctx: Context? = null): T? {
        if (ctx != null) IPv64Client.get(ctx)
        return fresh()
    }

    /**
     * Wert direkt setzen, z. B. mit dem Objekt, das eine Änderung zurückgeliefert hat (spart einen Abruf).
     * Erhöht die Generation, damit ein parallel laufender älterer Abruf den neuen Wert nicht überschreibt.
     */
    fun put(newValue: T) {
        synchronized(lock) {
            generation++
            value = newValue
            time = SystemClock.elapsedRealtime()
        }
    }

    /** Nach jeder Änderung im Bereich aufrufen; der nächste [get] lädt neu. */
    fun invalidate() {
        synchronized(lock) {
            value = null
            generation++
        }
    }

    /** Beim Account-Wechsel/Logout (über [CacheRegistry.clearAll]). */
    override fun clear() {
        invalidate()
    }

    private fun fresh(): T? = synchronized(lock) {
        val current = value ?: return@synchronized null
        if (SystemClock.elapsedRealtime() - time > ttlMs) {
            value = null
            return@synchronized null
        }
        current
    }

    private fun store(newValue: T, gen: Long) {
        synchronized(lock) {
            if (gen != generation) return
            value = newValue
            time = SystemClock.elapsedRealtime()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Gemeinsame Caches (von mehreren Bereichen genutzt)
// ---------------------------------------------------------------------------------------------

/** Account (`account.me()`): Plan, Nutzung/Limits (Feature-Gates), aktueller API-Key. 60 s. */
val AccountCache: TimedCache<Me> = TimedCache(60_000L)

/** Healthchecks-2.0-Account (`hc2.account()`): Plan, Limits, Standorte, Node-Zähler. 5 min. */
val Hc2AccountCache: TimedCache<Hc2Account> = TimedCache(5 * 60_000L)

/** Domainliste (`domains.list()`), z. B. für Domain-/Record-Auswahl. 60 s. Nach Änderungen invalidate()/put(). */
val DomainListCache: TimedCache<List<Domain>> = TimedCache(60_000L)

/** Account laden (gecacht, [AccountCache]); wirft IPv64Exception. */
suspend fun loadMe(ctx: Context, force: Boolean = false): Me =
    AccountCache.get(ctx, force) { it.account.me() }

/** hc2-Account laden (gecacht, [Hc2AccountCache]); wirft IPv64Exception. */
suspend fun loadHc2Account(ctx: Context, force: Boolean = false): Hc2Account =
    Hc2AccountCache.get(ctx, force) { it.hc2.account() }

/** Domainliste laden (gecacht, [DomainListCache]); wirft IPv64Exception. */
suspend fun loadDomains(ctx: Context, force: Boolean = false): List<Domain> =
    DomainListCache.get(ctx, force) { it.domains.list() }

/**
 * hc2-Checkliste laden; nutzt [Hc2CheckListCache] (20 s), damit Healthcheck-Tab, Pickers und
 * Monitoring-Hub dieselbe Liste teilen. force = true lädt immer neu. Wirft IPv64Exception.
 * Ein Abruf, während dessen der Account gewechselt oder die Liste invalidiert wurde, wird zurückgegeben,
 * aber nicht gespeichert.
 */
suspend fun loadHc2Checks(ctx: Context, force: Boolean = false): List<Hc2Check> =
    Hc2CheckListCache.get(ctx, force)
