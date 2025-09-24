package de.rpicloud.ipv64net.helper

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import de.rpicloud.ipv64net.models.AccountInfo
import de.rpicloud.ipv64net.models.DomainResult
import de.rpicloud.ipv64net.models.IntegrationResult
import de.rpicloud.ipv64net.models.NetworkResult
import de.rpicloud.ipv64net.models.parseDomainResult
import de.rpicloud.ipv64net.models.parseIntegrations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.jvm.java

object OkHttpClientProvider {
    val eventListener = object : EventListener() {
        override fun connectEnd(
            call: Call,
            inetSocketAddress: InetSocketAddress,
            proxy: Proxy,
            protocol: Protocol?
        ) {
            val ip = inetSocketAddress.address.hostAddress
            Log.d("NetDebug", "Verbindung zu: $ip (Version: ${if (inetSocketAddress.address is Inet6Address) "IPv6" else "IPv4"})")
        }
    }

    val ipv4OnlyDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val allAddresses = Dns.SYSTEM.lookup(hostname)
            Log.d("NetDebug", "DNS-Auflösung für $hostname: ${allAddresses.joinToString()}")
            return allAddresses.filter { it is Inet4Address }
        }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .eventListener(eventListener)
            .dns(ipv4OnlyDns)
            .build()
    }
}

class NetworkService {

    private val baseUrl: String = "https://ipv64.net/api.php"
    private var _ctx: Context? = null
    private var _apiToken: String = ""

    constructor(ctx: Context) {
        _ctx = ctx
        _apiToken = PreferencesManager.loadString(ctx, "APIKEY")
    }

    suspend fun GetAccountStatus(callback: (result: NetworkResult) -> Unit) {
        val url = "$baseUrl?get_account_info"

        withContext(Dispatchers.IO) {
            try {
                val gson = Gson()
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .addHeader("Accept", "application/json; charset=utf-8")
                    .addHeader("Authorization", "Bearer $_apiToken")
                    .get()
                    .build()

                val response = OkHttpClientProvider.client.newCall(request).execute()
                val responseText = response.body.string()
                if (response.isSuccessful) {
                    val result = gson.fromJson(responseText, AccountInfo::class.java)
                    println("♻️ - ${result.email}")
                    callback(NetworkResult("Success", result, 200))
                } else {
                    callback(NetworkResult("Fehler: ${response.code}", null, response.code))
                }
            } catch (e: Exception) {
                callback(NetworkResult(e.localizedMessage, null, 500))
            }
        }
    }

    suspend fun GetIntegrations(callback: (result: NetworkResult) -> Unit) {
        val url = "$baseUrl?get_integrations"

        withContext(Dispatchers.IO) {
            try {
                val gson = Gson()
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .addHeader("Accept", "application/json; charset=utf-8")
                    .addHeader("Authorization", "Bearer $_apiToken")
                    .get()
                    .build()

                val response = OkHttpClientProvider.client.newCall(request).execute()
                val responseText = response.body.string()
                if (response.isSuccessful) {
                    val result = parseIntegrations(responseText)
                    println("♻️ - ${result.get_account_info}")
                    callback(NetworkResult("Success", result, 200))
                } else {
                    callback(NetworkResult("Fehler: ${response.code}", null, response.code))
                }
            } catch (e: Exception) {
                callback(NetworkResult(e.localizedMessage, null, 500))
            }
        }
    }

    suspend fun GetDomains(callback: (result: NetworkResult) -> Unit) {
        val url = "$baseUrl?get_domains"

        withContext(Dispatchers.IO) {
            try {
                val gson = Gson()
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .addHeader("Accept", "application/json; charset=utf-8")
                    .addHeader("Authorization", "Bearer $_apiToken")
                    .get()
                    .build()

                val response = OkHttpClientProvider.client.newCall(request).execute()
                val responseText = response.body.string()
                if (response.isSuccessful) {
                    val result = parseDomainResult(responseText)
                    println("♻️ - ${result.add_domain}")
                    callback(NetworkResult("Success", result, 200))
                } else {
                    callback(NetworkResult("Fehler: ${response.code}", null, response.code))
                }
            } catch (e: Exception) {
                callback(NetworkResult(e.localizedMessage, null, 500))
            }
        }
    }

}