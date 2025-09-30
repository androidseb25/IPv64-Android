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

data class IPUpdateResult(
    var info: String? = "",
    var ip: IPUpdate? = IPUpdate.empty,
    var status: String? = ""
) {
    companion object {
        val empty = IPUpdateResult("", IPUpdate.empty, "")
    }
}

data class IPUpdate(
    var ipv4: String? = "0.0.0.0",
    var ipv6: String? = "::"
) {
    companion object {
        val empty = IPUpdate("0.0.0.0", "::")
    }
}