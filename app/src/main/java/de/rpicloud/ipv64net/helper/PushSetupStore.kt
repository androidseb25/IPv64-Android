package de.rpicloud.ipv64net.helper

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.security.MessageDigest

// Merkt sich pro Account und Gerät, ob die Push-Einrichtung erledigt ist und welcher hc2-Kanal dieses Gerät ist.
// Nur lokal, kein Netzwerk. Die Datei ist von Cloud-Backup und Gerätetransfer ausgeschlossen
// (backup_rules.xml / data_extraction_rules.xml), weil sie gerätespezifisch ist.
//
// Bereich (Scope) der Einträge: stabile Account-ID (überlebt neu erzeugte API-Keys und weitere Keys desselben Accounts),
// solange sie noch nicht bekannt ist der Hash des API-Keys. Die Account-ID wird pro Key-Hash zwischengespeichert
// (rememberAccountId), damit shouldShow() ohne Netzwerk auskommt.
object PushSetupStore {
    const val PREFS_NAME = "HC2_PUSH_SETUP"

    private const val KEY_DONE = "done_"
    private const val KEY_CHANNEL = "channel_"
    private const val KEY_TOKEN_FP = "tokenfp_"

    // Account-ID pro Key-Hash: acct_<keyHash> = Long
    private const val KEY_ACCOUNT_ID = "acct_"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Erste 16 Hex-Zeichen von SHA-256(API-Key); der Key selbst taucht nie in einem Schlüssel auf. null ohne Login
    fun apiKeyHash(ctx: Context): String? {
        val apiKey = PreferencesManager.loadString(ctx.applicationContext, "APIKEY").trim()
        if (apiKey.isEmpty()) return null
        return sha256Hex(apiKey).take(16)
    }

    // Scope für eine Account-ID (gehasht, damit Schlüsselnamen einheitlich bleiben)
    private fun accountScope(accountId: Long): String = "a" + sha256Hex("account:$accountId").take(16)

    /**
     * Bereich der Einträge für den aktuellen Login: Account-Scope, falls die Account-ID zum API-Key bekannt ist,
     * sonst der Key-Hash. null ohne Login. (Name bleibt aus Kompatibilität mit dem Trigger in MainActivity.)
     */
    fun accountHash(ctx: Context): String? {
        val keyHash = apiKeyHash(ctx) ?: return null
        val p = prefs(ctx)
        return if (p.contains(KEY_ACCOUNT_ID + keyHash)) accountScope(p.getLong(KEY_ACCOUNT_ID + keyHash, 0L)) else keyHash
    }

    // True, wenn die Account-ID zum aktuellen API-Key bereits bekannt ist
    fun hasAccountId(ctx: Context): Boolean {
        val keyHash = apiKeyHash(ctx) ?: return false
        return prefs(ctx).contains(KEY_ACCOUNT_ID + keyHash)
    }

    /**
     * Account-ID zum API-Key mit Hash [keyHash] speichern (vor der Anfrage ermittelt, damit ein Key-Wechsel während
     * der Anfrage die ID nicht dem falschen Key zuordnet) und Einträge, die noch unter dem Key-Hash liegen, in den
     * Account-Scope übernehmen (vorhandene Werte im Account-Scope haben Vorrang).
     */
    fun rememberAccountId(ctx: Context, keyHash: String, accountId: Long) {
        val p = prefs(ctx)
        val scope = accountScope(accountId)
        p.edit(commit = true) {
            putLong(KEY_ACCOUNT_ID + keyHash, accountId)
            // Migration Key-Hash → Account-Scope
            if (p.getBoolean(KEY_DONE + keyHash, false)) putBoolean(KEY_DONE + scope, true)
            if (p.contains(KEY_CHANNEL + keyHash) && !p.contains(KEY_CHANNEL + scope)) {
                putLong(KEY_CHANNEL + scope, p.getLong(KEY_CHANNEL + keyHash, 0L))
                p.getString(KEY_TOKEN_FP + keyHash, null)?.let { putString(KEY_TOKEN_FP + scope, it) }
            }
            remove(KEY_DONE + keyHash)
            remove(KEY_CHANNEL + keyHash)
            remove(KEY_TOKEN_FP + keyHash)
        }
    }

    // SHA-256 (Hex) eines Gerätetokens, um Tokenwechsel zu erkennen, ohne das Token zu speichern
    fun fingerprint(token: String): String = sha256Hex(token.trim())

    // Einrichtung anzeigen: eingeloggt, noch nicht erledigt/übersprungen, kein Gerätekanal bekannt, SecNtfy konfiguriert
    fun shouldShow(ctx: Context): Boolean {
        val h = accountHash(ctx) ?: return false
        val p = prefs(ctx)
        if (p.getBoolean(KEY_DONE + h, false)) return false
        if (p.contains(KEY_CHANNEL + h)) return false
        return isSecNtfyConfigured(ctx)
    }

    // Vor popBackStack() aufrufen (commit, damit der Trigger in MainActivity nicht erneut auslöst)
    fun markDone(ctx: Context) {
        val h = accountHash(ctx) ?: return
        prefs(ctx).edit(commit = true) { putBoolean(KEY_DONE + h, true) }
    }

    // ID des hc2-Kanals "dieses Gerät" für den aktuellen Account, null wenn unbekannt
    fun channelId(ctx: Context): Long? {
        val h = accountHash(ctx) ?: return null
        val p = prefs(ctx)
        return if (p.contains(KEY_CHANNEL + h)) p.getLong(KEY_CHANNEL + h, 0L) else null
    }

    // Gerätekanal und Fingerprint des dabei verwendeten Tokens speichern (fingerprint = fingerprint(token))
    fun setChannel(ctx: Context, id: Long, tokenFingerprint: String?) {
        val h = accountHash(ctx) ?: return
        prefs(ctx).edit(commit = true) {
            putLong(KEY_CHANNEL + h, id)
            if (tokenFingerprint != null) putString(KEY_TOKEN_FP + h, tokenFingerprint)
            else remove(KEY_TOKEN_FP + h)
        }
    }

    // Gerätekanal vergessen (z. B. auf dem Server gelöscht)
    fun clearChannel(ctx: Context) {
        val h = accountHash(ctx) ?: return
        prefs(ctx).edit(commit = true) {
            remove(KEY_CHANNEL + h)
            remove(KEY_TOKEN_FP + h)
        }
    }

    // Fingerprint des Tokens, mit dem der Gerätekanal zuletzt angelegt/aktualisiert wurde
    fun tokenFingerprint(ctx: Context): String? {
        val h = accountHash(ctx) ?: return null
        return prefs(ctx).getString(KEY_TOKEN_FP + h, null)
    }

    // SecNtfy-Zugangsdaten vorhanden (ohne sie gibt es kein Gerätetoken)
    fun isSecNtfyConfigured(ctx: Context): Boolean {
        val appContext = ctx.applicationContext
        return PreferencesManager.loadString(appContext, "SECNTFY_API_URL").isNotBlank() &&
            PreferencesManager.loadString(appContext, "SECNTFY_APP_KEY").isNotBlank()
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
