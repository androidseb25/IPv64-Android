package de.rpicloud.ipv64net.helper

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import de.rpicloud.ipv64net.models.AddDomainResult
import de.rpicloud.ipv64net.models.HealthCheck
import de.rpicloud.ipv64net.models.NetworkResult
import de.rpicloud.ipv64net.models.parseHealthCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

object OkHttpClientProvider {
    val eventListener = object : EventListener() {
        override fun connectEnd(
            call: Call,
            inetSocketAddress: InetSocketAddress,
            proxy: Proxy,
            protocol: Protocol?
        ) {
            val ip = inetSocketAddress.address.hostAddress
            Log.d(
                "NetDebug",
                "Verbindung zu: $ip (Version: ${if (inetSocketAddress.address is Inet6Address) "IPv6" else "IPv4"})"
            )
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
//            .dns(ipv4OnlyDns)
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

    suspend fun GetHealthchecks(callback: (result: NetworkResult) -> Unit) {
        val url = "$baseUrl?get_healthchecks&events"

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
                    val result = parseHealthCheckResult(responseText)
                    println("♻️ - ${result.get_account_info}")
                    callback(NetworkResult("Success", result, 200))
                } else {
                    callback(NetworkResult("Fehler: ${response.code}", responseText, response.code))
                }
            } catch (e: Exception) {
                callback(NetworkResult(e.localizedMessage, null, 500))
            }
        }
    }

    suspend fun PostNewHealthcheck(hcName: String, hcCount: Int, hcUnit: Int, callback: (result: NetworkResult) -> Unit) {
        val url = baseUrl

        val formBody = FormBody.Builder()
            .add("add_healthcheck", hcName)
            .add("alarm_count", hcCount.toString())
            .add("alarm_unit", hcUnit.toString())
            .build()

        withContext(Dispatchers.IO) {
            try {
                val gson = Gson()

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .addHeader("Accept", "application/json; charset=utf-8")
                    .addHeader("Authorization", "Bearer $_apiToken")
                    .post(formBody)
                    .build()

                val response = OkHttpClientProvider.client.newCall(request).execute()
                val responseText = response.body.string()
                if (response.isSuccessful) {
                    val result = gson.fromJson(responseText, AddDomainResult::class.java)
                    withContext(Dispatchers.Main) {
                        println("♻️ - ${result.add_domain}")
                        callback(NetworkResult("Success", result, 200))
                    }
                } else {
                    val result = gson.fromJson(responseText, AddDomainResult::class.java)
                    withContext(Dispatchers.Main) {
                        callback(NetworkResult("Fehler: ${response.code}", result, response.code))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(NetworkResult(e.localizedMessage, null, 500))
                }
            }
        }
    }

    suspend fun PostStartPauseHealthcheck(hcStartPause: String, hcToken: String, callback: (result: NetworkResult) -> Unit) {
        val url = baseUrl

        val formBody = FormBody.Builder()
            .add(hcStartPause, hcToken)
            .build()

        withContext(Dispatchers.IO) {
            try {
                val gson = Gson()

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .addHeader("Accept", "application/json; charset=utf-8")
                    .addHeader("Authorization", "Bearer $_apiToken")
                    .post(formBody)
                    .build()

                val response = OkHttpClientProvider.client.newCall(request).execute()
                val responseText = response.body.string()
                if (response.isSuccessful) {
                    val result = gson.fromJson(responseText, AddDomainResult::class.java)
                    withContext(Dispatchers.Main) {
                        println("♻️ - ${result.add_domain}")
                        callback(NetworkResult("Success", result, 200))
                    }
                } else {
                    val result = gson.fromJson(responseText, AddDomainResult::class.java)
                    withContext(Dispatchers.Main) {
                        callback(NetworkResult("Fehler: ${response.code}", result, response.code))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(NetworkResult(e.localizedMessage, null, 500))
                }
            }
        }
    }

    suspend fun PostEditHealthcheck(hc: HealthCheck, callback: (result: NetworkResult) -> Unit) {
        val url = baseUrl

        val formBody = FormBody.Builder()
            .add("edit_healthcheck", hc.healthtoken)
            .add("healthcheck_name", hc.name)
            .add("alarm_count", hc.alarm_count.toString())
            .add("alarm_unit", hc.alarm_unit.toString())
            .add("integration", hc.integration_id)
            .add("grace_count", hc.grace_count.toString())
            .add("grace_unit", hc.grace_unit.toString())
            .add("alarm_down", hc.alarm_down.toString())
            .add("alarm_up", hc.alarm_up.toString())
            .build()

        withContext(Dispatchers.IO) {
            try {
                val gson = Gson()

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .addHeader("Accept", "application/json; charset=utf-8")
                    .addHeader("Authorization", "Bearer $_apiToken")
                    .post(formBody)
                    .build()

                val response = OkHttpClientProvider.client.newCall(request).execute()
                val responseText = response.body.string()
                if (response.isSuccessful) {
                    val result = gson.fromJson(responseText, AddDomainResult::class.java)
                    withContext(Dispatchers.Main) {
                        println("♻️ - ${result.add_domain}")
                        callback(NetworkResult("Success", result, 200))
                    }
                } else {
                    val result = gson.fromJson(responseText, AddDomainResult::class.java)
                    withContext(Dispatchers.Main) {
                        callback(NetworkResult("Fehler: ${response.code}", result, response.code))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(NetworkResult(e.localizedMessage, null, 500))
                }
            }
        }
    }

    suspend fun DeleteHealthcheck(hcToken: String, callback: (result: NetworkResult) -> Unit) {
        val url = baseUrl

        withContext(Dispatchers.IO) {
            try {

                val formBody = FormBody.Builder()
                    .add("del_healthcheck", hcToken)
                    .build()

                val gson = Gson()

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Authorization: Bearer $_apiToken")
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .delete(formBody)
                    .build()

                val response = OkHttpClientProvider.client.newCall(request).execute()
                val responseText = response.body.string()
                if (response.isSuccessful) {
                    val result = gson.fromJson(responseText, AddDomainResult::class.java)
                    withContext(Dispatchers.Main) {
                        println("♻️ - ${result.add_domain}")
                        callback(NetworkResult("Success", result, 200))
                    }
                } else {
                    val result = gson.fromJson(responseText, AddDomainResult::class.java)
                    withContext(Dispatchers.Main) {
                        callback(NetworkResult("Fehler: ${response.code}", result, response.code))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(NetworkResult(e.localizedMessage, null, 500))
                }
            }
        }
    }
}