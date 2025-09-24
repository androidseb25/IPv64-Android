package de.rpicloud.ipv64net.models

data class AccountClass(
    var dyndns_domain_limit: Int,
    var dyndns_update_limit: Int,
    var owndomain_limit: Int,
    var healthcheck_limit: Int,
    var healthcheck_update_limit: Int,
    var dyndns_ttl: Int,
    var api_limit: Int,
    var sms_limit: Int,
    var vpn_traffic: Int,
    var vpn_queue: Int,
    var class_name: String
) {
    companion object {
        val empty = AccountClass(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "")
    }
}