package de.rpicloud.ipv64net.models

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class DomainResult(
    var subdomains: MutableList<Domain> = mutableListOf(),
    var info: String? = "",
    var status: String? = "",
    var add_domain: String? = ""
) {
    companion object {
        val empty = DomainResult(mutableListOf(), "", "", "")
    }
}

fun parseDomainResult(json: String, gson: Gson = Gson()): DomainResult {
    val root: JsonObject = JsonParser.parseString(json).asJsonObject

    val info = root["info"]?.asString ?: ""
    val status = root["status"]?.asString ?: ""
    val addDomain = root["add_domain"]?.asString ?: ""

    val subdomains = mutableListOf<Domain>()
    val subdomainsNode = root["subdomains"]

    if (subdomainsNode != null && subdomainsNode.isJsonObject) {
        val obj = subdomainsNode.asJsonObject
        obj.entrySet().forEach { (fqdn, value) ->
            val domain = gson.fromJson(value, Domain::class.java)
            domain.fqdn = fqdn // 🟢 FQDN aus dem Key setzen
            subdomains.add(domain)
        }
    } else if (subdomainsNode != null && subdomainsNode.isJsonArray) {
        // Falls die API irgendwann ein Array liefert – optionaler Fallback
        subdomainsNode.asJsonArray.forEach { el ->
            val domain = gson.fromJson(el, Domain::class.java)
            subdomains.add(domain)
        }
    }

    return DomainResult(
        subdomains = subdomains,
        info = info,
        status = status,
        add_domain = addDomain
    )
}