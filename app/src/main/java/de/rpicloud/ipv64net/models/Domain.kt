package de.rpicloud.ipv64net.models

import androidx.compose.ui.graphics.Color
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.v64domains

data class Domain(
    var updates: Int? = 0,
    var wildcard: Int? = 0,
    var domain_update_hash: String? = "",
    var records: MutableList<RecordInfos>? = mutableListOf(),
    // kommt NICHT aus dem JSON – füllen wir aus dem Schlüssel (z. B. "vcbdfaga.ipv64.net")
    var fqdn: String? = "",
    var ipv4: String = "0.0.0.0",
    var ipv6: String = "::1"
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
        get() = records?.count { it.content == ipv4 && it.type == "A" }!! >= 1

    val isSameTypeAAAAAddress: Boolean
        get() = records?.count { it.content == ipv6 && it.type == "AAAA" }!! >= 1

    val tintColor: Color
        get() = when(isSameTypeAAddress || isSameTypeAAAAAddress) {
            true -> Color.Green
            else -> Color.Red
        }

    val baseDomain: String
        get() {
            val parts = fqdn?.split(".")
            parts?.size?.let {
                if (it >= 2) {
                    val domain = parts.takeLast(2).joinToString(".")
                    return if (domain in String().v64domains()) domain else "Own Domain"
                }
            }
            return "Own Domain"
        }
}