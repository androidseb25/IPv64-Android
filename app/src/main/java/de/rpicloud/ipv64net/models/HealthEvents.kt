package de.rpicloud.ipv64net.models

import java.util.UUID

data class HealthEvents(
    var id: UUID = UUID.randomUUID(),
    var event_time: String = "",
    var status: Int = 0,
    var text: String = ""
) {
    companion object {
        val empty = HealthEvents()
    }

    val Status: StatusTypeClass
        get() = when(status) {
            1 -> StatusType.Active.type
            2 -> StatusType.Pause.type
            3 -> StatusType.Warning.type
            4 -> StatusType.Alarm.type
            5 -> StatusType.StabilizePhase.type
            else -> StatusType.Fallback.type
        }
}