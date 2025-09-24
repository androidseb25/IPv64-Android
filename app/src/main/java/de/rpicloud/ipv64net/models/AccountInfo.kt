package de.rpicloud.ipv64net.models

data class AccountInfo(
    var account_status: Int,
    var dyndns_updates: Int,
    var dyndns_subdomains: Int,
    var owndomains: Int,
    var healthchecks: Int,
    var healthchecks_updates: Int,
    var api_updates: Int,
    var sms_count: Int,
    var vpn_limit_reached: Int,
    var email: String,
    var reg_date: String,
    var update_hash: String,
    var api_key: String,
    var info: String,
    var status: String,
    val get_account_info: String,
    var vpn_traffic_input: Long,
    var vpn_traffic_output: Long,

    var account_class: AccountClass
) {
    companion object {
        val empty = AccountInfo(
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            0,
            0,
            AccountClass.empty
        )
    }
}
