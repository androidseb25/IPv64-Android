package de.rpicloud.ipv64net.models

data class HealthCheck(
    // healthstatus == 1 = Active; 2 = Paused; 3 = Warning; 4 = Alarm;
    var name: String = "",
    var healthstatus: Int = 0,
    var healthtoken: String = "",
    var add_time: String? = "",
    var last_update_time: String? = "",
    var alarm_time: String? = "",
    var alarm_down: Int = 0,
    var alarm_up: Int = 0,
    var integration_id: String = "0",
    var alarm_count: Int = 0,
    var alarm_unit: Int = 0,
    var grace_count: Int = 0,
    var grace_unit: Int = 0,
    var pings_total: Int = 0,
    var type: String = "",
    var type_options: String = "",
    var next_ping: String = "",
    var events: MutableList<HealthEvents> = mutableListOf()
) {
    companion object {
        val empty = HealthCheck()
    }

    val HealthStatus: StatusTypeClass
        get() = when(healthstatus) {
            1 -> StatusType.Active.type
            2 -> StatusType.Pause.type
            3 -> StatusType.Warning.type
            4 -> StatusType.Alarm.type
            5 -> StatusType.StabilizePhase.type
            else -> StatusType.Fallback.type
        }

    val AlarmUnit: Unit
        get() = when(alarm_unit) {
            1 -> Unit.Minuten
            2 -> Unit.Stunden
            else -> Unit.Tage
        }

    val GraceUnit: Unit
        get() = when(grace_unit) {
            1 -> Unit.Minuten
            2 -> Unit.Stunden
            else -> Unit.Tage
        }

    val AlarmDown: String
        get() = when (alarm_down) {
            0 -> "no"
            else -> "yes"
        }

    val AlarmUp: String
        get() = when (alarm_up) {
            0 -> "no"
            else -> "yes"
        }
}