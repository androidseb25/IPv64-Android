package de.rpicloud.ipv64net.models

data class AddDomainResult(
    var info: String? = "",
    var status: String? = "",
    var add_domain: String? = ""
) {
    companion object {
        val empty = AddDomainResult("", "", "")
    }
}