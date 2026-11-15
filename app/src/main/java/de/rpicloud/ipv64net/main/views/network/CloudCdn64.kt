package de.rpicloud.ipv64net.main.views.network

import de.rpicloud.ipv64net.helper.TimedCache
import de.sr.ipv64.IPv64
import de.sr.ipv64.IPv64ApiException
import de.sr.ipv64.IPv64AuthScheme
import de.sr.ipv64.IPv64NetworkException
import de.sr.ipv64.IPv64SerializationException
import de.sr.ipv64.model.Problem
import de.sr.ipv64.node64.model.CDNOnlineQuota
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal val CloudCdnCache = TimedCache<CloudCdnOverview>(30_000)

internal data class CloudCdnOverview(val quota: CDNOnlineQuota?, val hosts: List<CloudCdnHost>)

internal data class CloudCdnHost(val fqdn: String, val mode: Int?, val status: String?, val paused: Boolean)

internal data class CloudCdnInput(val fqdn: String, val target: String, val scheme: String, val port: Int?)

private val cdnJson = Json { ignoreUnknownKeys = true }
private val cdnHttp = OkHttpClient.Builder()
    .callTimeout(15, TimeUnit.SECONDS)
    .followRedirects(false)
    .retryOnConnectionFailure(false)
    .build()

internal suspend fun IPv64.loadCloudCdn(): CloudCdnOverview {
    val overview = cloudCdnRequest()
    return decodeCdn {
        CloudCdnOverview(
            quota = (overview["quota"] as? JsonObject)?.let { cdnJson.decodeFromJsonElement(CDNOnlineQuota.serializer(), it) },
            hosts = overview.getValue("hosts").jsonArray.map { it.jsonObject.toCloudCdnHost() }
        )
    }
}

internal suspend fun IPv64.enableCloudCdn(input: CloudCdnInput): CloudCdnHost {
    val body = buildJsonObject {
        put("fqdn", input.fqdn)
        put("mode", 3)
        put("source", if (input.target.isEmpty()) "records" else "fixed")
        if (input.target.isNotEmpty()) put("target", input.target)
        put("scheme", input.scheme)
        input.port?.let { put("port", it) }
    }
    val created = cloudCdnRequest(body)
    return decodeCdn { created.toCloudCdnHost() }
}

private fun JsonObject.toCloudCdnHost(): CloudCdnHost = CloudCdnHost(
    fqdn = requireNotNull(this["fqdn"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }),
    mode = get("mode")?.jsonPrimitive?.intOrNull,
    status = get("status")?.jsonPrimitive?.contentOrNull,
    paused = get("paused")?.jsonPrimitive?.booleanOrNull == true
)

private inline fun <T> decodeCdn(block: () -> T): T = try {
    block()
} catch (e: IllegalArgumentException) {
    throw IPv64SerializationException("Invalid CDN64 response", e)
} catch (e: NoSuchElementException) {
    throw IPv64SerializationException("Incomplete CDN64 response", e)
}

// shortcut: IPv64-Package lacks /cdn64o, replace this transport when the SDK exposes CDN64.
private suspend fun IPv64.cloudCdnRequest(body: JsonObject? = null): JsonObject {
    val url = baseUrl.toHttpUrl().newBuilder().addPathSegment("cdn64o")
    if (body != null) url.addPathSegment("hosts")
    val request = Request.Builder().url(url.build()).header("User-Agent", IPv64.DEFAULT_USER_AGENT)
    apiKey?.let {
        when (authScheme) {
            IPv64AuthScheme.Bearer -> request.header("Authorization", "Bearer $it")
            IPv64AuthScheme.ApiKeyHeader -> request.header("X-API-Key", it)
        }
    }
    actAs?.let { request.header("X-IPv64-Act-As", it) }
    body?.let { request.post(it.toString().toRequestBody("application/json".toMediaType())) }

    return suspendCancellableCoroutine { continuation ->
        val call = cdnHttp.newCall(request.build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(IPv64NetworkException(e.message.orEmpty(), e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        val text = it.body.string()
                        if (!it.isSuccessful) {
                            val problem = runCatching { cdnJson.decodeFromString<Problem>(text) }.getOrNull()
                            throw IPv64ApiException(it.code, problem, it.header("Retry-After")?.toIntOrNull(), text)
                        }
                        continuation.resume(decodeCdn { cdnJson.parseToJsonElement(text).jsonObject.getValue("data").jsonObject })
                    }
                } catch (e: IOException) {
                    onFailure(call, e)
                } catch (e: IPv64ApiException) {
                    continuation.resumeWithException(e)
                } catch (e: IPv64SerializationException) {
                    continuation.resumeWithException(e)
                }
            }
        })
    }
}
