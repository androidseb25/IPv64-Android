package de.rpicloud.ipv64net.models

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class HealthCheckResult(
    var domain: MutableList<HealthCheck> = mutableListOf(),
    var info: String? = "",
    var status: String? = "",
    var get_account_info: String? = ""
) {
    companion object {
        val empty = HealthCheckResult(mutableListOf(), "", "", "")
    }
}

fun parseHealthCheckResult(json: String, gson: Gson = Gson()): HealthCheckResult {
    val root: JsonObject = JsonParser.parseString(json).asJsonObject

    val info = root["info"]?.asString ?: ""
    val status = root["status"]?.asString ?: ""
    val getAccountInfo = root["get_account_info"]?.asString ?: ""

    val list = mutableListOf<HealthCheck>()

    // alle Top-Level-Entries durchgehen und Meta-Keys überspringen
    root.entrySet().forEach { (key, value) ->
        if (key == "info" || key == "status" || key == "get_account_info") return@forEach
        // Deine HealthChecks liegen unter numerischen Schlüsseln: "0","1","2",...
        // value ist jeweils ein Objekt, das direkt in HealthCheck passt
        if (value.isJsonObject) {
            val hc = gson.fromJson(value, HealthCheck::class.java)
            list.add(hc)
        }
    }

    return HealthCheckResult(
        domain = list,
        info = info,
        status = status,
        get_account_info = getAccountInfo
    )
}