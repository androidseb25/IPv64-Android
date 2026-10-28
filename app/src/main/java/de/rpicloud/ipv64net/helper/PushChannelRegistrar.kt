package de.rpicloud.ipv64net.helper

import android.content.Context
import android.os.Build
import android.provider.Settings
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.Hc2Channel
import de.sr.ipv64.model.Hc2ChannelInput
import de.sr.ipv64.model.Hc2ChannelType
import de.sr.ipv64.model.Hc2ChannelTypeField
import de.sr.ipv64.model.Hc2ChannelUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/**
 * Fehler der Geräteregistrierung, die nicht von der API stammen (kein Token, Typ fehlt, unbekanntes Format).
 * [title] und [message] sind für den Fehlerdialog gedacht und enthalten nie Token oder Konfiguration.
 */
class PushRegistrationException(val title: String, message: String) : Exception(message)

// Vom Benutzer gewählte Einstellungen für den Gerätekanal
class DeviceChannelSettings(
    val name: String,
    val default: Boolean,
    val notifyDown: Boolean,
    val notifyUp: Boolean,
    val lang: String?
)

// Ergebnis der Registrierung (bewusst keine data classes: toString() darf keine Kanal-Konfiguration ausgeben)
sealed class DeviceRegistration {
    // Kanal angelegt oder bestehender Kanal übernommen ([reused]); Einstellungen wurden angewendet
    class Registered(val channel: Hc2Channel, val reused: Boolean) : DeviceRegistration()

    // Es gibt App-Kanäle, aber keiner ist sicher dieses Gerät: Benutzer fragen (bevorzugter Kandidat zuerst)
    class ExistingCandidates(val candidates: List<Hc2Channel>) : DeviceRegistration()
}

/**
 * Registriert dieses Gerät als hc2-Kanal vom Typ "app". Das Gerätetoken ist der SecNtfy-Ntfy-Token.
 * Token und Kanal-Konfiguration sind geheim: nie loggen oder ausgeben (auch nicht die Bibliotheksobjekte,
 * deren toString() die Konfiguration enthalten kann).
 */
object PushChannelRegistrar {
    // Bevorzugte Schlüssel des Token-Felds (Groß-/Kleinschreibung egal)
    private val PREFERRED_TOKEN_KEYS = listOf("device_token", "devicetoken", "token", "ntfy_token")

    // Fallback, wenn der Kanaltyp kein erkennbares Token-Feld beschreibt
    const val FALLBACK_TOKEN_KEY = "device_token"

    private val TEXT_KINDS = setOf("text", "password", "textarea")

    // Kanaltyp "app" aus der Liste der Kanaltypen
    fun appType(types: List<Hc2ChannelType>?): Hc2ChannelType? =
        types?.firstOrNull { it.type == HC2_CHANNEL_TYPE_APP }

    /**
     * Stabile Account-ID zum aktuellen API-Key ermitteln (einmal pro Key, account.me()) und im PushSetupStore merken,
     * damit Einträge einen neu erzeugten oder zweiten API-Key desselben Accounts überleben. Fehler werden ignoriert
     * (dann gilt weiter der Key-Hash).
     */
    suspend fun ensureAccountId(ctx: Context) {
        val appContext = ctx.applicationContext
        if (PushSetupStore.hasAccountId(appContext)) return
        // Key-Hash und Client vor der Anfrage festhalten (Key-Wechsel während der Anfrage)
        val keyHash = PushSetupStore.apiKeyHash(appContext) ?: return
        val client = IPv64Client.get(appContext)
        try {
            val id = ipv64Retrying { client.account.me() }.id ?: return
            PushSetupStore.rememberAccountId(appContext, keyHash, id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IPv64Exception) {
            // Später erneut versuchen
        }
    }

    /**
     * Schlüssel des Token-Felds im Typ "app":
     * 1) bekannter Schlüssel, 2) erster Schlüssel mit "token", 3) einziges Pflichtfeld vom Typ text/password/textarea, 4) null.
     */
    fun resolveAppTokenKey(appType: Hc2ChannelType?): String? {
        val fields = appType?.fields.orEmpty().filter { !it.key.isNullOrBlank() }
        for (preferred in PREFERRED_TOKEN_KEYS) {
            fields.firstOrNull { it.key.equals(preferred, ignoreCase = true) }?.let { return it.key }
        }
        fields.firstOrNull { it.key!!.contains("token", ignoreCase = true) }?.let { return it.key }
        return fields
            .filter { it.required == true && (it.kind?.trim()?.lowercase()?.ifEmpty { null } ?: "text") in TEXT_KINDS }
            .singleOrNull()?.key
    }

    // Token-Schlüssel für einen bestehenden Kanal: aus dem Typ, sonst aus secretsSet/config, sonst Fallback
    fun tokenKeyFor(channel: Hc2Channel?, appType: Hc2ChannelType?): String =
        resolveAppTokenKey(appType)
            ?: channel?.secretsSet?.firstOrNull { it.contains("token", ignoreCase = true) }
            ?: channel?.config?.keys?.firstOrNull { it.contains("token", ignoreCase = true) }
            ?: FALLBACK_TOKEN_KEY

    /**
     * Kanal "dieses Gerät" in der Liste: zuerst über die gespeicherte ID (Typ app), sonst über den Token-Fingerprint
     * eines nicht geheimen Konfigurationswerts (zuerst unter [key], dann beliebiger Wert). null, wenn nicht gefunden.
     */
    fun findThisDeviceChannel(channels: List<Hc2Channel>, storedId: Long?, key: String?, token: String?): Hc2Channel? {
        val apps = channels.filter { it.type == HC2_CHANNEL_TYPE_APP && it.id != null }
        if (storedId != null) apps.firstOrNull { it.id == storedId }?.let { return it }
        if (token.isNullOrBlank()) return null
        return apps.firstOrNull { configHoldsToken(it, key, token) }
    }

    /**
     * Erkennt dieses Gerät ohne Netzwerkzugriff und merkt sich einen per Token gefundenen Kanal im [PushSetupStore].
     */
    fun detectThisDevice(ctx: Context, channels: List<Hc2Channel>, types: List<Hc2ChannelType>?): Hc2Channel? {
        val appContext = ctx.applicationContext
        val storedId = PushSetupStore.channelId(appContext)
        val token = SecNtfyHelper.storedNtfyToken(appContext)
        val found = findThisDeviceChannel(channels, storedId, resolveAppTokenKey(appType(types)), token)
        val foundId = found?.id
        if (foundId != null && foundId != storedId) {
            // Über den Token erkannt: Kanal und Fingerprint des passenden Tokens speichern
            PushSetupStore.setChannel(appContext, foundId, token?.let { PushSetupStore.fingerprint(it) })
        }
        return found
    }

    /**
     * Registriert dieses Gerät.
     * - [adoptChannelId]: diesen bestehenden App-Kanal übernehmen (Token per PATCH setzen, ID speichern).
     * - [createNew]: auch dann neu anlegen, wenn es nicht zuordenbare App-Kanäle gibt.
     * Ohne beides liefert die Funktion [DeviceRegistration.ExistingCandidates], wenn App-Kanäle existieren, von denen
     * keiner als dieses Gerät erkannt wird (verhindert doppelte Kanäle und damit doppelte Pushs).
     * Bestehende/übernommene Kanäle erhalten Name, Default und Benachrichtigungs-Schalter aus [settings].
     * Wirft [PushRegistrationException] für lokale Fehler und IPv64Exception für API-Fehler.
     */
    suspend fun registerThisDevice(
        ctx: Context,
        settings: DeviceChannelSettings,
        adoptChannelId: Long? = null,
        createNew: Boolean = false
    ): DeviceRegistration {
        val appContext = ctx.applicationContext

        // 1. Gerätetoken (SecNtfy)
        val token = SecNtfyHelper.ntfyToken(appContext)?.trim()?.ifEmpty { null }
            ?: throw PushRegistrationException(
                "Device token unavailable",
                "This device could not be registered for push notifications (Firebase or the push service is not " +
                        "reachable). Please check your internet connection and try again later."
            )
        val fp = PushSetupStore.fingerprint(token)

        // 2. Stabile Account-ID, bevor gespeicherte Kanal-IDs gelesen/geschrieben werden
        ensureAccountId(appContext)

        // 3. Kanaltyp "app"
        val app = appType(Hc2ChannelTypeCache.get(appContext))
            ?: throw PushRegistrationException(
                "Not supported",
                "Push notifications to the IPv64 app are not supported by the server yet. You can skip this step."
            )

        // 4. Token-Schlüssel (Fallback merken)
        val resolvedKey = resolveAppTokenKey(app)
        val key = resolvedKey ?: FALLBACK_TOKEN_KEY
        val client = IPv64Client.get(appContext)

        // 5. Bestehenden Kanal suchen (frische Liste, um Duplikate sicher zu vermeiden)
        val channels = Hc2ChannelCache.get(appContext, force = true)
        val apps = channels.filter { it.type == HC2_CHANNEL_TYPE_APP && it.id != null }
        val storedId = PushSetupStore.channelId(appContext)

        if (adoptChannelId != null) {
            val adopt = apps.firstOrNull { it.id == adoptChannelId }
                ?: throw PushRegistrationException("Channel not found", "The selected channel no longer exists.")
            // Token des übernommenen Kanals ist unbekannt (geheim): immer setzen
            return DeviceRegistration.Registered(applyToExisting(appContext, adopt, settings, key, token, true), reused = true)
        }

        val existing = findThisDeviceChannel(channels, storedId, key, token)
        val existingId = existing?.id
        if (existing != null && existingId != null) {
            val knownFp = if (existingId == storedId) PushSetupStore.tokenFingerprint(appContext) else null
            val tokenOutdated = !configHoldsToken(existing, key, token) && knownFp != fp
            return DeviceRegistration.Registered(
                applyToExisting(appContext, existing, settings, key, token, tokenOutdated),
                reused = true
            )
        }

        if (!createNew && apps.isNotEmpty()) {
            // Bevorzugt: Kanal mit eingegebenem Namen oder Standard-Gerätenamen
            val preferredNames = setOf(settings.name.trim().lowercase(), defaultDeviceName(appContext).trim().lowercase())
            val sorted = apps.sortedByDescending { it.name?.trim()?.lowercase() in preferredNames }
            return DeviceRegistration.ExistingCandidates(sorted)
        }

        // 6. Neu anlegen: Token plus Standardwerte weiterer Pflichtfelder; ohne Standardwert nicht raten
        val config = LinkedHashMap<String, JsonElement>()
        config[key] = JsonPrimitive(token)
        app.fields.forEach { field ->
            val fieldKey = field.key?.takeIf { it.isNotBlank() } ?: return@forEach
            if (fieldKey.equals(key, ignoreCase = true) || field.required != true) return@forEach
            val defaultValue = field.default
                ?: throw PushRegistrationException(
                    "Unknown app channel format",
                    "The server requires the setting \"${field.label ?: fieldKey}\" for app channels. " +
                            "Please add this device at ipv64.net."
                )
            config[fieldKey] = field.toConfigValue(defaultValue)
        }
        val created = ipv64Retrying {
            client.hc2.channels.create(
                Hc2ChannelInput(
                    name = settings.name,
                    type = HC2_CHANNEL_TYPE_APP,
                    config = JsonObject(config),
                    enabled = true,
                    default = settings.default,
                    lang = settings.lang,
                    notifyDown = settings.notifyDown,
                    notifyUp = settings.notifyUp
                )
            )
        }
        Hc2ChannelCache.invalidate()
        val createdId = created.id
            ?: throw PushRegistrationException(
                "Unknown app channel format",
                "The server did not return the new channel. Please check the channel list before trying again."
            )
        // Sofort merken, damit ein späterer Fehler keinen zweiten Kanal erzeugt
        PushSetupStore.setChannel(appContext, createdId, fp)

        // 7. Prüfen, ob der Server den Token übernommen hat. Eine verkürzte Antwort ist kein Beweis:
        //    erst per get() nachsehen; löschen nur, wenn der Schlüssel dort fehlt UND nur geraten war (Fallback).
        if (!holdsKey(created, key)) {
            val reloaded = try {
                ipv64Retrying { client.hc2.channels.get(createdId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IPv64Exception) {
                // Nicht prüfbar: Kanal behalten (ID ist gespeichert), nichts löschen
                return DeviceRegistration.Registered(created, reused = false)
            }
            if (!holdsKey(reloaded, key) && resolvedKey == null) {
                PushSetupStore.clearChannel(appContext)
                // Aufräumen auch bei Abbruch abschließen, damit kein unbrauchbarer Kanal übrig bleibt
                withContext(NonCancellable) {
                    try {
                        ipv64Retrying { client.hc2.channels.delete(createdId) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IPv64Exception) {
                        // Löschen fehlgeschlagen: Kanal bleibt bestehen und kann in der Liste gelöscht werden
                    }
                }
                throw PushRegistrationException(
                    "Unknown app channel format",
                    "The server describes app channels in a format this app version does not know. " +
                            "Please update the app or add this device at ipv64.net."
                )
            }
            return DeviceRegistration.Registered(reloaded, reused = false)
        }
        return DeviceRegistration.Registered(created, reused = false)
    }

    /**
     * Gleicht den gespeicherten Gerätekanal mit der Kanalliste ab und aktualisiert bei geändertem Ntfy-Token (anderer
     * Fingerprint) nur das Token-Feld per PATCH. Fehlt der Kanal in [channels] (evtl. veraltet), wird erst eine frische
     * Liste geladen; nur wenn er dort auch fehlt, wird er vergessen. True, wenn aktualisiert wurde. Wirft IPv64Exception.
     */
    suspend fun syncTokenIfChanged(ctx: Context, channels: List<Hc2Channel>): Boolean {
        val appContext = ctx.applicationContext
        val storedId = PushSetupStore.channelId(appContext) ?: return false
        fun find(list: List<Hc2Channel>) = list.firstOrNull { it.id == storedId && it.type == HC2_CHANNEL_TYPE_APP }
        val channel = find(channels) ?: find(Hc2ChannelCache.get(appContext, force = true))
        if (channel == null) {
            PushSetupStore.clearChannel(appContext)
            return false
        }
        val token = SecNtfyHelper.storedNtfyToken(appContext) ?: return false
        val fp = PushSetupStore.fingerprint(token)
        if (fp == PushSetupStore.tokenFingerprint(appContext)) return false

        val key = tokenKeyFor(channel, appType(Hc2ChannelTypeCache.get(appContext)))
        if (configHoldsToken(channel, key, token)) {
            PushSetupStore.setChannel(appContext, storedId, fp)
            return false
        }
        ipv64Retrying {
            IPv64Client.get(appContext).hc2.channels.update(
                storedId,
                Hc2ChannelUpdate(config = configWithToken(channel.config, key, token))
            )
        }
        PushSetupStore.setChannel(appContext, storedId, fp)
        Hc2ChannelCache.invalidate()
        return true
    }

    // Gerätename aus den Systemeinstellungen, sonst Hersteller und Modell
    fun defaultDeviceName(ctx: Context): String {
        val systemName = runCatching {
            Settings.Global.getString(ctx.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()?.trim()
        if (!systemName.isNullOrEmpty()) return systemName
        val model = Build.MODEL.orEmpty().trim()
        val manufacturer = Build.MANUFACTURER.orEmpty().trim().replaceFirstChar { it.uppercase() }
        return if (manufacturer.isEmpty() || model.startsWith(manufacturer, ignoreCase = true)) model
        else "$manufacturer $model"
    }

    /**
     * Bestehenden Kanal als dieses Gerät übernehmen: Token (falls [setToken]), Name, Default, Benachrichtigungs-Schalter
     * und ggf. "aktiviert" in einem PATCH setzen, sofern sie abweichen; dann ID und Fingerprint speichern.
     */
    private suspend fun applyToExisting(
        appContext: Context,
        channel: Hc2Channel,
        settings: DeviceChannelSettings,
        key: String,
        token: String,
        setToken: Boolean
    ): Hc2Channel {
        val id = channel.id ?: throw PushRegistrationException("Channel not found", "The channel has no ID.")
        val name = settings.name.trim()
        val update = Hc2ChannelUpdate(
            name = name.takeIf { it.isNotEmpty() && it != channel.name },
            config = if (setToken) configWithToken(channel.config, key, token) else null,
            enabled = true.takeIf { channel.enabled == false },
            // Nie einen bestehenden Default-Kanal abwählen (könnte der einzige des Accounts sein), nur hinzufügen
            default = true.takeIf { settings.default && channel.default != true },
            notifyDown = settings.notifyDown.takeIf { it != channel.notifyDown },
            notifyUp = settings.notifyUp.takeIf { it != channel.notifyUp }
        )
        val result = if (update == Hc2ChannelUpdate()) channel else {
            val updated = ipv64Retrying { IPv64Client.get(appContext).hc2.channels.update(id, update) }
            Hc2ChannelCache.invalidate()
            updated
        }
        PushSetupStore.setChannel(appContext, id, PushSetupStore.fingerprint(token))
        return result
    }

    // Server hat das Token-Feld übernommen (als Secret oder in der Konfiguration)
    private fun holdsKey(channel: Hc2Channel, key: String): Boolean =
        channel.secretsSet.any { it.equals(key, ignoreCase = true) } ||
                channel.config?.keys?.any { it.equals(key, ignoreCase = true) } == true

    // Vergleicht nicht geheime Konfigurationswerte per Fingerprint mit dem Token (zuerst unter key)
    private fun configHoldsToken(channel: Hc2Channel, key: String?, token: String): Boolean {
        val config = channel.config ?: return false
        val fp = PushSetupStore.fingerprint(token)
        fun matches(element: JsonElement?): Boolean {
            val primitive = element as? JsonPrimitive ?: return false
            if (primitive is JsonNull || !primitive.isString) return false
            val value = primitive.content.trim()
            return value.isNotEmpty() && PushSetupStore.fingerprint(value) == fp
        }
        if (key != null && matches(config.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value)) {
            return true
        }
        return config.values.any { matches(it) }
    }

    // Bestehende (nicht geheime) Konfiguration beibehalten und nur das Token-Feld setzen
    private fun configWithToken(existing: JsonObject?, key: String, token: String): JsonObject {
        val map = LinkedHashMap<String, JsonElement>()
        existing?.forEach { (k, v) -> if (!k.equals(key, ignoreCase = true)) map[k] = v }
        map[key] = JsonPrimitive(token)
        return JsonObject(map)
    }
}

// Sprache neuer Kanäle: Systemsprache, falls Deutsch, sonst Englisch
fun systemChannelLang(): String = if (Locale.getDefault().language == "de") "de" else "en"

// Wahrheitswert aus Formular-/Standardwert ("true", "1", "yes", "on")
fun parseChannelBool(raw: String?): Boolean = raw?.trim()?.lowercase() in setOf("true", "1", "yes", "on")

// Formularwert in den JSON-Typ des Felds umwandeln: bool → Boolean, number → Long/Double, sonst String
fun Hc2ChannelTypeField.toConfigValue(raw: String): JsonPrimitive = when (kind?.trim()?.lowercase()) {
    "bool" -> JsonPrimitive(parseChannelBool(raw))
    "number" -> {
        val text = raw.trim()
        text.toLongOrNull()?.let { JsonPrimitive(it) }
            ?: text.toDoubleOrNull()?.let { JsonPrimitive(it) }
            ?: JsonPrimitive(text)
    }

    else -> JsonPrimitive(raw)
}
