package de.rpicloud.ipv64net.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.rpicloud.ipv64net.R

data class Integration(
    var integration: String? = "",
    var integration_id: Int? = 0,
    var integration_name: String? = "",
    var options: IntegrationOptions? = IntegrationOptions.empty,
    var add_time: String? = "",
    var last_used: String? = ""
) {
    companion object {
        val empty = Integration("", 0, "", IntegrationOptions.empty, "", "")
    }

    val icon: Int
        get() = when (integration) {
            "webhook" -> R.drawable.webhook_24px
            "discord" -> R.drawable.discord_24px
            "ntfy" -> R.drawable.ntfy_24px
            "pushover" -> R.drawable.pushover_24px
            "telegram" -> R.drawable.telegram_24px
            "gotify" -> R.drawable.gotify_24px
            "email" -> R.drawable.alternate_email_24px
            "sms" -> R.drawable.sms_24px
            "mobil" -> R.drawable.mobile_24px
            else -> R.drawable.indeterminate_question_box_24px
        }

    var selectedState by mutableStateOf(false)
}
