package de.rpicloud.ipv64net.models

import java.util.UUID

data class MyLogs(
    var id: UUID = UUID.randomUUID(),
    var subdomain: String? = "",
    var time: String? = "",
    var header: String? = "",
    var content: String? = ""
) {
    companion object {
        val empty = MyLogs(UUID.randomUUID(), "", "", "", "")
    }
}

data class Logs(
    var logs: MutableList<MyLogs> = mutableListOf(),
    var info: String? = "",
) {
    companion object {
        val empty = Logs(mutableListOf(), "")
    }
}