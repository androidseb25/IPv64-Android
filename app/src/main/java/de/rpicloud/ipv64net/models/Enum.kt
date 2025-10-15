package de.rpicloud.ipv64net.models

import de.rpicloud.ipv64net.R

enum class LaunchScreens {
    LOGIN, MAIN, BIOMETRIC
}

enum class StatusType(val type: StatusTypeClass) {
    Active(
        StatusTypeClass(
            1,
            "Active",
            R.drawable.heart_check_24px,
            R.color.ipv64_green
        )
    ),
    Pause(
        StatusTypeClass(
            2,
            "Pause",
            R.drawable.pause_circle_24px,
            R.color.ipv64_teal
        )
    ),
    Warning(
        StatusTypeClass(
            3,
            "Warning",
            R.drawable.warning_24px,
            R.color.ipv64_orange
        )
    ),
    Alarm(
        StatusTypeClass(
            4,
            "Alarm",
            R.drawable.e911_emergency_24px,
            R.color.ipv64_red
        )
    ),
    StabilizePhase(
        StatusTypeClass(
            5,
            "Stabilization phase",
            R.color.transparent,
            R.color.ipv64_red
        )
    ),
    Fallback(
        StatusTypeClass(
            0,
            "Updated",
            R.color.transparent,
            R.color.ipv64_gray
        )
    ),
    Transparent(StatusTypeClass(-1, "", R.color.transparent, R.color.transparent))
}

enum class Unit(val Unit: v64TimeUnit) {
    Minuten(v64TimeUnit(1, "Minute/s")),
    Stunden(v64TimeUnit(2, "Hour/s")),
    Tage(v64TimeUnit(3, "Day/s"))
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
