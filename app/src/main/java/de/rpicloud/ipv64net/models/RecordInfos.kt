package de.rpicloud.ipv64net.models

data class RecordInfos(
    var record_id: Int? = 0,
    var content: String? = "",
    var ttl: Int? = 0,
    var type: String? = "",
    var praefix: String? = "",
    var last_update: String? = ""
) {
    companion object {
        val empty = RecordInfos(0, "", 0, "", "", "")
    }
}