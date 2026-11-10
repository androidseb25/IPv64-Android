package de.rpicloud.ipv64net.main.views.network

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.rpicloud.ipv64net.models.User
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.node64.model.ModuleStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import de.sr.ipv64.IPv64SerializationException
import de.sr.ipv64.Node64ApiException
import de.sr.ipv64.node64.Node64
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Lokaler Zugang (URL + API-Key) pro Node-ID in einer privaten Prefs-Datei. Ausgenommen vom Backup
 * (backup_rules/data_extraction_rules). owner = User.id des Accounts, der den Zugang angelegt hat.
 */
object LocalNodeStore {
    private const val FILE = "NODE64_LOCAL"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** (url, key) oder null, wenn für die Node nichts gespeichert ist. */
    fun get(ctx: Context, nodeId: String, owner: String = User.current?.id?.toString().orEmpty()): Pair<String, String>? {
        val p = prefs(ctx)
        if (p.getString("$nodeId.owner", null) != owner) return null
        val url = p.getString("$nodeId.url", null) ?: return null
        val key = p.getString("$nodeId.key", null) ?: return null
        return url to key
    }

    /** Name der Node laut lokalem Status (nur für die Liste direkter Nodes), null = unbekannt. */
    fun name(ctx: Context, nodeId: String): String? = prefs(ctx).getString("$nodeId.name", null)

    /** IDs aller Nodes mit gespeichertem Zugang dieses Accounts. */
    fun list(ctx: Context, owner: String): List<String> = localNodeIds(prefs(ctx).all, owner)

    fun setName(ctx: Context, nodeId: String, name: String) {
        prefs(ctx).edit().putString("$nodeId.name", name).apply()
    }

    fun save(ctx: Context, nodeId: String, url: String, key: String, owner: String, name: String? = null) {
        val edit = prefs(ctx).edit()
            .putString("$nodeId.url", url)
            .putString("$nodeId.key", key)
            .putString("$nodeId.owner", owner)
        if (name != null) edit.putString("$nodeId.name", name)
        edit.apply()
    }

    /** Zuletzt bekannter Zustand des Proxy64-Moduls (für den Tab, wenn die Node gerade nicht erreichbar ist). */
    fun proxyActive(ctx: Context, nodeId: String): Boolean = prefs(ctx).getBoolean("$nodeId.proxy", false)

    /** Zuletzt bekannter Zustand des DNS64-Moduls (steuert den DNS64-Tab). */
    fun dnsActive(ctx: Context, nodeId: String): Boolean = prefs(ctx).getBoolean("$nodeId.dns", false)

    /** Merkt, ob Proxy64 und DNS64 auf der Node eingeschaltet sind (schreibt nur bei Änderung). */
    fun saveModules(ctx: Context, nodeId: String, modules: List<ModuleStatus>) {
        val proxy = modules.any { it.isProxyModule() && it.enabled == true }
        val dns = modules.any { it.name.equals("dns64", ignoreCase = true) && it.enabled == true }
        if (proxy == proxyActive(ctx, nodeId) && dns == dnsActive(ctx, nodeId)) return
        prefs(ctx).edit().putBoolean("$nodeId.proxy", proxy).putBoolean("$nodeId.dns", dns).apply()
    }

    fun remove(ctx: Context, nodeId: String) {
        prefs(ctx).edit().remove("$nodeId.url").remove("$nodeId.key").remove("$nodeId.owner").remove("$nodeId.name").remove("$nodeId.proxy").remove("$nodeId.dns").apply()
    }

    /** Entfernt alle Zugänge eines Accounts (beim Entfernen des Accounts aus der App). */
    fun removeOwner(ctx: Context, owner: String) {
        val p = prefs(ctx)
        val nodeIds = p.all.filter { (k, v) -> k.endsWith(".owner") && v == owner }.keys.map { it.removeSuffix(".owner") }
        if (nodeIds.isEmpty()) return
        val edit = p.edit()
        nodeIds.forEach { edit.remove("$it.url").remove("$it.key").remove("$it.owner").remove("$it.name").remove("$it.proxy").remove("$it.dns") }
        edit.apply()
    }
}

/**
 * Direkt verbundene Nodes (mit API-Key) des aktiven Accounts als Compose-State; steuert die Tab-Leiste
 * (DNS64-Tab, CDN64 ↔ Proxy64) und die Node-Abschnitte in Healthchecks/DNS64/Proxy64.
 */
object ConnectedNodes {
    /** Node-IDs mit gespeichertem Zugang (sortiert). */
    var ids by mutableStateOf<List<String>>(emptyList())
        private set

    /** IDs der Nodes, deren Proxy64-Modul aktiv ist (zuletzt bekannter Stand). */
    var proxyIds by mutableStateOf<List<String>>(emptyList())
        private set

    val proxyActive: Boolean get() = proxyIds.isNotEmpty()

    /** IDs der Nodes, deren DNS64-Modul aktiv ist (zuletzt bekannter Stand); leer = kein DNS64-Tab. */
    var dnsIds by mutableStateOf<List<String>>(emptyList())
        private set

    /** Liest die Zugänge neu (nur Prefs, keine Anfrage). */
    fun refresh(ctx: Context) {
        val list = LocalNodeStore.list(ctx, User.current?.id?.toString().orEmpty())
        if (list != ids) ids = list
        val proxies = list.filter { LocalNodeStore.proxyActive(ctx, it) }
        if (proxies != proxyIds) proxyIds = proxies
        val dns = list.filter { LocalNodeStore.dnsActive(ctx, it) }
        if (dns != dnsIds) dnsIds = dns
    }

    /** Fragt status() jeder Node ab und merkt, ob Proxy64/DNS64 aktiv sind; nicht erreichbare Nodes behalten ihren Stand. */
    suspend fun refreshModules(ctx: Context) {
        coroutineScope {
            ids.map { id ->
                async {
                    val (url, key) = LocalNodeStore.get(ctx, id) ?: return@async
                    try {
                        LocalNodeStore.saveModules(ctx, id, localNodeClient(url, key).node.status().modules)
                    } catch (_: IPv64Exception) {
                    }
                }
            }.awaitAll()
        }
        refresh(ctx)
    }
}

/** Proxy64-Modul der Node (heißt laut Node64 "cdn64"). */
fun ModuleStatus.isProxyModule(): Boolean = name?.lowercase() in setOf("cdn64", "proxy64", "proxy")

/** Node-IDs aus den Prefs-Einträgen, die eine URL haben und [owner] gehören (sortiert). */
internal fun localNodeIds(all: Map<String, *>, owner: String): List<String> =
    all.keys.filter { it.endsWith(".url") }.map { it.removeSuffix(".url") }
        .filter { all["$it.owner"] == owner }
        .sorted()

private const val DEFAULT_NODE_PORT = 8080

/**
 * Eingabe → Basis-URL der Node-API, z. B. "192.168.1.10" → "http://192.168.1.10:8080/api".
 * Nur http/https. http für IP-Literale (lokal oder öffentlich) und lokale Namen; andere Namen über http prüft beim
 * Verbinden localOnlyDns. Ohne Schema: IP-Literal oder lokaler Name → http (ohne Port :8080), sonst https.
 * Leerer Pfad → "/api". Keine DNS-Abfrage.
 */
internal fun normalizeLocalNodeUrl(input: String): String? {
    val raw = input.trim()
    if (raw.isEmpty()) return null
    val hasScheme = raw.contains("://")
    val parsed = (if (hasScheme) raw else "http://$raw").toHttpUrlOrNull() ?: return null
    val local = isLocalHost(parsed.host)
    val ip = isIpHost(parsed.host)
    val builder = parsed.newBuilder().query(null).fragment(null).username("").password("")
    if (!hasScheme) {
        // Port explizit angegeben? Authority ohne IPv6-Klammerteil auf ":" prüfen
        val explicitPort = raw.substringBefore('/').substringAfterLast(']').contains(':')
        if (local || ip) {
            if (!explicitPort) builder.port(DEFAULT_NODE_PORT)
        } else {
            builder.scheme("https")
        }
    }
    val url = builder.build()
    // http: rein numerische Namen, die kein a.b.c.d sind ("192.168.1.1.", "3232235777"), ablehnen: OkHttp sähe sie als
    // Namen, das System löste sie aber zu einer IP auf. Echte IP-Literale (auch öffentliche) sind erlaubt.
    val numeric = url.host.all { it.isDigit() || it == '.' }
    if (url.scheme == "http" && numeric && !isIpHost(url.host)) return null
    val path = url.encodedPath.trimEnd('/').ifEmpty { "/api" }
    return url.newBuilder().encodedPath(path).build().toString()
}

private val ipv4Literal = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

/** Host ist ein IP-Literal (a.b.c.d oder IPv6, ohne Klammern wie in HttpUrl.host); löst nichts auf, ohne Android-API. */
internal fun isIpHost(host: String): Boolean = host.contains(':') || ipv4Literal.matches(host)

/** Ziel ist weder privates IP-Literal noch lokaler Name: Klartext ginge über das Internet. */
internal fun isPublicHttp(url: String): Boolean {
    val parsed = url.toHttpUrlOrNull() ?: return false
    return parsed.scheme == "http" && !isLocalHost(parsed.host)
}
private val localSuffixes = listOf(".local", ".lan", ".home.arpa", ".internal", ".fritz.box", ".home", ".localdomain")

/** Private/Link-Local/Loopback-IP-Literal, Name ohne Punkt oder lokaler Namens-Suffix. Löst keine Namen auf. */
internal fun isLocalHost(host: String): Boolean {
    // Kein trimEnd('.') vor der IP-Prüfung: "192.168.1.1." wäre für OkHttp ein Name und ginge am DNS-Filter vorbei
    val h = host.lowercase().removePrefix("[").removeSuffix("]")
    if (h.contains(':') || ipv4Literal.matches(h)) {
        // Nur Literale: getByName parst sie ohne DNS
        val addr = runCatching { InetAddress.getByName(h) }.getOrNull() ?: return false
        return isLocalAddress(addr)
    }
    // Rein numerisch, aber kein a.b.c.d (z. B. "192.168.1.1.", "3232235777"): nie als lokalen Namen werten
    if (h.all { it.isDigit() || it == '.' }) return false
    // Namen laufen immer durch localOnlyDns (+ NO_PROXY), daher sind Einzelnamen wie "raspberrypi" unkritisch
    val name = h.trimEnd('.')
    return !name.contains('.') || localSuffixes.any { name.endsWith(it) }
}

/** 10/8, 172.16/12, 192.168/16, 169.254/16, 127/8, fc00::/7, fe80::/10, ::1. */
internal fun isLocalAddress(a: InetAddress): Boolean =
    a.isLoopbackAddress || a.isLinkLocalAddress ||
        (a !is Inet6Address && a.isSiteLocalAddress) ||
        (a is Inet6Address && (a.address[0].toInt() and 0xFE) == 0xFC)

// Für http:// nur lokale Adressen zulassen (ein .local-Name darf nicht ins Internet auflösen)
// IP-Literale gibt der Nutzer bewusst ein (auch öffentliche); sie werden nur geparst, nicht gefiltert
private val localOnlyDns = Dns { host ->
    if (isIpHost(host)) return@Dns listOf(InetAddress.getByName(host))
    Dns.SYSTEM.lookup(host).filter(::isLocalAddress).ifEmpty { throw UnknownHostException("$host has no local address") }
}

private val localBaseClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
}

// Ohne Proxy: sonst ginge der Key im Klartext an den Proxy, und der DNS-Filter prüfte den Proxy statt der Node
private val localHttpClient by lazy { localBaseClient.newBuilder().dns(localOnlyDns).proxy(Proxy.NO_PROXY).build() }

/** Client für die lokale API einer Node; [url] muss von [normalizeLocalNodeUrl] stammen. */
fun localNodeClient(url: String, key: String): Node64 = Node64(
    baseUrl = url,
    apiKey = key,
    actor = "IPv64 Android",
    okHttpClient = if (url.startsWith("http://")) localHttpClient else localBaseClient
)

/** Fehler der lokalen Node → (Titel, Text). Nicht toError() verwenden: das kennt nur Cloud-Fehler. */
fun localNodeError(e: Throwable): Pair<String, String> {
    // Anmeldeseite des PROXY64-Zugriffsschutzes: die Anfrage erreicht die Node-API gar nicht
    if (e is Node64ApiException && e.status == 401 && e.rawBody.contains("/.node64/login")) {
        return "Address is sign-in protected" to
            "PROXY64 asks for a sign-in on this address, so the API key never reaches the node. " +
            "Use the node's local IP, or give the path /api its own open access list in the PROXY64 host (the node's API key still protects it)."
    }
    if (e is Node64ApiException) return when (e.status) {
        401, 403 -> "API key rejected" to listOfNotNull(
            "The node rejected the API key (HTTP ${e.status}).",
            e.error?.let { "Node: $it" },
            "Create a key in the node's web interface on the API tab."
        ).joinToString("\n")
        404 -> "Not a Node64 API" to "Not a Node64 API — does the URL end in /api?"
        else -> "Node error" to (e.error ?: "The node answered with HTTP ${e.status}.")
    }
    if (e is IPv64SerializationException) {
        return "Unexpected response" to "The address answered, but not like a Node64 API. Check the URL (it usually ends in /api)."
    }
    val causes = generateSequence(e) { it.cause }.take(10).toList()
    return when {
        causes.any { it is UnknownServiceException } ->
            "Unencrypted connection blocked" to "HTTP is only allowed for IP addresses and local names (e.g. nas.local). Use https:// for a domain."
        causes.any { it is UnknownHostException && it.message?.contains("no local address") == true } ->
            "Not a local address" to "Over http:// a name must resolve to an address in your local network. Use the node's IP address or its https:// address."
        causes.any { it is SSLHandshakeException || it is SSLPeerUnverifiedException } ->
            "Certificate not trusted" to "The node's HTTPS certificate is not trusted by this device. Use the node's local http:// address or a valid certificate."
        else -> "Node not reachable" to "Could not reach the node. Is your phone on the same network as the node?"
    }
}
