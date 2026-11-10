package de.rpicloud.ipv64net.models

import android.content.Context
import de.rpicloud.ipv64net.helper.CacheRegistry
import de.rpicloud.ipv64net.helper.PreferencesManager
import de.rpicloud.ipv64net.main.views.network.LocalNodeStore
import java.util.UUID

/** Ergebnis von [User.remove]. */
sealed class UserRemovalResult {
    /** Kein Eintrag mit dieser ID vorhanden. */
    data object NotFound : UserRemovalResult()

    /** Ein nicht aktiver Eintrag wurde entfernt; der aktive Account bleibt unverändert. */
    data object Removed : UserRemovalResult()

    /** Der aktive Eintrag wurde entfernt; die App nutzt jetzt [user]. */
    data class SwitchedTo(val user: User) : UserRemovalResult()

    /** Der letzte (aktive) Eintrag wurde entfernt; APIKEY ist geleert, die App muss zum Login. */
    data object LoggedOut : UserRemovalResult()
}

data class User(
    var id: UUID = UUID.randomUUID(),
    var Username: String = "",
    var ApiKey: String,
    var Information: String
) {
    companion object {
        private lateinit var appContext: Context

        fun init(context: Context) {
            appContext = context.applicationContext
            // Nur anlegen, wenn die Liste leer ist UND ein API-Key existiert.
            // Nach dem Entfernen des letzten Eintrags ist APIKEY leer, daher wird nichts neu angelegt.
            val apiKey = PreferencesManager.loadString(appContext, "APIKEY")
            if (apiKey.isNotEmpty() && list.isEmpty()) {
                val user = User(Username = "Default User", ApiKey = apiKey, Information = "")
                val list = mutableListOf<User>()
                list.add(user)
                saveList(list)
            }
        }

        val empty = User(Username = "", ApiKey = "", Information = "")

        val current: User?
            get() {
                if (list.isEmpty()) {
                    return null
                }

                val user = list.firstOrNull { it.ApiKey ==  PreferencesManager.loadString(appContext, "APIKEY") }

                if (user != null && user.Information.isEmpty()) {
                    user.Information = "No Information"
                }
                return user
            }

        val list: MutableList<User>
            get() {
                return try {
                    val data = PreferencesManager.loadList<User>(ctx = appContext, key = "APP_USERS")
                    val (userList, repaired) = withUniqueIds(data)
                    if (repaired) {
                        saveList(userList)
                    }
                    userList
                } catch (e: Exception) {
                    // Ungültige/beschädigte Daten (z. B. JsonSyntaxException aus loadList): Rohdaten sichern, bevor ein
                    // späteres saveList (init/save) sie überschreibt, dann leere Liste zurückgeben
                    backupRawList()
                    mutableListOf()
                }
            }

        // Nur die erste Sicherung behalten, damit ein späterer Fehler die ursprünglichen Daten nicht ersetzt
        private fun backupRawList() = runCatching {
            val raw = PreferencesManager.loadString(appContext, "APP_USERS")
            if (raw.isNotBlank() && PreferencesManager.loadString(appContext, "APP_USERS_BACKUP").isEmpty()) {
                PreferencesManager.saveString(appContext, "APP_USERS_BACKUP", raw)
            }
        }

        fun saveList(list: MutableList<User>) {
            PreferencesManager.saveList(ctx = appContext, key = "APP_USERS", list)
        }

        /**
         * Entfernt genau den Eintrag mit [id] aus der lokalen Account-Liste (nur auf diesem Gerät,
         * es wird keine Server-API aufgerufen).
         *
         * War der Eintrag der aktive Account (APIKEY), wird auf den ersten verbleibenden Eintrag gewechselt und
         * alle Caches werden geleert. Bleibt kein Eintrag übrig, wird APIKEY geleert ([UserRemovalResult.LoggedOut]);
         * den Wechsel zum Login übernimmt die UI.
         */
        fun remove(id: UUID): UserRemovalResult {
            val activeKey = PreferencesManager.loadString(appContext, "APIKEY")
            val (remaining, newKey, result) = removalDecision(list, activeKey, id)
            if (result == UserRemovalResult.NotFound) return result
            saveList(remaining.toMutableList())
            // Lokale Node-Zugänge dieses Accounts mit entfernen
            LocalNodeStore.removeOwner(appContext, id.toString())

            if (result is UserRemovalResult.SwitchedTo || result == UserRemovalResult.LoggedOut) {
                PreferencesManager.saveString(appContext, "APIKEY", newKey)
                // Zwischengespeicherte Daten des bisherigen Accounts verwerfen (wie beim Account-Wechsel/Logout)
                CacheRegistry.clearAll()
            }
            return result
        }
    }

    fun save() {
        val userList = list
        userList.add(this)
        saveList(userList)
    }

    fun update() {
        val userList = list
        val index = userList.indexOfFirst { it.id == this.id }
        if (index != -1) {
            userList[index] = this
            saveList(userList)
        }
    }
}

/**
 * Reine Entscheidung für [User.remove] (ohne Prefs, testbar): neue Liste, neuer APIKEY und Ergebnis.
 * Wird der aktive Eintrag entfernt, wird auf den ersten verbleibenden gewechselt; ohne Rest ist der Key leer.
 */
internal fun removalDecision(list: List<User>, activeKey: String, id: UUID): Triple<List<User>, String, UserRemovalResult> {
    val removed = list.firstOrNull { it.id == id } ?: return Triple(list, activeKey, UserRemovalResult.NotFound)
    val remaining = list.filter { it.id != id }
    // Nicht aktiv, oder derselbe API-Key steckt noch in einem anderen Eintrag: nichts umschalten
    if (activeKey.isEmpty() || removed.ApiKey != activeKey || remaining.any { it.ApiKey == activeKey }) {
        return Triple(remaining, activeKey, UserRemovalResult.Removed)
    }
    val next = remaining.firstOrNull()
    val result = if (next != null) UserRemovalResult.SwitchedTo(next) else UserRemovalResult.LoggedOut
    return Triple(remaining, next?.ApiKey ?: "", result)
}

/**
 * Fehlende oder doppelte IDs (z. B. durch das gemeinsam genutzte User.empty beim Login) durch neue ersetzen,
 * damit remove(id) genau einen Eintrag trifft und die Liste stabile Keys hat. True, wenn etwas repariert wurde.
 */
internal fun withUniqueIds(list: List<User>): Pair<MutableList<User>, Boolean> {
    var repaired = false
    val seenIds = mutableSetOf<UUID>()
    val result = list.map { u ->
        // id kann trotz Non-Null-Typ null sein, wenn Gson alte Daten ohne id liest
        if (u.id == null || !seenIds.add(u.id)) {
            repaired = true
            u.copy(id = UUID.randomUUID()).also { seenIds.add(it.id) }
        } else u
    }.toMutableList()
    return result to repaired
}
