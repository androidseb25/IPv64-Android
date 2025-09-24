package de.rpicloud.ipv64net.models

data class NetworkResult(
    var message: String?,
    var data: Any?,
    var status: Int
) {
    companion object {
        val empty = NetworkResult("", null, 0)
    }
}