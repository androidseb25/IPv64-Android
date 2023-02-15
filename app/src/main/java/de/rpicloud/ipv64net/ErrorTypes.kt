package de.rpicloud.ipv64net

import android.annotation.SuppressLint
import android.content.Context

class ErrorTypes {
    companion object Factory {
        @SuppressLint("StaticFieldLeak")
        var context: Context? = null

        var tooManyRequests: ErrorTyp = ErrorTyp (
            R.drawable.ic_sync_24,
            R.color.ipv64_error_orange,
            "Information",
            "Zu Viele Aktualisierungen!",
            "Du hast dein Limit von 5 API-Anfragen innerhalb der erlaubten 5 Sek. überschritten.",
            429
        )

        var updateCoolDown: ErrorTyp = ErrorTyp (
            R.drawable.round_bolt_24,
            R.color.ipv64_error_orange,
            "Information",
            "Zu Viele Aktualisierungen!",
            "Die A-Record Aktualisierung kann nur alle 5 Sek. ausgeführt werden!",
            429
        )

        var domainNotAvailable: ErrorTyp = ErrorTyp (
            R.drawable.ic_cloud_off_24,
            R.color.ipv64_red,
            "Fehler",
            "Domaine nicht verfügbar!",
            "Deine ausgewählte Domaine ist bereits vergeben!",
            403
        )

        var domainCreatedSuccesfully: ErrorTyp = ErrorTyp (
            R.drawable.ic_cloud_done_24,
            R.color.ipv64_error_green,
            "Erfolgreich",
            "Domaine wurde erfolgreich reserviert!",
            "Deine ausgewählte Domaine wurde erfolgreich, bei uns im System, für dich reserviert!",
            201
        )

        var deleteDomain: ErrorTyp = ErrorTyp (
            R.drawable.ic_delete_forever_24,
            R.color.ipv64_red,
            "Wirklick löschen?",
            "Willst du wirklich die Domain löschen?",
            "Deine Domaine wird mit allen bekannten DNS-Records unverzüglich gelöscht.",
            202
        )

        var deletehealth: ErrorTyp = ErrorTyp (
            R.drawable.ic_delete_forever_24,
            R.color.ipv64_red,
            "Wirklick löschen?",
            "Willst du wirklich den Healthcheck löschen?",
            "Dein Healthcheck wird mit allen dazugehörigen Events unverzüglich gelöscht.",
            202
        )

        var dnsRecordSuccesfullyCreated: ErrorTyp = ErrorTyp (
            R.drawable.ic_cloud_done_24,
            R.color.ipv64_error_green,
            "Erfolgreich",
            "DNS-Record wurde erfolgreich erstellt!",
            "Dein DNS-Record wurde erfolgreich im System erstellt!",
            201
        )

        var deleteDNSRecord: ErrorTyp = ErrorTyp (
            R.drawable.ic_delete_forever_24,
            R.color.ipv64_red,
            "Wirklick löschen?",
            "Willst du wirklich den\nDNS-Record löschen?",
            "Dein DNS-Record wird mit sofortiger Wirkung aus deiner Domain gelöscht.",
            202
        )

        var unauthorized: ErrorTyp = ErrorTyp (
            R.drawable.ic_vpn_key_off_24,
            R.color.ipv64_red,
            "Fehlgeschlagen",
            "Authorisierung fehlgeschlagen!",
            "Deine Authorisierung am Server ist fehlgeschlagen, bitte melde dich erneut an.",
            401
        )

        var domainLimit: ErrorTyp = ErrorTyp (
            R.drawable.ic_cloud_off_24,
            R.color.ipv64_red,
            "Fehlgeschlagen",
            "Domainlimit erreicht!",
            "Du hast dein Domainlimit erreicht!",
            403
        )

        var domainRules: ErrorTyp = ErrorTyp (
            R.drawable.ic_cloud_off_24,
            R.color.ipv64_red,
            "Fehlgeschlagen",
            "Domain konnte nicht erstellt werden!",
            "Deine Domaine konnte nicht erstellt werden, da Sie gegen die Domänenregel verstößt.",
            403
        )

        var websiteRequestError: ErrorTyp = ErrorTyp (
            R.drawable.round_bolt_24,
            R.color.ipv64_red,
            "Fehlgeschlagen",
            "Verbindung zum Server fehlgeschlagen!",
            "Es konnte keine Verbindung zum Server hergestellt werden!",
            500
        )

        var healthcheckCreatedSuccesfully: ErrorTyp = ErrorTyp (
            R.drawable.ic_cloud_done_24,
            R.color.ipv64_error_green,
            "Erfolgreich",
            "Healthcheck wurde erfolgreich erstellt!",
            "Dein neuer Healthcheck ist nun online!",
            201
        )

        var healthcheckUpdatedSuccesfully: ErrorTyp = ErrorTyp (
            R.drawable.ic_cloud_done_24,
            R.color.ipv64_error_green,
            "Erfolgreich",
            "Healthcheck erfolgreich aktualisiert!",
            "Dein Healthcheck wurde erfolgreich aktualisiert!",
            201
        )
    }
}