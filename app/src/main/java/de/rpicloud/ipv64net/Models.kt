package de.rpicloud.ipv64net

import java.util.*

data class DomainResult (
    var subdomains: MutableMap<String, Domain>? = mutableMapOf(),
    var info: String? = "",
    var status: String? = "",
    var add_domain: String? = ""
)

data class AddDomainResult (
    var info: String? = "",
    var status: String? = "",
    var add_domain: String? = ""
)

data class Domain (
    var updates: Int? = 0,
    var wildcard: Int? = 0,
    var domain_update_hash: String? = "",
    var records: MutableList<RecordInfos>? = mutableListOf()
)

data class RecordInfos (
    var record_id: Int? = 0,
    var content: String? = "",
    var ttl: Int? = 0,
    var type: String? = "",
    var praefix: String? = "",
    var last_update: String? = ""
)

data class MyIP (
    var ip: String? = ""
)

data class ErrorTyp (
    var icon: Int? = 0,
    var iconColor: Int? = 0,
    var navigationTitle: String? = "",
    var errorTitle: String? = "",
    var errorDescription: String? = "",
    var status: Int?
)

data class AccountInfo (
    var email: String? = "",
    var account_status: Int? = 0,
    var reg_date: String? = "2022-01-01 00:00:00",
    var update_hash: String? = "",
    var api_key: String? = "",
    var dyndns_updates: Int? = 0,
    var dyndns_subdomains: Int? = 0,
    var owndomains: Int? = 0,
    var healthchecks: Int? = 0,
    var healthchecks_updates: Int? = 0,
    var api_updates: Int? = 0,
    var sms_count: Int? = 0,
    var account_class: AccountClass? = AccountClass(),
    var devicetoken: String? = "",
    var info: String? = "",
    var status: String? = "",
    var get_account_info: String? = ""
)

data class AccountClass (
    var class_name: String? = "",
    var dyndns_domain_limit: Int? = 0,
    var dyndns_update_limit: Int? = 0,
    var owndomain_limit: Int? = 0,
    var healthcheck_limit: Int? = 0,
    var healthcheck_update_limit: Int? = 0,
    var dyndns_ttl: Int? = 0,
    var api_limit: Int? = 0,
    var sms_limit: Int? = 0
)

data class MyLogs (
    var id: UUID = UUID.randomUUID(),
    var subdomain: String? = "",
    var time: String? = "",
    var header: String? = "",
    var content: String? = ""
)

data class Logs (
    var logs: MutableList<MyLogs>? = mutableListOf(),
    var info: String? = "",
)

typealias OnChangedInRecyclerListener = (() -> Unit)