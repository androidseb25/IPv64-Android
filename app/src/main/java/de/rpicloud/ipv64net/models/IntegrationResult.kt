package de.rpicloud.ipv64net.models

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class IntegrationResult(
    var integration: MutableList<Integration>,
    var info: String,
    var status: String,
    var get_account_info: String
) {
    companion object {
        val empty = IntegrationResult(mutableListOf(), "", "", "")
    }
}

fun parseIntegrations(responseText: String, gson: Gson = Gson()): IntegrationResult {
    val root: JsonObject = JsonParser.parseString(responseText).asJsonObject

    val info = root["info"]?.asString ?: ""
    val status = root["status"]?.asString ?: ""
    val getAccountInfo = root["get_account_info"]?.asString ?: ""

    val integrations = mutableListOf<Integration>()

    // alle Keys durchgehen, Meta-Keys überspringen
    root.entrySet().forEach { (key, jsonElement) ->
        if (key == "info" || key == "status" || key == "get_account_info") return@forEach
        // Wert ist ein Integration-Objekt
        val integration = gson.fromJson(jsonElement, Integration::class.java)
        integrations.add(integration)
    }

    return IntegrationResult(integrations, info, status, getAccountInfo)
}