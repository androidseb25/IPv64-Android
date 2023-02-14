package de.rpicloud.ipv64net

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.LiveData
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpDelete
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import com.google.gson.Gson

class ApiNetwork {
    companion object Factory {
        @SuppressLint("StaticFieldLeak")
        var context: Context? = null
        private val apiUrl: String = "https://ipv64.net/api.php"
        private val gson = Gson()

        fun GetDomains(): DomainResult {
            val APIKEY = context?.getSharedString("APIKEY", "APIKEY")
            //var jsonString = gson.toJson(apiUser)
            val httpAsync = "${apiUrl}?get_domains"
                .httpGet()
                .header("Authorization", "Authorization: Bearer $APIKEY" )
                .responseString()

            return when (httpAsync.third) {
                is Result.Failure -> {
                    val ex = (httpAsync.third as Result.Failure<FuelError>).getException()
                    val errordata = String(ex.errorData)
                    println(errordata)
                    DomainResult(info = errordata)
                }
                is Result.Success -> {
                    val data = httpAsync.third.get()
                    gson.fromJson(data, DomainResult::class.java)
                }
            }
        }

        fun GetMyIP4(): MyIP {
            val httpAsync = "https://ipv4.ipv64.net/update.php?howismyip"
                .httpGet()
                .responseString()

            return when (httpAsync.third) {
                is Result.Failure -> {
                    val ex = (httpAsync.third as Result.Failure<FuelError>).getException()
                    val errordata = String(ex.errorData)
                    MyIP()
                }
                is Result.Success -> {
                    val data = httpAsync.third.get()
                    gson.fromJson(data, MyIP::class.java)
                }
            }
        }

        fun GetMyIP6(): MyIP {
            val httpAsync = "https://ipv6.ipv64.net/update.php?howismyip"
                .httpGet()
                .responseString()

            return when (httpAsync.third) {
                is Result.Failure -> {
                    val ex = (httpAsync.third as Result.Failure<FuelError>).getException()
                    val errordata = String(ex.errorData)
                    MyIP()
                }
                is Result.Success -> {
                    val data = httpAsync.third.get()
                    gson.fromJson(data, MyIP::class.java)
                }
            }
        }

        fun UpdateDomainIp(updateKey: String, domain: String): DomainResult {
            val APIKEY = context?.getSharedString("APIKEY", "APIKEY")
            //var jsonString = gson.toJson(apiUser)
            val httpAsync = "https://ipv4.ipv64.net/update.php?key=${updateKey}&domain=${domain}"
                .httpGet()
                .header("Authorization", "Authorization: Bearer $APIKEY" )
                .responseString()

            return when (httpAsync.third) {
                is Result.Failure -> {
                    val ex = (httpAsync.third as Result.Failure<FuelError>).getException()
                    val errordata = String(ex.errorData)
                    println(errordata)
                    DomainResult(info = errordata)
                }
                is Result.Success -> {
                    val data = httpAsync.third.get()
                    gson.fromJson(data, DomainResult::class.java)
                }
            }
        }

        fun PostDomain(domain: String): AddDomainResult {
            val APIKEY = context?.getSharedString("APIKEY", "APIKEY")
            val formData = listOf("add_domain" to domain )
            println(domain)
            val httpAsync = "${apiUrl}"
                .httpPost(parameters = formData)
                .header("Authorization", "Authorization: Bearer $APIKEY" )
                .responseString()

            return when (httpAsync.third) {
                is Result.Failure -> {
                    val ex = (httpAsync.third as Result.Failure<FuelError>).getException()
                    val errordata = String(ex.errorData)
                    var errorMessage = AddDomainResult()
                    if (errordata.isNotEmpty()) {
                        errorMessage = gson.fromJson(errordata, AddDomainResult::class.java)
                    }
                    errorMessage
                }
                is Result.Success -> {
                    val data = httpAsync.third.get()
                    val response = gson.fromJson(data, AddDomainResult::class.java)
                    response
                }
            }
        }

        fun DeleteDomain(domain: String): AddDomainResult {
            val APIKEY = context?.getSharedString("APIKEY", "APIKEY")
            println(domain)
            val httpAsync = "${apiUrl}"
                .httpDelete()
                .header("Authorization", "Authorization: Bearer $APIKEY" )
                .header(Headers.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body("del_domain=$domain" )
                .responseString()

            return when (httpAsync.third) {
                is Result.Failure -> {
                    val ex = (httpAsync.third as Result.Failure<FuelError>).getException()
                    val errordata = String(ex.errorData)
                    var errorMessage = AddDomainResult()
                    if (errordata.isNotEmpty()) {
                        errorMessage = gson.fromJson(errordata, AddDomainResult::class.java)
                    }
                    errorMessage
                }
                is Result.Success -> {
                    val data = httpAsync.third.get()
                    val response = gson.fromJson(data, AddDomainResult::class.java)
                    response
                }
            }
        }

        fun PostDNSRecord(domain: String, praefix: String, typ: String, content: String): AddDomainResult {
            val APIKEY = context?.getSharedString("APIKEY", "APIKEY")
            val formData = listOf("add_record" to domain, "praefix" to praefix, "type" to typ, "content" to content )
            println(domain)
            val httpAsync = "${apiUrl}"
                .httpPost(parameters = formData)
                .header("Authorization", "Authorization: Bearer $APIKEY" )
                .responseString()

            return when (httpAsync.third) {
                is Result.Failure -> {
                    val ex = (httpAsync.third as Result.Failure<FuelError>).getException()
                    val errordata = String(ex.errorData)
                    var errorMessage = AddDomainResult()
                    if (errordata.isNotEmpty()) {
                        errorMessage = gson.fromJson(errordata, AddDomainResult::class.java)
                    }
                    errorMessage
                }
                is Result.Success -> {
                    val data = httpAsync.third.get()
                    val response = gson.fromJson(data, AddDomainResult::class.java)
                    response
                }
            }
        }

        fun DeleteDNSRecord(recordId: Int): AddDomainResult {
            val APIKEY = context?.getSharedString("APIKEY", "APIKEY")
            val httpAsync = "${apiUrl}"
                .httpDelete()
                .body("del_record=$recordId" )
                .header("Authorization", "Authorization: Bearer $APIKEY" )
                .responseString()

            return when (httpAsync.third) {
                is Result.Failure -> {
                    val ex = (httpAsync.third as Result.Failure<FuelError>).getException()
                    val errordata = String(ex.errorData)
                    var errorMessage = AddDomainResult()
                    if (errordata.isNotEmpty()) {
                        errorMessage = gson.fromJson(errordata, AddDomainResult::class.java)
                    }
                    errorMessage
                }
                is Result.Success -> {
                    val data = httpAsync.third.get()

                    val response = gson.fromJson(data, AddDomainResult::class.java)
                    response
                }
            }
        }

        fun GetAccountStatus(): AccountInfo {
            val APIKEY = context?.getSharedString("APIKEY", "APIKEY")
            //var jsonString = gson.toJson(apiUser)
            val httpAsync = "${apiUrl}?get_account_info"
                .httpGet()
                .header("Authorization", "Authorization: Bearer $APIKEY" )
                .responseString()

            return when (httpAsync.third) {
                is Result.Failure -> {
                    val ex = (httpAsync.third as Result.Failure<FuelError>).getException()
                    val errordata = String(ex.errorData)
                    println(errordata)
                    AccountInfo(info = errordata)
                }
                is Result.Success -> {
                    val data = httpAsync.third.get()
                    gson.fromJson(data, AccountInfo::class.java)
                }
            }
        }

        fun GetLogs(): Logs {
            val APIKEY = context?.getSharedString("APIKEY", "APIKEY")
            //var jsonString = gson.toJson(apiUser)
            val httpAsync = "${apiUrl}?get_logs"
                .httpGet()
                .header("Authorization", "Authorization: Bearer $APIKEY" )
                .responseString()

            return when (httpAsync.third) {
                is Result.Failure -> {
                    val ex = (httpAsync.third as Result.Failure<FuelError>).getException()
                    val errordata = String(ex.errorData)
                    println(errordata)
                    Logs(info = errordata)
                }
                is Result.Success -> {
                    val data = httpAsync.third.get()
                    gson.fromJson(data, Logs::class.java)
                }
            }
        }
    }
}