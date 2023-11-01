package de.rpicloud.ipv64net

enum class LaunchScreens {
    LOGIN, MAIN, BIOMETRIC
}

enum class StatusType(val type: StatusTypeClass) {
    Active(
        StatusTypeClass(
            1,
            "Aktiv",
            R.drawable.round_check_circle_outline_24,
            R.color.ipv64_error_green
        )
    ),
    Pause(
        StatusTypeClass(
            2,
            "Pause",
            R.drawable.round_pause_circle_outline_24,
            R.color.ipv64_teal
        )
    ),
    Warning(
        StatusTypeClass(
            3,
            "Warnung",
            R.drawable.round_report_problem_24,
            R.color.ipv64_error_orange
        )
    ),
    Alarm(
        StatusTypeClass(
            4,
            "Alarm",
            R.drawable.round_notifications_active_24,
            R.color.ipv64_red
        )
    ),
    StabilizePhase(
        StatusTypeClass(
            5,
            "Stabilisierungsphase",
            R.color.transparent,
            R.color.ipv64_red
        )
    ),
    Fallback(
        StatusTypeClass(
            0,
            "Aktualisiert",
            R.color.transparent,
            R.color.ipv64_gray
        )
    ),
    Transparent(StatusTypeClass(-1, "", R.color.transparent, R.color.transparent))
}

enum class Unit(val Unit: MTimeUnit) {
    Minuten(MTimeUnit(1, "Minute/n")), Stunden(MTimeUnit(2, "Stunde/n")), Tage(
        MTimeUnit(
            3,
            "Tag/e"
        )
    )
}

enum class ConsumptionType(val CType: Int) {
    Gas(1), Strom(2), Kaltwasser(3), Warmwasser(4), Muell(5);
}

enum class ConsumptionUnit(val Unit: String) {
    KubikUnit("m³"), StromUnit("kWh"), MuellUnit("x");
}