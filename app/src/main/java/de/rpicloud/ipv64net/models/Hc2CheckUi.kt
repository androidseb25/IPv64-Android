package de.rpicloud.ipv64net.models

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import de.rpicloud.ipv64net.R
import de.sr.ipv64.model.Hc2CheckType

// Darstellung eines hc2-Check-Zustands (Badge, Zusammenfassung, Ereignisse)
enum class Hc2StateUi(
    val label: String,
    @param:ColorRes val color: Int,
    @param:DrawableRes val icon: Int
) {
    UP("Up", R.color.ipv64_green, R.drawable.heart_check_24px),
    DOWN("Down", R.color.ipv64_red, R.drawable.e911_emergency_24px),
    DEGRADED("Degraded", R.color.ipv64_orange, R.drawable.warning_24px),
    FLAPPING("Flapping", R.color.ipv64_orange, R.drawable.release_alert_24px),
    PAUSED("Paused", R.color.ipv64_teal, R.drawable.pause_circle_24px),
    PENDING("Pending", R.color.ipv64_gray, R.drawable.schedule_24px),
    UNKNOWN("Unknown", R.color.ipv64_gray, R.drawable.indeterminate_question_box_24px);

    companion object {
        // Zustand aus der API (up, down, degraded, flapping, paused, pending); Unbekanntes → UNKNOWN
        fun from(state: String?): Hc2StateUi = when (state?.trim()?.lowercase()) {
            "up" -> UP
            "down" -> DOWN
            "degraded" -> DEGRADED
            "flapping" -> FLAPPING
            "paused" -> PAUSED
            "pending" -> PENDING
            else -> UNKNOWN
        }

        // Ein pausierter Check zeigt immer "Paused", auch wenn state noch den letzten Zustand enthält
        fun from(state: String?, paused: Boolean?): Hc2StateUi = if (paused == true) PAUSED else from(state)
    }
}

// Lesbarer Name des Check-Typs
val Hc2CheckType?.label: String
    get() = when (this) {
        Hc2CheckType.HTTP -> "HTTP(S)"
        Hc2CheckType.KEYWORD -> "Keyword"
        Hc2CheckType.JSON -> "JSON"
        Hc2CheckType.DNS -> "DNS"
        Hc2CheckType.ICMP -> "Ping (ICMP)"
        Hc2CheckType.TCP -> "TCP"
        Hc2CheckType.PORT -> "Port"
        Hc2CheckType.CERT -> "Certificate"
        Hc2CheckType.PUSH -> "Push"
        Hc2CheckType.DOCKER -> "Docker"
        Hc2CheckType.WHOIS -> "WHOIS"
        null -> "Unknown"
    }

// Platzhalter für das Ziel-Feld im Formular (leer bei Push, dort gibt es kein Ziel)
val Hc2CheckType.targetHint: String
    get() = when (this) {
        Hc2CheckType.HTTP, Hc2CheckType.KEYWORD, Hc2CheckType.JSON -> "https://example.com/health"
        Hc2CheckType.DNS -> "example.com"
        Hc2CheckType.ICMP -> "example.com or IP address"
        Hc2CheckType.TCP -> "host:port"
        Hc2CheckType.PORT -> "host:port"
        Hc2CheckType.CERT -> "example.com or host:port"
        Hc2CheckType.PUSH -> ""
        Hc2CheckType.DOCKER -> "Container name"
        Hc2CheckType.WHOIS -> "example.com"
    }

// Online (ohne Node) anlegbar; Docker braucht immer einen Node
val Hc2CheckType.creatableOnline: Boolean
    get() = this != Hc2CheckType.DOCKER

// Sekunden lesbar, z. B. 45 s, 5 min, 1 min 30 s, 2 h, 1 h 30 min, 1 d 2 h
fun formatSeconds(seconds: Int): String {
    if (seconds <= 0) return "0 s"
    val units = listOf(86_400 to "d", 3_600 to "h", 60 to "min", 1 to "s")
    var rest = seconds
    val parts = mutableListOf<String>()
    for ((size, unit) in units) {
        val value = rest / size
        if (value > 0) {
            parts += "$value $unit"
            rest -= value * size
        }
    }
    // Höchstens zwei Einheiten anzeigen
    return parts.take(2).joinToString(" ")
}

// Erinnerungsintervall: null → "–", 0 → "Off", sonst z. B. "15 min", "6 h", "1 d"
fun repeatMinutesLabel(minutes: Int?): String = when {
    minutes == null -> "–"
    minutes <= 0 -> "Off"
    else -> formatSeconds(minutes * 60)
}
