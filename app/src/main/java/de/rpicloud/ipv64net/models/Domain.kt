package de.rpicloud.ipv64net.models

import androidx.compose.ui.graphics.Color
import de.rpicloud.ipv64net.R

data class Domain(
    var updates: Int? = 0,
    var wildcard: Int? = 0,
    var domain_update_hash: String? = "",
    var records: MutableList<RecordInfos>? = mutableListOf(),
    // kommt NICHT aus dem JSON – füllen wir aus dem Schlüssel (z. B. "vcbdfaga.ipv64.net")
    var fqdn: String? = ""
) {
    companion object {
        val empty = Domain(0, 0, "", mutableListOf(), "")
    }

    val IsWildcard: String
        get() = when (wildcard) {
            0 -> "no"
            else -> "yes"
        }

    val isSameTypeAAddress: Boolean
        get() = records?.count { it.content == "62.149.104.72" && it.type == "A" } == 1

    val isSameTypeAAAAAddress: Boolean
        get() = records?.count { it.content == "2a01:4f8:c010:974b::1" && it.type == "AAAA" } == 1

    val tintColor: Color
        get() = when(isSameTypeAAddress || isSameTypeAAAAAddress) {
            true -> Color.Green
            else -> Color.Red
        }
}