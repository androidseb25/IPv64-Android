package de.rpicloud.ipv64net.models

data class RequestType (
    var icon: Int? = 0,
    var iconColor: Int? = 0,
    var navigationTitle: String? = "",
    var errorTitle: String? = "",
    var errorDescription: String? = "",
    var status: Int?
)