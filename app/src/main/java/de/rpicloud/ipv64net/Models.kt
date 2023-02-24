package de.rpicloud.ipv64net

import org.json.JSONObject
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

data class HealthCheckResult (
    var domain: MutableList<HealthCheck> = mutableListOf(),
    var info: String? = "",
    var status: String? = "",
    var get_account_info: String? = ""
)

data class HealthCheck(
    // healthstatus == 1 = Active; 2 = Paused; 3 = Warning; 4 = Alarm;
    var name: String = "",
    var healthstatus: Int = 0,
    var healthtoken: String = "",
    var add_time: String? = "",
    var last_update_time: String? = "",
    var alarm_time: String? = "",
    var alarm_down: Int = 0,
    var alarm_up: Int = 0,
    var integration_id: String = "0",
    var alarm_count: Int = 0,
    var alarm_unit: Int = 0,
    var grace_count: Int = 0,
    var grace_unit: Int = 0,
    var pings_total: Int = 0,
    var type: String = "",
    var type_options: String = "",
    var next_ping: String = "",
    var events: MutableList<HealthEvents> = mutableListOf()
)

data class HealthEvents(
    var event_time: String? = "",
    var status: Int? = 0,
    var text: String? = ""
)

data class StatusTypeClass(
    var statusId: Int? = 0, var name: String? = "", var icon: Int? = 0, var color: Int? = 0
)

typealias OnChangedInRecyclerListener = (() -> Unit)