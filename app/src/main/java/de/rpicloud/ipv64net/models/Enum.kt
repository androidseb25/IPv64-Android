package de.rpicloud.ipv64net.models

import de.rpicloud.ipv64net.R

enum class LaunchScreens {
    LOGIN, MAIN
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
