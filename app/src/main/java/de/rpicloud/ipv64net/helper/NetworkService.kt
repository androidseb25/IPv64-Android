package de.rpicloud.ipv64net.helper

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import de.rpicloud.ipv64net.models.AccountInfo
import de.rpicloud.ipv64net.models.AddDomainResult
import de.rpicloud.ipv64net.models.HealthCheck
import de.rpicloud.ipv64net.models.IPResult
import de.rpicloud.ipv64net.models.IPUpdateResult
import de.rpicloud.ipv64net.models.Logs
import de.rpicloud.ipv64net.models.NetworkResult
import de.rpicloud.ipv64net.models.parseDomainResult
import de.rpicloud.ipv64net.models.parseHealthCheckResult
import de.rpicloud.ipv64net.models.parseIntegrations
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
                    callback(NetworkResult("Fehler: ${response.code}", responseText, response.code))
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
                    callback(NetworkResult("Fehler: ${response.code}", responseText, response.code))
                }
            } catch (e: Exception) {
                callback(NetworkResult(e.localizedMessage, null, 500))
            }
        }
    }

    suspend fun GetLogs(callback: (result: NetworkResult) -> Unit) {
        val url = "$baseUrl?get_logs"

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
                    val result = gson.fromJson(responseText, Logs::class.java)
                    println("♻️ - ${result.info}")
                    withContext(Dispatchers.Main) {
                        callback(NetworkResult("Success", result, 200))
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        callback(NetworkResult("Fehler: ${response.code}", responseText, response.code))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(NetworkResult(e.localizedMessage, null, 500))
                }
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
                    callback(NetworkResult("Fehler: ${response.code}", responseText, response.code))
                }
            } catch (e: Exception) {
                callback(NetworkResult(e.localizedMessage, null, 500))
            }
        }
    }

    suspend fun GetMyIP(forV4: Boolean = true, callback: (result: NetworkResult) -> Unit) {

        val url =
            if (forV4) "https://ipv4.ipv64.net/update.php?howismyip" else "https://ipv6.ipv64.net/update.php?howismyip"

        withContext(Dispatchers.IO) {
            try {
                val gson = Gson()
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .addHeader("Accept", "application/json; charset=utf-8")
                    .get()
                    .build()

                val response = OkHttpClientProvider.client.newCall(request).execute()
                val responseText = response.body.string()
                if (response.isSuccessful) {
                    val result = gson.fromJson(responseText, IPResult::class.java)
                    println("♻️ - ${result.ip}")
                    withContext(Dispatchers.Main) {
                        callback(NetworkResult("Success", result, 200))
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        callback(NetworkResult("Fehler: ${response.code}", responseText, response.code))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(NetworkResult(e.localizedMessage, null, 500))
                }
            }
        }
    }

    suspend fun PostNewDomain(domain: String, callback: (result: NetworkResult) -> Unit) {
        val url = baseUrl

        val formBody = FormBody.Builder()
            .add("add_domain", domain)
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

    suspend fun DeleteDomain(domain: String, callback: (result: NetworkResult) -> Unit) {
        val url = baseUrl

        withContext(Dispatchers.IO) {
            try {

                val formBody = FormBody.Builder()
                    .add("del_domain", domain)
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

    suspend fun DeleteDNSRecord(recordId: Int, callback: (result: NetworkResult) -> Unit) {
        val url = baseUrl

        val formBody = FormBody.Builder()
            .add("del_record", recordId.toString())
            .build()

        withContext(Dispatchers.IO) {
            try {
                val gson = Gson()

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .addHeader("Accept", "application/json; charset=utf-8")
                    .addHeader("Authorization", "Bearer $_apiToken")
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

    suspend fun UpdateDNSRecord(url: String, callback: (result: NetworkResult) -> Unit) {
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
                    val result = gson.fromJson(responseText, IPUpdateResult::class.java)
                    withContext(Dispatchers.Main) {
                        println("♻️ - ${result.info}")
                        callback(NetworkResult("Success", result, 200))
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        callback(NetworkResult("Fehler: ${response.code}", responseText, response.code))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(NetworkResult(e.localizedMessage, null, 500))
                }
            }
        }
    }

    suspend fun AddDnsRecord(
        domain: String,
        prefix: String,
        type: String,
        content: String,
        callback: (result: NetworkResult) -> Unit
    ) {
        val url = baseUrl

        val formBody = FormBody.Builder()
            .add("add_record", domain)
            .add("praefix", prefix)
            .add("type", type)
            .add("content", content)
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