package de.rpicloud.ipv64net.models

import de.rpicloud.ipv64net.R

enum class LaunchScreens {
    LOGIN, MAIN, BIOMETRIC
}

enum class RequestTyp(val type: RequestType) {
    TooManyRequests(RequestType(
        R.drawable.cloud_sync_24px,
        R.color.ipv64_orange,
        "Information",
        "Too many requests!",
        "You have exceeded your limit of 5 API requests within the allowed 5 seconds.",
        429
    )),
    UpdateCoolDown(RequestType(
        R.drawable.bolt_24px,
        R.color.ipv64_orange,
        "Information",
        "Too many updates!",
        "The A record update can only be run every 5 seconds!",
        429
    )),
    DomainNotAvailable(RequestType(
        R.drawable.cloud_off_24px,
        R.color.ipv64_red,
        "Error",
        "Domain not available!",
        "Your selected domain is already taken!",
        403
    )),
    UnAuthorized(RequestType(
        R.drawable.error_24px,
        R.color.ipv64_red,
        "Failed",
        "Authorization failed!",
        "Your authorization on the server has failed, please log in again.",
        401
    )),
    DomainLimitReached(RequestType(
        R.drawable.cloud_off_24px,
        R.color.ipv64_red,
        "Failed",
        "Domain limit reached!",
        "You have reached your domain limit!",
        403
    )),
    DomainRulesNotCreated(RequestType(
        R.drawable.cloud_off_24px,
        R.color.ipv64_red,
        "Failed",
        "Domain could not be created!",
        "Your domain could not be created because it violates the domain rule.",
        403
    )),
    WebsiteRequestFailed(RequestType(
        R.drawable.bolt_24px,
        R.color.ipv64_red,
        "Failed",
        "Connection to server failed!",
        "A connection to the server could not be established!",
        500
    ))
}
