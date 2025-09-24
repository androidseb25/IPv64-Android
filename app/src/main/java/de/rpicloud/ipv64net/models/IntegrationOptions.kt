package de.rpicloud.ipv64net.models

data class IntegrationOptions(
    var serverurl: String?,
    var downprio: String?,
    var upprio: String?,
    var number: String?,
    var countrycode: String?,
    var completenumber: String?,
    var key: String?,
    var webhookurl: String?,
    var pinguser: String?,
    var pinggroup: String?,
    var email: String?,
    var devicetoken: String?,
    var apptoken: String?,
    var priority: String?
) {
    companion object {
        val empty = IntegrationOptions("", "", "", "", "", "", "", "", "", "", "", "", "", "")
    }
}