package de.rpicloud.ipv64net.models

data class IPResult(
    var info: String? = "",
    var ip: String? = "",
    var status: String? = ""
) {
    companion object {
        val empty = IPResult("", "0.0.0.0", "")
    }
}